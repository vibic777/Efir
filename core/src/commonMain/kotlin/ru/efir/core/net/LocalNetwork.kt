package ru.efir.core.net

/**
 * IPv4-адреса этого устройства в локальной сети — сейчас, а не на момент запуска:
 * Wi-Fi мог смениться на кабель, VPN — выключиться, телефон — перейти в другую сеть.
 * Виртуальные адаптеры (Hyper-V, VirtualBox, Docker, VPN) пропускаются.
 */
expect fun localIpv4Addresses(): List<String>

/**
 * Адреса соседей по локальной сети — все адреса подсети каждого из [localIpv4Addresses],
 * кроме своих. Сеть шире 254 адресов берётся как ближайшие 254: поиск не должен
 * рассылать тысячи пакетов.
 */
expect fun localSubnetHosts(): List<String>

/** Свободен ли TCP-порт на этом устройстве прямо сейчас (его никто не слушает). */
expect fun isPortFree(port: Int): Boolean

/**
 * Виртуальный ли адаптер, по имени. Windows показывает описание («VMware Virtual
 * Ethernet Adapter»), Linux и Android — только короткое имя интерфейса.
 */
fun isVirtualAdapterName(displayName: String, name: String): Boolean {
    val shown = displayName.lowercase()
    if (listOf("virtualbox", "vmware", "hyper-v", "vethernet", "wsl", "docker", "tap-", "tunnel", "vpn").any { it in shown }) return true
    val short = name.lowercase()
    return listOf("virbr", "veth", "docker", "br-", "tun", "tap", "wg", "zt", "tailscale", "lxc", "lxd", "vmnet", "vboxnet", "rmnet", "dummy")
        .any { short.startsWith(it) }
}
