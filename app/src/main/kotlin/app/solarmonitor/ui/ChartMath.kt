package app.solarmonitor.ui

import java.util.Locale
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Pure scaling helpers for ChartView, kept free of Android classes so they can be unit-tested. */
object ChartMath {
    /** Number of horizontal gridlines above the axis. */
    const val STEPS = 4

    private val STEP_MULTIPLIERS = doubleArrayOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.0, 6.0, 8.0, 10.0)

    /** Smallest "nice" axis maximum >= [maxValue] such that STEPS equal gridlines have clean values. Returns 1.0 for non-positive input. */
    fun niceMax(maxValue: Double): Double {
        if (maxValue.isNaN() || maxValue <= 0.0) return 1.0
        val perStep = maxValue / STEPS
        val magnitude = 10.0.pow(floor(log10(perStep)))
        for (m in STEP_MULTIPLIERS) {
            val step = m * magnitude
            if (step * STEPS >= maxValue - 1e-9) return step * STEPS
        }
        return 10.0 * magnitude * STEPS
    }

    /** Bar index under x, or null when x is outside the plot or there are no bars. */
    fun barIndexAt(x: Float, left: Float, right: Float, count: Int): Int? {
        if (count <= 0 || x < left || x >= right) return null
        val idx = ((x - left) / (right - left) * count).toInt()
        return idx.coerceIn(0, count - 1)
    }

    fun xForMinute(minuteOfDay: Int, left: Float, right: Float): Float =
        left + (right - left) * (minuteOfDay / 1440f)

    /** Inverse of [xForMinute], clamped to a day. */
    fun minuteForX(x: Float, left: Float, right: Float): Int {
        if (right <= left) return 0
        val ratio = ((x - left) / (right - left)).coerceIn(0f, 1f)
        return (ratio * 1440f + 0.5f).toInt().coerceIn(0, 1440)
    }

    /** Index of the sample whose minute-of-day is closest to [minute], or null when there are none. */
    fun nearestSampleIndex(minute: Int, sampleMinutes: List<Int>): Int? {
        if (sampleMinutes.isEmpty()) return null
        var best = 0
        var bestDiff = Int.MAX_VALUE
        for ((i, m) in sampleMinutes.withIndex()) {
            val d = kotlin.math.abs(m - minute)
            if (d < bestDiff) { bestDiff = d; best = i }
        }
        return best
    }

    fun yForValue(value: Double, top: Float, bottom: Float, yMax: Double): Float =
        (bottom - (bottom - top) * (value / yMax)).toFloat()

    fun gridLabel(value: Double): String =
        if (value == floor(value)) String.format(Locale.US, "%.0f", value)
        else String.format(Locale.US, "%.1f", value)
}
