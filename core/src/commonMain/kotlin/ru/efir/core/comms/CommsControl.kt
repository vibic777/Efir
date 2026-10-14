package ru.efir.core.comms

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.ClipboardChanged
import ru.efir.core.protocol.ClipboardData
import ru.efir.core.protocol.ClipboardGet
import ru.efir.core.protocol.ClipboardSet
import ru.efir.core.protocol.ErrorMessage
import ru.efir.core.protocol.Message
import ru.efir.core.protocol.NotificationActionResult
import ru.efir.core.protocol.NotificationDismiss
import ru.efir.core.protocol.NotificationPosted
import ru.efir.core.protocol.NotificationRemoved
import ru.efir.core.protocol.NotificationReply

/**
 * Принимающая сторона синхронизации буфера: реагирует на `clipboardSet`,
 * `clipboardGet` и отправляет обратно `clipboardChanged` при локальных правках.
 */
class ClipboardHost(
    private val session: EfirSession,
    private val sync: ClipboardSync,
    private val scope: CoroutineScope,
) {

    private var attached = false

    /** Регистрирует обработчики и подписку на локальные изменения буфера. */
    fun attach() {
        if (attached) return
        attached = true

        session.rpc.onRequest<ClipboardSet> { request ->
            sync.applyRemote(request.text)
            ClipboardData(text = sync.current())
        }
        session.rpc.onRequest<ClipboardGet> {
            ClipboardData(text = sync.current())
        }

        // Локальные изменения → событие удалённой стороне.
        scope.launch {
            sync.incoming.collect { text ->
                runCatching { session.rpc.sendEvent(ClipboardChanged(text)) }
            }
        }
        sync.start()
    }
}

/**
 * Инициирующая сторона синхронизации буфера: шлёт `clipboardSet`, запрашивает
 * `clipboardGet` и применяет входящие `clipboardChanged` к локальному буферу.
 *
 * @param mirrorLocalChanges рассылать ли локальные изменения самостоятельно.
 *   В симметричном соединении обе стороны поднимают и [ClipboardHost], и
 *   [ClipboardClient], поэтому зеркалом занимается только хост — иначе одно
 *   локальное изменение ушло бы удалённой стороне дважды.
 */
class ClipboardClient(
    private val session: EfirSession,
    private val sync: ClipboardSync,
    private val scope: CoroutineScope,
    private val mirrorLocalChanges: Boolean = true,
) {

    private var attached = false

    /** Регистрирует подписку на удалённые изменения и локальные отправки. */
    fun attach() {
        if (attached) return
        attached = true

        session.rpc.onEvent<ClipboardChanged> { event -> sync.applyRemote(event.text) }

        if (mirrorLocalChanges) {
            scope.launch {
                sync.incoming.collect { text ->
                    runCatching { session.rpc.call(ClipboardSet(text)) }
                }
            }
        }
        sync.start()
    }

    /** Отправляет текущее локальное значение буфера удалённой стороне. */
    fun pushCurrent() {
        val text = sync.current() ?: return
        scope.launch { runCatching { session.rpc.call(ClipboardSet(text)) } }
    }

    /** Запрашивает содержимое удалённого буфера и применяет его. */
    fun pullRemote() {
        scope.launch {
            runCatching { session.rpc.call(ClipboardGet) }
                .onSuccess { response ->
                    if (response is ClipboardData) response.text?.let { sync.applyRemote(it) }
                }
        }
    }
}

/**
 * Клиент уведомлений Android: собирает входящие `notificationPosted`/
 * `notificationRemoved` в список для отображения на ПК.
 *
 * Фильтрация выполняется как на стороне-источнике ([NotificationsHost] — чтобы не
 * гнать лишний трафик), так и здесь (вторичная защита, если фильтр изменился после
 * отправки). Известные приложения накапливаются в [knownPackages] для UI-настроек.
 */
class NotificationsClient(
    private val session: EfirSession,
    private val scope: CoroutineScope,
    private var filter: NotificationFilter = NotificationFilter(),
) {

    private val _notifications = MutableStateFlow<List<NotificationPosted>>(emptyList())

    /** Активные уведомления (новые — в начале). */
    val notifications: StateFlow<List<NotificationPosted>> = _notifications.asStateFlow()

    private val _knownPackages = MutableStateFlow<Set<String>>(emptySet())

    /** Пакеты приложений, уведомления которых приходили (для настроек фильтра). */
    val knownPackages: StateFlow<Set<String>> = _knownPackages.asStateFlow()

    /** Текущие настройки фильтра. */
    val notificationFilter: NotificationFilter get() = filter

    private var attached = false

    fun attach() {
        if (attached) return
        attached = true
        session.rpc.onEvent<NotificationPosted> { event ->
            _knownPackages.value = _knownPackages.value + event.packageName
            if (!filter.allows(event)) return@onEvent
            _notifications.value = (listOf(event) + _notifications.value.filterNot { it.id == event.id })
            if (_notifications.value.size > MAX) {
                _notifications.value = _notifications.value.take(MAX)
            }
        }
        session.rpc.onEvent<NotificationRemoved> { event ->
            _notifications.value = _notifications.value.filterNot { it.id == event.id }
        }
    }

    /** Меняет фильтр и заново применяет его к уже накопленному списку. */
    fun updateFilter(next: NotificationFilter) {
        filter = next
        _notifications.value = _notifications.value.filter { next.allows(it) }
    }

    /** Очищает список (например, при разрыве соединения). */
    fun clear() {
        _notifications.value = emptyList()
    }

    /** Просит телефон закрыть уведомление; локально убирает его при успехе. */
    fun dismiss(id: String) {
        scope.launch {
            val result = runCatching { session.rpc.call(NotificationDismiss(id)) }.getOrNull()
            if (result is NotificationActionResult && result.success) {
                _notifications.value = _notifications.value.filterNot { it.id == id }
            }
        }
    }

    /** Отправляет быстрый ответ на уведомление (например, мессенджер). */
    fun reply(id: String, text: String) {
        scope.launch { runCatching { session.rpc.call(NotificationReply(id, text)) } }
    }

    private companion object {
        const val MAX = 200
    }
}

/**
 * Хост уведомлений на Android: отправляет события о появлении/удалении
 * уведомлений удалённой стороне. Вызывается из [NotificationListenerService].
 *
 * Если фильтр задан и не пропускает уведомление, событие не отправляется вовсе
 * (экономия трафика). Пакет всё равно запоминается вызывающей стороной в UI.
 */
class NotificationsHost(
    private val session: EfirSession,
    private val scope: CoroutineScope,
    private var filter: NotificationFilter = NotificationFilter(),
) {

    private var attached = false

    /** Регистрирует обработчики действий над уведомлениями (dismiss/reply). */
    fun attach() {
        if (attached) return
        attached = true
        session.rpc.onRequest<NotificationDismiss> { request ->
            val ok = dismissRemoteNotification(request.id)
            NotificationActionResult(
                id = request.id,
                success = ok,
                message = if (ok) null else "Не удалось закрыть уведомление",
            )
        }
        session.rpc.onRequest<NotificationReply> { request ->
            val ok = replyRemoteNotification(request.id, request.text)
            NotificationActionResult(
                id = request.id,
                success = ok,
                message = if (ok) null else "Быстрый ответ недоступен для этого уведомления",
            )
        }
    }

    /** Обновляет фильтр отсечения на стороне-источнике. */
    fun updateFilter(next: NotificationFilter) {
        filter = next
    }

    /** Отправляет событие о новом уведомлении (если проходит фильтр). */
    fun post(notification: NotificationPosted) {
        if (!filter.allows(notification)) return
        scope.launch { runCatching { session.rpc.sendEvent(notification) } }
    }

    /** Отправляет событие об удалении уведомления. */
    fun removed(id: String) {
        scope.launch { runCatching { session.rpc.sendEvent(NotificationRemoved(id)) } }
    }
}
