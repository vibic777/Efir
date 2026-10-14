package ru.efir.core

actual fun currentPlatform(): PlatformInfo =
    PlatformInfo(
        name = "Desktop",
        version = System.getProperty("os.name") ?: "?",
    )
