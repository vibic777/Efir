package ru.efir.core.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.efir.core.app.AppLog
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.AudioStarted
import ru.efir.core.protocol.CameraStarted
import ru.efir.core.protocol.ErrorMessage
import ru.efir.core.protocol.Message
import ru.efir.core.protocol.ScreenEnded
import ru.efir.core.protocol.ScreenStarted
import ru.efir.core.protocol.AudioStart
import ru.efir.core.protocol.AudioStop
import ru.efir.core.protocol.CameraFacing
import ru.efir.core.protocol.CameraStart
import ru.efir.core.protocol.CameraStop
import ru.efir.core.protocol.ScreenStart
import ru.efir.core.protocol.ScreenStop

/** Состояние приёма видеопотока камеры. */
data class CameraStreamClientState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val receivedFrames: Long = 0,
    /** Профайлер: фактический FPS приёма за окно. */
    val actualFps: Double = 0.0,
    /** Профайлер: оценка сквозной задержки (мс). */
    val latencyMs: Long = 0,
    val error: String? = null,
)

/** Состояние приёма звука. */
data class AudioStreamClientState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val sampleRate: Int = 0,
    val channels: Int = 0,
    val receivedChunks: Long = 0,
    val error: String? = null,
)

/**
 * Единый клиент медиапотоков (Windows): подписывается на бинарный медиа-канал
 * **одним** коллектором и маршрутизирует кадры по типу:
 *  - `SCREEN` → декодируется в [latestScreen];
 *  - `CAMERA` → декодируется в [latestCamera];
 *  - `AUDIO`  → передаётся [AudioPlayer].
 *
 * Единая подписка обязательна: канал доставляет каждый кадр только одному
 * подписчику, поэтому отдельные клиенты для экрана/камеры/звука конкурировали бы.
 */
class MediaStreamClient(
    private val session: EfirSession,
    private val scope: CoroutineScope,
    private val audioPlayer: AudioPlayer = createAudioPlayer(),
) {

    private val _screenState = MutableStateFlow(ScreenStreamClientState())
    val screenState: StateFlow<ScreenStreamClientState> = _screenState.asStateFlow()

    private val _cameraState = MutableStateFlow(CameraStreamClientState())
    val cameraState: StateFlow<CameraStreamClientState> = _cameraState.asStateFlow()

    private val _audioState = MutableStateFlow(AudioStreamClientState())
    val audioState: StateFlow<AudioStreamClientState> = _audioState.asStateFlow()

    private val _latestScreen = MutableStateFlow<ImageBitmap?>(null)

    /** Последний кадр экрана. */
    val latestScreen: StateFlow<ImageBitmap?> = _latestScreen.asStateFlow()

    private val _latestCamera = MutableStateFlow<ImageBitmap?>(null)

    /** Последний кадр камеры. */
    val latestCamera: StateFlow<ImageBitmap?> = _latestCamera.asStateFlow()

    private var reader: Job? = null
    private var screenBusy = false
    private var cameraBusy = false
    private val screenMetrics = StreamMetrics()
    private val cameraMetrics = StreamMetrics()
    private var screenDecoderInitialized = false
    private var cameraDecoderInitialized = false
    private var screenVideoDecoderInstance: VideoFrameDecoder? = null
    private var cameraVideoDecoderInstance: VideoFrameDecoder? = null

    /** Запускает единственную подписку на медиа-канал. */
    fun start() {
        if (reader != null) return
        reader = scope.launch {
            session.incomingMediaFrames().collect { bytes -> onFrame(bytes) }
        }
        // Показывающая сторона сама закончила показ: убираем картинку и говорим почему.
        session.rpc.onEvent<ScreenEnded> { event ->
            AppLog.i("screen", "the other device ended showing its screen: ${event.reason}")
            _screenState.value = ScreenStreamClientState(error = event.reason.ifBlank { StreamErrors.STOPPED })
            _latestScreen.value = null
        }
    }

    /**
     * Что не так с ответом на просьбу начать поток, или `null`, если поток пошёл.
     * Возвращается код из [StreamErrors] либо текст, который прислала другая сторона.
     */
    private fun startFailure(reply: Result<Message>, unavailable: String = StreamErrors.UNAVAILABLE, emptySession: (Message) -> Boolean): String? {
        val message = reply.getOrElse { error ->
            return if (error is TimeoutCancellationException) StreamErrors.NO_ANSWER else error.message ?: StreamErrors.FAILED
        }
        return when {
            message is ErrorMessage -> if (message.code.startsWith("screen_")) message.code else message.message
            emptySession(message) -> unavailable
            else -> null
        }
    }

    private suspend fun onFrame(bytes: ByteArray) {
        val frame = runCatching { MediaFrameCodec.decode(bytes) }.getOrNull() ?: return
        when (frame.kind) {
            MediaFrameKind.SCREEN -> onImageFrame(frame as? MediaFrame.Image ?: return, isScreen = true)
            MediaFrameKind.CAMERA -> onImageFrame(frame as? MediaFrame.Image ?: return, isScreen = false)
            MediaFrameKind.AUDIO -> onAudioFrame(frame as? MediaFrame.Audio ?: return)
        }
    }

    private suspend fun onImageFrame(frame: MediaFrame.Image, isScreen: Boolean) {
        val isVideo = frame.mimeType == MIME_H264
        if (isScreen) {
            // Показ не просили или он уже закончен: запоздавший кадр картинку не возвращает.
            if (!_screenState.value.active) return
            if (screenBusy) {
                _screenState.value = _screenState.value.copy(droppedFrames = _screenState.value.droppedFrames + 1)
                return
            }
            screenBusy = true
            try {
                val decoder = if (isVideo) screenVideoDecoder() else null
                // Кадр конфигурации кодека (SPS/PPS) не отображается, только накапливается.
                if (isVideo && frame.config) {
                    decoder?.decodeConfig(frame.payload)
                    return
                }
                val image = decodePayload(frame, decoder) ?: return
                // Пока кадр разбирался, показ могли остановить.
                if (!_screenState.value.active) return
                screenMetrics.onFrame(frame.payloadSize, frame.timestamp)
                val snap = screenMetrics.snapshot
                _latestScreen.value = image
                _screenState.value = _screenState.value.copy(
                    sessionId = frame.sessionId,
                    width = frame.width,
                    height = frame.height,
                    receivedFrames = _screenState.value.receivedFrames + 1,
                    actualFps = snap.fps,
                    latencyMs = snap.latencyMs,
                    bitrateBps = snap.bitrateBps,
                )
                if (!_screenState.value.active) _latestScreen.value = null
            } finally {
                screenBusy = false
            }
        } else {
            if (cameraBusy) return
            cameraBusy = true
            try {
                val decoder = if (isVideo) cameraVideoDecoder() else null
                if (isVideo && frame.config) {
                    decoder?.decodeConfig(frame.payload)
                    return
                }
                val image = decodePayload(frame, decoder) ?: return
                cameraMetrics.onFrame(frame.payloadSize, frame.timestamp)
                val snap = cameraMetrics.snapshot
                _latestCamera.value = image
                _cameraState.value = _cameraState.value.copy(
                    sessionId = frame.sessionId,
                    width = frame.width,
                    height = frame.height,
                    receivedFrames = _cameraState.value.receivedFrames + 1,
                    actualFps = snap.fps,
                    latencyMs = snap.latencyMs,
                )
            } finally {
                cameraBusy = false
            }
        }
    }

    /**
     * Декодирует полезную нагрузку кадра: JPEG через [decodeImage], либо H.264
     * через платформенный [VideoFrameDecoder]. Возвращает `null`, если декодер
     * недоступен или кадр не декодировался.
     */
    private fun decodePayload(frame: MediaFrame.Image, decoder: VideoFrameDecoder?): ImageBitmap? {
        return if (frame.mimeType == MIME_H264) {
            decoder?.decodeFrame(frame.payload)
        } else {
            decodeImage(frame.payload)
        }
    }

    /** Ленивая инициализация H.264-декодера экрана (если платформа поддерживает). */
    private fun screenVideoDecoder(): VideoFrameDecoder? {
        if (screenDecoderInitialized) return screenVideoDecoderInstance
        screenDecoderInitialized = true
        screenVideoDecoderInstance = createVideoFrameDecoder()
        return screenVideoDecoderInstance
    }

    /** Ленивая инициализация H.264-декодера камеры. */
    private fun cameraVideoDecoder(): VideoFrameDecoder? {
        if (cameraDecoderInitialized) return cameraVideoDecoderInstance
        cameraDecoderInitialized = true
        cameraVideoDecoderInstance = createVideoFrameDecoder()
        return cameraVideoDecoderInstance
    }

    private fun onAudioFrame(frame: MediaFrame.Audio) {
        runCatching {
            audioPlayer.play(
                CapturedAudioChunk(
                    sampleRate = frame.sampleRate,
                    channels = frame.channels,
                    codec = frame.codec,
                    timestamp = frame.timestamp,
                    payload = frame.payload,
                ),
            )
        }
        _audioState.value = _audioState.value.copy(
            sessionId = frame.sessionId,
            sampleRate = frame.sampleRate,
            channels = frame.channels,
            receivedChunks = _audioState.value.receivedChunks + 1,
        )
    }

    // --- Управление (RPC) ------------------------------------------------------

    /**
     * Запрашивает старт трансляции экрана.
     *
     * @param hardware использовать аппаратное H.264-кодирование (при поддержке хоста);
     *   иначе — совместимый JPEG-поток.
     */
    suspend fun startScreen(
        maxWidth: Int = 1280,
        maxHeight: Int = 720,
        fps: Int = 30,
        bitrate: Int = 4_000_000,
        hardware: Boolean = false,
    ) {
        _screenState.value = _screenState.value.copy(active = true, error = null)
        val codec = if (hardware && videoDecodingSupported()) MIME_H264 else "jpeg"
        // Ждём дольше обычного: на телефоне человеку нужно успеть разрешить показ.
        val reply = runCatching {
            session.rpc.call(ScreenStart(maxWidth, maxHeight, bitrate, fps, codec = codec), timeoutMs = StreamErrors.START_TIMEOUT_MS)
        }
        val failure = startFailure(reply) { it is ScreenStarted && it.sessionId.isEmpty() }
        if (failure != null) {
            AppLog.w("screen", "the other device did not show its screen: $failure")
            _screenState.value = ScreenStreamClientState(error = failure)
            _latestScreen.value = null
        } else {
            AppLog.i("screen", "the other device shows its screen")
        }
    }

    /** Останавливает трансляцию экрана. */
    suspend fun stopScreen() {
        runCatching { session.rpc.call(ScreenStop) }
        _screenState.value = ScreenStreamClientState()
        _latestScreen.value = null
    }

    /**
     * Запрашивает старт камеры.
     *
     * @param hardware использовать аппаратное H.264-кодирование (при поддержке хоста).
     */
    suspend fun startCamera(
        facing: CameraFacing = CameraFacing.BACK,
        width: Int = 1280,
        height: Int = 720,
        fps: Int = 30,
        hardware: Boolean = false,
    ) {
        _cameraState.value = _cameraState.value.copy(active = true, error = null)
        val codec = if (hardware && videoDecodingSupported()) MIME_H264 else "jpeg"
        val reply = runCatching { session.rpc.call(CameraStart(facing, width, height, fps, codec = codec)) }
        startFailure(reply, StreamErrors.MEDIA_UNAVAILABLE) { it is CameraStarted && it.sessionId.isEmpty() }?.let { failure ->
            AppLog.w("media", "the other device did not start its camera: $failure")
            _cameraState.value = CameraStreamClientState(error = failure)
            _latestCamera.value = null
        }
    }

    /** Останавливает камеру. */
    suspend fun stopCamera() {
        runCatching { session.rpc.call(CameraStop) }
        _cameraState.value = CameraStreamClientState()
        _latestCamera.value = null
    }

    /** Запрашивает старт передачи звука. */
    suspend fun startAudio(request: AudioStart = AudioStart()) {
        _audioState.value = _audioState.value.copy(active = true, error = null)
        val reply = runCatching { session.rpc.call(request) }
        startFailure(reply, StreamErrors.MEDIA_UNAVAILABLE) { it is AudioStarted && it.sessionId.isEmpty() }?.let { failure ->
            AppLog.w("media", "the other device did not start its sound: $failure")
            _audioState.value = AudioStreamClientState(error = failure)
        }
    }

    /** Останавливает передачу звука. */
    suspend fun stopAudio() {
        runCatching { session.rpc.call(AudioStop) }
        audioPlayer.stop()
        _audioState.value = AudioStreamClientState()
    }

    /** Полная остановка приёма. */
    fun stop() {
        reader?.cancel()
        reader = null
        audioPlayer.stop()
        runCatching { screenVideoDecoderInstance?.close() }
        runCatching { cameraVideoDecoderInstance?.close() }
        screenVideoDecoderInstance = null
        cameraVideoDecoderInstance = null
        screenDecoderInitialized = false
        cameraDecoderInitialized = false
        _screenState.value = ScreenStreamClientState()
        _cameraState.value = CameraStreamClientState()
        _audioState.value = AudioStreamClientState()
        _latestScreen.value = null
        _latestCamera.value = null
    }
}
