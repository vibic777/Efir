package ru.efir.core.sync

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.efir.core.files.FileSystem
import ru.efir.core.files.PathSanitizer
import ru.efir.core.protocol.RemoteEntry
import ru.efir.core.protocol.SyncAction
import ru.efir.core.protocol.SyncActionType
import ru.efir.core.protocol.SyncPlanResult
import ru.efir.core.protocol.SyncResolution
import ru.efir.core.protocol.SyncRuleConfig
import ru.efir.core.protocol.SyncSide

/** Прогресс/состояние запуска синхронизации. */
data class SyncRunStatus(
    val ruleId: String? = null,
    val running: Boolean = false,
    val currentPath: String? = null,
    val processed: Int = 0,
    val total: Int = 0,
    val message: String? = null,
)

/** Результат применения плана. */
data class SyncApplyResult(
    val ruleId: String,
    val applied: Int,
    val failed: Int,
    val conflicts: Int,
    val errors: List<String> = emptyList(),
)

/**
 * Детальный план с сохранённым контекстом сканирования. Нужен, чтобы разрешать
 * конфликты вручную без повторного дорогостоящего сканирования.
 */
data class SyncDetailedPlan(
    val ruleId: String,
    val result: SyncPlanResult,
    val local: SyncSnapshot,
    val remote: SyncSnapshot,
    val diffs: List<SyncFileDiff>,
)

/**
 * Доступ к удалённой файловой системе (для сканирования/операций синхронизации).
 * Реализуется поверх RPC файлового менеджера.
 */
interface SyncRemoteFs {
    suspend fun list(path: String): List<RemoteEntry>
    suspend fun mkdir(path: String): Boolean
    suspend fun delete(paths: List<String>, recursive: Boolean): Boolean
}

/** Перенос содержимого между сторонами (обёртка над движком передачи). */
interface SyncTransfer {
    suspend fun upload(localPath: String, remotePath: String, overwrite: Boolean = true)
    suspend fun download(remotePath: String, localPath: String, overwrite: Boolean = true)
}

/**
 * Движок синхронизации папок.
 *
 * Оркестрирует цикл **scan → diff → plan → apply → journal → baseline**:
 *  - [SyncScanner] строит снимки обеих сторон (с учётом фильтров правила);
 *  - [SyncDiffer] сравнивает снимки с базовой линией и находит конфликты;
 *  - [SyncPlanner] строит план действий по стратегии и направлению;
 *  - движок применяет план через [transfer]/[FileSystem]/[SyncRemoteFs];
 *  - результаты пишутся в журнал ([SyncStateStore]), после чего обновляется
 *    базовая линия (по фактически наблюдаемому состоянию обеих сторон).
 *
 * Движок не зависит от UI и сети — интеграция обеспечивается адаптерами.
 */
class SyncEngine(
    private val stateStore: SyncStateStore,
    private val localFs: FileSystem,
    private val remoteFs: SyncRemoteFs,
    private val transfer: SyncTransfer,
    /** Дополнительный хешер локальной стороны (по умолчанию по размеру+mtime). */
    private val localHasher: SyncHasher? = null,
    /** Хранилище резервных версий перезаписанных файлов (бэкап перед перезаписью). */
    private val versionStore: SyncVersionStore = InMemorySyncVersionStore(),
) {

    private val differ = SyncDiffer()
    private val planner = SyncPlanner()

    private val _status = MutableStateFlow(SyncRunStatus())

    /** Текущее состояние запуска (для UI). */
    val status: StateFlow<SyncRunStatus> = _status.asStateFlow()

    /** Последний детальный план — для ручного разрешения конфликтов. */
    private var lastDetailedPlan: SyncDetailedPlan? = null

    /** Сканирует обе стороны и строит план (dry-run), не изменяя данные. */
    suspend fun plan(rule: SyncRuleConfig): SyncPlanResult = detailedPlan(rule).result

    /** То же, что [plan], но сохраняет контекст для последующего разрешения конфликтов. */
    suspend fun detailedPlan(rule: SyncRuleConfig): SyncDetailedPlan {
        val filter = PathFilter.from(rule)
        val localScanner = SyncScanner(SyncSide.LOCAL, SyncLister { path -> localFs.list(path, 0, Int.MAX_VALUE).entries }, localHasher)
        val remoteScanner = SyncScanner(SyncSide.REMOTE, SyncLister { path -> remoteFs.list(path) })
        val localSnap = localScanner.scan(rule.localPath, filter)
        val remoteSnap = remoteScanner.scan(rule.remotePath, filter)
        val baseline = stateStore.baseline(rule.id)
        val diffs = differ.diff(localSnap, remoteSnap, baseline)
        val plan = SyncDetailedPlan(
            ruleId = rule.id,
            result = planner.plan(rule, localSnap, remoteSnap, diffs),
            local = localSnap,
            remote = remoteSnap,
            diffs = diffs,
        )
        lastDetailedPlan = plan
        return plan
    }

    /**
     * Разрешает конфликты вручную (выборы пользователя) и применяет получившийся
     * план. Контекст берётся из последнего [detailedPlan] для этого правила либо
     * строится заново.
     */
    suspend fun applyResolutions(rule: SyncRuleConfig, resolutions: List<SyncResolution>): SyncPlanResult {
        val context = lastDetailedPlan?.takeIf { it.ruleId == rule.id } ?: detailedPlan(rule)
        val resolved = planner.resolve(rule, context.local, context.remote, context.diffs, resolutions)
        if (resolved.actions.isNotEmpty()) apply(rule, resolved.actions)
        return resolved
    }

    /**
     * Выполняет полный цикл синхронизации: план + применение.
     * Конфликтующие файлы при стратегии [SyncConflictStrategy.ASK] пропускаются
     * и возвращаются в [SyncPlanResult]; вызывающая сторона решает их вручную.
     */
    suspend fun sync(rule: SyncRuleConfig): SyncPlanResult {
        val plan = plan(rule)
        if (plan.actions.isNotEmpty()) {
            apply(rule, plan.actions)
        } else {
            // Даже при пустом плане фиксируем текущее состояние как базовое,
            // чтобы последующие изменения вычислялись относительно него.
            runCatching { refreshBaseline(rule) }
        }
        return plan
    }

    /** Применяет действия плана, ведёт журнал и обновляет базовую линию. */
    suspend fun apply(rule: SyncRuleConfig, actions: List<SyncAction>): SyncApplyResult {
        val ordered = orderActions(actions)
        _status.value = SyncRunStatus(ruleId = rule.id, running = true, total = ordered.size)
        var applied = 0
        var failed = 0
        val errors = mutableListOf<String>()
        var index = 0

        for (action in ordered) {
            index++
            _status.value = _status.value.copy(
                currentPath = action.relativePath,
                processed = index - 1,
            )
            val result = runCatching { executeAction(rule, action) }
            val success = result.isSuccess
            if (success) applied++ else {
                failed++
                errors.add("${action.relativePath}: ${result.exceptionOrNull()?.message ?: "ошибка"}")
            }
            stateStore.appendJournal(
                SyncJournalEntry(
                    ruleId = rule.id,
                    timestamp = syncNow(),
                    action = action.type,
                    side = action.side,
                    relativePath = action.relativePath,
                    success = success,
                    detail = result.exceptionOrNull()?.message,
                ),
            )
        }

        // Обновляем базовую линию по фактическому состоянию обеих сторон.
        runCatching { refreshBaseline(rule) }

        _status.value = _status.value.copy(
            running = false,
            processed = ordered.size,
            currentPath = null,
            message = if (failed == 0) "Готово" else "Завершено с ошибками: $failed",
        )
        return SyncApplyResult(rule.id, applied, failed, conflicts = 0, errors = errors)
    }

    // --- Применение отдельных действий ----------------------------------------

    private suspend fun executeAction(rule: SyncRuleConfig, action: SyncAction) {
        val targetRelative = targetRelativePath(action.relativePath, action.newName)
        when (action.type) {
            SyncActionType.ADD, SyncActionType.UPDATE, SyncActionType.RENAME -> copy(rule, action.side, action, targetRelative)
            SyncActionType.DELETE -> delete(rule, action.side, action.relativePath)
            SyncActionType.SKIP -> Unit
        }
    }

    private suspend fun copy(rule: SyncRuleConfig, source: SyncSide, action: SyncAction, targetRelative: String) {
        if (action.directory) {
            if (source == SyncSide.LOCAL) {
                remoteFs.mkdir(remotePath(rule, targetRelative))
            } else {
                localFs.mkdir(localPath(rule, targetRelative))
            }
            return
        }
        // Резервная копия существующего целевого файла перед перезаписью.
        if (rule.backupBeforeOverwrite) {
            backupTarget(rule, source, targetRelative)
        }
        if (source == SyncSide.LOCAL) {
            transfer.upload(
                localPath = localPath(rule, action.relativePath),
                remotePath = remotePath(rule, targetRelative),
                overwrite = true,
            )
        } else {
            transfer.download(
                remotePath = remotePath(rule, action.relativePath),
                localPath = localPath(rule, targetRelative),
                overwrite = true,
            )
        }
    }

    /**
     * Делает резервную копию файла-цели перед перезаписью.
     *
     * Резервирование выполняется для **локальной** цели (когда данные идут с
     * удалённой стороны): для этого у движка есть полноценный [FileSystem].
     * Для удалённой цели бэкап не делается — интерфейс [SyncRemoteFs] не
     * предоставляет чтение (ограничение текущей итерации).
     */
    private suspend fun backupTarget(rule: SyncRuleConfig, source: SyncSide, targetRelative: String) {
        if (source != SyncSide.REMOTE) return
        val targetPath = localPath(rule, targetRelative)
        if ((localFs.size(targetPath) ?: 0L) <= 0L) return
        runCatching {
            versionStore.backup(
                ruleId = rule.id,
                relativePath = targetRelative,
                side = SyncSide.LOCAL.toProtocol(),
                sourcePath = targetPath,
                fileSystem = localFs,
            )
        }
    }

    // --- История версий --------------------------------------------------------

    /** История резервных версий файла (новые — первыми). */
    suspend fun versions(ruleId: String, relativePath: String): List<SyncVersion> =
        versionStore.history(ruleId, relativePath)

    /** Все резервные версии правила (новые — первыми). */
    suspend fun allVersions(ruleId: String): List<SyncVersion> = versionStore.all(ruleId)

    /** Восстанавливает локальную версию файла из бэкапа. */
    suspend fun restoreVersion(rule: SyncRuleConfig, version: SyncVersion): Boolean {
        if (version.side != SyncSide.LOCAL.toProtocol()) return false
        val target = localPath(rule, version.relativePath)
        return versionStore.restore(version, target, localFs)
    }

    /** Очищает историю версий правила. */
    suspend fun clearVersions(ruleId: String) = versionStore.clear(ruleId)

    private suspend fun delete(rule: SyncRuleConfig, target: SyncSide, relativePath: String) {
        if (target == SyncSide.LOCAL) {
            localFs.delete(listOf(localPath(rule, relativePath)), recursive = true)
        } else {
            remoteFs.delete(listOf(remotePath(rule, relativePath)), recursive = true)
        }
    }

    // --- Базовая линия ---------------------------------------------------------

    private suspend fun refreshBaseline(rule: SyncRuleConfig) {
        val filter = PathFilter.from(rule)
        val localScanner = SyncScanner(SyncSide.LOCAL, SyncLister { path -> localFs.list(path, 0, Int.MAX_VALUE).entries }, localHasher)
        val remoteScanner = SyncScanner(SyncSide.REMOTE, SyncLister { path -> remoteFs.list(path) })
        val localSnap = localScanner.scan(rule.localPath, filter)
        val remoteSnap = remoteScanner.scan(rule.remotePath, filter)

        val paths = (localSnap.byPath.keys + remoteSnap.byPath.keys).sorted()
        val entries = paths.map { path ->
            SyncBaselineEntry(
                relativePath = path,
                local = localSnap[path],
                remote = remoteSnap[path],
            )
        }
        stateStore.saveBaseline(SyncBaseline(ruleId = rule.id, entries = entries, updatedAt = syncNow()))
    }

    // --- Утилиты ---------------------------------------------------------------

    private fun orderActions(actions: List<SyncAction>): List<SyncAction> {
        // Каталоги — первыми, удаления — последними.
        val dirs = actions.filter { it.directory && it.type != SyncActionType.DELETE }
        val files = actions.filter { !it.directory && it.type != SyncActionType.DELETE }
        val deletes = actions.filter { it.type == SyncActionType.DELETE }
        return dirs + files + deletes
    }

    private fun targetRelativePath(relativePath: String, newName: String?): String {
        if (newName == null) return relativePath
        val dir = relativePath.substringBeforeLast('/', "")
        return if (dir.isEmpty()) newName else "$dir/$newName"
    }

    private fun localPath(rule: SyncRuleConfig, relative: String): String =
        joinPath(rule.localPath, relative)

    private fun remotePath(rule: SyncRuleConfig, relative: String): String =
        joinPath(rule.remotePath, relative)

    private fun joinPath(root: String, relative: String): String {
        if (relative.isBlank()) return PathSanitizer.normalize(root)
        return PathSanitizer.child(root, relative)
    }
}
