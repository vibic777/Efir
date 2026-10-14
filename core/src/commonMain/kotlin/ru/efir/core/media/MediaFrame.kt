package ru.efir.core.media

/**
 * Тип медиа-кадра. Определяет смысл бинарного потока в соответствующем
 * логическом канале [ru.efir.core.protocol.ProtocolChannel].
 */
enum class MediaFrameKind {
    SCREEN,
    CAMERA,
    AUDIO,
}

/**
 * Медиа-кадр, передаваемый по бинарному каналу (SCREEN/CAMERA/AUDIO).
 *
 * Формат на проводе не зависит от платформы: заголовок с метаданными
 * (поток, порядковый номер, метка времени, ключевой кадр и размеры)
 * и сырая полезная нагрузка (кодек-специфичные байты).
 *
 * Полезная нагрузка умышленно представлена [ByteArray], поэтому
 * автоматическое сравнение в data-классах отключено (см. [MediaFrameCodec]).
 */
sealed interface MediaFrame {

    /** Тип кадра. */
    val kind: MediaFrameKind

    /** Идентификатор медиа-сессии (выдаётся при старте потока). */
    val sessionId: String

    /** Порядковый номер кадра в потоке (начинается с 0). */
    val sequence: Long

    /** Метка времени создания кадра (epoch millis). */
    val timestamp: Long

    /** Является ли кадр ключевым (для видео-кодеков). */
    val keyframe: Boolean

    /** Размер полезной нагрузки в байтах. */
    val payloadSize: Int get() = payload.size

    /** Сырая полезная нагрузка кадра. */
    val payload: ByteArray

    /**
     * Изображение-кадр (экран или камера).
     *
     * @param mimeType MIME-тип полезной нагрузки (например, `image/jpeg`).
     */
    data class Image(
        override val kind: MediaFrameKind,
        override val sessionId: String,
        override val sequence: Long,
        override val timestamp: Long,
        override val keyframe: Boolean,
        val width: Int,
        val height: Int,
        val mimeType: String,
        /**
         * Является ли кадр кадром конфигурации кодека (CSD/SPS-PPS) для
         * видео-потоков (`video/avc`). Для изображений всегда `false`.
         */
        val config: Boolean = false,
        override val payload: ByteArray,
    ) : MediaFrame {
        init {
            require(kind == MediaFrameKind.SCREEN || kind == MediaFrameKind.CAMERA) {
                "MediaFrame.Image поддерживает только SCREEN/CAMERA, получено $kind"
            }
        }
    }

    /**
     * Кадр звука (микрофон или системный звук).
     *
     * @param codec кодек полезной нагрузки (например, `pcm16`, `aac`).
     */
    data class Audio(
        override val sessionId: String,
        override val sequence: Long,
        override val timestamp: Long,
        val sampleRate: Int,
        val channels: Int,
        val codec: String,
        override val payload: ByteArray,
    ) : MediaFrame {
        override val kind: MediaFrameKind get() = MediaFrameKind.AUDIO
        override val keyframe: Boolean get() = true
    }
}
