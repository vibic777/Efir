package ru.efir.core.input

import kotlinx.coroutines.test.runTest
import ru.efir.core.protocol.KeyAction
import ru.efir.core.protocol.TouchPoint
import ru.efir.core.protocol.TouchStroke
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ScreenGeometryTest {

    @Test
    fun mapsNormalizedToPixels() {
        val geometry = ScreenGeometry(width = 1080, height = 2400)
        assertEquals(0f, geometry.mapX(0f))
        assertEquals(540f, geometry.mapX(0.5f))
        assertEquals(1080f, geometry.mapX(1f))
        assertEquals(1200f, geometry.mapY(0.5f))
        assertEquals(2400f, geometry.mapY(1f))
    }

    @Test
    fun clampsOutOfRangeCoordinates() {
        val geometry = ScreenGeometry(width = 100, height = 200)
        assertEquals(0f, geometry.mapX(-1f))
        assertEquals(100f, geometry.mapX(2f))
        assertEquals(0f, geometry.mapY(-5f))
        assertEquals(200f, geometry.mapY(5f))
    }

    @Test
    fun rejectsNonPositiveSize() {
        val error = runCatching { ScreenGeometry(0, 10) }.exceptionOrNull()
        assertTrue(error is IllegalArgumentException)
    }

    @Test
    fun touchStrokeCopyPreservesStructure() {
        val stroke = TouchStroke(
            points = listOf(TouchPoint(0f, 0f), TouchPoint(1f, 1f)),
            startDelayMs = 10,
            durationMs = 100,
        )
        val scaled = stroke.copy(
            points = stroke.points.map { it.copy(x = it.x * 2, y = it.y * 2) },
        )
        assertEquals(2, scaled.points.size)
        assertEquals(2f, scaled.points[1].x)
        assertEquals(100, scaled.durationMs)
    }

    @Test
    fun noOpInjectorIsNotReady() = runTest {
        val injector = NoOpInputInjector()
        assertFalse(injector.isReady)
        // Вызовы no-op не должны бросать исключений.
        injector.updateGeometry(ScreenGeometry(10, 10))
        injector.tap(1f, 1f, 10)
        injector.swipe(0f, 0f, 1f, 1f, 10)
        injector.multiTouch(emptyList())
        injector.typeText("test")
        injector.pressKey(KeyAction.BACK)
    }
}
