package ru.efir.core.media

import androidx.compose.runtime.mutableStateOf
import ru.efir.core.app.loadPreference
import ru.efir.core.app.savePreference

/**
 * Коды причин, по которым показ экрана не начался или закончился. По сети идёт
 * код, а не фраза: каждая сторона показывает причину на своём языке
 * (`Strings.streamError`).
 */
object StreamErrors {
    /** На телефоне нужно разрешить показ экрана, а на запрос никто не ответил. */
    const val PERMISSION = "screen_permission_needed"

    /** Человек у другого устройства отказал. */
    const val REFUSED = "screen_refused"

    /** Показ экрана выключен в настройках компьютера. */
    const val DISABLED = "screen_disabled"

    /** Устройство не умеет показывать экран. */
    const val UNAVAILABLE = "screen_unavailable"

    /** Камера или звук на устройстве недоступны или не разрешены. */
    const val MEDIA_UNAVAILABLE = "media_unavailable"

    /** Показ остановили на самом устройстве. */
    const val STOPPED = "screen_stopped"

    /** Другое устройство не ответило вовремя. */
    const val NO_ANSWER = "screen_no_answer"

    /** Сбой захвата; подробности — в журнале показывающего устройства. */
    const val FAILED = "screen_failed"

    /** Сколько ждать ответа на просьбу показать экран: человеку нужно успеть нажать «Разрешить» на телефоне. */
    const val START_TIMEOUT_MS = 60_000L

    /** Сколько телефон ждёт ответа человека; меньше [START_TIMEOUT_MS], чтобы причина успела дойти. */
    const val PERMISSION_WAIT_MS = 45_000L
}

/** Показ не начался по известной причине ([code] из [StreamErrors]). */
class StreamRefused(val code: String, detail: String? = null) : Exception(detail ?: code)

/**
 * Что компьютер позволяет другому устройству со своим экраном («Настройки» →
 * «Показ экрана»). На телефоне то же самое решают разрешения Android, поэтому
 * там эти переключатели не действуют.
 *
 * Показ включён, пока его не выключат: о начале показа компьютер сообщает
 * уведомлением и значком в верхней панели, остановить можно в любой момент.
 * Управление мышью и клавиатурой выключено, пока его не включат.
 */
object ScreenSharing {
    const val PREF_SHOW = "shareScreen"
    const val PREF_CONTROL = "allowControl"

    private val show = mutableStateOf(true)
    private val control = mutableStateOf(false)

    @Volatile
    private var loaded = false

    private fun ensureLoaded() {
        if (loaded) return
        loaded = true
        show.value = loadPreference(PREF_SHOW) != "false"
        control.value = loadPreference(PREF_CONTROL) == "true"
    }

    val showAllowed: Boolean
        get() {
            ensureLoaded()
            return show.value
        }

    fun allowShow(on: Boolean) {
        ensureLoaded()
        show.value = on
        savePreference(PREF_SHOW, on.toString())
    }

    val controlAllowed: Boolean
        get() {
            ensureLoaded()
            return control.value
        }

    fun allowControl(on: Boolean) {
        ensureLoaded()
        control.value = on
        savePreference(PREF_CONTROL, on.toString())
    }
}
