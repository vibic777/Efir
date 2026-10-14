package ru.efir.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.addCallback
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import ru.efir.core.BuildInfo
import ru.efir.core.app.AppLog
import ru.efir.core.app.AppVisibility
import ru.efir.core.app.EfirAppScreen
import ru.efir.core.app.EfirController
import ru.efir.core.app.FileLogSink
import ru.efir.core.app.loadPreference
import ru.efir.core.app.localDeviceInfo
import ru.efir.core.app.savePreference
import ru.efir.core.media.EfirAndroidEnvironment
import ru.efir.core.pairing.FileTrustStore
import ru.efir.core.protocol.DeviceType
import ru.efir.core.sync.FileSyncStateStore
import ru.efir.core.transfer.FileTransferQueueStore
import ru.efir.core.ui.ExtraSwitch
import ru.efir.core.ui.LocalBackPress
import ru.efir.core.ui.LocalExtraSettings
import java.io.File
import kotlin.system.exitProcess

/**
 * Android-точка входа: создаёт контроллер, инициализирует окружение ядра
 * (контекст, MediaProjection, foreground-сервис) и показывает общий UI.
 *
 * Контроллер живёт от [onCreate] до [onDestroy], а не пока экран на виду:
 * телефоном управляют с ПК как раз тогда, когда Efir свёрнут.
 *
 * Батарея (`docs/BATTERY_RU.md`): с уходом с экрана гаснет всё, что нужно только
 * глазам ([AppVisibility]); фоновый сервис работает, только пока он нужен
 * ([updateService]), а процессор удерживается только на время соединения.
 * Разрешения при запуске не запрашиваются: каждое выдаётся отдельно в «Настройках».
 */
class MainActivity : ComponentActivity() {

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private lateinit var controller: EfirController

    /** «Оставаться на связи в фоне» (Настройки → Общие). */
    private var keepInBackground by mutableStateOf(true)

    @Volatile
    private var connected = false

    private var serviceWanted: Boolean? = null

    /** Запрос разрешения MediaProjection (блок 04). */
    private val projectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val data = result.data
        AppLog.i("perm", "screen sharing " + if (result.resultCode == RESULT_OK && data != null) "allowed" else "refused")
        val granted = result.resultCode == RESULT_OK && data != null
        if (granted) {
            EfirAndroidEnvironment.projectionPermission =
                EfirAndroidEnvironment.ProjectionPermission(result.resultCode, data!!)
            // Сервис типа mediaProjection обязан работать до начала захвата.
            updateService(force = true)
        }
        // Показа экрана мог ждать другой конец соединения: ему — ответ человека.
        EfirAndroidEnvironment.onProjectionAnswer(granted)
    }

    /** Вопрос о показе экрана отложен до возвращения Эфира на экран (его попросили, пока он был свёрнут). */
    private var askScreenOnResume = false

    /** Запрос исключения из оптимизации батареи (Doze). */
    private val batteryOptimizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { /* Результат не критичен: пользователь мог оставить как есть. */ }

    /** Запрос runtime-разрешений одной группы: камера, микрофон, SMS, звонки или уведомления. */
    private val runtimePermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        // Состояние читает экран «Настройки»; в журнал — что человек ответил.
        result.forEach { (permission, granted) ->
            AppLog.i("perm", permission.substringAfterLast('.') + (if (granted) " granted" else " refused"))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // Журнал — первым: по нему ищут причину всего, что случится дальше.
        AppLog.install(FileLogSink(File(filesDir, "logs")))
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            AppLog.fatal("app", "uncaught error in thread ${thread.name}", error)
            runCatching { AppLog.read() }
            previousHandler?.uncaughtException(thread, error)
        }
        AppLog.i(
            "app",
            "start ${BuildInfo.VERSION}, Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}), ${Build.MANUFACTURER} ${Build.MODEL}",
        )

        // Инициализируем окружение ядра до создания UI/контроллера.
        EfirAndroidEnvironment.appContext = applicationContext
        EfirAndroidEnvironment.requestForegroundService = { updateService(force = true) }
        // Сервис не останавливаем вместе с показом экрана: решает updateService.
        EfirAndroidEnvironment.stopForegroundService = { }
        EfirAndroidEnvironment.requestNotificationPermission = { }
        EfirAndroidEnvironment.requestIgnoreBatteryOptimizations = { requestIgnoreBatteryOptimizations() }
        // Просьба приходит и из сети, не из потока интерфейса.
        EfirAndroidEnvironment.requestProjection = { runOnUiThread { requestProjectionPermission() } }
        EfirAndroidEnvironment.projectionServiceReady = { ScreenCaptureService.projectionReady }
        EfirAndroidEnvironment.requestPermissions = { permissions -> requestRuntimePermissions(permissions) }
        EfirAndroidEnvironment.closeApp = {
            ScreenCaptureService.stop(this)
            finishAndRemoveTask()
            exitProcess(0)
        }
        keepInBackground = loadPreference(PREF_BACKGROUND) != "false"

        val info = localDeviceInfo()
        controller = EfirController(
            scope = appScope,
            selfType = DeviceType.ANDROID,
            selfDeviceId = info.deviceId,
            selfName = info.name,
            selfHost = info.host,
            trustStore = FileTrustStore(filesDir),
            // Правила синхронизации и их журнал переживают перезапуск.
            syncStateStore = FileSyncStateStore(filesDir),
            transferQueueStore = FileTransferQueueStore(filesDir),
            // Телефон по умолчанию ждёт подключения (режим сервиса-переключателя).
            listenOnStart = true,
            // Порт ожидания задаётся в «Настройках» → «Сеть».
            listenPort = ru.efir.core.net.NetworkSettings.listenPort(),
        )
        controller.start()

        // Соединение есть — сервис держит процесс и процессор; нет — отпускает.
        appScope.launch {
            controller.connection.collect { link ->
                connected = link != null
                runOnUiThread { updateService() }
            }
        }

        setContent {
            val background = ExtraSwitch(
                title = { it.keepInBackground },
                hint = { it.keepInBackgroundHint },
                checked = keepInBackground,
                onChange = { enabled ->
                    keepInBackground = enabled
                    savePreference(PREF_BACKGROUND, enabled.toString())
                    updateService()
                },
            )
            CompositionLocalProvider(
                LocalExtraSettings provides listOf(background),
                // «Назад» внутри приложения: со страницы — в меню, из раздела — к устройствам.
                LocalBackPress provides { enabled, onBack -> BackHandler(enabled, onBack) },
            ) {
                EfirAppScreen(controller)
            }
        }
        updateService()
        if (intent?.getBooleanExtra(EXTRA_ASK_SCREEN, false) == true) askScreenOnResume = true

        // «Назад» с первого экрана сворачивает Эфир, а не закрывает: телефон остаётся на связи.
        // Экраны приложения перехватывают «Назад» раньше (BackPress), сюда доходит только последнее нажатие.
        onBackPressedDispatcher.addCallback(this) { moveTaskToBack(true) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Нажато уведомление «Показать экран?»: вопрос задаётся, когда окно выйдет на экран.
        if (intent.getBooleanExtra(EXTRA_ASK_SCREEN, false)) askScreenOnResume = true
    }

    override fun onResume() {
        super.onResume()
        if (askScreenOnResume) {
            askScreenOnResume = false
            getSystemService(NotificationManager::class.java)?.cancel(ASK_SCREEN_NOTIFICATION)
            requestProjectionPermission()
        }
    }

    /** Пока Эфир на экране, Wi-Fi пропускает к нему пакеты поиска «всей сети» (иначе Android их отбрасывает). */
    private var multicastLock: WifiManager.MulticastLock? = null

    override fun onStart() {
        super.onStart()
        AppVisibility.visible.value = true
        runCatching {
            val wifi = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            multicastLock = wifi.createMulticastLock("efir-discovery").apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    override fun onStop() {
        // Замок держится только на виду: в фоне он зря будил бы Wi-Fi чужими пакетами.
        runCatching { multicastLock?.takeIf { it.isHeld }?.release() }
        multicastLock = null
        // С экрана ушли: поиск устройств и перечитывание состояния останавливаются.
        AppVisibility.visible.value = false
        super.onStop()
    }

    /**
     * Запускает или останавливает фоновый сервис. Он нужен, когда идёт соединение,
     * когда выдан показ экрана (Android требует сервис для захвата) или когда
     * человек оставил «Оставаться на связи в фоне». Иначе сервиса нет вовсе.
     */
    private fun updateService(force: Boolean = false) {
        val needed = force || connected || keepInBackground || EfirAndroidEnvironment.projectionPermission != null
        ScreenCaptureService.connected = connected
        if (needed != serviceWanted) {
            serviceWanted = needed
            AppLog.i("service", if (needed) "background service wanted (connected=$connected, keep=$keepInBackground)" else "background service not needed")
        }
        runCatching {
            if (needed) ScreenCaptureService.start(this) else ScreenCaptureService.stop(this)
        }.onFailure { AppLog.w("service", "the background service could not be started", it) }
    }

    /**
     * Запрашивает ещё не выданные разрешения одной группы. После «Больше не
     * спрашивать» система окно не покажет — тогда открываем страницу приложения,
     * где разрешение включается вручную.
     */
    private fun requestRuntimePermissions(permissions: List<String>) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isEmpty()) return
        val asked = getSharedPreferences(PREFS_ASKED, Context.MODE_PRIVATE)
        val canAsk = missing.any { !asked.getBoolean(it, false) || shouldShowRequestPermissionRationale(it) }
        AppLog.i("perm", "asking for " + missing.joinToString { it.substringAfterLast('.') } + if (canAsk) "" else " (the system no longer asks: opening the app's settings)")
        if (!canAsk) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")),
                )
            }
            return
        }
        asked.edit().apply { missing.forEach { putBoolean(it, true) } }.apply()
        runCatching { runtimePermissionsLauncher.launch(missing.toTypedArray()) }
    }

    /**
     * Открывает системный диалог исключения приложения из оптимизации батареи,
     * чтобы Doze не приостанавливал сессию в фоне. Если исключение уже выдано —
     * диалог не показывается.
     */
    private fun requestIgnoreBatteryOptimizations() {
        val power = getSystemService(POWER_SERVICE) as PowerManager
        if (power.isIgnoringBatteryOptimizations(packageName)) return
        runCatching {
            @Suppress("BatteryLife")
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                .setData(Uri.parse("package:$packageName"))
            batteryOptimizationLauncher.launch(intent)
        }
    }

    /**
     * Показывает системный диалог запроса разрешения на захват экрана. Если Эфир
     * свёрнут, окно из фона Android открыть не даёт: тогда в шторке появляется
     * уведомление «Показать экран?», нажатие на которое открывает Эфир с этим вопросом.
     */
    private fun requestProjectionPermission() {
        if (EfirAndroidEnvironment.projectionPermission != null) {
            EfirAndroidEnvironment.onProjectionAnswer(true)
            return
        }
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) {
            AppLog.i("perm", "screen sharing is asked for while the app is in the background: showing a notification")
            notifyAskScreen()
            return
        }
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        runCatching { projectionLauncher.launch(manager.createScreenCaptureIntent()) }
            .onFailure {
                AppLog.w("perm", "the screen sharing question could not be shown", it)
                EfirAndroidEnvironment.onProjectionAnswer(false)
            }
    }

    /** Уведомление «Показать экран?» — для случая, когда Эфир свёрнут. */
    private fun notifyAskScreen() {
        val manager = getSystemService(NotificationManager::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(ASK_CHANNEL, getString(R.string.ask_channel), NotificationManager.IMPORTANCE_HIGH),
            )
        }
        val open = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(EXTRA_ASK_SCREEN, true),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, ASK_CHANNEL)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        val notification = builder
            .setContentTitle(getString(R.string.ask_screen_title))
            .setContentText(getString(R.string.ask_screen_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        // Без разрешения на уведомления Android его не покажет: тогда вопрос ждёт, пока Эфир откроют.
        runCatching { manager.notify(ASK_SCREEN_NOTIFICATION, notification) }
        askScreenOnResume = true
    }

    override fun onDestroy() {
        AppLog.i("app", "the app is closed")
        controller.stop()
        ScreenCaptureService.connected = false
        ScreenCaptureService.stop(this)
        EfirAndroidEnvironment.projectionPermission = null
        EfirAndroidEnvironment.appContext = null
        EfirAndroidEnvironment.requestForegroundService = null
        EfirAndroidEnvironment.stopForegroundService = null
        EfirAndroidEnvironment.requestNotificationPermission = null
        EfirAndroidEnvironment.requestIgnoreBatteryOptimizations = null
        EfirAndroidEnvironment.requestProjection = null
        EfirAndroidEnvironment.projectionServiceReady = null
        EfirAndroidEnvironment.requestPermissions = null
        EfirAndroidEnvironment.closeApp = null
        appScope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val PREF_BACKGROUND = "keepInBackground"
        const val PREFS_ASKED = "efir_permissions_asked"
        const val EXTRA_ASK_SCREEN = "ru.efir.android.ASK_SCREEN"
        const val ASK_CHANNEL = "efir_requests"
        const val ASK_SCREEN_NOTIFICATION = 4102
    }
}
