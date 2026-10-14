package ru.efir.core.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.efir.core.protocol.Ack
import ru.efir.core.protocol.CapabilitiesResult
import ru.efir.core.protocol.Capability
import ru.efir.core.protocol.ClipboardChanged
import ru.efir.core.protocol.ClipboardSet
import ru.efir.core.protocol.Envelope
import ru.efir.core.protocol.MessageIds
import ru.efir.core.protocol.ProtocolCodec
import ru.efir.core.protocol.RequestCapabilities
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TransportIntegrationTest {

    /** Свободный порт из диапазона ОС. */
    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    /** Ожидает, пока сервер начнёт принимать соединения. */
    private suspend fun awaitServerReady(port: Int) {
        withTimeout(10_000) {
            while (true) {
                val ok = runCatching {
                    java.net.Socket("127.0.0.1", port).use { true }
                }.getOrDefault(false)
                if (ok) return@withTimeout
                delay(100)
            }
        }
    }

    @Test
    fun clientServerRoundTripOverWebSocket() = runBlocking {
        val port = freePort()
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val server = EfirServer(
            config = ServerConfig(bindAddress = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
            onConnection = { connection ->
                val session = EfirSession(connection, serverScope)
                session.start()
                session.rpc.onRequest<RequestCapabilities> {
                    CapabilitiesResult(setOf(Capability.CLIPBOARD, Capability.FILE_MANAGER))
                }
            },
        )
        server.start()

        try {
            awaitServerReady(port)

            val connection = ClientConnection.connect(
                config = ClientConfig(host = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
                scope = clientScope,
            )
            val clientSession = EfirSession(connection, clientScope)
            clientSession.start()

            val response = withTimeout(10_000) {
                clientSession.rpc.call(RequestCapabilities, timeoutMs = 8_000)
            }
            val result = assertIs<CapabilitiesResult>(response)
            assertEquals(setOf(Capability.CLIPBOARD, Capability.FILE_MANAGER), result.capabilities)

            clientSession.close()
        } finally {
            server.stop()
            serverScope.cancel()
            clientScope.cancel()
        }
    }

    @Test
    fun serverEventReachesClient() = runBlocking {
        val port = freePort()
        val serverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val server = EfirServer(
            config = ServerConfig(bindAddress = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
            onConnection = { connection ->
                val session = EfirSession(connection, serverScope)
                session.start()
                // Реагируем на clipboardSet: шлём событие изменения и подтверждение.
                session.rpc.onRequest<ClipboardSet> { request ->
                    connection.sendText(
                        ProtocolCodec.encode(
                            Envelope(
                                id = MessageIds.new(),
                                timestamp = 0L,
                                body = ClipboardChanged(text = request.text),
                            ),
                        ),
                    )
                    Ack()
                }
            },
        )
        server.start()

        try {
            awaitServerReady(port)

            val received = CompletableDeferred<String>()
            val connection = ClientConnection.connect(
                config = ClientConfig(host = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
                scope = clientScope,
            )
            val clientSession = EfirSession(connection, clientScope)
            clientSession.start()
            clientSession.rpc.onEvent<ClipboardChanged> { event ->
                if (!received.isCompleted) received.complete(event.text)
            }

            val ack = withTimeout(10_000) {
                clientSession.rpc.call(ClipboardSet(text = "ping-clip"), timeoutMs = 8_000)
            }
            assertIs<Ack>(ack)

            val value = withTimeout(10_000) { received.await() }
            assertTrue(value == "ping-clip")

            clientSession.close()
        } finally {
            server.stop()
            serverScope.cancel()
            clientScope.cancel()
        }
    }
}
