package ru.efir.core.media

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.withTimeoutOrNull
import ru.efir.core.media.EfirAndroidEnvironment.ProjectionPermission

/**
 * Держатель платформенного окружения Android, необходимое ядру для захвата
 * экрана и инъекции ввода. Заполняется приложением (`androidApp`) в
 * `MainActivity`/сервисах.
 *
 * Ядро не может само запросить разрешение MediaProjection или включить
 * службу доступности — это делает UI-слой, а сюда лишь передаёт результат.
 */
object EfirAndroidEnvironment {

    /** Результат запроса разрешения MediaProjection (Activity result). */
    data class ProjectionPermission(
        val resultCode: Int,
        val data: Intent,
    )

    /** Контекст приложения (application context). */
    @Volatile
    var appContext: Context? = null

    /** Текущее разрешение MediaProjection (если выдано пользователем). */
    @Volatile
    var projectionPermission: ProjectionPermission? = null

    /**
     * Запросить запуск foreground-сервиса захвата экрана.
     * Устанавливается приложением; обязателен на Android 10+.
     */
    @Volatile
    var requestForegroundService: (() -> Unit)? = null

    /** Запросить остановку foreground-сервиса захвата экрана. */
    @Volatile
    var stopForegroundService: (() -> Unit)? = null

    /**
     * Запросить у пользователя исключение из оптимизации батареи (Doze).
     * Устанавливается приложением; открывает системный диалог.
     */
    @Volatile
    var requestIgnoreBatteryOptimizations: (() -> Unit)? = null

    /**
     * Запросить у системы постоянный доступ к уведомлениям (Android 13+),
     * если `POST_NOTIFICATIONS` ещё не выдан.
     */
    @Volatile
    var requestNotificationPermission: (() -> Unit)? = null

    /** Показать системный диалог разрешения на захват экрана (MediaProjection). */
    @Volatile
    var requestProjection: (() -> Unit)? = null

    /** Работает ли уже фоновый сервис с типом «показ экрана»: без него Android не отдаёт захват. */
    @Volatile
    var projectionServiceReady: (() -> Boolean)? = null

    /** Ответ человека на системный вопрос о показе экрана: его ждёт [awaitProjection]. */
    @Volatile
    private var projectionAnswer: CompletableDeferred<Boolean>? = null

    /** Приложение сообщает, что человек ответил на вопрос о показе экрана. */
    fun onProjectionAnswer(granted: Boolean) {
        projectionAnswer?.complete(granted)
    }

    /**
     * Разрешение на показ экрана. Если его ещё нет, телефон спрашивает человека
     * (окно системы; когда Эфир свёрнут — уведомление, которое это окно открывает)
     * и ждёт ответа до [StreamErrors.PERMISSION_WAIT_MS].
     *
     * @throws StreamRefused отказ или нет ответа.
     */
    suspend fun awaitProjection(): ProjectionPermission {
        projectionPermission?.let { return it }
        val ask = requestProjection ?: throw StreamRefused(StreamErrors.PERMISSION, "the app window is closed")
        val answer = CompletableDeferred<Boolean>()
        projectionAnswer = answer
        try {
            ask()
            val granted = withTimeoutOrNull(StreamErrors.PERMISSION_WAIT_MS) { answer.await() }
                ?: throw StreamRefused(StreamErrors.PERMISSION)
            if (!granted) throw StreamRefused(StreamErrors.REFUSED)
        } finally {
            projectionAnswer = null
        }
        return projectionPermission ?: throw StreamRefused(StreamErrors.PERMISSION)
    }

    /** Запросить runtime-разрешения одной группы (камера, микрофон, SMS, звонки, уведомления). */
    @Volatile
    var requestPermissions: ((List<String>) -> Unit)? = null

    /** Закрыть приложение целиком (после отзыва разрешений: система снимает их с завершением процесса). */
    @Volatile
    var closeApp: (() -> Unit)? = null
}
