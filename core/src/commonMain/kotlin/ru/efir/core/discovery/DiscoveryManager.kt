package ru.efir.core.discovery

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.efir.core.app.AppLog
import ru.efir.core.protocol.nowMillis

/**
 * Обнаружение устройств в локальной сети.
 *
 * Экономно по батарее: в покое устройство в сеть ничего не шлёт и ничего не
 * считает. Рассылку ведёт только то устройство, на котором открыт поиск
 * ([setSearching]): раз в [probeIntervalMs] оно спрашивает сеть, а устройства,
 * которые ждут подключения ([discoverable]), отвечают ему адресно. Список
 * найденных живёт, пока идёт поиск; молчащие дольше [ttlMs] из него уходят.
 */
class DiscoveryManager(
    private val transport: DiscoveryTransport,
    private val scope: CoroutineScope,
    private val selfDeviceId: String,
    private val ttlMs: Long = 10_000,
    private val probeIntervalMs: Long = 3_000,
) {
    private val mutex = Mutex()
    private val devices = mutableMapOf<String, DiscoveredDevice>()
    private val repliedAt = mutableMapOf<String, Long>()

    private val _found = MutableStateFlow<List<DiscoveredDevice>>(emptyList())

    /** Найденные устройства (пусто, пока поиск не идёт). */
    val found: StateFlow<List<DiscoveredDevice>> = _found.asStateFlow()

    /** Анонс этого устройства на сейчас: адрес и «ждёт сопряжения» меняются по ходу работы. */
    var selfAnnounce: () -> DiscoveryAnnounce? = { null }

    /** Отвечать ли на чужой поиск (устройство ждёт подключения). */
    @Volatile
    var discoverable: Boolean = false

    /** Адреса, по которым устройства подключались в прошлый раз: их спрашиваем при каждом опросе. */
    var knownHosts: () -> List<String> = { emptyList() }

    /** Адреса соседей по подсети: их обходим в начале поиска и изредка потом. */
    var subnetHosts: () -> List<String> = { emptyList() }

    private var probes = 0

    private var searchJob: Job? = null

    /** Идёт ли поиск. */
    val searching: Boolean get() = searchJob != null

    /** Начинает слушать сеть. Рассылки при этом нет. */
    fun start() {
        transport.start { announce ->
            if (announce.deviceId != selfDeviceId) scope.launch { onAnnounce(announce) }
        }
    }

    /** Останавливает всё и освобождает порт. */
    fun stop() {
        searchJob?.cancel()
        searchJob = null
        transport.stop()
    }

    /** Включает или выключает поиск: он нужен, только пока человек смотрит на список устройств. */
    fun setSearching(enabled: Boolean) {
        if (enabled == searching) return
        if (enabled) {
            probes = 0
            searchJob = scope.launch {
                while (isActive) {
                    probe()
                    delay(probeIntervalMs)
                    sweep()
                }
            }
        } else {
            searchJob?.cancel()
            searchJob = null
            scope.launch { clear() }
        }
    }

    /** Поиск заново: список очищается, сеть опрашивается сразу (сеть или адреса изменились). */
    fun refresh() {
        scope.launch {
            clear()
            probes = 0
            if (searching) probe()
        }
    }

    /** Разовый анонс: «я здесь» для тех, кто ищет прямо сейчас (включили ожидание, показали ключ). */
    fun announceSelf() {
        selfAnnounce()?.let { transport.announce(it.copy(probe = false, timestamp = nowMillis())) }
    }

    /**
     * Опрос сети. Вопрос «всем сразу» (широковещательный) до телефона на Android часто не
     * доходит: его Wi-Fi отбрасывает такие пакеты, пока приложение не на экране. Поэтому тот же
     * вопрос идёт ещё и адресно: каждому знакомому устройству по адресу прошлого раза — всякий
     * раз, и всем адресам подсети — в начале поиска и затем каждый [SWEEP_EVERY]-й опрос.
     */
    private fun probe() {
        val question = selfAnnounce()?.copy(probe = true, timestamp = nowMillis()) ?: return
        transport.announce(question)
        val known = runCatching { knownHosts() }.getOrDefault(emptyList())
        if (probes % SWEEP_EVERY == 0) {
            transport.sendToAll(question, (known + runCatching { subnetHosts() }.getOrDefault(emptyList())).distinct())
        } else if (known.isNotEmpty()) {
            transport.sendToAll(question, known)
        }
        probes++
    }

    private suspend fun onAnnounce(announce: DiscoveryAnnounce) {
        val reply = mutex.withLock {
            if (searching) {
                if (announce.deviceId !in devices) {
                    AppLog.i("net", "found ${announce.type} \"${announce.name}\" at ${announce.host}:${announce.port}" +
                        if (announce.waitingToPair) " (waiting to pair)" else "")
                }
                devices[announce.deviceId] = DiscoveredDevice(announce, nowMillis())
                publish()
            }
            // Ответ на поиск — не чаще раза в секунду одному адресу.
            val now = nowMillis()
            val due = announce.probe && discoverable && now - (repliedAt[announce.host] ?: 0L) >= REPLY_GAP_MS
            if (due) {
                repliedAt.entries.removeAll { now - it.value > ttlMs }
                repliedAt[announce.host] = now
            }
            due
        }
        if (reply) {
            selfAnnounce()?.let { transport.sendTo(it.copy(probe = false, timestamp = nowMillis()), announce.host) }
        }
    }

    private suspend fun clear() {
        mutex.withLock {
            devices.clear()
            publish()
        }
    }

    private suspend fun sweep() {
        mutex.withLock {
            val now = nowMillis()
            val before = devices.size
            devices.entries.removeAll { now - it.value.lastSeenAt > ttlMs }
            if (devices.size != before) publish()
        }
    }

    private fun publish() {
        // Ждущие сопряжения — первыми: к ним и идут с ключом.
        _found.value = devices.values.sortedWith(
            compareByDescending<DiscoveredDevice> { it.announce.waitingToPair }.thenBy { it.announce.name.lowercase() },
        )
    }

    private companion object {
        const val REPLY_GAP_MS = 1_000L

        /** Обход подсети — раз в столько опросов (опрос раз в 3 с, обход — раз в 15 с). */
        const val SWEEP_EVERY = 5
    }
}
