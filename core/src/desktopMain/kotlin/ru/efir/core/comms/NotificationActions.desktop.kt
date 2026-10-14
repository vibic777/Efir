package ru.efir.core.comms

// Действия над уведомлениями доступны только на Android-хосте.

actual fun notificationActionsSupported(): Boolean = false

actual fun dismissRemoteNotification(id: String): Boolean = false

actual fun replyRemoteNotification(id: String, text: String): Boolean = false
