package ru.efir.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class AdaptiveBitrateControllerTest {

    private fun controller(target: Int = 1_000_000, now: () -> Long): AdaptiveBitrateController =
        AdaptiveBitrateController(
            targetBitrate = target,
            initialQuality = 70,
            initialFps = 30,
            now = now,
        )

    @Test
    fun ignoresFramesUntilWindowElapses() {
        var time = 0L
        val controller = controller { time }

        time = 400
        // Окно (1000 мс) ещё не истекло — решения нет.
        assertNull(controller.onFrame(2_000_000))
        assertEquals(0, controller.lastMeasuredBps)
    }

    @Test
    fun noChangeWhenWithinHysteresisBand() {
        var time = 0L
        val controller = controller { time }

        time = 1000
        // Ровно цель — попадает в «мёртвую зону» [0.6; 1.15] от цели.
        assertNull(controller.onFrame(1_000_000))
        assertEquals(1_000_000, controller.lastMeasuredBps)
        assertEquals(
            StreamTuning(70, 30, 100),
            controller.tuning,
        )
    }

    @Test
    fun stepsDownScaleFirstWhenOverloaded() {
        var time = 0L
        val controller = controller { time }

        time = 1000
        val tuning = controller.onFrame(2_000_000) // 2x цель → перегрузка

        assertNotNull(tuning)
        assertEquals(90, tuning.scalePercent)
        assertEquals(70, tuning.jpegQuality)
        assertEquals(30, tuning.targetFps)
    }

    @Test
    fun stepsDownQualityAfterScaleIsExhausted() {
        var time = 0L
        val controller = controller { time }

        // Доводим масштаб до минимума (40), затем следующий шаг — качество.
        var last: StreamTuning? = null
        var t = 1000L
        repeat(6) {
            time = t
            last = controller.onFrame(2_000_000)
            t += 1000
        }
        assertEquals(40, controller.tuning.scalePercent)
        // Ещё один шаг перегрузки: масштаб исчерпан, шаг уходит в качество.
        time = t
        last = controller.onFrame(2_000_000)
        assertEquals(65, controller.tuning.jpegQuality)
        assertNotNull(last)
    }

    @Test
    fun stepsUpFpsFirstWhenUnderloaded() {
        var time = 0L
        val controller = controller { time }

        time = 1000
        val tuning = controller.onFrame(100_000) // 0.1x цель → недогруз

        assertNotNull(tuning)
        assertEquals(35, tuning.targetFps)
        assertEquals(70, tuning.jpegQuality)
        assertEquals(100, tuning.scalePercent)
    }

    @Test
    fun clampsAtMaximumsAndReturnsNull() {
        var time = 0L
        val controller = AdaptiveBitrateController(
            targetBitrate = 1_000_000,
            initialQuality = 85,
            initialFps = 60,
            maxJpegQuality = 85,
            maxFps = 60,
            now = { time },
        )

        time = 1000
        // Недогруз, но все параметры уже на максимуме → изменения нет.
        assertNull(controller.onFrame(10_000))
        assertEquals(StreamTuning(85, 60, 100), controller.tuning)
    }

    @Test
    fun measuresBytesPerSecond() {
        var time = 0L
        val controller = controller { time }

        time = 1000
        controller.onFrame(500_000)
        assertEquals(500_000, controller.lastMeasuredBps)

        // Второе окно считает только новые байты.
        time = 2000
        controller.onFrame(250_000)
        assertEquals(250_000, controller.lastMeasuredBps)
    }
}
