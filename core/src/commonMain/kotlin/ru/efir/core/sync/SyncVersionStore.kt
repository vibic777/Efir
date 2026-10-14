package ru.efir.core.sync

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.efir.core.files.FileSystem
import ru.efir.core.files.PathSanitizer

/**
 * Сохранённая (резервная) версия файла перед перезаписью.
 *
 * @param id уникальный идентификатор версии.
 * @param ruleId правило синхронизации.
 * @param relativePath относительный путь файла (в корне правила).
 * @param side сторона, на которой сохранена резервная копия.
 * @param timestamp время создания версии (epoch millis).
 * @param size размер файла версии.
 * @param backupPath путь к файлу резервной копии (на той же стороне).
 * @param sha256 контрольная сумма оригинала (до перезаписи).
 */
data class SyncVersion(
    val id: String,
    val ruleId: String,
    val relativePath: String,
    val side: ru.efir.core.protocol.SyncSide,
    val timestamp: Long,
    val size: Long,
    val backupPath: String,
    val sha256: String? = null,
)

/**
 * Хранилище версий/бэкапов перезаписанных файлов синхронизации.
 *
 * Позволяет:
 *  - сохранять резервную копию перед перезаписью (`backupBeforeOverwrite`);
 *  - показывать историю версий файла;
 *  - восстанавливать выбранную версию.
 */
interface SyncVersionStore {

    /** Сохраняет резервную копию файла, возвращая запись версии. */
    suspend fun backup(
        ruleId: String,
        relativePath: String,
        side: ru.efir.core.protocol.SyncSide,
        sourcePath: String,
        fileSystem: FileSystem,
    ): SyncVersion?

    /** История версий файла (новые — первыми). */
    suspend fun history(ruleId: String, relativePath: String): List<SyncVersion>

    /** Все версии правила (новые — первыми). */
    suspend fun all(ruleId: String): List<SyncVersion>

    /** Восстанавливает версию поверх целевого файла (на её стороне). */
    suspend fun restore(version: SyncVersion, targetPath: String, fileSystem: FileSystem): Boolean

    /** Удаляет историю версий правила. */
    suspend fun clear(ruleId: String)
}

/**
 * Хранилище версий в памяти: резервные копии кладутся рядом с оригиналами
 * (`<path>.efirbackup.<timestamp>`), а метаданные — в память.
 *
 * Примечание: файлы бэкапов остаются на диске; метод [clear] удаляет только
 * записи метаданных.
 */
class InMemorySyncVersionStore : SyncVersionStore {

    private val mutex = Mutex()
    private val versions = mutableMapOf<String, MutableList<SyncVersion>>()
    private var counter: Long = 0

    override suspend fun backup(
        ruleId: String,
        relativePath: String,
        side: ru.efir.core.protocol.SyncSide,
        sourcePath: String,
        fileSystem: FileSystem,
    ): SyncVersion? = mutex.withLock {
        val exists = fileSystem.size(sourcePath) ?: return@withLock null
        val sha = runCatching { fileSystem.sha256(sourcePath) }.getOrNull()
        val timestamp = syncNow()
        val backupPath = "$sourcePath.efirbackup.$timestamp"
        if (!copyFile(fileSystem, sourcePath, backupPath)) return@withLock null

        val version = SyncVersion(
            id = "v${++counter}_$timestamp",
            ruleId = ruleId,
            relativePath = relativePath,
            side = side,
            timestamp = timestamp,
            size = exists,
            backupPath = backupPath,
            sha256 = sha,
        )
        versions.getOrPut(ruleId) { mutableListOf() }.add(0, version)
        version
    }

    override suspend fun history(ruleId: String, relativePath: String): List<SyncVersion> =
        mutex.withLock {
            versions[ruleId].orEmpty().filter { it.relativePath == relativePath }
        }

    override suspend fun all(ruleId: String): List<SyncVersion> = mutex.withLock {
        versions[ruleId].orEmpty().toList()
    }

    override suspend fun restore(
        version: SyncVersion,
        targetPath: String,
        fileSystem: FileSystem,
    ): Boolean = mutex.withLock {
        copyFile(fileSystem, version.backupPath, targetPath)
    }

    override suspend fun clear(ruleId: String) {
        mutex.withLock { versions.remove(ruleId) }
    }
}

/** Проверяет, лежит ли путь бэкапа рядом с корректным относительным путём. */
internal fun backupRelativePath(relativePath: String): String =
    PathSanitizer.normalize(relativePath)

/** Блок копирования файлов (256 КБ). */
internal const val VERSION_COPY_BLOCK: Int = 256 * 1024

/** Копирует содержимое файла [from] в [to] блоками (без загрузки целиком в память). */
internal suspend fun copyFile(fileSystem: FileSystem, from: String, to: String): Boolean =
    runCatching {
        val size = fileSystem.size(from) ?: return@runCatching false
        fileSystem.prepareWriteTarget(to, 0)
        var offset = 0L
        while (offset < size) {
            val block = fileSystem.read(from, offset, VERSION_COPY_BLOCK)
            if (block.isEmpty()) break
            fileSystem.append(to, block)
            offset += block.size
        }
        true
    }.getOrDefault(false)
