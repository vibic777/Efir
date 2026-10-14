package ru.efir.core.sync

import ru.efir.core.protocol.SyncAction
import ru.efir.core.protocol.SyncActionType
import ru.efir.core.protocol.SyncConflict
import ru.efir.core.protocol.SyncConflictStrategy
import ru.efir.core.protocol.SyncDirection
import ru.efir.core.protocol.SyncPlanResult
import ru.efir.core.protocol.SyncResolution
import ru.efir.core.protocol.SyncResolutionChoice
import ru.efir.core.protocol.SyncRuleConfig
import ru.efir.core.protocol.SyncSide

/**
 * Строит план действий синхронизации из различий [SyncFileDiff] и правил
 * [SyncRuleConfig]: учитывает направление, стратегию разрешения конфликтов,
 * распространение удалений и автопереименование при коллизиях.
 *
 * Соглашение по [SyncAction.side]:
 *  - для `ADD`/`UPDATE` — сторона-**источник** (данные копируются с неё на другую);
 *  - для `DELETE` — сторона-**цель** (файл удаляется на ней);
 *  - `RENAME` выражается как `ADD`/`UPDATE` с заполненным [SyncAction.newName]:
 *    имя файла в цели заменяется на [SyncAction.newName].
 *
 * Путь в действии — относительный к корню соответствующей стороны.
 */
class SyncPlanner {

    /** Строит план синхронизации (dry-run): действия + нерешённые конфликты. */
    fun plan(
        rule: SyncRuleConfig,
        local: SyncSnapshot,
        remote: SyncSnapshot,
        diffs: List<SyncFileDiff>,
    ): SyncPlanResult {
        val actions = mutableListOf<SyncAction>()
        val conflicts = mutableListOf<SyncConflict>()
        val localPaths = local.byPath.keys
        val remotePaths = remote.byPath.keys

        for (diff in diffs) {
            if (diff.local == null && diff.remote == null) continue

            if (diff.isDirectory) {
                directoryAction(rule, diff)?.let { actions.add(it) }
                continue
            }

            if (diff.conflict) {
                val resolved = resolveConflict(rule, diff, localPaths, remotePaths)
                if (resolved != null) actions.add(resolved) else conflicts.add(diff.toConflict())
            } else {
                singleSidedAction(rule, diff)?.let { actions.add(it) }
            }
        }

        return SyncPlanResult(ruleId = rule.id, actions = actions, conflicts = conflicts)
    }

    /**
     * Применяет ручные выборы пользователя к конфликтам (стратегия `ASK`):
     * формирует действия и обновляет список неразрешённых конфликтов.
     */
    fun resolve(
        rule: SyncRuleConfig,
        local: SyncSnapshot,
        remote: SyncSnapshot,
        diffs: List<SyncFileDiff>,
        resolutions: List<SyncResolution>,
    ): SyncPlanResult {
        val byPath = resolutions.associateBy { it.relativePath }
        val actions = mutableListOf<SyncAction>()
        val unresolved = mutableListOf<SyncConflict>()
        val localPaths = local.byPath.keys
        val remotePaths = remote.byPath.keys

        for (diff in diffs) {
            if (!diff.conflict) {
                // Не-конфликтные различия обрабатываем обычным образом.
                if (diff.isDirectory) {
                    directoryAction(rule, diff)?.let { actions.add(it) }
                } else {
                    singleSidedAction(rule, diff)?.let { actions.add(it) }
                }
                continue
            }
            val resolution = byPath[diff.relativePath]
            val action = resolution?.let {
                actionForChoice(rule, diff, it.choice, localPaths, remotePaths)
            } ?: resolveConflict(rule, diff, localPaths, remotePaths)
            if (action != null) actions.add(action) else unresolved.add(diff.toConflict())
        }
        return SyncPlanResult(ruleId = rule.id, actions = actions, conflicts = unresolved)
    }

    /** Строит действие по явному выбору пользователя. */
    private fun actionForChoice(
        rule: SyncRuleConfig,
        diff: SyncFileDiff,
        choice: SyncResolutionChoice,
        localPaths: Set<String>,
        remotePaths: Set<String>,
    ): SyncAction? = when (choice) {
        SyncResolutionChoice.KEEP_LOCAL -> preferSide(rule, diff, SyncSide.LOCAL)
        SyncResolutionChoice.KEEP_REMOTE -> preferSide(rule, diff, SyncSide.REMOTE)
        SyncResolutionChoice.KEEP_NEWEST -> preferNewest(rule, diff)
        SyncResolutionChoice.KEEP_BOTH -> renameBoth(rule, diff, localPaths, remotePaths)
        SyncResolutionChoice.SKIP -> null
    }

    // --- Не-конфликтные случаи -------------------------------------------------

    private fun singleSidedAction(rule: SyncRuleConfig, diff: SyncFileDiff): SyncAction? {
        val localChanged = diff.localChange != SyncSideChange.NONE
        val remoteChanged = diff.remoteChange != SyncSideChange.NONE

        return when {
            !localChanged && !remoteChanged -> null

            localChanged && !remoteChanged -> when (diff.localChange) {
                SyncSideChange.CREATED, SyncSideChange.MODIFIED ->
                    copyAction(rule, SyncSide.LOCAL, diff, diff.local, null)

                SyncSideChange.REMOVED ->
                    deleteAction(rule, target = SyncSide.REMOTE, diff.relativePath)

                SyncSideChange.NONE -> null
            }

            remoteChanged && !localChanged -> when (diff.remoteChange) {
                SyncSideChange.CREATED, SyncSideChange.MODIFIED ->
                    copyAction(rule, SyncSide.REMOTE, diff, diff.remote, null)

                SyncSideChange.REMOVED ->
                    deleteAction(rule, target = SyncSide.LOCAL, diff.relativePath)

                SyncSideChange.NONE -> null
            }

            else -> null
        }
    }

    /** Создаёт отсутствующий каталог на стороне-цели (если разрешено направлением). */
    private fun directoryAction(rule: SyncRuleConfig, diff: SyncFileDiff): SyncAction? {
        val localPresent = diff.local != null
        val remotePresent = diff.remote != null
        return when {
            !localPresent && remotePresent ->
                copyAction(rule, SyncSide.REMOTE, diff, diff.remote, null)

            localPresent && !remotePresent ->
                copyAction(rule, SyncSide.LOCAL, diff, diff.local, null)

            else -> null
        }
    }

    /**
     * Создаёт действие копирования. Тип — `ADD`, если у стороны-цели узла нет,
     * иначе `UPDATE`. `null`, если направление запрещает копирование с [source].
     */
    private fun copyAction(
        rule: SyncRuleConfig,
        source: SyncSide,
        diff: SyncFileDiff,
        node: SyncFileNode?,
        newName: String?,
    ): SyncAction? {
        if (!rule.allowsSource(source)) return null
        val targetNode = if (source == SyncSide.LOCAL) diff.remote else diff.local
        val type = if (targetNode == null || newName != null) SyncActionType.ADD else SyncActionType.UPDATE
        return SyncAction(
            type = type,
            side = source,
            relativePath = diff.relativePath,
            directory = node?.directory ?: false,
            newName = newName,
            size = node?.size ?: 0,
            sha256 = node?.sha256,
        )
    }

    /** Создаёт действие удаления, если разрешено распространение удалений и направление. */
    private fun deleteAction(rule: SyncRuleConfig, target: SyncSide, relativePath: String): SyncAction? {
        if (!rule.deletePropagation) return null
        val source = if (target == SyncSide.LOCAL) SyncSide.REMOTE else SyncSide.LOCAL
        if (!rule.allowsSource(source)) return null
        return SyncAction(
            type = SyncActionType.DELETE,
            side = target,
            relativePath = relativePath,
        )
    }

    // --- Конфликты -------------------------------------------------------------

    /** Разрешает конфликт согласно стратегии; `null` — требуется ручное решение. */
    private fun resolveConflict(
        rule: SyncRuleConfig,
        diff: SyncFileDiff,
        localPaths: Set<String>,
        remotePaths: Set<String>,
    ): SyncAction? = when (rule.conflictStrategy) {
        SyncConflictStrategy.ASK -> null
        SyncConflictStrategy.PREFER_LOCAL -> preferSide(rule, diff, SyncSide.LOCAL)
        SyncConflictStrategy.PREFER_REMOTE -> preferSide(rule, diff, SyncSide.REMOTE)
        SyncConflictStrategy.PREFER_NEWEST,
        SyncConflictStrategy.OVERWRITE,
        -> preferNewest(rule, diff)

        SyncConflictStrategy.MERGE,
        SyncConflictStrategy.APPEND,
        SyncConflictStrategy.RENAME,
        -> renameBoth(rule, diff, localPaths, remotePaths)
    }

    /** Конфликт в пользу выбранной стороны. */
    private fun preferSide(rule: SyncRuleConfig, diff: SyncFileDiff, side: SyncSide): SyncAction? {
        val chosen = if (side == SyncSide.LOCAL) diff.local else diff.remote
        val target = if (side == SyncSide.LOCAL) SyncSide.REMOTE else SyncSide.LOCAL
        return if (chosen != null) {
            copyAction(rule, side, diff, chosen, null)
        } else {
            deleteAction(rule, target, diff.relativePath)
        }
    }

    /** Побеждает сторона с более новым временем изменения. */
    private fun preferNewest(rule: SyncRuleConfig, diff: SyncFileDiff): SyncAction? {
        val localAt = diff.local?.modifiedAt ?: Long.MIN_VALUE
        val remoteAt = diff.remote?.modifiedAt ?: Long.MIN_VALUE
        val side = if (localAt >= remoteAt) SyncSide.LOCAL else SyncSide.REMOTE
        return preferSide(rule, diff, side)
    }

    /** Оставляет оба файла, переименовывая входящий при коллизии. */
    private fun renameBoth(
        rule: SyncRuleConfig,
        diff: SyncFileDiff,
        localPaths: Set<String>,
        remotePaths: Set<String>,
    ): SyncAction? {
        // Если одна сторона удалена — это не «оба», применяем «самый новый».
        if (diff.local == null || diff.remote == null) return preferNewest(rule, diff)

        val source = if (diff.remote.modifiedAt > diff.local.modifiedAt) SyncSide.REMOTE else SyncSide.LOCAL
        val node = if (source == SyncSide.LOCAL) diff.local else diff.remote
        if (!rule.allowsSource(source)) return null
        val targetPaths = if (source == SyncSide.LOCAL) remotePaths else localPaths
        val newName = uniqueName(targetPaths, diff.relativePath)
        return copyAction(rule, source, diff, node, newName)
    }

    /**
     * Генерирует незанятое имя по правилу «минимальный свободный суффикс»:
     * `аааа.ext` → `аааа-1.ext`; если `аааа-1` занят, но `аааа-2` свободен —
     * возвращается `аааа-1` (то есть ищем именно первый незанятый, а не
     * «максимальный + 1»). Точка перед расширением не теряется, файлы без
     * расширения получают суффикс целиком (`readme` → `readme-1`).
     */
    private fun uniqueName(existingPaths: Set<String>, relativePath: String): String {
        val dir = relativePath.substringBeforeLast('/', "")
        val name = relativePath.substringAfterLast('/')
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var counter = 1
        while (counter < MAX_UNIQUE_SUFFIX) {
            val candidate = "$base-$counter$ext"
            val full = if (dir.isEmpty()) candidate else "$dir/$candidate"
            if (full !in existingPaths) return candidate
            counter++
        }
        // Все суффиксы заняты — отступаемся временной меткой.
        return "$base-${syncNow()}$ext"
    }

    private companion object {
        const val MAX_UNIQUE_SUFFIX = 1000
    }

    private fun SyncFileDiff.toConflict(): SyncConflict = SyncConflict(
        relativePath = relativePath,
        localSize = local?.size ?: 0,
        remoteSize = remote?.size ?: 0,
        localModifiedAt = local?.modifiedAt ?: 0,
        remoteModifiedAt = remote?.modifiedAt ?: 0,
    )
}

/** Разрешено ли направление для копирования с указанной стороны-источника. */
internal fun SyncRuleConfig.allowsSource(source: SyncSide): Boolean = when (direction) {
    SyncDirection.BIDIRECTIONAL -> true
    SyncDirection.LOCAL_TO_REMOTE -> source == SyncSide.LOCAL
    SyncDirection.REMOTE_TO_LOCAL -> source == SyncSide.REMOTE
}
