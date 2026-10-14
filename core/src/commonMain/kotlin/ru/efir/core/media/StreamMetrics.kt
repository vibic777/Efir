package ru.efir.core.media

import kotlinx.datetime.Clock

/**
 * Снимок метрик одного видеопотока.
 *
 * @param fps фактическая частота кадров за последнее окно.
 * @param bitrateBps фактическая пропускная способность (байт/с) за последнее окно.
 * @param avgFrameBytes средний размер кадра за последнее окно.
 * @param latencyMs оценка задержки «создание кадра → обработка» (мс).
 * @param totalFrames всего учтённых кадров.
 * @param totalBytes всего учтённых байт.
 */
data class StreamMetricsSnapshot(
    val fps: Double = 0.0,
    val bitrateBps: Long = 0,
    val avgFrameBytes: Int = 0,
    val latencyMs: Long = 0,
    val totalFrames: Long = 0,
    val totalBytes: Long = 0,
)

/**
 * Лёгкий профайлер видеопотока для оценки производительности.
 *
 * Считает фактический FPS и битрейт по скользящему окну [windowMillis] и оценивает
 * задержку «создание кадра → обработка» по метке времени кадра:
 *
 * - на **передающей** стороне [onFrame] вызывается после отправки кадра, задержка
 *   отражает время кодирования/отправки относительно момента захвата;
 * - на **принимающей** стороне [onFrame] вызывается после декодирования, задержка
 *   отражает сквозную задержку «захват → отображение».
 *
 * Класс чистый и детерминированно тестируемый — источник времени подаётся через [now],
 * поэтому не зависит от платформы и легко покрывается юнит-тестами.
 */
class StreamMetrics(
    private val windowMillis: Long = 1000,
    private val now: () -> Long = { Clock.System.now().toEpochMilliseconds() },
) {

    private var windowStart: Long = now()
    private var windowFrames: Long = 0
    private var windowBytes: Long = 0
    private var totalFrames: Long = 0
    private var totalBytes: Long = 0
    private var lastLatencyMs: Long = 0

    /** Текущие метрики. */
    val snapshot: StreamMetricsSnapshot
        get() {
            val elapsed = (now() - windowStart).coerceAtLeast(1)
            val fps = if (windowFrames == 0L) 0.0 else windowFrames * 1000.0 / elapsed
            val bps = windowBytes * 1000 / elapsed
            val avg = if (windowFrames == 0L) 0 else (windowBytes / windowFrames).toInt()
            return StreamMetricsSnapshot(
                fps = fps,
                bitrateBps = bps,
                avgFrameBytes = avg,
                latencyMs = lastLatencyMs,
                totalFrames = totalFrames,
                totalBytes = totalBytes,
            )
        }

    /**
     * Учитывает кадр размером [bytes].
     *
     * @param frameTimestamp метка времени создания кадра (epoch millis). Если >0,
     *   пересчитывается оценка задержки; `0` отключает оценку для этого кадра.
     */
    fun onFrame(bytes: Int, frameTimestamp: Long = 0) {
        if (bytes <= 0) return
        windowFrames++
        windowBytes += bytes
        totalFrames++
        totalBytes += bytes
        if (frameTimestamp > 0) {
            lastLatencyMs = (now() - frameTimestamp).coerceAtLeast(0)
        }
        val nowMs = now()
        if (nowMs - windowStart >= windowMillis) {
            windowStart = nowMs
            windowFrames = 0
            windowBytes = 0
        }
    }

    /** Сбрасывает накопленные метрики (например, при старте новой сессии). */
    fun reset() {
        windowStart = now()
        windowFrames = 0
        windowBytes = 0
        totalFrames = 0
        totalBytes = 0
        lastLatencyMs = 0
    }
}
