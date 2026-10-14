package ru.efir.core.protocol

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------------------------------------------------------------------------
// Буфер обмена
// ---------------------------------------------------------------------------

/** Установить текстовое содержимое буфера обмена. */
@Serializable
@SerialName("clipboardSet")
data class ClipboardSet(
    val text: String,
) : RpcRequest

/** Запросить текущее содержимое буфера обмена. */
@Serializable
@SerialName("clipboardGet")
object ClipboardGet : RpcRequest

/** Содержимое буфера обмена. */
@Serializable
@SerialName("clipboardData")
data class ClipboardData(
    val text: String?,
) : RpcResponse

/** Буфер обмена изменился на удалённой стороне. */
@Serializable
@SerialName("clipboardChanged")
data class ClipboardChanged(
    val text: String,
) : Event

// ---------------------------------------------------------------------------
// Уведомления
// ---------------------------------------------------------------------------

/** Появилось уведомление на Android. */
@Serializable
@SerialName("notificationPosted")
data class NotificationPosted(
    val id: String,
    val packageName: String,
    val appName: String? = null,
    val title: String? = null,
    val text: String? = null,
    val postedAt: Long,
    val ongoing: Boolean = false,
) : Event

/** Уведомление удалено. */
@Serializable
@SerialName("notificationRemoved")
data class NotificationRemoved(
    val id: String,
) : Event

/** Закрыть уведомление на телефоне (с ПК). */
@Serializable
@SerialName("notificationDismiss")
data class NotificationDismiss(
    val id: String,
) : RpcRequest

/** Быстрый ответ на уведомление (например, мессенджер). */
@Serializable
@SerialName("notificationReply")
data class NotificationReply(
    val id: String,
    val text: String,
) : RpcRequest

/** Результат действия над уведомлением. */
@Serializable
@SerialName("notificationActionResult")
data class NotificationActionResult(
    val id: String,
    val success: Boolean,
    val message: String? = null,
) : RpcResponse

// ---------------------------------------------------------------------------
// SMS
// ---------------------------------------------------------------------------

/** Тип SMS-сообщения. */
@Serializable
enum class SmsType { INBOX, SENT, DRAFT, OUTBOX, UNKNOWN }

/** SMS-сообщение. */
@Serializable
data class SmsMessage(
    val id: Long,
    val threadId: Long,
    val address: String,
    val body: String,
    val date: Long,
    val type: SmsType,
    val read: Boolean = false,
    /** Идентификатор SIM-подписки (для двойной SIM), `null` — по умолчанию. */
    val subscriptionId: Int? = null,
)

/** SIM-подписка (слот) для выбора при отправке SMS (двойная SIM). */
@Serializable
data class SmsSubscription(
    val subscriptionId: Int,
    val slotIndex: Int = 0,
    val displayName: String? = null,
    val isDefault: Boolean = false,
)

/** Запрос списка SIM-подписок. */
@Serializable
@SerialName("smsSubscriptions")
object SmsSubscriptions : RpcRequest

/** Результат [SmsSubscriptions]. */
@Serializable
@SerialName("smsSubscriptionsResult")
data class SmsSubscriptionsResult(
    val subscriptions: List<SmsSubscription>,
) : RpcResponse

/** Статус доставки отправленного SMS. */
@Serializable
enum class SmsDeliveryStatus { SENT, DELIVERED, FAILED }

/** Событие изменения статуса доставки исходящего SMS. */
@Serializable
@SerialName("smsDeliveryStatus")
data class SmsDeliveryStatusChanged(
    val address: String,
    val status: SmsDeliveryStatus,
    val subscriptionId: Int? = null,
    val detail: String? = null,
) : Event

/** Запрос списка SMS. */
@Serializable
@SerialName("smsList")
data class SmsList(
    val threadId: Long? = null,
    val limit: Int = 100,
    val offset: Int = 0,
) : RpcRequest

/** Результат [SmsList]. */
@Serializable
@SerialName("smsListResult")
data class SmsListResult(
    val messages: List<SmsMessage>,
) : RpcResponse

/** Отправка SMS. */
@Serializable
@SerialName("smsSend")
data class SmsSend(
    val address: String,
    val body: String,
    /** Выбранная SIM-подписка (двойная SIM); `null` — по умолчанию. */
    val subscriptionId: Int? = null,
    /** Запрашивать ли отчёт о доставке (события `smsDeliveryStatus`). */
    val deliveryReport: Boolean = true,
) : RpcRequest

/** Пришло новое SMS. */
@Serializable
@SerialName("smsReceived")
data class SmsReceived(
    val message: SmsMessage,
) : Event

/** Краткая сводка SMS-треда (диалога). */
@Serializable
data class SmsThread(
    val threadId: Long,
    val address: String? = null,
    val snippet: String = "",
    val messageCount: Int = 0,
    val lastDate: Long = 0,
)

/** Запрос списка SMS-тредов. */
@Serializable
@SerialName("smsThreads")
data class SmsThreads(
    val limit: Int = 50,
) : RpcRequest

/** Результат [SmsThreads]. */
@Serializable
@SerialName("smsThreadsResult")
data class SmsThreadsResult(
    val threads: List<SmsThread>,
) : RpcResponse

/** Запись из истории вызовов. */
@Serializable
data class CallLogEntry(
    val id: Long,
    val number: String? = null,
    val name: String? = null,
    val date: Long,
    val durationSeconds: Long = 0,
    val type: String = "UNKNOWN",
)

/** Запрос истории вызовов. */
@Serializable
@SerialName("callLogList")
data class CallLogList(
    val limit: Int = 50,
) : RpcRequest

/** Результат [CallLogList]. */
@Serializable
@SerialName("callLogListResult")
data class CallLogListResult(
    val entries: List<CallLogEntry>,
) : RpcResponse

// ---------------------------------------------------------------------------
// Звонки
// ---------------------------------------------------------------------------

/** Состояние звонка. */
@Serializable
enum class CallState { IDLE, RINGING, OFFHOOK }

/** Изменение состояния звонка. */
@Serializable
@SerialName("callStateChanged")
data class CallStateChanged(
    val state: CallState,
    val number: String? = null,
    val name: String? = null,
    /** Входящий ли звонок (иначе исходящий) — для корректных действий в UI. */
    val incoming: Boolean = true,
) : Event

/** Действие над звонком. */
@Serializable
enum class CallActionType { ANSWER, REJECT, END }

/** Управление звонком с ПК. */
@Serializable
@SerialName("callAction")
data class CallAction(
    val action: CallActionType,
) : RpcRequest

/** Результат выполнения [CallAction] / [CallInitiate]. */
@Serializable
@SerialName("callActionResult")
data class CallActionResult(
    val ok: Boolean,
    val detail: String? = null,
) : RpcResponse

/** Инициация исходящего звонка с ПК. */
@Serializable
@SerialName("callInitiate")
data class CallInitiate(
    /** Номер абонента, которому звоним. */
    val number: String,
    /** Выбранная SIM-подписка (двойная SIM); `null` — по умолчанию. */
    val subscriptionId: Int? = null,
) : RpcRequest
