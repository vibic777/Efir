package ru.efir.core.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import ru.efir.core.BuildInfo
import ru.efir.core.app.AppFolders
import ru.efir.core.app.AppLog
import ru.efir.core.app.AppVisibility
import ru.efir.core.app.EfirController
import ru.efir.core.app.PermissionItem
import ru.efir.core.app.exportLog
import ru.efir.core.app.localFilePickerSupported
import ru.efir.core.app.openLocalFolder
import ru.efir.core.app.platformPermissions
import ru.efir.core.app.revokeAllClosesApp
import ru.efir.core.app.revokeAllPermissions
import ru.efir.core.app.savePreference
import ru.efir.core.files.defaultFileRoots
import ru.efir.core.media.ScreenSharing
import ru.efir.core.net.EfirNetwork
import ru.efir.core.net.NetworkSettings
import ru.efir.core.net.isPortFree
import ru.efir.core.protocol.DeviceType
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.BackPress
import ru.efir.core.ui.Button
import ru.efir.core.ui.ErrorText
import ru.efir.core.ui.ExtraSwitch
import ru.efir.core.ui.FilterChip
import ru.efir.core.ui.HelpLink
import ru.efir.core.ui.HelpScope
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.Hint
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.ListRow
import ru.efir.core.ui.LocalExtraSettings
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.Mono
import ru.efir.core.ui.OutlinedButton
import ru.efir.core.ui.OutlinedTextField
import ru.efir.core.ui.SectionCard
import ru.efir.core.ui.SwitchRow
import ru.efir.core.ui.Text
import ru.efir.core.ui.TextButton
import ru.efir.core.ui.TipSettings
import ru.efir.core.ui.i18n.Lang
import ru.efir.core.ui.i18n.Strings
import ru.efir.core.ui.theme.ThemeMode
import ru.efir.core.ui.theme.positive

/** Страницы настроек: значок, название, что на странице, и её раздел справки. */
enum class SettingsSection(
    val icon: ImageVector,
    val title: (Strings) -> String,
    val hint: (Strings) -> String,
    val topic: HelpTopic,
) {
    GENERAL(AppIcons.Tune, { it.tabGeneral }, { it.setGeneralHint }, HelpTopic.SETTINGS),
    APPEARANCE(AppIcons.Visibility, { it.setAppearance }, { it.setAppearanceHint }, HelpTopic.SETTINGS),
    FOLDERS(AppIcons.Folder, { it.setFolders }, { it.setFoldersHint }, HelpTopic.FOLDERS),
    NETWORK(AppIcons.Link, { it.setNetwork }, { it.setNetworkHint }, HelpTopic.NETWORK),
    SCREEN(AppIcons.ScreenRecord, { it.setScreen }, { it.setScreenHint }, HelpTopic.SCREEN_SHARE),
    PERMISSIONS(AppIcons.Lock, { it.permissions }, { it.setPermissionsHint }, HelpTopic.PERMISSIONS),
    LOG(AppIcons.Description, { it.tabLog }, { it.setLogHint }, HelpTopic.LOG),
    ABOUT(AppIcons.Info, { it.about }, { it.setAboutHint }, HelpTopic.ABOUT),
}

/**
 * Какая страница настроек открыта; `null` — меню. Другие экраны открывают нужную
 * страницу через [LocalOpenSettings] (папка загрузок — из «Файлов», разрешения — из «Устройств»).
 */
object SettingsLink {
    var section: SettingsSection? by mutableStateOf(null)
}

/** Открывает настройки на странице (`null` — на меню). Задаёт корневой экран. */
val LocalOpenSettings = staticCompositionLocalOf<(SettingsSection?) -> Unit> { {} }

/** Одна настройка для поиска по меню: на какой она странице, как называется и какими словами её ищут. */
private class SettingEntry(val section: SettingsSection, val title: String, val words: String = "")

private fun settingEntries(
    s: Strings,
    extra: List<ExtraSwitch>,
    permissions: List<PermissionItem>,
    computer: Boolean,
): List<SettingEntry> = buildList {
    add(SettingEntry(SettingsSection.GENERAL, s.language, Lang.entries.joinToString { it.title } + ". " + s.tipLanguage))
    extra.forEach { add(SettingEntry(SettingsSection.GENERAL, it.title(s), it.hint(s))) }
    add(SettingEntry(SettingsSection.APPEARANCE, s.theme, listOf(s.themeSystem, s.themeLight, s.themeDark).joinToString()))
    add(SettingEntry(SettingsSection.APPEARANCE, s.tipsTitle, s.tipsHint))
    add(SettingEntry(SettingsSection.FOLDERS, s.downloadsFolder, s.downloadsFolderHint))
    add(SettingEntry(SettingsSection.FOLDERS, s.syncFolder, s.syncFolderHint))
    add(SettingEntry(SettingsSection.FOLDERS, s.sharedFolders, s.sharedFoldersHint))
    add(SettingEntry(SettingsSection.NETWORK, s.listenPortTitle, s.listenPortHint))
    if (computer) {
        add(SettingEntry(SettingsSection.SCREEN, s.showScreen, s.showScreenHint))
        add(SettingEntry(SettingsSection.SCREEN, s.allowControl, s.allowControlHint))
    } else {
        add(SettingEntry(SettingsSection.SCREEN, s.setScreen, s.screenPhoneHint))
    }
    permissions.forEach { add(SettingEntry(SettingsSection.PERMISSIONS, s.permissionTitle(it.id), s.permissionHint(it.id))) }
    add(SettingEntry(SettingsSection.LOG, s.tabLog, s.logHint))
    add(SettingEntry(SettingsSection.ABOUT, s.versionTitle, s.versionLine(BuildInfo.VERSION) + ". " + s.sameVersionHint))
    add(SettingEntry(SettingsSection.ABOUT, s.help, s.tipOpenHelp))
    add(SettingEntry(SettingsSection.ABOUT, s.thisDevice, s.licenses))
}

/** Сколько последних строк журнала показывать на экране (копируется и отдаётся он целиком). */
private const val LOG_LINES = 300

/** Какую папку сейчас выбирают в окне выбора. */
private enum class FolderChoice { DOWNLOADS, SYNC, SHARED }

/**
 * Экран «Настройки», как в Notio: меню страниц с поиском, на каждой странице — её
 * настройки. Поиск находит настройку по слову из названия или описания и открывает
 * её страницу. Внизу меню — версия приложения.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    controller: EfirController,
    lang: Lang,
    onLang: (Lang) -> Unit,
    theme: ThemeMode,
    onTheme: (ThemeMode) -> Unit,
) {
    val s = LocalStrings.current
    val visible by AppVisibility.visible.collectAsState()
    val extra = LocalExtraSettings.current
    val computer = controller.selfType != DeviceType.ANDROID
    val hasPermissions = remember { platformPermissions().isNotEmpty() }
    val sections = remember(hasPermissions) { SettingsSection.entries.filter { it != SettingsSection.PERMISSIONS || hasPermissions } }
    val current = SettingsLink.section?.takeIf { it in sections }
    var query by remember { mutableStateOf("") }

    // Разрешения выдаются в системных окнах: перечитываем состояние, пока их страница перед глазами.
    var tick by remember { mutableIntStateOf(0) }
    val onPermissions = current == SettingsSection.PERMISSIONS
    LaunchedEffect(visible, onPermissions) {
        while (visible && onPermissions) {
            tick++
            delay(1_500)
        }
    }
    val permissions = remember(tick) { platformPermissions() }
    // Отзыв, который закроет приложение, сначала подтверждается.
    var confirmRevoke by remember { mutableStateOf<PermissionItem?>(null) }
    var confirmRevokeAll by remember { mutableStateOf(false) }
    // Памятка перед выдачей настройки, которую Android может блокировать как «ограниченную».
    var guide by remember { mutableStateOf<PermissionItem?>(null) }
    var logText by remember { mutableStateOf("") }
    var logNote by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(current) {
        if (current == SettingsSection.LOG) logText = AppLog.tail(LOG_LINES)
    }
    var choosing by remember { mutableStateOf<FolderChoice?>(null) }

    // «Назад» на телефоне: со страницы — в меню.
    BackPress(enabled = current != null) { SettingsLink.section = null }

    ScreenColumn(current?.topic ?: HelpTopic.SETTINGS) {
        if (current == null) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it.take(60) },
                label = { Text(s.settingsSearch) },
                tip = s.tipSettingsSearch,
                leadingIcon = { Icon(AppIcons.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconAction(AppIcons.Close, s.clearSearch, { query = "" })
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            val words = query.trim().split(' ').filter { it.length >= 2 }
            if (words.isNotEmpty()) {
                // Найденные настройки: страница › настройка и её описание.
                val found = settingEntries(s, extra, permissions, computer).filter { entry ->
                    val text = entry.section.title(s) + " " + entry.title + " " + entry.words
                    words.all { text.contains(it, ignoreCase = true) }
                }
                if (found.isEmpty()) Hint(s.settingsNothingFound)
                Card(Modifier.fillMaxWidth()) {
                    found.forEachIndexed { index, entry ->
                        if (index > 0) HorizontalDivider()
                        MenuRow(
                            icon = entry.section.icon,
                            title = entry.section.title(s) + " › " + entry.title,
                            hint = entry.words,
                            topic = entry.section.topic,
                            onClick = {
                                query = ""
                                SettingsLink.section = entry.section
                            },
                        )
                    }
                }
            } else {
                Card(Modifier.fillMaxWidth()) {
                    sections.forEachIndexed { index, section ->
                        if (index > 0) HorizontalDivider()
                        val missing = if (section == SettingsSection.PERMISSIONS) permissions.count { it.granted == false } else 0
                        MenuRow(
                            icon = section.icon,
                            title = section.title(s) + if (missing > 0) " ($missing)" else "",
                            hint = if (missing > 0) s.permissionsMissing(missing) else section.hint(s),
                            topic = section.topic,
                            warning = missing > 0,
                            onClick = { SettingsLink.section = section },
                        )
                    }
                }
                // Версия видна сразу, не заходя на страницу «О приложении».
                Hint(s.versionLine(BuildInfo.VERSION), Modifier.padding(start = 16.dp, top = 4.dp))
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconAction(AppIcons.ArrowBack, s.back, { SettingsLink.section = null })
                Spacer(Modifier.width(4.dp))
                Icon(current.icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                androidx.compose.material3.Text(
                    current.title(s),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.semantics { heading() },
                )
            }
        }

        if (current == SettingsSection.GENERAL) {
            SectionCard(s.language, icon = AppIcons.Description) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Lang.entries.forEach { item ->
                        FilterChip(lang == item, { onLang(item) }, { Text(item.title) }, tip = s.tipLanguage)
                    }
                }
            }

            if (extra.isNotEmpty()) {
                SectionCard(s.appBehavior, icon = AppIcons.Sync) {
                    extra.forEach { item ->
                        SwitchRow(
                            title = item.title(s),
                            subtitle = item.hint(s),
                            checked = item.checked,
                            onChange = item.onChange,
                        )
                    }
                }
            }
        }

        if (current == SettingsSection.APPEARANCE) {
            SectionCard(s.theme, icon = AppIcons.Visibility) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(theme == ThemeMode.SYSTEM, { onTheme(ThemeMode.SYSTEM) }, { Text(s.themeSystem) }, tip = s.tipThemeSystem)
                    FilterChip(theme == ThemeMode.LIGHT, { onTheme(ThemeMode.LIGHT) }, { Text(s.themeLight) })
                    FilterChip(theme == ThemeMode.DARK, { onTheme(ThemeMode.DARK) }, { Text(s.themeDark) })
                }
            }

            SectionCard(s.tipsTitle, icon = AppIcons.Help, topic = HelpTopic.HINTS) {
                Hint(s.tipsHint)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TipSettings.CHOICES.forEach { seconds ->
                        FilterChip(
                            selected = TipSettings.seconds == seconds,
                            onClick = {
                                TipSettings.seconds = seconds
                                savePreference(TipSettings.PREF, seconds.toString())
                            },
                            label = { Text(if (seconds == 0) s.tipsOff else s.tipSeconds(seconds)) },
                            tip = if (seconds == 0) s.tipTipsOff else s.tipTipsSeconds(seconds),
                        )
                    }
                }
            }
        }

        if (current == SettingsSection.FOLDERS) {
            val rules by controller.syncRules.collectAsState()

            SectionCard(s.downloadsFolder, icon = AppIcons.FileDownload, topic = HelpTopic.DOWNLOADS) {
                Hint(s.downloadsFolderHint)
                FolderLine(AppFolders.downloadDir, standard = !AppFolders.downloadDirChosen)
                FolderButtons(
                    path = AppFolders.downloadDir,
                    chosen = AppFolders.downloadDirChosen,
                    onChoose = { choosing = FolderChoice.DOWNLOADS },
                    onReset = { AppFolders.chooseDownloadDir(null) },
                )
            }

            SectionCard(s.syncFolder, icon = AppIcons.Sync, topic = HelpTopic.SYNC) {
                Hint(s.syncFolderHint)
                FolderLine(AppFolders.syncDir, standard = !AppFolders.syncDirChosen)
                FolderButtons(
                    path = AppFolders.syncDir,
                    chosen = AppFolders.syncDirChosen,
                    onChoose = { choosing = FolderChoice.SYNC },
                    onReset = { AppFolders.chooseSyncDir(null) },
                )
            }

            SectionCard(s.sharedFolders, icon = AppIcons.Lock) {
                Hint(s.sharedFoldersHint)
                val standard = remember { defaultFileRoots().map { it.replace('\\', '/') } }
                standard.forEach { ListRow(title = it, icon = AppIcons.Folder, subtitle = s.folderAlways) }
                val downloads = AppFolders.downloadDir
                if (standard.none { isInside(it, downloads) }) {
                    ListRow(title = downloads, icon = AppIcons.FileDownload, subtitle = s.folderIsDownloads)
                }
                rules.mapNotNull { AppFolders.clean(it.localPath) }.distinct()
                    .filter { folder -> standard.none { isInside(it, folder) } }
                    .forEach { ListRow(title = it, icon = AppIcons.Sync, subtitle = s.folderIsRule) }
                AppFolders.sharedFolders.forEach { folder ->
                    ListRow(title = folder, icon = AppIcons.Folder) {
                        IconAction(AppIcons.Delete, s.removeSharedFolder, { AppFolders.removeSharedFolder(folder) })
                    }
                }
                OutlinedButton(onClick = { choosing = FolderChoice.SHARED }, tip = s.tipAddShared) { Text(s.addSharedFolder) }
            }
        }

        if (current == SettingsSection.NETWORK) {
            SectionCard(s.listenPortTitle, icon = AppIcons.Link) {
                Hint(s.listenPortHint)
                var manual by remember { mutableStateOf(NetworkSettings.manualPort()) }
                var byHand by remember { mutableStateOf(manual != null) }
                var typed by remember { mutableStateOf((manual ?: controller.selfPort).toString()) }
                val port = typed.toIntOrNull()
                val valid = NetworkSettings.valid(port)

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = !byHand,
                        onClick = {
                            byHand = false
                            if (manual != null) {
                                NetworkSettings.saveManualPort(null)
                                manual = null
                                AppLog.i("settings", "the listening port is chosen automatically (from the next start)")
                            }
                        },
                        label = { Text(s.portAuto) },
                        tip = s.tipPortAuto,
                    )
                    FilterChip(byHand, { byHand = true }, { Text(s.portManual) }, tip = s.tipPortManual)
                }

                if (!byHand) {
                    Hint(s.portAutoHint(EfirNetwork.DEFAULT_PORT))
                } else {
                    OutlinedTextField(
                        value = typed,
                        onValueChange = { typed = it.filter { ch -> ch.isDigit() }.take(5) },
                        label = { Text(s.listenPortTitle) },
                        tip = s.tipListenPort,
                        isError = !valid,
                        singleLine = true,
                    )
                    // Занят ли порт: проверяется чуть погодя после ввода, не в потоке интерфейса.
                    var free by remember { mutableStateOf<Boolean?>(null) }
                    LaunchedEffect(port) {
                        free = null
                        if (valid && port != controller.selfPort) {
                            delay(300)
                            free = withContext(Dispatchers.Default) { isPortFree(port!!) }
                        }
                    }
                    when {
                        !valid -> ErrorText(s.listenPortError)
                        port == controller.selfPort -> Hint(s.portOurs)
                        free == true -> Hint(s.portFree(port!!))
                        free == false -> ErrorText(s.portBusy(port!!))
                    }
                    Button(
                        enabled = valid && port != manual,
                        tip = s.tipSavePort,
                        onClick = {
                            NetworkSettings.saveManualPort(port)
                            manual = port
                            AppLog.i("settings", "the listening port is set to $port (from the next start)")
                        },
                    ) { Text(s.save) }
                }

                // Работающий порт меняется только с перезапуском: говорим, какой сейчас и что будет.
                Hint(s.listenPortNow(controller.selfPort))
                val next = manual
                if (next != null && next != controller.selfPort) ErrorText(s.listenPortRestart(next))
                if (next == null && NetworkSettings.manualPort() == null && controller.selfPort != EfirNetwork.DEFAULT_PORT && byHand.not()) {
                    Hint(s.portAutoMoved(EfirNetwork.DEFAULT_PORT))
                }
            }
        }
        if (current == SettingsSection.SCREEN) {
            SectionCard(s.setScreen, icon = AppIcons.ScreenRecord) {
                if (computer) {
                    SwitchRow(s.showScreen, ScreenSharing.showAllowed, { ScreenSharing.allowShow(it) }, subtitle = s.showScreenHint)
                    SwitchRow(s.allowControl, ScreenSharing.controlAllowed, { ScreenSharing.allowControl(it) }, subtitle = s.allowControlHint)
                } else {
                    Hint(s.ownScreenPhoneHint)
                    Hint(s.screenPhoneHint)
                    OutlinedButton(onClick = { SettingsLink.section = SettingsSection.PERMISSIONS }, topic = HelpTopic.PERMISSIONS) {
                        Text(s.toPermissions)
                    }
                }
            }
        }

        if (current == SettingsSection.PERMISSIONS) {
            SectionCard(s.permissions, icon = AppIcons.Lock) {
                Hint(s.permissionsHint)
                permissions.forEach { item ->
                    val granted = item.granted == true
                    ListRow(
                        title = s.permissionTitle(item.id),
                        icon = if (granted) AppIcons.Check else AppIcons.Warning,
                        iconTint = if (granted) MaterialTheme.colorScheme.positive else MaterialTheme.colorScheme.error,
                        subtitle = s.permissionHint(item.id),
                    ) {
                        val revoke = item.revoke
                        if (!granted) {
                            // Настройку, которую Android может блокировать, сначала объясняем.
                            OutlinedButton(
                                onClick = { if (item.appInfo != null) guide = item else item.request() },
                                tip = s.tipGrant,
                            ) { Text(s.grant) }
                        } else if (revoke != null) {
                            OutlinedButton(
                                onClick = { if (item.revokeClosesApp) confirmRevoke = item else revoke() },
                                tip = if (item.revokeClosesApp) s.tipRevokeCloses else s.tipRevoke,
                            ) { Text(s.revokePermission) }
                        }
                    }
                }
                if (controller.defaultSmsRoleSupported) {
                    val isDefault = remember(tick) { controller.isDefaultSmsApp() }
                    ListRow(
                        title = s.defaultSms,
                        icon = if (isDefault) AppIcons.Check else AppIcons.Info,
                        iconTint = if (isDefault) MaterialTheme.colorScheme.positive else MaterialTheme.colorScheme.onSurfaceVariant,
                        subtitle = if (isDefault) s.defaultSmsOk else s.defaultSmsNo,
                    ) {
                        if (!isDefault) OutlinedButton(onClick = { controller.requestDefaultSmsRole() }, tip = s.tipGrant) { Text(s.grant) }
                    }
                }
                if (permissions.any { it.granted == true && it.revoke != null }) {
                    OutlinedButton(onClick = { confirmRevokeAll = true }, tip = s.tipRevokeAll) { Text(s.revokeAll) }
                }
            }
        }

        if (current == SettingsSection.LOG) {
            SectionCard(
                title = s.tabLog,
                icon = AppIcons.Description,
                actions = {
                    IconAction(AppIcons.Refresh, s.logRefresh, { logText = AppLog.tail(LOG_LINES) })
                    IconAction(AppIcons.ContentCopy, s.logCopy, {
                        controller.copyToLocalClipboard(AppLog.read())
                        logNote = s.logCopied
                    })
                    IconAction(AppIcons.FileUpload, s.logExport, {
                        logNote = exportLog(AppLog.read())?.let { s.logSaved(it) }
                    })
                    IconAction(AppIcons.Delete, s.logClear, {
                        AppLog.clear()
                        logText = AppLog.tail(LOG_LINES)
                        logNote = null
                    })
                },
            ) {
                Hint(s.logHint)
                AppLog.path?.let { Mono(it) }
                logNote?.let { Hint(it) }
                if (logText.isBlank()) {
                    Hint(s.logEmpty)
                } else {
                    SelectionContainer {
                        androidx.compose.material3.Text(
                            logText,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        if (current == SettingsSection.ABOUT) {
            SectionCard(s.versionTitle, icon = AppIcons.Info) {
                SelectionContainer {
                    androidx.compose.material3.Text(
                        s.versionLine(BuildInfo.VERSION),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
                Hint(s.aboutText)
                Hint(s.sameVersionHint)
                OutlinedButton(onClick = { HelpLink.open(HelpTopic.START) }, tip = s.tipOpenHelp, topic = HelpTopic.HINTS) {
                    Text(s.openHelp)
                }
            }
            SectionCard(s.thisDevice, icon = deviceIcon(controller.selfType)) {
                ListRow(
                    title = controller.selfName,
                    icon = deviceIcon(controller.selfType),
                    subtitle = "${s.platformName(controller.selfType)} · ${controller.selfAddress}:${controller.selfPort}",
                )
                Hint(s.downloadsFolder)
                Mono(AppFolders.downloadDir)
                Hint(s.licenses)
            }
        }
    }

    choosing?.let { choice ->
        FolderPickerDialog(
            title = when (choice) {
                FolderChoice.DOWNLOADS -> s.downloadsFolder
                FolderChoice.SYNC -> s.syncFolder
                FolderChoice.SHARED -> s.addSharedFolder
            },
            start = when (choice) {
                FolderChoice.DOWNLOADS -> AppFolders.downloadDir
                FolderChoice.SYNC -> AppFolders.syncDir
                FolderChoice.SHARED -> null
            },
            source = remember { localFolderSource() },
            onPick = { path ->
                when (choice) {
                    FolderChoice.DOWNLOADS -> AppFolders.chooseDownloadDir(path)
                    FolderChoice.SYNC -> AppFolders.chooseSyncDir(path)
                    FolderChoice.SHARED -> AppFolders.addSharedFolder(path)
                }
                AppLog.i("settings", "a folder was chosen: ${choice.name.lowercase()}")
                choosing = null
            },
            onDismiss = { choosing = null },
        )
    }

    confirmRevoke?.let { item ->
        ConfirmDialog(
            title = s.revokeTitle(s.permissionTitle(item.id)),
            text = s.revokeClosesAppText,
            confirm = s.revokePermission,
            onConfirm = {
                confirmRevoke = null
                item.revoke?.invoke()
            },
            onDismiss = { confirmRevoke = null },
        )
    }
    guide?.let { item ->
        AlertDialog(
            onDismissRequest = { guide = null },
            title = { Text(s.permissionTitle(item.id)) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.restrictedGuide, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { item.appInfo?.invoke() },
                        modifier = Modifier.fillMaxWidth(),
                        tip = s.tipAppInfo,
                    ) { Text(s.openAppInfo) }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        guide = null
                        item.request()
                    },
                    tip = s.tipGrant,
                ) { Text(s.openSetting) }
            },
            dismissButton = { TextButton(onClick = { guide = null }) { Text(s.close) } },
        )
    }
    if (confirmRevokeAll) {
        ConfirmDialog(
            title = s.revokeAllTitle,
            text = s.revokeAllText + if (revokeAllClosesApp) s.revokeAllClosesText else "",
            confirm = s.revokeAll,
            onConfirm = {
                confirmRevokeAll = false
                revokeAllPermissions()
            },
            onDismiss = { confirmRevokeAll = false },
        )
    }
}

/** Лежит ли [path] внутри папки [root] (или это она сама); без учёта регистра букв диска. */
private fun isInside(root: String, path: String): Boolean {
    val r = root.trimEnd('/').lowercase()
    val p = path.trimEnd('/').lowercase()
    return p == r || p.startsWith("$r/")
}

/** Строка меню настроек: значок, название, что внутри; нажатие открывает страницу. */
@Composable
private fun MenuRow(
    icon: ImageVector,
    title: String,
    hint: String,
    topic: HelpTopic,
    onClick: () -> Unit,
    warning: Boolean = false,
) = HelpScope(topic) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            androidx.compose.material3.Text(title, style = MaterialTheme.typography.titleMedium)
            if (hint.isNotBlank()) {
                androidx.compose.material3.Text(
                    hint,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
        // Стрелка «вперёд» — та же «назад», отражённая.
        Icon(AppIcons.ArrowBack, null, Modifier.size(18.dp).graphicsLayer(scaleX = -1f), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Путь папки и пометка, что она стандартная. */
@Composable
private fun FolderLine(path: String, standard: Boolean) {
    val s = LocalStrings.current
    SelectionContainer { Mono(path) }
    if (standard) Hint(s.folderStandard)
}

/** Кнопки папки: выбрать, открыть в проводнике (на компьютере), вернуть стандартную. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FolderButtons(path: String, chosen: Boolean, onChoose: () -> Unit, onReset: () -> Unit) {
    val s = LocalStrings.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = onChoose, tip = s.tipChooseFolder) { Text(s.chooseFolder) }
        if (localFilePickerSupported) {
            OutlinedButton(onClick = { openLocalFolder(path) }) { Text(s.openFolder) }
        }
        TextButton(onClick = onReset, enabled = chosen, tip = s.tipResetFolder) { Text(s.resetFolder) }
    }
}
