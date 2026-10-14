package ru.efir.core.comms

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import ru.efir.core.protocol.SmsDeliveryStatus
import ru.efir.core.protocol.SmsDeliveryStatusChanged

/**
 * Приёмник статусов отправки/доставки исходящих SMS.
 *
 * `SmsManager` передаёт сюда результат отправки (`sentIntent`) и доставки
 * (`deliveryIntent`, требует роли default SMS app). Событие публикуется в
 * активный [SmsRepository].[deliveries], откуда уходит на ПК событием
 * `smsDeliveryStatus`.
 */
class EfirSmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val publish = AndroidSmsRepositoryAccess.publishDelivery ?: return
        val address = intent.getStringExtra(EXTRA_ADDRESS) ?: return
        val statusName = intent.getStringExtra(EXTRA_STATUS)
        val status = runCatching { SmsDeliveryStatus.valueOf(statusName ?: "") }.getOrNull() ?: return
        val subscriptionId = intent.getIntExtra(EXTRA_SUBSCRIPTION_ID, -1).takeIf { it >= 0 }

        val finalStatus = if (resultCode == Activity.RESULT_OK) status else SmsDeliveryStatus.FAILED
        val detail = when {
            resultCode == Activity.RESULT_OK -> null
            status == SmsDeliveryStatus.SENT -> "Не удалось отправить (код $resultCode)"
            else -> "Не доставлено (код $resultCode)"
        }

        publish(
            SmsDeliveryStatusChanged(
                address = address,
                status = finalStatus,
                subscriptionId = subscriptionId,
                detail = detail,
            ),
        )
    }

    companion object {
        const val ACTION = "ru.efir.core.comms.SMS_STATUS"
        const val EXTRA_ADDRESS = "address"
        const val EXTRA_STATUS = "status"
        const val EXTRA_SUBSCRIPTION_ID = "subscription_id"
    }
}
