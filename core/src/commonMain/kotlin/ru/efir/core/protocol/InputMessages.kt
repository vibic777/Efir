package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Точка касания. Координаты нормализованы в диапазоне 0..1 относительно
 * захваченного экрана, что делает их независимыми от разрешения.
 */
@Serializable
data class TouchPoint(
    val x: Float,
    val y: Float,
)

/**
 * Один «штрих» жеста (последовательность точек одного пальца).
 *
 * @param startDelayMs задержка старта относительно начала жеста.
 * @param durationMs длительность прохождения штриха.
 */
@Serializable
data class TouchStroke(
    val points: List<TouchPoint>,
    val startDelayMs: Long = 0,
    val durationMs: Long,
)

/** Одиночный тап/долгое нажатие. */
@Serializable
@SerialName("inputTap")
data class InputTap(
    val x: Float,
    val y: Float,
    val durationMs: Long = 50,
) : RpcRequest

/** Свайп/перетаскивание. */
@Serializable
@SerialName("inputSwipe")
data class InputSwipe(
    val fromX: Float,
    val fromY: Float,
    val toX: Float,
    val toY: Float,
    val durationMs: Long,
) : RpcRequest

/** Мультитач-жест (пинч, вращение, несколько пальцев). */
@Serializable
@SerialName("inputMultiTouch")
data class InputMultiTouch(
    val strokes: List<TouchStroke>,
) : RpcRequest

/** Ввод текста в сфокусированное поле. */
@Serializable
@SerialName("inputText")
data class InputText(
    val text: String,
) : RpcRequest

/** Системные/аппаратные клавиши. */
@Serializable
enum class KeyAction {
    BACK,
    HOME,
    RECENTS,
    NOTIFICATIONS,
    ENTER,
    DELETE,
    TAB,
    ESCAPE,
    DPAD_UP,
    DPAD_DOWN,
    DPAD_LEFT,
    DPAD_RIGHT,
    VOLUME_UP,
    VOLUME_DOWN,
    POWER,
    APP_SWITCH,
}

/** Нажатие системной/аппаратной клавиши. */
@Serializable
@SerialName("inputKey")
data class InputKey(
    val action: KeyAction,
) : RpcRequest
