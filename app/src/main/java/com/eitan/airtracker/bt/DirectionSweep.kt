package com.eitan.airtracker.bt

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Finds the direction of the AirPods from Bluetooth signal strength using "body shadowing":
 * the user holds the phone against the chest and turns a full circle. The human body
 * attenuates 2.4 GHz signals strongly, so the signal is strongest when facing the earbuds.
 * We bin RSSI by compass heading and pick the strongest direction.
 *
 * This is a statistical estimate, not a precise bearing; [SweepResult.spreadDb] tells how
 * pronounced the peak was (bigger = more trustworthy).
 */
class DirectionSweep {
    private val sums = DoubleArray(BINS)
    private val counts = IntArray(BINS)
    var samples = 0
        private set

    fun add(headingDeg: Float, rssi: Int) {
        val b = (((headingDeg % 360f) + 360f) % 360f / BIN_DEG).toInt().coerceIn(0, BINS - 1)
        sums[b] += rssi
        counts[b]++
        samples++
    }

    /** Which bins already have data (for drawing progress). */
    fun covered(): BooleanArray = BooleanArray(BINS) { counts[it] > 0 }

    fun coverage(): Float = counts.count { it > 0 }.toFloat() / BINS

    fun isComplete(): Boolean = coverage() >= 11f / BINS && samples >= 30

    fun result(nowMs: Long): SweepResult? {
        if (counts.count { it > 0 } < 6) return null
        val mean = DoubleArray(BINS) { if (counts[it] > 0) sums[it] / counts[it] else Double.NaN }
        // fill gaps from neighbours
        val filled = DoubleArray(BINS) { i ->
            if (!mean[i].isNaN()) mean[i] else {
                val l = mean[(i + BINS - 1) % BINS]
                val r = mean[(i + 1) % BINS]
                when {
                    !l.isNaN() && !r.isNaN() -> (l + r) / 2
                    !l.isNaN() -> l
                    !r.isNaN() -> r
                    else -> Double.NaN
                }
            }
        }
        val valid = filled.filter { !it.isNaN() }
        if (valid.isEmpty()) return null
        val fallback = valid.average()
        val f = DoubleArray(BINS) { if (filled[it].isNaN()) fallback else filled[it] }
        // circular smoothing
        val sm = DoubleArray(BINS) { i ->
            0.25 * f[(i + BINS - 1) % BINS] + 0.5 * f[i] + 0.25 * f[(i + 1) % BINS]
        }
        val max = sm.max()
        val min = sm.min()
        // weighted circular mean of the strong side (convert dB to linear-ish weights)
        var x = 0.0
        var y = 0.0
        for (i in 0 until BINS) {
            val w = 10.0.pow((sm[i] - max) / 6.0) // 6 dB down → weight 0.1
            val a = Math.toRadians(i * BIN_DEG + BIN_DEG / 2.0)
            x += w * cos(a)
            y += w * sin(a)
        }
        var deg = Math.toDegrees(atan2(y, x)).toFloat()
        if (deg < 0) deg += 360f
        return SweepResult(deg, max - min, nowMs)
    }

    companion object {
        const val BINS = 12
        const val BIN_DEG = 30f
    }
}

data class SweepResult(
    /** Magnetic heading (same reference as the compass) of the strongest signal. */
    val bearingDeg: Float,
    val spreadDb: Double,
    val timeMs: Long,
) {
    val confidence: String
        get() = when {
            spreadDb >= 10 -> "גבוהה"
            spreadDb >= 5 -> "בינונית"
            else -> "נמוכה"
        }
    val reliable: Boolean get() = spreadDb >= 4
}
