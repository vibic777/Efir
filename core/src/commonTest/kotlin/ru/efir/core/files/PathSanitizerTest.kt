package ru.efir.core.files

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PathSanitizerTest {

    @Test
    fun normalizesSeparatorsAndDots() {
        assertEquals("/a/b/c", PathSanitizer.normalize("/a//b/./c/"))
        assertEquals("a/b", PathSanitizer.normalize("a\\b"))
    }

    @Test
    fun collapsesParentReferences() {
        assertEquals("/a/c", PathSanitizer.normalize("/a/b/../c"))
        assertEquals("/a", PathSanitizer.normalize("/a/b/.."))
        // Нельзя выйти выше абсолютного корня.
        assertEquals("/", PathSanitizer.normalize("/../../.."))
    }

    @Test
    fun keepsLeadingParentForRelative() {
        assertEquals("../a", PathSanitizer.normalize("../a"))
        assertEquals("../../a", PathSanitizer.normalize("../../a"))
    }

    @Test
    fun detectsWithinRoot() {
        assertTrue(PathSanitizer.isWithin("/sdcard", "/sdcard/Download/file.txt"))
        assertTrue(PathSanitizer.isWithin("/sdcard", "/sdcard"))
        assertFalse(PathSanitizer.isWithin("/sdcard", "/other/file.txt"))
        // Границы сегментов: /sdcard2 не внутри /sdcard.
        assertFalse(PathSanitizer.isWithin("/sdcard", "/sdcard2/file"))
    }

    @Test
    fun findsMatchingRoot() {
        val roots = listOf("/sdcard", "/data/user/0/ru.efir")
        assertEquals("/sdcard", PathSanitizer.findRoot(roots, "/sdcard/Download"))
        assertNull(PathSanitizer.findRoot(roots, "/etc/passwd"))
    }

    @Test
    fun requireWithinRejectsEscape() {
        val error = runCatching {
            PathSanitizer.requireWithin(listOf("/sdcard"), "/sdcard/../etc/passwd")
        }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun buildsChildAndParent() {
        assertEquals("/a/b/c", PathSanitizer.child("/a/b", "c"))
        assertEquals("/a/b", PathSanitizer.parent("/a/b/c"))
        assertEquals("/a", PathSanitizer.parent("/a/b"))
        assertEquals("/", PathSanitizer.parent("/a"))
        assertEquals("", PathSanitizer.parent("a"))
    }

    @Test
    fun extractsName() {
        assertEquals("file.txt", PathSanitizer.name("/a/b/file.txt"))
        assertEquals("b", PathSanitizer.name("/a/b/"))
    }

    @Test
    fun validatesNames() {
        assertTrue(PathSanitizer.isValidName("photo.jpg"))
        assertFalse(PathSanitizer.isValidName(""))
        assertFalse(PathSanitizer.isValidName(".."))
        assertFalse(PathSanitizer.isValidName("a/b"))
        assertFalse(PathSanitizer.isValidName("a\\b"))
    }
}
