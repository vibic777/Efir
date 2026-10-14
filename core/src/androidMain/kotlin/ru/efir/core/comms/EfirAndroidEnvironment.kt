package ru.efir.core.comms

import ru.efir.core.protocol.NotificationPosted

/**
 * Держатель колбэков и состояния Android-службы уведомлений.
 *
 * Служба [`EfirNotificationListenerService`] выставляет здесь актуальные
 * колбэки, а активная сессия ([ru.efir.core.app.EfirController]) их читает и
 * пересылает удалённой стороне через `NotificationsHost`.
 */
object EfirAndroidEnvironment {

    /** Включён ли доступ к уведомлениям (служба подключена). */
    @Volatile
    var notificationsEnabled: Boolean = false

    /** Активный экземпляр службы (может понадобиться для dismiss и т.п.). */
    @Volatile
    var notificationListener: Any? = null

    /** Колбэк появления уведомления. */
    @Volatile
    var onNotificationPosted: ((NotificationPosted) -> Unit)? = null

    /** Колбэк удаления уведомления (по id/key). */
    @Volatile
    var onNotificationRemoved: ((String) -> Unit)? = null
}
