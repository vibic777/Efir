package ru.efir.core.sync

import kotlinx.serialization.Serializable
import ru.efir.core.protocol.SyncActionType
import ru.efir.core.protocol.SyncSide

/**
 * Узел дерева файлов одной стороны (локальной или удалённой).
 *
 * Путь [relativePath] — относительный (относительно корня папки правила),
 * с каноническим разделителем `/`. Для каталогов [sha256] всегда `null`.
 *
 * @param directory признак каталога.
 * @param size размер файла в байтах (0 для каталогов).
 * @param modifiedAt время изменения (epoch millis).
 * @param sha256 контрольная сумма содержимого (для файлов) или `null`.
 */
@Serializable
data class SyncFileNode(
    val relativePath: String,
    val directory: Boolean = false,
    val size: Long = 0,
    val modifiedAt: Long = 0,
    val sha256: String? = null,
)

/**
 * Снимок одной стороны: список узлов, проиндексированный по [relativePath].
 * Вспомогательный контейнер (строится сканером, не сериализуется).
 */
data class SyncSnapshot(
    val side: SyncSide,
    val nodes: List<SyncFileNode>,
) {
    /** Индекс по относительному пути. */
    val byPath: Map<String, SyncFileNode> = nodes.associateBy { it.relativePath }

    operator fun get(relativePath: String): SyncFileNode? = byPath[relativePath]
}

/**
 * Запись базовой линии синхронизации: наблюдённое состояние *каждой* стороны
 * на момент последней успешной синхронизации. Хранение обеих сторон отдельно
 * исключает ложные «изменения» из-за разного mtime после копирования.
 */
@Serializable
data class SyncBaselineEntry(
    val relativePath: String,
    val local: SyncFileNode? = null,
    val remote: SyncFileNode? = null,
)

/** Базовая линия одного правила. */
@Serializable
data class SyncBaseline(
    val ruleId: String,
    val entries: List<SyncBaselineEntry> = emptyList(),
    val updatedAt: Long = 0,
) {
    fun byPath(): Map<String, SyncBaselineEntry> = entries.associateBy { it.relativePath }
}

/** Отображение протокольной стороны во внутреннюю (для читаемости кода). */
fun SyncSide.isLocal(): Boolean = this == SyncSide.LOCAL

/** Отображение внутренней стороны в протокольную. */
fun SyncSide.toProtocol(): SyncSide = if (this == SyncSide.LOCAL) SyncSide.LOCAL else SyncSide.REMOTE

/** Вид изменения стороны относительно базовой линии. */
enum class SyncSideChange { NONE, CREATED, MODIFIED, REMOVED }

/**
 * Различие одного относительного пути между сторонами и базовой линией.
 *
 * @param local узел локальной стороны или `null`, если отсутствует.
 * @param remote узел удалённой стороны или `null`, если отсутствует.
 * @param localChange что произошло локально относительно базовой линии.
 * @param remoteChange что произошло удалённо относительно базовой линии.
 * @param conflict обе стороны изменились несовместимо.
 */
data class SyncFileDiff(
    val relativePath: String,
    val local: SyncFileNode?,
    val remote: SyncFileNode?,
    val localChange: SyncSideChange,
    val remoteChange: SyncSideChange,
    val conflict: Boolean,
) {
    val isDirectory: Boolean
        get() = (local?.directory ?: false) || (remote?.directory ?: false)
}

/** Запись журнала синхронизации. */
@Serializable
data class SyncJournalEntry(
    val ruleId: String,
    val timestamp: Long,
    val action: SyncActionType,
    val side: SyncSide,
    val relativePath: String,
    val success: Boolean,
    val detail: String? = null,
)

/** Хранилище журнала одного правила. */
@Serializable
data class SyncJournal(
    val ruleId: String,
    val entries: List<SyncJournalEntry> = emptyList(),
)
