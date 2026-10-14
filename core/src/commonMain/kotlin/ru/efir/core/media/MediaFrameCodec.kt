package ru.efir.core.media

/**
 * Кодек бинарных медиа-кадров [MediaFrame].
 *
 * Формат кадра (big-endian):
 * ```
 * magic        : 2 байта  (0x4D 0x46 — "MF")
 * version      : 1 байт
 * kind         : 1 байт   (0=SCREEN, 1=CAMERA, 2=AUDIO)
 * flags        : 1 байт   (bit0 — keyframe, bit1 — config/CSD для видео)
 * sessionIdLen : 1 байт
 * sessionId    : sessionIdLen байт (UTF-8)
 * sequence     : 8 байт   (Long)
 * timestamp    : 8 байт   (Long)
 * a            : 4 байта  (Image: width,  Audio: sampleRate)
 * b            : 4 байта  (Image: height, Audio: channels)
 * strLen       : 2 байта  (длина строки)
 * str          : strLen байт (Image: mimeType, Audio: codec)
 * payload      : остаток файла
 * ```
 */
object MediaFrameCodec {

    const val VERSION: Int = 1

    private const val MAGIC_0 = 0x4D // 'M'
    private const val MAGIC_1 = 0x46 // 'F'

    private const val FIXED_HEADER =
        2 + 1 + 1 + 1 + 1 + 8 + 8 + 4 + 4 + 2 // без sessionId и str

    /** Ошибка разбора медиа-кадра. */
    class MediaFrameFormatException(message: String) : Exception(message)

    /** Сериализует кадр в байты. */
    fun encode(frame: MediaFrame): ByteArray {
        val session = frame.sessionId.encodeToByteArray()
        require(session.size <= 255) { "sessionId слишком длинный (${session.size} > 255)" }

        val (a, b, str) = frame.params()
        val strBytes = str.encodeToByteArray()
        require(strBytes.size <= 0xFFFF) { "Строковое поле слишком длинное (${strBytes.size} > 65535)" }

        val size = FIXED_HEADER + session.size + strBytes.size + frame.payload.size
        val out = ByteArray(size)
        var pos = 0

        out[pos++] = MAGIC_0.toByte()
        out[pos++] = MAGIC_1.toByte()
        out[pos++] = VERSION.toByte()
        out[pos++] = frame.kind.ordinal.toByte()
        var flags = 0
        if (frame.keyframe) flags = flags or 0x01
        if (frame is MediaFrame.Image && frame.config) flags = flags or 0x02
        out[pos++] = flags.toByte()
        out[pos++] = session.size.toByte()
        session.copyInto(out, pos); pos += session.size
        putLong(out, pos, frame.sequence); pos += 8
        putLong(out, pos, frame.timestamp); pos += 8
        putInt(out, pos, a); pos += 4
        putInt(out, pos, b); pos += 4
        putShort(out, pos, strBytes.size); pos += 2
        strBytes.copyInto(out, pos); pos += strBytes.size
        frame.payload.copyInto(out, pos)

        return out
    }

    /** Разбирает кадр из байтов. */
    fun decode(bytes: ByteArray): MediaFrame {
        require(bytes.size >= FIXED_HEADER) { "Кадр слишком короткий: ${bytes.size}" }
        var pos = 0

        if (bytes[pos].toInt() and 0xFF != MAGIC_0 || bytes[pos + 1].toInt() and 0xFF != MAGIC_1) {
            throw MediaFrameFormatException("Неверная сигнатура медиа-кадра")
        }
        pos += 2

        val version = bytes[pos++].toInt() and 0xFF
        if (version != VERSION) {
            throw MediaFrameFormatException("Неподдерживаемая версия медиа-кадра: $version")
        }

        val kindOrdinal = bytes[pos++].toInt() and 0xFF
        val kind = MediaFrameKind.entries.getOrNull(kindOrdinal)
            ?: throw MediaFrameFormatException("Неизвестный тип кадра: $kindOrdinal")

        val flags = bytes[pos++].toInt() and 0xFF
        val keyframe = flags and 0x01 != 0
        val config = flags and 0x02 != 0

        val sessionLen = bytes[pos++].toInt() and 0xFF
        if (pos + sessionLen > bytes.size) throw MediaFrameFormatException("sessionId выходит за границы")
        val sessionId = bytes.decodeToString(pos, pos + sessionLen); pos += sessionLen

        if (pos + 8 + 8 + 4 + 4 + 2 > bytes.size) throw MediaFrameFormatException("Обрезанный заголовок")
        val sequence = getLong(bytes, pos); pos += 8
        val timestamp = getLong(bytes, pos); pos += 8
        val a = getInt(bytes, pos); pos += 4
        val b = getInt(bytes, pos); pos += 4
        val strLen = getShort(bytes, pos); pos += 2

        if (pos + strLen > bytes.size) throw MediaFrameFormatException("Строковое поле выходит за границы")
        val str = bytes.decodeToString(pos, pos + strLen); pos += strLen

        val payload = bytes.copyOfRange(pos, bytes.size)

        return when (kind) {
            MediaFrameKind.SCREEN, MediaFrameKind.CAMERA -> MediaFrame.Image(
                kind = kind,
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                keyframe = keyframe,
                width = a,
                height = b,
                mimeType = str,
                config = config,
                payload = payload,
            )

            MediaFrameKind.AUDIO -> MediaFrame.Audio(
                sessionId = sessionId,
                sequence = sequence,
                timestamp = timestamp,
                sampleRate = a,
                channels = b,
                codec = str,
                payload = payload,
            )
        }
    }

    /** Возвращает тройку (a, b, str) для конкретного типа кадра. */
    private fun MediaFrame.params(): Triple<Int, Int, String> = when (this) {
        is MediaFrame.Image -> Triple(width, height, mimeType)
        is MediaFrame.Audio -> Triple(sampleRate, channels, codec)
    }

    // --- big-endian примитивы -------------------------------------------------

    private fun putLong(out: ByteArray, offset: Int, value: Long) {
        for (i in 0 until 8) {
            out[offset + i] = (value ushr (56 - i * 8)).toByte()
        }
    }

    private fun putInt(out: ByteArray, offset: Int, value: Int) {
        for (i in 0 until 4) {
            out[offset + i] = (value ushr (24 - i * 8)).toByte()
        }
    }

    private fun putShort(out: ByteArray, offset: Int, value: Int) {
        out[offset] = (value ushr 8).toByte()
        out[offset + 1] = value.toByte()
    }

    private fun getLong(bytes: ByteArray, offset: Int): Long {
        var result = 0L
        for (i in 0 until 8) {
            result = (result shl 8) or (bytes[offset + i].toLong() and 0xFF)
        }
        return result
    }

    private fun getInt(bytes: ByteArray, offset: Int): Int {
        var result = 0
        for (i in 0 until 4) {
            result = (result shl 8) or (bytes[offset + i].toInt() and 0xFF)
        }
        return result
    }

    private fun getShort(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 8) or (bytes[offset + 1].toInt() and 0xFF)
    }
}
