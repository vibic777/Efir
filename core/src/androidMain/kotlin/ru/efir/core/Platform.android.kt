package ru.efir.core

import android.os.Build

actual fun currentPlatform(): PlatformInfo =
    PlatformInfo(
        name = "Android",
        version = Build.VERSION.RELEASE ?: "?",
    )
