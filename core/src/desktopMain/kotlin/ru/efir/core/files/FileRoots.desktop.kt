package ru.efir.core.files

import java.io.File

actual fun defaultFileRoots(): List<String> {
    val home = System.getProperty("user.home") ?: "."
    return listOf(File(home).absolutePath)
}

actual fun protectedFilePaths(): List<String> {
    val home = System.getProperty("user.home") ?: "."
    return listOf(File(home, ".efir").absolutePath, File(home, ".mind").absolutePath)
}

actual fun localBrowseRoots(): List<String> {
    val home = File(System.getProperty("user.home") ?: ".").absolutePath
    // Домашняя папка и все диски (на Linux — «/»).
    val drives = File.listRoots().orEmpty().filter { runCatching { it.exists() }.getOrDefault(false) }.map { it.absolutePath }
    return (listOf(home) + drives).map { it.replace('\\', '/') }.distinct()
}
