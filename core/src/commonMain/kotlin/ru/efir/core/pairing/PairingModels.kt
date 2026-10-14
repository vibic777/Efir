package ru.efir.core.pairing

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.efir.core.protocol.DeviceType

/**
 * Данные сопряжения, кодируемые в QR-код принимающей стороной.
 *
 * @param deviceId идентификатор принимающего устройства.
 * @param deviceName отображаемое имя принимающего устройства.
 * @param type тип принимающего устройства.
 * @param host адрес для подключения (IP в локальной сети).
 * @param port порт control-канала.
 * @param fingerprint SHA-256 отпечаток TLS-сертификата (hex).
 * @param pairingToken одноразовый токен сопряжения.
 * @param expiresAt время истечения токена (epoch millis).
 */
@Serializable
data class PairingPayload(
    val deviceId: String,
    val deviceName: String,
    val type: DeviceType,
    val host: String,
    val port: Int,
    val fingerprint: String?,
    val pairingToken: String,
    val expiresAt: Long,
)

/** Доверенное устройство, сохранённое после успешного сопряжения. */
@Serializable
data class TrustedDevice(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val fingerprint: String?,
    val sessionToken: String,
    val pairedAt: Long,
    val lastSeenAt: Long,
    /** Адрес, по которому к устройству подключались в прошлый раз: туда идём, когда оно не найдено в сети само. */
    val lastHost: String? = null,
    val lastPort: Int = 0,
)

/** Кодек данных сопряжения (использует общий JSON протокола). */
object PairingCodec {
    private val json get() = ru.efir.core.protocol.ProtocolCodec.json
    fun encode(payload: PairingPayload): String = json.encodeToString(payload)
    fun decode(text: String): PairingPayload = json.decodeFromString(text)

    fun encodeTrusted(device: TrustedDevice): String = json.encodeToString(device)
    fun decodeTrusted(text: String): TrustedDevice = json.decodeFromString(text)
}
