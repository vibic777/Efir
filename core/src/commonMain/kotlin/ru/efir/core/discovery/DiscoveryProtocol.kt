package ru.efir.core.discovery

import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import ru.efir.core.protocol.Capability
import ru.efir.core.protocol.DeviceType
import ru.efir.core.protocol.PROTOCOL_VERSION
import ru.efir.core.protocol.ProtocolCodec

/**
 * UDP-анонс устройства в локальной сети.
 *
 * Идёт на [ru.efir.core.net.EfirNetwork.DISCOVERY_PORT] и служит для
 * авто-обнаружения без участия интернета. Рассылает его только то устройство,
 * на котором открыт поиск ([probe]); остальные отвечают ему адресно.
 */
@Serializable
data class DiscoveryAnnounce(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val host: String,
    val port: Int,
    val capabilities: Set<Capability> = emptySet(),
    val protocolVersion: Int = PROTOCOL_VERSION,
    val timestamp: Long,
    /**
     * Это поиск: отправитель просит устройства, которые ждут подключения, ответить
     * своим анонсом. Сами по себе устройства в сеть ничего не рассылают.
     */
    val probe: Boolean = false,
    /** Устройство показывает ключ доступа и ждёт сопряжения. */
    val waitingToPair: Boolean = false,
)

/** Найденное устройство с временем последнего анонса (для TTL). */
data class DiscoveredDevice(
    val announce: DiscoveryAnnounce,
    val lastSeenAt: Long,
) {
    val deviceId: String get() = announce.deviceId
}

/** Кодек для (де)сериализации UDP-анонсов (использует общий JSON протокола). */
object DiscoveryCodec {
    fun encode(announce: DiscoveryAnnounce): String = ProtocolCodec.json.encodeToString(announce)
    fun decode(text: String): DiscoveryAnnounce = ProtocolCodec.json.decodeFromString(text)
}
