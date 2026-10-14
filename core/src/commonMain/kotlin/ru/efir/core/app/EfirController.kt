package ru.efir.core.app

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.efir.core.discovery.DiscoveredDevice
import ru.efir.core.discovery.DiscoveryAnnounce
import ru.efir.core.discovery.DiscoveryManager
import ru.efir.core.discovery.createDiscoveryTransport
import ru.efir.core.net.localIpv4Addresses
import ru.efir.core.files.FileBrowserState
import ru.efir.core.files.FileManagerClient
import ru.efir.core.files.FileManagerHost
import ru.efir.core.input.InputInjectionHost
import ru.efir.core.input.isAccessibilityServiceEnabled
import ru.efir.core.input.inputInjectionSupported
import ru.efir.core.input.openAccessibilitySettings as openAccessibilitySettingsPlatform
import ru.efir.core.protocol.InputMultiTouch
import ru.efir.core.protocol.TouchStroke
import ru.efir.core.media.AudioStreamClientState
import ru.efir.core.media.AudioStreamHost
import ru.efir.core.media.AudioStreamState
import ru.efir.core.media.CameraStreamClientState
import ru.efir.core.media.CameraStreamHost
import ru.efir.core.media.CameraStreamState
import ru.efir.core.media.MediaStreamClient
import ru.efir.core.media.ScreenStreamClientState
import ru.efir.core.media.ScreenStreamHost
import ru.efir.core.media.ScreenStreamState
import ru.efir.core.media.screenCaptureSupported
import ru.efir.core.net.ClientConfig
import ru.efir.core.net.EfirHost
import ru.efir.core.net.EfirNetwork
import ru.efir.core.net.EfirSession
import ru.efir.core.net.ServerConfig
import ru.efir.core.transfer.DEFAULT_MAX_CONCURRENT_TRANSFERS
import ru.efir.core.transfer.InMemoryTransferQueueStore
import ru.efir.core.transfer.QueuedTransfer
import ru.efir.core.transfer.TransferEngine
import ru.efir.core.transfer.TransferJob
import ru.efir.core.transfer.TransferQueue
import ru.efir.core.transfer.TransferQueueStore
import ru.efir.core.net.TlsConfig
import ru.efir.core.net.createEfirHost
import ru.efir.core.net.openEfirConnection
import ru.efir.core.pairing.PairingPayload
import ru.efir.core.pairing.PairingService
import ru.efir.core.pairing.SecureTokens
import ru.efir.core.pairing.TrustStore
import ru.efir.core.pairing.TrustedDevice
import ru.efir.core.comms.CallClient
import ru.efir.core.comms.CallController
import ru.efir.core.comms.CallHost
import ru.efir.core.comms.ClipboardClient
import ru.efir.core.comms.ClipboardEntry
import ru.efir.core.comms.ClipboardHost
import ru.efir.core.comms.ClipboardSync
import ru.efir.core.comms.NotificationFilter
import ru.efir.core.comms.NotificationsClient
import ru.efir.core.comms.NotificationsHost
import ru.efir.core.comms.notificationActionsSupported
import ru.efir.core.comms.SmsClient
import ru.efir.core.comms.SmsHost
import ru.efir.core.comms.createLocalClipboard
import ru.efir.core.comms.createSmsRepository
import ru.efir.core.comms.defaultSmsRoleSupported
import ru.efir.core.comms.isDefaultSmsApp
import ru.efir.core.comms.requestDefaultSmsRole
import ru.efir.core.protocol.CallActionType
import ru.efir.core.protocol.Capability
import ru.efir.core.protocol.DeviceInfo
import ru.efir.core.protocol.DeviceType
import ru.efir.core.protocol.KeyAction
import ru.efir.core.protocol.NotificationPosted
import ru.efir.core.protocol.SmsDeliveryStatusChanged
import ru.efir.core.protocol.SmsSubscription
import ru.efir.core.protocol.SyncRuleConfig
import ru.efir.core.protocol.nowMillis
import ru.efir.core.sync.EngineSyncTransfer
import ru.efir.core.sync.FileManagerRemoteFs
import ru.efir.core.sync.InMemorySyncStateStore
import ru.efir.core.sync.SyncEngine
import ru.efir.core.sync.SyncJournal
import ru.efir.core.sync.SyncRunStatus
import ru.efir.core.sync.SyncScheduler
import ru.efir.core.sync.SyncStateStore

/**
 * Роль устройства в **активном соединении**.
 *
 * Платформа (Android/Windows) больше не определяет роль: любое устройство может
 * как ждать подключения ([HOST]), так и инициировать его ([CLIENT]). Тип платформы
 * передаётся отдельно через [ru.efir.core.protocol.DeviceType].
 */
/** Текст, присланный другим устройством для показа: от кого и что. */
data class ReceivedText(val from: String, val text: String)

enum class LinkRole { HOST, CLIENT }

/** Активное соединение с удалённым устройством. */
data class ActiveConnection(
    val remote: DeviceInfo,
    val remoteAddress: String?,
    val trusted: Boolean,
    /** Роль этой стороны в данном соединении. */
    val linkRole: LinkRole = LinkRole.CLIENT,
)

/**
 * Центральный контроллер приложения: владеет обнаружением, хостом/клиентом,
 * сопряжением, трансляцией экрана и инъекцией ввода; хранит состояние UI.
 * Один экземпляр на приложение.
 *
 * Контроллер **симметричен**: [selfType] описывает платформу, а роль в соединении
 * ([LinkRole]) определяется направлением — это устройство может как ждать
 * подключения (поднятый [EfirHost]), так и инициировать его.
 *
 * @param listenOnStart Поднимать сервер сразу при [start] («режим сервиса»:
 *   постоянное ожидание подключения). Иначе сервер поднимается по требованию —
 *   при начале сопряжения или явном [startListening].
 */
class EfirController(
    private val scope: CoroutineScope,
    /** Тип платформы этого устройства (Android/Windows); роль в соединении от него не зависит. */
    val selfType: DeviceType,
    private val selfDeviceId: String,
    /** Отображаемое имя этого устройства. */
    val selfName: String,
    private val selfHost: String,
    private val trustStore: TrustStore,
    private val fingerprint: String? = null,
    private val capabilities: Set<Capability> = emptySet(),
    discoveryPort: Int = EfirNetwork.DISCOVERY_PORT,
    /** Хранилище правил/базовых линий/журнала синхронизации. */
    private val syncStateStore: SyncStateStore = InMemorySyncStateStore(),
    /** Хранилище незавершённых передач для возобновления после перезапуска. */
    private val transferQueueStore: TransferQueueStore = InMemoryTransferQueueStore(),
    private val listenOnStart: Boolean = true,
    /** Порт, на котором устройство ждёт подключения (в тестах — свободный, чтобы не мешать запущенному приложению). */
    private val listenPort: Int = EfirNetwork.DEFAULT_PORT,
) {

    private val discovery: DiscoveryManager

    private val localPeer = LocalPeer(
        deviceId = selfDeviceId,
        name = selfName,
        type = selfType,
        capabilities = capabilities,
    )

    // --- Обнаружение ---
    private val _devices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices: StateFlow<List<DiscoveredDevice>> = _devices.asStateFlow()

    // --- Сопряжение (QR + ключ доступа) ---
    private val _pairingInfo = MutableStateFlow<PairingService.PendingPairing?>(null)
    val pairingInfo: StateFlow<PairingService.PendingPairing?> = _pairingInfo.asStateFlow()

    private val _selfAddresses = MutableStateFlow(listOf(selfHost))

    /**
     * Адреса этого устройства в локальной сети — на сейчас. Перечитываются
     * [refreshNetwork]: пока приложение запускали и начинали сопрягать, сеть могла смениться.
     */
    val selfAddresses: StateFlow<List<String>> = _selfAddresses.asStateFlow()

    /** Основной адрес (IP) этого устройства: он же идёт в данные сопряжения. */
    val selfAddress: String get() = _selfAddresses.value.firstOrNull() ?: selfHost

    /** Порт, на котором эта сторона принимает подключения. */
    val selfPort: Int get() = listenPort

    // --- Соединение ---
    private val _connection = MutableStateFlow<ActiveConnection?>(null)
    val connection: StateFlow<ActiveConnection?> = _connection.asStateFlow()

    private val _connectionError = MutableStateFlow<String?>(null)
    val connectionError: StateFlow<String?> = _connectionError.asStateFlow()

    private val _connecting = MutableStateFlow(false)

    /** Идёт ли сейчас попытка подключения (для индикатора в UI). */
    val connecting: StateFlow<Boolean> = _connecting.asStateFlow()

    /** Убирает сообщение об ошибке соединения. */
    fun clearConnectionError() {
        _connectionError.value = null
    }

    // --- Доверенные устройства ---
    private val _trustedDevices = MutableStateFlow<List<TrustedDevice>>(emptyList())
    val trustedDevices: StateFlow<List<TrustedDevice>> = _trustedDevices.asStateFlow()

    // --- Трансляция экрана ---
    private val _screenHostState = MutableStateFlow(ScreenStreamState())
    val screenHostState: StateFlow<ScreenStreamState> = _screenHostState.asStateFlow()

    private val _cameraHostState = MutableStateFlow(CameraStreamState())
    val cameraHostState: StateFlow<CameraStreamState> = _cameraHostState.asStateFlow()

    private val _audioHostState = MutableStateFlow(AudioStreamState())
    val audioHostState: StateFlow<AudioStreamState> = _audioHostState.asStateFlow()

    private val _screenClientState = MutableStateFlow(ScreenStreamClientState())
    val screenClientState: StateFlow<ScreenStreamClientState> = _screenClientState.asStateFlow()

    private val _cameraClientState = MutableStateFlow(CameraStreamClientState())

    /** Состояние приёма камеры телефона. */
    val cameraClientState: StateFlow<CameraStreamClientState> = _cameraClientState.asStateFlow()

    private val _audioClientState = MutableStateFlow(AudioStreamClientState())

    /** Состояние приёма звука телефона. */
    val audioClientState: StateFlow<AudioStreamClientState> = _audioClientState.asStateFlow()

    private val _remoteScreen = MutableStateFlow<ImageBitmap?>(null)

    /** Последний полученный кадр экрана удалённого устройства (для ПК-клиента). */
    val remoteScreen: StateFlow<ImageBitmap?> = _remoteScreen.asStateFlow()

    private val _remoteCamera = MutableStateFlow<ImageBitmap?>(null)

    /** Последний полученный кадр камеры удалённого устройства (для ПК-клиента). */
    val remoteCamera: StateFlow<ImageBitmap?> = _remoteCamera.asStateFlow()

    /** Поддерживает ли это устройство захват экрана / инъекцию ввода. */
    val canCaptureScreen: Boolean = screenCaptureSupported()
    val canInjectInput: Boolean = inputInjectionSupported()

    // --- Ошибки ввода (например, служба доступности выключена) ---
    private val _inputError = MutableStateFlow<String?>(null)

    private val _receivedText = MutableStateFlow<ReceivedText?>(null)

    /** Текст, присланный другим устройством, который нужно показать человеку окном (или `null`). */
    val receivedText: StateFlow<ReceivedText?> = _receivedText.asStateFlow()

    /** Окно с присланным текстом закрыто. */
    fun clearReceivedText() {
        _receivedText.value = null
    }

    /** Последняя ошибка инъекции ввода (или `null`). */
    val inputError: StateFlow<String?> = _inputError.asStateFlow()

    /** Включена ли служба доступности (для Android-хоста). */
    fun isInputServiceEnabled(): Boolean = isAccessibilityServiceEnabled()

    /** Открывает системные настройки для включения службы доступности. */
    fun openInputServiceSettings(): Boolean = openAccessibilitySettingsPlatform()

    val pairing: PairingService = PairingService(
        trustStore = trustStore,
        fingerprint = fingerprint,
        host = selfHost,
        port = listenPort,
    )

    private val hostHandshake = HostHandshake(
        local = localPeer,
        pairing = pairing,
        trustStore = trustStore,
        onReady = { session, remote, trusted ->
            // Принимающая сторона: удалённое устройство подтвердило доверие
            // (сохранённым токеном или сопряжением) — иначе сюда не попадаем.
            refreshTrusted()
            // Ключ сопряжения одноразовый: после успеха убираем его с экрана.
            if (!pairing.hasPending) _pairingInfo.value = null
            AppLog.i("link", "accepted ${remote.type} \"${remote.name}\" from ${session.connection.remoteAddress}")
            // Соединение настраивается до ответа: когда другая сторона его получит, обработчики здесь уже стоят.
            // Сбой настройки (нет разрешения на звонки, SMS, камеру) сопряжение не срывает: шаги защищены
            // по одному (см. step), а всё остальное пишется в журнал.
            try {
                attachSession(
                    session = session,
                    remote = remote,
                    trusted = trusted,
                    linkRole = LinkRole.HOST,
                    remoteAddress = session.connection.remoteAddress,
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                AppLog.e("link", "the connection could not be set up", e)
            }
        },
    )

    private var host: EfirHost? = null
    private val sessions = mutableListOf<EfirSession>()
    private val sessionsMutex = Mutex()

    // Активные подсистемы соединения. Каждая сторона поднимает **обе** роли:
    // и отдаёт своё экран/файлы, и принимает чужие.
    private var screenHost: ScreenStreamHost? = null
    private var cameraHost: CameraStreamHost? = null
    private var audioHost: AudioStreamHost? = null
    private var mediaClient: MediaStreamClient? = null
    private var inputHost: InputInjectionHost? = null
    private var fileHost: FileManagerHost? = null
    private var fileClient: FileManagerClient? = null
    private var transfer: TransferEngine? = null
    private var activeSession: EfirSession? = null

    /**
     * Область жизни активного соединения: в ней работают подсистемы и
     * подписки на их состояние. Отменяется целиком при разрыве, поэтому после
     * переподключения не остаётся «вторых» коллекторов от прошлой сессии.
     */
    private var linkScope: CoroutineScope? = null

    /** Сериализует установку и разрыв соединения. */
    private val linkMutex = Mutex()

    /**
     * Сессии, к которым набор подсистем уже прикреплён.
     *
     * На принимающей стороне handshake способен вызвать [attachSession] дважды
     * (сначала `Hello` от известного устройства, затем `PairRequest` при
     * сопряжении), поэтому повторное прикрепление подавляется — иначе появились
     * бы дублирующие коллекторы медиа/файловых каналов и второй движок передач.
     */
    private val attachedSessions = mutableSetOf<EfirSession>()

    // --- Очередь передач (параллелизм + приоритеты) ---
    private val transferQueue = TransferQueue(
        scope = scope,
        initialMaxConcurrent = DEFAULT_MAX_CONCURRENT_TRANSFERS,
    )

    /** Максимальное число одновременных передач (настраивается из UI). */
    var maxConcurrentTransfers: Int
        get() = transferQueue.maxConcurrent
        set(value) {
            transferQueue.maxConcurrent = value
        }

    /** Лимит скорости передач (байт/с; `0` — без ограничения). */
    var transferThrottleBytesPerSecond: Long
        get() = transfer?.throttleBytesPerSecond ?: 0
        set(value) {
            transfer?.throttleBytesPerSecond = value
        }

    // --- Движок передачи файлов ---
    private val _transfers = MutableStateFlow<List<TransferJob>>(emptyList())

    /** Задачи передачи файлов (прогресс для UI). */
    val transfers: StateFlow<List<TransferJob>> = _transfers.asStateFlow()

    private val _pendingTransfers = MutableStateFlow<List<QueuedTransfer>>(emptyList())

    /** Незавершённые передачи прошлых запусков (для возобновления). */
    val pendingTransfers: StateFlow<List<QueuedTransfer>> = _pendingTransfers.asStateFlow()

    private val _transferGroups = MutableStateFlow<List<ru.efir.core.transfer.TransferGroup>>(emptyList())

    /** Агрегированный прогресс групп передач (например, папок). */
    val transferGroups: StateFlow<List<ru.efir.core.transfer.TransferGroup>> = _transferGroups.asStateFlow()

    // --- Файловый менеджер ---
    private val _fileBrowser = MutableStateFlow(FileBrowserState())

    /** Состояние файлового браузера (только на клиентской стороне). */
    val fileBrowser: StateFlow<FileBrowserState> = _fileBrowser.asStateFlow()

    /** Локальный backend файловой системы (для передачи с ПК). */
    private val localFiles = ru.efir.core.files.LocalFileSystemFactory.create()

    // --- Синхронизация папок (блок 08) ---
    private val _syncRules = MutableStateFlow<List<SyncRuleConfig>>(emptyList())

    /** Правила синхронизации (сохраняются в [syncStateStore]). */
    val syncRules: StateFlow<List<SyncRuleConfig>> = _syncRules.asStateFlow()

    private val _syncStatus = MutableStateFlow(SyncRunStatus())

    /** Текущий прогресс синхронизации. */
    val syncStatus: StateFlow<SyncRunStatus> = _syncStatus.asStateFlow()

    private val _syncJournal = MutableStateFlow<SyncJournal?>(null)

    /** Журнал последнего выбранного правила синхронизации. */
    val syncJournal: StateFlow<SyncJournal?> = _syncJournal.asStateFlow()

    private val _syncConflicts = MutableStateFlow<List<ru.efir.core.protocol.SyncConflict>>(emptyList())

    /** Неразрешённые конфликты последней синхронизации (стратегия `ASK`). */
    val syncConflicts: StateFlow<List<ru.efir.core.protocol.SyncConflict>> = _syncConflicts.asStateFlow()

    private val _syncPlanPreview = MutableStateFlow<ru.efir.core.protocol.SyncPlanResult?>(null)

    /** Последний dry-run план синхронизации (для UI). */
    val syncPlanPreview: StateFlow<ru.efir.core.protocol.SyncPlanResult?> = _syncPlanPreview.asStateFlow()

    // --- Буфер обмена и уведомления (блок 10) ---
    private val clipboardSync = ClipboardSync(createLocalClipboard(), scope)

    /** История буфера обмена. */
    val clipboardHistory: StateFlow<List<ClipboardEntry>> get() = clipboardSync.history

    private val _notifications = MutableStateFlow<List<NotificationPosted>>(emptyList())

    /** Зеркалированные уведомления Android. */
    val notifications: StateFlow<List<NotificationPosted>> = _notifications.asStateFlow()

    private val _notificationFilter = MutableStateFlow(NotificationFilter())

    /** Настройки фильтрации уведомлений. */
    val notificationFilter: StateFlow<NotificationFilter> = _notificationFilter.asStateFlow()

    private val _knownNotificationPackages = MutableStateFlow<Set<String>>(emptySet())

    /** Пакеты приложений, чьи уведомления приходили (для UI-настроек). */
    val knownNotificationPackages: StateFlow<Set<String>> = _knownNotificationPackages.asStateFlow()

    private var clipboardHost: ClipboardHost? = null
    private var clipboardClient: ClipboardClient? = null
    private var notificationsClient: NotificationsClient? = null

    private val _notificationsHost = MutableStateFlow<NotificationsHost?>(null)

    /** Хост уведомлений для Android-службы (если эта сторона — хост). */
    val notificationsHost: StateFlow<NotificationsHost?> = _notificationsHost.asStateFlow()

    /** Включает/выключает зеркалирование уведомлений. */
    fun setNotificationsEnabled(enabled: Boolean) {
        _notificationFilter.value = _notificationFilter.value.copy(enabled = enabled)
        pushNotificationFilter()
    }

    /** Показывать ли «постоянные» (ongoing) уведомления. */
    fun setNotificationsHideOngoing(hide: Boolean) {
        _notificationFilter.value = _notificationFilter.value.copy(hideOngoing = hide)
        pushNotificationFilter()
    }

    /** Добавляет/убирает пакет в чёрном списке (исключённые приложения). */
    fun toggleNotificationPackage(packageName: String) {
        _notificationFilter.value = _notificationFilter.value.togglePackage(packageName)
        pushNotificationFilter()
    }

    /** Пакеты, исключённые из зеркалирования. */
    private fun pushNotificationFilter() {
        _notificationsHost.value?.updateFilter(_notificationFilter.value)
    }

    /** Поддерживает ли текущая платформа управление уведомлениями (dismiss/reply). */
    val canControlNotifications: Boolean = notificationActionsSupported()

    /** Закрывает уведомление на телефоне. */
    fun dismissNotification(id: String) {
        notificationsClient?.dismiss(id)
    }

    /** Отправляет быстрый ответ на уведомление (например, мессенджер). */
    fun replyToNotification(id: String, text: String) {
        notificationsClient?.reply(id, text)
    }

    // --- SMS и звонки (блок 12) ---
    private val smsRepository = createSmsRepository()

    /** Платформенный источник/исполнитель звонков (Android-хост). */
    private val callController = CallController()

    private val _smsMessages = MutableStateFlow<List<ru.efir.core.protocol.SmsMessage>>(emptyList())

    /** SMS-сообщения (для UI). */
    val smsMessages: StateFlow<List<ru.efir.core.protocol.SmsMessage>> = _smsMessages.asStateFlow()

    private val _callState = MutableStateFlow(ru.efir.core.protocol.CallStateChanged(ru.efir.core.protocol.CallState.IDLE))

    /** Текущее состояние звонка на удалённой стороне. */
    val callState: StateFlow<ru.efir.core.protocol.CallStateChanged> = _callState.asStateFlow()

    private val _callActionResult = MutableStateFlow<String?>(null)

    /** Деталь последнего действия над звонком (`null` — успех/нет данных). */
    val callActionResult: StateFlow<String?> = _callActionResult.asStateFlow()

    private val _smsThreads = MutableStateFlow<List<ru.efir.core.protocol.SmsThread>>(emptyList())

    /** SMS-треды (диалоги) с телефона. */
    val smsThreads: StateFlow<List<ru.efir.core.protocol.SmsThread>> = _smsThreads.asStateFlow()

    private val _callLog = MutableStateFlow<List<ru.efir.core.protocol.CallLogEntry>>(emptyList())

    /** История вызовов с телефона. */
    val callLog: StateFlow<List<ru.efir.core.protocol.CallLogEntry>> = _callLog.asStateFlow()

    private val _smsSubscriptions = MutableStateFlow<List<SmsSubscription>>(emptyList())

    /** Доступные SIM-подписки телефона (для выбора при отправке). */
    val smsSubscriptions: StateFlow<List<SmsSubscription>> = _smsSubscriptions.asStateFlow()

    private val _smsDeliveries = MutableStateFlow<List<SmsDeliveryStatusChanged>>(emptyList())

    /** Последние статусы доставки отправленных SMS. */
    val smsDeliveries: StateFlow<List<SmsDeliveryStatusChanged>> = _smsDeliveries.asStateFlow()

    /** Поддерживаются ли SMS на этой платформе. */
    val smsSupported: Boolean get() = smsRepository.supported

    /** Поддерживается ли запрос роли приложения SMS по умолчанию. */
    val defaultSmsRoleSupported: Boolean
        get() = ru.efir.core.comms.defaultSmsRoleSupported()

    /** Является ли это приложение приложением SMS по умолчанию (для UI-подсказок). */
    fun isDefaultSmsApp(): Boolean =
        smsRepository.isDefaultSmsApp || ru.efir.core.comms.isDefaultSmsApp()

    private var smsHost: SmsHost? = null
    private var smsClient: SmsClient? = null
    private var callHost: CallHost? = null
    private var callClient: CallClient? = null

    private var syncEngine: SyncEngine? = null
    private var syncScheduler: SyncScheduler? = null
    private val _conflictRuleId = MutableStateFlow<String?>(null)

    /** Правило, к которому относятся [syncConflicts]. */
    val conflictRuleId: StateFlow<String?> = _conflictRuleId.asStateFlow()

    init {
        val transport = createDiscoveryTransport(discoveryPort)
        discovery = DiscoveryManager(
            transport = transport,
            scope = scope,
            selfDeviceId = selfDeviceId,
        )
        // Адресный опрос: знакомые устройства по адресу прошлого раза и соседи по подсети.
        discovery.knownHosts = { _trustedDevices.value.mapNotNull { it.lastHost?.takeIf { host -> host.isNotBlank() } } }
        discovery.subnetHosts = { ru.efir.core.net.localSubnetHosts() }
        scope.launch { refreshTrusted() }
        scope.launch { reloadSyncRules() }
    }

    // --- Режим «сервис»: постоянное ожидание подключения ---
    private val _listening = MutableStateFlow(false)

    /** Поднят ли сервер приёма входящих соединений (режим «ждать подключения»). */
    val listening: StateFlow<Boolean> = _listening.asStateFlow()

    private var discoveryJob: Job? = null

    /** Запускает обнаружение, рассылку анонса и сервер ожидания. Повторный вызов безвреден. */
    fun start() {
        if (discoveryJob != null) return
        discovery.selfAnnounce = {
            DiscoveryAnnounce(
                deviceId = selfDeviceId,
                name = selfName,
                type = selfType,
                host = selfAddress,
                port = listenPort,
                capabilities = capabilities,
                timestamp = 0L,
                waitingToPair = _pairingInfo.value?.let { it.payload.expiresAt > nowMillis() } == true,
            )
        }
        discovery.start()
        discoveryJob = scope.launch { discovery.found.collect { _devices.value = it } }
        refreshNetwork()

        // Любая платформа может постоянно ждать подключение (режим сервиса).
        if (listenOnStart) startHost()
    }

    /**
     * Включает или выключает поиск устройств в сети. Поиск — единственное, что
     * рассылает пакеты по сети, поэтому идёт, только пока список устройств на экране.
     */
    fun setSearching(enabled: Boolean) {
        if (enabled != discovery.searching) AppLog.d("net", if (enabled) "search started" else "search stopped")
        discovery.setSearching(enabled)
    }

    private var networkLogged = false

    /**
     * Перечитывает сетевые адреса этого устройства и ищет устройства заново.
     * Нужна, когда сеть сменилась уже после запуска: другой Wi-Fi, кабель вместо
     * Wi-Fi, выключенный VPN. Порт ожидания слушает все адреса, его не трогаем.
     */
    fun refreshNetwork() {
        scope.launch {
            val addresses = runCatching { localIpv4Addresses() }.getOrDefault(emptyList()).ifEmpty { listOf(selfHost) }
            val changed = addresses != _selfAddresses.value
            if (changed || !networkLogged) {
                networkLogged = true
                AppLog.i("net", "addresses: " + addresses.joinToString(", "))
            }
            _selfAddresses.value = addresses
            pairing.host = addresses.first()
            if (changed) discovery.refresh()
            if (_listening.value) discovery.announceSelf()
        }
    }

    /** Включает/выключает режим постоянного ожидания подключения («сервис-переключатель»). */
    fun setListeningEnabled(enabled: Boolean) {
        if (enabled) startHost() else stopHost()
    }

    /** Останавливает обнаружение, сервер и активные сессии. */
    fun stop() {
        discoveryJob?.cancel()
        discoveryJob = null
        discovery.stop()
        _devices.value = emptyList()
        stopHost()
        scope.launch { closeLink(error = null) }
    }

    /** Поднимает сервер ожидания входящих соединений (любая платформа). */
    private fun startHost() {
        if (host != null) {
            _listening.value = true
            return
        }
        host = createEfirHost(
            config = ServerConfig(
                bindAddress = "0.0.0.0",
                port = listenPort,
                // TLS-обёртка сервера — на следующей итерации (блок 02/03).
                tls = TlsConfig(enabled = false),
            ),
            onConnection = { connection ->
                val session = EfirSession(connection, scope)
                session.start()
                hostHandshake.attach(session)
                sessionsMutex.withLock { sessions.add(session) }
                connection.closed.await()
                onSessionClosed(session)
            },
        ).also { it.start() }
        _listening.value = true
        discovery.discoverable = true
        AppLog.i("net", "waiting for connections on port $listenPort")
        // Те, кто ищет прямо сейчас, видят устройство сразу, не дожидаясь своего следующего опроса.
        discovery.announceSelf()
    }

    /** Останавливает сервер ожидания. */
    private fun stopHost() {
        if (host != null) AppLog.i("net", "no longer waiting for connections")
        host?.stop()
        host = null
        _listening.value = false
        discovery.discoverable = false
    }

    /**
     * Единая симметричная настройка соединения.
     *
     * Каждая сторона поднимает **обе** роли: отдаёт свой экран/камеру/звук/файлы
     * и одновременно принимает чужие. Коммуникационные подсистемы (SMS, звонки,
     * уведомления) имеют смысл только на телефоне, поэтому они включаются по
     * типу устройства ([DeviceType.ANDROID]), а не по роли в соединении.
     */
    private suspend fun attachSession(
        session: EfirSession,
        remote: DeviceInfo,
        trusted: Boolean,
        linkRole: LinkRole,
        remoteAddress: String?,
    ) {
        // Подсистемы прикрепляем один раз на сессию (см. [attachedSessions]).
        val firstTime = sessionsMutex.withLock { attachedSessions.add(session) }
        if (!firstTime) {
            if (activeSession === session) {
                _connection.value = ActiveConnection(remote, remoteAddress, trusted, linkRole)
            }
            return
        }

        // Активное соединение одно: новое вытесняет прежнее.
        if (activeSession != null && activeSession !== session) {
            closeLink(error = null, keep = session)
        }

        val scope = CoroutineScope(this.scope.coroutineContext + SupervisorJob(this.scope.coroutineContext[Job]))
        linkScope = scope
        activeSession = session
        _connectionError.value = null
        _connection.value = ActiveConnection(remote, remoteAddress, trusted, linkRole)

        // --- Отдача: наш экран / камера / звук / ввод ---
        val screen = ScreenStreamHost(session, scope)
        screen.attach()
        screenHost = screen

        val camera = CameraStreamHost(session, scope)
        camera.attach()
        cameraHost = camera

        val audio = AudioStreamHost(session, scope)
        audio.attach()
        audioHost = audio

        val input = InputInjectionHost(
            session,
            // Компьютер без разрешённого управления присланный текст не вставляет, а показывает окном.
            onTextShown = if (selfType == DeviceType.ANDROID) null else { text ->
                AppLog.i("input", "a text from the other device is shown in a window (${text.length} characters)")
                _receivedText.value = ReceivedText(remote.name, text)
            },
        )
        step("input geometry") { input.ensureGeometryFromDevice() }
        input.attach()
        inputHost = input

        // --- Приём: их экран / камера / звук ---
        val client = MediaStreamClient(session, scope)
        client.start()
        mediaClient = client

        // --- Файлы: обе роли на одном движке ---
        val files = FileManagerHost(session)
        files.attach()
        fileHost = files

        val fileBrowserClient = FileManagerClient(session, scope)
        fileClient = fileBrowserClient
        step("file roots") { fileBrowserClient.loadRoots() }
        scope.launch { fileBrowserClient.state.collect { _fileBrowser.value = it } }

        val engine = TransferEngine(
            session = session,
            scope = scope,
            fileSystem = localFiles,
            queue = transferQueue,
            remoteLister = { path -> fileBrowserClient.listRemote(path) },
            queueStore = transferQueueStore,
        )
        engine.attachBoth()
        transfer = engine
        scope.launch { engine.jobs.collect { _transfers.value = it } }
        scope.launch { engine.pendingTransfers.collect { _pendingTransfers.value = it } }
        scope.launch {
            engine.jobs.collect { jobs -> _transferGroups.value = ru.efir.core.transfer.aggregateGroups(jobs) }
        }

        step("sync") { setupSync(scope, fileBrowserClient, engine) }

        // --- Буфер обмена: обе стороны зеркалят друг друга ---
        val clipHost = ClipboardHost(session, clipboardSync, scope)
        clipHost.attach()
        clipboardHost = clipHost

        val clipClient = ClipboardClient(session, clipboardSync, scope)
        clipClient.attach()
        clipboardClient = clipClient
        step("clipboard") { clipClient.pullRemote() }

        // --- Телефонные функции — только если удалённая или локальная сторона Android ---
        if (selfType == DeviceType.ANDROID) {
            step("notifications") {
                _notificationsHost.value =
                    NotificationsHost(session, scope, _notificationFilter.value).also { it.attach() }
            }

            step("sms") {
                val sms = SmsHost(session, smsRepository, scope)
                sms.attach()
                smsHost = sms
            }

            // Без разрешения на звонки слежение за ними не запускается — соединение при этом остаётся.
            step("calls") { callController.start() }
            scope.launch {
                callController.states().collect { state ->
                    _callState.value = state
                    callHost?.emit(state)
                }
            }
            val calls = CallHost(
                session = session,
                scope = scope,
                onAction = { action -> callController.performAction(action) },
                onInitiate = { number, subscriptionId ->
                    callController.initiateCall(number, subscriptionId)
                },
            )
            calls.attach()
            callHost = calls
        }

        if (remote.type == DeviceType.ANDROID) {
            val notif = NotificationsClient(session, scope, _notificationFilter.value)
            notif.attach()
            notificationsClient = notif
            scope.launch { notif.notifications.collect { _notifications.value = it } }
            scope.launch { notif.knownPackages.collect { _knownNotificationPackages.value = it } }
            scope.launch { _notificationFilter.collect { notif.updateFilter(it) } }

            val sms = SmsClient(session, scope)
            sms.attach()
            smsClient = sms
            scope.launch { sms.messages.collect { _smsMessages.value = it } }
            scope.launch { sms.threads.collect { _smsThreads.value = it } }
            scope.launch { sms.callLog.collect { _callLog.value = it } }
            scope.launch { sms.subscriptions.collect { _smsSubscriptions.value = it } }
            scope.launch { sms.deliveries.collect { _smsDeliveries.value = it } }
            step("sms lists") {
                sms.loadSubscriptions()
                sms.loadThreads()
            }

            val calls = CallClient(session, scope)
            calls.attach()
            callClient = calls
            scope.launch { calls.state.collect { _callState.value = it } }
            scope.launch { calls.lastActionResult.collect { _callActionResult.value = it } }
        }

        scope.launch { screen.state.collect { _screenHostState.value = it } }
        scope.launch { camera.state.collect { _cameraHostState.value = it } }
        scope.launch { audio.state.collect { _audioHostState.value = it } }
        scope.launch { client.screenState.collect { _screenClientState.value = it } }
        scope.launch { client.latestScreen.collect { _remoteScreen.value = it } }
        scope.launch { client.cameraState.collect { _cameraClientState.value = it } }
        scope.launch { client.latestCamera.collect { _remoteCamera.value = it } }
        scope.launch { client.audioState.collect { _audioClientState.value = it } }

        AppLog.i("link", "connected: ${remote.type} \"${remote.name}\" at $remoteAddress, role $linkRole")
        watchLink(session)
    }

    /**
     * Шаг настройки соединения. Его сбой (нет разрешения, ошибка платформы, молчащая
     * другая сторона) пишется в журнал и отключает только эту возможность.
     */
    private suspend fun step(name: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            AppLog.w("link", "setup step \"$name\" failed", e)
        }
    }

    /**
     * Следит за жизнью соединения: закрытие транспорта или тишина в heartbeat
     * (удалённая сторона пропала из сети без закрытия сокета) разрывают связь.
     */
    private fun watchLink(session: EfirSession) {
        val attachedAt = nowMillis()
        linkScope?.launch {
            while (true) {
                delay(HEARTBEAT_CHECK_MS)
                val lastSeen = maxOf(session.lastPongAt.value, attachedAt)
                if (nowMillis() - lastSeen > HEARTBEAT_TIMEOUT_MS) {
                    // Закрытие сессии завершит `closed`, а дальше сработает общий путь.
                    this@EfirController.scope.launch { runCatching { session.close() } }
                    return@launch
                }
            }
        }
        this.scope.launch {
            session.connection.closed.await()
            onSessionClosed(session)
        }
    }

    /** Сессия закрылась (с любой стороны): забываем её и, если она активная, сбрасываем связь. */
    private suspend fun onSessionClosed(session: EfirSession) {
        sessionsMutex.withLock {
            sessions.remove(session)
            attachedSessions.remove(session)
        }
        closeLink(error = "Соединение разорвано", only = session)
    }

    /**
     * Начинает сопряжение на этой стороне (для показа QR/JSON + ключа доступа).
     * Сервер ожидания поднимается автоматически: ждать подключение может любое устройство.
     */
    fun beginPairing() {
        _pairingInfo.value = pairing.begin(
            deviceId = selfDeviceId,
            deviceName = selfName,
            type = selfType,
        )
        startHost()
        discovery.announceSelf()
        AppLog.i("pair", "waiting with an access key")
    }

    /** Завершает сессию сопряжения. */
    fun cancelPairing() {
        pairing.cancel()
        _pairingInfo.value = null
    }

    /**
     * Подключается к найденному устройству и выполняет handshake.
     * Если оно ещё не сопряжено, требуется токен из QR и ключ доступа.
     */
    fun connectTo(
        hostAddress: String,
        port: Int = EfirNetwork.DEFAULT_PORT,
        pairingToken: String? = null,
        accessKey: String? = null,
        expectedFingerprint: String? = null,
        /** Идентификатор устройства, если он известен заранее (из обнаружения или QR). */
        deviceId: String? = null,
    ) {
        if (_connecting.value) return
        _connectionError.value = null
        _connecting.value = true
        AppLog.i("link", "connecting to $hostAddress:$port" + (if (accessKey != null) " with an access key" else "") +
            (deviceId?.let { " (device $it)" } ?: ""))
        scope.launch {
            var opened: EfirSession? = null
            runCatching {
                val connection = openEfirConnection(
                    config = ClientConfig(
                        host = hostAddress,
                        port = port,
                        tls = TlsConfig(enabled = false, expectedFingerprint = expectedFingerprint),
                    ),
                    parent = scope,
                )
                val session = EfirSession(connection, scope)
                opened = session
                session.start()
                sessionsMutex.withLock { sessions.add(session) }

                val outcome = ClientHandshake.perform(
                    session = session,
                    local = localPeer,
                    previousSessionToken = deviceId?.let { trustStore.find(it)?.sessionToken },
                    pairingToken = pairingToken,
                    accessKey = accessKey,
                    tokenFor = { id -> trustStore.find(id)?.sessionToken },
                )
                val remote = outcome.remote
                val token = outcome.sessionToken
                if (outcome.accepted && remote != null && token != null) {
                    // Запоминаем удалённую сторону: дальше подключаемся без ключа,
                    // в любом направлении (токен общий для пары устройств).
                    val now = nowMillis()
                    val known = trustStore.find(remote.deviceId)
                    trustStore.save(
                        TrustedDevice(
                            deviceId = remote.deviceId,
                            name = remote.name,
                            type = remote.type,
                            fingerprint = expectedFingerprint ?: known?.fingerprint,
                            sessionToken = token,
                            pairedAt = known?.takeIf { it.sessionToken == token }?.pairedAt ?: now,
                            lastSeenAt = now,
                            lastHost = hostAddress,
                            lastPort = port,
                        ),
                    )
                    refreshTrusted()
                    attachSession(
                        session = session,
                        remote = remote,
                        trusted = true,
                        linkRole = LinkRole.CLIENT,
                        remoteAddress = "$hostAddress:$port",
                    )
                } else {
                    AppLog.w("link", "refused by $hostAddress:$port: ${outcome.reason}")
                    _connectionError.value = outcome.reason ?: "Подключение отклонено"
                    closeSession(session)
                }
            }.onFailure { error ->
                AppLog.e("link", "connecting to $hostAddress:$port failed", error)
                _connectionError.value = connectionErrorText(error, hostAddress, port)
                opened?.let { closeSession(it) }
            }
            _connecting.value = false
        }
    }

    private suspend fun closeSession(session: EfirSession) {
        sessionsMutex.withLock {
            sessions.remove(session)
            attachedSessions.remove(session)
        }
        runCatching { session.close() }
    }

    /** Понятный текст ошибки подключения вместо «сырого» сообщения исключения. */
    private fun connectionErrorText(error: Throwable, host: String, port: Int): String {
        val name = error::class.simpleName.orEmpty()
        return when {
            "Timeout" in name -> "Устройство $host:$port не отвечает"
            "Connect" in name || "UnresolvedAddress" in name || "NoRouteToHost" in name ->
                "Не удалось подключиться к $host:$port — проверьте адрес и что на устройстве включено ожидание"
            else -> error.message?.takeIf { it.isNotBlank() } ?: "Ошибка подключения к $host:$port"
        }
    }

    /** Настраивает движок синхронизации для активного соединения. */
    private fun setupSync(scope: CoroutineScope, files: FileManagerClient, transfer: TransferEngine) {
        val engine = SyncEngine(
            stateStore = syncStateStore,
            localFs = localFiles,
            remoteFs = FileManagerRemoteFs(files),
            transfer = EngineSyncTransfer(transfer),
        )
        syncEngine = engine
        scope.launch { engine.status.collect { _syncStatus.value = it } }

        val scheduler = SyncScheduler(
            scope = scope,
            onRun = { rule -> runCatching { engine.sync(rule) } },
            watcher = runCatching { ru.efir.core.sync.createLocalSyncWatcher() }.getOrNull(),
        )
        syncScheduler = scheduler
        scope.launch { reloadSyncRules() }
        scope.launch {
            scheduler.onConnected(syncStateStore.rules())
        }
    }

    /** Подключается по данным сопряжения из QR-кода с вводом ключа доступа. */
    fun connectByPairing(payload: PairingPayload, accessKey: String) {
        connectTo(
            hostAddress = payload.host,
            port = payload.port,
            pairingToken = payload.pairingToken,
            accessKey = accessKey,
            expectedFingerprint = payload.fingerprint,
            deviceId = payload.deviceId,
        )
    }

    /** Отключает активное соединение. */
    fun disconnect() {
        scope.launch { closeLink(error = null) }
    }

    /**
     * Разрывает активное соединение и возвращает состояние UI к исходному.
     *
     * @param error текст для [connectionError] (`null` — разрыв по желанию пользователя).
     * @param keep сессия, которую закрывать не нужно (новое соединение, вытесняющее старое).
     * @param only разрывать, только если активна именно эта сессия. Проверяется под
     *   замком: о закрытии одной сессии сообщают два наблюдателя, и второй не должен
     *   снести соединение, установленное между их вызовами.
     */
    private suspend fun closeLink(
        error: String?,
        keep: EfirSession? = null,
        only: EfirSession? = null,
    ): Unit = linkMutex.withLock {
        if (only != null && activeSession !== only) return@withLock
        val hadLink = activeSession != null
        if (hadLink) AppLog.i("link", "closed" + (error?.let { ": $it" } ?: ""))
        activeSession = null

        runCatching { callController.stop() }
        runCatching { screenHost?.shutdown() }
        runCatching { cameraHost?.shutdown() }
        runCatching { audioHost?.shutdown() }
        runCatching { mediaClient?.stop() }
        syncScheduler?.stop()
        linkScope?.cancel()
        linkScope = null

        val closing = sessionsMutex.withLock {
            val list = sessions.filter { it !== keep }
            sessions.retainAll { it === keep }
            attachedSessions.retainAll { it === keep }
            list
        }
        closing.forEach { runCatching { it.close() } }

        screenHost = null
        cameraHost = null
        audioHost = null
        mediaClient = null
        inputHost = null
        fileHost = null
        fileClient = null
        transfer = null
        syncEngine = null
        syncScheduler = null
        clipboardHost = null
        clipboardClient = null
        notificationsClient?.clear()
        notificationsClient = null
        smsClient?.clear()
        smsClient = null
        smsHost = null
        callClient?.clear()
        callClient = null
        callHost = null

        _connection.value = null
        if (hadLink && error != null) _connectionError.value = error
        _remoteScreen.value = null
        _remoteCamera.value = null
        _screenHostState.value = ScreenStreamState()
        _cameraHostState.value = CameraStreamState()
        _audioHostState.value = AudioStreamState()
        _screenClientState.value = ScreenStreamClientState()
        _cameraClientState.value = CameraStreamClientState()
        _audioClientState.value = AudioStreamClientState()
        _fileBrowser.value = FileBrowserState()
        _transfers.value = emptyList()
        _pendingTransfers.value = emptyList()
        _transferGroups.value = emptyList()
        _syncStatus.value = SyncRunStatus()
        _syncConflicts.value = emptyList()
        _syncPlanPreview.value = null
        _notifications.value = emptyList()
        _knownNotificationPackages.value = emptySet()
        _notificationsHost.value = null
        _smsMessages.value = emptyList()
        _smsThreads.value = emptyList()
        _callLog.value = emptyList()
        _smsSubscriptions.value = emptyList()
        _smsDeliveries.value = emptyList()
        _callActionResult.value = null
        _inputError.value = null
        _callState.value = ru.efir.core.protocol.CallStateChanged(ru.efir.core.protocol.CallState.IDLE)
    }

    // --- Управление трансляцией экрана (клиент) -------------------------------

    /** Запрашивает у Android-хоста старт трансляции экрана. */
    fun startScreenStream() {
        val client = mediaClient ?: return
        scope.launch { client.startScreen() }
    }

    /** Останавливает трансляцию экрана. */
    fun stopScreenStream() {
        val client = mediaClient ?: return
        scope.launch {
            client.stopScreen()
            _remoteScreen.value = null
        }
    }

    /** Прекращает показ экрана этого устройства: другая сторона узнаёт об этом и убирает картинку. */
    fun stopOwnScreen() {
        val host = screenHost ?: return
        scope.launch { host.stopSharing() }
    }

    // --- Камера и звук (клиент, блок 11) --------------------------------------

    /** Запрашивает у Android-хоста старт трансляции камеры. */
    fun startCameraStream(front: Boolean = false) {
        val client = mediaClient ?: return
        scope.launch {
            client.startCamera(
                facing = if (front) ru.efir.core.protocol.CameraFacing.FRONT
                else ru.efir.core.protocol.CameraFacing.BACK,
            )
        }
    }

    /** Останавливает трансляцию камеры. */
    fun stopCameraStream() {
        val client = mediaClient ?: return
        scope.launch {
            client.stopCamera()
            _remoteCamera.value = null
        }
    }

    /** Запускает передачу звука телефона (микрофон или системный звук). */
    fun startAudioStream(system: Boolean = false) {
        val client = mediaClient ?: return
        scope.launch {
            client.startAudio(
                ru.efir.core.protocol.AudioStart(
                    source = if (system) ru.efir.core.protocol.AudioSource.SYSTEM
                    else ru.efir.core.protocol.AudioSource.MICROPHONE,
                ),
            )
        }
    }

    /** Останавливает передачу звука. */
    fun stopAudioStream() {
        val client = mediaClient ?: return
        scope.launch { client.stopAudio() }
    }

    // --- Инъекция ввода (клиент) ---------------------------------------------

    /** Отправляет хост-устройству тап по нормализованным координатам (0..1). */
    fun sendTap(normalizedX: Float, normalizedY: Float, durationMs: Long = 50) {
        sendInput(ru.efir.core.protocol.InputTap(normalizedX, normalizedY, durationMs))
    }

    /** Отправляет свайп. */
    fun sendSwipe(
        fromX: Float,
        fromY: Float,
        toX: Float,
        toY: Float,
        durationMs: Long = 300,
    ) {
        sendInput(ru.efir.core.protocol.InputSwipe(fromX, fromY, toX, toY, durationMs))
    }

    /** Отправляет мультитач-жест (пинч/зум, несколько пальцев). */
    fun sendMultiTouch(strokes: List<TouchStroke>) {
        if (strokes.isEmpty()) return
        sendInput(InputMultiTouch(strokes))
    }

    /** Отправляет системную клавишу. */
    fun sendKey(action: KeyAction) {
        sendInput(ru.efir.core.protocol.InputKey(action))
    }

    /** Отправляет текст для ввода в сфокусированное поле. */
    fun sendText(text: String) {
        sendInput(ru.efir.core.protocol.InputText(text))
    }

    /** Очищает последнюю ошибку ввода. */
    fun clearInputError() {
        _inputError.value = null
    }

    /**
     * Отправляет RPC-запрос инъекции и обрабатывает ответ: если пришла ошибка
     * (например, служба доступности выключена) — публикует её в [inputError].
     */
    private fun sendInput(request: ru.efir.core.protocol.RpcRequest) {
        val session = activeSession ?: return
        scope.launch {
            runCatching { session.rpc.call(request) }
                .onSuccess { response ->
                    if (response is ru.efir.core.protocol.ErrorMessage) {
                        _inputError.value = response.message
                    }
                }
        }
    }

    // --- Файловый менеджер (клиент) -------------------------------------------

    /** Загружает список корней удалённой файловой системы. */
    fun loadFileRoots() {
        fileClient?.loadRoots()
    }

    /** Открывает каталог удалённой файловой системы. */
    fun openFolder(path: String) {
        fileClient?.open(path)
    }

    /** Переходит к родительскому каталогу. */
    fun fileGoUp() {
        fileClient?.goUp()
    }

    /** Обновляет текущий каталог файлового менеджера. */
    fun refreshFiles() {
        fileClient?.refresh()
    }

    /** Удаляет пути в удалённой файловой системе. */
    fun deleteFiles(paths: List<String>, recursive: Boolean = false) {
        fileClient?.delete(paths, recursive)
    }

    /** Переименовывает элемент удалённой файловой системы. */
    fun renameFile(path: String, newName: String) {
        fileClient?.rename(path, newName)
    }

    /** Создаёт каталог в удалённой файловой системе. */
    fun makeFolder(path: String) {
        fileClient?.mkdir(path)
    }

    /** Загружает предпросмотр файла (метаданные + первые байты) или `null`. */
    suspend fun readFilePreview(path: String, maxBytes: Int = 256 * 1024): Pair<ru.efir.core.protocol.RemoteEntry, ByteArray>? =
        fileClient?.readPreview(path, maxBytes)

    // --- Передача файлов (блок 07) --------------------------------------------

    /** Отправляет локальный файл (ПК) на Android. */
    fun uploadFile(localPath: String, remotePath: String, overwrite: Boolean = false) {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.uploadFile(localPath, remotePath, overwrite) } }
    }

    /** Скачивает файл с удалённой стороны в локальный путь. */
    fun downloadFile(remotePath: String, localPath: String, overwrite: Boolean = false) {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.downloadFile(remotePath, localPath, overwrite) } }
    }

    /** Папка на этом устройстве, куда складываются скачанные файлы (выбирается в «Настройках» → «Папки»). */
    val downloadDir: String get() = AppFolders.downloadDir

    /** Папки другого устройства для выбора в диалоге: `null` — его разделы. Пусто, если соединения нет. */
    suspend fun listRemoteFolders(path: String?): List<ru.efir.core.protocol.RemoteEntry> =
        fileClient?.listRemote(path).orEmpty().filter { it.directory }

    /** Создаёт папку на другом устройстве; `true` при успехе. */
    suspend fun makeRemoteFolder(path: String): Boolean = fileClient?.mkdirRemote(path) == true

    /** Скачивает файл или папку в [downloadDir], не затирая одноимённые. */
    fun downloadToDefault(entry: ru.efir.core.protocol.RemoteEntry) {
        val engine = transfer ?: return
        scope.launch {
            runCatching {
                val dir = downloadDir.replace('\\', '/').trimEnd('/')
                runCatching { localFiles.mkdir(dir) }
                val target = freeLocalName(dir, entry.name)
                if (entry.directory) engine.downloadFolder(entry.path, target)
                else engine.downloadFile(entry.path, target, false)
            }
        }
    }

    /** Отправляет локальные файлы в каталог удалённой стороны. */
    fun uploadTo(remoteDir: String, localPaths: List<String>) {
        val engine = transfer ?: return
        scope.launch {
            localPaths.forEach { local ->
                val name = local.replace('\\', '/').substringAfterLast('/')
                runCatching { engine.uploadFile(local, remoteDir.trimEnd('/') + "/" + name, false) }
            }
        }
    }

    /** Подбирает свободное имя `имя (2).ext` в локальной папке [dir]. */
    private suspend fun freeLocalName(dir: String, name: String): String {
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val base = dot?.let { name.substring(0, it) } ?: name
        val ext = dot?.let { name.substring(it) }.orEmpty()
        var candidate = "$dir/$name"
        var index = 2
        while (runCatching { localFiles.stat(candidate) }.isSuccess && index < 1000) {
            candidate = "$dir/$base ($index)$ext"
            index++
        }
        return candidate
    }

    /** Рекурсивно отправляет локальную папку на удалённую сторону. */
    fun uploadFolder(localDir: String, remoteDir: String) {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.uploadFolder(localDir, remoteDir) } }
    }

    /** Рекурсивно скачивает папку с удалённой стороны локально. */
    fun downloadFolder(remoteDir: String, localDir: String) {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.downloadFolder(remoteDir, localDir) } }
    }

    /** Отменяет активную передачу по идентификатору. */
    fun cancelTransfer(id: String) {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.cancel(id) } }
    }

    /** Возобновляет все незавершённые передачи прошлых запусков. */
    fun resumePendingTransfers() {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.resumeAll() } }
    }

    /** Возобновляет одну незавершённую передачу по идентификатору. */
    fun resumePendingTransfer(id: String) {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.resume(id) } }
    }

    /** Отбрасывает одну незавершённую передачу. */
    fun discardPendingTransfer(id: String) {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.discard(id) } }
    }

    /** Отбрасывает все незавершённые передачи прошлых запусков. */
    fun discardAllPendingTransfers() {
        val engine = transfer ?: return
        scope.launch { runCatching { engine.discardAll() } }
    }

    // --- Синхронизация папок (блок 08) ----------------------------------------

    /** Перечитывает правила синхронизации из хранилища. */
    suspend fun reloadSyncRules() {
        val rules = syncStateStore.rules()
        _syncRules.value = rules
        // Папка правила на этом устройстве может лежать на любом диске: она открывается для обмена.
        AppFolders.ruleFolders = rules.map { it.localPath }
    }

    /** Создаёт/обновляет правило синхронизации (с автосохранением). */
    fun saveSyncRule(rule: SyncRuleConfig) {
        scope.launch {
            syncStateStore.saveRule(rule)
            reloadSyncRules()
            syncScheduler?.start(syncStateStore.rules())
        }
    }

    /** Удаляет правило синхронизации. */
    fun removeSyncRule(ruleId: String) {
        scope.launch {
            syncStateStore.removeRule(ruleId)
            reloadSyncRules()
            syncScheduler?.start(syncStateStore.rules())
        }
    }

    /** Включает/выключает правило. */
    fun setSyncRuleEnabled(ruleId: String, enabled: Boolean) {
        scope.launch {
            val rule = syncStateStore.rule(ruleId) ?: return@launch
            syncStateStore.saveRule(rule.copy(enabled = enabled))
            reloadSyncRules()
            syncScheduler?.start(syncStateStore.rules())
        }
    }

    /** Запускает синхронизацию правила вручную. */
    fun runSyncRule(ruleId: String) {
        val engine = syncEngine ?: return
        scope.launch {
            val rule = syncStateStore.rule(ruleId) ?: return@launch
            val plan = runCatching { engine.sync(rule) }.getOrNull()
            _conflictRuleId.value = ruleId
            _syncConflicts.value = plan?.conflicts.orEmpty()
            _syncJournal.value = syncStateStore.journal(ruleId)
        }
    }

    /** Строит план синхронизации (dry-run), не изменяя данные. */
    suspend fun planSyncRule(ruleId: String): ru.efir.core.protocol.SyncPlanResult? {
        val rule = syncStateStore.rule(ruleId) ?: return null
        val result = syncEngine?.detailedPlan(rule)?.result
        _syncPlanPreview.value = result
        return result
    }

    /** Применяет ручные выборы пользователя по конфликтам. */
    fun resolveSyncConflicts(ruleId: String, resolutions: List<ru.efir.core.protocol.SyncResolution>) {
        val engine = syncEngine ?: return
        scope.launch {
            val rule = syncStateStore.rule(ruleId) ?: return@launch
            val result = runCatching { engine.applyResolutions(rule, resolutions) }.getOrNull()
            _syncConflicts.value = result?.conflicts.orEmpty()
            _syncJournal.value = syncStateStore.journal(ruleId)
        }
    }

    /** Загружает журнал синхронизации правила в состояние UI. */
    fun loadSyncJournal(ruleId: String) {
        scope.launch { _syncJournal.value = syncStateStore.journal(ruleId) }
    }

    // --- История версий синхронизации (бэкапы) --------------------------------

    /** Загружает все резервные версии правила. */
    suspend fun syncVersions(ruleId: String) = syncEngine?.allVersions(ruleId).orEmpty()

    /** Загружает версии конкретного файла. */
    suspend fun fileVersions(ruleId: String, relativePath: String) =
        syncEngine?.versions(ruleId, relativePath).orEmpty()

    /** Восстанавливает выбранную версию файла из бэкапа. */
    fun restoreSyncVersion(ruleId: String, version: ru.efir.core.sync.SyncVersion) {
        val engine = syncEngine ?: return
        scope.launch {
            val rule = syncStateStore.rule(ruleId) ?: return@launch
            engine.restoreVersion(rule, version)
        }
    }

    /** Очищает историю версий правила. */
    fun clearSyncVersions(ruleId: String) {
        scope.launch { syncEngine?.clearVersions(ruleId) }
    }

    // --- SMS и звонки (блок 12) ----------------------------------------------

    /** Загружает список SMS с телефона. */
    fun loadSms(threadId: Long? = null) = smsClient?.load(threadId)

    /** Загружает список SMS-тредов (диалогов). */
    fun loadSmsThreads() = smsClient?.loadThreads()

    /** Загружает историю вызовов. */
    fun loadCallLog() = smsClient?.loadCallLog()

    /** Загружает список SIM-подписок телефона. */
    fun loadSmsSubscriptions() = smsClient?.loadSubscriptions()

    /** Отправляет SMS с ПК (опционально через выбранную SIM). */
    fun sendSms(address: String, body: String, subscriptionId: Int? = null) =
        smsClient?.send(address, body, subscriptionId = subscriptionId)

    /** Выполняет действие над звонком (принять/отклонить/завершить). */
    fun callAction(action: CallActionType) = callClient?.action(action)

    /** Инициирует исходящий звонок с ПК (опционально через выбранную SIM). */
    fun startCall(number: String, subscriptionId: Int? = null) =
        callClient?.initiate(number, subscriptionId)

    /** Запрашивает роль приложения SMS по умолчанию (Android-хост). */
    fun requestDefaultSmsRole(): Boolean = ru.efir.core.comms.requestDefaultSmsRole()

    // --- Буфер обмена и уведомления (блок 10) --------------------------------

    /** Копирует текст в системный буфер и (если открыт клиент) отправляет его. */
    fun copyToClipboard(text: String) {
        clipboardSync.applyRemote(text)
        clipboardClient?.pushCurrent()
    }

    /**
     * Кладёт [text] только в локальный системный буфер, без рассылки удалённой
     * стороне. Используется кнопкой «Копировать» на экране сопряжения.
     */
    fun copyToLocalClipboard(text: String) {
        clipboardSync.copyLocal(text)
    }

    /** Запрашивает содержимое удалённого буфера. */
    fun pullRemoteClipboard() = clipboardClient?.pullRemote()

    /** Закрепляет/снимает запись истории буфера. */
    fun toggleClipboardPinned(timestamp: Long) = clipboardSync.togglePinned(timestamp)

    /** Очищает историю буфера (закреплённые сохраняются). */
    fun clearClipboardHistory() = clipboardSync.clearHistory()

    /** Отзывает доверие к устройству. */
    fun revokeTrust(deviceId: String) {
        scope.launch {
            trustStore.remove(deviceId)
            refreshTrusted()
            if (_connection.value?.remote?.deviceId == deviceId) closeLink(error = null)
        }
    }

    private suspend fun refreshTrusted() {
        _trustedDevices.value = trustStore.all()
    }

    /** Генерирует ключ доступа для локальной проверки (утилита для UI/тестов). */
    fun newAccessKey(): String = SecureTokens.accessKey()

    /** Короткий случайный идентификатор (для технических id в UI). */
    fun newId(): String = SecureTokens.token(4)

    private companion object {
        /** Как часто проверяется heartbeat активного соединения. */
        const val HEARTBEAT_CHECK_MS = 5_000L

        /** Сколько тишины (три интервала heartbeat) считается потерей связи. */
        const val HEARTBEAT_TIMEOUT_MS = 45_000L
    }
}
