package ru.efir.core.comms

import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.telephony.SmsManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import ru.efir.core.media.EfirAndroidEnvironment
import ru.efir.core.protocol.CallLogEntry
import ru.efir.core.protocol.SmsDeliveryStatus
import ru.efir.core.protocol.SmsDeliveryStatusChanged
import ru.efir.core.protocol.SmsMessage
import ru.efir.core.protocol.SmsSubscription
import ru.efir.core.protocol.SmsThread
import ru.efir.core.protocol.SmsType

/**
 * Android-доступ к SMS через `Telephony.Sms` provider и `SmsManager`.
 *
 * Требует разрешений `READ_SMS`/`SEND_SMS` (и роли default SMS app для полного
 * доступа). Входящие SMS публикуются в [publishIncoming] из broadcast-приёмника,
 * статусы доставки — в [publishDelivery] из [EfirSmsStatusReceiver].
 */
private class AndroidSmsRepository(
    private val context: Context,
) : SmsRepository {

    override val supported: Boolean = true

    override val isDefaultSmsApp: Boolean
        get() = TelephonySmsDefault.check(context)

    private val incomingFlow = MutableSharedFlow<SmsMessage>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    private val deliveryFlow = MutableSharedFlow<SmsDeliveryStatusChanged>(
        replay = 0,
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    override suspend fun list(threadId: Long?, limit: Int, offset: Int): List<SmsMessage> {
        val uri = Uri.parse("content://sms/")
        val selection = threadId?.let { "thread_id = ?" }
        val args = threadId?.let { arrayOf(it.toString()) }
        val projection = arrayOf(
            "_id", "thread_id", "address", "body", "date", "type", "read", "sub_id",
        )
        val result = mutableListOf<SmsMessage>()
        context.contentResolver.query(
            uri,
            projection,
            selection,
            args,
            "date DESC LIMIT ${limit.coerceAtLeast(1)} OFFSET ${offset.coerceAtLeast(0)}",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val threadIdx = cursor.getColumnIndexOrThrow("thread_id")
            val addrIdx = cursor.getColumnIndexOrThrow("address")
            val bodyIdx = cursor.getColumnIndexOrThrow("body")
            val dateIdx = cursor.getColumnIndexOrThrow("date")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            val readIdx = cursor.getColumnIndexOrThrow("read")
            val subIdx = cursor.getColumnIndex("sub_id")
            while (cursor.moveToNext()) {
                result.add(
                    SmsMessage(
                        id = cursor.getLong(idIdx),
                        threadId = cursor.getLong(threadIdx),
                        address = cursor.getString(addrIdx) ?: "",
                        body = cursor.getString(bodyIdx) ?: "",
                        date = cursor.getLong(dateIdx),
                        type = mapType(cursor.getInt(typeIdx)),
                        read = cursor.getInt(readIdx) == 1,
                        subscriptionId = if (subIdx >= 0 && !cursor.isNull(subIdx)) cursor.getInt(subIdx) else null,
                    ),
                )
            }
        }
        return result
    }

    override suspend fun threads(limit: Int): List<SmsThread> {
        // Провайдер `content://sms/` не содержит колонок `snippet`/`message_count`
        // (они доступны только через `content://mms-sms/conversations`), поэтому
        // читаем реальные сообщения и сворачиваем их в сводки тредов общей
        // функцией [groupSmsThreads] — так сводка гарантированно совпадает с
        // отображаемыми сообщениями.
        val scan = (limit.coerceAtLeast(1) * 20).coerceAtLeast(200)
        return groupSmsThreads(list(null, scan, 0), limit)
    }

    override suspend fun callLog(limit: Int): List<CallLogEntry> {
        val uri = Uri.parse("content://call_log/calls")
        val projection = arrayOf("_id", "number", "cached_name", "date", "duration", "type")
        val result = mutableListOf<CallLogEntry>()
        context.contentResolver.query(
            uri,
            projection,
            null,
            null,
            "date DESC LIMIT ${limit.coerceAtLeast(1)}",
        )?.use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val numberIdx = cursor.getColumnIndexOrThrow("number")
            val nameIdx = cursor.getColumnIndexOrThrow("cached_name")
            val dateIdx = cursor.getColumnIndexOrThrow("date")
            val durationIdx = cursor.getColumnIndexOrThrow("duration")
            val typeIdx = cursor.getColumnIndexOrThrow("type")
            while (cursor.moveToNext()) {
                result.add(
                    CallLogEntry(
                        id = cursor.getLong(idIdx),
                        number = cursor.getString(numberIdx),
                        name = cursor.getString(nameIdx),
                        date = cursor.getLong(dateIdx),
                        durationSeconds = cursor.getLong(durationIdx) / 1000,
                        type = mapCallType(cursor.getInt(typeIdx)),
                    ),
                )
            }
        }
        return result
    }

    override suspend fun subscriptions(): List<SmsSubscription> {
        val manager = context.getSystemService(SubscriptionManager::class.java) ?: return emptyList()
        val defaultId = runCatching { SubscriptionManager.getDefaultSmsSubscriptionId() }
            .getOrDefault(SubscriptionManager.INVALID_SUBSCRIPTION_ID)
        val infos: List<SubscriptionInfo> = runCatching {
            @Suppress("MissingPermission")
            manager.activeSubscriptionInfoList ?: emptyList()
        }.getOrDefault(emptyList())
        return infos.map { info ->
            SmsSubscription(
                subscriptionId = info.subscriptionId,
                slotIndex = info.simSlotIndex,
                displayName = info.displayName?.toString(),
                isDefault = info.subscriptionId == defaultId,
            )
        }
    }

    override suspend fun send(
        address: String,
        body: String,
        subscriptionId: Int?,
        deliveryReport: Boolean,
    ) {
        val manager = resolveManager(subscriptionId)
        val sentIntent = if (deliveryReport || isDefaultSmsApp) {
            buildPendingIntent(address, subscriptionId, SmsDeliveryStatus.SENT)
        } else null
        val deliveryIntent = if (deliveryReport) {
            buildPendingIntent(address, subscriptionId, SmsDeliveryStatus.DELIVERED)
        } else null
        manager.sendTextMessage(address, null, body, sentIntent, deliveryIntent)
        // Сохраняем в провайдер (доступно при роли default SMS app).
        if (isDefaultSmsApp) {
            runCatching {
                val values = ContentValues().apply {
                    put("address", address)
                    put("body", body)
                    put("date", System.currentTimeMillis())
                    put("type", 2) // MESSAGE_TYPE_SENT
                    put("read", 1)
                    subscriptionId?.let { put("sub_id", it) }
                }
                context.contentResolver.insert(Uri.parse("content://sms/sent"), values)
            }
        }
    }

    override suspend fun markRead(id: Long) {
        runCatching {
            val values = ContentValues().apply { put("read", 1) }
            context.contentResolver.update(Uri.parse("content://sms/"), values, "_id = ?", arrayOf(id.toString()))
        }
    }

    override fun incoming(): Flow<SmsMessage> = incomingFlow.asSharedFlow()

    override fun deliveries(): Flow<SmsDeliveryStatusChanged> = deliveryFlow.asSharedFlow()

    /**
     * Определяет `thread_id` диалога по адресу отправителя.
     *
     * Входящее SMS в broadcast-приёмнике приходит без `thread_id`; чтобы
     * сообщение попало в правильный диалог, находим существующий тред по
     * нормализованному адресу. Если тред не найден, возвращаем `null` — на
     * стороне ПК диалог будет сгруппирован по адресу.
     */
    fun resolveThreadId(address: String): Long? {
        if (address.isBlank()) return null
        return runCatching {
            context.contentResolver.query(
                Uri.parse("content://sms/"),
                arrayOf("thread_id"),
                "address = ?",
                arrayOf(address),
                "date DESC LIMIT 1",
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getLong(0) else null
            }
        }.getOrNull()
    }

    /**
     * Сохраняет входящее SMS в провайдер, если приложение является приложением
     * SMS по умолчанию (`SMS_DELIVER`). Иначе система сама запишет сообщение.
     * Возвращает `thread_id` сохранённой записи либо `null`.
     */
    fun persistIncoming(message: SmsMessage): Long? {
        if (!isDefaultSmsApp) return null
        return runCatching {
            val values = ContentValues().apply {
                put("address", message.address)
                put("body", message.body)
                put("date", message.date)
                put("type", 1) // MESSAGE_TYPE_INBOX
                put("read", if (message.read) 1 else 0)
                message.subscriptionId?.let { put("sub_id", it) }
            }
            context.contentResolver.insert(Uri.parse("content://sms/inbox"), values)
        }.getOrNull()?.let { uri -> uri.lastPathSegment?.toLongOrNull() }
    }

    /** Вызывается приёмником при поступлении нового SMS. */
    fun publishIncoming(message: SmsMessage) {
        incomingFlow.tryEmit(message)
    }

    /** Вызывается приёмником статуса при изменении статуса доставки. */
    fun publishDelivery(status: SmsDeliveryStatusChanged) {
        deliveryFlow.tryEmit(status)
    }

    /** Возвращает менеджер SMS для выбранной подписки (или по умолчанию). */
    private fun resolveManager(subscriptionId: Int?): SmsManager {
        if (subscriptionId == null) {
            @Suppress("DEPRECATION")
            return SmsManager.getDefault()
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(SmsManager::class.java).createForSubscriptionId(subscriptionId)
        } else {
            @Suppress("DEPRECATION")
            SmsManager.getSmsManagerForSubscriptionId(subscriptionId)
        }
    }

    /** Создаёт `PendingIntent` для приёма статуса отправки/доставки. */
    private fun buildPendingIntent(
        address: String,
        subscriptionId: Int?,
        status: SmsDeliveryStatus,
    ): PendingIntent {
        val intent = Intent(EfirSmsStatusReceiver.ACTION).apply {
            setPackage(context.packageName)
            putExtra(EfirSmsStatusReceiver.EXTRA_ADDRESS, address)
            putExtra(EfirSmsStatusReceiver.EXTRA_STATUS, status.name)
            subscriptionId?.let { putExtra(EfirSmsStatusReceiver.EXTRA_SUBSCRIPTION_ID, it) }
        }
        val requestCode = (address.hashCode() * 31 + status.ordinal).let { if (it == Int.MIN_VALUE) 0 else it }
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        return PendingIntent.getBroadcast(context, requestCode, intent, flags)
    }

    private fun mapType(type: Int): SmsType = when (type) {
        1 -> SmsType.INBOX
        2 -> SmsType.SENT
        3 -> SmsType.DRAFT
        4 -> SmsType.OUTBOX
        else -> SmsType.UNKNOWN
    }

    private fun mapCallType(type: Int): String = when (type) {
        1 -> "INCOMING"
        2 -> "OUTGOING"
        3 -> "MISSED"
        4 -> "VOICEMAIL"
        5 -> "REJECTED"
        6 -> "BLOCKED"
        else -> "UNKNOWN"
    }
}

/** Проверка роли default SMS app без дублирования кода. */
private object TelephonySmsDefault {
    fun check(context: Context): Boolean {
        val current = android.provider.Telephony.Sms.getDefaultSmsPackage(context)
        return current != null && current == context.packageName
    }
}

/**
 * Держатель колбэков публикации входящих SMS и статусов доставки для
 * broadcast-приёмников ([EfirSmsReceiver], [EfirSmsStatusReceiver]) — приёмники
 * не имеют доступа к экземпляру репозитория.
 */
object AndroidSmsRepositoryAccess {
    /** Публикует входящее SMS в активный репозиторий (устанавливается фабрикой). */
    @Volatile
    var publish: ((SmsMessage) -> Unit)? = null

    /** Публикует статус доставки в активный репозиторий. */
    @Volatile
    var publishDelivery: ((SmsDeliveryStatusChanged) -> Unit)? = null

    /** Разрешает `thread_id` входящего SMS по адресу отправителя. */
    @Volatile
    var resolveThreadId: ((String) -> Long?)? = null

    /** Сохраняет входящее SMS в провайдер (роль default SMS app), возвращает `_id`. */
    @Volatile
    var persistIncoming: ((SmsMessage) -> Long?)? = null
}

actual fun createSmsRepository(): SmsRepository {
    val context = EfirAndroidEnvironment.appContext
        ?: throw IllegalStateException("EfirAndroidEnvironment.appContext не инициализирован")
    val repository = AndroidSmsRepository(context)
    AndroidSmsRepositoryAccess.publish = { message -> repository.publishIncoming(message) }
    AndroidSmsRepositoryAccess.publishDelivery = { status -> repository.publishDelivery(status) }
    AndroidSmsRepositoryAccess.resolveThreadId = { address -> repository.resolveThreadId(address) }
    AndroidSmsRepositoryAccess.persistIncoming = { message -> repository.persistIncoming(message) }
    return repository
}
