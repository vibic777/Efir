package ru.efir.core.app

import ru.efir.core.protocol.DeviceType

/**
 * Локальная информация об устройстве, используемая при обнаружении и сопряжении.
 */
data class LocalDeviceInfo(
    val deviceId: String,
    val name: String,
    val type: DeviceType,
    val host: String,
)

/**
 * Возвращает локальную информацию об устройстве (id, имя, тип, адрес в LAN).
 * Реализуется платформенно.
 */
expect fun localDeviceInfo(): LocalDeviceInfo
