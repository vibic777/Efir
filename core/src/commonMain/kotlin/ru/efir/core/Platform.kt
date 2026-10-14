package ru.efir.core

/**
 * Информация о платформе, на которой выполняется код.
 * Реализуется через expect/actual в androidMain и desktopMain.
 */
data class PlatformInfo(
    val name: String,
    val version: String,
)

expect fun currentPlatform(): PlatformInfo
