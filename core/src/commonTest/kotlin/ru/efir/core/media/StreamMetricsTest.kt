package ru.efir.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Тесты профайлера потоков [StreamMetrics]. Время подаётся вручную,
 * поэтому проверки детерминированы.
 */
class StreamMetricsTest {

    @Test
    fun fpsAndBitrateMeasuredOverWindow() {
        var clock = 0L
        val metrics = StreamMetrics(windowMillis = 1000, now = { clock })

        // 30 кадров по 1000 байт в течение 1000 мс.
        repeat(30) {
            clock += 33
            metrics.onFrame(1000, frameTimestamp = 0)
        }
        val snap = metrics.snapshot
        // 30 кадров за ~990 мс → ~30 FPS.
        assertTrue(snap.fps in 28.0..32.0, "fps=${snap.fps}")
        // 30000 байт за ~990 мс → ~30303 байт/с.
        assertTrue(snap.bitrateBps in 28_000..33_000, "bps=${snap.bitrateBps}")
        assertEquals(1000, snap.avgFrameBytes)
        assertEquals(30, snap.totalFrames)
        assertEquals(30_000, snap.totalBytes)
    }

    @Test
    fun latencyEstimatedFromFrameTimestamp() {
        var clock = 5000L
        val metrics = StreamMetrics(now = { clock })
        // Кадр создан в 4900, обработан сейчас (5000) → задержка 100 мс.
        metrics.onFrame(2000, frameTimestamp = 4900)
        assertEquals(100, metrics.snapshot.latencyMs)
    }

    @Test
    fun windowResetsAfterElapsed() {
        var clock = 0L
        val metrics = StreamMetrics(windowMillis = 1000, now = { clock })
        repeat(10) { metrics.onFrame(500) }
        clock = 1100
        // Первый кадр после истечения окна — сбрасывает счётчики окна.
        metrics.onFrame(500)
        val snap = metrics.snapshot
        // В окне теперь только 1 кадр (остальные сброшены) — фактический FPS внизу.
        assertTrue(snap.fps < 10.0, "fps=${snap.fps}")
        // Но тотальные счётчики сохраняются.
        assertEquals(11, snap.totalFrames)
    }

    @Test
    fun ignoresNonPositiveFrameBytes() {
        val metrics = StreamMetrics(now = { 0L })
        metrics.onFrame(0)
        metrics.onFrame(-5)
        assertEquals(0, metrics.snapshot.totalFrames)
    }

    @Test
    fun resetClearsTotals() {
        val metrics = StreamMetrics(now = { 0L })
        metrics.onFrame(1000, frameTimestamp = 0)
        metrics.reset()
        assertEquals(0, metrics.snapshot.totalFrames)
        assertEquals(0, metrics.snapshot.totalBytes)
    }
}
