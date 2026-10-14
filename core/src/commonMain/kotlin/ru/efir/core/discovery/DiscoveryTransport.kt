package ru.efir.core.discovery

/**
 * Транспорт обнаружения (UDP-бродкаст). Реализуется платформенно
 * (см. actual-функцию [createDiscoveryTransport]).
 */
interface DiscoveryTransport {

    /**
     * Запускает прослушивание. В принятом анонсе адрес [DiscoveryAnnounce.host] —
     * тот, с которого пакет пришёл на самом деле: он верен, даже если у отправителя
     * несколько сетей или адрес сменился после запуска.
     */
    fun start(onAnnounce: (DiscoveryAnnounce) -> Unit)

    /** Останавливает транспорт и освобождает сокеты. */
    fun stop()

    /** Немедленно рассылает анонс всей сети (по всем сетевым интерфейсам, какие есть сейчас). */
    fun announce(announce: DiscoveryAnnounce)

    /** Отправляет анонс одному устройству — ответ на его поиск. */
    fun sendTo(announce: DiscoveryAnnounce, host: String) {}

    /**
     * Отправляет анонс каждому адресу из списка по отдельности. Нужна поиску: телефон
     * на Android пакеты «всей сети» по Wi-Fi часто отбрасывает, а адресованные ему — принимает.
     */
    fun sendToAll(announce: DiscoveryAnnounce, hosts: List<String>) {
        hosts.forEach { sendTo(announce, it) }
    }
}

/**
 * Создаёт платформенный UDP-транспорт обнаружения.
 *
 * @param discoveryPort порт для приёма/рассылки анонсов.
 * @param announceProvider функция, возвращающая текущий анонс этого устройства
 *   (вызывается периодически для рассылки).
 * @param announceIntervalMs интервал рассылки анонсов.
 */
expect fun createDiscoveryTransport(
    discoveryPort: Int,
    announceIntervalMs: Long = 3_000,
): DiscoveryTransport
