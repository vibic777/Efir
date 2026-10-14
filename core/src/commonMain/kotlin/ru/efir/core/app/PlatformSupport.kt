package ru.efir.core.app

/** Папка на этом устройстве, куда складываются скачанные файлы. */
expect fun defaultDownloadDir(): String

/** Локальная папка, предлагаемая по умолчанию в новом правиле синхронизации. */
expect fun defaultSyncLocalDir(): String

/** Есть ли на платформе системный диалог выбора файлов. */
expect val localFilePickerSupported: Boolean

/** Показывает диалог выбора файлов и возвращает их пути (пусто — отмена). */
expect suspend fun pickLocalFiles(): List<String>

/** Открывает локальную папку в проводнике; `false`, если платформа не умеет. */
expect fun openLocalFolder(path: String): Boolean

/** Читает сохранённую настройку приложения (язык, тема). */
expect fun loadPreference(key: String): String?

/** Сохраняет настройку приложения. */
expect fun savePreference(key: String, value: String)

/** Двухбуквенный код языка системы (`ru`, `en`, ...). */
expect fun systemLanguageCode(): String

/**
 * Разрешение, которое пользователь выдаёт системе вручную (Android).
 *
 * @param id ключ для подписи в интерфейсе: `files`, `runtime`, `screen`,
 *   `accessibility`, `notifications`, `battery`.
 * @param granted выдано ли (`null` — узнать нельзя).
 * @param request открывает системный диалог или экран настроек.
 * @param appInfo открывает сведения о приложении в настройках системы. Задан, когда
 *   Android может блокировать эту настройку как «ограниченную»: тогда перед выдачей
 *   показывается памятка, как её разрешить.
 * @param revoke отзывает разрешение: сразу, либо открывает системный экран, где оно отключается.
 * @param revokeClosesApp отзыв вступает в силу только с закрытием приложения — оно закроется.
 */
class PermissionItem(
    val id: String,
    val granted: Boolean?,
    val revokeClosesApp: Boolean = false,
    val revoke: (() -> Unit)? = null,
    val appInfo: (() -> Unit)? = null,
    val request: () -> Unit,
)

/** Разрешения, нужные приложению на этой платформе (на ПК — пусто). */
expect fun platformPermissions(): List<PermissionItem>

/**
 * Отзывает все разрешения разом: что можно — сразу, для остальных открывает системные настройки
 * приложения. Возвращает `true`, если приложение после этого закроется (на ПК ничего не делает).
 */
expect fun revokeAllPermissions()

/** Закроет ли [revokeAllPermissions] приложение (Android 13+). */
expect val revokeAllClosesApp: Boolean

/**
 * Отдаёт журнал приложения наружу: на ПК сохраняет файл в папку загрузок и
 * открывает её, на телефоне открывает системное «Поделиться». Возвращает путь
 * сохранённого файла, если он есть.
 */
expect fun exportLog(text: String): String?
