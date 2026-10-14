package ru.efir.core.files

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.efir.core.protocol.RemoteEntry
import ru.efir.core.transfer.Sha256
import ru.efir.core.transfer.Sha256Util.toHex
import java.io.File
import java.io.RandomAccessFile
import java.net.URLConnection
import java.nio.file.Files
import java.nio.file.Path

/**
 * Файловая система на JVM (`java.io.File`) с ограничением доступа доверенными
 * корнями. Используется на Android (внешнее хранилище) и на Desktop.
 *
 * Все операции проверяют, что целевой путь лежит внутри одного из [roots]
 * (после нормализации и разрешения симлинков — через `canonicalPath`).
 *
 * Потокобезопасна: операции stateless, ввод-вывод выполняется на [Dispatchers.IO].
 */
class LocalFileSystem(
    /** Разрешённые корни на сейчас: человек меняет их в «Настройках», пока приложение работает. */
    private val rootsProvider: () -> List<String>,
    private val listLimitDefault: Int = 500,
) : FileSystem {

    constructor(rootPaths: List<String> = defaultFileRoots(), listLimitDefault: Int = 500) :
        this({ rootPaths }, listLimitDefault)

    private class Roots(val paths: List<String>, val files: List<File>)

    @Volatile
    private var cachedRoots = Roots(emptyList(), emptyList())

    /** Служебные папки приложения: недоступны, даже если лежат внутри корня. */
    private val protectedDirs: List<File> = protectedFilePaths().map { path ->
        val file = File(path)
        runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
    }

    /** Канонические абсолютные пути корней (для проверки вложенности). */
    private val canonicalRoots: List<File>
        get() {
            val paths = rootsProvider()
            val cached = cachedRoots
            if (cached.paths == paths) return cached.files
            val files = paths
                .map { path ->
                    val file = File(path)
                    runCatching { file.canonicalFile }.getOrElse { file.absoluteFile }
                }
                .distinct()
            cachedRoots = Roots(paths, files)
            return files
        }

    override val roots: List<RemoteEntry> get() = canonicalRoots.map { root ->
        RemoteEntry(
            name = root.name.ifEmpty { root.path },
            path = root.path,
            directory = true,
            size = 0,
            modifiedAt = root.lastModified(),
            readable = root.canRead(),
            writable = root.canWrite(),
        )
    }

    override suspend fun list(path: String?, offset: Int, limit: Int): FileListing =
        withContext(Dispatchers.IO) {
            if (path.isNullOrBlank()) {
                // Запрос доступных корней.
                val all = roots
                val sliced = all.drop(offset).take(if (limit <= 0) listLimitDefault else limit)
                return@withContext FileListing(
                    path = "",
                    entries = sliced,
                    hasMore = offset + sliced.size < all.size,
                )
            }
            val dir = resolve(path) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
            if (!dir.exists()) throw IllegalArgumentException("Каталог не найден: $path")
            if (!dir.isDirectory) throw IllegalArgumentException("Не каталог: $path")

            val children = dir.listFiles()?.toList().orEmpty()
                .sortedWith(compareByDescending<File> { it.isDirectory }.thenBy { it.name.lowercase() })
            val effectiveLimit = if (limit <= 0) listLimitDefault else limit
            val page = children.drop(offset).take(effectiveLimit).map { it.toEntry() }
            FileListing(
                path = dir.path,
                entries = page,
                hasMore = offset + page.size < children.size,
            )
        }

    override suspend fun stat(path: String): RemoteEntry = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
        if (!file.exists()) throw IllegalArgumentException("Не найдено: $path")
        file.toEntry()
    }

    override suspend fun delete(paths: List<String>, recursive: Boolean): Unit = withContext(Dispatchers.IO) {
        for (raw in paths) {
            val file = resolve(raw) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $raw")
            if (!file.exists()) continue
            if (file.isDirectory && !recursive && (file.list()?.isNotEmpty() == true)) {
                throw IllegalArgumentException("Каталог не пуст (требуется рекурсивное удаление): $raw")
            }
            if (!file.deleteRecursively()) {
                throw IllegalStateException("Не удалось удалить: $raw")
            }
        }
    }

    override suspend fun rename(path: String, newName: String): RemoteEntry = withContext(Dispatchers.IO) {
        PathSanitizer.requireValidName(newName)
        val source = resolve(path) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
        if (!source.exists()) throw IllegalArgumentException("Не найдено: $path")

        val parent = source.parentFile ?: throw IllegalArgumentException("Нет родительского каталога: $path")
        val target = File(parent, newName)
        // Целевой путь обязан оставаться внутри доверенных корней.
        resolve(target.path) ?: throw IllegalArgumentException("Целевой путь вне корней: ${target.path}")
        if (target.exists() && target.canonicalPath != source.canonicalPath) {
            throw IllegalArgumentException("Имя уже занято: $newName")
        }
        if (source.canonicalPath == target.canonicalPath) return@withContext source.toEntry()
        if (!source.renameTo(target)) {
            throw IllegalStateException("Не удалось переименовать: $path → $newName")
        }
        target.toEntry()
    }

    override suspend fun mkdir(path: String): RemoteEntry = withContext(Dispatchers.IO) {
        val dir = resolve(path, allowMissing = true)
            ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
        if (dir.exists()) {
            if (dir.isDirectory) return@withContext dir.toEntry()
            throw IllegalArgumentException("Уже существует файл с таким именем: $path")
        }
        if (!dir.mkdirs()) throw IllegalStateException("Не удалось создать каталог: $path")
        dir.toEntry()
    }

    override suspend fun open(path: String): FileHandle = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
        if (!file.exists() || !file.isFile) throw IllegalArgumentException("Файл не найден: $path")
        FileHandle(
            path = file.path,
            name = file.name,
            size = file.length(),
            mimeType = guessMimeType(file),
            modifiedAt = file.lastModified(),
        )
    }

    override suspend fun read(path: String, offset: Long, maxBytes: Int): ByteArray =
        withContext(Dispatchers.IO) {
            val file = resolve(path) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
            if (!file.exists() || !file.isFile) throw IllegalArgumentException("Файл не найден: $path")
            val start = offset.coerceAtLeast(0)
            val size = maxBytes.coerceAtLeast(0)
            if (size == 0) return@withContext ByteArray(0)
            RandomAccessFile(file, "r").use { raf ->
                if (start >= raf.length()) return@withContext ByteArray(0)
                raf.seek(start)
                val toRead = minOf(size.toLong(), raf.length() - start).toInt()
                val buffer = ByteArray(toRead)
                raf.readFully(buffer)
                buffer
            }
        }

    override suspend fun size(path: String): Long? = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
        if (file.exists() && file.isFile) file.length() else null
    }

    override suspend fun append(path: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val file = resolve(path, allowMissing = true)
            ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
        file.parentFile?.mkdirs()
        if (file.exists() && !file.isFile) throw IllegalArgumentException("Не файл: $path")
        file.appendBytes(bytes)
    }

    override suspend fun prepareWriteTarget(path: String, truncateTo: Long): Long =
        withContext(Dispatchers.IO) {
            val file = resolve(path, allowMissing = true)
                ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
            if (file.exists() && !file.isFile) throw IllegalArgumentException("Не файл: $path")
            file.parentFile?.mkdirs()
            RandomAccessFile(file, "rw").use { raf ->
                val target = truncateTo.coerceIn(0, raf.length())
                if (raf.length() != target) raf.setLength(target)
                if (raf.length() == 0L) {
                    // Гарантируем существование файла для последующей дозаписи.
                    raf.write(ByteArray(0))
                }
            }
            file.length()
        }

    override suspend fun sha256(path: String): String = withContext(Dispatchers.IO) {
        val file = resolve(path) ?: throw IllegalArgumentException("Путь вне разрешённых корней: $path")
        if (!file.exists() || !file.isFile) throw IllegalArgumentException("Файл не найден: $path")
        val digest = Sha256()
        val buffer = ByteArray(SHA_BUFFER)
        file.inputStream().use { input ->
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        digest.digest().toHex()
    }

    override suspend fun freeSpace(path: String): Long = withContext(Dispatchers.IO) {
        val file = resolve(path, allowMissing = true) ?: File(path).absoluteFile
        val probe = generateSequence(file) { it.parentFile }.firstOrNull { it.exists() }
            ?: return@withContext Long.MAX_VALUE
        runCatching { probe.usableSpace }.getOrDefault(Long.MAX_VALUE)
    }

    /** Каталог из каталога [File] в [RemoteEntry]. */
    private fun File.toEntry(): RemoteEntry = RemoteEntry(
        name = name,
        path = path,
        directory = isDirectory,
        size = if (isDirectory) 0 else length(),
        modifiedAt = lastModified(),
        readable = canRead(),
        writable = canWrite(),
    )

    /**
     * Разрешает путь в [File], проверяя, что он внутри одного из корней.
     *
     * @param allowMissing разрешить несуществующий целевой путь (для mkdir).
     * @return файл или `null`, если путь вне корней.
     */
    private fun resolve(path: String, allowMissing: Boolean = false): File? {
        val candidate = File(path)
        val canonical = runCatching {
            if (candidate.exists() || !allowMissing) candidate.canonicalFile
            else canonicalizeMissing(candidate)
        }.getOrElse { candidate.absoluteFile }
        val inside = canonicalRoots.any { root -> isInside(root, canonical) }
        // Папка данных самого приложения (токены доверия, журнал) другому устройству не отдаётся.
        val private = protectedDirs.any { dir -> isInside(dir, canonical) }
        return if (inside && !private) canonical else null
    }

    /**
     * Канонизирует несуществующий путь: канонизирует ближайшего существующего
     * предка и дописывает оставшиеся сегменты.
     */
    private fun canonicalizeMissing(file: File): File {
        val existing = generateSequence(file) { it.parentFile }.firstOrNull { it.exists() }
            ?: return file.absoluteFile
        val existingCanonical: Path = existing.canonicalFile.toPath()
        val relative = existing.toPath().relativize(file.toPath()).toString()
        // normalize убирает «..» из несуществующего хвоста: без него путь вида
        // «корень/нет/../../чужое» считался лежащим внутри корня.
        return existingCanonical.resolve(relative).normalize().toFile()
    }

    private fun isInside(root: File, target: File): Boolean {
        val rootPath = root.toPath()
        val targetPath = target.toPath()
        return targetPath == rootPath || targetPath.startsWith(rootPath)
    }

    private fun guessMimeType(file: File): String? =
        runCatching { Files.probeContentType(file.toPath()) }.getOrNull()
            ?: URLConnection.guessContentTypeFromName(file.name)

    private companion object {
        const val SHA_BUFFER = 64 * 1024
    }
}
