package ru.efir.core.app

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import ru.efir.core.ui.Button
import ru.efir.core.ui.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Surface
import ru.efir.core.ui.Text
import ru.efir.core.ui.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import ru.efir.core.ui.AppIcons
import ru.efir.core.ui.EmptyState
import ru.efir.core.ui.BackPress
import ru.efir.core.ui.HelpLink
import ru.efir.core.ui.HelpOverlay
import ru.efir.core.ui.HelpTopic
import ru.efir.core.ui.HoverTip
import ru.efir.core.ui.IconAction
import ru.efir.core.ui.LocalOpenHelp
import ru.efir.core.ui.LocalStrings
import ru.efir.core.ui.LocalTitleBar
import ru.efir.core.ui.TipSettings
import ru.efir.core.ui.StatusDot
import ru.efir.core.ui.i18n.Lang
import ru.efir.core.ui.i18n.Strings
import ru.efir.core.ui.screens.CallBanner
import ru.efir.core.ui.screens.ClipboardScreen
import ru.efir.core.ui.screens.DevicesScreen
import ru.efir.core.ui.screens.FilesScreen
import ru.efir.core.ui.screens.MediaScreen
import ru.efir.core.ui.screens.PhoneScreen
import ru.efir.core.ui.screens.RemoteScreenScreen
import ru.efir.core.ui.screens.LocalOpenSettings
import ru.efir.core.ui.screens.SettingsLink
import ru.efir.core.ui.screens.SettingsScreen
import ru.efir.core.ui.screens.SettingsSection
import ru.efir.core.ui.screens.SyncScreen
import ru.efir.core.ui.theme.EfirLogo
import ru.efir.core.ui.theme.EfirTheme
import ru.efir.core.ui.theme.ThemeMode
import ru.efir.core.ui.theme.isDark
import ru.efir.core.ui.theme.positive

/** Экраны приложения. Первые семь — разделы навигации, настройки открываются из верхней панели; справка — поверх окна. */
private enum class Dest(val icon: ImageVector, val title: (Strings) -> String, val needsLink: Boolean) {
    DEVICES(AppIcons.Smartphone, { it.navDevices }, false),
    SCREEN(AppIcons.ScreenRecord, { it.navScreen }, true),
    FILES(AppIcons.Folder, { it.navFiles }, true),
    SYNC(AppIcons.Sync, { it.navSync }, true),
    CLIPBOARD(AppIcons.ContentPaste, { it.navClipboard }, true),
    MEDIA(AppIcons.Videocam, { it.navMedia }, true),
    PHONE(AppIcons.Call, { it.navPhone }, true),
    SETTINGS(AppIcons.Tune, { it.settings }, false),
}

private val NAV = listOf(Dest.DEVICES, Dest.SCREEN, Dest.FILES, Dest.SYNC, Dest.CLIPBOARD, Dest.MEDIA, Dest.PHONE)

private const val PREF_LANG = "language"
private const val PREF_THEME = "theme"

/** Ширина окна, с которой навигация становится боковой панелью. */
private val WIDE_FROM = 720.dp

/**
 * Корневой экран Efir: верхняя панель с состоянием соединения, навигация по
 * разделам (боковая панель на широком окне, полоса значков на узком) и
 * содержимое раздела. Язык и тема запоминаются между запусками.
 */
@Composable
fun EfirAppScreen(controller: EfirController) {
    var lang by remember { mutableStateOf(Lang.byCode(loadPreference(PREF_LANG) ?: systemLanguageCode())) }
    var theme by remember {
        mutableStateOf(ThemeMode.entries.firstOrNull { it.name == loadPreference(PREF_THEME) } ?: ThemeMode.SYSTEM)
    }
    remember {
        loadPreference(TipSettings.PREF)?.toIntOrNull()?.let { TipSettings.seconds = it.coerceIn(0, 60) }
    }
    var dest by remember { mutableStateOf(Dest.DEVICES) }
    // Куда вернуться из настроек: туда, откуда в них пришли.
    var beforeSettings by remember { mutableStateOf(Dest.DEVICES) }

    fun openSettings(section: SettingsSection?) {
        if (dest != Dest.SETTINGS) beforeSettings = dest
        SettingsLink.section = section
        dest = Dest.SETTINGS
    }

    // Просьба из меню трея: открыть раздел.
    val navRequest by AppNavigation.request.collectAsState()
    LaunchedEffect(navRequest) {
        navRequest?.let { name ->
            Dest.entries.firstOrNull { it.name == name }?.let { dest = it }
            AppNavigation.request.value = null
        }
    }

    val connection by controller.connection.collectAsState()
    val inputError by controller.inputError.collectAsState()
    val screenHost by controller.screenHostState.collectAsState()
    val receivedText by controller.receivedText.collectAsState()
    val callState by controller.callState.collectAsState()
    val s = lang.strings

    CompositionLocalProvider(
        LocalStrings provides s,
        // Справка открывается поверх окна и диалогов: экран под ней остаётся как был.
        LocalOpenHelp provides { topic -> HelpLink.open(topic) },
        LocalOpenSettings provides { section -> openSettings(section) },
    ) {
        EfirTheme(theme) {
            val dark = theme.isDark()
            LocalTitleBar.current?.let { bar -> SideEffect { bar.onTheme(dark) } }
            Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                BoxWithConstraints(Modifier.windowInsetsPadding(WindowInsets.safeDrawing)) {
                    val wide = maxWidth >= WIDE_FROM
                    Column(Modifier.fillMaxSize()) {
                        TopBar(
                            connection = connection,
                            dest = dest,
                            sharing = screenHost.active,
                            onStopSharing = { controller.stopOwnScreen() },
                            // Справка открывается на разделе экрана, который сейчас перед глазами.
                            onHelp = { HelpLink.open(if (dest == Dest.SETTINGS) SettingsLink.section?.topic ?: HelpTopic.SETTINGS else helpTopicOf(dest)) },
                            // Повторное нажатие на «Настройки» возвращает туда, откуда пришли.
                            onSettings = { if (dest == Dest.SETTINGS) dest = beforeSettings else openSettings(null) },
                        )
                        if (!wide) NavStrip(dest, onDest = { dest = it })
                        HorizontalDivider()
                        // Звонок на телефоне виден с любого экрана ПК.
                        if (connection != null && controller.selfType != ru.efir.core.protocol.DeviceType.ANDROID) {
                            CallBanner(controller, callState)
                        }
                        Row(Modifier.weight(1f)) {
                            if (wide) NavSide(dest, onDest = { dest = it })
                            Box(Modifier.weight(1f).fillMaxHeight()) {
                                val link = connection
                                when {
                                    dest == Dest.SETTINGS -> SettingsScreen(
                                        controller = controller,
                                        lang = lang,
                                        onLang = {
                                            lang = it
                                            savePreference(PREF_LANG, it.code)
                                        },
                                        theme = theme,
                                        onTheme = {
                                            theme = it
                                            savePreference(PREF_THEME, it.name)
                                        },
                                    )

                                    dest == Dest.DEVICES -> DevicesScreen(controller, onOpenSettings = { openSettings(SettingsSection.PERMISSIONS) })
                                    link == null -> EmptyState(
                                        icon = dest.icon,
                                        title = s.needConnectionTitle,
                                        text = s.needConnectionText(dest.title(s)),
                                    ) {
                                        Button(onClick = { dest = Dest.DEVICES }) { Text(s.toDevices) }
                                    }

                                    dest == Dest.SCREEN -> RemoteScreenScreen(controller, link)
                                    dest == Dest.FILES -> FilesScreen(controller, link)
                                    dest == Dest.SYNC -> SyncScreen(controller, link)
                                    dest == Dest.CLIPBOARD -> ClipboardScreen(controller, link)
                                    dest == Dest.MEDIA -> MediaScreen(controller, link)
                                    else -> PhoneScreen(controller, link)
                                }
                            }
                        }
                    }
                }
            }

            // «Назад» на телефоне: из настроек — туда, откуда пришли (со страницы — сначала в меню, это делает сам экран),
            // из раздела — к устройствам.
            BackPress(enabled = dest != Dest.DEVICES && !(dest == Dest.SETTINGS && SettingsLink.section != null)) {
                dest = if (dest == Dest.SETTINGS) beforeSettings else Dest.DEVICES
            }

            HelpOverlay()

            // Текст, присланный другим устройством: его можно прочитать, выделить и скопировать.
            receivedText?.let { received ->
                AlertDialog(
                    onDismissRequest = { controller.clearReceivedText() },
                    title = { Text(s.receivedTextTitle(received.from)) },
                    text = {
                        Column(Modifier.verticalScroll(rememberScrollState())) {
                            SelectionContainer {
                                androidx.compose.material3.Text(received.text, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    },
                    confirmButton = { TextButton(onClick = { controller.clearReceivedText() }) { Text(s.ok) } },
                    dismissButton = {
                        TextButton(
                            tip = s.tipCopyReceived,
                            onClick = {
                                controller.copyToLocalClipboard(received.text)
                                controller.clearReceivedText()
                            },
                        ) { Text(s.copyText) }
                    },
                )
            }

            inputError?.let {
                AccessibilityPromptDialog(
                    // Управление не включено на той стороне: что сделать, зависит от того, телефон там или компьютер.
                    message = if (connection?.remote?.type == ru.efir.core.protocol.DeviceType.ANDROID) s.inputUnavailableHint else s.inputUnavailableHintPc,
                    canOpenSettings = false,
                    onOpenSettings = {
                        controller.openInputServiceSettings()
                        controller.clearInputError()
                    },
                    onDismiss = { controller.clearInputError() },
                )
            }
        }
    }
}

/** Верхняя панель: знак и имя приложения, с кем соединены, значок идущего показа экрана, справка и настройки. */
@Composable
private fun TopBar(
    connection: ActiveConnection?,
    dest: Dest,
    sharing: Boolean,
    onStopSharing: () -> Unit,
    onHelp: () -> Unit,
    onSettings: () -> Unit,
) {
    val s = LocalStrings.current
    // На ПК эта панель и есть заголовок окна: за неё окно перетаскивают, справа — место системных кнопок.
    val titleBar = LocalTitleBar.current
    Row(
        Modifier.fillMaxWidth().height(56.dp).then(titleBar?.modifier ?: Modifier).padding(start = 16.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EfirLogo(Modifier.size(32.dp))
        Spacer(Modifier.width(12.dp))
        Text(s.appName, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.width(16.dp))
        Row(
            Modifier
                .weight(1f)
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            StatusDot(if (connection != null) MaterialTheme.colorScheme.positive else MaterialTheme.colorScheme.outline)
            Text(
                connection?.remote?.name ?: s.noConnection,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        // Экран этого устройства сейчас видят: значок горит всё время показа, нажатие останавливает показ.
        if (sharing) {
            IconAction(AppIcons.ScreenRecord, s.sharingNow, onStopSharing, tint = MaterialTheme.colorScheme.error, topic = HelpTopic.SCREEN_SHARE)
        }
        IconAction(AppIcons.Help, s.help, onHelp, tonal = HelpLink.open, topic = HelpTopic.HINTS)
        IconAction(AppIcons.Tune, s.settings, onSettings, tonal = dest == Dest.SETTINGS, topic = HelpTopic.SETTINGS)
        if (titleBar != null) Spacer(Modifier.width(titleBar.endInset + 8.dp))
    }
}

/** Боковая навигация широкого окна. */
@Composable
private fun NavSide(dest: Dest, onDest: (Dest) -> Unit) {
    val s = LocalStrings.current
    NavigationRail(
        modifier = Modifier.fillMaxHeight().width(96.dp),
        containerColor = MaterialTheme.colorScheme.background,
    ) {
        Spacer(Modifier.height(8.dp))
        NAV.forEach { item ->
            HoverTip(s.navTip(item.name), topic = helpTopicOf(item)) {
                NavigationRailItem(
                    selected = dest == item,
                    onClick = { onDest(item) },
                    icon = { Icon(item.icon, contentDescription = null) },
                    label = { Text(item.title(s), maxLines = 1, style = MaterialTheme.typography.labelSmall) },
                )
            }
        }
    }
}

/** Полоса разделов узкого окна (телефон): значки-вкладки, прокручиваются вбок. */
@Composable
private fun NavStrip(dest: Dest, onDest: (Dest) -> Unit) {
    val s = LocalStrings.current
    Row(
        Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        NAV.forEach { item ->
            val name = item.title(s)
            HoverTip(name, topic = helpTopicOf(item)) {
                FilterChip(
                    selected = dest == item,
                    onClick = { onDest(item) },
                    label = { Icon(item.icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
                    modifier = Modifier.semantics {
                        contentDescription = name
                        role = Role.Tab
                    },
                )
            }
        }
    }
}

private fun helpTopicOf(dest: Dest): HelpTopic = when (dest) {
    Dest.DEVICES -> HelpTopic.DEVICES
    Dest.SCREEN -> HelpTopic.SCREEN
    Dest.FILES -> HelpTopic.FILES
    Dest.SYNC -> HelpTopic.SYNC
    Dest.CLIPBOARD -> HelpTopic.CLIPBOARD
    Dest.MEDIA -> HelpTopic.MEDIA
    Dest.PHONE -> HelpTopic.PHONE
    Dest.SETTINGS -> HelpTopic.SETTINGS
}

/**
 * Подсказка «включите службу доступности»: показывается, когда удалённая
 * сторона не смогла выполнить нажатие (`input_not_ready`).
 */
@Composable
private fun AccessibilityPromptDialog(
    message: String,
    canOpenSettings: Boolean,
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val s = LocalStrings.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(s.inputUnavailable) },
        text = { Text(message) },
        confirmButton = {
            if (canOpenSettings) {
                TextButton(onClick = onOpenSettings) { Text(s.openSettings) }
            } else {
                TextButton(onClick = onDismiss) { Text(s.gotIt) }
            }
        },
        dismissButton = {
            if (canOpenSettings) TextButton(onClick = onDismiss) { Text(s.later) }
        },
    )
}
