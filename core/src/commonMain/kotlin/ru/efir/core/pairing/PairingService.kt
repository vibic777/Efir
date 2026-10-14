package ru.efir.core.pairing

import ru.efir.core.protocol.DeviceType
import ru.efir.core.protocol.nowMillis

/** Результат проверки запроса сопряжения на принимающей стороне. */
sealed interface PairingVerdict {
    data class Accepted(val trusted: TrustedDevice) : PairingVerdict
    data class Rejected(val reason: String) : PairingVerdict
}

/**
 * Логика сопряжения (не зависит от платформы и UI).
 *
 * Принимающая сторона создаёт [PairingPayload] с одноразовым токеном и ключом
 * доступа, клиент присылает токен и ключ, сервис проверяет их и выдаёт доверие.
 */
class PairingService(
    private val trustStore: TrustStore,
    private val fingerprint: String?,
    /** Адрес этого устройства для данных сопряжения; обновляется, когда сеть меняется. */
    var host: String,
    private val port: Int,
    private val payloadTtlMs: Long = 2 * 60 * 1000,
) {

    /** Активная сессия сопряжения: payload и ожидаемый ключ доступа. */
    data class PendingPairing(val payload: PairingPayload, val accessKey: String)

    private var pending: PendingPairing? = null
    private var failedAttempts = 0

    /** Идёт ли сейчас сопряжение (ключ ещё не использован и не отменён). */
    val hasPending: Boolean get() = pending != null

    /** Создаёт новую сессию сопряжения (для отображения QR + ключа доступа). */
    fun begin(
        deviceId: String,
        deviceName: String,
        type: DeviceType,
    ): PendingPairing {
        val now = nowMillis()
        val session = PendingPairing(
            payload = PairingPayload(
                deviceId = deviceId,
                deviceName = deviceName,
                type = type,
                host = host,
                port = port,
                fingerprint = fingerprint,
                pairingToken = SecureTokens.token(),
                expiresAt = now + payloadTtlMs,
            ),
            accessKey = SecureTokens.accessKey(),
        )
        pending = session
        failedAttempts = 0
        return session
    }

    /** Отменяет активную сессию сопряжения. */
    fun cancel() {
        pending = null
    }

    /**
     * Проверяет присланные клиентом токен и ключ доступа.
     * При успехе сохраняет доверенное устройство и возвращает сессионный токен.
     *
     * Ключ сравнивается регистронезависимо и игнорируя дефисы/пробелы, чтобы
     * пользователь мог ввести его как с разделителями, так и без.
     */
    suspend fun verify(
        pairingToken: String,
        accessKey: String,
        clientDeviceId: String,
        clientName: String,
        clientType: DeviceType,
        /**
         * Проверка подтверждения вместо сравнения самого ключа: получает ожидаемый ключ
         * (в каноническом виде) и говорит, подтвердил ли клиент его знание.
         */
        keyCheck: ((expectedKey: String) -> Boolean)? = null,
    ): PairingVerdict {
        val session = pending ?: return PairingVerdict.Rejected("Нет активной сессии сопряжения")
        val now = nowMillis()
        if (now > session.payload.expiresAt) {
            pending = null
            return PairingVerdict.Rejected("Срок действия QR-кода истёк")
        }
        // Токен из QR необязателен: устройство, выбранное из списка «рядом», знает
        // только ключ доступа (62 бита, попытки ограничены). Присланный токен обязан совпасть.
        val tokenMatches = pairingToken.isEmpty() || pairingToken == session.payload.pairingToken
        val expectedKey = SecureTokens.normalizeAccessKey(session.accessKey)
        val keyMatches = keyCheck?.invoke(expectedKey) ?: (SecureTokens.normalizeAccessKey(accessKey) == expectedKey)
        if (!tokenMatches || !keyMatches) {
            // Перебор ключа: после нескольких промахов сопряжение начинается заново.
            failedAttempts++
            if (failedAttempts >= MAX_FAILED_ATTEMPTS) {
                pending = null
                return PairingVerdict.Rejected("Слишком много неверных попыток — начните сопряжение заново")
            }
            return PairingVerdict.Rejected(
                if (tokenMatches) "Неверный ключ доступа" else "Неверный токен сопряжения",
            )
        }

        val trusted = TrustedDevice(
            deviceId = clientDeviceId,
            name = clientName,
            type = clientType,
            fingerprint = fingerprint,
            sessionToken = SecureTokens.token(),
            pairedAt = now,
            lastSeenAt = now,
        )
        trustStore.save(trusted)
        pending = null
        return PairingVerdict.Accepted(trusted)
    }

    /** Проверяет, известно ли устройство и валиден ли его сессионный токен. */
    suspend fun isTrusted(deviceId: String, sessionToken: String): Boolean {
        val device = trustStore.find(deviceId) ?: return false
        return device.sessionToken == sessionToken
    }

    private companion object {
        /** Сколько неверных попыток допускается на одну сессию сопряжения. */
        const val MAX_FAILED_ATTEMPTS = 5
    }
}
