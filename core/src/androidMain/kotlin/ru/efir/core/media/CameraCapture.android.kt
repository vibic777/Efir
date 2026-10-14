package ru.efir.core.media

import android.content.Context
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.map
import ru.efir.core.media.EfirAndroidEnvironment
import ru.efir.core.protocol.CameraFacing
import ru.efir.core.protocol.nowMillis

actual fun cameraCaptureSupported(): Boolean = true

/**
 * Android-источник захвата камеры на базе Camera2.
 *
 * Открывает выбранную камеру, создаёт сессию с `ImageReader` в формате JPEG и
 * отправляет повторяющиеся запросы захвата. Кадры (готовый JPEG) уходят в поток.
 *
 * Требует разрешения `CAMERA`, выданного приложением заранее.
 */
private class AndroidCameraCaptureSource(
    private val context: Context,
) : CameraCaptureSource {

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var reader: ImageReader? = null
    private var handlerThread: HandlerThread? = null
    private var handler: Handler? = null
    private var quality: CameraQuality = CameraQuality()
    private var requestBuilder: CaptureRequest.Builder? = null

    /** Аппаратный H.264-энкодер (активен только в режиме `hardware`). */
    private var encoder: AndroidH264Encoder? = null
    private var hardwareActive: Boolean = false

    @Volatile
    private var liveJpegQuality: Int = 70

    override fun setEncoderQuality(jpegQuality: Int, targetFps: Int) {
        liveJpegQuality = jpegQuality.coerceIn(1, 100)
        val builder = requestBuilder ?: return
        val activeSession = session ?: return
        runCatching {
            builder.set(CaptureRequest.JPEG_QUALITY, liveJpegQuality.toByte())
            activeSession.setRepeatingRequest(builder.build(), null, handler)
        }
    }

    override suspend fun start(quality: CameraQuality): CaptureSize {
        this.quality = quality
        liveJpegQuality = quality.jpegQuality
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cameraId = selectCameraId(manager, quality.facing)
            ?: throw IllegalStateException("Камера не найдена (facing=${quality.facing})")

        val thread = HandlerThread("efir-camera-capture").also { it.start() }
        handlerThread = thread
        val h = Handler(thread.looper)
        handler = h

        val size = fitWithin(quality.width, quality.height, quality.width, quality.height)

        // Аппаратный H.264 (opt-in): камера пишет прямо в Surface кодера.
        val h264 = if (quality.hardware && h264EncodingSupported()) {
            AndroidH264Encoder(
                width = size.width,
                height = size.height,
                bitrate = quality.bitrate,
                frameRate = quality.targetFps,
            ).takeIf { it.start() }
        } else {
            null
        }

        val targetSurface: Surface
        if (h264 != null) {
            encoder = h264
            hardwareActive = true
            targetSurface = h264.surface
        } else {
            hardwareActive = false
            val imageReader = ImageReader.newInstance(size.width, size.height, android.graphics.ImageFormat.JPEG, 2)
            reader = imageReader
            targetSurface = imageReader.surface
        }

        @Suppress("MissingPermission")
        manager.openCamera(
            cameraId,
            object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    device = camera
                    createSession(camera, targetSurface)
                }

                override fun onDisconnected(camera: CameraDevice) {
                    runCatching { camera.close() }
                    device = null
                }

                override fun onError(camera: CameraDevice, error: Int) {
                    runCatching { camera.close() }
                    device = null
                }
            },
            h,
        )
        return size
    }

    private fun createSession(camera: CameraDevice, surface: Surface) {
        val builder = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
            addTarget(surface)
            set(CaptureRequest.CONTROL_MODE, CameraCharacteristics.CONTROL_MODE_AUTO)
            // JPEG_QUALITY применим только к JPEG-потоку (не к Surface кодера).
            if (!hardwareActive) {
                set(CaptureRequest.JPEG_QUALITY, liveJpegQuality.toByte())
            }
        }
        requestBuilder = builder
        val request = builder.build()
        camera.createCaptureSession(
            listOf(surface),
            object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(newSession: CameraCaptureSession) {
                    session = newSession
                    runCatching { newSession.setRepeatingRequest(request, null, handler) }
                }

                override fun onConfigureFailed(newSession: CameraCaptureSession) {
                    runCatching { newSession.close() }
                }
            },
            handler,
        )
    }

    override fun frames(): Flow<CapturedCameraFrame> {
        // Аппаратный H.264-путь: кадры отдаёт сам кодек.
        encoder?.let { h264 ->
            return h264.drain().map { frame ->
                CapturedCameraFrame(
                    size = frame.size,
                    mimeType = frame.mimeType,
                    timestamp = frame.timestamp,
                    payload = frame.payload,
                    keyframe = frame.keyframe,
                    config = frame.config,
                )
            }
        }
        return jpegFrames()
    }

    private fun jpegFrames(): Flow<CapturedCameraFrame> = callbackFlow {
        val reader = reader ?: throw IllegalStateException("Камера не запущена")
        reader.setOnImageAvailableListener({ r ->
            val image = r.acquireLatestImage() ?: return@setOnImageAvailableListener
            try {
                val buffer = image.planes[0].buffer
                val bytes = ByteArray(buffer.remaining())
                buffer.get(bytes)
                trySend(
                    CapturedCameraFrame(
                        size = CaptureSize(image.width, image.height),
                        mimeType = "image/jpeg",
                        timestamp = nowMillis(),
                        payload = bytes,
                    ),
                )
            } finally {
                image.close()
            }
        }, handler)
        awaitClose { reader.setOnImageAvailableListener(null, null) }
    }

    override suspend fun stop() {
        runCatching { session?.close() }
        session = null
        requestBuilder = null
        runCatching { device?.close() }
        device = null
        runCatching { encoder?.stop() }
        encoder = null
        hardwareActive = false
        runCatching { reader?.close() }
        reader = null
        handlerThread?.quitSafely()
        handlerThread = null
        handler = null
    }

    /** Выбирает id камеры по направлению (фронтальная/тыловая). */
    private fun selectCameraId(manager: CameraManager, facing: CameraFacing): String? {
        val desired = if (facing == CameraFacing.FRONT) {
            CameraCharacteristics.LENS_FACING_FRONT
        } else {
            CameraCharacteristics.LENS_FACING_BACK
        }
        return manager.cameraIdList.firstOrNull { id ->
            manager.getCameraCharacteristics(id).get(CameraCharacteristics.LENS_FACING) == desired
        } ?: manager.cameraIdList.firstOrNull()
    }
}

actual fun createCameraCaptureSource(): CameraCaptureSource {
    val context = EfirAndroidEnvironment.appContext
        ?: throw IllegalStateException("EfirAndroidEnvironment.appContext не инициализирован")
    return AndroidCameraCaptureSource(context)
}
