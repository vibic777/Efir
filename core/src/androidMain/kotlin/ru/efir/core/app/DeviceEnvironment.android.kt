package ru.efir.core.app

import android.os.Build
import ru.efir.core.protocol.DeviceType
import java.net.Inet4Address
import java.net.NetworkInterface

actual fun localDeviceInfo(): LocalDeviceInfo {
    val name = listOfNotNull(Build.MANUFACTURER, Build.MODEL)
        .joinToString(" ")
        .trim()
        .ifEmpty { "Android" }

    return LocalDeviceInfo(
        deviceId = "android-" + (Build.FINGERPRINT?.hashCode()?.toUInt()?.toString(16) ?: "device"),
        name = name,
        type = DeviceType.ANDROID,
        host = bestLocalIpv4(),
    )
}

/** Находит наиболее вероятный локальный IPv4-адрес (Wi-Fi). */
private fun bestLocalIpv4(): String {
    runCatching {
        val interfaces = NetworkInterface.getNetworkInterfaces()
        while (interfaces.hasMoreElements()) {
            val ni = interfaces.nextElement()
            if (!ni.isUp || ni.isLoopback) continue
            val name = ni.name.lowercase()
            if (name.startsWith("wlan") || name.startsWith("eth")) {
                ni.inetAddresses.toList().forEach { addr ->
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        return addr.hostAddress ?: "0.0.0.0"
                    }
                }
            }
        }
        // Резервный проход: любой IPv4 не-loopback.
        val interfaces2 = NetworkInterface.getNetworkInterfaces()
        while (interfaces2.hasMoreElements()) {
            val ni = interfaces2.nextElement()
            if (!ni.isUp || ni.isLoopback) continue
            ni.inetAddresses.toList().forEach { addr ->
                if (addr is Inet4Address && !addr.isLoopbackAddress) {
                    return addr.hostAddress ?: "0.0.0.0"
                }
            }
        }
    }
    return "0.0.0.0"
}
