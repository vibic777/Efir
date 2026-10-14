package ru.efir.core.comms

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/** Источник изменения записи буфера обмена. */
enum class ClipboardOrigin { LOCAL, REMOTE }

/**
 * Запись истории буфера обмена.
 *
 * @param text содержимое.
 * @param origin откуда пришло значение.
 * @param timestamp время (epoch millis).
 * @param pinned закреплена ли запись (не вытесняется).
 */
data class ClipboardEntry(
    val text: String,
    val origin: ClipboardOrigin,
    val timestamp: Long,
    val pinned: Boolean = false,
)

/** Платформенный доступ к системному буферу обмена (текст). */
interface LocalClipboard {
    /** Читает текущий текст или `null`, если пусто/недоступно. */
    fun getText(): String?

    /** Записывает текст в системный буфер. */
    fun setText(text: String)
}

/** Фабрика платформенного буфера (desktop — AWT, android — ClipboardManager). */
expect fun createLocalClipboard(): LocalClipboard

/**
 * Синхронизация буфера обмена с историей и подавлением эха.
 *
 * Периодически опрашивает локальный буфер; при изменении (не вызванном [applyRemote])
 * публикует значение в [incoming] — это сигнал отправить `clipboardEvent` удалённой
 * стороне. Применение удалённого значения через [applyRemote] помечается как
 * «подавленное», чтобы опрос не отправил его обратно (защита от цикла).
 *
 * Работает на любой платформе; опрос выбран, т.к. надёжные слушатели буфера
 * недоступны в фоне на Android 10+ и не кроссплатформенны.
 */
class ClipboardSync(
    private val clipboard: LocalClipboard,
    private val scope: CoroutineScope,
    private val pollIntervalMs: Long = DEFAULT_POLL_MS,
    private val maxHistory: Int = DEFAULT_MAX_HISTORY,
) {

    private val _history = MutableStateFlow<List<ClipboardEntry>>(emptyList())

    /** История буфера (новые — в начале). */
    val history: StateFlow<List<ClipboardEntry>> = _history.asStateFlow()

    private val _incoming = MutableSharedFlow<String>(extraBufferCapacity = 32)

    /** Локальные изменения буфера, которые нужно отправить удалённой стороне. */
    val incoming: SharedFlow<String> = _incoming.asSharedFlow()

    /** Последнее известное значение (для запроса/проверки). */
    var lastValue: String? = null
        private set

    /** Значение, установленное от удалённой стороны (подавляем эхо один раз). */
    private var suppressValue: String? = null

    private var started = false

    /** Запускает фоновый опрос локального буфера. */
    fun start() {
        if (started) return
        started = true
        lastValue = runCatching { clipboard.getText() }.getOrNull()?.also { seedHistory(it) }
        scope.launch {
            while (isActive) {
                delay(pollIntervalMs)
                pollOnce()
            }
        }
    }

    /** Один цикл опроса (виден для тестов). */
    fun pollOnce() {
        val value = runCatching { clipboard.getText() }.getOrNull() ?: return
        if (value == lastValue) return
        lastValue = value
        if (value == suppressValue) {
            // Это эхо применённого удалённого значения — не отправляем обратно.
            suppressValue = null
            return
        }
        addHistory(value, ClipboardOrigin.LOCAL)
        _incoming.tryEmit(value)
    }

    /** Применяет значение, полученное от удалённой стороны. */
    fun applyRemote(text: String) {
        if (text == lastValue) return
        suppressValue = text
        lastValue = text
        runCatching { clipboard.setText(text) }
        addHistory(text, ClipboardOrigin.REMOTE)
    }

    /** Текущее содержимое буфера (или последнее известное). */
    fun current(): String? = runCatching { clipboard.getText() }.getOrNull() ?: lastValue

    /**
     * Пишет [text] в системный буфер, не считая это локальным изменением
     * (подавляем эхо, чтобы не отправить его удалённой стороне).
     */
    fun copyLocal(text: String) {
        suppressValue = text
        lastValue = text
        runCatching { clipboard.setText(text) }
        addHistory(text, ClipboardOrigin.LOCAL)
    }

    /** Закрепляет/снимает закрепление записи истории. */
    fun togglePinned(timestamp: Long) {
        _history.value = _history.value.map {
            if (it.timestamp == timestamp) it.copy(pinned = !it.pinned) else it
        }
    }

    /** Очищает историю (закреплённые записи сохраняются). */
    fun clearHistory() {
        _history.value = _history.value.filter { it.pinned }
    }

    private fun seedHistory(text: String) {
        if (text.isBlank()) return
        _history.value = listOf(ClipboardEntry(text, ClipboardOrigin.LOCAL, now()))
    }

    private fun addHistory(text: String, origin: ClipboardOrigin) {
        if (text.isBlank()) return
        val entry = ClipboardEntry(text, origin, now())
        val existing = _history.value
        val withoutDuplicate = existing.filterNot { it.text == text }
        val trimmed = enforceLimit(listOf(entry) + withoutDuplicate)
        _history.value = trimmed
    }

    private fun enforceLimit(entries: List<ClipboardEntry>): List<ClipboardEntry> {
        if (entries.size <= maxHistory) return entries
        val pinned = entries.filter { it.pinned }
        val unpinned = entries.filterNot { it.pinned }.take((maxHistory - pinned.size).coerceAtLeast(0))
        return (pinned + unpinned).sortedByDescending { it.timestamp }
    }

    private fun now(): Long = Clock.System.now().toEpochMilliseconds()

    companion object {
        const val DEFAULT_POLL_MS = 1_500L
        const val DEFAULT_MAX_HISTORY = 50
    }
}
