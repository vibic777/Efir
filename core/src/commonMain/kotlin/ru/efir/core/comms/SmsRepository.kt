package ru.efir.core.comms

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import ru.efir.core.protocol.CallLogEntry
import ru.efir.core.protocol.SmsDeliveryStatusChanged
import ru.efir.core.protocol.SmsMessage
import ru.efir.core.protocol.SmsSubscription
import ru.efir.core.protocol.SmsThread

/**
 * Backend-независимый доступ к SMS (Android — `Telephony.Sms` + `SmsManager`,
 * Desktop — не поддерживается).
 */
interface SmsRepository {

    /** Поддерживается ли работа с SMS на платформе. */
    val supported: Boolean

    /**
     * Является ли приложение приложением SMS по умолчанию.
     * Даёт полный доступ (запись в провайдер, отчёты о доставке, `SMS_DELIVER`).
     */
    val isDefaultSmsApp: Boolean get() = false

    /** Список сообщений (треда или всех). */
    suspend fun list(threadId: Long?, limit: Int, offset: Int): List<SmsMessage>

    /** Список SMS-тредов (диалогов) со сводкой. */
    suspend fun threads(limit: Int): List<SmsThread> = emptyList()

    /** История вызовов (CallLog). */
    suspend fun callLog(limit: Int): List<CallLogEntry> = emptyList()

    /** Список активных SIM-подписок (для двойной SIM). */
    suspend fun subscriptions(): List<SmsSubscription> = emptyList()

    /**
     * Отправляет SMS; бросает исключение при ошибке.
     *
     * @param subscriptionId выбранная SIM-подписка (`null` — по умолчанию).
     * @param deliveryReport запрашивать ли отчёт о доставке (события [deliveries]).
     */
    suspend fun send(
        address: String,
        body: String,
        subscriptionId: Int? = null,
        deliveryReport: Boolean = true,
    )

    /** Помечает сообщение прочитанным. */
    suspend fun markRead(id: Long)

    /** Поток входящих SMS (реал-тайм). */
    fun incoming(): Flow<SmsMessage>

    /** Поток статусов доставки исходящих SMS (реал-тайм). */
    fun deliveries(): Flow<SmsDeliveryStatusChanged> = emptyFlow()
}

/** Создаёт платформенный backend SMS. */
expect fun createSmsRepository(): SmsRepository
