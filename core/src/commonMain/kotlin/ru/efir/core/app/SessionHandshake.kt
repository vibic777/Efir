package ru.efir.core.app

import ru.efir.core.net.EfirSession
import ru.efir.core.pairing.AuthProof
import ru.efir.core.pairing.SecureTokens
import ru.efir.core.pairing.PairingService
import ru.efir.core.pairing.PairingVerdict
import ru.efir.core.pairing.TrustStore
import ru.efir.core.pairing.TrustedDevice
import ru.efir.core.protocol.Capability
import ru.efir.core.protocol.CapabilitiesResult
import ru.efir.core.protocol.DeviceInfo
import ru.efir.core.protocol.DeviceType
import ru.efir.core.protocol.Hello
import ru.efir.core.protocol.HelloAck
import ru.efir.core.protocol.PROTOCOL_VERSION
import ru.efir.core.protocol.PairRequest
import ru.efir.core.protocol.PairResponse
import ru.efir.core.protocol.RequestCapabilities
import ru.efir.core.protocol.nowMillis

/** Локальный участник соединения: кем представляется это приложение. */
data class LocalPeer(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val capabilities: Set<Capability> = emptySet(),
) {
    fun toDeviceInfo(): DeviceInfo = DeviceInfo(
        deviceId = deviceId,
        name = name,
        type = type,
        protocolVersion = PROTOCOL_VERSION,
        capabilities = capabilities,
    )
}

/** Итог клиентского handshake. */
data class HandshakeOutcome(
    val accepted: Boolean,
    val remote: DeviceInfo?,
    val sessionToken: String?,
    val reason: String?,
)

/**
 * Обработчики handshake на принимающей стороне (хост).
 *
 * Регистрирует на сессии ответы на [Hello], [PairRequest] и [RequestCapabilities].
 * Сопряжение делегируется [PairingService], доверие — [TrustStore].
 */
class HostHandshake(
    private val local: LocalPeer,
    private val pairing: PairingService,
    private val trustStore: TrustStore,
    /** Вызывается при успешном handshake: сессия готова к работе. */
    private val onReady: suspend (session: EfirSession, remote: DeviceInfo, trusted: Boolean) -> Unit = { _, _, _ -> },
) {

    /** Подключает обработчики к установленной сессии. */
    fun attach(session: EfirSession) {
        session.rpc.onRequest<Hello> { hello -> handleHello(session, hello) }
        session.rpc.onRequest<PairRequest> { request -> handlePair(session, request) }
        session.rpc.onRequest<RequestCapabilities> { CapabilitiesResult(local.capabilities) }
    }

    private suspend fun handleHello(session: EfirSession, hello: Hello): HelloAck {
        val remote = hello.device
        if (remote.protocolVersion > PROTOCOL_VERSION) {
            return HelloAck(
                device = local.toDeviceInfo(),
                accepted = false,
                reason = "Несовместимая версия протокола: удалённая ${remote.protocolVersion}, локальная $PROTOCOL_VERSION",
            )
        }

        // Повторное подключение доверенного устройства: оно подтверждает, что знает токен
        // доверия, подписью отпечатка этого канала. Сам токен по сети не идёт.
        val binding = session.connection.channelBinding
        val existing = trustStore.find(remote.deviceId)?.takeIf {
            AuthProof.matches(hello.tokenProof, it.sessionToken, binding, AuthProof.CLIENT)
        }
        val refreshed = existing?.copy(name = remote.name, lastSeenAt = nowMillis())

        // Незнакомое устройство получает только приветствие: сессия остаётся
        // «пустой» (без файлов, экрана, SMS), пока оно не пройдёт сопряжение
        // через PairRequest. Иначе любой узел сети получил бы полный доступ.
        if (refreshed == null) {
            return HelloAck(device = local.toDeviceInfo(), accepted = true, sessionToken = null)
        }

        trustStore.save(refreshed)
        onReady(session, remote, true)
        return HelloAck(
            device = local.toDeviceInfo(),
            accepted = true,
            // Встречное подтверждение: клиент убеждается, что говорит с тем же устройством.
            hostProof = AuthProof.of(refreshed.sessionToken, binding, AuthProof.HOST),
        )
    }

    private suspend fun handlePair(session: EfirSession, request: PairRequest): PairResponse {
        val binding = session.connection.channelBinding
        var hostProof: String? = null
        val verdict = pairing.verify(
            pairingToken = request.pairingToken,
            accessKey = request.accessKey,
            clientDeviceId = request.device.deviceId,
            clientName = request.device.name,
            clientType = request.device.type,
            keyCheck = { expected ->
                AuthProof.matches(request.keyProof, expected, binding, AuthProof.CLIENT).also { ok ->
                    if (ok) hostProof = AuthProof.of(expected, binding, AuthProof.HOST)
                }
            },
        )
        when (verdict) {
            is PairingVerdict.Accepted -> AppLog.i("pair", "paired with ${request.device.type} \"${request.device.name}\"")
            is PairingVerdict.Rejected -> AppLog.w("pair", "pairing of \"${request.device.name}\" refused: ${verdict.reason}")
        }
        return when (verdict) {
            is PairingVerdict.Accepted -> {
                // Именованные аргументы запрещены для функциональных типов — позиционно.
                onReady(
                    session,
                    DeviceInfo(verdict.trusted.deviceId, verdict.trusted.name, verdict.trusted.type),
                    true,
                )
                PairResponse(accepted = true, sessionToken = verdict.trusted.sessionToken, hostProof = hostProof)
            }

            is PairingVerdict.Rejected -> PairResponse(accepted = false, reason = verdict.reason)
        }
    }
}

/** Клиентский handshake: приветствие и (опционально) сопряжение по токену и ключу доступа. */
object ClientHandshake {

    /** Причина отказа, когда удалённая сторона не знает это устройство. */
    /** Другая сторона не подтвердила, что знает ключ доступа. */
    const val NOT_CONFIRMED = "Устройство не подтвердило ключ доступа: сопряжение отменено"

    const val NOT_PAIRED = "Устройство не сопряжено: введите данные сопряжения и ключ доступа"

    /**
     * Выполняет handshake на уже запущенной сессии.
     *
     * Соединение считается установленным, только если удалённая сторона
     * подтвердила доверие: вернула сессионный токен на [Hello] либо приняла
     * [PairRequest].
     *
     * @param previousSessionToken токен доверия для повторного подключения без QR.
     * @param pairingToken токен из QR-кода (для первичного сопряжения).
     * @param accessKey ключ доступа, отображаемый на принимающей стороне.
     * @param tokenFor сохранённый токен доверия по идентификатору устройства —
     *   нужен, когда адрес введён вручную и устройство узнаётся лишь из ответа.
     */
    suspend fun perform(
        session: EfirSession,
        local: LocalPeer,
        previousSessionToken: String? = null,
        pairingToken: String? = null,
        accessKey: String? = null,
        timeoutMs: Long = 10_000,
        tokenFor: suspend (deviceId: String) -> String? = { null },
    ): HandshakeOutcome {
        val binding = session.connection.channelBinding
        val ack = hello(session, local, previousSessionToken, pairingToken, timeoutMs)
            ?: return HandshakeOutcome(false, null, null, "Неожиданный ответ на приветствие")

        if (!ack.accepted) {
            return HandshakeOutcome(false, ack.device, null, ack.reason)
        }

        if (accessKey != null) {
            // Ключ доступа по сети не идёт: идёт подтверждение, что он известен.
            val key = SecureTokens.normalizeAccessKey(accessKey)
            val pairRaw = session.rpc.call(
                PairRequest(
                    pairingToken = pairingToken.orEmpty(),
                    accessKey = "",
                    device = local.toDeviceInfo(),
                    keyProof = AuthProof.of(key, binding, AuthProof.CLIENT),
                ),
                timeoutMs = timeoutMs,
            )
            val pair = pairRaw as? PairResponse
                ?: return HandshakeOutcome(false, ack.device, null, "Неожиданный ответ сопряжения")
            if (!pair.accepted || pair.sessionToken == null) {
                return HandshakeOutcome(false, ack.device, null, pair.reason ?: "Сопряжение отклонено")
            }
            // Принявший тоже обязан знать ключ: иначе это не то устройство, которому мы его вводили.
            if (!AuthProof.matches(pair.hostProof, key, binding, AuthProof.HOST)) {
                return HandshakeOutcome(false, ack.device, null, NOT_CONFIRMED)
            }
            return HandshakeOutcome(true, ack.device, pair.sessionToken, null)
        }

        if (previousSessionToken != null && AuthProof.matches(ack.hostProof, previousSessionToken, binding, AuthProof.HOST)) {
            return HandshakeOutcome(true, ack.device, previousSessionToken, null)
        }

        // Устройство стало известно только из ответа: пробуем сохранённый токен.
        val known = tokenFor(ack.device.deviceId)
        if (known != null && known != previousSessionToken) {
            val second = hello(session, local, known, null, timeoutMs)
            if (second != null && second.accepted && AuthProof.matches(second.hostProof, known, binding, AuthProof.HOST)) {
                return HandshakeOutcome(true, second.device, known, null)
            }
        }
        return HandshakeOutcome(false, ack.device, null, NOT_PAIRED)
    }

    private suspend fun hello(
        session: EfirSession,
        local: LocalPeer,
        sessionToken: String?,
        pairingToken: String?,
        timeoutMs: Long,
    ): HelloAck? = session.rpc.call(
        Hello(
            device = local.toDeviceInfo(),
            pairToken = pairingToken,
            tokenProof = sessionToken?.let { AuthProof.of(it, session.connection.channelBinding, AuthProof.CLIENT) },
        ),
        timeoutMs = timeoutMs,
    ) as? HelloAck
}
