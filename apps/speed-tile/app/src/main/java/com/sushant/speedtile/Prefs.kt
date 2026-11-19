package com.sushant.speedtile

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Small settings store. Simple switches apply instantly. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _theme = MutableStateFlow(sp.getInt(K_THEME, 0))
    /** 0 = system, 1 = light, 2 = dark */
    val theme: StateFlow<Int> = _theme
    fun setTheme(v: Int) { sp.edit().putInt(K_THEME, v).apply(); _theme.value = v }

    private val _gaugeMax = MutableStateFlow(sp.getInt(K_GAUGE, 160))
    val gaugeMax: StateFlow<Int> = _gaugeMax
    fun setGaugeMax(v: Int) { sp.edit().putInt(K_GAUGE, v).apply(); _gaugeMax.value = v }

    private val _autoStop = MutableStateFlow(sp.getInt(K_AUTOSTOP, 15))
    /** Minutes without movement before GPS switches itself off (0 = never). */
    val autoStopMin: StateFlow<Int> = _autoStop
    fun setAutoStop(v: Int) { sp.edit().putInt(K_AUTOSTOP, v).apply(); _autoStop.value = v }

    private val _decimals = MutableStateFlow(sp.getBoolean(K_DECIMALS, false))
    val decimals: StateFlow<Boolean> = _decimals
    fun setDecimals(v: Boolean) { sp.edit().putBoolean(K_DECIMALS, v).apply(); _decimals.value = v }

    private val _keepOn = MutableStateFlow(sp.getBoolean(K_KEEP_ON, true))
    val keepScreenOn: StateFlow<Boolean> = _keepOn
    fun setKeepScreenOn(v: Boolean) { sp.edit().putBoolean(K_KEEP_ON, v).apply(); _keepOn.value = v }

    private val _tileAdded = MutableStateFlow(sp.getBoolean(K_TILE_ADDED, false))
    val tileAdded: StateFlow<Boolean> = _tileAdded
    fun setTileAdded(v: Boolean) { sp.edit().putBoolean(K_TILE_ADDED, v).apply(); _tileAdded.value = v }

    var askedNotifications: Boolean
        get() = sp.getBoolean(K_ASKED_NOTIF, false)
        set(v) { sp.edit().putBoolean(K_ASKED_NOTIF, v).apply() }

    var askedLocation: Boolean
        get() = sp.getBoolean(K_ASKED_LOC, false)
        set(v) { sp.edit().putBoolean(K_ASKED_LOC, v).apply() }

    fun loadTrip(): TripStats = TripStats(
        maxKmh = sp.getFloat(K_MAX, 0f),
        distanceM = sp.getFloat(K_DIST, 0f).toDouble(),
        movingMs = sp.getLong(K_MOVING, 0L),
    )

    fun saveTrip(t: TripStats) {
        sp.edit().putFloat(K_MAX, t.maxKmh).putFloat(K_DIST, t.distanceM.toFloat())
            .putLong(K_MOVING, t.movingMs).apply()
    }

    private companion object {
        const val K_THEME = "theme"
        const val K_GAUGE = "gauge_max"
        const val K_AUTOSTOP = "auto_stop_min"
        const val K_DECIMALS = "decimals"
        const val K_KEEP_ON = "keep_screen_on"
        const val K_TILE_ADDED = "tile_added"
        const val K_ASKED_NOTIF = "asked_notifications"
        const val K_ASKED_LOC = "asked_location"
        const val K_MAX = "trip_max"
        const val K_DIST = "trip_distance"
        const val K_MOVING = "trip_moving"
    }
}
