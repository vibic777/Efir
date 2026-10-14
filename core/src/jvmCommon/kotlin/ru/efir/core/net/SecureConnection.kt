package ru.efir.core.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.nio.ByteBuffer
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрованный канал поверх обычного соединения. Всё, что идёт по сети после
 * первых двух кадров, — ключи доступа и подтверждения доверия, файлы, SMS,
 * экран, нажатия — зашифровано и защищено от подмены.
 *
 * Как устроено:
 * 1. Стороны обмениваются одноразовыми открытыми ключами (ECDH, кривая P-256);
 *    закрытые ключи живут только в памяти и только на это соединение.
 * 2. Из общего секрета выводятся (HKDF, HMAC-SHA256) два ключа AES-256 — по одному
 *    на направление — и [channelBinding], «отпечаток» именно этого канала.
 * 3. Каждый кадр шифруется AES-256-GCM со своим номером; кадр с чужим или
 *    повторным номером, изменённый или переставленный, закрывает соединение.
 *
 * Сам обмен ключами никого не удостоверяет: посредник мог бы провести его с
 * каждой стороной отдельно. Поэтому стороны подтверждают друг другу знание
 * секрета (ключа доступа при сопряжении, токена доверия потом) подписью,
 * в которую входит [channelBinding] (см. `AuthProof`): у посредника отпечатки
 * двух каналов разные, и чужая подпись ему не подходит. Сам секрет по сети не идёт.
 *
 * Никаких сторонних библиотек: только криптография платформы (JCA).
 */
class SecureConnection(
    private val inner: EfirConnection,
    private val scope: CoroutineScope,
    /** Эта сторона открыла соединение (от роли зависит, какой ключ в какую сторону). */
    private val client: Boolean,
) : AbstractEfirConnection() {

    override val remoteAddress: String? get() = inner.remoteAddress

    @Volatile
    private var binding: ByteArray? = null
    override val channelBinding: ByteArray? get() = binding

    private var sendKey: SecretKeySpec? = null
    private var receiveKey: SecretKeySpec? = null
    private var sendCounter = 0L
    private var receiveCounter = 0L
    private val sendMutex = Mutex()

    private val peerHello = CompletableDeferred<ByteArray>()
    private val keysReady = CompletableDeferred<Unit>()
    private var reader: Job? = null
    private var first = true

    /**
     * Обменивается ключами с другой стороной. Пока не вернётся, по каналу ничего не ходит.
     * @throws Exception если другая сторона не ответила или ответила не тем (старая версия, не Эфир).
     */
    suspend fun handshake(timeoutMs: Long = HANDSHAKE_TIMEOUT_MS) {
        val pair = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }.generateKeyPair()
        val mine = pair.public.encoded

        reader = scope.launch {
            try {
                inner.incomingBinary().collect { onFrame(it) }
            } catch (_: Exception) {
                // Кадр не расшифровался или пришёл не по порядку: канал больше не годится.
            } finally {
                runCatching { inner.close() }
                markClosed()
            }
        }
        scope.launch {
            inner.closed.await()
            markClosed()
        }

        inner.sendBinary(MAGIC + mine)
        val hello = withTimeout(timeoutMs) { peerHello.await() }
        require(hello.size > MAGIC.size && hello.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) { "not an Efir secure channel" }
        val theirs = hello.copyOfRange(MAGIC.size, hello.size)

        val peerKey = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(theirs))
        val shared = KeyAgreement.getInstance("ECDH").apply {
            init(pair.private)
            doPhase(peerKey, true)
        }.generateSecret()

        // Порядок ключей одинаков на обеих сторонах: сначала ключ открывшего соединение.
        val clientKey = if (client) mine else theirs
        val hostKey = if (client) theirs else mine
        val salt = MessageDigest.getInstance("SHA-256").digest(clientKey + hostKey)
        val prk = hmac(salt, shared)
        val toHost = SecretKeySpec(hmac(prk, "efir client to host".toByteArray()), "AES")
        val toClient = SecretKeySpec(hmac(prk, "efir host to client".toByteArray()), "AES")
        sendKey = if (client) toHost else toClient
        receiveKey = if (client) toClient else toHost
        binding = hmac(prk, "efir channel binding".toByteArray())
        shared.fill(0)

        _state.value = ConnectionState.CONNECTED
        keysReady.complete(Unit)
    }

    private suspend fun onFrame(frame: ByteArray) {
        if (first) {
            first = false
            peerHello.complete(frame)
            return
        }
        keysReady.await()
        require(frame.size >= HEADER + TAG_BYTES) { "frame too short" }
        val type = frame[0]
        val counter = ByteBuffer.wrap(frame, 1, 8).long
        // Номера идут строго подряд: повтор, пропуск или перестановка — признак вмешательства.
        require(counter == receiveCounter) { "frame out of order" }
        receiveCounter++
        val plain = cipher(Cipher.DECRYPT_MODE, receiveKey!!, counter, type).doFinal(frame, HEADER, frame.size - HEADER)
        when (type) {
            TEXT -> deliverText(String(plain, Charsets.UTF_8))
            BINARY -> deliverBinary(plain)
            else -> error("unknown frame type")
        }
    }

    override suspend fun sendText(text: String) = send(TEXT, text.toByteArray(Charsets.UTF_8))

    override suspend fun sendBinary(bytes: ByteArray) = send(BINARY, bytes)

    private suspend fun send(type: Byte, plain: ByteArray) {
        val key = sendKey ?: error("the secure channel is not ready")
        // Номер кадра и порядок отправки совпадают: шифруем и отправляем под одним замком.
        sendMutex.withLock {
            val counter = sendCounter++
            val sealed = cipher(Cipher.ENCRYPT_MODE, key, counter, type).doFinal(plain)
            val frame = ByteBuffer.allocate(HEADER + sealed.size).put(type).putLong(counter).put(sealed).array()
            inner.sendBinary(frame)
        }
    }

    private fun cipher(mode: Int, key: SecretKeySpec, counter: Long, type: Byte): Cipher {
        // 96-битный номер: ключ у каждого направления свой, поэтому номера не повторяются.
        val nonce = ByteBuffer.allocate(NONCE_BYTES).putInt(0).putLong(counter).array()
        return Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(mode, key, GCMParameterSpec(TAG_BYTES * 8, nonce))
            // Тип кадра входит в проверку: текстовый кадр нельзя выдать за двоичный.
            updateAAD(byteArrayOf(type))
        }
    }

    override suspend fun close() {
        runCatching { inner.close() }
        reader?.cancel()
        markClosed()
    }

    private companion object {
        val MAGIC = "EFIR-SEC1".toByteArray()
        const val HANDSHAKE_TIMEOUT_MS = 10_000L
        const val TEXT: Byte = 1
        const val BINARY: Byte = 2
        const val HEADER = 9
        const val NONCE_BYTES = 12
        const val TAG_BYTES = 16

        fun hmac(key: ByteArray, data: ByteArray): ByteArray =
            Mac.getInstance("HmacSHA256").apply { init(SecretKeySpec(key, "HmacSHA256")) }.doFinal(data)
    }
}
