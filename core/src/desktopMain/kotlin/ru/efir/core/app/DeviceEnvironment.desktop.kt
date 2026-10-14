package ru.efir.core.app

import ru.efir.core.protocol.DeviceType
import java.net.Inet4Address
import java.net.NetworkInterface

actual fun localDeviceInfo(): LocalDeviceInfo {
    val name = System.getProperty("user.name")
        ?.let { "ПК ($it)" }
        ?: (System.getProperty("os.name") ?: "PC")

    return LocalDeviceInfo(
        deviceId = "win-" + (System.getProperty("user.name") ?: "pc").hashCode().toUInt().toString(16),
        name = name,
        type = DeviceType.WINDOWS,
        host = bestLocalIpv4(),
    )
}

/** Находит локальный IPv4-адрес активного интерфейса. */
private fun bestLocalIpv4(): String {
    runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (!ni.isUp || ni.isLoopback) continue
            ni.inetAddresses.toList().forEach { addr ->
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress ?: "127.0.0.1"
                }
            }
        }
    }
    return "127.0.0.1"
}
