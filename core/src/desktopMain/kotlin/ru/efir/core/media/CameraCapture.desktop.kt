package ru.efir.core.media

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Источник-заглушка: захват камеры на Desktop не поддерживается. */
private class UnsupportedCameraCapture : CameraCaptureSource {
    override suspend fun start(quality: CameraQuality): CaptureSize =
        throw UnsupportedOperationException("Захват камеры не поддерживается на Desktop")

    override fun frames(): Flow<CapturedCameraFrame> = flow { }

    override suspend fun stop() = Unit
}

actual fun cameraCaptureSupported(): Boolean = false

actual fun createCameraCaptureSource(): CameraCaptureSource = UnsupportedCameraCapture()
