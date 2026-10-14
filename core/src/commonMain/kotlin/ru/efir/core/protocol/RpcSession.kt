package ru.efir.core.protocol

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import kotlin.reflect.KClass

/** Приёмник готовых конвертов (транспорт отправки). */
fun interface EnvelopeSink {
    suspend fun send(envelope: Envelope)
}

/**
 * Сессия RPC поверх control-канала.
 *
 * Отвечает за:
 *  - отправку запросов и ожидание ответов (с таймаутом) по связке id/replyTo;
 *  - маршрутизацию входящих запросов зарегистрированным обработчикам и отправку ответов;
 *  - доставку входящих событий обработчикам.
 *
 * Потокобезопасна: карта ожидающих ответов защищена [Mutex].
 */
class RpcSession(
    private val sink: EnvelopeSink,
    private val scope: CoroutineScope,
    private val defaultTimeoutMs: Long = 30_000,
) {

    private val mutex = Mutex()
    private val pending = mutableMapOf<String, CompletableDeferred<Message>>()

    private val requestHandlers = mutableMapOf<KClass<out Message>, suspend (Message) -> Message>()
    private val eventHandlers = mutableMapOf<KClass<out Message>, suspend (Message) -> Unit>()

    /** Регистрирует обработчик входящих запросов указанного типа. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Message> onRequest(type: KClass<T>, handler: suspend (T) -> Message) {
        requestHandlers[type] = { msg -> handler(msg as T) }
    }

    /** Регистрирует обработчик входящих событий указанного типа. */
    @Suppress("UNCHECKED_CAST")
    fun <T : Message> onEvent(type: KClass<T>, handler: suspend (T) -> Unit) {
        eventHandlers[type] = { msg -> handler(msg as T) }
    }

    /** Удобная обёртка с reified-типом для регистрации запросов. */
    inline fun <reified T : Message> onRequest(noinline handler: suspend (T) -> Message) =
        onRequest(T::class, handler)

    /** Удобная обёртка с reified-типом для регистрации событий. */
    inline fun <reified T : Message> onEvent(noinline handler: suspend (T) -> Unit) =
        onEvent(T::class, handler)

    /**
     * Отправляет запрос и ожидает ответ.
     *
     * @throws TimeoutCancellationException если ответ не получен за [timeoutMs].
     */
    suspend fun call(
        request: Message,
        channel: ProtocolChannel = ProtocolChannel.CONTROL,
        timeoutMs: Long = defaultTimeoutMs,
    ): Message {
        val id = MessageIds.new()
        val deferred = CompletableDeferred<Message>()
        mutex.withLock { pending[id] = deferred }
        return try {
            sink.send(
                Envelope(
                    id = id,
                    channel = channel,
                    timestamp = nowMillis(),
                    body = request,
                ),
            )
            withTimeout(timeoutMs) { deferred.await() }
        } finally {
            mutex.withLock { pending.remove(id) }
        }
    }

    /** Отправляет событие «выстрелил и забыл» (ответ не ожидается). */
    suspend fun sendEvent(
        event: Event,
        channel: ProtocolChannel = ProtocolChannel.CONTROL,
    ) {
        sink.send(
            Envelope(
                id = MessageIds.new(),
                channel = channel,
                timestamp = nowMillis(),
                body = event,
            ),
        )
    }

    /**
     * Обрабатывает входящий конверт: ответы — разрешают ожидания,
     * запросы — уходят в обработчики (с автоматическим ответом),
     * события — доставляются подписчикам.
     */
    fun onEnvelope(envelope: Envelope) {
        if (envelope.protocolVersion > PROTOCOL_VERSION) {
            // Удалённая сторона новее — работать нельзя.
            scope.launch {
                sink.send(
                    Envelope(
                        id = MessageIds.new(),
                        replyTo = envelope.id,
                        channel = envelope.channel,
                        timestamp = nowMillis(),
                        body = ErrorMessage(
                            code = "protocol_version_mismatch",
                            message = "Локальная версия ${PROTOCOL_VERSION} < удалённой ${envelope.protocolVersion}",
                        ),
                    ),
                )
            }
            return
        }

        when (val body = envelope.body) {
            is RpcResponse -> {
                val target = envelope.replyTo ?: return
                scope.launch {
                    val deferred = mutex.withLock { pending[target] }
                    deferred?.complete(body)
                }
            }

            is RpcRequest -> {
                scope.launch { handleRequest(envelope, body) }
            }

            is Event -> {
                scope.launch {
                    eventHandlers[body::class]?.invoke(body)
                }
            }
        }
    }

    private suspend fun handleRequest(envelope: Envelope, request: RpcRequest) {
        val handler = requestHandlers[request::class]
        val response: Message = if (handler == null) {
            ErrorMessage(
                code = "unhandled_request",
                message = "Нет обработчика для ${request::class.simpleName}",
            )
        } else {
            runCatching { handler(request) }.getOrElse { error ->
                ErrorMessage(
                    code = "handler_error",
                    message = error.message ?: error::class.simpleName ?: "unknown error",
                )
            }
        }
        sink.send(
            Envelope(
                id = MessageIds.new(),
                replyTo = envelope.id,
                channel = envelope.channel,
                timestamp = nowMillis(),
                body = response,
            ),
        )
    }

    /** Отменяет все ожидающие ответы (при закрытии соединения). */
    suspend fun close() {
        mutex.withLock {
            pending.values.forEach { it.cancel() }
            pending.clear()
        }
    }
}
