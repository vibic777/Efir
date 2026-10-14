package ru.efir.core.net

import java.net.Inet4Address
import java.net.NetworkInterface

actual fun localIpv4Addresses(): List<String> = runCatching {
    NetworkInterface.getNetworkInterfaces().toList()
        .filter { it.isUp && !it.isLoopback && !it.isVirtual && !isVirtualAdapterName(it.displayName ?: it.name, it.name) }
        .flatMap { it.inetAddresses.toList() }
        .filterIsInstance<Inet4Address>()
        .filter { it.isSiteLocalAddress && !it.isLoopbackAddress && !it.isLinkLocalAddress }
        .mapNotNull { it.hostAddress }
        .distinct()
}.getOrDefault(emptyList())

actual fun isPortFree(port: Int): Boolean = runCatching {
    java.net.ServerSocket().use { socket ->
        socket.reuseAddress = false
        socket.bind(java.net.InetSocketAddress(port))
    }
    true
}.getOrDefault(false)

actual fun localSubnetHosts(): List<String> {
    val own = localIpv4Addresses()
    return own.flatMap { address ->
        val parts = address.split('.')
        if (parts.size != 4) emptyList() else (1..254).map { "${parts[0]}.${parts[1]}.${parts[2]}.$it" }
    }.filter { it !in own }.distinct()
}
