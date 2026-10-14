package ru.efir.core.comms

import android.app.Notification
import android.app.RemoteInput
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

private val listener: NotificationListenerService?
    get() = EfirAndroidEnvironment.notificationListener as? NotificationListenerService

actual fun notificationActionsSupported(): Boolean =
    EfirAndroidEnvironment.notificationsEnabled && listener != null

actual fun dismissRemoteNotification(id: String): Boolean {
    val service = listener ?: return false
    return runCatching {
        val sbn = find(service, id) ?: return false
        service.cancelNotification(sbn.key)
        true
    }.getOrDefault(false)
}

actual fun replyRemoteNotification(id: String, text: String): Boolean {
    val service = listener ?: return false
    if (text.isBlank()) return false
    return runCatching {
        val sbn = find(service, id) ?: return false
        val action = sbn.notification?.actions?.firstOrNull { hasRemoteInput(it) } ?: return false
        val remoteInput = action.remoteInputs.firstOrNull() ?: return false

        val intent = Intent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val bundle = Bundle().apply { putCharSequence(remoteInput.resultKey, text) }
        RemoteInput.addResultsToIntent(arrayOf(remoteInput), intent, bundle)

        action.actionIntent.send(service, 0, intent)
        service.cancelNotification(sbn.key)
        true
    }.getOrDefault(false)
}

/** Находит активное уведомление по протокольному id (key). */
private fun find(service: NotificationListenerService, id: String): StatusBarNotification? =
    service.activeNotifications?.firstOrNull { it.key == id }

/** Есть ли у действия уведомления поле быстрого ответа (RemoteInput). */
private fun hasRemoteInput(action: Notification.Action): Boolean =
    action.remoteInputs?.isNotEmpty() == true
