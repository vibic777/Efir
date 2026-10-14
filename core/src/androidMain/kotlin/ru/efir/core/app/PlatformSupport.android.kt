package ru.efir.core.app

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.Process
import android.provider.Settings
import ru.efir.core.input.EfirAccessibilityService
import ru.efir.core.input.isAccessibilityServiceEnabled
import ru.efir.core.input.openAccessibilitySettings
import ru.efir.core.media.EfirAndroidEnvironment
import java.io.File
import java.util.Locale

actual fun defaultDownloadDir(): String =
    File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Efir").absolutePath

actual fun defaultSyncLocalDir(): String =
    File(Environment.getExternalStorageDirectory(), "Efir").absolutePath

actual val localFilePickerSupported: Boolean = false

actual suspend fun pickLocalFiles(): List<String> = emptyList()

actual fun openLocalFolder(path: String): Boolean = false

private const val PREFERENCES = "efir_settings"

actual fun loadPreference(key: String): String? =
    EfirAndroidEnvironment.appContext
        ?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        ?.getString(key, null)

actual fun savePreference(key: String, value: String) {
    EfirAndroidEnvironment.appContext
        ?.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        ?.edit()?.putString(key, value)?.apply()
}

actual fun systemLanguageCode(): String = Locale.getDefault().language

/**
 * Группы runtime-разрешений. Каждая выдаётся и отзывается отдельно: человек даёт
 * только то, чем пользуется, а отказ в одном не мешает остальным.
 */
private val PERMISSION_GROUPS: List<Pair<String, List<String>>> = buildList {
    add("camera" to listOf(Manifest.permission.CAMERA))
    add("microphone" to listOf(Manifest.permission.RECORD_AUDIO))
    add("sms" to listOf(Manifest.permission.READ_SMS, Manifest.permission.SEND_SMS, Manifest.permission.RECEIVE_SMS))
    add(
        "phone" to buildList {
            add(Manifest.permission.READ_PHONE_STATE)
            add(Manifest.permission.READ_CALL_LOG)
            add(Manifest.permission.CALL_PHONE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) add(Manifest.permission.ANSWER_PHONE_CALLS)
        },
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add("notify" to listOf(Manifest.permission.POST_NOTIFICATIONS))
}

/** До Android 11 доступ к общей памяти — обычное runtime-разрешение. */
private val LEGACY_STORAGE = listOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

/** Все runtime-разрешения приложения — для отзыва всех сразу. */
val RUNTIME_PERMISSIONS: List<String> =
    PERMISSION_GROUPS.flatMap { it.second } + if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) LEGACY_STORAGE else emptyList()

private fun Context.granted(permission: String): Boolean =
    checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

private fun Context.open(vararg intents: Intent) {
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { startActivity(intent) }.isSuccess) return
    }
}

/** Android 13+ умеет отзывать runtime-разрешения у самого приложения — но только с его закрытием. */
private val canRevokeRuntime: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

private fun Context.openAppSettings() =
    open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName")), Intent(Settings.ACTION_SETTINGS))

/** Закрывает приложение: отозванные runtime-разрешения система снимает, когда процесс завершён. */
private fun closeApp() {
    Handler(Looper.getMainLooper()).postDelayed({
        EfirAndroidEnvironment.closeApp?.invoke() ?: Process.killProcess(Process.myPid())
    }, 400)
}

/** Отзыв runtime-разрешений: на Android 13+ сам (с закрытием приложения), раньше — через настройки. */
private fun Context.revokeRuntime(permissions: List<String>) {
    if (canRevokeRuntime) {
        val granted = permissions.filter { granted(it) }
        if (granted.isNotEmpty()) runCatching { revokeSelfPermissionsOnKill(granted) }
        closeApp()
    } else {
        openAppSettings()
    }
}

private fun revokeScreen() {
    EfirAndroidEnvironment.projectionPermission = null
}

private fun Context.revokeAccessibility() {
    // Работающая служба отключает себя сама; иначе — системный экран.
    EfirAccessibilityService.instance?.disableSelf() ?: openAccessibilitySettings()
}

/**
 * Разрешены ли приложению «ограниченные настройки» (Android 13+). Приложению,
 * установленному из файла, а не из магазина, система не даёт включить службу
 * доступности и доступ к уведомлениям, а на новых версиях — и SMS со звонками,
 * пока человек не разрешит это в сведениях о приложении. `null` — узнать нельзя.
 */
private fun Context.restrictedSettingsAllowed(): Boolean? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return runCatching {
        val ops = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        when (ops.unsafeCheckOpNoThrow("android:access_restricted_settings", Process.myUid(), packageName)) {
            AppOpsManager.MODE_ERRORED, AppOpsManager.MODE_IGNORED -> false
            else -> true
        }
    }.getOrNull()
}

actual val revokeAllClosesApp: Boolean get() = canRevokeRuntime

actual fun revokeAllPermissions() {
    val context = EfirAndroidEnvironment.appContext ?: return
    revokeScreen()
    EfirAccessibilityService.instance?.disableSelf()
    if (canRevokeRuntime) {
        val granted = RUNTIME_PERMISSIONS.filter { context.granted(it) }
        if (granted.isNotEmpty()) runCatching { context.revokeSelfPermissionsOnKill(granted) }
    }
    // Доступ к файлам, к уведомлениям и работу в фоне Android даёт отключить только в своих настройках.
    context.openAppSettings()
    if (canRevokeRuntime) closeApp()
}

actual fun platformPermissions(): List<PermissionItem> {
    val context = EfirAndroidEnvironment.appContext ?: return emptyList()
    val packageUri = Uri.parse("package:${context.packageName}")
    val items = mutableListOf<PermissionItem>()
    val request = { permissions: List<String> -> EfirAndroidEnvironment.requestPermissions?.invoke(permissions) ?: Unit }

    val accessibility = isAccessibilityServiceEnabled()
    val listeners = Settings.Secure.getString(context.contentResolver, "enabled_notification_listeners").orEmpty()
    val listening = listeners.contains(context.packageName)

    // Пока система блокирует «ограниченные настройки», остальное ниже выдать не получится: строка идёт первой.
    if (context.restrictedSettingsAllowed() == false && !(accessibility && listening)) {
        items += PermissionItem("restricted", false) { context.openAppSettings() }
    }

    // Файлы: без доступа ко всей памяти удалённая сторона видит пустые папки.
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // Выдаётся и отзывается на одном и том же системном экране.
        val allFiles = {
            context.open(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, packageUri),
                Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION),
            )
        }
        items += PermissionItem("files", Environment.isExternalStorageManager(), revoke = allFiles, request = allFiles)
    } else {
        items += PermissionItem(
            "files", LEGACY_STORAGE.all { context.granted(it) },
            revoke = { context.openAppSettings() },
        ) { request(LEGACY_STORAGE) }
    }

    // Камера, микрофон, SMS, звонки, уведомления — каждое своей строкой.
    for ((id, permissions) in PERMISSION_GROUPS) {
        items += PermissionItem(
            id, permissions.all { context.granted(it) },
            revokeClosesApp = canRevokeRuntime,
            revoke = { context.revokeRuntime(permissions) },
        ) { request(permissions) }
    }

    items += PermissionItem("screen", EfirAndroidEnvironment.projectionPermission != null, revoke = { revokeScreen() }) {
        EfirAndroidEnvironment.requestProjection?.invoke()
    }

    // Android 13+ блокирует эти две настройки у приложения, установленного из файла («Ограниченные настройки»):
    // перед выдачей показывается памятка, как их разрешить.
    val appInfo: (() -> Unit)? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) ({ context.openAppSettings() }) else null

    items += PermissionItem(
        "accessibility", accessibility,
        revoke = { context.revokeAccessibility() },
        appInfo = appInfo.takeIf { !accessibility },
    ) {
        openAccessibilitySettings()
    }

    val listenerSettings = { context.open(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }
    items += PermissionItem(
        "notifications", listening,
        revoke = listenerSettings,
        appInfo = appInfo.takeIf { !listening },
        request = listenerSettings,
    )

    val power = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    items += PermissionItem(
        "battery", power?.isIgnoringBatteryOptimizations(context.packageName),
        revoke = { context.open(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS), Intent(Settings.ACTION_SETTINGS)) },
    ) {
        EfirAndroidEnvironment.requestIgnoreBatteryOptimizations?.invoke()
    }
    return items
}

actual fun exportLog(text: String): String? {
    val context = EfirAndroidEnvironment.appContext ?: return null
    // Журнал уходит туда, куда выберет человек: мессенджер, почта, заметки.
    // Через Intent проходит ограниченный объём, поэтому берётся конец журнала — самое свежее.
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, "Efir log")
        .putExtra(Intent.EXTRA_TEXT, text.takeLast(200_000))
    context.open(Intent.createChooser(send, "Efir log"))
    return null
}
