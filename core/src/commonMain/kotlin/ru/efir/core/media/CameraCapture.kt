package ru.efir.core.media

import kotlinx.coroutines.flow.Flow
import ru.efir.core.protocol.CameraFacing
import ru.efir.core.protocol.CameraStart

/**
 * Настройки захвата камеры.
 *
 * @param facing фронтальная или тыловая камера.
 * @param width/height целевое разрешение.
 * @param targetFps целевая частота кадров.
 * @param jpegQuality качество JPEG (1..100).
 * @param bitrate целевой битрейт (для будущих видеокодеков).
 */
data class CameraQuality(
    val facing: CameraFacing = CameraFacing.BACK,
    val width: Int = 1280,
    val height: Int = 720,
    val targetFps: Int = 30,
    val jpegQuality: Int = 70,
    val bitrate: Int = 4_000_000,
    val adaptive: Boolean = true,
    /** Использовать аппаратное H.264-кодирование вместо JPEG (если поддерживается). */
    val hardware: Boolean = false,
) {
    init {
        require(targetFps in 1..120) { "targetFps вне диапазона: $targetFps" }
        require(jpegQuality in 1..100) { "jpegQuality вне диапазона: $jpegQuality" }
    }

    companion object {
        /**
         * Строит настройки из RPC-запроса [CameraStart]. Аппаратное кодирование
         * включается, если запрошен кодек H.264.
         */
        fun from(start: CameraStart): CameraQuality = CameraQuality(
            facing = start.facing,
            width = start.width,
            height = start.height,
            targetFps = start.fps.coerceIn(1, 120),
            jpegQuality = start.jpegQuality.coerceIn(1, 100),
            bitrate = start.bitrate,
            adaptive = start.adaptive,
            hardware = start.codec.equals(MIME_H264, ignoreCase = true),
        )
    }
}

/** Один захваченный кадр камеры (готовый к отправке). */
data class CapturedCameraFrame(
    val size: CaptureSize,
    val mimeType: String,
    val timestamp: Long,
    val payload: ByteArray,
    /** Ключевой кадр (для видео-кодеков; для JPEG всегда `true`). */
    val keyframe: Boolean = true,
    /** Кадр конфигурации кодека (CSD/SPS-PPS), не отображается получателем. */
    val config: Boolean = false,
)

/**
 * Источник захвата камеры (платформенный).
 * Android — `Camera2`/`CameraX`; Desktop — не поддерживается.
 */
interface CameraCaptureSource {
    suspend fun start(quality: CameraQuality): CaptureSize
    fun frames(): Flow<CapturedCameraFrame>

    /** Быстрое обновление качества JPEG/FPS без пересоздания сессии (адаптивный битрейт). */
    fun setEncoderQuality(jpegQuality: Int, targetFps: Int) = Unit

    suspend fun stop()
}

/** Поддерживает ли платформа захват камеры. */
expect fun cameraCaptureSupported(): Boolean

/** Создаёт платформенный источник захвата камеры. */
expect fun createCameraCaptureSource(): CameraCaptureSource
