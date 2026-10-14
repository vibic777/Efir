package ru.efir.core.comms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import ru.efir.core.protocol.SmsDeliveryStatus
import ru.efir.core.protocol.SmsDeliveryStatusChanged
import ru.efir.core.protocol.SmsMessage
import ru.efir.core.protocol.SmsSubscription
import ru.efir.core.protocol.SmsThread
import ru.efir.core.protocol.SmsType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Проверяет backend SMS с in-memory репозиторием (без Android). */
class SmsControlTest {

    private class FakeSmsRepository : SmsRepository {
        override val supported: Boolean = true
        override val isDefaultSmsApp: Boolean = true
        val stored = mutableListOf<SmsMessage>()
        val sent = mutableListOf<Triple<String, String, Int?>>()
        var lastDeliveryReport: Boolean = false
        private val incomingFlow = MutableSharedFlow<SmsMessage>(extraBufferCapacity = 4)
        private val deliveryFlow = MutableSharedFlow<SmsDeliveryStatusChanged>(extraBufferCapacity = 4)

        override suspend fun list(threadId: Long?, limit: Int, offset: Int): List<SmsMessage> =
            stored.drop(offset).take(limit)

        override suspend fun subscriptions(): List<SmsSubscription> =
            listOf(
                SmsSubscription(subscriptionId = 1, slotIndex = 0, displayName = "SIM 1", isDefault = true),
                SmsSubscription(subscriptionId = 2, slotIndex = 1, displayName = "SIM 2"),
            )

        override suspend fun send(
            address: String,
            body: String,
            subscriptionId: Int?,
            deliveryReport: Boolean,
        ) {
            sent.add(Triple(address, body, subscriptionId))
            lastDeliveryReport = deliveryReport
        }

        override suspend fun markRead(id: Long) = Unit

        override fun incoming(): Flow<SmsMessage> = incomingFlow

        override fun deliveries(): Flow<SmsDeliveryStatusChanged> = deliveryFlow

        suspend fun emit(message: SmsMessage) {
            incomingFlow.emit(message)
        }

        suspend fun emitDelivery(status: SmsDeliveryStatusChanged) {
            deliveryFlow.emit(status)
        }
    }

    @Test
    fun listReturnsStoredMessages() = runTest {
        val repo = FakeSmsRepository()
        repo.stored.add(SmsMessage(1, 1, "+700", "hi", 100, SmsType.INBOX))
        assertEquals(1, repo.list(null, 10, 0).size)
    }

    @Test
    fun sendRecordsMessage() = runTest {
        val repo = FakeSmsRepository()
        repo.send("+7999", "test")
        assertEquals(1, repo.sent.size)
        assertEquals("+7999", repo.sent[0].first)
        assertNull(repo.sent[0].third)
    }

    @Test
    fun sendRecordsSubscriptionAndDelivery() = runTest {
        val repo = FakeSmsRepository()
        repo.send("+7999", "test", subscriptionId = 2, deliveryReport = false)
        assertEquals(2, repo.sent[0].third)
        assertTrue(!repo.lastDeliveryReport)
    }

    @Test
    fun subscriptionsAreAvailable() = runTest {
        val repo = FakeSmsRepository()
        val subs = repo.subscriptions()
        assertEquals(2, subs.size)
        assertTrue(subs[0].isDefault)
    }

    @Test
    fun incomingFlowDelivers() = runTest {
        val repo = FakeSmsRepository()
        val received = mutableListOf<SmsMessage>()
        val job = launch { repo.incoming().collect { received.add(it) } }
        yield()
        repo.emit(SmsMessage(2, 1, "+7111", "yo", 200, SmsType.INBOX))
        yield()
        assertEquals(1, received.size)
        assertTrue(received[0].body == "yo")
        job.cancel()
    }

    @Test
    fun deliveryFlowDelivers() = runTest {
        val repo = FakeSmsRepository()
        val received = mutableListOf<SmsDeliveryStatusChanged>()
        val job = launch { repo.deliveries().collect { received.add(it) } }
        yield()
        repo.emitDelivery(
            SmsDeliveryStatusChanged("+7999", SmsDeliveryStatus.DELIVERED, subscriptionId = 1),
        )
        yield()
        assertEquals(1, received.size)
        assertEquals(SmsDeliveryStatus.DELIVERED, received[0].status)
        job.cancel()
    }

    @Test
    fun groupSmsThreadsAggregatesByThread() {
        val messages = listOf(
            SmsMessage(1, 10, "+700", "hi", 100, SmsType.INBOX),
            SmsMessage(2, 10, "+700", "later", 300, SmsType.INBOX),
            SmsMessage(3, 11, "+711", "other", 200, SmsType.SENT),
        )
        val threads = groupSmsThreads(messages)
        assertEquals(2, threads.size)
        // Свежий тред (threadId=10, date=300) идёт первым.
        assertEquals(10L, threads[0].threadId)
        assertEquals(2, threads[0].messageCount)
        assertEquals("later", threads[0].snippet)
        assertEquals(11L, threads[1].threadId)
        assertEquals(1, threads[1].messageCount)
    }

    @Test
    fun mergeSmsThreadCreatesOrBumps() {
        val base = listOf(SmsThread(threadId = 5, address = "+700", snippet = "old", messageCount = 2, lastDate = 100))
        val merged = mergeSmsThread(base, SmsMessage(9, 5, "+700", "new", 200, SmsType.INBOX))
        assertEquals(1, merged.size)
        assertEquals(3, merged[0].messageCount)
        assertEquals("new", merged[0].snippet)
        assertEquals(200L, merged[0].lastDate)

        val created = mergeSmsThread(emptyList(), SmsMessage(9, 7, "+711", "hello", 250, SmsType.INBOX))
        assertEquals(1, created.size)
        assertEquals(7L, created[0].threadId)
        assertEquals(1, created[0].messageCount)
    }

    @Test
    fun groupSmsThreadsRespectsLimitAndOrder() {
        val messages = (1..5).map {
            SmsMessage(it.toLong(), it.toLong(), "+70$it", "m$it", it * 100L, SmsType.INBOX)
        }
        val threads = groupSmsThreads(messages, limit = 2)
        assertEquals(2, threads.size)
        // Самые свежие диалоги — впереди.
        assertEquals(500L, threads[0].lastDate)
        assertEquals(400L, threads[1].lastDate)
    }

    @Test
    fun groupSmsThreadsKeysUnknownThreadsByAddress() {
        val messages = listOf(
            SmsMessage(1, 0, "+700", "a", 100, SmsType.INBOX),
            SmsMessage(2, 0, "+711", "b", 200, SmsType.INBOX),
            SmsMessage(3, 0, "+700", "c", 300, SmsType.INBOX),
        )
        val threads = groupSmsThreads(messages)
        // Два неизвестных треда с разными адресами не должны сливаться.
        assertEquals(2, threads.size)
        assertEquals("+700", threads[0].address)
        assertEquals(2, threads[0].messageCount)
        assertEquals(300L, threads[0].lastDate)
        assertEquals("+711", threads[1].address)
    }

    @Test
    fun mergeSmsThreadMatchesUnknownThreadByAddress() {
        val base = listOf(
            SmsThread(threadId = 0, address = "+700", snippet = "old", messageCount = 1, lastDate = 100),
        )
        val merged = mergeSmsThread(base, SmsMessage(9, 0, "+700", "new", 200, SmsType.INBOX))
        assertEquals(1, merged.size)
        assertEquals(2, merged[0].messageCount)
        assertEquals("new", merged[0].snippet)
    }
}
