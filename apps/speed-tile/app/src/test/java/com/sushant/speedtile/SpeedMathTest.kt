package com.sushant.speedtile

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedMathTest {

    @Test fun converts_metres_per_second_to_kmh() {
        assertEquals(36f, SpeedMath.mpsToKmh(10f), 0.001f)
        assertEquals(0f, SpeedMath.mpsToKmh(0f), 0.001f)
    }

    @Test fun tiny_speeds_are_treated_as_standing_still() {
        assertEquals(0f, SpeedMath.clean(1.2f), 0f)
        assertEquals(0f, SpeedMath.clean(Float.NaN), 0f)
        assertEquals(5f, SpeedMath.clean(5f), 0f)
    }

    @Test fun formats_speed() {
        assertEquals("--", SpeedMath.formatSpeed(null, false))
        assertEquals("43", SpeedMath.formatSpeed(42.6f, false))
        assertEquals("42.6", SpeedMath.formatSpeed(42.6f, true))
        assertEquals("0", SpeedMath.formatSpeed(0f, false))
    }

    @Test fun formats_distance_and_time() {
        assertEquals("850 m", SpeedMath.formatDistance(850.2))
        assertEquals("3.42 km", SpeedMath.formatDistance(3421.0))
        assertEquals("123.5 km", SpeedMath.formatDistance(123_456.0))
        assertEquals("45s", SpeedMath.formatDuration(45_000))
        assertEquals("12m 30s", SpeedMath.formatDuration(750_000))
        assertEquals("1h 05m", SpeedMath.formatDuration(3_900_000))
    }

    @Test fun distance_of_one_hundredth_degree_latitude_is_about_1112_m() {
        val d = SpeedMath.distanceM(25.0, 85.0, 25.01, 85.0)
        assertEquals(1112.0, d, 2.0)
    }

    @Test fun idle_check() {
        assertFalse(SpeedMath.isIdleTooLong(10 * 60_000L, 0L, 0))
        assertFalse(SpeedMath.isIdleTooLong(4 * 60_000L, 0L, 5))
        assertTrue(SpeedMath.isIdleTooLong(5 * 60_000L, 0L, 5))
    }
}

class TripTrackerTest {

    @Test fun uses_gps_doppler_speed_when_available() {
        val t = TripTracker()
        assertEquals(54f, t.add(Fix(25.0, 85.0, 1_000, speedMps = 15f, accuracyM = 5f))!!, 0.01f)
    }

    @Test fun falls_back_to_distance_over_time() {
        val t = TripTracker()
        assertNull(t.add(Fix(25.0, 85.0, 0, accuracyM = 5f)))
        // ~11.1 m in 1 s = ~40 km/h
        val kmh = t.add(Fix(25.0001, 85.0, 1_000, accuracyM = 5f))!!
        assertEquals(40f, kmh, 0.5f)
    }

    @Test fun no_fallback_with_poor_accuracy() {
        val t = TripTracker()
        t.add(Fix(25.0, 85.0, 0, accuracyM = 80f))
        assertNull(t.add(Fix(25.0001, 85.0, 1_000, accuracyM = 80f)))
    }

    @Test fun accumulates_distance_moving_time_max_and_average() {
        val t = TripTracker()
        var lat = 25.0
        for (i in 0..10) {
            t.add(Fix(lat, 85.0, i * 1_000L, speedMps = 10f, accuracyM = 5f))
            lat += 0.00009 // ~10 m
        }
        val s = t.stats
        assertEquals(36f, s.maxKmh, 0.01f)
        assertEquals(10_000L, s.movingMs)
        assertEquals(100.0, s.distanceM, 2.0)
        assertEquals(36f, s.avgKmh, 1f)
    }

    @Test fun standing_still_adds_nothing() {
        val t = TripTracker()
        for (i in 0..5) t.add(Fix(25.0 + i * 0.000001, 85.0, i * 1_000L, speedMps = 0.3f, accuracyM = 5f))
        assertEquals(0L, t.stats.movingMs)
        assertEquals(0.0, t.stats.distanceM, 0.0)
        assertEquals(0f, t.stats.maxKmh, 0f)
    }

    @Test fun inaccurate_fixes_do_not_count_for_stats() {
        val t = TripTracker()
        t.add(Fix(25.0, 85.0, 0, speedMps = 50f, accuracyM = 120f))
        assertEquals(0f, t.stats.maxKmh, 0f)
    }

    @Test fun long_gaps_are_not_counted_as_moving() {
        val t = TripTracker()
        t.add(Fix(25.0, 85.0, 0, speedMps = 10f, accuracyM = 5f))
        t.add(Fix(25.01, 85.0, 60_000, speedMps = 10f, accuracyM = 5f))
        assertEquals(0L, t.stats.movingMs)
    }

    @Test fun duplicate_fix_is_ignored() {
        val t = TripTracker()
        t.add(Fix(25.0, 85.0, 1_000, speedMps = 10f, accuracyM = 5f))
        assertNull(t.add(Fix(25.0, 85.0, 1_000, speedMps = 10f, accuracyM = 5f)))
    }

    @Test fun one_gps_spike_does_not_become_top_speed() {
        val t = TripTracker()
        var lat = 25.0
        val speeds = listOf(40f, 41f, 190f, 42f, 43f) // km/h, one glitch
        speeds.forEachIndexed { i, kmh ->
            t.add(Fix(lat, 85.0, i * 1_000L, speedMps = kmh / 3.6f, accuracyM = 5f))
            lat += 0.0001
        }
        assertEquals(42f, t.stats.maxKmh, 0.1f) // two steady readings: min(42, 43)
    }

    @Test fun spike_keeps_showing_previous_speed() {
        val t = TripTracker()
        t.add(Fix(25.0, 85.0, 0, speedMps = 40f / 3.6f, accuracyM = 5f))
        val shown = t.add(Fix(25.0001, 85.0, 1_000, speedMps = 190f / 3.6f, accuracyM = 5f))
        assertEquals(40f, shown!!, 0.1f)
    }

    @Test fun uncertain_speed_is_ignored_for_records() {
        val t = TripTracker()
        t.add(Fix(25.0, 85.0, 0, speedMps = 20f, accuracyM = 5f, speedAccMps = 6f))
        t.add(Fix(25.0001, 85.0, 1_000, speedMps = 20f, accuracyM = 5f, speedAccMps = 6f))
        assertEquals(0f, t.stats.maxKmh, 0f)
    }

    @Test fun top_speed_needs_two_steady_readings() {
        val t = TripTracker()
        t.add(Fix(25.0, 85.0, 0, speedMps = 10f, accuracyM = 5f))
        assertEquals(0f, t.stats.maxKmh, 0f)
        t.add(Fix(25.0001, 85.0, 1_000, speedMps = 11f, accuracyM = 5f))
        assertEquals(36f, t.stats.maxKmh, 0.1f) // min of the two readings
    }

    @Test fun real_hard_braking_is_not_a_glitch() {
        assertFalse(SpeedMath.isImpossibleJump(60f, 30f, 1_000)) // 30 km/h drop in 1 s
        assertTrue(SpeedMath.isImpossibleJump(40f, 190f, 1_000))
    }

    @Test fun reset_clears_everything() {
        val t = TripTracker(TripStats(80f, 5000.0, 600_000))
        t.reset()
        assertEquals(TripStats(), t.stats)
    }
}

class TileViewTest {

    @Test fun off_tile() {
        val v = TileView.from(SpeedState(), false)
        assertFalse(v.active)
        assertEquals("Speed", v.label)
        assertEquals("Tap to start", v.subtitle)
        assertNull(v.iconNumber)
    }

    @Test fun live_tile_shows_kmh() {
        val v = TileView.from(SpeedState(running = true, status = Status.LIVE, speedKmh = 47.4f, accuracyM = 4f), false)
        assertTrue(v.active)
        assertEquals("47 km/h", v.label)
        assertEquals("GPS · ±4 m", v.subtitle)
        assertEquals("47", v.iconNumber)
    }

    @Test fun live_tile_with_decimal_keeps_round_icon() {
        val v = TileView.from(SpeedState(running = true, status = Status.LIVE, speedKmh = 47.4f), true)
        assertEquals("47.4 km/h", v.label)
        assertEquals("47", v.iconNumber)
    }

    @Test fun waiting_and_idle_states() {
        assertEquals("Finding GPS · 0/0 sats", TileView.from(SpeedState(running = true, status = Status.WAITING), false).subtitle)
        assertEquals("Weak GPS signal", TileView.from(SpeedState(running = true, status = Status.WEAK), false).subtitle)
        assertEquals("Stopped · not moving", TileView.from(SpeedState(stopReason = StopReason.IDLE), false).subtitle)
    }
}
