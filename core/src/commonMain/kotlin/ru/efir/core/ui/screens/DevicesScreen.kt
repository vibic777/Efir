package ru.efir.core.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import ru.efir.core.app.AppVisibility
import ru.efir.core.app.EfirController
import ru.efir.core.app.LinkRole
import ru.efir.core.app.platformPermissions
import ru.efir.core.discovery.DiscoveredDevice
import ru.efir.core.pairing.PairingCodec
import ru.efir.core.pairing.QrCodeView
import ru.efir.core.pairing.SecureTokens
import ru.efir.core.pairing.TrustedDevice
import ru.efir.core.protocol.nowMillis
import ru.efir.core.ui.AccessKeyField
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.Button
import ru.efir.core.ui.ErrorText
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.Hint
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.ListRow
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.Mono
import ru.efir.core.ui.OutlinedButton
import ru.efir.core.ui.onEnter
import ru.efir.core.ui.OutlinedTextField
import ru.efir.core.ui.ScreenTabs
import ru.efir.core.ui.SectionCard
import ru.efir.core.ui.SwitchRow
import ru.efir.core.ui.TabItem
import ru.efir.core.ui.Text
import ru.efir.core.ui.TextButton
import ru.efir.core.ui.theme.positive

/** Порог, после которого устройство в списке считается пропавшим из сети. */
private const val DEVICE_STALE_MS = 6_000L

/** Кому вводится ключ доступа: устройство из списка или адрес, набранный руками. */
private class PairTarget(val name: String, val host: String, val port: Int, val deviceId: String?)

/**
 * Экран «Устройства»: состояние соединения, новое подключение («Ждать с ключом» на
 * одном устройстве, «Найти устройство» на другом), устройства рядом, это
 * устройство с его адресами на сейчас и список доверенных.
 *
 * Поиск в сети идёт, только пока экран на виду: с уходом с него или сворачиванием
 * приложения рассылка прекращается.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DevicesScreen(controller: EfirController, onOpenSettings: () -> Unit) {
    val s = LocalStrings.current
    val devices by controller.devices.collectAsState()
    val trusted by controller.trustedDevices.collectAsState()
    val connection by controller.connection.collectAsState()
    val connecting by controller.connecting.collectAsState()
    val error by controller.connectionError.collectAsState()
    val pairingInfo by controller.pairingInfo.collectAsState()
    val listening by controller.listening.collectAsState()
    val addresses by controller.selfAddresses.collectAsState()
    val visible by AppVisibility.visible.collectAsState()

    // Поиск и перечитывание сети — пока экран перед глазами.
    DisposableEffect(visible) {
        controller.setSearching(visible)
        if (visible) controller.refreshNetwork()
        onDispose { controller.setSearching(false) }
    }

    // Секундный такт: «видели N с назад» и обратный отсчёт ключа.
    var now by remember { mutableLongStateOf(nowMillis()) }
    LaunchedEffect(visible) {
        while (visible) {
            now = nowMillis()
            delay(1_000)
        }
    }

    var tab by remember { mutableStateOf(0) }
    var finding by remember { mutableStateOf(false) }
    var pairTarget by remember { mutableStateOf<PairTarget?>(null) }
    var revoke by remember { mutableStateOf<TrustedDevice?>(null) }
    val trustedIds = trusted.map { it.deviceId }.toSet()
    val port = controller.selfPort
    val online = addresses.any { it != "127.0.0.1" && it != "0.0.0.0" }

    ScreenColumn(HelpTopic.DEVICES) {
        // Вкладки вместо одной длинной страницы: соединение и новое подключение, найденные рядом, доверенные, это устройство.
        ScreenTabs(
            tabs = listOf(
                TabItem(AppIcons.Link, s.tabConnection),
                TabItem(AppIcons.Search, s.tabNearby, devices.size),
                TabItem(AppIcons.Lock, s.tabTrusted, trusted.size),
                TabItem(deviceIcon(controller.selfType), s.thisDevice),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        // --- Соединение ---
        if (tab == 0) SectionCard(s.connection, icon = AppIcons.Link) {
            val link = connection
            when {
                link != null -> {
                    ListRow(
                        title = link.remote.name,
                        icon = deviceIcon(link.remote.type),
                        iconTint = MaterialTheme.colorScheme.positive,
                        subtitle = listOfNotNull(
                            s.platformName(link.remote.type),
                            link.remoteAddress,
                            if (link.linkRole == LinkRole.HOST) s.linkIncoming else s.linkOutgoing,
                        ).joinToString(" · "),
                    ) {
                        OutlinedButton(onClick = { controller.disconnect() }, tip = s.tipDisconnect) { Text(s.disconnect) }
                    }
                }

                connecting -> {
                    Text(s.connectingNow, style = MaterialTheme.typography.bodyMedium)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }

                else -> Hint(s.noConnectionHint)
            }
            error?.let { message ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ErrorText(message, Modifier.weight(1f))
                    IconAction(AppIcons.Close, s.hideMessage, { controller.clearConnectionError() })
                }
            }
        }

        // --- Разрешения (Android) ---
        val missing = remember(now / 2_000) { platformPermissions().count { it.granted == false } }
        if (tab == 0 && missing > 0) {
            SectionCard(s.permissionsMissing(missing), icon = AppIcons.Warning, topic = HelpTopic.PERMISSIONS) {
                Hint(s.permissionsMissingHint)
                Button(onClick = onOpenSettings, tip = s.tipOpenPermissions) { Text(s.openSettings) }
            }
        }

        // --- Новое подключение ---
        if (tab == 0) SectionCard(s.pairing, icon = AppIcons.Key) {
            Hint(s.pairingHint)
            val pending = pairingInfo
            if (pending == null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { controller.refreshNetwork(); controller.beginPairing() }, tip = s.tipWaitKey) { Text(s.showKey) }
                    OutlinedButton(onClick = { controller.refreshNetwork(); finding = true }, tip = s.tipFindDevice) { Text(s.findDevice) }
                }
            } else {
                val left = (pending.payload.expiresAt - now) / 1000
                var showQr by remember { mutableStateOf(false) }
                val payloadJson = remember(pending) { PairingCodec.encode(pending.payload) }
                HorizontalDivider()
                Hint(s.keyTitle)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = SecureTokens.formatAccessKey(pending.accessKey),
                        style = MaterialTheme.typography.headlineMedium,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = if (left > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.weight(1f),
                    )
                    IconAction(AppIcons.ViewGrid, if (showQr) s.hideQr else s.showQr, { showQr = !showQr })
                    IconAction(AppIcons.ContentCopy, s.copyPairingData, { controller.copyToLocalClipboard(payloadJson) })
                    IconAction(AppIcons.Close, s.cancelPairing, { controller.cancelPairing() })
                }
                if (left > 0) {
                    Hint(s.keyValidFor(left))
                    if (online) Hint(s.keyAddress(addresses.joinToString(", ") { "$it:$port" })) else ErrorText(s.noLocalNetwork)
                } else {
                    ErrorText(s.keyExpired)
                    Button(onClick = { controller.refreshNetwork(); controller.beginPairing() }, tip = s.tipWaitKey) { Text(s.newKey) }
                }
                if (showQr) {
                    QrCodeView(text = payloadJson, modifier = Modifier.align(Alignment.CenterHorizontally))
                    Hint(s.qrHint)
                }
            }
        }

        // --- Устройства рядом ---
        if (tab == 1) SectionCard(
            title = s.nearby(devices.size),
            icon = AppIcons.Search,
            topic = HelpTopic.NETWORK,
            actions = { IconAction(AppIcons.Refresh, s.recheckNetwork, { controller.refreshNetwork() }) },
        ) {
            if (devices.isEmpty()) {
                Hint(s.searching)
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Hint(s.searchingHint)
            }
            devices.forEach { device ->
                NearbyRow(
                    device = device,
                    now = now,
                    known = device.deviceId in trustedIds,
                    current = connection?.remote?.deviceId == device.deviceId,
                    busy = connecting,
                    onConnect = {
                        controller.connectTo(
                            hostAddress = device.announce.host,
                            port = device.announce.port,
                            deviceId = device.deviceId,
                        )
                    },
                    onPair = { pairTarget = device.asTarget() },
                )
            }
        }

        // --- Это устройство ---
        if (tab == 3) SectionCard(
            title = s.thisDevice,
            icon = deviceIcon(controller.selfType),
            topic = HelpTopic.NETWORK,
            actions = { IconAction(AppIcons.Refresh, s.recheckNetwork, { controller.refreshNetwork() }) },
        ) {
            ListRow(title = controller.selfName, subtitle = s.platformName(controller.selfType))
            if (online) {
                Hint(s.addressesNow)
                addresses.forEach { Mono("$it:$port") }
            } else {
                ErrorText(s.noLocalNetwork)
            }
            SwitchRow(
                title = s.listen,
                subtitle = s.listenHint,
                checked = listening,
                onChange = { controller.setListeningEnabled(it) },
            )
        }

        // --- Доверенные ---
        if (tab == 2) SectionCard(s.trustedTitle(trusted.size), icon = AppIcons.Lock, topic = HelpTopic.TRUSTED) {
            if (trusted.isEmpty()) Hint(s.trustedEmpty)
            trusted.forEach { device ->
                val near = devices.firstOrNull { it.deviceId == device.deviceId }
                // Не найдено в сети само — идём по адресу прошлого подключения.
                val host = near?.announce?.host ?: device.lastHost
                val hostPort = near?.announce?.port ?: device.lastPort.takeIf { it > 0 } ?: port
                ListRow(
                    title = device.name,
                    icon = deviceIcon(device.type),
                    subtitle = listOfNotNull(
                        s.platformName(device.type),
                        s.pairedAt(formatDateTime(device.pairedAt)),
                        if (near != null) s.online else device.lastHost?.let { s.lastAddress("$it:$hostPort") },
                    ).joinToString(" · "),
                ) {
                    if (host != null && connection?.remote?.deviceId != device.deviceId) {
                        IconAction(AppIcons.Link, s.connect, {
                            controller.connectTo(host, hostPort, deviceId = device.deviceId)
                        }, enabled = !connecting)
                    }
                    IconAction(AppIcons.Delete, s.revoke, { revoke = device })
                }
            }
        }
    }

    if (finding) {
        FindDeviceDialog(
            nearby = devices,
            trustedIds = trustedIds,
            defaultPort = port,
            onRecheck = { controller.refreshNetwork() },
            onDismiss = { finding = false },
            onChosen = { target ->
                finding = false
                pairTarget = target
            },
            onConnect = { device ->
                finding = false
                controller.connectTo(
                    hostAddress = device.announce.host,
                    port = device.announce.port,
                    deviceId = device.deviceId,
                )
            },
            onPairingData = { json ->
                val payload = runCatching { PairingCodec.decode(json) }.getOrNull()
                if (payload != null) {
                    finding = false
                    pairTarget = PairTarget(payload.deviceName, payload.host, payload.port, payload.deviceId)
                }
                payload != null
            },
        )
    }

    pairTarget?.let { target ->
        KeyDialog(
            target = target,
            onDismiss = { pairTarget = null },
            onPair = { key ->
                controller.connectTo(target.host, port = target.port, accessKey = key, deviceId = target.deviceId)
                pairTarget = null
            },
        )
    }

    revoke?.let { device ->
        ConfirmDialog(
            title = s.revokeTitle,
            text = s.revokeText(device.name),
            confirm = s.revoke,
            onConfirm = {
                controller.revokeTrust(device.deviceId)
                revoke = null
            },
            onDismiss = { revoke = null },
        )
    }
}

private fun DiscoveredDevice.asTarget() = PairTarget(announce.name, announce.host, announce.port, deviceId)

@Composable
private fun NearbyRow(
    device: DiscoveredDevice,
    now: Long,
    known: Boolean,
    current: Boolean,
    busy: Boolean,
    onConnect: () -> Unit,
    onPair: () -> Unit,
) {
    val s = LocalStrings.current
    val ageMs = (now - device.lastSeenAt).coerceAtLeast(0)
    val stale = ageMs > DEVICE_STALE_MS
    ListRow(
        title = device.announce.name,
        icon = deviceIcon(device.announce.type),
        iconTint = if (stale) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
        subtitle = listOfNotNull(
            s.platformName(device.announce.type),
            "${device.announce.host}:${device.announce.port}",
            if (stale) s.seenAgo(ageMs / 1000) else s.online,
            if (known) s.paired else if (device.announce.waitingToPair) s.foundWaiting else s.notPaired,
        ).joinToString(" · "),
    ) {
        when {
            current -> Text(s.connected, color = MaterialTheme.colorScheme.positive, modifier = Modifier.padding(end = 8.dp))
            known -> Button(onClick = onConnect, enabled = !busy, tip = s.tipConnect) { Text(s.connect) }
            else -> OutlinedButton(onClick = onPair, enabled = !busy, tip = s.tipPair) { Text(s.pair) }
        }
    }
}

/**
 * «Найти устройство»: найденные в сети (ждущие с ключом — первыми), кнопка
 * «Проверить сеть» и адрес, набранный руками, — для сети, где устройства не видят
 * друг друга сами. В поле адреса годятся `192.168.1.5`, `192.168.1.5:47812` и
 * данные сопряжения целиком (то, что копирует кнопка рядом с ключом или хранит QR-код).
 */
@Composable
private fun FindDeviceDialog(
    nearby: List<DiscoveredDevice>,
    trustedIds: Set<String>,
    defaultPort: Int,
    onRecheck: () -> Unit,
    onDismiss: () -> Unit,
    onChosen: (PairTarget) -> Unit,
    onConnect: (DiscoveredDevice) -> Unit,
    onPairingData: (String) -> Boolean,
) {
    val s = LocalStrings.current
    val clipboard = LocalClipboardManager.current
    var address by remember { mutableStateOf("") }
    var problem by remember { mutableStateOf<String?>(null) }
    // Показываются все найденные: и новые, и уже сопряжённые. Раньше сопряжённые скрывались,
    // и казалось, что поиск устройство не нашёл.
    val candidates = nearby

    // «Далее» по адресу, набранному руками: то же делает Enter в поле.
    fun submit() {
        val text = address.trim()
        if (text.isEmpty()) return
        when {
            text.startsWith("{") -> if (!onPairingData(text)) problem = s.pairingDataError
            else -> {
                val host = text.substringBeforeLast(':', text).trim()
                val port = text.substringAfterLast(':', "").toIntOrNull() ?: defaultPort
                if (host.isBlank() || host.any { it.isWhitespace() } || port !in 1..65535) {
                    problem = s.addressError
                } else {
                    onChosen(PairTarget(host, host, port, null))
                }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.findDevice) },
        text = {
            Column(
                Modifier.heightIn(max = 460.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(s.pairDialogHint, style = MaterialTheme.typography.bodyMedium)
                if (candidates.isEmpty()) {
                    Hint(s.searching)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
                candidates.forEach { device ->
                    // Сопряжённое устройство подключается сразу, без ключа. Если же оно показывает ключ
                    // (его сопрягают заново — например, после переустановки), идём к вводу ключа.
                    val known = device.deviceId in trustedIds && !device.announce.waitingToPair
                    OutlinedButton(
                        onClick = { if (known) onConnect(device) else onChosen(device.asTarget()) },
                        modifier = Modifier.fillMaxWidth(),
                        tip = if (known) s.tipConnect else s.tipPair,
                    ) {
                        Column(Modifier.fillMaxWidth()) {
                            Text("${device.announce.name}  (${device.announce.host}:${device.announce.port})")
                            androidx.compose.material3.Text(
                                if (known) s.foundPaired else if (device.announce.waitingToPair) s.foundWaiting else s.foundNotWaiting,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (device.announce.waitingToPair) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }
                OutlinedButton(onClick = onRecheck, tip = s.tipRecheck) { Text(s.recheckNetwork) }
                HorizontalDivider()
                Hint(s.manualHint)
                OutlinedTextField(
                    value = address,
                    onValueChange = {
                        address = it
                        problem = null
                    },
                    label = { Text(s.addressOrData) },
                    singleLine = true,
                    isError = problem != null,
                    trailingIcon = {
                        IconAction(AppIcons.ContentPaste, s.paste, {
                            clipboard.getText()?.text?.let { address = it.trim() }
                        })
                    },
                    modifier = Modifier.fillMaxWidth().onEnter { submit() },
                    tip = s.tipAddressField,
                )
                problem?.let { ErrorText(it) }
                Mono(s.addressExample(defaultPort))
            }
        },
        confirmButton = {
            TextButton(
                onClick = { submit() },
                enabled = address.isNotBlank(),
                tip = s.tipUseAddress,
            ) { Text(s.useAddress) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}

/** Ввод ключа доступа, показанного на устройстве [target]. */
@Composable
private fun KeyDialog(target: PairTarget, onDismiss: () -> Unit, onPair: (key: String) -> Unit) {
    val s = LocalStrings.current
    var key by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.pairWith(target.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(s.enterKeyFor(target.name), style = MaterialTheme.typography.bodyMedium)
                Mono("${target.host}:${target.port}")
                AccessKeyField(
                    value = key,
                    onValueChange = { key = it },
                    label = s.accessKey,
                    // Enter сопрягает, как кнопка «Сопрячь», — когда ключ введён целиком.
                    modifier = Modifier.fillMaxWidth().onEnter { if (SecureTokens.isAccessKeyValid(key)) onPair(key) },
                    tip = s.tipKeyField,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onPair(key) },
                enabled = SecureTokens.isAccessKeyValid(key),
                tip = s.tipPairAndConnect,
            ) { Text(s.pairAndConnect) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(s.cancel) } },
    )
}
