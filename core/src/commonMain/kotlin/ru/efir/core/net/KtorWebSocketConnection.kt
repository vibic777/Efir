package ru.efir.core.net

import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.WebSocketSession
import io.ktor.websocket.close
import io.ktor.websocket.readBytes
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Реализация [EfirConnection] поверх Ktor [WebSocketSession].
 *
 * Работает как для клиентских, так и для серверных сессий, поскольку обе
 * реализуют единый интерфейс [WebSocketSession]. Потребляет входной канал
 * сессии в фоновой корутине и раскладывает кадры по текстовым/бинарным потокам.
 */
class KtorWebSocketConnection(
    private val session: WebSocketSession,
    private val scope: CoroutineScope,
    override val remoteAddress: String? = null,
) : AbstractEfirConnection() {

    private var consumer: Job? = null

    /** Запускает фоновое чтение входящих кадров. */
    fun start() {
        _state.value = ConnectionState.CONNECTED
        consumer = scope.launch { consume() }
    }

    private suspend fun consume() {
        try {
            for (frame in session.incoming) {
                when (frame) {
                    is Frame.Text -> deliverText(frame.readText())
                    is Frame.Binary -> deliverBinary(frame.readBytes())
                    is Frame.Close -> break
                    else -> Unit // Ping/Pong обрабатываются самим движком Ktor.
                }
            }
        } finally {
            markClosed()
        }
    }

    override suspend fun sendText(text: String) {
        session.send(Frame.Text(text))
    }

    override suspend fun sendBinary(bytes: ByteArray) {
        session.send(Frame.Binary(fin = true, data = bytes))
    }

    override suspend fun close() {
        if (_state.value == ConnectionState.CLOSING || _state.value == ConnectionState.DISCONNECTED) return
        _state.value = ConnectionState.CLOSING
        runCatching { session.close(CloseReason(CloseReason.Codes.NORMAL, "bye")) }
        consumer?.cancel()
        markClosed()
    }
}
