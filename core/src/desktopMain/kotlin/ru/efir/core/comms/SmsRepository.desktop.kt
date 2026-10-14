package ru.efir.core.comms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.efir.core.protocol.SmsMessage

/** Desktop-заглушка: доступ к SMS не поддерживается. */
private class UnsupportedSmsRepository : SmsRepository {
    override val supported: Boolean = false

    override suspend fun list(threadId: Long?, limit: Int, offset: Int): List<SmsMessage> =
        throw UnsupportedOperationException("SMS не поддерживаются на Desktop")

    override suspend fun send(
        address: String,
        body: String,
        subscriptionId: Int?,
        deliveryReport: Boolean,
    ): Unit = throw UnsupportedOperationException("SMS не поддерживаются на Desktop")

    override suspend fun markRead(id: Long): Unit = Unit

    override fun incoming(): Flow<SmsMessage> = emptyFlow()

    override fun deliveries() = emptyFlow<ru.efir.core.protocol.SmsDeliveryStatusChanged>()
}

actual fun createSmsRepository(): SmsRepository = UnsupportedSmsRepository()
