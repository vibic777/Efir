package ru.efir.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import ru.efir.core.ui.Button
import ru.efir.core.ui.FilterChip
import androidx.compose.material3.MaterialTheme
import ru.efir.core.ui.OutlinedButton
import ru.efir.core.ui.OutlinedTextField
import ru.efir.core.ui.Switch
import ru.efir.core.ui.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.efir.core.app.ActiveConnection
import ru.efir.core.app.EfirController
import ru.efir.core.comms.ClipboardOrigin
import ru.efir.core.protocol.CallActionType
import ru.efir.core.protocol.CallState
import ru.efir.core.protocol.CallStateChanged
import ru.efir.core.protocol.DeviceType
import ru.efir.core.protocol.SmsDeliveryStatus
import ru.efir.core.protocol.SmsType
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.EmptyState
import ru.efir.core.ui.ErrorText
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.Hint
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.ListRow
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.Mono
import ru.efir.core.ui.ScreenTabs
import ru.efir.core.ui.SectionCard
import ru.efir.core.ui.TabItem
import ru.efir.core.ui.SwitchRow

/** Экран «Буфер и уведомления»: общий буфер обмена и уведомления телефона. */
@Composable
fun ClipboardScreen(controller: EfirController, connection: ActiveConnection) {
    val s = LocalStrings.current
    val history by controller.clipboardHistory.collectAsState()
    val notifications by controller.notifications.collectAsState()
    val filter by controller.notificationFilter.collectAsState()
    val knownPackages by controller.knownNotificationPackages.collectAsState()
    var replyTo by remember { mutableStateOf<String?>(null) }
    var replyText by remember { mutableStateOf("") }

    var tab by remember { mutableStateOf(0) }

    ScreenColumn(HelpTopic.CLIPBOARD) {
        ScreenTabs(
            tabs = listOf(
                TabItem(AppIcons.ContentPaste, s.clipboard),
                TabItem(AppIcons.Notifications, s.notifications, notifications.size.takeIf { it > 0 }),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        if (tab == 0) SectionCard(
            title = s.clipboard,
            icon = AppIcons.ContentPaste,
            actions = {
                IconAction(AppIcons.FileDownload, s.pullClipboard(connection.remote.name), { controller.pullRemoteClipboard() })
                IconAction(AppIcons.Delete, s.clearHistory, { controller.clearClipboardHistory() }, enabled = history.isNotEmpty())
            },
        ) {
            Hint(s.clipboardHint)
            if (history.isEmpty()) Hint(s.historyEmpty)
            history.take(20).forEach { entry ->
                ListRow(
                    title = entry.text.lineSequence().firstOrNull().orEmpty().take(120),
                    icon = if (entry.origin == ClipboardOrigin.REMOTE) AppIcons.CallReceived else AppIcons.CallMade,
                    subtitle = (if (entry.origin == ClipboardOrigin.REMOTE) s.fromDevice(connection.remote.name) else s.fromHere) +
                        " · " + formatDateTime(entry.timestamp),
                ) {
                    IconAction(AppIcons.ContentCopy, s.copyAgain, { controller.copyToClipboard(entry.text) })
                    IconAction(
                        if (entry.pinned) AppIcons.Star else AppIcons.StarBorder,
                        if (entry.pinned) s.unpin else s.pin,
                        { controller.toggleClipboardPinned(entry.timestamp) },
                    )
                }
            }
        }

        if (tab == 0) return@ScreenColumn

        if (connection.remote.type != DeviceType.ANDROID) {
            SectionCard(s.notifications, icon = AppIcons.Notifications, topic = HelpTopic.NOTIFICATIONS) { Hint(s.notificationsPhoneOnly) }
            return@ScreenColumn
        }

        SectionCard(s.notificationsTitle(notifications.size), icon = AppIcons.Notifications, topic = HelpTopic.NOTIFICATIONS) {
            SwitchRow(s.mirrorNotifications, filter.enabled, { controller.setNotificationsEnabled(it) })
            SwitchRow(s.hideOngoing, filter.hideOngoing, { controller.setNotificationsHideOngoing(it) }, subtitle = s.hideOngoingHint)
            if (notifications.isEmpty()) Hint(s.noNotifications)
            notifications.take(30).forEach { n ->
                ListRow(
                    title = listOfNotNull(n.appName ?: n.packageName, n.title).joinToString(" · "),
                    icon = AppIcons.Notifications,
                    subtitle = listOfNotNull(n.text, formatDateTime(n.postedAt)).joinToString(" · "),
                ) {
                    if (controller.canControlNotifications) {
                        IconAction(AppIcons.Send, s.reply, {
                            replyTo = if (replyTo == n.id) null else n.id
                            replyText = ""
                        })
                        IconAction(AppIcons.Close, s.dismissNotification, { controller.dismissNotification(n.id) })
                    }
                }
                if (replyTo == n.id) {
                    OutlinedTextField(
                        value = replyText,
                        onValueChange = { replyText = it },
                        label = { Text(s.replyText) },
                        tip = s.tipReply,
                        singleLine = true,
                        trailingIcon = {
                            IconAction(AppIcons.Send, s.send, {
                                controller.replyToNotification(n.id, replyText)
                                replyTo = null
                                replyText = ""
                            }, enabled = replyText.isNotBlank())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }

        if (knownPackages.isNotEmpty()) {
            SectionCard(s.notificationApps, icon = AppIcons.Tune, topic = HelpTopic.NOTIFICATIONS) {
                Hint(s.notificationAppsHint)
                knownPackages.sorted().forEach { pkg ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Mono(pkg, Modifier.weight(1f), maxLines = 1)
                        Switch(
                            checked = pkg !in filter.excludedPackages,
                            onCheckedChange = { controller.toggleNotificationPackage(pkg) },
                            tip = s.tipAppNotifications,
                        )
                    }
                }
            }
        }
    }
}

/** Баннер идущего или входящего звонка: виден на любом экране. */
@Composable
fun CallBanner(controller: EfirController, call: CallStateChanged, modifier: Modifier = Modifier) {
    val s = LocalStrings.current
    if (call.state == CallState.IDLE) return
    val who = call.name ?: call.number ?: s.unknownNumber
    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.primaryContainer, modifier = modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            androidx.compose.material3.Icon(AppIcons.Call, null)
            Text(
                text = when {
                    call.state == CallState.RINGING && call.incoming -> s.incomingCall(who)
                    call.state == CallState.RINGING -> s.outgoingCall(who)
                    else -> s.callInProgress(who)
                },
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (call.state == CallState.RINGING && call.incoming) {
                Button(onClick = { controller.callAction(CallActionType.ANSWER) }, tip = s.tipAnswer) { Text(s.answer) }
                OutlinedButton(onClick = { controller.callAction(CallActionType.REJECT) }, tip = s.tipReject) { Text(s.reject) }
            } else {
                OutlinedButton(onClick = { controller.callAction(CallActionType.END) }, tip = s.tipEndCall) { Text(s.endCall) }
            }
        }
    }
}

/** Экран «Телефон»: SMS и звонки телефона с другого устройства. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PhoneScreen(controller: EfirController, connection: ActiveConnection) {
    val s = LocalStrings.current
    if (connection.remote.type != DeviceType.ANDROID) {
        ScreenColumn(HelpTopic.PHONE) {
            EmptyState(AppIcons.Call, s.phoneOnlyTitle, s.phoneOnlyText)
        }
        return
    }

    val messages by controller.smsMessages.collectAsState()
    val threads by controller.smsThreads.collectAsState()
    val callLog by controller.callLog.collectAsState()
    val subscriptions by controller.smsSubscriptions.collectAsState()
    val deliveries by controller.smsDeliveries.collectAsState()
    val callActionResult by controller.callActionResult.collectAsState()

    var address by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    var sim by remember { mutableStateOf<Int?>(null) }
    var openThread by remember { mutableStateOf<Long?>(null) }

    var tab by remember { mutableStateOf(0) }

    ScreenColumn(HelpTopic.PHONE) {
        ScreenTabs(
            tabs = listOf(
                TabItem(AppIcons.Sms, s.tabSms, threads.size.takeIf { it > 0 }),
                TabItem(AppIcons.Call, s.callLog, callLog.size.takeIf { it > 0 }),
            ),
            selected = tab,
            onSelect = { tab = it },
        )

        if (tab == 0) SectionCard(s.newMessage, icon = AppIcons.Sms) {
            OutlinedTextField(
                value = address,
                onValueChange = { address = it },
                label = { Text(s.phoneNumber) },
                tip = s.tipPhoneNumber,
                singleLine = true,
                trailingIcon = {
                    IconAction(AppIcons.Call, s.callNumber, {
                        controller.startCall(address.trim(), subscriptionId = sim)
                    }, enabled = address.isNotBlank())
                },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(s.smsText) },
                tip = s.tipSmsText,
                minLines = 2,
                maxLines = 5,
                modifier = Modifier.fillMaxWidth(),
            )
            if (subscriptions.size > 1) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(sim == null, { sim = null }, { Text(s.simDefault) })
                    subscriptions.forEach { sub ->
                        FilterChip(sim == sub.subscriptionId, { sim = sub.subscriptionId }, {
                            Text(sub.displayName ?: "SIM ${sub.slotIndex + 1}")
                        })
                    }
                }
            }
            Button(
                enabled = address.isNotBlank() && text.isNotBlank(),
                tip = s.tipSendSms,
                onClick = {
                    controller.sendSms(address.trim(), text, subscriptionId = sim)
                    text = ""
                },
            ) { Text(s.sendSms) }
            callActionResult?.let { ErrorText(it) }
            deliveries.take(3).forEach { delivery ->
                val label = when (delivery.status) {
                    SmsDeliveryStatus.SENT -> s.smsSent
                    SmsDeliveryStatus.DELIVERED -> s.smsDelivered
                    SmsDeliveryStatus.FAILED -> s.smsFailed
                }
                val line = "$label · ${delivery.address}" + (delivery.detail?.let { " — $it" } ?: "")
                if (delivery.status == SmsDeliveryStatus.FAILED) ErrorText(line) else Hint(line)
            }
        }

        if (tab == 0) SectionCard(
            title = s.threads(threads.size),
            icon = AppIcons.Sms,
            actions = { IconAction(AppIcons.Refresh, s.refresh, { controller.loadSmsThreads() }) },
        ) {
            if (threads.isEmpty()) Hint(s.noThreads)
            threads.take(30).forEach { thread ->
                ListRow(
                    title = thread.address ?: s.threadNumber(thread.threadId),
                    icon = AppIcons.Sms,
                    iconTint = if (openThread == thread.threadId) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    subtitle = thread.snippet.take(120) + " · " + formatDateTime(thread.lastDate),
                    modifier = Modifier.clickable {
                        openThread = thread.threadId
                        thread.address?.let { address = it }
                        controller.loadSms(thread.threadId)
                    },
                ) {
                    Hint(thread.messageCount.toString())
                }
            }
        }

        if (tab == 0) SectionCard(
            title = if (openThread == null) s.recentMessages else s.messagesOfThread,
            icon = AppIcons.Description,
            actions = {
                if (openThread != null) {
                    IconAction(AppIcons.Close, s.showAllMessages, {
                        openThread = null
                        controller.loadSms()
                    })
                }
                IconAction(AppIcons.Refresh, s.refresh, { controller.loadSms(openThread) })
            },
        ) {
            if (messages.isEmpty()) Hint(s.noMessages)
            messages.take(40).forEach { sms ->
                val outgoing = sms.type == SmsType.SENT || sms.type == SmsType.OUTBOX
                ListRow(
                    title = sms.body,
                    icon = if (outgoing) AppIcons.CallMade else AppIcons.CallReceived,
                    iconTint = if (outgoing) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                    subtitle = sms.address + " · " + formatDateTime(sms.date),
                )
            }
        }

        if (tab == 1) SectionCard(
            title = s.callLog,
            icon = AppIcons.History,
            topic = HelpTopic.CALLS,
            actions = { IconAction(AppIcons.Refresh, s.refresh, { controller.loadCallLog() }) },
        ) {
            if (callLog.isEmpty()) {
                Hint(s.callLogEmpty)
                OutlinedButton(onClick = { controller.loadCallLog() }, tip = s.tipLoadCallLog) { Text(s.loadCallLog) }
            }
            callLog.take(30).forEach { entry ->
                val missed = entry.type == "MISSED" || entry.type == "REJECTED"
                ListRow(
                    title = entry.name ?: entry.number ?: s.unknownNumber,
                    icon = when (entry.type) {
                        "OUTGOING" -> AppIcons.CallMade
                        "INCOMING" -> AppIcons.CallReceived
                        else -> AppIcons.CallEnd
                    },
                    iconTint = if (missed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    subtitle = s.callType(entry.type) + " · " + s.duration(entry.durationSeconds) + " · " + formatDateTime(entry.date),
                ) {
                    entry.number?.let { number ->
                        IconAction(AppIcons.Sms, s.writeSms, { address = number })
                        IconAction(AppIcons.Call, s.callNumber, { controller.startCall(number, subscriptionId = sim) })
                    }
                }
            }
        }
    }
}
