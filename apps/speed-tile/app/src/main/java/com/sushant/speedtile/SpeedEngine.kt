package com.sushant.speedtile

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

enum class Status { OFF, WAITING, LIVE, WEAK, GPS_OFF }

enum class StopReason { NONE, USER, IDLE, GPS_OFF, NO_PERMISSION, BLOCKED }

data class SpeedState(
    val running: Boolean = false,
    val status: Status = Status.OFF,
    val speedKmh: Float? = null,
    val accuracyM: Float? = null,
    val satsUsed: Int = 0,
    val satsSeen: Int = 0,
    val trip: TripStats = TripStats(),
    val stopReason: StopReason = StopReason.NONE,
)

/** Single source of truth shared by the app screen, the notification and the tile. */
class SpeedEngine(private val prefs: Prefs, private val clock: () -> Long = System::currentTimeMillis) {
    private val tracker = TripTracker(prefs.loadTrip())
    private val _state = MutableStateFlow(SpeedState(trip = tracker.stats))
    val state: StateFlow<SpeedState> = _state

    var lastFixAt = 0L
        private set
    var lastMovingAt = 0L
        private set
    private var fixesSinceSave = 0

    fun onStarted() {
        val now = clock()
        lastFixAt = 0L
        lastMovingAt = now
        tracker.breakChain()
        _state.update {
            it.copy(running = true, status = Status.WAITING, speedKmh = null, accuracyM = null,
                satsUsed = 0, satsSeen = 0, stopReason = StopReason.NONE)
        }
    }

    fun onStopped(reason: StopReason) {
        prefs.saveTrip(tracker.stats)
        tracker.breakChain()
        _state.update {
            it.copy(running = false, status = if (reason == StopReason.GPS_OFF) Status.GPS_OFF else Status.OFF,
                speedKmh = null, accuracyM = null, satsUsed = 0, satsSeen = 0, stopReason = reason)
        }
    }

    fun onFix(fix: Fix) {
        val kmh = tracker.add(fix)
        lastFixAt = clock()
        if (kmh != null && kmh >= SpeedMath.MOVING_KMH) lastMovingAt = lastFixAt
        _state.update {
            it.copy(
                status = if (kmh == null && it.speedKmh == null) Status.WAITING else Status.LIVE,
                speedKmh = kmh ?: it.speedKmh,
                accuracyM = fix.accuracyM,
                trip = tracker.stats,
            )
        }
        if (++fixesSinceSave >= 15) { fixesSinceSave = 0; prefs.saveTrip(tracker.stats) }
    }

    /** Called every couple of seconds while running: flags a lost signal. */
    fun onTick() {
        val s = _state.value
        if (!s.running) return
        if (s.status == Status.LIVE && clock() - lastFixAt > WEAK_AFTER_MS) {
            tracker.breakChain()
            _state.update { it.copy(status = Status.WEAK, speedKmh = null) }
        }
    }

    fun onSatellites(used: Int, seen: Int) = _state.update { it.copy(satsUsed = used, satsSeen = seen) }

    fun onGpsDisabled() = _state.update { it.copy(status = Status.GPS_OFF, speedKmh = null) }

    fun onGpsEnabled() = _state.update { if (it.running) it.copy(status = Status.WAITING) else it }

    fun resetTrip() {
        tracker.reset()
        prefs.saveTrip(tracker.stats)
        _state.update { it.copy(trip = tracker.stats) }
    }

    /** Only for screenshots/tests: shows a given state without real GPS. */
    fun showForTest(state: SpeedState) { _state.value = state }

    companion object {
        const val WEAK_AFTER_MS = 8_000L
    }
}
