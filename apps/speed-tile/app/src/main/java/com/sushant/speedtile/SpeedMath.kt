package com.sushant.speedtile

import java.util.Locale
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** One GPS reading, kept free of Android types so it can be unit tested. */
data class Fix(
    val lat: Double,
    val lon: Double,
    val timeMs: Long,
    val speedMps: Float? = null,
    val accuracyM: Float? = null,
)

data class TripStats(
    val maxKmh: Float = 0f,
    val distanceM: Double = 0.0,
    val movingMs: Long = 0L,
) {
    val avgKmh: Float
        get() = if (movingMs < 1_000L) 0f else ((distanceM / (movingMs / 1000.0)) * 3.6).toFloat()
}

object SpeedMath {
    /** Below this the GPS is just drifting while you stand still. */
    const val NOISE_KMH = 1.5f
    /** At or above this you count as moving (distance + moving time). */
    const val MOVING_KMH = 2f
    /** Readings worse than this are not used for trip stats. */
    const val GOOD_ACCURACY_M = 30f
    /** Speed is computed from positions only when both are at least this good. */
    const val FALLBACK_ACCURACY_M = 50f

    fun mpsToKmh(mps: Float): Float = mps * 3.6f

    fun clean(kmh: Float): Float = if (kmh.isNaN() || kmh < NOISE_KMH) 0f else kmh

    fun formatSpeed(kmh: Float?, decimals: Boolean): String = when {
        kmh == null -> "--"
        decimals -> String.format(Locale.US, "%.1f", kmh)
        else -> kmh.roundToInt().toString()
    }

    fun formatDistance(m: Double): String = when {
        m < 1000 -> "${m.roundToInt()} m"
        m < 100_000 -> String.format(Locale.US, "%.2f km", m / 1000)
        else -> String.format(Locale.US, "%.1f km", m / 1000)
    }

    fun formatDuration(ms: Long): String {
        val total = ms / 1000
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return when {
            h > 0 -> String.format(Locale.US, "%dh %02dm", h, m)
            m > 0 -> String.format(Locale.US, "%dm %02ds", m, s)
            else -> "${s}s"
        }
    }

    /** Great-circle distance in metres. */
    fun distanceM(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }

    fun isIdleTooLong(nowMs: Long, lastMovingMs: Long, autoStopMin: Int): Boolean =
        autoStopMin > 0 && nowMs - lastMovingMs >= autoStopMin * 60_000L
}

/**
 * Turns GPS fixes into a live speed and running trip totals.
 * Uses the receiver Doppler speed when the phone gives one, otherwise distance / time.
 */
class TripTracker(initial: TripStats = TripStats()) {
    var stats: TripStats = initial
        private set
    private var last: Fix? = null

    /** Returns the cleaned speed in km/h, or null when it cannot be worked out yet. */
    fun add(fix: Fix): Float? {
        val prev = last
        val dt = if (prev != null) fix.timeMs - prev.timeMs else 0L
        if (prev != null && dt <= 0L) return null // duplicate or out-of-order fix

        val gap = if (prev != null) SpeedMath.distanceM(prev.lat, prev.lon, fix.lat, fix.lon) else 0.0
        val raw: Float? = when {
            fix.speedMps != null -> SpeedMath.mpsToKmh(fix.speedMps)
            prev != null && dt in 500L..10_000L &&
                (fix.accuracyM ?: 0f) <= SpeedMath.FALLBACK_ACCURACY_M &&
                (prev.accuracyM ?: 0f) <= SpeedMath.FALLBACK_ACCURACY_M ->
                ((gap / (dt / 1000.0)) * 3.6).toFloat()
            else -> null
        }
        last = fix
        val kmh = raw?.let { SpeedMath.clean(it) } ?: return null

        val good = (fix.accuracyM ?: 0f) <= SpeedMath.GOOD_ACCURACY_M
        if (good) {
            var s = stats
            if (kmh > s.maxKmh) s = s.copy(maxKmh = kmh)
            if (prev != null && kmh >= SpeedMath.MOVING_KMH && dt <= 10_000L) {
                s = s.copy(distanceM = s.distanceM + gap, movingMs = s.movingMs + dt)
            }
            stats = s
        }
        return kmh
    }

    /** Forget the previous fix (after a signal loss) so a long gap is not counted as a jump. */
    fun breakChain() { last = null }

    fun reset() {
        stats = TripStats()
        last = null
    }
}
