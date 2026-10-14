package ru.efir.core.comms

import android.content.Intent
import android.provider.Telephony
import ru.efir.core.media.EfirAndroidEnvironment

/** Android: роль default SMS app доступна начиная с 4.4 (minSdk 26). */
actual fun defaultSmsRoleSupported(): Boolean = EfirAndroidEnvironment.appContext != null

actual fun isDefaultSmsApp(): Boolean {
    val context = EfirAndroidEnvironment.appContext ?: return false
    val current = Telephony.Sms.getDefaultSmsPackage(context)
    return current != null && current == context.packageName
}

actual fun requestDefaultSmsRole(): Boolean {
    val context = EfirAndroidEnvironment.appContext ?: return false
    return runCatching {
        val intent = Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
            putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
        true
    }.getOrDefault(false)
}
