package ru.efir.core.comms

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.CallAction
import ru.efir.core.protocol.CallActionResult
import ru.efir.core.protocol.CallActionType
import ru.efir.core.protocol.CallInitiate
import ru.efir.core.protocol.CallLogEntry
import ru.efir.core.protocol.CallLogList
import ru.efir.core.protocol.CallLogListResult
import ru.efir.core.protocol.CallState
import ru.efir.core.protocol.CallStateChanged
import ru.efir.core.protocol.ErrorMessage
import ru.efir.core.protocol.Message
import ru.efir.core.protocol.SmsDeliveryStatusChanged
import ru.efir.core.protocol.SmsList
import ru.efir.core.protocol.SmsListResult
import ru.efir.core.protocol.SmsMessage
import ru.efir.core.protocol.SmsReceived
import ru.efir.core.protocol.SmsSend
import ru.efir.core.protocol.SmsSubscription
import ru.efir.core.protocol.SmsSubscriptions
import ru.efir.core.protocol.SmsSubscriptionsResult
import ru.efir.core.protocol.SmsThread
import ru.efir.core.protocol.SmsThreads
import ru.efir.core.protocol.SmsThreadsResult

/**
 * Принимающая сторона SMS (Android-хост): обслуживает `smsList`/`smsSend`,
 * `smsThreads`, список SIM-подписок, транслирует входящие SMS как `smsReceived`,
 * а статусы доставки — как `smsDeliveryStatus`.
 */
class SmsHost(
    private val session: EfirSession,
    private val repository: SmsRepository,
    private val scope: CoroutineScope,
) {

    private var attached = false

    /** Поддерживается ли роль приложения SMS по умолчанию. */
    val isDefaultSmsApp: Boolean get() = repository.isDefaultSmsApp

    fun attach() {
        if (attached) return
        attached = true
        session.rpc.onRequest<SmsList> { request -> handleList(request) }
        session.rpc.onRequest<SmsSend> { request -> handleSend(request) }
        session.rpc.onRequest<SmsThreads> { request -> handleThreads(request) }
        session.rpc.onRequest<CallLogList> { request -> handleCallLog(request) }
        session.rpc.onRequest<SmsSubscriptions> { SmsSubscriptionsResult(repository.subscriptions()) }

        // Реал-тайм приём входящих SMS → событие удалённой стороне.
        scope.launch {
            repository.incoming().collect { message ->
                runCatching { session.rpc.sendEvent(SmsReceived(message)) }
            }
        }

        // Реал-тайм статусы доставки → событие удалённой стороне.
        scope.launch {
            repository.deliveries().collect { status ->
                runCatching { session.rpc.sendEvent(status) }
            }
        }
    }

    private suspend fun handleList(request: SmsList): Message = try {
        val messages = repository.list(request.threadId, request.limit, request.offset)
        SmsListResult(messages)
    } catch (error: Exception) {
        ErrorMessage("sms_error", error.message ?: "Не удалось прочитать SMS")
    }

    private suspend fun handleSend(request: SmsSend): Message = try {
        repository.send(
            address = request.address,
            body = request.body,
            subscriptionId = request.subscriptionId,
            deliveryReport = request.deliveryReport,
        )
        SmsListResult(emptyList())
    } catch (error: Exception) {
        ErrorMessage("sms_send_failed", error.message ?: "Не удалось отправить SMS")
    }

    private suspend fun handleThreads(request: SmsThreads): Message = try {
        SmsThreadsResult(repository.threads(request.limit))
    } catch (error: Exception) {
        ErrorMessage("sms_threads_error", error.message ?: "Не удалось прочитать треды SMS")
    }

    private suspend fun handleCallLog(request: CallLogList): Message = try {
        CallLogListResult(repository.callLog(request.limit))
    } catch (error: Exception) {
        ErrorMessage("call_log_error", error.message ?: "Не удалось прочитать историю вызовов")
    }
}

/**
 * Инициирующая сторона SMS (Windows): запрашивает список/треды/подписки,
 * отправляет сообщения, публикует входящие и статусы доставки.
 */
class SmsClient(
    private val session: EfirSession,
    private val scope: CoroutineScope,
) {

    private val _messages = MutableStateFlow<List<SmsMessage>>(emptyList())

    /** Загруженные SMS-сообщения. */
    val messages: StateFlow<List<SmsMessage>> = _messages.asStateFlow()

    private val _threads = MutableStateFlow<List<SmsThread>>(emptyList())

    /** Загруженные SMS-треды (диалоги). */
    val threads: StateFlow<List<SmsThread>> = _threads.asStateFlow()

    private val _callLog = MutableStateFlow<List<CallLogEntry>>(emptyList())

    /** История вызовов. */
    val callLog: StateFlow<List<CallLogEntry>> = _callLog.asStateFlow()

    private val _subscriptions = MutableStateFlow<List<SmsSubscription>>(emptyList())

    /** Доступные SIM-подписки (для выбора при отправке). */
    val subscriptions: StateFlow<List<SmsSubscription>> = _subscriptions.asStateFlow()

    private val _deliveries = MutableStateFlow<List<SmsDeliveryStatusChanged>>(emptyList())

    /** Последние статусы доставки отправленных SMS (новые в начале). */
    val deliveries: StateFlow<List<SmsDeliveryStatusChanged>> = _deliveries.asStateFlow()

    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        session.rpc.onEvent<SmsReceived> { event ->
            _messages.value = (listOf(event.message) + _messages.value).distinctBy { it.id }
            // Реал-тайм обновление тредов: добавляем/поднимаем диалог.
            _threads.value = mergeSmsThread(_threads.value, event.message)
        }
        session.rpc.onEvent<SmsDeliveryStatusChanged> { event ->
            _deliveries.value = (listOf(event) + _deliveries.value).take(20)
        }
    }

    /** Загружает список SMS (опционально по треду). */
    fun load(threadId: Long? = null) {
        scope.launch {
            runCatching { session.rpc.call(SmsList(threadId = threadId)) }
                .onSuccess { response ->
                    if (response is SmsListResult) _messages.value = response.messages
                }
        }
    }

    /** Загружает список SMS-тредов. */
    fun loadThreads(limit: Int = 50) {
        scope.launch {
            runCatching { session.rpc.call(SmsThreads(limit)) }
                .onSuccess { response ->
                    if (response is SmsThreadsResult) _threads.value = response.threads
                }
        }
    }

    /** Загружает историю вызовов. */
    fun loadCallLog(limit: Int = 50) {
        scope.launch {
            runCatching { session.rpc.call(CallLogList(limit)) }
                .onSuccess { response ->
                    if (response is CallLogListResult) _callLog.value = response.entries
                }
        }
    }

    /** Загружает список SIM-подписок. */
    fun loadSubscriptions() {
        scope.launch {
            runCatching { session.rpc.call(SmsSubscriptions) }
                .onSuccess { response ->
                    if (response is SmsSubscriptionsResult) _subscriptions.value = response.subscriptions
                }
        }
    }

    /** Отправляет SMS (опционально через выбранную SIM). */
    fun send(
        address: String,
        body: String,
        subscriptionId: Int? = null,
        deliveryReport: Boolean = true,
    ) {
        scope.launch {
            runCatching {
                session.rpc.call(
                    SmsSend(
                        address = address,
                        body = body,
                        subscriptionId = subscriptionId,
                        deliveryReport = deliveryReport,
                    ),
                )
            }
        }
    }

    /** Очищает локальный список (при разрыве соединения). */
    fun clear() {
        _messages.value = emptyList()
        _threads.value = emptyList()
        _callLog.value = emptyList()
        _deliveries.value = emptyList()
        _subscriptions.value = emptyList()
    }
}

/**
 * Принимающая сторона управления звонками (Android-хост): транслирует события
 * состояния звонка (`callStateChanged`) и обрабатывает действия `callAction`,
 * а также инициацию исходящих вызовов `callInitiate`.
 */
class CallHost(
    private val session: EfirSession,
    private val scope: CoroutineScope,
    /** Исполнитель действия над звонком; возвращает `null` при успехе. */
    private val onAction: (CallActionType) -> String? = { null },
    /** Инициатор исходящего звонка; возвращает `null` при успехе. */
    private val onInitiate: (String, Int?) -> String? = { _, _ -> null },
) {

    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        session.rpc.onRequest<CallAction> { request ->
            val detail = runCatching { onAction(request.action) }
                .getOrElse { it.message ?: "Не удалось выполнить действие" }
            CallActionResult(ok = detail == null, detail = detail)
        }
        session.rpc.onRequest<CallInitiate> { request ->
            val detail = runCatching { onInitiate(request.number, request.subscriptionId) }
                .getOrElse { it.message ?: "Не удалось инициировать звонок" }
            CallActionResult(ok = detail == null, detail = detail)
        }
    }

    /** Отправляет событие изменения состояния звонка удалённой стороне. */
    fun emit(state: CallStateChanged) {
        scope.launch { runCatching { session.rpc.sendEvent(state) } }
    }
}

/** Клиент звонков (Windows): принимает события состояния и шлёт действия. */
class CallClient(
    private val session: EfirSession,
    private val scope: CoroutineScope,
) {

    private val _state = MutableStateFlow(CallStateChanged(CallState.IDLE))

    /** Текущее состояние звонка. */
    val state: StateFlow<CallStateChanged> = _state.asStateFlow()

    private val _lastActionResult = MutableStateFlow<String?>(null)

    /** Деталь последнего действия над звонком (`null` — успех/нет данных). */
    val lastActionResult: StateFlow<String?> = _lastActionResult.asStateFlow()

    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        session.rpc.onEvent<CallStateChanged> { event -> _state.value = event }
    }

    /** Выполняет действие над звонком (принять/отклонить/завершить). */
    fun action(action: CallActionType) {
        scope.launch {
            runCatching { session.rpc.call(CallAction(action)) }
                .onSuccess { response ->
                    if (response is CallActionResult && !response.ok) {
                        _lastActionResult.value = response.detail ?: "Действие не выполнено"
                    } else {
                        _lastActionResult.value = null
                    }
                }
        }
    }

    /** Инициирует исходящий звонок с ПК (опционально через выбранную SIM). */
    fun initiate(number: String, subscriptionId: Int? = null) {
        scope.launch {
            runCatching { session.rpc.call(CallInitiate(number = number, subscriptionId = subscriptionId)) }
                .onSuccess { response ->
                    if (response is CallActionResult && !response.ok) {
                        _lastActionResult.value = response.detail ?: "Не удалось позвонить"
                    } else {
                        _lastActionResult.value = null
                    }
                }
        }
    }

    /** Очищает состояние. */
    fun clear() {
        _state.value = CallStateChanged(CallState.IDLE)
        _lastActionResult.value = null
    }
}

/**
 * Ключ группировки SMS в диалог: по `threadId`, если он известен, иначе — по
 * адресу отправителя (входящее SMS в приёмнике может прийти без `thread_id`).
 * Это не даёт смешать сообщения разных неизвестных отправителей в один диалог.
 */
private fun smsThreadKey(threadId: Long, address: String?): String =
    if (threadId > 0L) "t:$threadId" else "a:${(address ?: "").trim()}"

private fun smsThreadKey(message: SmsMessage): String = smsThreadKey(message.threadId, message.address)

private fun smsThreadKey(thread: SmsThread): String = smsThreadKey(thread.threadId, thread.address)

/**
 * Сворачивает плоский список SMS в сводки тредов (диалогов): по одному на
 * `threadId` (или адрес, если тред неизвестен), с последним сниппетом,
 * количеством сообщений и временем, отсортированные по времени последнего
 * сообщения (новые в начале).
 */
fun groupSmsThreads(messages: List<SmsMessage>, limit: Int = 50): List<SmsThread> {
    if (messages.isEmpty()) return emptyList()
    return messages
        .groupBy { smsThreadKey(it) }
        .map { (_, list) ->
            val last = list.maxByOrNull { it.date } ?: list.first()
            SmsThread(
                threadId = last.threadId,
                address = last.address.ifBlank { null },
                snippet = last.body,
                messageCount = list.size,
                lastDate = last.date,
            )
        }
        .sortedByDescending { it.lastDate }
        .take(limit.coerceAtLeast(1))
}

/**
 * Обновляет список тредов новым входящим SMS: поднимает/создаёт диалог с
 * актуальным сниппетом и инкрементом счётчика (реал-тайм UI без перезагрузки).
 */
fun mergeSmsThread(threads: List<SmsThread>, message: SmsMessage): List<SmsThread> {
    val key = smsThreadKey(message)
    val existing = threads.firstOrNull { smsThreadKey(it) == key }
    val updated = existing?.copy(
        address = message.address.ifBlank { existing.address ?: "" }.ifBlank { null },
        snippet = message.body,
        messageCount = existing.messageCount + 1,
        lastDate = message.date,
    ) ?: SmsThread(
        threadId = message.threadId,
        address = message.address.ifBlank { null },
        snippet = message.body,
        messageCount = 1,
        lastDate = message.date,
    )
    return listOf(updated) + threads.filterNot { smsThreadKey(it) == key }
}
