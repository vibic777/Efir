package ru.efir.core.media

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import ru.efir.core.app.AppLog
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.ErrorMessage
import ru.efir.core.protocol.Message
import ru.efir.core.protocol.ScreenEnded
import ru.efir.core.protocol.MessageIds
import ru.efir.core.protocol.ScreenStart
import ru.efir.core.protocol.ScreenStarted
import ru.efir.core.protocol.ScreenStop
import ru.efir.core.protocol.ScreenStopped

/**
 * Состояние хоста трансляции экрана.
 *
 * @param active идёт ли захват и передача кадров.
 * @param sessionId идентификатор активной медиа-сессии.
 * @param width/height фактический размер кадра.
 * @param error последняя ошибка (если была).
 */
data class ScreenStreamState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val sentFrames: Long = 0,
    val fps: Int = 0,
    val jpegQuality: Int = 0,
    val measuredBps: Long = 0,
    /** Профайлер: фактический FPS за окно. */
    val actualFps: Double = 0.0,
    /** Профайлер: оценка задержки «захват → отправка» (мс). */
    val latencyMs: Long = 0,
    val error: String? = null,
)

/**
 * Передающая сторона трансляции экрана (Android-хост).
 *
 * Подписывается на RPC-запросы [ScreenStart]/[ScreenStop] установленной сессии,
 * запускает платформенный [ScreenCaptureSource], кодирует кадры в [MediaFrame]
 * и отправляет их в бинарный SCREEN-канал.
 *
 * Потокобезопасен: запуск/остановка сериализованы [Mutex].
 */
class ScreenStreamHost(
    private val session: EfirSession,
    private val scope: CoroutineScope,
    private val captureFactory: () -> ScreenCaptureSource = ::createScreenCaptureSource,
    private val supported: Boolean = screenCaptureSupported(),
) {

    private val mutex = Mutex()

    private val _state = MutableStateFlow(ScreenStreamState())
    val state: StateFlow<ScreenStreamState> = _state.asStateFlow()

    private var source: ScreenCaptureSource? = null
    private var pump: Job? = null
    private var sequence: Long = 0
    private var quality: ScreenQuality? = null
    private var abr: AdaptiveBitrateController? = null
    private var lastFrameAt: Long = 0
    private var metrics: StreamMetrics? = null

    /** Регистрирует обработчики на сессии. Вызывается один раз после установки соединения. */
    fun attach() {
        session.rpc.onRequest<ScreenStart> { request -> start(request) }
        session.rpc.onRequest<ScreenStop> { stop() }
    }

    private suspend fun start(request: ScreenStart): Message {
        if (!supported) {
            _state.value = _state.value.copy(error = StreamErrors.UNAVAILABLE)
            return ErrorMessage(code = StreamErrors.UNAVAILABLE, message = StreamErrors.UNAVAILABLE)
        }
        return mutex.withLock {
            // Повторный запрос — перезапускаем с новыми настройками.
            stopInternal()
            val quality = ScreenQuality.from(request)
            val capture = captureFactory()
            val size = try {
                capture.start(quality)
            } catch (e: CancellationException) {
                runCatching { capture.stop() }
                throw e
            } catch (e: Throwable) {
                // Причина уходит другой стороне кодом: там её покажут на своём языке.
                runCatching { capture.stop() }
                val code = (e as? StreamRefused)?.code ?: StreamErrors.FAILED
                AppLog.w("screen", "showing the screen did not start: $code", e.takeIf { it !is StreamRefused })
                _state.value = ScreenStreamState(error = code)
                return@withLock ErrorMessage(code = code, message = e.message ?: code)
            }
            source = capture
            this.quality = quality
            abr = if (quality.adaptive) {
                AdaptiveBitrateController(quality.bitrate, quality.jpegQuality, quality.targetFps)
            } else {
                null
            }
            sequence = 0
            lastFrameAt = 0
            metrics = StreamMetrics()
            val sessionId = MessageIds.new()
            _state.value = ScreenStreamState(
                active = true,
                sessionId = sessionId,
                width = size.width,
                height = size.height,
                fps = quality.targetFps,
                jpegQuality = quality.jpegQuality,
            )
            AppLog.i("screen", "the screen is shown to the other device: ${size.width}x${size.height}")
            pump = scope.launch { pumpFrames(capture, sessionId) }
            ScreenStarted(
                sessionId = sessionId,
                width = size.width,
                height = size.height,
                codec = request.codec,
            )
        }
    }

    private suspend fun stop(): ScreenStopped {
        return mutex.withLock {
            val id = _state.value.sessionId ?: ""
            if (_state.value.active) AppLog.i("screen", "the other device stopped watching the screen")
            stopInternal()
            _state.value = ScreenStreamState()
            ScreenStopped(sessionId = id)
        }
    }

    /**
     * Показ останавливает само это устройство: кнопкой «Остановить показ» или потому,
     * что захват закончился ([reason] — код из [StreamErrors]). Другая сторона узнаёт
     * об этом событием и убирает картинку.
     */
    suspend fun stopSharing(reason: String = StreamErrors.STOPPED, only: String? = null) {
        mutex.withLock {
            val id = _state.value.sessionId ?: return@withLock
            if (only != null && only != id) return@withLock
            AppLog.i("screen", "showing the screen ended here: $reason")
            stopInternal()
            _state.value = ScreenStreamState(error = reason.takeIf { it != StreamErrors.STOPPED })
            runCatching { session.rpc.sendEvent(ScreenEnded(sessionId = id, reason = reason)) }
        }
    }

    private suspend fun stopInternal() {
        pump?.cancel()
        pump = null
        source?.let { runCatching { it.stop() } }
        source = null
        quality = null
        abr = null
        lastFrameAt = 0
        metrics = null
        _state.value = _state.value.copy(active = false)
    }
    private suspend fun pumpFrames(source: ScreenCaptureSource, sessionId: String) {
        val base = quality
        val controller = abr
        val profiler = metrics
        var activeFps = base?.targetFps ?: 0
        source.frames()
            .catch { error ->
                // Захват закончился сам (показ остановили в системе, сбой): сообщаем другой стороне.
                val code = (error as? StreamRefused)?.code ?: StreamErrors.FAILED
                if (error !is StreamRefused) AppLog.w("screen", "the screen capture failed", error)
                scope.launch { stopSharing(reason = code, only = sessionId) }
            }
            .collect { frame ->
                // Троттлинг FPS: пропускаем кадры, приходящие чаще целевого интервала.
                if (activeFps > 0) {
                    val minInterval = 1000L / activeFps.coerceAtLeast(1)
                    if (lastFrameAt != 0L && frame.timestamp - lastFrameAt < minInterval) return@collect
                }
                lastFrameAt = frame.timestamp

                val bytes = MediaFrameCodec.encode(
                    MediaFrame.Image(
                        kind = MediaFrameKind.SCREEN,
                        sessionId = sessionId,
                        sequence = sequence++,
                        timestamp = frame.timestamp,
                        keyframe = frame.keyframe,
                        width = frame.size.width,
                        height = frame.size.height,
                        mimeType = frame.mimeType,
                        config = frame.config,
                        payload = frame.payload,
                    ),
                )
                session.connection.sendBinary(bytes)
                val tuning = controller?.onFrame(bytes.size)
                profiler?.onFrame(bytes.size, frame.timestamp)
                val metricsSnapshot = profiler?.snapshot
                val state = _state.value.copy(
                    sentFrames = sequence,
                    measuredBps = controller?.lastMeasuredBps ?: 0,
                    actualFps = metricsSnapshot?.fps ?: 0.0,
                    latencyMs = metricsSnapshot?.latencyMs ?: 0,
                )
                if (tuning != null) {
                    activeFps = tuning.targetFps
                    runCatching { source.setEncoderQuality(tuning.jpegQuality, tuning.targetFps) }
                    _state.value = state.copy(fps = tuning.targetFps, jpegQuality = tuning.jpegQuality)
                } else {
                    _state.value = state
                }
                // Пауза для удержания целевого FPS, когда источник отдаёт кадры чаще.
                if (activeFps in 1..119) {
                    delay(1000L / activeFps)
                }
            }
    }

    /** Принудительная остановка (например, при закрытии соединения). */
    suspend fun shutdown() {
        mutex.withLock { stopInternal() }
    }
}
