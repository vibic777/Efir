package ru.efir.core.files

import kotlinx.coroutines.test.runTest
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LocalFileSystemTest {

    private lateinit var root: File
    private lateinit var fs: LocalFileSystem

    @BeforeTest
    fun setUp() {
        root = Files.createTempDirectory("efir-fs-test").toFile()
        File(root, "Documents").mkdirs()
        File(root, "Documents/notes.txt").writeText("hello efir")
        File(root, "photo.jpg").writeText("jpeg-bytes")
        fs = LocalFileSystem(rootPaths = listOf(root.absolutePath))
    }

    @AfterTest
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun exposesRoots() {
        assertEquals(1, fs.roots.size)
        assertTrue(fs.roots.first().directory)
    }

    @Test
    fun listsRootsWhenPathNull() = runTest {
        val listing = fs.list(null)
        assertEquals(1, listing.entries.size)
    }

    @Test
    fun listsDirectoryDirectoriesFirst() = runTest {
        val listing = fs.list(root.absolutePath)
        assertEquals(2, listing.entries.size)
        assertTrue(listing.entries.first().directory)
        assertEquals("Documents", listing.entries.first().name)
    }

    @Test
    fun statsFile() = runTest {
        val entry = fs.stat(root.resolve("photo.jpg").absolutePath)
        assertFalse(entry.directory)
        assertTrue(entry.size > 0)
    }

    @Test
    fun createsAndDeletesDirectory() = runTest {
        val target = root.resolve("NewDir").absolutePath
        val entry = fs.mkdir(target)
        assertTrue(entry.directory)
        assertTrue(java.io.File(target).isDirectory)

        fs.delete(listOf(target), recursive = true)
        assertFalse(java.io.File(target).exists())
    }

    @Test
    fun renamesWithinParent() = runTest {
        val original = root.resolve("photo.jpg").absolutePath
        val renamed = fs.rename(original, "photo2.jpg")
        assertEquals("photo2.jpg", renamed.name)
        assertFalse(java.io.File(original).exists())
        assertTrue(java.io.File(renamed.path).exists())
    }

    @Test
    fun rejectsPathOutsideRoots() = runTest {
        assertFailsWith<IllegalArgumentException> {
            fs.stat("/etc/passwd")
        }
        assertFailsWith<IllegalArgumentException> {
            fs.list(root.absolutePath + "/../../../etc")
        }
    }

    @Test
    fun rejectsDeletingNonEmptyDirectoryWithoutRecursive() = runTest {
        assertFailsWith<IllegalArgumentException> {
            fs.delete(listOf(root.resolve("Documents").absolutePath), recursive = false)
        }
    }

    @Test
    fun readsBlockWithOffset() = runTest {
        val path = root.resolve("Documents/notes.txt").absolutePath
        val handle = fs.open(path)
        assertEquals("notes.txt", handle.name)

        val first = fs.read(path, offset = 0, maxBytes = 5)
        assertEquals("hello", first.decodeToString())

        val rest = fs.read(path, offset = 5, maxBytes = 100)
        assertEquals(" efir", rest.decodeToString())
    }

    @Test
    fun rejectsInvalidRenameName() = runTest {
        val path = root.resolve("photo.jpg").absolutePath
        assertFailsWith<IllegalArgumentException> {
            fs.rename(path, "a/b.jpg")
        }
    }
}
