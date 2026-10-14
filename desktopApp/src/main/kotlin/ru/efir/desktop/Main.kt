package ru.efir.desktop

import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.isTraySupported
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import ru.efir.core.BuildInfo
import ru.efir.core.app.AppLog
import ru.efir.core.app.AppNavigation
import ru.efir.core.app.AppVisibility
import ru.efir.core.app.EfirAppScreen
import ru.efir.core.app.EfirController
import ru.efir.core.app.FileLogSink
import ru.efir.core.app.loadPreference
import ru.efir.core.app.localDeviceInfo
import ru.efir.core.app.openLocalFolder
import ru.efir.core.app.savePreference
import ru.efir.core.app.systemLanguageCode
import ru.efir.core.pairing.FileTrustStore
import ru.efir.core.protocol.DeviceType
import ru.efir.core.sync.FileSyncStateStore
import ru.efir.core.transfer.FileTransferQueueStore
import ru.efir.core.ui.ExtraSwitch
import ru.efir.core.ui.LocalExtraSettings
import ru.efir.core.ui.LocalTitleBar
import ru.efir.core.ui.TitleBarSpec
import ru.efir.core.ui.i18n.Lang
import ru.efir.core.ui.theme.EfirLogoVector
import java.awt.Dimension
import java.awt.Frame
import java.io.File

private const val PREF_CLOSE_TO_TRAY = "closeToTray"
private const val PREF_SINGLE_INSTANCE = "singleInstance"
private const val PREF_TRAY_TOLD = "closeToTrayTold"

/** Сколько доверенных устройств показывать в меню трея. */
private const val TRAY_DEVICES = 5

fun main(args: Array<String>) {
    val dataDir = File(System.getProperty("user.home"), ".efir")
    // Данные прежнего названия (сопряжения, правила, настройки) переезжают один раз.
    val oldDataDir = File(System.getProperty("user.home"), ".mind")
    if (!dataDir.exists() && oldDataDir.isDirectory) oldDataDir.renameTo(dataDir)
    AppLog.install(FileLogSink(File(dataDir, "logs")))
    installCrashLog(dataDir)
    AppLog.i(
        "app",
        "start ${BuildInfo.VERSION}, ${System.getProperty("os.name")} ${System.getProperty("os.version")}, " +
            "java ${System.getProperty("java.vm.vendor")} ${System.getProperty("java.version")}" +
            if (AutoStart.ARG in args) ", started by the system" else "",
    )

    AutoStart.startedBySystem = AutoStart.ARG in args
    // Второй запуск показывает уже открытое окно и завершается (Настройки → Общие; можно выключить).
    if (loadPreference(PREF_SINGLE_INSTANCE) != "false" && !SingleInstance.claim(dataDir)) return

    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val info = localDeviceInfo()
    val controller = EfirController(
        scope = scope,
        selfType = DeviceType.WINDOWS,
        selfDeviceId = info.deviceId,
        selfName = info.name,
        selfHost = info.host,
        trustStore = FileTrustStore(dataDir),
        // Правила синхронизации и их журнал переживают перезапуск.
        syncStateStore = FileSyncStateStore(dataDir),
        transferQueueStore = FileTransferQueueStore(dataDir),
        // Десктоп по умолчанию ждёт подключения (режим сервиса-переключателя).
        listenOnStart = true,
        // Порт ожидания задаётся в «Настройках» → «Сеть».
        listenPort = ru.efir.core.net.NetworkSettings.listenPort(),
    )
    controller.start()

    application {
        val trayThere = remember { isTraySupported }
        var closeToTray by remember { mutableStateOf(loadPreference(PREF_CLOSE_TO_TRAY) != "false") }
        var singleInstance by remember { mutableStateOf(loadPreference(PREF_SINGLE_INSTANCE) != "false") }
        var autoStart by remember { mutableStateOf(AutoStart.isOn()) }
        // Запущен системой — сразу в трей; окно приходит от значка.
        var windowVisible by remember { mutableStateOf(!(AutoStart.startedBySystem && trayThere)) }
        var frame by remember { mutableStateOf<Frame?>(null) }
        val windowState = rememberWindowState(width = 1100.dp, height = 760.dp)
        val trayState = rememberTrayState()

        val connection by controller.connection.collectAsState()
        val trusted by controller.trustedDevices.collectAsState()
        val rules by controller.syncRules.collectAsState()
        val screenShown by controller.screenHostState.collectAsState()
        // Язык трея — тот же, что выбран в приложении (читается, когда меню перерисовывается).
        val s = Lang.byCode(loadPreference("language") ?: systemLanguageCode()).strings

        fun exit() {
            AppLog.i("app", "exit")
            runCatching { AppLog.read() }
            controller.stop()
            exitApplication()
        }

        /** Окно из трея — на экран и поверх остальных; [section] открывает нужный раздел. */
        fun showWindow(section: String? = null) {
            windowVisible = true
            windowState.isMinimized = false
            frame?.let {
                it.isVisible = true
                it.state = Frame.NORMAL
                it.toFront()
                it.requestFocus()
            }
            if (section != null) AppNavigation.request.value = section
        }
        SingleInstance.onShow = { showWindow() }

        // Свёрнуто или в трее — не на виду: поиск устройств и перечитывание состояния останавливаются.
        LaunchedEffect(windowVisible, windowState.isMinimized) {
            AppVisibility.visible.value = windowVisible && !windowState.isMinimized
        }

        // Экран этого компьютера начали показывать: об этом говорит уведомление, даже если окно в трее.
        LaunchedEffect(screenShown.active) {
            if (screenShown.active && trayThere) {
                runCatching { trayState.sendNotification(Notification(s.appName, s.trayScreenShown(connection?.remote?.name.orEmpty()))) }
            }
        }

        // Пришёл текст с другого устройства: окно выходит на экран, чтобы его было видно.
        val receivedText by controller.receivedText.collectAsState()
        LaunchedEffect(receivedText) {
            if (receivedText != null) showWindow()
        }

        if (trayThere) {
            Tray(
                icon = rememberVectorPainter(EfirLogoVector),
                state = trayState,
                // Версия видна, не открывая окна.
                tooltip = (connection?.let { s.trayConnected(it.remote.name) } ?: s.trayIdle) + " · " + BuildInfo.VERSION,
                onAction = { showWindow() },
                menu = {
                    Item(s.trayOpen, onClick = { showWindow() })
                    Separator()
                    val link = connection
                    if (link != null) {
                        // Действия с подключённым устройством.
                        Item(s.trayScreen, onClick = { showWindow("SCREEN") })
                        Item(s.trayFiles, onClick = { showWindow("FILES") })
                        if (rules.any { it.enabled }) {
                            Item(s.traySyncAll, onClick = { rules.filter { it.enabled }.forEach { controller.runSyncRule(it.id) } })
                        }
                        Item(s.trayDisconnect + ": " + link.remote.name, onClick = { controller.disconnect() })
                    } else {
                        // Подключение к доверенному устройству по адресу прошлого раза.
                        trusted.filter { it.lastHost != null }.sortedByDescending { it.lastSeenAt }.take(TRAY_DEVICES).forEach { device ->
                            Item(s.trayConnect(device.name), onClick = {
                                controller.connectTo(
                                    hostAddress = device.lastHost.orEmpty(),
                                    port = device.lastPort.takeIf { it > 0 } ?: controller.selfPort,
                                    deviceId = device.deviceId,
                                )
                            })
                        }
                    }
                    Item(s.trayDevices, onClick = { showWindow("DEVICES") })
                    Item(s.trayDownloads, onClick = { openLocalFolder(controller.downloadDir) })
                    Separator()
                    Item(s.trayExit, onClick = { exit() })
                },
            )
        }

        Window(
            onCloseRequest = {
                if (closeToTray && trayThere) {
                    // Крестик прячет окно в трей: устройство остаётся на связи. Один раз об этом говорим.
                    windowVisible = false
                    if (loadPreference(PREF_TRAY_TOLD) != "true") {
                        savePreference(PREF_TRAY_TOLD, "true")
                        runCatching { trayState.sendNotification(Notification(s.appName, s.trayNotice)) }
                    }
                } else {
                    exit()
                }
            },
            visible = windowVisible,
            title = s.appName,
            icon = rememberVectorPainter(EfirLogoVector),
            state = windowState,
        ) {
            window.minimumSize = Dimension(420, 560)
            LaunchedEffect(window) { frame = window }
            // Верхняя панель приложения служит заголовком окна (на JetBrains Runtime).
            val chrome = rememberWindowChrome()
            LaunchedEffect(chrome) { AppLog.i("app", if (chrome.native) "the top bar is the window's title bar" else "the system title bar is kept (no JetBrains Runtime)") }
            val titleBar = remember(chrome, chrome.rightInset) {
                if (chrome.native) TitleBarSpec(chrome.hitTest(), chrome.rightInset, chrome::setDarkControls) else null
            }
            val options = buildList {
                if (trayThere) {
                    add(
                        ExtraSwitch({ it.closeToTray }, { it.closeToTrayHint }, closeToTray) { on ->
                            closeToTray = on
                            savePreference(PREF_CLOSE_TO_TRAY, on.toString())
                        },
                    )
                }
                if (AutoStart.available) {
                    add(
                        ExtraSwitch({ it.autoStart }, { it.autoStartHint }, autoStart) { on ->
                            AutoStart.set(on)
                            autoStart = AutoStart.isOn()
                        },
                    )
                }
                add(
                    ExtraSwitch({ it.singleInstance }, { it.singleInstanceHint }, singleInstance) { on ->
                        singleInstance = on
                        savePreference(PREF_SINGLE_INSTANCE, on.toString())
                    },
                )
            }
            CompositionLocalProvider(
                LocalTitleBar provides titleBar,
                LocalExtraSettings provides options,
            ) {
                EfirAppScreen(controller)
            }
        }
    }
}
