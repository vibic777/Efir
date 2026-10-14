package ru.efir.android

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import ru.efir.core.app.AppLog
import ru.efir.core.media.EfirAndroidEnvironment

/**
 * Foreground-сервис: держит процесс живым, пока телефон на связи в фоне, и
 * даёт право на захват экрана (на Android 10+ захват `MediaProjection` без
 * сервиса типа `mediaProjection` система отзывает).
 *
 * Батарея (`docs/BATTERY_RU.md`): сам по себе сервис ничего не делает и процессор
 * не удерживает — без соединения телефон спит как обычно, порт ожидания будит
 * его входящим подключением. Частичный `WakeLock` берётся только на время
 * соединения ([connected]) и снимается с его концом: это то, чего человек ждёт
 * прямо сейчас. Убитый системой сервис сам не перезапускается.
 */
class ScreenCaptureService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = buildNotification()
        // Тип «показ экрана» допустим только с выданным разрешением на захват; иначе — «связь с устройством».
        val withProjection = EfirAndroidEnvironment.projectionPermission != null
        val started = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                var type = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                if (withProjection) {
                    type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
                }
                startForeground(NOTIFICATION_ID, notification, type)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }
        if (started.isFailure) {
            projectionReady = false
            AppLog.e("service", "the background service was refused by the system", started.exceptionOrNull())
            stopSelf()
            return START_NOT_STICKY
        }
        // Захват экрана можно начинать, только когда сервис работает с типом «показ экрана».
        projectionReady = withProjection || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
        applyWakeLock()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (instance === this) instance = null
        projectionReady = false
        releaseWakeLock()
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }

    /** Процессор удерживается, только пока идёт соединение. */
    private fun applyWakeLock() {
        if (connected) acquireWakeLock() else releaseWakeLock()
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        runCatching {
            val manager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = manager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "$WAKELOCK_TAG:$NOTIFICATION_ID",
            ).apply {
                setReferenceCounted(false)
                acquire()
            }
        }
    }

    private fun releaseWakeLock() {
        runCatching {
            wakeLock?.takeIf { it.isHeld }?.release()
        }
        wakeLock = null
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.capture_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            manager.createNotificationChannel(channel)
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        // Нажатие на уведомление открывает приложение.
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        return builder
            .setContentTitle(getString(R.string.capture_notification_title))
            .setContentText(getString(R.string.capture_notification_text))
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .setContentIntent(open)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "efir_screen_capture"
        private const val NOTIFICATION_ID = 4101
        private const val WAKELOCK_TAG = "Efir:Connection"

        @Volatile
        private var instance: ScreenCaptureService? = null

        /** Работает ли сервис с типом «показ экрана» (до этого Android не отдаёт захват). */
        @Volatile
        var projectionReady: Boolean = false
            private set

        /** Идёт ли соединение с другим устройством: на это время удерживается процессор. */
        @Volatile
        var connected: Boolean = false
            set(value) {
                field = value
                instance?.applyWakeLock()
            }

        /** Запускает сервис (повторный вызов обновляет его тип и удержание процессора). */
        fun start(context: Context) {
            val intent = Intent(context, ScreenCaptureService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /** Останавливает сервис. */
        fun stop(context: Context) {
            context.stopService(Intent(context, ScreenCaptureService::class.java))
        }
    }
}
