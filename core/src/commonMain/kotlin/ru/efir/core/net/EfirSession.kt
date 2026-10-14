package ru.efir.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import ru.efir.core.protocol.EnvelopeSink
import ru.efir.core.protocol.Envelope
import ru.efir.core.protocol.MessageIds
import ru.efir.core.protocol.Ping
import ru.efir.core.protocol.Pong
import ru.efir.core.protocol.ProtocolCodec
import ru.efir.core.protocol.RpcSession
import ru.efir.core.protocol.nowMillis

/**
 * Высокоуровневая сессия Efir: объединяет транспорт [EfirConnection],
 * RPC-слой [RpcSession] и механизм heartbeat.
 *
 * Клиент и сервер используют один и тот же класс после установления соединения.
 */
class EfirSession(
    val connection: EfirConnection,
    private val scope: CoroutineScope,
    private val heartbeatIntervalMs: Long = 15_000,
) {

    /** Отправка конвертов уходит в текстовый канал транспорта. */
    val rpc: RpcSession = RpcSession(
        sink = EnvelopeSink { envelope -> connection.sendText(ProtocolCodec.encode(envelope)) },
        scope = scope,
    )

    private val _lastPongAt = MutableStateFlow(0L)

    /** Время последнего полученного pong (epoch millis). */
    val lastPongAt: StateFlow<Long> = _lastPongAt.asStateFlow()

    private var reader: Job? = null
    private var binaryReader: Job? = null
    private var heartbeat: Job? = null

    // Бинарные кадры раздаются всем подписчикам (broadcast), а не одному:
    // в симметричном соединении каждая сторона может одновременно быть и
    // отправителем, и приёмником (экран/файлы в обе стороны). Потребители
    // обязаны отбрасывать «чужие» кадры по своему идентификатору сессии/передачи.
    private val mediaFlow = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = BINARY_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    private val fileFlow = MutableSharedFlow<ByteArray>(
        replay = 0,
        extraBufferCapacity = BINARY_BUFFER,
        onBufferOverflow = BufferOverflow.SUSPEND,
    )

    /** Поток бинарных медиа-кадров (SCREEN/CAMERA/AUDIO). */
    fun incomingMediaFrames(): Flow<ByteArray> = mediaFlow.asSharedFlow()

    /** Поток бинарных чанков передачи файлов. */
    fun incomingFileFrames(): Flow<ByteArray> = fileFlow.asSharedFlow()

    /** Запускает чтение входящих конвертов и heartbeat. */
    fun start() {
        reader = scope.launch {
            connection.incomingText().collect { text ->
                runCatching { ProtocolCodec.decode(text) }
                    .onSuccess { rpc.onEnvelope(it) }
            }
        }

        // Бинарные кадры мультиплексируются по типу: медиа → mediaFrames,
        // файловые чанки → fileFrames. Подписчики получают свой поток.
        binaryReader = scope.launch {
            connection.incomingBinary().collect { bytes ->
                if (isTransferFrame(bytes)) fileFlow.emit(bytes) else mediaFlow.tryEmit(bytes)
            }
        }

        rpc.onEvent<Ping> { ping ->
            // Отвечаем на heartbeat удалённой стороны.
            connection.sendText(
                ProtocolCodec.encode(
                    Envelope(
                        id = MessageIds.new(),
                        timestamp = nowMillis(),
                        body = Pong(nonce = ping.nonce),
                    ),
                ),
            )
        }

        rpc.onEvent<Pong> { _lastPongAt.value = nowMillis() }

        heartbeat = scope.launch {
            while (isActive) {
                delay(heartbeatIntervalMs)
                runCatching {
                    connection.sendText(
                        ProtocolCodec.encode(
                            Envelope(
                                id = MessageIds.new(),
                                timestamp = nowMillis(),
                                body = Ping(nonce = nowMillis()),
                            ),
                        ),
                    )
                }
            }
        }
    }

    /** Проверяет, что соединение «живо» (был pong за последние 2 интервала). */
    fun isHealthy(): Boolean = nowMillis() - _lastPongAt.value < heartbeatIntervalMs * 3

    /** Останавливает сессию и закрывает соединение. */
    suspend fun close() {
        heartbeat?.cancel()
        reader?.cancel()
        binaryReader?.cancel()
        rpc.close()
        connection.close()
    }

    private fun isTransferFrame(bytes: ByteArray): Boolean =
        bytes.size >= 2 &&
            bytes[0].toInt() and 0xFF == 0x54 &&
            bytes[1].toInt() and 0xFF == 0x46

    private companion object {
        /** Ёмкость буфера бинарных кадров (сверх подписчиков). */
        const val BINARY_BUFFER = 64
    }
}
