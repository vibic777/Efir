package ru.efir.core.input

import ru.efir.core.protocol.KeyAction
import ru.efir.core.protocol.TouchStroke

/**
 * Прямоугольник области устройства в пикселях физического экрана.
 * Используется для преобразования нормализованных координат.
 */
data class ScreenGeometry(
    val width: Int,
    val height: Int,
) {
    init {
        require(width > 0 && height > 0) { "Размеры области должны быть положительными: ${width}x$height" }
    }

    /** Преобразует нормализованную координату X (0..1) в пиксель. */
    fun mapX(normalized: Float): Float = (normalized.coerceIn(0f, 1f) * width)

    /** Преобразует нормализованную координату Y (0..1) в пиксель. */
    fun mapY(normalized: Float): Float = (normalized.coerceIn(0f, 1f) * height)
}

/**
 * Платформенный инжектор ввода.
 *
 * Android: реализуется поверх `AccessibilityService` (`dispatchGesture`,
 * `GLOBAL_ACTION_*`, `ACTION_SET_TEXT`).
 * Desktop: не поддерживается.
 */
interface InputInjector {

    /** Готов ли инжектор принимать ввод (служба включена/разрешена). */
    val isReady: Boolean

    /** Обновляет целевую геометрию экрана (после смены ориентации/разрешения). */
    fun updateGeometry(geometry: ScreenGeometry)

    /** Одиночный тап или долгое нажатие. Координаты — пиксели устройства. */
    suspend fun tap(x: Float, y: Float, durationMs: Long)

    /** Свайп/перетаскивание. */
    suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long)

    /** Мультитач-жест из нескольких штрихов. */
    suspend fun multiTouch(strokes: List<TouchStroke>)

    /** Ввод текста в сфокусированное поле. */
    suspend fun typeText(text: String)

    /** Системная/аппаратная клавиша. */
    suspend fun pressKey(action: KeyAction)
}

/** Поддерживает ли текущая платформа инъекцию ввода. */
expect fun inputInjectionSupported(): Boolean

/**
 * Создаёт платформенный инжектор ввода.
 * На платформах без поддержки возвращается no-op реализация с [InputInjector.isReady] = false.
 */
expect fun createInputInjector(): InputInjector

/**
 * No-op инжектор для платформ без поддержки ввода (например, Desktop).
 * Позволяет общему коду не проверять поддержку на каждом вызове.
 */
class NoOpInputInjector : InputInjector {
    override val isReady: Boolean = false
    override fun updateGeometry(geometry: ScreenGeometry) = Unit
    override suspend fun tap(x: Float, y: Float, durationMs: Long) = Unit
    override suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) = Unit
    override suspend fun multiTouch(strokes: List<TouchStroke>) = Unit
    override suspend fun typeText(text: String) = Unit
    override suspend fun pressKey(action: KeyAction) = Unit
}
