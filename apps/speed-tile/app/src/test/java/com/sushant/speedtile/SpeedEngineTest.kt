package com.sushant.speedtile

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class SpeedEngineTest {
    private var now = 100_000L
    private fun engine(): Pair<SpeedEngine, Prefs> {
        val prefs = Prefs(ApplicationProvider.getApplicationContext())
        return SpeedEngine(prefs) { now } to prefs
    }

    @Test fun start_fix_weak_and_stop() {
        val (e, _) = engine()
        e.onStarted()
        assertEquals(Status.WAITING, e.state.value.status)
        e.onFix(Fix(25.0, 85.0, 1_000, speedMps = 20f, accuracyM = 4f))
        assertEquals(Status.LIVE, e.state.value.status)
        assertEquals(72f, e.state.value.speedKmh!!, 0.01f)
        now += SpeedEngine.WEAK_AFTER_MS + 1
        e.onTick()
        assertEquals(Status.WEAK, e.state.value.status)
        assertNull(e.state.value.speedKmh)
        e.onStopped(StopReason.USER)
        assertFalse(e.state.value.running)
    }

    @Test fun trip_survives_restart_and_reset_clears_it() {
        val (e, prefs) = engine()
        e.onStarted()
        e.onFix(Fix(25.0, 85.0, 0, speedMps = 10f, accuracyM = 4f))
        e.onFix(Fix(25.00009, 85.0, 1_000, speedMps = 10f, accuracyM = 4f))
        e.onStopped(StopReason.USER)
        val again = SpeedEngine(prefs) { now }
        assertEquals(36f, again.state.value.trip.maxKmh, 0.01f)
        assertTrue(again.state.value.trip.distanceM > 5)
        again.resetTrip()
        assertEquals(TripStats(), SpeedEngine(prefs) { now }.state.value.trip)
    }
}
