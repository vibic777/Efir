package ru.efir.core.comms

/**
 * Платформенные операции с ролью «приложение SMS по умолчанию».
 *
 * Полноценная отправка SMS и запись в провайдер (со статусами доставки) доступны
 * только приложению с ролью default SMS app (Android 4.4+). На Desktop — не
 * поддерживается.
 */

/** Поддерживается ли запрос роли default SMS app на этой платформе. */
expect fun defaultSmsRoleSupported(): Boolean

/** Является ли текущее приложение приложением SMS по умолчанию. */
expect fun isDefaultSmsApp(): Boolean

/**
 * Открывает системный диалог запроса роли default SMS app.
 * @return `true`, если удалось открыть диалог.
 */
expect fun requestDefaultSmsRole(): Boolean
