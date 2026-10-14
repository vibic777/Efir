package ru.efir.core.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow

/** Состояние соединения. */
enum class ConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    CLOSING,
}

/**
 * Дуплексное соединение Efir: текстовые контрольные сообщения (JSON-конверты)
 * и бинарные кадры (медиа/файлы).
 */
interface EfirConnection {

    /** Текущее состояние соединения. */
    val state: StateFlow<ConnectionState>

    /** Завершается при закрытии соединения. */
    val closed: CompletableDeferred<Unit>

    /** Адрес удалённой стороны (для логов/диагностики). */
    val remoteAddress: String?

    /**
     * «Отпечаток» шифрованного канала, одинаковый на обеих его сторонах и свой у
     * каждого соединения. Входит в подтверждения доверия (см. `AuthProof`), чтобы
     * посредник не мог переслать чужое подтверждение. `null` — канал не шифрован (тесты в памяти).
     */
    val channelBinding: ByteArray? get() = null

    /** Отправляет текстовый кадр (конверт). */
    suspend fun sendText(text: String)

    /** Отправляет бинарный кадр. */
    suspend fun sendBinary(bytes: ByteArray)

    /** Поток входящих текстовых кадров. */
    fun incomingText(): Flow<String>

    /** Поток входящих бинарных кадров. */
    fun incomingBinary(): Flow<ByteArray>

    /** Закрывает соединение. */
    suspend fun close()
}

/** Базовая реализация буферизации входящих кадров — общая для всех транспортов. */
abstract class AbstractEfirConnection : EfirConnection {

    protected val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    override val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private val textChannel = Channel<String>(Channel.BUFFERED)
    private val binaryChannel = Channel<ByteArray>(Channel.BUFFERED)

    override val closed = CompletableDeferred<Unit>()

    override fun incomingText(): Flow<String> = textChannel.receiveAsFlow()

    override fun incomingBinary(): Flow<ByteArray> = binaryChannel.receiveAsFlow()

    /** Доставляет входящий текстовый кадр подписчикам. */
    protected suspend fun deliverText(text: String) {
        textChannel.send(text)
    }

    /** Доставляет входящий бинарный кадр подписчикам. */
    protected suspend fun deliverBinary(bytes: ByteArray) {
        binaryChannel.send(bytes)
    }

    /** Помечает соединение закрытым и завершает фоновые каналы. */
    protected fun markClosed() {
        if (!closed.isCompleted) {
            _state.value = ConnectionState.DISCONNECTED
            textChannel.close()
            binaryChannel.close()
            closed.complete(Unit)
        }
    }
}
