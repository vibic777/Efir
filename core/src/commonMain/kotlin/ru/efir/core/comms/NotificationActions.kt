package ru.efir.core.comms

/**
 * Платформенные действия над уведомлениями Android (dismiss/быстрый ответ),
 * инициируемые с ПК.
 *
 * На Android реализуются через активный [`NotificationListenerService`]
 * (`EfirAndroidEnvironment.notificationListener`). На Desktop — не поддерживаются.
 */

/** Поддерживает ли платформа управление уведомлениями (dismiss/reply). */
expect fun notificationActionsSupported(): Boolean

/**
 * Закрывает (dismiss) уведомление по его id/key.
 * @return `true`, если действие выполнено.
 */
expect fun dismissRemoteNotification(id: String): Boolean

/**
 * Отправляет быстрый текстовый ответ на уведомление (через `RemoteInput`),
 * если у него есть подходящее действие.
 * @return `true`, если действие выполнено.
 */
expect fun replyRemoteNotification(id: String, text: String): Boolean
