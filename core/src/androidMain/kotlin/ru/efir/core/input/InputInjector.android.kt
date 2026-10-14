package ru.efir.core.input

import ru.efir.core.protocol.KeyAction
import ru.efir.core.protocol.TouchPoint
import ru.efir.core.protocol.TouchStroke

actual fun inputInjectionSupported(): Boolean = true

actual fun createInputInjector(): InputInjector = AndroidInputInjector()

/**
 * Android-инжектор ввода поверх [EfirAccessibilityService].
 *
 * Жесты строятся как [TouchStroke] и передаются службе (`dispatchGesture`),
 * системные клавиши — через `GLOBAL_ACTION_*`, текст — через `ACTION_SET_TEXT`.
 */
internal class AndroidInputInjector : InputInjector {

    override val isReady: Boolean get() = EfirAccessibilityService.isConnected

    private var geometry: ScreenGeometry? = null

    override fun updateGeometry(geometry: ScreenGeometry) {
        this.geometry = geometry
    }

    override suspend fun tap(x: Float, y: Float, durationMs: Long) {
        val service = EfirAccessibilityService.instance ?: return
        service.performStrokes(
            listOf(
                TouchStroke(
                    points = listOf(TouchPoint(x, y)),
                    startDelayMs = 0,
                    durationMs = durationMs.coerceAtLeast(1),
                ),
            ),
        )
    }

    override suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long) {
        val service = EfirAccessibilityService.instance ?: return
        // Линейная интерполяция промежуточных точек для плавного свайпа.
        val steps = SWIPE_STEPS
        val points = (0..steps).map { i ->
            val t = i.toFloat() / steps
            TouchPoint(fromX + (toX - fromX) * t, fromY + (toY - fromY) * t)
        }
        service.performStrokes(
            listOf(
                TouchStroke(
                    points = points,
                    startDelayMs = 0,
                    durationMs = durationMs.coerceAtLeast(1),
                ),
            ),
        )
    }

    override suspend fun multiTouch(strokes: List<TouchStroke>) {
        EfirAccessibilityService.instance?.performStrokes(strokes)
    }

    override suspend fun typeText(text: String) {
        EfirAccessibilityService.instance?.appendFocusedText(text)
    }

    override suspend fun pressKey(action: KeyAction) {
        val service = EfirAccessibilityService.instance ?: return
        when (action) {
            KeyAction.BACK -> service.performGlobal(EfirAccessibilityService.GLOBAL_BACK)
            KeyAction.HOME -> service.performGlobal(EfirAccessibilityService.GLOBAL_HOME)
            KeyAction.RECENTS -> service.performGlobal(EfirAccessibilityService.GLOBAL_RECENTS)
            KeyAction.APP_SWITCH -> service.performGlobal(EfirAccessibilityService.GLOBAL_RECENTS)
            KeyAction.NOTIFICATIONS -> service.performGlobal(EfirAccessibilityService.GLOBAL_NOTIFICATIONS)
            KeyAction.ENTER -> service.appendFocusedText("\n")
            KeyAction.TAB -> service.appendFocusedText("\t")
            KeyAction.DELETE -> service.deleteLastChar()
            // Требуют root/спец. разрешений — без root недоступны.
            KeyAction.ESCAPE,
            KeyAction.DPAD_UP,
            KeyAction.DPAD_DOWN,
            KeyAction.DPAD_LEFT,
            KeyAction.DPAD_RIGHT,
            KeyAction.VOLUME_UP,
            KeyAction.VOLUME_DOWN,
            KeyAction.POWER,
            -> Unit
        }
    }

    private companion object {
        const val SWIPE_STEPS = 12
    }
}
