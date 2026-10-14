package ru.efir.core.media

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Декодер кадров видео-кодека (H.264) для принимающей стороны.
 *
 * Используется, когда хост отдаёт поток в аппаратно закодированном виде
 * (`video/avc`) вместо JPEG. Реализация платформенная:
 * - **Desktop (ПК):** чистый JVM-декодер (JCodec), см. [createVideoFrameDecoder];
 * - **Android:** не требуется (устройство — хост), фабрика возвращает `null`.
 *
 * Декодер не потокобезопасен — кадры следует подавать последовательно
 * (клиент делает это в единственном коллекторе медиа-канала).
 */
/** MIME-тип аппаратно закодированного H.264-потока в бинарных кадрах. */
const val MIME_H264: String = "video/avc"

interface VideoFrameDecoder {

    /**
     * Передаёт кадр конфигурации кодека (CSD: SPS/PPS). Вызывается при получении
     * кадра с `config = true` перед декодированием ключевых кадров.
     */
    fun decodeConfig(csd: ByteArray)

    /**
     * Декодирует один видеокадр (Annex-B NAL-единицы) в [ImageBitmap].
     * Возвращает `null`, если кадр не удалось декодировать (например, пропущен
     * ключевой кадр) — клиент просто пропускает такой кадр.
     */
    fun decodeFrame(frame: ByteArray): ImageBitmap?

    /** Освобождает ресурсы декодера. */
    fun close()
}

/** Поддерживает ли платформа декодирование видео-кодеков (H.264). */
expect fun videoDecodingSupported(): Boolean

/**
 * Создаёт платформенный декодер видео ([VideoFrameDecoder]) или возвращает
 * `null`, если платформа не поддерживает декодирование (например, Android-хост).
 */
expect fun createVideoFrameDecoder(): VideoFrameDecoder?
