package ru.efir.core.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NetworkInterface

/**
 * Реализация [DiscoveryTransport] на UDP для JVM (Android и Desktop).
 *
 * Слушает [discoveryPort]. Сам ничего периодически не шлёт: рассылку и её темп
 * задаёт [DiscoveryManager]. Широковещательные адреса читаются заново при каждой
 * отправке, поэтому смена сети подхватывается сама.
 */
class UdpDiscoveryTransport(
    private val discoveryPort: Int,
) : DiscoveryTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var listenerSocket: DatagramSocket? = null
    private var listenerJob: Job? = null

    override fun start(onAnnounce: (DiscoveryAnnounce) -> Unit) {
        listenerJob = scope.launch {
            // Порт может быть занят вторым экземпляром приложения: тогда работаем без обнаружения.
            val socket = runCatching {
                DatagramSocket(null).apply {
                    reuseAddress = true
                    broadcast = true
                    bind(InetSocketAddress(discoveryPort))
                }
            }.getOrNull() ?: return@launch
            listenerSocket = socket
            listenLoop(socket, onAnnounce)
        }
    }

    private fun listenLoop(socket: DatagramSocket, onAnnounce: (DiscoveryAnnounce) -> Unit) {
        val buffer = ByteArray(8192)
        while (scope.isActive && !socket.isClosed) {
            try {
                val packet = DatagramPacket(buffer, buffer.size)
                socket.receive(packet)
                val text = String(packet.data, 0, packet.length, Charsets.UTF_8)
                val announce = runCatching { DiscoveryCodec.decode(text) }.getOrNull() ?: continue
                // Адрес — откуда пакет пришёл на самом деле, а не тот, что отправитель знал о себе при запуске.
                onAnnounce(announce.copy(host = packet.address?.hostAddress ?: announce.host))
            } catch (_: Exception) {
                if (socket.isClosed) break
            }
        }
    }

    override fun announce(announce: DiscoveryAnnounce) {
        scope.launch { send(announce, broadcastAddresses()) }
    }

    override fun sendTo(announce: DiscoveryAnnounce, host: String) {
        scope.launch {
            val address = runCatching { InetAddress.getByName(host) }.getOrNull() ?: return@launch
            send(announce, listOf(address))
        }
    }

    override fun sendToAll(announce: DiscoveryAnnounce, hosts: List<String>) {
        // Один сокет на весь список; адреса числовые, поиска имён нет.
        scope.launch { send(announce, hosts.mapNotNull { runCatching { InetAddress.getByName(it) }.getOrNull() }) }
    }

    private fun send(announce: DiscoveryAnnounce, targets: List<InetAddress>) {
        runCatching {
            val payload = DiscoveryCodec.encode(announce).toByteArray(Charsets.UTF_8)
            DatagramSocket().use { socket ->
                socket.broadcast = true
                targets.forEach { address ->
                    runCatching { socket.send(DatagramPacket(payload, payload.size, address, discoveryPort)) }
                }
            }
        }
    }

    /** Широковещательные адреса всех активных сетевых интерфейсов — на сейчас. */
    private fun broadcastAddresses(): List<InetAddress> {
        val result = mutableListOf<InetAddress>()
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (!ni.isUp || ni.isLoopback) continue
                ni.interfaceAddresses.forEach { ia -> ia.broadcast?.let { result.add(it) } }
            }
        }
        if (result.isEmpty()) {
            runCatching { result.add(InetAddress.getByName("255.255.255.255")) }
        }
        return result.distinct()
    }

    override fun stop() {
        listenerJob?.cancel()
        runCatching { listenerSocket?.close() }
        listenerSocket = null
        scope.cancel()
    }
}

actual fun createDiscoveryTransport(
    discoveryPort: Int,
    announceIntervalMs: Long,
): DiscoveryTransport = UdpDiscoveryTransport(discoveryPort)
