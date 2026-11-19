package com.sushant.speedtile

import android.app.Application
import android.content.ComponentName
import android.service.quicksettings.TileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

class SpeedTileApp : Application() {
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val prefs by lazy { Prefs(this) }
    val engine by lazy { SpeedEngine(prefs) }
    val panelWatch by lazy {
        PanelWatch(
            handler = android.os.Handler(android.os.Looper.getMainLooper()),
            enabled = { prefs.offOnPanelClose.value },
            isRunning = { engine.state.value.running },
            stop = {
                android.util.Log.i(SpeedService.TAG, "Quick Settings closed: speed off")
                SpeedService.stop(this, StopReason.PANEL_CLOSED)
            },
        )
    }

    override fun onCreate() {
        super.onCreate()
        // Ask the system to refresh the tile whenever what it shows changes.
        engine.state
            .map { Triple(it.running, it.status, it.speedKmh?.let { s -> SpeedMath.formatSpeed(s, prefs.decimals.value) }) }
            .distinctUntilChanged()
            .onEach {
                runCatching {
                    TileService.requestListeningState(this, ComponentName(this, SpeedTileService::class.java))
                }
            }
            .launchIn(appScope)
        // Speed went off for any reason: forget panel tracking.
        engine.state.map { it.running }.distinctUntilChanged()
            .onEach { running -> if (!running) panelWatch.reset() }
            .launchIn(appScope)
    }
}

val android.content.Context.app: SpeedTileApp get() = applicationContext as SpeedTileApp
