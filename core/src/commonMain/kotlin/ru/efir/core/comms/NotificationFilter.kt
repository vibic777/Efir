package ru.efir.core.comms

import ru.efir.core.protocol.NotificationPosted

/**
 * Настройки фильтрации зеркалирования уведомлений Android на ПК.
 *
 * @param enabled глобальный выключатель зеркалирования (если `false` — уведомления
 *   вообще не пересылаются).
 * @param excludedPackages пакеты приложений, уведомления которых не пересылаются.
 * @param hideOngoing скрывать «постоянные» уведомления (музыка, навигация и т.п.).
 * @param onlyPackagesAllowlist если непусто — пересылаются только уведомления этих
 *   пакетов (белый список имеет приоритет над чёрным).
 */
data class NotificationFilter(
    val enabled: Boolean = true,
    val excludedPackages: Set<String> = emptySet(),
    val hideOngoing: Boolean = false,
    val onlyPackagesAllowlist: Set<String> = emptySet(),
) {

    /** Должно ли уведомление быть переслано на ПК. */
    fun allows(notification: NotificationPosted): Boolean {
        if (!enabled) return false
        if (hideOngoing && notification.ongoing) return false
        if (onlyPackagesAllowlist.isNotEmpty()) {
            return notification.packageName in onlyPackagesAllowlist
        }
        return notification.packageName !in excludedPackages
    }

    /** Переключает пакет в чёрном списке (включить/исключить). */
    fun togglePackage(packageName: String): NotificationFilter =
        copy(
            excludedPackages = if (packageName in excludedPackages) {
                excludedPackages - packageName
            } else {
                excludedPackages + packageName
            },
        )
}
