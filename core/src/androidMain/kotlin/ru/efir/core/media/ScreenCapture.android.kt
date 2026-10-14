package ru.efir.core.media

import android.content.Context
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import ru.efir.core.app.AppLog
import ru.efir.core.protocol.nowMillis
import java.io.ByteArrayOutputStream

actual fun screenCaptureSupported(): Boolean = true

actual fun deviceScreenSize(): CaptureSize? {
    val metrics = Resources.getSystem().displayMetrics
    if (metrics.widthPixels <= 0 || metrics.heightPixels <= 0) return null
    return CaptureSize(metrics.widthPixels, metrics.heightPixels)
}

/**
 * Android-источник захвата экрана.
 *
 * Использует выданное ранее разрешение [EfirAndroidEnvironment.projectionPermission].
 * Виртуальный дисплей направляет кадры в `ImageReader` (RGBA_8888), затем
 * кадр масштабируется до целевого размера и сжимается в JPEG.
 */
private class AndroidScreenCaptureSource(
    private val context: Context,
) : ScreenCaptureSource {

    override var quality: ScreenQuality = ScreenQuality()
        private set

    private var projection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null

    /** Аппаратный H.264-энкодер (активен только в режиме `hardware`). */
    private var encoder: AndroidH264Encoder? = null
    private var hardwareActive: Boolean = false

    private var density: Int = Resources.getSystem().displayMetrics.densityDpi

    /** Что сделать, когда показ остановили в системе: задаёт поток кадров, пока его читают. */
    @Volatile
    private var onProjectionStopped: (() -> Unit)? = null

    @Volatile
    private var liveJpegQuality: Int = 70

    override fun setEncoderQuality(jpegQuality: Int, targetFps: Int) {
        liveJpegQuality = jpegQuality.coerceIn(1, 100)
        // В аппаратном режиме качество передаём целевому битрейту (JPEG-качество
        // напрямую неприменимо): грубо пересчитываем из процента качества.
        if (hardwareActive) {
            val bps = (quality.bitrate * (liveJpegQuality / 70.0)).toInt()
            encoder?.setBitrate(bps)
        }
    }

    override suspend fun start(quality: ScreenQuality): CaptureSize {
        this.quality = quality
        liveJpegQuality = quality.jpegQuality

        // Разрешения ещё нет — телефон спрашивает человека сам и ждёт ответа:
        // заранее идти в «Настройки» ради показа экрана не нужно.
        val permission = EfirAndroidEnvironment.awaitProjection()

        // Сервис типа «показ экрана» обязан работать до получения MediaProjection (Android 10+,
        // на 14+ без него SecurityException). Запуск сервиса не мгновенный — ждём, пока он встанет.
        EfirAndroidEnvironment.requestForegroundService?.invoke()
        if (!awaitProjectionService()) {
            throw StreamRefused(StreamErrors.FAILED, "the foreground service for screen sharing did not start")
        }

        val manager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val created: MediaProjection? = try {
            manager.getMediaProjection(permission.resultCode, permission.data)
        } catch (e: SecurityException) {
            // Разрешение одноразовое (Android 14+) и уже использовано: в следующий раз спросим заново.
            EfirAndroidEnvironment.projectionPermission = null
            throw StreamRefused(StreamErrors.PERMISSION, e.message)
        }
        val granted = created ?: throw StreamRefused(StreamErrors.FAILED, "MediaProjection was not created")
        projection = granted

        val thread = HandlerThread("efir-screen-capture").also { it.start() }
        handlerThread = thread
        val h = Handler(thread.looper)
        handler = h

        // Android 14+ не даёт начать захват без этого обработчика. Он же сообщает, что показ
        // остановили в системе (кнопка в шторке, блокировка экрана): тогда поток кадров закрывается.
        granted.registerCallback(
            object : MediaProjection.Callback() {
                override fun onStop() {
                    onProjectionStopped?.invoke()
                }
            },
            h,
        )

        val captureSize = targetCaptureSize()

        // Аппаратный H.264 (opt-in): виртуальный дисплей пишет прямо в Surface кодера.
        if (quality.hardware && h264EncodingSupported()) {
            val h264 = AndroidH264Encoder(
                width = captureSize.width,
                height = captureSize.height,
                bitrate = quality.bitrate,
                frameRate = quality.targetFps,
            )
            if (h264.start()) {
                encoder = h264
                hardwareActive = true
                virtualDisplay = projection?.createVirtualDisplay(
                    "efir-screen",
                    captureSize.width,
                    captureSize.height,
                    density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    h264.surface,
                    null,
                    h,
                )
                return captureSize
            }
        }

        // Программный JPEG-путь (по умолчанию).
        hardwareActive = false
        val reader = ImageReader.newInstance(
            captureSize.width,
            captureSize.height,
            PixelFormat.RGBA_8888,
            2,
        )
        imageReader = reader

        virtualDisplay = projection?.createVirtualDisplay(
            "efir-screen",
            captureSize.width,
            captureSize.height,
            density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader.surface,
            null,
            h,
        )

        return captureSize
    }

    override fun frames(): Flow<CapturedScreenFrame> {
        // Аппаратный H.264-путь: кадры отдаёт сам кодек через drain().
        encoder?.let { return it.drain() }
        return jpegFrames()
    }

    /** Сервис встаёт за доли секунды; дольше пяти секунд ждать нечего. */
    private suspend fun awaitProjectionService(): Boolean {
        val ready = EfirAndroidEnvironment.projectionServiceReady ?: return true
        repeat(100) {
            if (ready()) return true
            delay(50)
        }
        return ready()
    }

    private fun jpegFrames(): Flow<CapturedScreenFrame> = callbackFlow {
        val reader = imageReader
            ?: throw IllegalStateException("Захват не запущен")
        val q = quality
        onProjectionStopped = {
            // Остановлено в системе: прежнее разрешение больше не действует.
            EfirAndroidEnvironment.projectionPermission = null
            close(StreamRefused(StreamErrors.STOPPED))
        }

        reader.setOnImageAvailableListener({ r ->
            // Показ могли остановить посреди кадра: закрытый ImageReader бросает исключение,
            // а оно в чужом потоке уронило бы приложение.
            val image = runCatching { r.acquireLatestImage() }.getOrNull() ?: return@setOnImageAvailableListener
            try {
                val plane = image.planes[0]
                val pixelStride = plane.pixelStride
                val rowStride = plane.rowStride
                val rowPadding = rowStride - pixelStride * image.width
                val paddedWidth = image.width + rowPadding / pixelStride

                val bitmap = Bitmap.createBitmap(paddedWidth, image.height, Bitmap.Config.ARGB_8888)
                bitmap.copyPixelsFromBuffer(plane.buffer)

                // Обрезаем padding по правому краю.
                val cropped = if (paddedWidth != image.width) {
                    Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
                } else {
                    bitmap
                }

                val scaled = scaleToFit(cropped, q.maxWidth, q.maxHeight)
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, liveJpegQuality, out)

                trySend(
                    CapturedScreenFrame(
                        size = CaptureSize(scaled.width, scaled.height),
                        mimeType = "image/jpeg",
                        timestamp = nowMillis(),
                        payload = out.toByteArray(),
                    ),
                )

                if (scaled !== cropped) scaled.recycle()
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
            } catch (e: Throwable) {
                AppLog.w("screen", "a screen frame was lost", e)
            } finally {
                runCatching { image.close() }
            }
        }, handler)

        awaitClose {
            onProjectionStopped = null
            reader.setOnImageAvailableListener(null, null)
        }
    }

    override suspend fun updateConfig(quality: ScreenQuality): CaptureSize {
        this.quality = quality
        val target = targetCaptureSize()
        virtualDisplay?.resize(target.width, target.height, density)
        encoder?.requestKeyFrame()
        return target
    }

    override suspend fun stop() {
        onProjectionStopped = null
        runCatching { virtualDisplay?.release() }
        virtualDisplay = null
        runCatching { encoder?.stop() }
        encoder = null
        hardwareActive = false
        runCatching { imageReader?.close() }
        imageReader = null
        runCatching { projection?.stop() }
        projection = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
        // Android 14+: разрешение на захват одноразовое — после остановки его
        // нужно запросить заново, иначе следующий запуск упадёт с SecurityException.
        if (android.os.Build.VERSION.SDK_INT >= 34) EfirAndroidEnvironment.projectionPermission = null
        EfirAndroidEnvironment.stopForegroundService?.invoke()
    }

    /** Целевой размер виртуального дисплея с сохранением пропорций экрана. */
    private fun targetCaptureSize(): CaptureSize {
        val screen = deviceScreenSize() ?: CaptureSize(quality.maxWidth, quality.maxHeight)
        return fitWithin(screen.width, screen.height, quality.maxWidth, quality.maxHeight)
    }
}

/** Масштабирует (при необходимости уменьшает) размер до вписывания в max-границы. */
internal fun fitWithin(width: Int, height: Int, maxWidth: Int, maxHeight: Int): CaptureSize {
    if (width <= 0 || height <= 0) return CaptureSize(maxWidth.coerceAtLeast(1), maxHeight.coerceAtLeast(1))
    val limitW = if (maxWidth > 0) maxWidth else width
    val limitH = if (maxHeight > 0) maxHeight else height
    val scale = minOf(limitW.toFloat() / width, limitH.toFloat() / height, 1f)
    return CaptureSize(
        width = (width * scale).toInt().coerceAtLeast(1),
        height = (height * scale).toInt().coerceAtLeast(1),
    )
}

/** Масштабирует Bitmap до вписывания; возвращает исходный, если уменьшение не нужно. */
private fun scaleToFit(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
    val target = fitWithin(bitmap.width, bitmap.height, maxWidth, maxHeight)
    if (target.width == bitmap.width && target.height == bitmap.height) return bitmap
    return Bitmap.createScaledBitmap(bitmap, target.width, target.height, true)
}

actual fun createScreenCaptureSource(): ScreenCaptureSource {
    val context = EfirAndroidEnvironment.appContext
        ?: throw IllegalStateException("EfirAndroidEnvironment.appContext не инициализирован")
    return AndroidScreenCaptureSource(context)
}
