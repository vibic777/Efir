package ru.efir.core.input

import ru.efir.core.protocol.TouchPoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GestureBuilderTest {

    @Test
    fun linearSwipeHasStepsPlusOnePointsAndEndpoints() {
        val stroke = GestureBuilder.linearSwipe(
            from = TouchPoint(0f, 0f),
            to = TouchPoint(1f, 1f),
            durationMs = 300,
            steps = 10,
        )
        assertEquals(11, stroke.points.size)
        assertEquals(TouchPoint(0f, 0f), stroke.points.first())
        assertEquals(TouchPoint(1f, 1f), stroke.points.last())
        assertEquals(300, stroke.durationMs)
    }

    @Test
    fun linearSwipeInterpolatesMidpoint() {
        val stroke = GestureBuilder.linearSwipe(
            from = TouchPoint(0f, 0f),
            to = TouchPoint(1f, 0f),
            durationMs = 100,
            steps = 4,
        )
        // Средняя точка (i=2) должна быть ровно посередине.
        assertEquals(0.5f, stroke.points[2].x)
    }

    @Test
    fun swipeDurationIsAtLeastOne() {
        val stroke = GestureBuilder.linearSwipe(
            from = TouchPoint(0f, 0f),
            to = TouchPoint(1f, 0f),
            durationMs = 0,
        )
        assertTrue(stroke.durationMs >= 1)
    }

    @Test
    fun pinchProducesTwoOpposingStrokes() {
        val strokes = GestureBuilder.pinch(
            centerX = 0.5f,
            centerY = 0.5f,
            startSpan = 0.2f,
            endSpan = 0.6f,
            durationMs = 300,
            steps = 10,
        )
        assertEquals(2, strokes.size)
        // Левая точка — слева от центра, правая — справа.
        assertTrue(strokes[0].points.first().x < 0.5f)
        assertTrue(strokes[1].points.first().x > 0.5f)
        // При разведении конечный спан больше начального.
        val endSpanLeft = 0.5f - strokes[0].points.last().x
        val endSpanRight = strokes[1].points.last().x - 0.5f
        assertTrue(endSpanLeft > 0.05f)
        assertTrue(endSpanRight > 0.05f)
    }

    @Test
    fun twoFingerTruncatesToShortest() {
        val first = listOf(TouchPoint(0f, 0f), TouchPoint(0.5f, 0f), TouchPoint(1f, 0f))
        val second = listOf(TouchPoint(0f, 1f), TouchPoint(0.5f, 1f))
        val strokes = GestureBuilder.twoFinger(first, second, durationMs = 200)
        assertEquals(2, strokes.size)
        assertEquals(2, strokes[0].points.size)
        assertEquals(2, strokes[1].points.size)
    }

    @Test
    fun simplifyReducesToMaxPoints() {
        val points = (0..100).map { TouchPoint(it / 100f, 0f) }
        val simplified = GestureBuilder.simplify(points, maxPoints = 10)
        assertEquals(10, simplified.size)
        assertEquals(points.first(), simplified.first())
        assertEquals(points.last(), simplified.last())
    }

    @Test
    fun pathLengthOfStraightLine() {
        val points = listOf(TouchPoint(0f, 0f), TouchPoint(0.3f, 0f), TouchPoint(0.3f, 0.4f))
        assertEquals(0.7f, GestureBuilder.pathLength(points), absoluteTolerance = 1e-4f)
    }

    @Test
    fun frameMappingFitCentersAndMaps() {
        // Кадр 200x100 в виджете 400x400 → масштаб 2, поля по вертикали.
        val mapping = FrameMapping(frameWidth = 200, frameHeight = 100, viewWidth = 400f, viewHeight = 400f)
        assertEquals(2f, mapping.scale)
        assertEquals(400f, mapping.scaledWidth)
        assertEquals(200f, mapping.scaledHeight)
        assertEquals(0f, mapping.offsetX)
        assertEquals(100f, mapping.offsetY)
        // Центр кадра — центр виджета.
        assertEquals(0.5f, mapping.toNormalizedX(200f))
        assertEquals(0.5f, mapping.toNormalizedY(200f))
        // Верхняя граница изображения (offsetY) → 0.
        assertEquals(0f, mapping.toNormalizedY(100f))
    }

    @Test
    fun frameMappingClampsOutsideImage() {
        val mapping = FrameMapping(frameWidth = 100, frameHeight = 100, viewWidth = 300f, viewHeight = 100f)
        assertEquals(0f, mapping.toNormalizedX(-50f))
        assertEquals(1f, mapping.toNormalizedX(1000f))
    }

    @Test
    fun frameMappingContainsRespectsLetterbox() {
        val mapping = FrameMapping(frameWidth = 200, frameHeight = 100, viewWidth = 400f, viewHeight = 400f)
        // Точка в поле (letterbox) — вне изображения.
        assertTrue(!mapping.contains(200f, 50f))
        // Точка внутри изображения.
        assertTrue(mapping.contains(200f, 200f))
    }
}
