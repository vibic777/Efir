package ru.efir.core.net

import io.ktor.websocket.CloseReason
import io.ktor.websocket.close
import ru.efir.core.app.AppLog
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

/**
 * Сервер Efir (сторона Android): слушает control-подключения по WebSocket.
 *
 * Каждое входящее соединение оборачивается в [KtorWebSocketConnection] и
 * передаётся в [onConnection]. Обработка соединений ведётся в общем [scope].
 */
class EfirServer(
    private val config: ServerConfig,
    private val onConnection: suspend (EfirConnection) -> Unit,
) {

    private lateinit var engine: EmbeddedServer<*, *>
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Запускает сервер (не блокирует вызывающий поток). */
    fun start() {
        engine = embeddedServer(CIO, port = config.port, host = config.bindAddress) {
            install(WebSockets) {
                // Предел размера кадра: чужой не заставит выделить память без меры.
                maxFrameSize = MAX_FRAME_BYTES
            }
            routing {
                webSocket(config.path) {
                    val remoteHost = call.request.local.remoteHost
                    // Эфир — только для локальной сети: подключение с адреса из интернета
                    // (сотовая сеть, VPN с белым адресом) закрывается сразу.
                    if (!isLocalAddress(remoteHost)) {
                        AppLog.w("net", "a connection from $remoteHost was refused: not a local network address")
                        close(CloseReason(CloseReason.Codes.VIOLATED_POLICY, "local network only"))
                        return@webSocket
                    }
                    val raw = KtorWebSocketConnection(
                        session = this,
                        scope = scope,
                        remoteAddress = remoteHost + ":" + call.request.local.remotePort,
                    )
                    raw.start()
                    // Дальше — только шифрованный канал; кто его не открыл (не Эфир, старая версия), тот отключается.
                    val connection = SecureConnection(raw, scope, client = false)
                    val secured = runCatching { connection.handshake() }
                    if (secured.isFailure) {
                        AppLog.w("net", "no secure channel with $remoteHost: ${secured.exceptionOrNull()?.message}")
                        raw.close()
                        return@webSocket
                    }
                    onConnection(connection)
                    // Удерживаем обработчик живым, пока соединение не закрыто,
                    // иначе Ktor завершит сессию сразу после возврата функции.
                    connection.closed.await()
                }
            }
        }
        engine.start(wait = false)
    }

    /** Останавливает сервер и освобождает ресурсы. */
    fun stop() {
        if (::engine.isInitialized) {
            engine.stop(500, 1_000)
        }
        scope.cancel()
    }
}

/** Самый большой кадр, который соединение согласно принять. */
internal const val MAX_FRAME_BYTES = 64L * 1024 * 1024

/** Адрес из локальной сети (или этот же компьютер), а не из интернета. */
internal fun isLocalAddress(host: String): Boolean = runCatching {
    val address = java.net.InetAddress.getByName(host)
    val unique = address is java.net.Inet6Address && (address.address[0].toInt() and 0xFE) == 0xFC
    address.isLoopbackAddress || address.isSiteLocalAddress || address.isLinkLocalAddress || unique
}.getOrDefault(false)
