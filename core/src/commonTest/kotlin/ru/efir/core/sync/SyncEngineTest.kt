package ru.efir.core.sync

import kotlinx.coroutines.test.runTest
import ru.efir.core.files.FileHandle
import ru.efir.core.files.FileListing
import ru.efir.core.files.FileSystem
import ru.efir.core.protocol.RemoteEntry
import ru.efir.core.protocol.SyncRuleConfig
import ru.efir.core.protocol.SyncSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Сквозной тест движка синхронизации на in-memory реализациях ФС и переноса. */
class SyncEngineTest {

    @Test
    fun uploadsNewLocalFileAndUpdatesBaseline() = runTest {
        val localFs = InMemoryFileSystem()
        localFs.addFile("/local", "notes.txt", "hello".encodeToByteArray())

        val remoteFs = InMemoryRemoteFs()
        val transfer = RecordingTransfer(localFs, remoteFs)

        val store = InMemorySyncStateStore()
        val engine = SyncEngine(store, localFs, remoteFs, transfer)
        val rule = SyncRuleConfig("r", "r", "/local", "/remote")

        val plan = engine.sync(rule)

        assertEquals(1, plan.actions.size)
        assertTrue(transfer.uploads.contains("/local/notes.txt" to "/remote/notes.txt"))
        assertTrue(remoteFs.exists("/remote/notes.txt"))

        // Базовая линия должна зафиксировать обе стороны.
        val baseline = assertNotNull(store.baseline("r"))
        val entry = assertNotNull(baseline.byPath()["notes.txt"])
        assertNotNull(entry.local)
        assertNotNull(entry.remote)

        // Повторная синхронизация не должна вносить изменений.
        val second = engine.sync(rule)
        assertTrue(second.actions.isEmpty(), "повторная синхронизация должна быть пустой")
    }

    @Test
    fun downloadsNewRemoteFile() = runTest {
        val localFs = InMemoryFileSystem()
        val remoteFs = InMemoryRemoteFs()
        remoteFs.addFile("/remote", "photo.jpg", "img".encodeToByteArray())

        val store = InMemorySyncStateStore()
        val engine = SyncEngine(store, localFs, remoteFs, RecordingTransfer(localFs, remoteFs))
        val rule = SyncRuleConfig("r", "r", "/local", "/remote")

        val plan = engine.sync(rule)
        assertEquals(1, plan.actions.size)
        assertEquals(SyncSide.REMOTE, plan.actions[0].side)
        assertTrue(localFs.exists("/local/photo.jpg"))
    }

    @Test
    fun deletePropagationRemovesRemote() = runTest {
        val localFs = InMemoryFileSystem()
        val remoteFs = InMemoryRemoteFs()
        localFs.addFile("/local", "gone.txt", "x".encodeToByteArray())
        remoteFs.addFile("/remote", "gone.txt", "x".encodeToByteArray())

        val store = InMemorySyncStateStore()
        val engine = SyncEngine(store, localFs, remoteFs, RecordingTransfer(localFs, remoteFs))

        // Первая синхронизация — установим базу.
        var rule = SyncRuleConfig("r", "r", "/local", "/remote")
        engine.sync(rule)
        assertTrue(remoteFs.exists("/remote/gone.txt"))

        // Удаляем локально, включаем распространение удалений.
        localFs.remove("/local/gone.txt")
        rule = rule.copy(deletePropagation = true)
        engine.sync(rule)

        assertTrue(!remoteFs.exists("/remote/gone.txt"), "удаление должно распространиться")
    }
}

/** Простая in-memory файловая система (дерево в map путь→байты/каталоги). */
private class InMemoryFileSystem : FileSystem {
    private val files = mutableMapOf<String, ByteArray>()
    private val dirs = mutableSetOf<String>()
    private var clock = 1000L

    private val rootPaths = mutableListOf<String>()

    override val roots: List<RemoteEntry>
        get() = rootPaths.map { RemoteEntry(name = it, path = it, directory = true, size = 0, modifiedAt = 0) }

    fun addRoot(path: String) {
        rootPaths += path
        dirs += path
    }

    fun addFile(dir: String, name: String, bytes: ByteArray) {
        if (dir !in rootPaths) addRoot(dir)
        dirs += dir
        files["$dir/$name"] = bytes
        clock++
    }

    fun exists(path: String): Boolean = path in files || path in dirs

    fun remove(path: String) {
        files.remove(path)
        dirs.remove(path)
    }

    override suspend fun list(path: String?, offset: Int, limit: Int): FileListing {
        if (path.isNullOrBlank()) {
            return FileListing("", roots)
        }
        val prefix = if (path.endsWith("/")) path else "$path/"
        val children = (files.keys + dirs)
            .filter { it.startsWith(prefix) && it != path && !it.substring(prefix.length).contains('/') }
            .sorted()
            .map { p -> entry(p) }
        return FileListing(path, children)
    }

    override suspend fun stat(path: String): RemoteEntry = entry(path)

    override suspend fun delete(paths: List<String>, recursive: Boolean) {
        for (p in paths) {
            files.keys.filter { it == p || it.startsWith("$p/") }.forEach { files.remove(it) }
            dirs.remove(p)
        }
    }

    override suspend fun rename(path: String, newName: String): RemoteEntry {
        val parent = path.substringBeforeLast('/')
        val target = "$parent/$newName"
        files[path]?.let { files[target] = it; files.remove(path) }
        return entry(target)
    }

    override suspend fun mkdir(path: String): RemoteEntry {
        dirs += path
        return entry(path)
    }

    override suspend fun open(path: String): FileHandle {
        val bytes = files[path] ?: ByteArray(0)
        return FileHandle(path, path.substringAfterLast('/'), bytes.size.toLong(), null, clock)
    }

    override suspend fun read(path: String, offset: Long, maxBytes: Int): ByteArray {
        val bytes = files[path] ?: return ByteArray(0)
        val start = offset.toInt().coerceIn(0, bytes.size)
        val end = (start + maxBytes).coerceIn(start, bytes.size)
        return bytes.copyOfRange(start, end)
    }

    override suspend fun size(path: String): Long? = files[path]?.size?.toLong()

    override suspend fun append(path: String, bytes: ByteArray) {
        val existing = files[path] ?: ByteArray(0)
        files[path] = existing + bytes
        clock++
    }

    override suspend fun prepareWriteTarget(path: String, truncateTo: Long): Long {
        val existing = files[path] ?: ByteArray(0)
        files[path] = existing.copyOf(truncateTo.toInt().coerceIn(0, existing.size))
        return files[path]!!.size.toLong()
    }

    override suspend fun sha256(path: String): String {
        val bytes = files[path] ?: ByteArray(0)
        return bytes.joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    }

    override suspend fun freeSpace(path: String): Long = Long.MAX_VALUE

    private fun entry(path: String): RemoteEntry {
        val bytes = files[path]
        return RemoteEntry(
            name = path.substringAfterLast('/'),
            path = path,
            directory = bytes == null,
            size = bytes?.size?.toLong() ?: 0,
            modifiedAt = clock,
        )
    }
}

/** Удалённая ФС поверх той же карты, что и локальная (упрощение для теста). */
private class InMemoryRemoteFs : SyncRemoteFs {
    private val fs = InMemoryFileSystem()

    fun addFile(dir: String, name: String, bytes: ByteArray) = fs.addFile(dir, name, bytes)
    fun exists(path: String): Boolean = fs.exists(path)

    override suspend fun list(path: String): List<RemoteEntry> = fs.list(path, 0, 0).entries
    override suspend fun mkdir(path: String): Boolean = runCatching { fs.mkdir(path); true }.getOrDefault(false)
    override suspend fun delete(paths: List<String>, recursive: Boolean): Boolean =
        runCatching { fs.delete(paths, recursive); true }.getOrDefault(false)
}

/** Записывает выполненные переносы и синхронизирует in-memory ФС. */
private class RecordingTransfer(
    private val localFs: InMemoryFileSystem,
    private val remoteFs: InMemoryRemoteFs,
) : SyncTransfer {

    val uploads = mutableListOf<Pair<String, String>>()
    val downloads = mutableListOf<Pair<String, String>>()

    override suspend fun upload(localPath: String, remotePath: String, overwrite: Boolean) {
        uploads += localPath to remotePath
        val bytes = localFs.read(localPath, 0, Int.MAX_VALUE)
        val dir = remotePath.substringBeforeLast('/')
        remoteFs.addFile(dir, remotePath.substringAfterLast('/'), bytes)
    }

    override suspend fun download(remotePath: String, localPath: String, overwrite: Boolean) {
        downloads += remotePath to localPath
        val bytes = remoteFs.list(remotePath.substringBeforeLast('/', "")).firstOrNull { it.path == remotePath }
        localFs.addFile(
            localPath.substringBeforeLast('/'),
            localPath.substringAfterLast('/'),
            ByteArray(bytes?.size?.toInt() ?: 0),
        )
    }
}
