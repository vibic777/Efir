package ru.efir.core.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.util.Locale
import java.util.Properties

private val home: File get() = File(System.getProperty("user.home") ?: ".")

private fun File.unixPath(): String = absolutePath.replace('\\', '/')

actual fun defaultDownloadDir(): String = File(File(home, "Downloads"), "Efir").unixPath()

actual fun defaultSyncLocalDir(): String = File(File(home, "Documents"), "Efir").unixPath()

actual val localFilePickerSupported: Boolean = true

actual suspend fun pickLocalFiles(): List<String> = withContext(Dispatchers.IO) {
    val dialog = FileDialog(null as Frame?, "Efir", FileDialog.LOAD)
    dialog.isMultipleMode = true
    dialog.isVisible = true
    val files = dialog.files.map { it.unixPath() }
    dialog.dispose()
    files
}

actual fun openLocalFolder(path: String): Boolean = runCatching {
    val dir = File(path)
    dir.mkdirs()
    Desktop.getDesktop().open(dir)
}.isSuccess

private val settingsFile: File get() = File(File(home, ".efir"), "settings.properties")

private fun readSettings(): Properties = Properties().apply {
    runCatching { if (settingsFile.exists()) settingsFile.inputStream().use { load(it) } }
}

actual fun loadPreference(key: String): String? = readSettings().getProperty(key)

actual fun savePreference(key: String, value: String) {
    runCatching {
        val properties = readSettings()
        properties.setProperty(key, value)
        settingsFile.parentFile?.mkdirs()
        settingsFile.outputStream().use { properties.store(it, "Efir") }
    }
}

actual fun systemLanguageCode(): String = Locale.getDefault().language

actual fun platformPermissions(): List<PermissionItem> = emptyList()

actual fun revokeAllPermissions() {}

actual val revokeAllClosesApp: Boolean = false

actual fun exportLog(text: String): String? = runCatching {
    val dir = File(AppFolders.downloadDir).apply { mkdirs() }
    val stamp = java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"))
    val file = File(dir, "efir-log-$stamp.txt")
    file.writeText(text)
    openLocalFolder(dir.absolutePath)
    file.absolutePath.replace('\\', '/')
}.getOrNull()
