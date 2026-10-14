package ru.efir.core.media

/**
 * Android в роли **хоста** поток не декодирует (он его кодирует и отправляет),
 * поэтому декодер видео на этой платформе не требуется.
 */
actual fun videoDecodingSupported(): Boolean = false

actual fun createVideoFrameDecoder(): VideoFrameDecoder? = null
