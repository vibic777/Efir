package ru.efir.core.files

import java.io.File

actual fun listLocalFolders(path: String): List<String> = runCatching {
    File(path).listFiles { file -> file.isDirectory && !file.isHidden }
        .orEmpty()
        .sortedBy { it.name.lowercase() }
        .map { it.absolutePath.replace('\\', '/') }
}.getOrDefault(emptyList())

actual fun makeLocalFolder(path: String): Boolean = runCatching {
    val dir = File(path)
    dir.isDirectory || dir.mkdirs()
}.getOrDefault(false)
