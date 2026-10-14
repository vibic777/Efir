package ru.efir.core.media

import ru.efir.core.protocol.AudioSource
import ru.efir.core.protocol.AudioStart
import ru.efir.core.protocol.CameraFacing
import ru.efir.core.protocol.CameraStart
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** Проверяет построение и валидацию настроек захвата камеры/звука (блок 11). */
class MediaQualityTest {

    @Test
    fun cameraQualityFromRequest() {
        val quality = CameraQuality.from(
            CameraStart(facing = CameraFacing.FRONT, width = 1920, height = 1080, fps = 60),
        )
        assertEquals(CameraFacing.FRONT, quality.facing)
        assertEquals(1920, quality.width)
        assertEquals(1080, quality.height)
        assertEquals(60, quality.targetFps)
    }

    @Test
    fun audioQualityFromRequest() {
        val quality = AudioQuality.from(
            AudioStart(source = AudioSource.SYSTEM, sampleRate = 44_100, channels = 2),
        )
        assertEquals(AudioSource.SYSTEM, quality.source)
        assertEquals(44_100, quality.sampleRate)
        assertEquals(2, quality.channels)
    }

    @Test
    fun cameraQualityRejectsBadFps() {
        assertFailsWith<IllegalArgumentException> { CameraQuality(targetFps = 0) }
    }

    @Test
    fun audioQualityRejectsBadSampleRate() {
        assertFailsWith<IllegalArgumentException> { AudioQuality(sampleRate = 100) }
        assertFailsWith<IllegalArgumentException> { AudioQuality(channels = 3) }
    }
}
