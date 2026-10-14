package ru.efir.core.media

import androidx.compose.ui.graphics.ImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.efir.core.net.EfirConnection
import ru.efir.core.net.EfirSession
import ru.efir.core.protocol.ScreenStart

/**
 * Состояние приёма видеопотока экрана на клиенте.
 *
 * @param active ожидается ли поток (запрошен старт).
 * @param width/height размер последнего полученного кадра.
 * @param receivedFrames число декодированных кадров.
 * @param droppedFrames число кадров, пропущенных из-за занятости декодера.
 * @param error последняя ошибка.
 */
data class ScreenStreamClientState(
    val active: Boolean = false,
    val sessionId: String? = null,
    val width: Int = 0,
    val height: Int = 0,
    val receivedFrames: Long = 0,
    val droppedFrames: Long = 0,
    /** Профайлер: фактический FPS приёма за окно. */
    val actualFps: Double = 0.0,
    /** Профайлер: оценка сквозной задержки «захват → декодирование» (мс). */
    val latencyMs: Long = 0,
    /** Профайлер: фактическая пропускная способность (байт/с). */
    val bitrateBps: Long = 0,
    val error: String? = null,
)

/**
 * Принимающая сторона видеопотока (Windows-клиент).
 *
 * Читает бинарные кадры соединения, разбирает их [MediaFrameCodec],
 * декодирует JPEG в [ImageBitmap] и публикует последний кадр для UI.
 *
 * Для низкой задержки при перегрузке предыдущий ещё не обработанный кадр
 * пропускается (счётчик [ScreenStreamClientState.droppedFrames]).
 */
class ScreenStreamClient(
    private val session: EfirSession,
    private val scope: CoroutineScope,
) {

    private val connection: EfirConnection = session.connection

    private val _state = MutableStateFlow(ScreenStreamClientState())
    val state: StateFlow<ScreenStreamClientState> = _state.asStateFlow()

    private val _latestFrame = MutableStateFlow<ImageBitmap?>(null)

    /** Последний декодированный кадр (для отрисовки). */
    val latestFrame: StateFlow<ImageBitmap?> = _latestFrame.asStateFlow()

    private var reader: Job? = null
    private var busy = false
    private val metrics = StreamMetrics()

    /** Запрашивает старт трансляции у удалённой стороны. */
    suspend fun requestStart(
        maxWidth: Int = 1280,
        maxHeight: Int = 720,
        fps: Int = 30,
        bitrate: Int = 4_000_000,
        codec: String = "h264",
    ) {
        _state.value = _state.value.copy(active = true, error = null)
        runCatching {
            session.rpc.call(
                ScreenStart(maxWidth = maxWidth, maxHeight = maxHeight, fps = fps, bitrate = bitrate, codec = codec),
            )
        }.onFailure { error ->
            _state.value = _state.value.copy(active = false, error = error.message ?: "Не удалось запустить поток")
        }
    }

    /** Запускает чтение бинарных кадров. Вызывается после установки соединения. */
    fun start() {
        if (reader != null) return
        reader = scope.launch {
            session.incomingMediaFrames().collect { bytes ->
                onBinaryFrame(bytes)
            }
        }
    }

    private suspend fun onBinaryFrame(bytes: ByteArray) {
        val frame = runCatching { MediaFrameCodec.decode(bytes) }
            .getOrElse { error ->
                _state.value = _state.value.copy(error = error.message ?: "Повреждённый медиа-кадр")
                return
            }
        if (frame.kind != MediaFrameKind.SCREEN || frame !is MediaFrame.Image) return

        if (busy) {
            // Декодер занят — пропускаем кадр, чтобы не копить задержку.
            _state.value = _state.value.copy(droppedFrames = _state.value.droppedFrames + 1)
            return
        }
        busy = true
        try {
            val image = decodeImage(frame.payload)
            if (image != null) {
                metrics.onFrame(frame.payloadSize, frame.timestamp)
                val snap = metrics.snapshot
                _latestFrame.value = image
                _state.value = _state.value.copy(
                    sessionId = frame.sessionId,
                    width = frame.width,
                    height = frame.height,
                    receivedFrames = _state.value.receivedFrames + 1,
                    actualFps = snap.fps,
                    latencyMs = snap.latencyMs,
                    bitrateBps = snap.bitrateBps,
                )
            }
        } finally {
            busy = false
        }
    }

    /** Останавливает чтение кадров. */
    fun stop() {
        reader?.cancel()
        reader = null
        _state.value = ScreenStreamClientState()
        _latestFrame.value = null
    }
}
