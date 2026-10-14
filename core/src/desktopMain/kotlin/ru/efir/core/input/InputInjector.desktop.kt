package ru.efir.core.input

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.efir.core.media.ScreenSharing
import ru.efir.core.media.primaryScreenBounds
import ru.efir.core.protocol.KeyAction
import ru.efir.core.protocol.TouchStroke
import java.awt.GraphicsEnvironment
import java.awt.Robot
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.event.InputEvent
import java.awt.event.KeyEvent

/** Управлять компьютером можно, если есть экран: разрешает это человек в «Настройках». */
actual fun inputInjectionSupported(): Boolean = !GraphicsEnvironment.isHeadless()

actual fun createInputInjector(): InputInjector =
    if (GraphicsEnvironment.isHeadless()) NoOpInputInjector() else DesktopInputInjector()

/** Нажатие дольше этого считается долгим — на компьютере это правая кнопка мыши. */
private const val LONG_PRESS_MS = 450L

/**
 * Мышь и клавиатура компьютера по командам другого устройства (`java.awt.Robot`).
 * Нажатие по картинке — щелчок левой кнопкой, долгое — правой, проведение —
 * перетаскивание с зажатой левой кнопкой, текст вставляется через буфер обмена.
 *
 * Работает, только пока в «Настройках» → «Показ экрана» включено «Разрешать
 * управление этим компьютером» ([ScreenSharing.controlAllowed]); по умолчанию выключено.
 */
private class DesktopInputInjector : InputInjector {

    private val robot: Robot? by lazy { runCatching { Robot() }.getOrNull() }

    override val isReady: Boolean get() = ScreenSharing.controlAllowed && robot != null

    override fun updateGeometry(geometry: ScreenGeometry) = Unit

    /** Координаты приходят в точках основного экрана; у него может быть сдвиг относительно начала рабочего стола. */
    private fun move(robot: Robot, x: Float, y: Float) {
        val bounds = primaryScreenBounds()
        robot.mouseMove((bounds?.x ?: 0) + x.toInt(), (bounds?.y ?: 0) + y.toInt())
    }

    override suspend fun tap(x: Float, y: Float, durationMs: Long): Unit = withContext(Dispatchers.IO) {
        val robot = robot ?: return@withContext
        val button = if (durationMs >= LONG_PRESS_MS) InputEvent.BUTTON3_DOWN_MASK else InputEvent.BUTTON1_DOWN_MASK
        move(robot, x, y)
        robot.mousePress(button)
        delay(30)
        robot.mouseRelease(button)
    }

    override suspend fun swipe(fromX: Float, fromY: Float, toX: Float, toY: Float, durationMs: Long): Unit =
        withContext(Dispatchers.IO) {
            val robot = robot ?: return@withContext
            val steps = 12
            move(robot, fromX, fromY)
            robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            try {
                for (step in 1..steps) {
                    delay((durationMs / steps).coerceIn(5, 40))
                    val part = step.toFloat() / steps
                    move(robot, fromX + (toX - fromX) * part, fromY + (toY - fromY) * part)
                }
            } finally {
                // Кнопка отпускается и при отмене: иначе мышь осталась бы «зажатой».
                robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)
            }
        }

    /** Жестов двумя пальцами у мыши нет. */
    override suspend fun multiTouch(strokes: List<TouchStroke>) = Unit

    override suspend fun typeText(text: String): Unit = withContext(Dispatchers.IO) {
        val robot = robot ?: return@withContext
        if (text.isEmpty()) return@withContext
        // Вставка через буфер: набор по клавишам зависел бы от раскладки.
        Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)
        delay(60)
        robot.keyPress(KeyEvent.VK_CONTROL)
        try {
            robot.keyPress(KeyEvent.VK_V)
            robot.keyRelease(KeyEvent.VK_V)
        } finally {
            robot.keyRelease(KeyEvent.VK_CONTROL)
        }
    }

    override suspend fun pressKey(action: KeyAction): Unit = withContext(Dispatchers.IO) {
        val robot = robot ?: return@withContext
        // Кнопки телефона на компьютере: «Назад» — Esc, остальным соответствия нет.
        if (action == KeyAction.BACK) {
            robot.keyPress(KeyEvent.VK_ESCAPE)
            robot.keyRelease(KeyEvent.VK_ESCAPE)
        }
    }
}
