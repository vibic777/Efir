package ru.efir.core.ui.i18n

/**
 * Языки интерфейса. Новый язык — это один файл с наследником [Strings]
 * (см. `StringsEn.kt`) и одна строка здесь; непереведённое остаётся русским.
 */
enum class Lang(val code: String, val title: String, val strings: Strings) {
    RU("ru", "Русский", RuStrings),
    EN("en", "English", EnStrings),
    ;

    companion object {
        /** Язык по коду (`ru`, `en`); русскоязычным системам — русский, остальным — английский. */
        fun byCode(code: String): Lang =
            entries.firstOrNull { it.code == code.lowercase() }
                ?: if (code.lowercase() in setOf("be", "uk", "kk")) RU else EN
    }
}

/** Русские тексты — основа: остальные языки переопределяют их. */
object RuStrings : Strings()

/**
 * Все тексты интерфейса. Значения здесь русские; язык-наследник переопределяет
 * свойства и функции. Тексты подсказок значков и разделов справки лежат здесь
 * же, поэтому подсказка, справка и поиск по справке всегда на одном языке.
 */
@Suppress("TooManyFunctions")
open class Strings {
    // --- Общее ---
    open val appName = "Эфир"
    open val cancel = "Отмена"
    open val close = "Закрыть"
    open val save = "Сохранить"
    open val delete = "Удалить"
    open val edit = "Изменить"
    open val create = "Создать"
    open val refresh = "Обновить"
    open val send = "Отправить"
    open val unknown = "Неизвестно"
    open val gotIt = "Понятно"
    open val later = "Позже"
    open val openSettings = "Открыть настройки"
    open val sidePc = "ПК"
    open val sidePhone = "Телефон"
    open val sideDevice = "Устройство"
    open val sideThis = "Здесь"
    open val sideOther = "Там"
    open val byteUnits = listOf("Б", "КБ", "МБ", "ГБ", "ТБ")
    open val perSecond = "/с"
    open fun andMore(count: Int) = "…и ещё $count"

    // --- Навигация ---
    open val navDevices = "Устройства"
    open val navScreen = "Экран"
    open val navFiles = "Файлы"
    open val navSync = "Синхронизация"
    open val navClipboard = "Буфер"
    open val navMedia = "Камера и звук"
    open val navPhone = "Телефон"
    open val help = "Справка"
    open val settings = "Настройки"
    open val noConnection = "Нет соединения"
    open val needConnectionTitle = "Нужно соединение"
    open fun needConnectionText(section: String) =
        "Раздел «$section» работает с другим устройством. Подключитесь к нему на экране «Устройства»."
    open val toDevices = "К устройствам"

    // --- Устройства ---
    open val connection = "Соединение"
    open val connected = "Подключено"
    open val connect = "Подключиться"
    open val disconnect = "Отключить"
    open val linkIncoming = "подключилось к этому устройству"
    open val linkOutgoing = "подключение отсюда"
    open val connectingNow = "Подключение…"
    open val noConnectionHint =
        "Соединения нет. Выберите устройство из списка ниже или сопрягите новое по ключу доступа."
    open val hideMessage = "Скрыть сообщение"
    open fun permissionsMissing(count: Int) = "Не выдано разрешений: $count"
    open val permissionsMissingHint =
        "Без них часть функций телефона недоступна другому устройству: файлы, управление экраном, уведомления."
    open fun nearby(count: Int) = "Устройства рядом ($count)"
    open val searching = "Поиск устройств в локальной сети…"
    open val searchingHint =
        "Эфир должен быть запущен на обоих устройствах, а они — находиться в одной сети Wi-Fi или LAN. " +
            "Сеть сменилась — нажмите «Проверить сеть»."
    open val online = "в сети"
    open fun seenAgo(seconds: Long) =
        if (seconds < 60) "нет ответа $seconds с" else "нет ответа ${seconds / 60} мин"
    open val paired = "сопряжено"
    open val notPaired = "не сопряжено"
    open val pair = "Сопрячь"
    open val pairing = "Сопряжение"
    open val pairingHint =
        "Новое устройство подключается по одноразовому ключу: на одном устройстве нажмите «Ждать с ключом», " +
            "на другом — «Найти устройство» и введите ключ. Дальше устройства подключаются друг к другу без ключа."
    open val showKey = "Ждать с ключом"
    open val enterKey = "Ввести ключ"
    open val keyTitle = "Ключ доступа — введите его на другом устройстве"
    open val showQr = "Показать QR-код"
    open val hideQr = "Скрыть QR-код"
    open val copyPairingData = "Копировать данные сопряжения"
    open val cancelPairing = "Отменить сопряжение"
    open fun keyValidFor(seconds: Long) = "Действует ещё ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}."
    open fun keyAddress(address: String) = "Адрес этого устройства: $address"
    open val keyExpired = "Срок действия ключа истёк"
    open val newKey = "Новый ключ"
    open val qrHint = "В QR-коде — те же данные сопряжения, что копирует кнопка: адрес, порт и одноразовый токен."
    open val thisDevice = "Это устройство"
    open val listen = "Ждать подключения"
    open val listenHint = "Устройство отвечает на поиск и принимает подключения. Само в сеть ничего не рассылает"
    open fun trustedTitle(count: Int) = "Доверенные устройства ($count)"
    open val trustedEmpty = "Сопряжённых устройств пока нет"
    open fun pairedAt(date: String) = "сопряжено $date"
    open val revoke = "Отозвать доверие"
    open val revokeTitle = "Отозвать доверие?"
    open fun revokeText(name: String) =
        "«$name» больше не сможет подключаться без нового сопряжения. Текущее соединение с ним будет разорвано."
    open fun pairWith(name: String) = "Сопряжение с «$name»"
    open val pairDialogHint = "Новое устройство: нажмите на нём «Ждать с ключом» — оно появится здесь с пометкой «ждёт сопряжения». Уже сопряжённое подключается нажатием, без ключа."
    open val addressOrData = "Адрес устройства или данные сопряжения"
    open val paste = "Вставить из буфера"
    open val accessKey = "Ключ доступа (12 символов)"
    open fun addressExample(port: Int) = "192.168.1.5  или  192.168.1.5:$port"
    open val keyLengthError = "Ключ доступа состоит из 12 символов"
    open val pairingDataError = "Не удалось разобрать данные сопряжения"
    open val addressError = "Укажите адрес устройства"
    open val pairAndConnect = "Сопрячь"

    // --- Экран ---
    open fun screenOf(name: String) = "Экран: $name"
    open val startScreen = "Показать экран"
    open val stopScreen = "Остановить показ экрана"
    open val keyBack = "Назад"
    open val keyHome = "Домой"
    open val keyRecents = "Недавние приложения"
    open val typeText = "Ввести текст на устройстве"
    open val sendText = "Отправить текст в поле ввода"
    open fun screenStats(width: Int, height: Int, frames: Long, dropped: Long) =
        "$width×$height · кадров $frames · пропущено $dropped"
    open val waitingFrame = "Ожидание первого кадра…"
    open val screenHint =
        "Экран другого устройства появится здесь. По картинке можно нажимать, проводить и сводить два пальца."
    open val remoteScreen = "Экран удалённого устройства"
    open val ownScreen = "Экран этого устройства"
    open fun ownScreenShared(width: Int, height: Int, frames: Long) =
        "Сейчас показывается другому устройству: $width×$height · кадров $frames"
    open val ownScreenIdle = "Сейчас никому не показывается"
    open val captureUnavailable = "Захват экрана на этом устройстве недоступен или не разрешён"
    open val inputUnavailable = "Управление недоступно"
    open val inputUnavailableHint =
        "На телефоне откройте «Специальные возможности» и включите службу «Эфир — управление вводом»."

    // --- Камера и звук ---
    open fun cameraOf(name: String) = "Камера: $name"
    open val cameraBack = "Основная камера"
    open val cameraFront = "Фронтальная камера"
    open val startCamera = "Включить камеру"
    open val stopCamera = "Выключить камеру"
    open fun cameraStats(width: Int, height: Int, frames: Long) = "$width×$height · кадров $frames"
    open fun cameraIdle(which: String) = "Камера выключена. Выбрана: ${which.lowercase()}."
    open fun soundOf(name: String) = "Звук: $name"
    open val audioMic = "Микрофон"
    open val audioSystem = "Системный звук"
    open val startAudio = "Включить звук"
    open val stopAudio = "Выключить звук"
    open fun audioStats(rate: Int, channels: Int, chunks: Long) = "$rate Гц · каналов $channels · блоков $chunks"
    open fun audioIdle(which: String) = "Звук не передаётся. Источник: ${which.lowercase()}."
    open val mediaHint = "Камера и микрофон работают, пока на телефоне выданы разрешения и приложение Эфир запущено."

    // --- Файлы ---
    open fun filesOf(name: String) = "Файлы: $name"
    open val goUp = "На уровень выше"
    open val newFolder = "Новая папка"
    open val upload = "Отправить файлы на устройство"
    open val fileRoots = "Разделы устройства"
    open val noRoots = "Устройство не показало ни одного раздела. На телефоне проверьте разрешение на доступ к файлам."
    open val emptyFolder = "Папка пуста"
    open val moreItems = "Показаны не все элементы папки"
    open fun downloadsTo(dir: String) = "Скачанное сохраняется в $dir"
    open val openDownloads = "Открыть папку загрузок"
    open val folderName = "Имя папки"
    open val newFolderDefault = "Новая папка"
    open val rename = "Переименовать"
    open val newName = "Новое имя"
    open val deleteTitle = "Удалить?"
    open fun deleteFileText(name: String) = "Файл «$name» будет удалён на другом устройстве без возможности восстановления."
    open fun deleteFolderText(name: String) = "Папка «$name» со всем содержимым будет удалена на другом устройстве без возможности восстановления."
    open val download = "Скачать"
    open val downloadFolder = "Скачать папку"
    open val previewFailed = "Не удалось загрузить файл для просмотра"
    open val previewImageFailed = "Не удалось показать изображение"
    open fun previewTruncated(size: String) = "Показаны первые $size"
    open val previewUnavailable = "Для файлов этого типа просмотра нет — файл можно скачать."
    open val kindImage = "изображение"
    open val kindVideo = "видео"
    open val kindAudio = "аудио"
    open val kindText = "текст"
    open val kindFolder = "папка"
    open val kindFile = "файл"
    open fun pendingTransfers(count: Int) = "Незавершённые передачи ($count)"
    open val pendingHint = "Остались с прошлого раза — можно продолжить с того же места."
    open val resumeAll = "Продолжить все"
    open val discardAll = "Убрать все"
    open val resume = "Продолжить"
    open val discard = "Убрать"
    open fun inFolder(label: String) = "папка $label"
    open fun transfersTitle(count: Int) = "Передачи ($count)"
    open val cancelTransfer = "Отменить передачу"
    open fun groupProgress(done: Int, total: Int) = "файлов $done из $total"
    open val statusQueued = "в очереди"
    open val statusRunning = "передаётся"
    open val statusDone = "готово"
    open fun statusFailed(error: String) = "ошибка: $error"
    open val statusCancelled = "отменено"
    open fun eta(seconds: Long): String {
        val t = seconds.coerceAtLeast(0)
        return "осталось " + when {
            t < 60 -> "$t с"
            t < 3600 -> "${t / 60} мин ${t % 60} с"
            else -> "${t / 3600} ч ${(t % 3600) / 60} мин"
        }
    }

    // --- Синхронизация ---
    open val syncRules = "Правила синхронизации"
    open val newRule = "Новое правило"
    open val newRuleName = "Новое правило"
    open val noRules = "Правило связывает папку на этом устройстве с папкой на другом и держит их одинаковыми."
    open val conflictsLabel = "при конфликте"
    open val syncNow = "Синхронизировать сейчас"
    open val previewPlan = "Показать, что изменится"
    open val journal = "Журнал"
    open val versionHistory = "История версий"
    open fun syncProgress(done: Int, total: Int) = "Выполнено $done из $total"
    open val planFailed = "Не удалось построить план синхронизации"
    open fun planTitle(rule: String) = "Что изменится · $rule"
    open fun planSummary(actions: Int, conflicts: Int) = "Действий: $actions, конфликтов: $conflicts"
    open fun journalTitle(rule: String) = "Журнал · $rule"
    open val journalEmpty = "Записей пока нет"
    open fun versionsTitle(rule: String) = "История версий · $rule"
    open val clearVersions = "Очистить историю версий"
    open fun versionsOnlyFile(file: String) = "Только файл $file"
    open val showAllFiles = "Все файлы"
    open val versionsEmpty = "Сохранённых версий нет"
    open val onlyThisFile = "Версии только этого файла"
    open val restoreVersion = "Вернуть эту версию"
    open val deleteRuleTitle = "Удалить правило?"
    open fun deleteRuleText(name: String) = "Правило «$name» будет удалено. Файлы в обеих папках останутся."
    open fun conflictsTitle(count: Int) = "Конфликты ($count)"
    open val conflictsHint = "Файл изменён на обеих сторонах. Выберите, какую версию оставить."
    open fun keep(side: String) = "Оставить: $side"
    open val keepBoth = "Оставить оба"
    open val keepNewest = "Оставить новейшие"
    open val skip = "Пропустить"
    open val forAllConflicts = "Для всех конфликтов сразу:"
    open val ruleTitle = "Правило синхронизации"
    open val ruleName = "Название"
    open fun folderOn(side: String) = "Папка · $side"
    open val presets = "Готовые варианты"
    open val direction = "Направление"
    open val trigger = "Когда запускать"
    open val intervalMinutes = "Интервал, мин"
    open val onConflict = "Если файл изменён на обеих сторонах"
    open val propagateDeletes = "Переносить удаления"
    open val propagateDeletesHint = "Файл, удалённый на одной стороне, удаляется и на другой"
    open val backupVersions = "Хранить прежние версии"
    open val backupVersionsHint = "Перед перезаписью файл сохраняется в историю версий"
    open val includeMasks = "Только эти файлы (маски через запятую)"
    open val excludeMasks = "Кроме этих файлов (маски через запятую)"
    open fun everyMinutes(minutes: Int) = "каждые $minutes мин"
    open val triggerManual = "вручную"
    open val triggerScheduled = "по расписанию"
    open val triggerOnConnect = "при подключении"
    open val triggerOnChange = "при изменениях"
    open val strategyMerge = "объединять"
    open val strategyOverwrite = "перезаписывать"
    open val strategyAppend = "только добавлять"
    open val strategyNewest = "побеждает новейший"
    open val strategyRename = "переименовывать"
    open val strategyAsk = "спрашивать"
    open fun wins(side: String) = "побеждает: $side"
    open val presetSafeMerge = "Безопасное объединение"
    open fun mirror(from: String, to: String) = "Зеркало $from → $to"
    open val presetAddOnly = "Только добавление"
    open val presetAsk = "Спрашивать о конфликтах"
    open val actionAdd = "добавить"
    open val actionUpdate = "обновить"
    open val actionDelete = "удалить"
    open val actionRename = "переименовать"
    open val actionSkip = "пропустить"

    // --- Буфер и уведомления ---
    open val clipboard = "Буфер обмена"
    open fun pullClipboard(name: String) = "Забрать буфер с «$name»"
    open val pullClipboardShort = "Забрать буфер с другого устройства"
    open val clearHistory = "Очистить историю (закреплённое останется)"
    open val clipboardHint = "Кнопка со стрелкой забирает буфер обмена другого устройства: текст попадает в буфер этого устройства и в историю. При подключении буфер забирается сам."
    open val historyEmpty = "История пуста"
    open fun fromDevice(name: String) = "с «$name»"
    open val fromHere = "с этого устройства"
    open val copyAgain = "Скопировать снова"
    open val pin = "Закрепить"
    open val unpin = "Открепить"
    open val notifications = "Уведомления"
    open fun notificationsTitle(count: Int) = "Уведомления телефона ($count)"
    open val notificationsPhoneOnly = "Уведомления показываются, когда другое устройство — телефон."
    open val mirrorNotifications = "Показывать уведомления телефона"
    open val hideOngoing = "Скрывать постоянные"
    open val hideOngoingHint = "Музыка, навигация, загрузки и другие несмахиваемые уведомления"
    open val noNotifications = "Уведомлений нет"
    open val reply = "Ответить"
    open val replyText = "Ответ"
    open val dismissNotification = "Закрыть уведомление на телефоне"
    open val notificationApps = "Приложения"
    open val notificationAppsHint = "Выключите приложение, чтобы его уведомления сюда не приходили."

    // --- Телефон ---
    open val unknownNumber = "Неизвестный номер"
    open fun incomingCall(who: String) = "Входящий звонок: $who"
    open fun outgoingCall(who: String) = "Исходящий звонок: $who"
    open fun callInProgress(who: String) = "Разговор: $who"
    open val answer = "Принять"
    open val reject = "Отклонить"
    open val endCall = "Завершить"
    open val phoneOnlyTitle = "Только для телефона"
    open val phoneOnlyText = "SMS и звонки доступны, когда другое устройство — телефон на Android."
    open val newMessage = "Новое сообщение"
    open val phoneNumber = "Номер"
    open val callNumber = "Позвонить"
    open val writeSms = "Написать SMS"
    open val smsText = "Текст SMS"
    open val simDefault = "SIM по умолчанию"
    open val sendSms = "Отправить SMS"
    open val smsSent = "Отправлено"
    open val smsDelivered = "Доставлено"
    open val smsFailed = "Не отправлено"
    open fun threads(count: Int) = "Диалоги ($count)"
    open val noThreads = "Диалогов нет. На телефоне проверьте разрешение на SMS."
    open fun threadNumber(id: Long) = "Диалог $id"
    open val recentMessages = "Последние сообщения"
    open val messagesOfThread = "Сообщения диалога"
    open val showAllMessages = "Показать сообщения всех диалогов"
    open val noMessages = "Сообщений нет"
    open val callLog = "Журнал звонков"
    open val callLogEmpty = "Журнал звонков не загружен"
    open val loadCallLog = "Загрузить"
    open val callIncoming = "Входящий"
    open val callOutgoing = "Исходящий"
    open val callMissed = "Пропущенный"
    open val callRejected = "Отклонённый"
    open fun callType(type: String) = when (type) {
        "INCOMING" -> callIncoming
        "OUTGOING" -> callOutgoing
        "MISSED" -> callMissed
        "REJECTED" -> callRejected
        else -> unknown
    }
    open fun duration(seconds: Long) = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

    // --- Настройки ---
    open val language = "Язык"
    open val theme = "Тема"
    open val themeSystem = "Как в системе"
    open val themeLight = "Светлая"
    open val themeDark = "Тёмная"
    open val permissions = "Разрешения"
    open val permissionsHint =
        "Android выдаёт их только вручную. Каждое нужно для своей функции — без него она не работает. " +
            "Любое можно отозвать — по одному или все сразу."
    open val grant = "Выдать"
    open val revokePermission = "Отозвать"
    open val revokeAll = "Отозвать все"
    open fun revokeTitle(name: String) = "Отозвать: $name?"
    open val revokeClosesAppText =
        "Android снимает эти разрешения только вместе с закрытием приложения. Эфир закроется, соединение прервётся. " +
            "При следующем запуске разрешения можно выдать снова."
    open val revokeAllTitle = "Отозвать все разрешения?"
    open val revokeAllText =
        "Показ экрана и управление вводом отключатся сразу. Откроются системные настройки приложения: " +
            "доступ к файлам, доступ к уведомлениям и работу в фоне Android позволяет отключить только там."
    open val revokeAllClosesText = " Камера, микрофон, SMS и звонки будут отозваны, и Эфир закроется."
    open fun permissionTitle(id: String) = when (id) {
        "files" -> "Доступ ко всем файлам"
        "camera" -> "Камера"
        "microphone" -> "Микрофон"
        "sms" -> "SMS"
        "phone" -> "Звонки и журнал вызовов"
        "notify" -> "Уведомления приложения"
        "restricted" -> "Ограниченные настройки"
        "screen" -> "Показ экрана"
        "accessibility" -> "Управление вводом"
        "notifications" -> "Доступ к уведомлениям телефона"
        "battery" -> "Работа в фоне"
        else -> id
    }
    open fun permissionHint(id: String) = when (id) {
        "files" -> "Чтобы другое устройство видело память телефона и могло передавать файлы"
        "camera" -> "Чтобы показывать картинку с камеры телефона на другом устройстве"
        "microphone" -> "Чтобы передавать звук с микрофона"
        "sms" -> "Чтение, приём и отправка SMS с другого устройства"
        "phone" -> "Журнал вызовов, набор номера, ответ на звонок и его сброс"
        "notify" -> "Значок в шторке: телефон на связи, идёт показ экрана"
        "restricted" -> "Приложению, установленному из файла, Android блокирует службу управления, доступ к уведомлениям, SMS и звонки. " +
            "Откройте сведения о приложении, меню ⋮ вверху справа → «Разрешить ограниченные настройки»"
        "screen" -> "Android спрашивает его заново при каждом запуске приложения и перед каждым показом"
        "accessibility" -> "Служба «Эфир — управление вводом»: нажатия и жесты с другого устройства"
        "notifications" -> "Чтобы уведомления телефона появлялись на другом устройстве"
        "battery" -> "Чтобы система не останавливала соединение при выключенном экране"
        else -> ""
    }
    open val defaultSms = "Приложение SMS по умолчанию"
    open val defaultSmsOk = "Отправка и отчёты о доставке работают полностью"
    open val defaultSmsNo = "Необязательно. Без этого отчёты о доставке и запись в «Отправленные» могут не работать"
    open val about = "О приложении"
    open val aboutText = "Android и Windows в одной локальной сети: без интернета, облака и учётных записей."
    open val downloadsFolder = "Папка загрузок"
    open val licenses = "Шрифт Roboto — SIL OFL 1.1; значки Material Icons — Apache 2.0 (папка docs/licenses)."

    // --- Вкладки ---
    open val tabConnection = "Подключение"
    open val tabNearby = "Рядом"
    open val tabTrusted = "Доверенные"
    open val tabGeneral = "Общие"
    open val tabSms = "SMS"
    open val tabTransfers = "Передачи"
    open val transfersEmpty = "Передач пока нет: скачайте или отправьте файл на вкладке «Файлы»"

    // --- Новое подключение и сеть ---
    open val findDevice = "Найти устройство"
    open val recheckNetwork = "Проверить сеть"
    open val noLocalNetwork = "Нет локальной сети: подключите Wi-Fi или кабель и нажмите «Проверить сеть»"
    open val addressesNow = "Адреса этого устройства в сети сейчас:"
    open val foundWaiting = "ждёт сопряжения"
    open val foundNotWaiting = "не ждёт: нажмите на нём «Ждать с ключом»"
    open val manualHint = "Устройства нет в списке? Введите его адрес — он показан на нём под ключом и на вкладке «Это устройство»."
    open val useAddress = "Далее"
    open fun enterKeyFor(name: String) = "Введите ключ доступа, который показывает «$name»."
    open fun lastAddress(address: String) = "прошлый адрес $address"

    // --- Подсказки к кнопкам и полям ---
    open val tipDisconnect = "Разорвать соединение с этим устройством"
    open val tipOpenPermissions = "Открыть разрешения Android в настройках"
    open val tipWaitKey = "Показать одноразовый ключ и ждать, пока его введут на другом устройстве"
    open val tipFindDevice = "Найти в сети устройство, которое показывает ключ, и ввести этот ключ"
    open val tipConnect = "Подключиться к сопряжённому устройству — ключ не нужен"
    open val tipPair = "Ввести ключ доступа, показанный на этом устройстве"
    open val tipRecheck = "Перечитать сетевые адреса и искать устройства заново — если сеть сменилась"
    open val tipAddressField = "IP-адрес другого устройства, например 192.168.1.5, или скопированные данные сопряжения"
    open val tipUseAddress = "Перейти к вводу ключа для этого адреса"
    open val tipKeyField = "12 символов с экрана другого устройства; дефисы ставятся сами"
    open val tipPairAndConnect = "Проверить ключ, запомнить устройство и подключиться"
    open val tipLanguage = "Язык интерфейса, справки и подсказок"
    open val tipThemeSystem = "Светлая или тёмная — как в системе"
    open val tipTipsOff = "Не показывать всплывающие подсказки"
    open fun tipTipsSeconds(seconds: Int) = "Подсказка остаётся на экране $seconds с"
    open val tipGrant = "Открыть системное окно, где выдаётся это разрешение"
    open val tipRevoke = "Снять это разрешение"
    open val tipRevokeCloses = "Снять это разрешение; для этого Эфир закроется"
    open val tipRevokeAll = "Снять все выданные разрешения сразу"
    open val tipHelpSearch = "Слово или его часть, от двух букв: найденное показывается в окружении, нажатие открывает раздел"
    open fun navTip(id: String) = when (id) {
        "DEVICES" -> "Устройства: соединение, сопряжение, доверенные"
        "SCREEN" -> "Экран другого устройства и управление им"
        "FILES" -> "Файлы другого устройства и передачи"
        "SYNC" -> "Синхронизация папок по правилам"
        "CLIPBOARD" -> "Общий буфер обмена и уведомления телефона"
        "MEDIA" -> "Камера и звук другого устройства"
        "PHONE" -> "SMS и звонки телефона"
        else -> ""
    }

    // --- Подсказки остальных экранов ---
    open val tipDownload = "Скачать файл в папку загрузок этого устройства"
    open val tipFolderButton = "Выбрать папку в окне: диски, подпапки, новая папка"
    open val tipResumeAll = "Продолжить прерванные передачи с того места, где они остановились"
    open val tipDiscardAll = "Забыть прерванные передачи; недокачанные части будут удалены"
    open val tipNewRule = "Создать правило: какие две папки и как держать одинаковыми"
    open val tipRuleEnabled = "Включить или выключить правило: выключенное не запускается"
    open val tipShowAllFiles = "Показать сохранённые версии всех файлов правила"
    open fun tipKeepSide(side: String) = "Оставить версию, которая лежит на стороне «$side»; другая будет заменена"
    open val tipKeepBoth = "Оставить обе версии: вторая получит другое имя"
    open val tipKeepNewest = "В каждом конфликте оставить ту версию, что изменена позже"
    open val tipSkipConflict = "Не трогать сейчас: конфликт останется до следующей синхронизации"
    open val tipSaveRule = "Сохранить правило; запускается оно кнопкой или по своему условию"
    open val tipRuleName = "Название правила — как оно будет показано в списке"
    open val tipFolderPath = "Полный путь к папке, например D:/Photos или /sdcard/DCIM; кнопка справа выбирает папку в окне"
    open val tipInterval = "Раз в сколько минут запускать правило, пока устройства соединены"
    open val tipMasks = "Через запятую; * заменяет любые символы. Пусто — без ограничений"
    open val tipReply = "Ответ уйдёт в приложение, которое показало это уведомление"
    open val tipAppNotifications = "Показывать на другом устройстве уведомления этого приложения"
    open val tipAnswer = "Принять звонок на телефоне"
    open val tipReject = "Отклонить входящий звонок"
    open val tipEndCall = "Завершить разговор"
    open val tipPhoneNumber = "Номер получателя SMS или номер, который нужно набрать"
    open val tipSmsText = "Текст сообщения; длинное телефон отправит несколькими SMS"
    open val tipSendSms = "Отправить SMS с телефона на этот номер"
    open val tipLoadCallLog = "Прочитать журнал вызовов с телефона"
    open val tipTypeText = "Текст попадёт туда, где на устройстве стоит курсор; компьютер без разрешённого управления покажет его окном"
    open val tipStartScreen = "Попросить другое устройство показать его экран"

    // --- Журнал ---
    open val tabLog = "Журнал"
    open val logHint =
        "Что происходило в приложении: запуск, сеть, поиск устройств, сопряжение, соединение, ошибки. " +
            "Ключей, токенов и содержимого файлов, буфера, SMS и уведомлений в журнале нет. " +
            "Если что-то не работает, пришлите его: «Копировать» или «Сохранить / отправить»."
    open val logRefresh = "Обновить журнал"
    open val logCopy = "Копировать журнал целиком"
    open val logExport = "Сохранить журнал в файл или отправить"
    open val logClear = "Очистить журнал"
    open val logEmpty = "Журнал пуст"
    open val logCopied = "Журнал скопирован в буфер обмена"
    open fun logSaved(path: String) = "Журнал сохранён: $path"

    // --- Ограниченные настройки Android ---
    open val restrictedGuide =
        "Если приложение установлено из файла, Android 13 и новее не даёт включить эту настройку сразу " +
            "и показывает окно «Ограниченные настройки». Тогда:\n\n" +
            "1. Нажмите «Открыть настройку», выберите «Эфир» и попробуйте включить. Появится окно «Ограниченные настройки» — нажмите «ОК». " +
            "Без этой попытки нужный пункт меню не появляется.\n\n" +
            "2. Вернитесь сюда и нажмите «Сведения о приложении». Вверху справа откройте меню ⋮ (три точки) → " +
            "«Разрешить ограниченные настройки» и подтвердите отпечатком или PIN-кодом.\n\n" +
            "3. Снова нажмите «Открыть настройку» и включите «Эфир» — теперь получится.\n\n" +
            "Если окно «Ограниченные настройки» не появилось, настройка включится с первого раза."
    open val openAppInfo = "Сведения о приложении"
    open val openSetting = "Открыть настройку"
    open val tipAppInfo = "Страница приложения в настройках Android: в её меню ⋮ есть «Разрешить ограниченные настройки»"

    // --- Подсказки и работа приложения (настройки) ---
    open val tipsTitle = "Подсказки"
    open val tipsHint =
        "Подсказка появляется, если задержать курсор или палец на кнопке, поле или переключателе; нажатие на неё открывает справку. " +
            "Здесь — сколько секунд она остаётся на экране."
    open val tipsOff = "Выключены"
    open fun tipSeconds(seconds: Int) = "$seconds с"
    open val appBehavior = "Работа приложения"
    open val closeToTray = "Сворачивать в трей при закрытии"
    open val closeToTrayHint = "Крестик окна прячет Эфир в область уведомлений, устройство остаётся на связи. Выход — в меню значка в трее"
    open val autoStart = "Запускать вместе с системой"
    open val autoStartHint = "Эфир стартует при входе в систему свёрнутым в трей и ждёт подключений"
    open val singleInstance = "Только один экземпляр"
    open val singleInstanceHint = "Повторный запуск показывает уже открытое окно. Меняется со следующего запуска"
    open val keepInBackground = "Оставаться на связи в фоне"
    open val keepInBackgroundHint =
        "Телефон принимает подключения, когда Эфир свёрнут; в шторке висит уведомление. Процессор при этом не удерживается: " +
            "без соединения телефон спит как обычно"

    // --- Трей ---
    open val trayOpen = "Открыть Эфир"
    open val trayExit = "Выход"
    open val trayNotice = "Эфир продолжает работать в трее. Выход — в меню значка"
    open val trayIdle = "Эфир — нет соединения"
    open fun trayConnected(name: String) = "Эфир — подключено: $name"
    open fun trayConnect(name: String) = "Подключить: $name"
    open val trayDisconnect = "Отключить"
    open val traySyncAll = "Синхронизировать сейчас"
    open val trayFiles = "Файлы устройства"
    open val trayScreen = "Экран устройства"
    open val trayDevices = "Устройства"
    open val trayDownloads = "Папка загрузок"

    // --- Общие кнопки ---
    open val back = "Назад"
    open val chooseFolder = "Выбрать папку"
    open val changeFolder = "Изменить папку загрузок"
    open fun versionLine(version: String) = "$appName $version"

    // --- Показ экрана ---
    /** Причина, по которой показ экрана, камера или звук не начались или закончились: по сети приходит код. */
    open fun streamError(code: String) = when (code) {
        "screen_permission_needed" ->
            "На телефоне не разрешён показ экрана. Откройте на нём Эфир и нажмите «Начать» в окне Android " +
                "(если Эфир свёрнут — нажмите уведомление «Показать экран?»), затем повторите."
        "screen_refused" -> "На другом устройстве показ экрана отклонили."
        "screen_disabled" -> "На компьютере показ экрана выключен: «Настройки» → «Показ экрана» → «Показывать экран этого компьютера»."
        "screen_unavailable" -> "Другое устройство не умеет показывать экран."
        "media_unavailable" -> "На другом устройстве это недоступно или не разрешено."
        "screen_stopped" -> "Показ экрана остановлен на другом устройстве."
        "screen_no_answer" -> "Другое устройство не ответило. Проверьте, что Эфир на нём открыт."
        "screen_failed" -> "Не удалось начать показ экрана. Подробности — в журнале на другом устройстве («Настройки» → «Журнал»)."
        else -> code
    }
    /** То же — на самом показывающем устройстве, в карточке «Экран этого устройства». */
    open fun ownShareError(code: String) = when (code) {
        "screen_permission_needed" -> "Вопрос Android о показе экрана остался без ответа — показ не начался."
        "screen_refused" -> "Показ экрана отклонён."
        "screen_disabled" -> "Показ остановлен: он выключен в настройках."
        "screen_unavailable" -> "На этом устройстве нет экрана для показа."
        else -> "Не удалось показать экран. Подробности — в «Настройках» → «Журнал»."
    }
    open val fullScreen = "На весь экран"
    open val exitFullScreen = "Выйти из полного экрана"
    open val rotateScreen = "Повернуть картинку на четверть оборота"
    open val ok = "ОК"
    open val copyText = "Копировать"
    open val tipCopyReceived = "Положить этот текст в буфер обмена и закрыть окно"
    open fun receivedTextTitle(name: String) = "Текст с устройства «$name»"
    open val foundPaired = "сопряжено — нажмите, чтобы подключиться"
    open val sharingNow = "Экран этого устройства показывается — нажмите, чтобы остановить"
    open val stopSharing = "Остановить показ"
    open val tipStopSharing = "Сразу прекратить показ экрана этого устройства: на другом устройстве картинка исчезнет"
    open val allowScreenShare = "Разрешить показ экрана"
    open val tipAllowScreenShare = "Задать вопрос Android о показе экрана заранее, чтобы показ начался без ожидания"
    open val screenShareReady = "Показ экрана разрешён: по просьбе другого устройства он начнётся сразу."
    open val ownScreenPhoneHint =
        "Когда другое устройство попросит показать экран, телефон спросит разрешение: окном Android, а если Эфир свёрнут — уведомлением «Показать экран?»."
    open val ownScreenPcOn = "Сопряжённое устройство может попросить показ экрана. О начале показа придёт уведомление."
    open val ownScreenPcOff = "Показ экрана этого компьютера выключен в настройках."
    open val openScreenSettings = "Настройки показа экрана"
    open val inputUnavailableHintPc =
        "На компьютере откройте «Настройки» → «Показ экрана» и включите «Разрешать управление этим компьютером»."
    open fun trayScreenShown(name: String) = "Экран этого компьютера показывается: $name"

    // --- Меню настроек ---
    open val settingsSearch = "Поиск по настройкам"
    open val tipSettingsSearch = "Слово из названия или описания настройки: «папка», «тема», «трей», «версия»"
    open val settingsNothingFound = "Такой настройки нет"
    open val setAppearance = "Внешний вид"
    open val setFolders = "Папки"
    open val setScreen = "Показ экрана"
    open val setNetwork = "Сеть"
    open val setNetworkHint = "Порт соединения: автоматически или вручную"
    open val portAuto = "Автоматически"
    open val portManual = "Вручную"
    open val tipPortAuto = "Стандартный порт, а если он занят — ближайший свободный"
    open val tipPortManual = "Задать порт самому; Эфир покажет, свободен ли он"
    open fun portAutoHint(port: Int) = "Эфир берёт стандартный порт $port, а если он занят другой программой — ближайший свободный за ним."
    open fun portAutoMoved(port: Int) = "Стандартный порт $port при запуске был занят, поэтому выбран следующий свободный."
    open fun portFree(port: Int) = "Порт $port свободен."
    open fun portBusy(port: Int) = "Порт $port занят другой программой. Выберите другой."
    open val portOurs = "На этом порту Эфир работает сейчас."
    open val listenPortTitle = "Порт соединения"
    open val listenPortHint =
        "На этом порту устройство ждёт подключения. По умолчанию он выбирается сам; вручную его задают, если нужен определённый порт — например, открытый в сети. " +
            "На другом устройстве ничего менять не надо: порт оно узнаёт из поиска и данных сопряжения, а при вводе адреса вручную его пишут через двоеточие — 192.168.1.5:порт."
    open val listenPortError = "Порт — число от 1024 до 65535, кроме 47813 (он занят поиском устройств)"
    open fun listenPortNow(port: Int) = "Сейчас Эфир ждёт подключения на порту $port."
    open fun listenPortRestart(port: Int) = "Порт $port начнёт действовать после перезапуска Эфира. Брандмауэр может спросить разрешение для нового порта."
    open val tipListenPort = "Число от 1024 до 65535; стандартный порт — 47812"
    open val tipSavePort = "Запомнить порт; он начнёт действовать после перезапуска Эфира"
    open val tipResetPort = "Вернуть стандартный порт 47812"
    open val setGeneralHint = "Язык, работа приложения"
    open val setAppearanceHint = "Тема, время показа подсказок"
    open val setFoldersHint = "Папка загрузок, папка для правил синхронизации, папки для другого устройства"
    open val setScreenHint = "Показ экрана этого устройства и управление им"
    open val setPermissionsHint = "Что разрешено Эфиру на телефоне"
    open val setLogHint = "Что происходило в приложении; отправить журнал"
    open val setAboutHint = "Версия, это устройство, справка, лицензии"
    open val downloadsFolderHint = "Сюда сохраняется скачанное с другого устройства. Годится любая папка на любом диске."
    open val syncFolder = "Папка для новых правил"
    open val syncFolderHint = "Эта папка подставляется в новое правило синхронизации. В самом правиле её можно изменить."
    open val sharedFolders = "Папки, доступные другому устройству"
    open val sharedFoldersHint =
        "Их сопряжённое устройство видит в «Файлах» и с ними может синхронизироваться. За их пределы оно не выходит."
    open val addSharedFolder = "Добавить папку"
    open val removeSharedFolder = "Закрыть папку для другого устройства"
    open val resetFolder = "По умолчанию"
    open val openFolder = "Открыть папку"
    open val folderAlways = "открыта всегда"
    open val folderIsDownloads = "папка загрузок"
    open val folderIsRule = "папка правила синхронизации"
    open val folderStandard = "стандартная"
    open val tipChooseFolder = "Открыть окно выбора папки: диски, подпапки, ввод пути вручную"
    open val tipResetFolder = "Вернуть стандартную папку"
    open val tipAddShared = "Открыть другому устройству ещё один диск или папку"
    open val showScreen = "Показывать экран этого компьютера"
    open val showScreenHint =
        "Сопряжённое устройство может попросить показ экрана. О начале показа приходит уведомление, остановить его можно в любой момент"
    open val allowControl = "Разрешать управление этим компьютером"
    open val allowControlHint =
        "Другое устройство сможет двигать мышь, нажимать кнопки и вставлять текст. Включайте, только если доверяете сопряжённому устройству"
    open val screenPhoneHint = "На телефоне показ экрана и управление разрешает Android: это настраивается на странице «Разрешения»."
    open val toPermissions = "Открыть разрешения"
    open val versionTitle = "Версия"
    open val sameVersionHint = "На телефоне и компьютере должна стоять одна и та же версия."
    open val openHelp = "Открыть справку"
    open val tipOpenHelp = "Справка: разделы по экранам и поиск по словам"

    // --- Окно выбора папки ---
    open val pickerRoots = "Диски и разделы"
    open val pickerPath = "Путь к папке"
    open val pickerGo = "Перейти по этому пути"
    open val pickerEmpty = "Подпапок нет"
    open val pickerChoose = "Выбрать эту папку"
    open val pickerFailed = "Не удалось открыть или создать папку"
    open val pickerRemoteHint = "Показаны папки, которые открыло другое устройство."
    open val tipPickerPath = "Полный путь, например D:/Photos; Enter или стрелка справа — перейти"
    open fun folderOnDevice(side: String) = "Папка · $side"

    // --- Справка ---
    open val helpGroupStart = "Начало"
    open val helpIconsTitle = "Значки раздела"
    open fun helpFound(count: Int) = "Найдено мест: $count. Нажмите на место, чтобы открыть его раздел"
    open val helpSearch = "Поиск по справке"
    open val clearSearch = "Очистить поиск"
    open val helpNothingFound = "Ничего не найдено. Попробуйте другое слово или его часть"
    open val helpFooter =
        "Подсказка есть у каждой кнопки, поля и переключателя: задержите на нём курсор или палец. " +
            "Нажатие на подсказку открывает её раздел справки."

    /** Тексты разделов справки: ключ — имя раздела (`HelpTopic`). */
    open val helpTexts: Map<String, ru.efir.core.ui.HelpText> get() = HELP_RU
}