package ru.efir.core.comms

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Проверяет подавление эха, историю и закрепление записей буфера. */
class ClipboardSyncTest {

    /** Управляемый буфер для теста. */
    private class FakeClipboard : LocalClipboard {
        var value: String? = null
        override fun getText(): String? = value
        override fun setText(text: String) {
            value = text
        }
    }

    @Test
    fun detectsLocalChangeAndEmits() = runTest {
        val clipboard = FakeClipboard()
        val sync = ClipboardSync(clipboard, CoroutineScope(coroutineContext))
        val received = mutableListOf<String>()
        val job = launch { sync.incoming.collect { received.add(it) } }
        // Даём коллектору подписаться до эмиссии.
        testScheduler.runCurrent()

        clipboard.value = "hello"
        sync.pollOnce()
        testScheduler.runCurrent()

        assertEquals(listOf("hello"), received)
        assertEquals("hello", sync.lastValue)
        job.cancel()
    }

    @Test
    fun applyRemoteSuppressesEcho() = runTest {
        val clipboard = FakeClipboard()
        val sync = ClipboardSync(clipboard, CoroutineScope(coroutineContext))
        val received = mutableListOf<String>()
        val job = launch { sync.incoming.collect { received.add(it) } }

        sync.applyRemote("from-phone")
        testScheduler.runCurrent()
        assertEquals("from-phone", clipboard.value)

        // Опрос не должен повторно отправить применённое удалённое значение.
        sync.pollOnce()
        testScheduler.runCurrent()
        assertTrue(received.isEmpty(), "эхо удалённого значения не должно отправляться")

        // Новое локальное изменение снова отправляется.
        clipboard.value = "local"
        sync.pollOnce()
        testScheduler.runCurrent()
        assertEquals(listOf("local"), received)
        job.cancel()
    }

    @Test
    fun historyDeduplicatesAndKeepsPinned() = runTest {
        val clipboard = FakeClipboard()
        val sync = ClipboardSync(clipboard, CoroutineScope(coroutineContext), maxHistory = 3)

        sync.applyRemote("a")
        sync.applyRemote("b")
        sync.applyRemote("c")
        sync.applyRemote("a") // дубликат — переезжает вверх

        assertEquals("a", sync.history.value.first().text)
        assertEquals(3, sync.history.value.size)

        // Закрепляем вторую запись.
        val pinned = sync.history.value[1]
        sync.togglePinned(pinned.timestamp)
        assertTrue(sync.history.value.any { it.pinned && it.text == pinned.text })

        sync.clearHistory()
        assertTrue(sync.history.value.all { it.pinned })
    }

    @Test
    fun currentFallsBackToLastValue() = runTest {
        val clipboard = FakeClipboard()
        val sync = ClipboardSync(clipboard, CoroutineScope(coroutineContext))
        sync.applyRemote("x")
        assertEquals("x", sync.current())
        assertNull(FakeClipboard().value)
    }
}
