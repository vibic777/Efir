package ru.efir.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import ru.efir.core.app.ClientHandshake
import ru.efir.core.app.HostHandshake
import ru.efir.core.app.LocalPeer
import ru.efir.core.pairing.InMemoryTrustStore
import ru.efir.core.pairing.PairingService
import ru.efir.core.protocol.DeviceType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PairingNetworkTest {

    private fun freePort(): Int = java.net.ServerSocket(0).use { it.localPort }

    private suspend fun awaitServerReady(port: Int) {
        withTimeout(10_000) {
            while (true) {
                val ok = runCatching { java.net.Socket("127.0.0.1", port).use { true } }.getOrDefault(false)
                if (ok) return@withTimeout
                delay(100)
            }
        }
    }

    @Test
    fun pairingOverNetworkEstablishesTrust() = runBlocking {
        val port = freePort()
        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val trustStore = InMemoryTrustStore()
        val pairing = PairingService(
            trustStore = trustStore,
            fingerprint = "deadbeef",
            host = "127.0.0.1",
            port = port,
        )
        val pending = pairing.begin("android-1", "Pixel", DeviceType.ANDROID)

        val hostLocal = LocalPeer("android-1", "Pixel", DeviceType.ANDROID)
        val hostHandshake = HostHandshake(hostLocal, pairing, trustStore)

        val host = createEfirHost(
            config = ServerConfig(bindAddress = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
            onConnection = { connection ->
                val session = EfirSession(connection, hostScope)
                session.start()
                hostHandshake.attach(session)
                connection.closed.await()
            },
        )
        host.start()

        try {
            awaitServerReady(port)

            val connection = openEfirConnection(
                config = ClientConfig(host = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
                parent = clientScope,
            )
            val clientSession = EfirSession(connection, clientScope)
            clientSession.start()

            val clientLocal = LocalPeer("win-1", "PC", DeviceType.WINDOWS)
            val outcome = ClientHandshake.perform(
                session = clientSession,
                local = clientLocal,
                pairingToken = pending.payload.pairingToken,
                accessKey = pending.accessKey,
            )

            assertTrue(outcome.accepted, "Сопряжение должно быть принято: ${outcome.reason}")
            assertNotNull(outcome.sessionToken)
            val trusted = trustStore.find("win-1")
            assertNotNull(trusted, "Доверенное устройство должно быть сохранено")
            assertEquals(DeviceType.WINDOWS, trusted.type)

            clientSession.close()
        } finally {
            host.stop()
            hostScope.cancel()
            clientScope.cancel()
        }
    }

    @Test
    fun wrongAccessKeyOverNetworkIsRejected() = runBlocking {
        val port = freePort()
        val hostScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val clientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        val trustStore = InMemoryTrustStore()
        val pairing = PairingService(trustStore, "fp", "127.0.0.1", port)
        val pending = pairing.begin("android-1", "Pixel", DeviceType.ANDROID)

        val host = createEfirHost(
            config = ServerConfig(bindAddress = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
            onConnection = { connection ->
                val session = EfirSession(connection, hostScope)
                session.start()
                HostHandshake(LocalPeer("android-1", "Pixel", DeviceType.ANDROID), pairing, trustStore).attach(session)
                connection.closed.await()
            },
        )
        host.start()

        try {
            awaitServerReady(port)
            val connection = openEfirConnection(
                ClientConfig(host = "127.0.0.1", port = port, tls = TlsConfig(enabled = false)),
                clientScope,
            )
            val clientSession = EfirSession(connection, clientScope)
            clientSession.start()

            val outcome = ClientHandshake.perform(
                session = clientSession,
                local = LocalPeer("win-9", "PC", DeviceType.WINDOWS),
                pairingToken = pending.payload.pairingToken,
                accessKey = "000000000000",
            )
            assertTrue(!outcome.accepted, "Неверный ключ доступа должен быть отклонён")
            assertTrue(trustStore.find("win-9") == null)

            clientSession.close()
        } finally {
            host.stop()
            hostScope.cancel()
            clientScope.cancel()
        }
    }
}
