package ru.efir.core.comms

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import ru.efir.core.protocol.NotificationPosted

/**
 * Служба прослушивания уведомлений Android (`NotificationListenerService`).
 *
 * Требует разрешения пользователя в «Доступе к уведомлениям». При включении
 * публикует событие, чтобы [EfirAndroidEnvironment] мог привязать хост и
 * передать уведомления на ПК.
 *
 * Сама служба не держит соединение — она лишь выставляет колбэк, который
 * использует активная сессия [ru.efir.core.app.EfirController].
 */
class EfirNotificationListenerService : NotificationListenerService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onListenerConnected() {
        super.onListenerConnected()
        EfirAndroidEnvironment.notificationsEnabled = true
        EfirAndroidEnvironment.notificationListener = this
    }

    override fun onListenerDisconnected() {
        EfirAndroidEnvironment.notificationsEnabled = false
        EfirAndroidEnvironment.notificationListener = null
        super.onListenerDisconnected()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        val posted = sbn?.toPosted() ?: return
        EfirAndroidEnvironment.onNotificationPosted?.invoke(posted)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
        val key = sbn?.key ?: return
        EfirAndroidEnvironment.onNotificationRemoved?.invoke(key)
    }

    override fun onDestroy() {
        scope.cancel()
        EfirAndroidEnvironment.notificationListener = null
        EfirAndroidEnvironment.onNotificationPosted = null
        EfirAndroidEnvironment.onNotificationRemoved = null
        super.onDestroy()
    }

    /** Преобразует системное уведомление в протокольное [NotificationPosted]. */
    private fun StatusBarNotification.toPosted(): NotificationPosted {
        val extras = notification?.extras
        val title = extras?.getCharSequence(android.app.Notification.EXTRA_TITLE)?.toString()
        val text = extras?.getCharSequence(android.app.Notification.EXTRA_TEXT)?.toString()
        return NotificationPosted(
            id = key ?: "$packageName:$id",
            packageName = packageName,
            appName = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(packageName, 0),
                ).toString()
            }.getOrNull(),
            title = title,
            text = text,
            postedAt = postTime,
            ongoing = (notification?.flags ?: 0) and android.app.Notification.FLAG_ONGOING_EVENT != 0,
        )
    }
}
