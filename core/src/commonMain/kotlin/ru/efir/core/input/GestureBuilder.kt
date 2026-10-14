package ru.efir.core.input

import ru.efir.core.protocol.TouchPoint
import ru.efir.core.protocol.TouchStroke
import kotlin.math.hypot

/**
 * Построитель штрихов жестов (общая, платформенно-независимая логика).
 *
 * Все координаты здесь — нормализованные (0..1); масштабирование в пиксели
 * выполняет принимающая сторона (`InputInjectionHost`/`InputInjector`).
 *
 * Используется клиентом (ПК) для генерации свайпов произвольной траектории,
 * мультитач-жестов и пинча. Чистые функции — легко тестируются.
 */
object GestureBuilder {

    /** Число сегментов интерполяции по умолчанию для плавного свайпа. */
    const val DEFAULT_STEPS: Int = 12

    /**
     * Строит одиночный штрих по траектории точек.
     *
     * @param points точки траектории (нормализованные координаты).
     * @param durationMs длительность штриха.
     * @param startDelayMs задержка старта относительно начала жеста.
     */
    fun stroke(
        points: List<TouchPoint>,
        durationMs: Long,
        startDelayMs: Long = 0,
    ): TouchStroke = TouchStroke(
        points = points,
        startDelayMs = startDelayMs.coerceAtLeast(0),
        durationMs = durationMs.coerceAtLeast(1),
    )

    /**
     * Строит линейно интерполированный свайп между двумя точками.
     * Возвращает [TouchStroke] из [steps] + 1 точек.
     */
    fun linearSwipe(
        from: TouchPoint,
        to: TouchPoint,
        durationMs: Long,
        steps: Int = DEFAULT_STEPS,
        startDelayMs: Long = 0,
    ): TouchStroke {
        val safeSteps = steps.coerceAtLeast(2)
        val points = (0..safeSteps).map { i ->
            val t = i.toFloat() / safeSteps
            TouchPoint(
                x = from.x + (to.x - from.x) * t,
                y = from.y + (to.y - from.y) * t,
            )
        }
        return stroke(points, durationMs, startDelayMs)
    }

    /**
     * Строит жест «пинч»/«зум» двумя пальцами.
     *
     * Пальцы движутся симметрично относительно центра: при [startSpan] < [endSpan]
     * жест «разводит» пальцы (зум внутрь/приближение), иначе «сводит» (зум наружу).
     *
     * @param centerX/centerY центр жеста (нормализованные координаты).
     * @param startSpan начальное расстояние между пальцами по X.
     * @param endSpan конечное расстояние между пальцами по X.
     * @param durationMs длительность жеста.
     * @param steps число сегментов интерполяции.
     */
    fun pinch(
        centerX: Float,
        centerY: Float,
        startSpan: Float,
        endSpan: Float,
        durationMs: Long,
        steps: Int = DEFAULT_STEPS,
    ): List<TouchStroke> {
        val safeSteps = steps.coerceAtLeast(2)
        val halfStart = startSpan / 2f
        val halfEnd = endSpan / 2f

        val left = (0..safeSteps).map { i ->
            val t = i.toFloat() / safeSteps
            TouchPoint(centerX - lerp(halfStart, halfEnd, t), centerY)
        }
        val right = (0..safeSteps).map { i ->
            val t = i.toFloat() / safeSteps
            TouchPoint(centerX + lerp(halfStart, halfEnd, t), centerY)
        }
        return listOf(
            stroke(left, durationMs),
            stroke(right, durationMs),
        )
    }

    /**
     * Строит жест двумя пальцами по произвольным траекториям одинаковой длины.
     * Если длины различаются — обрезает до минимальной.
     */
    fun twoFinger(
        first: List<TouchPoint>,
        second: List<TouchPoint>,
        durationMs: Long,
    ): List<TouchStroke> {
        val n = minOf(first.size, second.size)
        if (n == 0) return emptyList()
        return listOf(
            stroke(first.take(n), durationMs),
            stroke(second.take(n), durationMs),
        )
    }

    /**
     * Прореживает траекторию сэмплированных точек до [maxPoints], сохраняя
     * форму (равномерная выборка по индексам). Для ограничения размера жеста.
     */
    fun simplify(points: List<TouchPoint>, maxPoints: Int): List<TouchPoint> {
        if (maxPoints <= 1 || points.size <= maxPoints) return points
        val lastIndex = points.lastIndex
        return (0 until maxPoints).map { i ->
            val index = (i.toLong() * lastIndex / (maxPoints - 1)).toInt()
            points[index]
        }
    }

    /** Длина нормализованной траектории (для оценки «свайп vs тап»). */
    fun pathLength(points: List<TouchPoint>): Float {
        if (points.size < 2) return 0f
        var sum = 0f
        for (i in 1 until points.size) {
            val dx = points[i].x - points[i - 1].x
            val dy = points[i].y - points[i - 1].y
            sum += hypot(dx, dy)
        }
        return sum
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
}
