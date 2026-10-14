package ru.efir.core.sync

import ru.efir.core.protocol.SyncRuleConfig
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** Проверяет include/exclude-маски, ограничения по размеру и временные файлы. */
class PathFilterTest {

    @Test
    fun acceptsEverythingByDefault() {
        assertTrue(PathFilter.ACCEPT_ALL.accepts("dir/file.txt"))
    }

    @Test
    fun excludesTemporaryFiles() {
        assertFalse(PathFilter.ACCEPT_ALL.accepts("file.tmp"))
        assertFalse(PathFilter.ACCEPT_ALL.accepts("file.efirpart"))
        assertFalse(PathFilter.ACCEPT_ALL.accepts(".DS_Store"))
    }

    @Test
    fun includePatternsRestrictFiles() {
        val filter = PathFilter.from(rule(include = listOf("**/*.jpg", "**/*.png")))
        assertTrue(filter.accepts("photos/a.jpg"))
        assertTrue(filter.accepts("photos/a.png"))
        assertFalse(filter.accepts("photos/a.txt"))
    }

    @Test
    fun excludePatternsWin() {
        val filter = PathFilter.from(rule(include = listOf("**/*"), exclude = listOf("**/node_modules/**")))
        assertFalse(filter.accepts("app/node_modules/lib.js"))
        assertTrue(filter.accepts("app/src/main.kt"))
    }

    @Test
    fun nestedExcludeDirectory() {
        val filter = PathFilter.from(rule(exclude = listOf("**/.git/**")))
        assertTrue(filter.excludesDirectory(".git"))
        assertTrue(filter.excludesDirectory("repo/.git"))
        assertFalse(filter.accepts("repo/.git/config"))
    }

    @Test
    fun sizeLimitsApplyToFilesOnly() {
        val filter = PathFilter.from(rule(minSize = 100, maxSize = 1000))
        assertFalse(filter.accepts("small.bin", directory = false, size = 10))
        assertFalse(filter.accepts("big.bin", directory = false, size = 5000))
        assertTrue(filter.accepts("ok.bin", directory = false, size = 500))
        // Каталоги размером не ограничиваются.
        assertTrue(filter.accepts("dir", directory = true, size = 0))
    }

    private fun rule(
        include: List<String> = emptyList(),
        exclude: List<String> = emptyList(),
        minSize: Long? = null,
        maxSize: Long? = null,
    ) = SyncRuleConfig(
        id = "r",
        name = "r",
        localPath = "/local",
        remotePath = "/remote",
        includePatterns = include,
        excludePatterns = exclude,
        minFileSize = minSize,
        maxFileSize = maxSize,
    )
}
