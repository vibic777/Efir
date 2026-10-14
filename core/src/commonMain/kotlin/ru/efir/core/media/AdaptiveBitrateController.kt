package ru.efir.core.media

import kotlinx.datetime.Clock

/**
 * Параметры потока, которыми управляет адаптивный регулятор битрейта.
 *
 * @param jpegQuality качество JPEG-кадра (1..100).
 * @param targetFps целевая частота кадров.
 * @param scalePercent масштаб кадра в процентах от исходного (100 — без уменьшения).
 */
data class StreamTuning(
    val jpegQuality: Int,
    val targetFps: Int,
    val scalePercent: Int = 100,
)

/**
 * Адаптивный регулятор битрейта (adaptive bitrate) для видеопотоков.
 *
 * Оценивает **фактическую** скорость отдачи по скользящему окну и корректирует
 * качество JPEG, целевую частоту кадров и масштаб кадра, удерживая поток около
 * целевого битрейта:
 *
 * - перегрузка (`> highWater` от цели) → шаг вниз: сначала масштаб, затем
 *   качество, затем FPS (сначала жертвуем разрешением, сохраняя плавность);
 * - недогруз (`< lowWater` от цели) → шаг вверх: сначала FPS, затем качество,
 *   затем масштаб (сначала возвращаем плавность, затем детализацию);
 * - между порогами — без изменений (гистерезис, чтобы избежать «дребезга»).
 *
 * Изменения возвращаются методом [onFrame] не чаще одного раза за [windowMillis],
 * поэтому вызывающая сторона применяет их точечно. Класс чистый и тестируемый:
 * источник времени подаётся через [now].
 */
class AdaptiveBitrateController(
    targetBitrate: Int,
    initialQuality: Int = 70,
    initialFps: Int = 30,
    private val minJpegQuality: Int = 30,
    private val maxJpegQuality: Int = 85,
    private val minFps: Int = 5,
    private val maxFps: Int = 60,
    private val minScalePercent: Int = 40,
    private val windowMillis: Long = 1000,
    private val highWater: Double = 1.15,
    private val lowWater: Double = 0.6,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private val target: Long = targetBitrate.coerceAtLeast(50_000).toLong()

    private var quality: Int = initialQuality.coerceIn(minJpegQuality, maxJpegQuality)
    private var fps: Int = initialFps.coerceIn(minFps, maxFps)
    private var scale: Int = 100

    private var windowStart: Long = now()
    private var windowBytes: Long = 0

    /** Текущие параметры потока. */
    val tuning: StreamTuning get() = StreamTuning(quality, fps, scale)

    /** Последняя измеренная скорость отдачи (байт/с). */
    var lastMeasuredBps: Long = 0
        private set

    /**
     * Учитывает отправленный кадр размером [bytes].
     *
     * Когда накапливается окно [windowMillis], пересчитывает фактическую скорость
     * и, при выходе за пороги, возвращает новые [StreamTuning]. Если изменения не
     * требуются — возвращает `null`.
     */
    fun onFrame(bytes: Int): StreamTuning? {
        if (bytes <= 0) return null
        windowBytes += bytes
        val nowMs = now()
        val elapsed = nowMs - windowStart
        if (elapsed < windowMillis) return null
        lastMeasuredBps = windowBytes * 1000 / elapsed
        windowStart = nowMs
        windowBytes = 0
        return adjust()
    }

    private fun adjust(): StreamTuning? {
        val before = tuning
        when {
            lastMeasuredBps > (target * highWater).toLong() -> stepDown()
            lastMeasuredBps < (target * lowWater).toLong() -> stepUp()
            else -> return null
        }
        val after = tuning
        return if (after != before) after else null
    }

    private fun stepDown() {
        when {
            scale > minScalePercent -> scale = (scale - 10).coerceAtLeast(minScalePercent)
            quality > minJpegQuality -> quality = (quality - 5).coerceAtLeast(minJpegQuality)
            fps > minFps -> fps = (fps - 5).coerceAtLeast(minFps)
        }
    }

    private fun stepUp() {
        when {
            fps < maxFps -> fps = (fps + 5).coerceAtMost(maxFps)
            quality < maxJpegQuality -> quality = (quality + 5).coerceAtMost(maxJpegQuality)
            scale < 100 -> scale = (scale + 10).coerceAtMost(100)
        }
    }
}
