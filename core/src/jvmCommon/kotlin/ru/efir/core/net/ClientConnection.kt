package ru.efir.core.net

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.engine.cio.CIOEngineConfig
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.request.url
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Клиент Efir (сторона Windows, а также симметрично может использоваться Android).
 *
 * Подключается к серверу по WebSocket поверх TLS (или без него в режиме отладки)
 * и возвращает готовую [EfirConnection].
 */
object ClientConnection {

    /**
     * Устанавливает соединение с сервером.
     *
     * При включённом TLS и заданном [TlsConfig.expectedFingerprint] доверие
     * проверяется пиннингом; иначе используется режим TOFU (доверяем всем).
     */
    suspend fun connect(
        config: ClientConfig,
        scope: CoroutineScope,
    ): EfirConnection = withContext(Dispatchers.IO) {
        val client = HttpClient(CIO) {
            install(WebSockets) {
                maxFrameSize = MAX_FRAME_BYTES
            }
            engine {
                if (config.tls.enabled) {
                    https {
                        trustManager = when (val fp = config.tls.expectedFingerprint) {
                            null -> JvmTls.trustAllManager()
                            else -> JvmTls.pinningManager(fp)
                        }
                    }
                }
            }
        }

        val session = client.webSocketSession {
            url(
                buildString {
                    append(if (config.tls.enabled) "wss://" else "ws://")
                    append(config.host)
                    append(':')
                    append(config.port)
                    append(config.path)
                },
            )
        }

        val raw = KtorWebSocketConnection(
            session = session,
            scope = scope,
            remoteAddress = "${config.host}:${config.port}",
        )
        raw.start()
        // Дальше — только шифрованный канал.
        val connection = SecureConnection(raw, scope, client = true)
        try {
            connection.handshake()
        } catch (e: kotlinx.coroutines.CancellationException) {
            if (e !is kotlinx.coroutines.TimeoutCancellationException) throw e
            raw.close()
            throw IllegalStateException(NO_SECURE_CHANNEL)
        } catch (e: Exception) {
            raw.close()
            throw IllegalStateException(NO_SECURE_CHANNEL, e)
        }
        connection
    }
}

private const val NO_SECURE_CHANNEL =
    "Не удалось открыть защищённое соединение: на другом устройстве должна быть та же версия Эфира"
