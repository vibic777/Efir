package ru.efir.core.media

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jcodec.codecs.h264.H264Decoder
import org.jcodec.common.model.ColorSpace
import org.jcodec.common.model.Picture
import org.jcodec.scale.Yuv420pToRgb
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import java.nio.ByteBuffer

actual fun videoDecodingSupported(): Boolean = true

actual fun createVideoFrameDecoder(): VideoFrameDecoder? = JCodecH264Decoder()

/**
 * Чисто-JVM декодер H.264 на базе JCodec.
 *
 * Трансформирует декодированные YUV420-кадры в RGB и создаёт Skia-растр
 * ([ImageBitmap]) для отрисовки в Compose. Декодер хранит состояние между
 * кадрами, поэтому кадры нужно подавать строго по порядку.
 */
private class JCodecH264Decoder : VideoFrameDecoder {

    private val decoder = H264Decoder()
    private val transform = Yuv420pToRgb()
    private var csd: ByteArray? = null

    override fun decodeConfig(csd: ByteArray) {
        this.csd = csd.copyOf()
    }

    override fun decodeFrame(frame: ByteArray): ImageBitmap? = runCatching {
        // Для ключевых кадров подмешиваем CSD (SPS/PPS), если хост их прислал
        // отдельно: JCodec разбирает параметры набора из входного потока.
        val data = csd?.let { it + frame } ?: frame
        val picture = decoder.decodeFrame(ByteBuffer.wrap(data), null) ?: return null
        toImageBitmap(picture)
    }.getOrNull()

    private fun toImageBitmap(picture: Picture): ImageBitmap {
        val width = picture.width
        val height = picture.height
        val rgb = Picture.create(width, height, ColorSpace.RGB)
        transform.transform(picture, rgb)

        val plane: ByteArray = rgb.data[0]
        // JCodec RGB-плоскость хранит байты R,G,B. Skia ожидает BGRA (little-endian).
        val bgra = ByteArray(width * height * 4)
        var src = 0
        var dst = 0
        while (src + 2 < plane.size) {
            bgra[dst] = plane[src + 2]     // B
            bgra[dst + 1] = plane[src + 1] // G
            bgra[dst + 2] = plane[src]     // R
            bgra[dst + 3] = 0xFF.toByte()  // A
            src += 3
            dst += 4
        }

        val info = ImageInfo(width, height, ColorType.BGRA_8888, ColorAlphaType.OPAQUE)
        return Image.makeRaster(info, bgra, width * 4).toComposeImageBitmap()
    }

    override fun close() {
        csd = null
    }
}
