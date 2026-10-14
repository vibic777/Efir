package ru.efir.core.files

import ru.efir.core.protocol.RemoteEntry

/**
 * Результат листинга каталога (backend-независимый).
 *
 * @param path запрошенный путь.
 * @param entries содержимое каталога.
 * @param hasMore есть ли ещё элементы за пределами [entries] (пагинация).
 */
data class FileListing(
    val path: String,
    val entries: List<RemoteEntry>,
    val hasMore: Boolean = false,
)

/**
 * Описание открытого файла для потоковой передачи/предпросмотра.
 *
 * @param path путь к файлу.
 * @param name имя файла.
 * @param size полный размер в байтах.
 * @param mimeType MIME-тип (может быть `null`, если неизвестен).
 * @param modifiedAt время изменения (epoch millis).
 */
data class FileHandle(
    val path: String,
    val name: String,
    val size: Long,
    val mimeType: String?,
    val modifiedAt: Long,
)

/** Категория файла для выбора способа предпросмотра. */
enum class FileKind {
    IMAGE,
    VIDEO,
    AUDIO,
    TEXT,
    DIRECTORY,
    OTHER,
}

/** Определяет [FileKind] по MIME-типу/имени. */
fun classify(entry: RemoteEntry): FileKind {
    if (entry.directory) return FileKind.DIRECTORY
    val name = entry.name.lowercase()
    val ext = name.substringAfterLast('.', "")
    return when (ext) {
        "jpg", "jpeg", "png", "gif", "webp", "bmp", "heic" -> FileKind.IMAGE
        "mp4", "mkv", "webm", "mov", "3gp", "avi" -> FileKind.VIDEO
        "mp3", "wav", "ogg", "flac", "aac", "m4a" -> FileKind.AUDIO
        "txt", "md", "json", "xml", "csv", "log", "kt", "kts", "gradle", "properties", "yml", "yaml" -> FileKind.TEXT
        else -> FileKind.OTHER
    }
}

/**
 * Backend-независимый доступ к файловой системе.
 *
 * Реализации:
 *  - JVM/Android — поверх `java.io.File` (см. `LocalFileSystem`);
 *  - Android SAF — отдельная реализация позже (URI-деревья).
 *
 * Все реализации обязаны ограничивать доступ доверенными корнями
 * ([roots]) и не позволять выходить за их пределы (`..`, симлинки).
 */
interface FileSystem {

    /** Доступные корневые каталоги (точки входа). */
    val roots: List<RemoteEntry>

    /**
     * Листинг каталога.
     *
     * @param path `null` — список корней (возвращается синтетический листинг).
     */
    suspend fun list(path: String?, offset: Int = 0, limit: Int = 500): FileListing

    /** Метаданные одного пути. */
    suspend fun stat(path: String): RemoteEntry

    /** Удаление путей (рекурсивно при [recursive]). */
    suspend fun delete(paths: List<String>, recursive: Boolean)

    /** Переименование/перемещение в пределах родительского каталога. */
    suspend fun rename(path: String, newName: String): RemoteEntry

    /** Создание каталога. */
    suspend fun mkdir(path: String): RemoteEntry

    /** Открывает файл для передачи/предпросмотра. */
    suspend fun open(path: String): FileHandle

    /**
     * Читает блок данных файла (для предпросмотра/превью).
     *
     * @param offset смещение от начала файла.
     * @param maxBytes максимальный размер блока.
     */
    suspend fun read(path: String, offset: Long, maxBytes: Int): ByteArray

    /** Размер файла в байтах или `null`, если файл не найден. */
    suspend fun size(path: String): Long?

    /** Дописывает блок данных в конец файла (создаёт при отсутствии). */
    suspend fun append(path: String, bytes: ByteArray)

    /** Открывает (создаёт при отсутствии) файл для записи с обрезкой до [truncateTo]. */
    suspend fun prepareWriteTarget(path: String, truncateTo: Long = 0): Long

    /** Вычисляет SHA-256 содержимого файла (hex, нижний регистр). */
    suspend fun sha256(path: String): String

    /** Свободное место в байтах в разделе, содержащем [path]. */
    suspend fun freeSpace(path: String): Long
}
