package ru.efir.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowScope
import com.jetbrains.JBR
import com.jetbrains.WindowDecorations
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive

/**
 * Рамка окна, как в Notio: верхняя панель приложения сама служит заголовком окна —
 * её цвет, знак и кнопки, — а «свернуть / развернуть / закрыть», привязка окон,
 * скруглённые углы и тень остаются системными. Отдельной полосы заголовка другого
 * цвета над приложением нет.
 *
 * Нужна среда JetBrains Runtime (с ней приложение упаковано). На любой другой Java
 * остаётся обычный системный заголовок, а панель под ним выглядит так же.
 */
class WindowChrome private constructor(private val titleBar: WindowDecorations.CustomTitleBar?) {
    val native: Boolean get() = titleBar != null

    /** Ширина справа, занятая системными кнопками. */
    val rightInset: Dp get() = (titleBar?.rightInset ?: 0f).dp

    /** Светлые значки системных кнопок на тёмной теме, тёмные — на светлой. */
    fun setDarkControls(dark: Boolean) {
        runCatching { titleBar?.putProperty("controls.dark", dark) }
    }

    /**
     * Нажатия на кнопки приложения в панели достаются приложению; в остальных местах
     * панель двигает окно, двойной щелчок разворачивает его, правый — открывает
     * системное меню, как у обычного заголовка.
     */
    fun hitTest(): Modifier {
        val bar = titleBar ?: return Modifier
        return Modifier.pointerInput(Unit) {
            val context = currentCoroutineContext()
            awaitPointerEventScope {
                var inControl = false
                while (context.isActive) {
                    val event = awaitPointerEvent(PointerEventPass.Main)
                    event.changes.forEach {
                        if (!it.isConsumed && !inControl) {
                            bar.forceHitTest(false)
                        } else {
                            if (event.type == PointerEventType.Press) inControl = true
                            if (event.type == PointerEventType.Release) inControl = false
                            bar.forceHitTest(true)
                        }
                    }
                }
            }
        }
    }

    companion object {
        /** Высота верхней панели приложения. */
        const val HEIGHT = 56

        fun install(window: java.awt.Frame): WindowChrome {
            val bar = runCatching {
                if (!JBR.isAvailable() || !JBR.isWindowDecorationsSupported()) return@runCatching null
                val decorations = JBR.getWindowDecorations()
                decorations.createCustomTitleBar().also {
                    it.height = HEIGHT.toFloat()
                    decorations.setCustomTitleBar(window, it)
                }
            }.getOrNull()
            return WindowChrome(bar)
        }
    }
}

/** Рамка этого окна — на всё время его жизни. */
@Composable
fun WindowScope.rememberWindowChrome(): WindowChrome {
    val frame = window as java.awt.Frame
    val chrome = remember(frame) { WindowChrome.install(frame) }
    // Ширина системных кнопок известна, только когда окно показано: её подхватывает перерисовка.
    var tick by remember { mutableFloatStateOf(0f) }
    DisposableEffect(frame) {
        val listener = object : java.awt.event.ComponentAdapter() {
            override fun componentResized(e: java.awt.event.ComponentEvent?) {
                tick = chrome.rightInset.value
            }

            override fun componentShown(e: java.awt.event.ComponentEvent?) {
                tick = chrome.rightInset.value
            }
        }
        frame.addComponentListener(listener)
        onDispose { frame.removeComponentListener(listener) }
    }
    @Suppress("UNUSED_EXPRESSION")
    tick
    return chrome
}
