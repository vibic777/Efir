package ru.efir.core.input

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import ru.efir.core.media.EfirAndroidEnvironment

/**
 * Android-реализация проверки/включения службы доступности Efir.
 *
 * Служба должна быть включена пользователем вручную в системных настройках —
 * программно включить её без root нельзя. Здесь только проверяем статус и
 * открываем экран настроек.
 */

/** Класс нашей службы доступности (пакет — у приложения, а не у библиотеки). */
private const val EFIR_SERVICE_CLASS = "ru.efir.core.input.EfirAccessibilityService"

actual fun isAccessibilityServiceEnabled(): Boolean {
    // Быстрый путь: живой экземпляр службы уже подключён.
    if (EfirAccessibilityService.isConnected) return true

    val context = EfirAndroidEnvironment.appContext ?: return false
    val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
        ?: return false
    val enabled = manager.getEnabledAccessibilityServiceList(
        android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK,
    )
    return enabled.any { info ->
        val serviceInfo = info.resolveInfo?.serviceInfo ?: return@any false
        val component = ComponentName(serviceInfo.packageName, serviceInfo.name)
        component == ComponentName(context.packageName, EFIR_SERVICE_CLASS)
    }
}

actual fun openAccessibilitySettings(): Boolean {
    val context = EfirAndroidEnvironment.appContext ?: return false
    val intents = listOf(
        Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS),
        // Некоторые прошивки прячут общий экран — ведём в настройки приложения.
        Intent(Settings.ACTION_SETTINGS),
    )
    for (intent in intents) {
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return true
    }
    return false
}

/** Утилита: читает строку включённых служб (для диагностики/тестов). */
internal fun enabledAccessibilityServices(context: Context): Set<String> {
    val raw = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return emptySet()
    if (TextUtils.isEmpty(raw)) return emptySet()
    return raw.split(':').mapNotNull { entry ->
        val component = ComponentName.unflattenFromString(entry) ?: return@mapNotNull null
        component.flattenToString()
    }.toSet()
}
