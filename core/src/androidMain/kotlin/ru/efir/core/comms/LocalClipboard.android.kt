package ru.efir.core.comms

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import ru.efir.core.media.EfirAndroidEnvironment

/**
 * Буфер обмена на Android через [ClipboardManager].
 *
 * На Android 10+ чтение буфера в фоне ограничено — метод [getText] может
 * возвращать `null`, когда приложение/служба не в фокусе. Запись доступна всегда.
 */
private class AndroidLocalClipboard(private val context: Context) : LocalClipboard {

    private val manager: ClipboardManager?
        get() = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager

    override fun getText(): String? = runCatching {
        val clip = manager?.primaryClip ?: return null
        if (clip.itemCount == 0) return null
        clip.getItemAt(0).coerceToText(context)?.toString()
    }.getOrNull()

    override fun setText(text: String) {
        runCatching { manager?.setPrimaryClip(ClipData.newPlainText("Efir", text)) }
    }
}

actual fun createLocalClipboard(): LocalClipboard {
    val context = EfirAndroidEnvironment.appContext
        ?: error("EfirAndroidEnvironment.appContext не инициализирован")
    return AndroidLocalClipboard(context)
}
