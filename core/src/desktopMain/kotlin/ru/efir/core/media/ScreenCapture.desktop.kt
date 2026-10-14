package ru.efir.core.media

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import ru.efir.core.protocol.nowMillis
import java.awt.BasicStroke
import java.awt.Color
import java.awt.GraphicsEnvironment
import java.awt.MouseInfo
import java.awt.Polygon
import java.awt.Rectangle
import java.awt.RenderingHints
import java.awt.Robot
import java.awt.image.BufferedImage
import java.awt.image.DataBufferInt
import java.io.ByteArrayOutputStream
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.ImageWriter
import javax.imageio.stream.MemoryCacheImageOutputStream

// --- Захват экрана ---------------------------------------------------------

actual fun screenCaptureSupported(): Boolean = !GraphicsEnvironment.isHeadless()

/** Основной экран компьютера: его и показываем, по нему же считаются нажатия. */
internal fun primaryScreenBounds(): Rectangle? = runCatching {
    if (GraphicsEnvironment.isHeadless()) return null
    GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.bounds
}.getOrNull()

actual fun deviceScreenSize(): CaptureSize? =
    primaryScreenBounds()?.takeIf { it.width > 0 && it.height > 0 }?.let { CaptureSize(it.width, it.height) }

/** Экран компьютера меняется реже телефонного, а захват стоит процессора: кадров в секунду не больше этого. */
private const val MAX_FPS = 12

/**
 * Захват основного экрана компьютера: снимок `java.awt.Robot`, уменьшение до
 * запрошенного размера и сжатие в JPEG.
 *
 * Экономно (`docs/BATTERY_RU.md`): снимки идут, только пока поток кто-то читает,
 * не чаще [MAX_FPS] раз в секунду, а кадр, который не отличается от прежнего, не
 * сжимается и не отправляется — неподвижный экран сеть не занимает.
 */
private class DesktopScreenCaptureSource : ScreenCaptureSource {

    override var quality: ScreenQuality = ScreenQuality()
        private set

    @Volatile
    private var liveJpegQuality = 70

    @Volatile
    private var liveFps = MAX_FPS

    private var robot: Robot? = null
    private var area: Rectangle? = null
    private var target = CaptureSize(1, 1)

    override fun setEncoderQuality(jpegQuality: Int, targetFps: Int) {
        liveJpegQuality = jpegQuality.coerceIn(1, 100)
        liveFps = targetFps.coerceIn(1, MAX_FPS)
    }

    override suspend fun start(quality: ScreenQuality): CaptureSize = withContext(Dispatchers.IO) {
        if (!ScreenSharing.showAllowed) throw StreamRefused(StreamErrors.DISABLED)
        val bounds = primaryScreenBounds() ?: throw StreamRefused(StreamErrors.UNAVAILABLE)
        this@DesktopScreenCaptureSource.quality = quality
        liveJpegQuality = quality.jpegQuality
        liveFps = quality.targetFps.coerceIn(1, MAX_FPS)
        robot = Robot(GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice)
        area = bounds
        target = fit(bounds.width, bounds.height, quality.maxWidth, quality.maxHeight)
        ImageIO.setUseCache(false)
        if (!ImageIO.getImageWritersByFormatName("jpeg").hasNext()) {
            throw StreamRefused(StreamErrors.FAILED, "no JPEG writer in this Java runtime")
        }
        target
    }

    override fun frames(): Flow<CapturedScreenFrame> = flow {
        val robot = robot ?: throw StreamRefused(StreamErrors.FAILED, "the capture is not started")
        val area = area ?: throw StreamRefused(StreamErrors.FAILED, "the capture is not started")
        val size = target
        // Два холста по очереди: в одном новый кадр, в другом прежний — для сравнения.
        val canvases = Array(2) { BufferedImage(size.width, size.height, BufferedImage.TYPE_INT_RGB) }
        var index = 0
        var sent = false
        // Кодировщик живёт вместе с потоком кадров и освобождается им же: остановка показа
        // не может забрать его из-под идущего кадра.
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        try {
            while (currentCoroutineContext().isActive) {
                val startedAt = nowMillis()
                // Показ выключили в настройках посреди показа: он заканчивается.
                if (!ScreenSharing.showAllowed) throw StreamRefused(StreamErrors.DISABLED)
                val canvas = canvases[index]
                draw(robot.createScreenCapture(area), canvas, area)
                val pixels = (canvas.raster.dataBuffer as DataBufferInt).data
                val previous = (canvases[1 - index].raster.dataBuffer as DataBufferInt).data
                if (!sent || !pixels.contentEquals(previous)) {
                    emit(
                        CapturedScreenFrame(
                            size = size,
                            mimeType = "image/jpeg",
                            timestamp = startedAt,
                            payload = jpeg(writer, canvas),
                        ),
                    )
                    sent = true
                    index = 1 - index
                }
                val period = 1000L / liveFps.coerceAtLeast(1)
                delay((period - (nowMillis() - startedAt)).coerceAtLeast(10))
            }
        } finally {
            runCatching { writer.dispose() }
        }
    }
        // Отправка медленнее захвата — лишние кадры пропускаются, а не копятся.
        .conflate()
        .flowOn(Dispatchers.IO)

    /** Уменьшает снимок до размера кадра и рисует указатель мыши: в снимок экрана он не попадает. */
    private fun draw(shot: BufferedImage, canvas: BufferedImage, area: Rectangle) {
        val g = canvas.createGraphics()
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            g.drawImage(shot, 0, 0, canvas.width, canvas.height, null)
            val pointer = runCatching { MouseInfo.getPointerInfo()?.location }.getOrNull() ?: return
            if (!area.contains(pointer)) return
            val x = (pointer.x - area.x) * canvas.width / area.width
            val y = (pointer.y - area.y) * canvas.height / area.height
            val arrow = Polygon(
                intArrayOf(x, x, x + 4, x + 7, x + 9, x + 6, x + 11),
                intArrayOf(y, y + 15, y + 11, y + 17, y + 16, y + 10, y + 10),
                7,
            )
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g.color = Color.WHITE
            g.fillPolygon(arrow)
            g.color = Color.BLACK
            g.stroke = BasicStroke(1f)
            g.drawPolygon(arrow)
        } finally {
            g.dispose()
        }
    }

    private fun jpeg(writer: ImageWriter, image: BufferedImage): ByteArray {
        val param = writer.defaultWriteParam.apply {
            compressionMode = ImageWriteParam.MODE_EXPLICIT
            compressionQuality = liveJpegQuality / 100f
        }
        val out = ByteArrayOutputStream(96 * 1024)
        MemoryCacheImageOutputStream(out).use { stream ->
            writer.output = stream
            writer.write(null, IIOImage(image, null, null), param)
        }
        return out.toByteArray()
    }

    override suspend fun updateConfig(quality: ScreenQuality): CaptureSize {
        this.quality = quality
        setEncoderQuality(quality.jpegQuality, quality.targetFps)
        return target
    }

    /** Поток кадров останавливает тот, кто его читает; здесь освобождать нечего. */
    override suspend fun stop() = Unit

    private fun fit(width: Int, height: Int, maxWidth: Int, maxHeight: Int): CaptureSize {
        val limitW = if (maxWidth > 0) maxWidth else width
        val limitH = if (maxHeight > 0) maxHeight else height
        val scale = minOf(limitW.toFloat() / width, limitH.toFloat() / height, 1f)
        return CaptureSize((width * scale).toInt().coerceAtLeast(1), (height * scale).toInt().coerceAtLeast(1))
    }
}

actual fun createScreenCaptureSource(): ScreenCaptureSource = DesktopScreenCaptureSource()
