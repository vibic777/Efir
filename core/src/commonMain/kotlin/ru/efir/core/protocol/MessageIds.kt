package ru.efir.core.protocol

import kotlinx.datetime.Clock
import kotlin.random.Random

/** Генератор уникальных идентификаторов сообщений (timestamp + случайные байты). */
object MessageIds {

    private const val HEX = "0123456789abcdef"

    /** Возвращает новый идентификатор длиной 24 символа (hex). */
    fun new(): String = buildString(24) {
        val ts = Clock.System.now().toEpochMilliseconds()
        // Младшие 48 бит времени — обеспечивает монотонность префикса.
        for (i in 0 until 12) {
            append(HEX[((ts shr (i * 4)) and 0xF).toInt()])
        }
        // 48 бит случайности — уникальность в пределах одной миллисекунды.
        repeat(12) {
            append(HEX[Random.nextInt(16)])
        }
    }
}

/** Текущее время в epoch millis. */
internal fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()
