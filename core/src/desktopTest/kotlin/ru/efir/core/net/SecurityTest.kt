package ru.efir.core.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import ru.efir.core.files.LocalFileSystem
import ru.efir.core.pairing.AuthProof
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Проверки защиты: шифрованный канал, подтверждения доверия, границы доступа к файлам. */
class SecurityTest {

    /** Пара соединений в памяти; [tamper] может изменить кадр по пути от A к B, [tap] видит всё, что идёт по «сети». */
    private class Wire(val tamper: (ByteArray) -> ByteArray = { it }, val tap: (ByteArray) -> Unit = {}) {
        inner class End : AbstractEfirConnection() {
            lateinit var peer: End
            var outgoing: (ByteArray) -> ByteArray = { it }
            override val remoteAddress: String? = "memory"
            override suspend fun sendText(text: String) = error("the secure channel sends binary frames only")
            override suspend fun sendBinary(bytes: ByteArray) {
                tap(bytes)
                peer.receive(outgoing(bytes))
            }
            suspend fun receive(bytes: ByteArray) = deliverBinary(bytes)
            override suspend fun close() = markClosed()
        }

        val a = End()
        val b = End()

        init {
            a.peer = b
            b.peer = a
            a.outgoing = tamper
        }
    }

    private fun secured(wire: Wire, scope: CoroutineScope): Pair<SecureConnection, SecureConnection> = runBlocking {
        val client = SecureConnection(wire.a, scope, client = true)
        val host = SecureConnection(wire.b, scope, client = false)
        val hostReady = scope.launch { host.handshake() }
        client.handshake()
        hostReady.join()
        client to host
    }

    @Test
    fun nothingReadableGoesOverTheWire() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val seen = mutableListOf<ByteArray>()
            val (client, host) = secured(Wire(tap = { synchronized(seen) { seen += it } }), scope)
            val secret = "access key ABCD-EFGH-JKLM and an SMS text"
            client.sendText(secret)
            assertEquals(secret, withTimeout(5_000) { host.incomingText().first() })
            host.sendBinary(byteArrayOf(1, 2, 3))
            assertContentEquals(byteArrayOf(1, 2, 3), withTimeout(5_000) { client.incomingBinary().first() })

            val onTheWire = synchronized(seen) { seen.joinToString("") { String(it, Charsets.ISO_8859_1) } }
            assertFalse("ABCD-EFGH-JKLM" in onTheWire, "the text must not be readable on the wire")
            assertContentEquals(client.channelBinding, host.channelBinding)
            assertNotNull(client.channelBinding)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aChangedFrameClosesTheConnection() = runBlocking<Unit> {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            var frames = 0
            // Первый кадр — открытый ключ; второй (уже шифрованный) портим на один бит.
            val wire = Wire(tamper = { frame -> if (++frames == 2) frame.copyOf().also { it[it.size - 1] = (it[it.size - 1].toInt() xor 1).toByte() } else frame })
            val (client, host) = secured(wire, scope)
            client.sendText("hello")
            assertNull(withTimeoutOrNull(1_500) { host.incomingText().firstOrNull() }, "a changed frame must not be delivered")
            withTimeout(5_000) { host.closed.await() }
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun aProofOfOneChannelIsUselessOnAnother() {
        val token = "trust-token"
        val one = ByteArray(32) { 1 }
        val other = ByteArray(32) { 2 }
        val proof = AuthProof.of(token, one, AuthProof.CLIENT)
        assertTrue(AuthProof.matches(proof, token, one, AuthProof.CLIENT))
        // Перехваченное подтверждение не подходит ни к другому каналу, ни к другой роли, ни к другому секрету.
        assertFalse(AuthProof.matches(proof, token, other, AuthProof.CLIENT))
        assertFalse(AuthProof.matches(proof, token, one, AuthProof.HOST))
        assertFalse(AuthProof.matches(proof, "another", one, AuthProof.CLIENT))
        assertFalse(token in proof)
    }

    @Test
    fun onlyLocalAddressesMayConnect() {
        assertTrue(isLocalAddress("192.168.1.20"))
        assertTrue(isLocalAddress("10.0.0.5"))
        assertTrue(isLocalAddress("172.16.4.1"))
        assertTrue(isLocalAddress("127.0.0.1"))
        assertFalse(isLocalAddress("8.8.8.8"))
        assertFalse(isLocalAddress("93.184.216.34"))
    }

    @Test
    fun aMissingPathCannotClimbOutOfTheRoot() = runBlocking<Unit> {
        val base = Files.createTempDirectory("efir-sec-test").toFile()
        try {
            val root = File(base, "root").apply { mkdirs() }
            val fs = LocalFileSystem(rootPaths = listOf(root.absolutePath))
            // «корень/нет/../../outside» лежит снаружи, хотя текстом начинается с корня.
            val outside = root.absolutePath + "/missing/../../outside"
            assertFailsWith<IllegalArgumentException> { fs.mkdir(outside) }
            assertFalse(File(base, "outside").exists())
            assertFailsWith<IllegalArgumentException> { fs.append(root.absolutePath + "/none/../../stolen.txt", byteArrayOf(1)) }
            assertFalse(File(base, "stolen.txt").exists())
        } finally {
            base.deleteRecursively()
        }
    }
}
