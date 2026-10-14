package ru.efir.core.media

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
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.AudioStart
import ru.efir.core.protocol.AudioStarted
import ru.efir.core.protocol.AudioStop
import ru.efir.core.protocol.AudioStopped
import ru.efir.core.protocol.CameraStart
import ru.efir.core.protocol.CameraStarted
import ru.efir.core.protocol.CameraStop
import ru.efir.core.protocol.CameraStopped
import ru.efir.core.protocol.MessageIds

/** Состояние хоста трансляции камеры. */
data class CameraStreamState(
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

/** Состояние хоста передачи звука. */
data class AudioStreamState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val sentChunks: Long = 0,
    val error: String? = null,
)

/**
 * Передающая сторона трансляции камеры (Android-хост). Аналог [ScreenStreamHost],
 * но по каналу CAMERA.
 */
class CameraStreamHost(
    private val session: EfirSession,
    private val scope: CoroutineScope,
    private val captureFactory: () -> CameraCaptureSource = ::createCameraCaptureSource,
    private val supported: Boolean = cameraCaptureSupported(),
) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(CameraStreamState())
    val state: StateFlow<CameraStreamState> = _state.asStateFlow()

    private var source: CameraCaptureSource? = null
    private var pump: Job? = null
    private var sequence: Long = 0
    private var quality: CameraQuality? = null
    private var abr: AdaptiveBitrateController? = null
    private var lastFrameAt: Long = 0
    private var metrics: StreamMetrics? = null

    fun attach() {
        session.rpc.onRequest<CameraStart> { request -> start(request) }
        session.rpc.onRequest<CameraStop> { stop() }
    }

    private suspend fun start(request: CameraStart): CameraStarted = mutex.withLock {
        if (!supported) {
            _state.value = _state.value.copy(error = "Захват камеры не поддерживается на этом устройстве")
            return@withLock CameraStarted(sessionId = "", width = 0, height = 0, codec = "jpeg")
        }
        stopInternal()
        val quality = CameraQuality.from(request)
        val capture = captureFactory()
        val size = capture.start(quality)
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
        _state.value = CameraStreamState(
            active = true,
            sessionId = sessionId,
            width = size.width,
            height = size.height,
            fps = quality.targetFps,
            jpegQuality = quality.jpegQuality,
        )
        pump = scope.launch { pumpFrames(capture, sessionId) }
        CameraStarted(sessionId = sessionId, width = size.width, height = size.height, codec = "jpeg")
    }

    private suspend fun stop(): CameraStopped = mutex.withLock {
        val id = _state.value.sessionId ?: ""
        stopInternal()
        _state.value = CameraStreamState()
        CameraStopped(sessionId = id)
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

    private suspend fun pumpFrames(capture: CameraCaptureSource, sessionId: String) {
        val base = quality
        val controller = abr
        val profiler = metrics
        var activeFps = base?.targetFps ?: 0
        capture.frames()
            .catch { error ->
                _state.value = _state.value.copy(active = false, error = error.message ?: "Сбой камеры")
            }
            .collect { frame ->
                if (activeFps > 0) {
                    val minInterval = 1000L / activeFps.coerceAtLeast(1)
                    if (lastFrameAt != 0L && frame.timestamp - lastFrameAt < minInterval) return@collect
                }
                lastFrameAt = frame.timestamp

                val bytes = MediaFrameCodec.encode(
                    MediaFrame.Image(
                        kind = MediaFrameKind.CAMERA,
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
                    runCatching { capture.setEncoderQuality(tuning.jpegQuality, tuning.targetFps) }
                    _state.value = state.copy(fps = tuning.targetFps, jpegQuality = tuning.jpegQuality)
                } else {
                    _state.value = state
                }
                if (activeFps in 1..119) {
                    delay(1000L / activeFps)
                }
            }
    }

    suspend fun shutdown() = mutex.withLock { stopInternal() }
}

/**
 * Передающая сторона передачи звука (Android-хост): микрофон или системный звук
 * по каналу AUDIO.
 */
class AudioStreamHost(
    private val session: EfirSession,
    private val scope: CoroutineScope,
    private val captureFactory: () -> AudioCaptureSource = ::createAudioCaptureSource,
    private val supported: Boolean = audioCaptureSupported(),
) {

    private val mutex = Mutex()
    private val _state = MutableStateFlow(AudioStreamState())
    val state: StateFlow<AudioStreamState> = _state.asStateFlow()

    private var source: AudioCaptureSource? = null
    private var pump: Job? = null
    private var sequence: Long = 0

    fun attach() {
        session.rpc.onRequest<AudioStart> { request -> start(request) }
        session.rpc.onRequest<AudioStop> { stop() }
    }

    private suspend fun start(request: AudioStart): AudioStarted = mutex.withLock {
        if (!supported) {
            _state.value = _state.value.copy(error = "Захват звука не поддерживается на этом устройстве")
            return@withLock AudioStarted(sessionId = "", sampleRate = request.sampleRate, channels = request.channels, codec = request.codec)
        }
        stopInternal()
        val quality = AudioQuality.from(request)
        val capture = captureFactory()
        val actual = capture.start(quality)
        source = capture
        sequence = 0
        val sessionId = MessageIds.new()
        _state.value = AudioStreamState(
            active = true,
            sessionId = sessionId,
            sampleRate = actual.sampleRate,
            channels = actual.channels,
        )
        pump = scope.launch { pumpChunks(capture, sessionId) }
        AudioStarted(
            sessionId = sessionId,
            sampleRate = actual.sampleRate,
            channels = actual.channels,
            codec = actual.codec,
        )
    }

    private suspend fun stop(): AudioStopped = mutex.withLock {
        val id = _state.value.sessionId ?: ""
        stopInternal()
        _state.value = AudioStreamState()
        AudioStopped(sessionId = id)
    }

    private suspend fun stopInternal() {
        pump?.cancel()
        pump = null
        source?.let { runCatching { it.stop() } }
        source = null
        _state.value = _state.value.copy(active = false)
    }

    private suspend fun pumpChunks(capture: AudioCaptureSource, sessionId: String) {
        capture.chunks()
            .catch { error ->
                _state.value = _state.value.copy(active = false, error = error.message ?: "Сбой звука")
            }
            .collect { chunk ->
                val media = MediaFrame.Audio(
                    sessionId = sessionId,
                    sequence = sequence++,
                    timestamp = chunk.timestamp,
                    sampleRate = chunk.sampleRate,
                    channels = chunk.channels,
                    codec = chunk.codec,
                    payload = chunk.payload,
                )
                session.connection.sendBinary(MediaFrameCodec.encode(media))
                _state.value = _state.value.copy(sentChunks = sequence)
            }
    }

    suspend fun shutdown() = mutex.withLock { stopInternal() }
}
