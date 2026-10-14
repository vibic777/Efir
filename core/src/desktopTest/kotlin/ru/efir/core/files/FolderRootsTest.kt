package ru.efir.core.files

import kotlinx.coroutines.test.runTest
import ru.efir.core.app.AppFolders
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Папки, которые открывает человек: добавленная в «Настройках» папка доступна сразу,
 * без перезапуска, а закрытая — сразу недоступна; за пределы открытых папок выйти нельзя.
 */
class FolderRootsTest {

    @Test
    fun rootsChangeWhileTheAppRuns() = runTest {
        val home = Files.createTempDirectory("efir-home").toFile()
        val otherDisk = Files.createTempDirectory("efir-other-disk").toFile()
        try {
            File(otherDisk, "Photos").mkdirs()
            File(otherDisk, "Photos/a.txt").writeText("photo")
            val allowed = mutableListOf(home.absolutePath)
            val fs = LocalFileSystem(rootsProvider = { allowed.toList() })

            // Папка на «другом диске» закрыта, пока её не добавили.
            assertFailsWith<IllegalArgumentException> { fs.list(File(otherDisk, "Photos").absolutePath) }
            assertEquals(1, fs.roots.size)

            allowed += otherDisk.absolutePath
            assertEquals(2, fs.roots.size)
            assertEquals(listOf("a.txt"), fs.list(File(otherDisk, "Photos").absolutePath).entries.map { it.name })
            // Запись в неё тоже разрешена: сюда можно скачивать и синхронизировать.
            fs.append(File(otherDisk, "Photos/b.txt").absolutePath, "new".toByteArray())
            assertTrue(File(otherDisk, "Photos/b.txt").isFile)

            // Папку закрыли — доступ пропадает сразу.
            allowed.remove(otherDisk.absolutePath)
            assertFailsWith<IllegalArgumentException> { fs.stat(File(otherDisk, "Photos/a.txt").absolutePath) }

            // Обход «..» из открытой папки по-прежнему не проходит.
            assertFailsWith<IllegalArgumentException> { fs.mkdir(home.absolutePath + "/../" + otherDisk.name + "/x") }
        } finally {
            home.deleteRecursively()
            otherDisk.deleteRecursively()
        }
    }

    @Test
    fun localFoldersAreListedAndCreated() {
        val dir = Files.createTempDirectory("efir-pick").toFile()
        try {
            File(dir, "b").mkdirs()
            File(dir, "A").mkdirs()
            File(dir, "file.txt").writeText("x")
            // Только папки, по алфавиту без учёта регистра, пути через «/».
            val found = listLocalFolders(dir.absolutePath)
            assertEquals(listOf("A", "b"), found.map { it.substringAfterLast('/') })
            assertTrue(found.none { '\\' in it })

            assertTrue(makeLocalFolder(File(dir, "new/deep").absolutePath))
            assertTrue(File(dir, "new/deep").isDirectory)
            val startedAt = System.currentTimeMillis()
            val roots = localBrowseRoots()
            println("browse roots: $roots in ${System.currentTimeMillis() - startedAt} ms")
            assertTrue(roots.isNotEmpty())
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun pathsAreCleaned() {
        assertEquals("D:/Photos", AppFolders.clean(" D:\\Photos\\ "))
        assertEquals("D:/", AppFolders.clean("D:\\"))
        assertEquals("D:/", AppFolders.clean("D:"))
        assertEquals("/", AppFolders.clean("/"))
        assertEquals("/storage/emulated/0/Efir", AppFolders.clean("/storage/emulated/0/Efir/"))
        assertNull(AppFolders.clean("   "))
    }
}