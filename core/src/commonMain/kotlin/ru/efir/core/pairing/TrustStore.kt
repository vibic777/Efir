package ru.efir.core.pairing

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Хранилище доверенных устройств. */
interface TrustStore {
    suspend fun all(): List<TrustedDevice>
    suspend fun find(deviceId: String): TrustedDevice?
    suspend fun save(device: TrustedDevice)
    suspend fun remove(deviceId: String)
}

/** Простое хранилище в памяти (заменяется платформенным персистентным позже). */
class InMemoryTrustStore : TrustStore {
    private val mutex = Mutex()
    private val devices = mutableMapOf<String, TrustedDevice>()

    override suspend fun all(): List<TrustedDevice> = mutex.withLock { devices.values.toList() }

    override suspend fun find(deviceId: String): TrustedDevice? = mutex.withLock { devices[deviceId] }

    override suspend fun save(device: TrustedDevice) {
        mutex.withLock { devices[device.deviceId] = device }
    }

    override suspend fun remove(deviceId: String) {
        mutex.withLock { devices.remove(deviceId) }
    }
}
