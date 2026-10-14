package ru.efir.core.input

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import ru.efir.core.protocol.KeyAction
import ru.efir.core.protocol.TouchStroke

/**
 * Служба доступности Efir: выполняет инъекцию жестов и ввод без root.
 *
 * Включается пользователем в системных настройках «Специальные возможности».
 * Пока служба активна, её экземпляр доступен статически через [instance];
 * [AndroidInputInjector] делегирует вызовы сюда.
 *
 * Возможности: `dispatchGesture` (тап/свайп/мультитач), `GLOBAL_ACTION_*`
 * (Back/Home/Recents/Notifications), `ACTION_SET_TEXT` (ввод текста).
 */
class EfirAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    /** Выполняет жест из одного или нескольких штрихов. Возвращает успех. */
    fun performStrokes(strokes: List<TouchStroke>): Boolean {
        if (strokes.isEmpty()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return false

        val builder = GestureDescription.Builder()
        for (stroke in strokes) {
            if (stroke.points.isEmpty()) continue
            val path = Path().apply {
                val first = stroke.points.first()
                moveTo(first.x, first.y)
                for (i in 1 until stroke.points.size) {
                    val p = stroke.points[i]
                    lineTo(p.x, p.y)
                }
            }
            builder.addStroke(
                GestureDescription.StrokeDescription(
                    path,
                    stroke.startDelayMs.coerceAtLeast(0),
                    stroke.durationMs.coerceAtLeast(1),
                ),
            )
        }
        val gesture = runCatching { builder.build() }.getOrNull() ?: return false
        return dispatchGesture(gesture, null, null)
    }

    /** Системное действие через `GLOBAL_ACTION_*`. */
    fun performGlobal(action: Int): Boolean = runCatching { performGlobalAction(action) }.getOrDefault(false)

    /**
     * Ввод текста в сфокусированное редактируемое поле через `ACTION_SET_TEXT`.
     * Если узел не поддерживает действие — пытается найти редактируемый узел.
     */
    fun setFocusedText(text: String): Boolean {
        val node = findEditableNode() ?: return false
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return ok
    }

    /** Дописывает текст к текущему содержимому сфокусированного поля. */
    fun appendFocusedText(text: String): Boolean {
        val node = findEditableNode() ?: return false
        val current = node.text?.toString().orEmpty()
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, current + text)
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return ok
    }

    /** Удаляет последний символ из сфокусированного поля. */
    fun deleteLastChar(): Boolean {
        val node = findEditableNode() ?: return false
        val current = node.text?.toString().orEmpty()
        if (current.isEmpty()) {
            node.recycle()
            return false
        }
        val args = Bundle().apply {
            putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                current.substring(0, current.length - 1),
            )
        }
        val ok = node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        node.recycle()
        return ok
    }

    private fun findEditableNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow ?: return null
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) return focused
        focused?.recycle()
        return findEditable(root)
    }

    private fun findEditable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val found = findEditable(child)
            if (found != null) {
                if (found !== child) child.recycle()
                return found
            }
            child.recycle()
        }
        return null
    }

    companion object {
        /** Активный экземпляр службы или `null`, если она выключена. */
        @Volatile
        var instance: EfirAccessibilityService? = null
            private set

        /** Доступна ли инъекция ввода (служба подключена). */
        val isConnected: Boolean get() = instance != null

        // Константы GLOBAL_ACTION_* (доступны с API 16/24/30 — значения стабильны).
        val GLOBAL_BACK: Int get() = AccessibilityService.GLOBAL_ACTION_BACK
        val GLOBAL_HOME: Int get() = AccessibilityService.GLOBAL_ACTION_HOME
        val GLOBAL_RECENTS: Int get() = AccessibilityService.GLOBAL_ACTION_RECENTS
        val GLOBAL_NOTIFICATIONS: Int get() = AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
    }
}
