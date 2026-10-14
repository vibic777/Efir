package ru.efir.core.comms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import ru.efir.core.protocol.SmsMessage
import ru.efir.core.protocol.SmsType

/**
 * Приёмник входящих SMS (`SMS_RECEIVED`, а для приложения SMS по умолчанию —
 * `SMS_DELIVER`). Преобразует системное сообщение в протокольное, разрешает
 * `thread_id` диалога по адресу и — при роли default SMS app — сохраняет
 * сообщение в провайдер. Затем публикует его в активном [SmsRepository],
 * откуда оно уходит на ПК событием `smsReceived`.
 */
class EfirSmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION &&
            action != Telephony.Sms.Intents.SMS_DELIVER_ACTION
        ) {
            return
        }
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        val publish = AndroidSmsRepositoryAccess.publish ?: return
        for (sms in messages) {
            val body = sms.displayMessageBody ?: sms.messageBody ?: continue
            val address = sms.displayOriginatingAddress ?: sms.originatingAddress ?: ""

            // Разрешаем существующий тред по адресу отправителя.
            var threadId = AndroidSmsRepositoryAccess.resolveThreadId?.invoke(address)
            var message = SmsMessage(
                id = sms.indexOnIcc.toLong(),
                threadId = threadId ?: 0L,
                address = address,
                body = body,
                date = sms.timestampMillis,
                type = SmsType.INBOX,
                read = false,
            )

            // При роли default SMS app система не записывает SMS_DELIVER в
            // провайдер сама — сохраняем сами, затем уточняем тред (для нового
            // отправителя он создаётся только после вставки).
            AndroidSmsRepositoryAccess.persistIncoming?.invoke(message)
            if (threadId == null) {
                threadId = AndroidSmsRepositoryAccess.resolveThreadId?.invoke(address)
                if (threadId != null) message = message.copy(threadId = threadId)
            }

            publish(message)
        }
    }
}
