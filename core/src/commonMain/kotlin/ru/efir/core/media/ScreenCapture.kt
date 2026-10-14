package ru.efir.core.media

import kotlinx.coroutines.flow.Flow
import ru.efir.core.protocol.ScreenStart

/**
 * Настройки качества захвата/трансляции экрана.
 *
 * @param maxWidth максимальная ширина кадра (с сохранением пропорций, 0 — без ограничения).
 * @param maxHeight максимальная высота кадра (0 — без ограничения).
 * @param targetFps целевая частота кадров.
 * @param jpegQuality качество JPEG-кодирования кадра (1..100).
 * @param bitrate целевой битрейт (используется будущими видеокодеками).
 */
data class ScreenQuality(
    val maxWidth: Int = 1280,
    val maxHeight: Int = 720,
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
        /** Строит настройки из RPC-запроса [ScreenStart]. */
        fun from(start: ScreenStart): ScreenQuality = ScreenQuality(
            maxWidth = start.maxWidth,
            maxHeight = start.maxHeight,
            targetFps = start.fps.coerceIn(1, 120),
            jpegQuality = start.jpegQuality.coerceIn(1, 100),
            bitrate = start.bitrate,
            hardware = start.codec.equals(MIME_H264, ignoreCase = true),
        )
    }
}

/** Фактический размер захваченного кадра. */
data class CaptureSize(
    val width: Int,
    val height: Int,
)

/**
 * Один захваченный кадр экрана (готовый к отправке по SCREEN-каналу).
 *
 * @param mimeType MIME-тип полезной нагрузки (обычно `image/jpeg`).
 * @param timestamp время захвата (epoch millis).
 */
data class CapturedScreenFrame(
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
 * Источник захвата экрана (платформенный).
 *
 * Android: обёртка над `MediaProjection` + `VirtualDisplay` + `ImageReader`.
 * Desktop: не поддерживается (возвращает ошибку/пустой поток).
 */
interface ScreenCaptureSource {

    /** Целевые настройки, заданные при последнем [start]/[updateConfig]. */
    val quality: ScreenQuality

    /** Запускает захват с указанными настройками и возвращает фактический размер. */
    suspend fun start(quality: ScreenQuality): CaptureSize

    /** Поток захваченных кадров. Активен после [start]. */
    fun frames(): Flow<CapturedScreenFrame>

    /**
     * Быстрое обновление только «лёгких» параметров кодирования (качество JPEG,
     * целевой FPS) без пересоздания виртуального дисплея. Используется адаптивным
     * регулятором битрейта в горячем пути отправки кадров.
     */
    fun setEncoderQuality(jpegQuality: Int, targetFps: Int) = Unit

    /**
     * Меняет настройки «на лету» (например, после смены ориентации/разрешения).
     * Возвращает новый фактический размер кадра.
     */
    suspend fun updateConfig(quality: ScreenQuality): CaptureSize

    /** Останавливает захват и освобождает ресурсы. */
    suspend fun stop()
}

/** Поддерживает ли текущая платформа захват экрана. */
expect fun screenCaptureSupported(): Boolean

/**
 * Физический размер экрана устройства в пикселях (для преобразования
 * нормализованных координат ввода). `null`, если недоступен (Desktop).
 */
expect fun deviceScreenSize(): CaptureSize?

/**
 * Создаёт платформенный источник захвата экрана.
 * На платформах без поддержки возвращается «пустой» источник, который
 * выбрасывает [UnsupportedOperationException] при [ScreenCaptureSource.start].
 */
expect fun createScreenCaptureSource(): ScreenCaptureSource
