package ru.efir.core.sync

import kotlinx.coroutines.test.runTest
import ru.efir.core.files.FileHandle
import ru.efir.core.files.FileListing
import ru.efir.core.files.FileSystem
import ru.efir.core.protocol.RemoteEntry
import ru.efir.core.protocol.SyncSide
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Проверяет бэкап и восстановление версий файлов. */
class SyncVersionStoreTest {

    private class MemFs : FileSystem {
        val files = mutableMapOf<String, ByteArray>()

        override val roots: List<RemoteEntry> get() = emptyList()

        override suspend fun list(path: String?, offset: Int, limit: Int): FileListing = FileListing("", emptyList())
        override suspend fun stat(path: String): RemoteEntry =
            RemoteEntry(path.substringAfterLast('/'), path, false, size(path) ?: 0, 0)

        override suspend fun delete(paths: List<String>, recursive: Boolean) {
            paths.forEach { files.remove(it) }
        }

        override suspend fun rename(path: String, newName: String): RemoteEntry = stat(path)

        override suspend fun mkdir(path: String): RemoteEntry = stat(path)

        override suspend fun open(path: String): FileHandle =
            FileHandle(path, path.substringAfterLast('/'), size(path) ?: 0, null, 0)

        override suspend fun read(path: String, offset: Long, maxBytes: Int): ByteArray {
            val bytes = files[path] ?: return ByteArray(0)
            val start = offset.toInt().coerceIn(0, bytes.size)
            val end = (start + maxBytes).coerceIn(start, bytes.size)
            return bytes.copyOfRange(start, end)
        }

        override suspend fun size(path: String): Long? = files[path]?.size?.toLong()

        override suspend fun append(path: String, bytes: ByteArray) {
            files[path] = (files[path] ?: ByteArray(0)) + bytes
        }

        override suspend fun prepareWriteTarget(path: String, truncateTo: Long): Long {
            val existing = files[path] ?: ByteArray(0)
            files[path] = existing.copyOf(truncateTo.toInt().coerceIn(0, existing.size))
            return files[path]!!.size.toLong()
        }

        override suspend fun sha256(path: String): String =
            (files[path] ?: ByteArray(0)).joinToString("") { it.toUByte().toString(16).padStart(2, '0') }

        override suspend fun freeSpace(path: String): Long = Long.MAX_VALUE
    }

    @Test
    fun backupThenRestoreRoundTrip() = runTest {
        val fs = MemFs()
        fs.files["/local/a.txt"] = "original".encodeToByteArray()
        val store = InMemorySyncVersionStore()

        val version = store.backup(
            ruleId = "r",
            relativePath = "a.txt",
            side = SyncSide.LOCAL,
            sourcePath = "/local/a.txt",
            fileSystem = fs,
        )
        assertNotNull(version)
        assertTrue(fs.files.containsKey(version.backupPath))

        // Меняем файл и восстанавливаем.
        fs.files["/local/a.txt"] = "changed".encodeToByteArray()
        val restored = store.restore(version, "/local/a.txt", fs)
        assertTrue(restored)
        assertEquals("original", fs.files["/local/a.txt"]!!.decodeToString())
    }

    @Test
    fun historyKeepsEntriesNewestFirst() = runTest {
        val fs = MemFs()
        fs.files["/local/b.txt"] = "v1".encodeToByteArray()
        val store = InMemorySyncVersionStore()

        store.backup("r", "b.txt", SyncSide.LOCAL, "/local/b.txt", fs)
        fs.files["/local/b.txt"] = "v2".encodeToByteArray()
        store.backup("r", "b.txt", SyncSide.LOCAL, "/local/b.txt", fs)

        assertEquals(2, store.history("r", "b.txt").size)
        // Последняя версия — первой.
        assertTrue(store.history("r", "b.txt").first().timestamp >= store.history("r", "b.txt").last().timestamp)
    }

    @Test
    fun backupMissingFileReturnsNull() = runTest {
        val fs = MemFs()
        val store = InMemorySyncVersionStore()
        assertEquals(null, store.backup("r", "none.txt", SyncSide.LOCAL, "/local/none.txt", fs))
    }
}
