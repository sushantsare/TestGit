package com.sushant.speedtile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** The Quick Settings tile: tap to start/stop, shows live GPS speed in km/h. */
class SpeedTileService : TileService() {

    private var job: Job? = null

    override fun onTileAdded() { app.prefs.setTileAdded(true) }
    override fun onTileRemoved() { app.prefs.setTileAdded(false) }

    override fun onStartListening() {
        super.onStartListening()
        app.prefs.setTileAdded(true)
        job?.cancel()
        job = combine(app.engine.state, app.prefs.decimals) { s, d -> s to d }
            .onEach { (s, d) -> render(s, d) }
            .launchIn(app.appScope)
    }

    override fun onStopListening() {
        job?.cancel()
        job = null
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        if (app.engine.state.value.running) {
            SpeedService.stop(this)
            return
        }
        if (isLocked) unlockAndRun { openStarter() } else openStarter()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openStarter() {
        val intent = Intent(this, StartActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            val pi = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun render(s: SpeedState, decimals: Boolean) {
        val tile = qsTile ?: return
        val view = TileView.from(s, decimals)
        tile.state = if (view.active) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = view.label
        if (Build.VERSION.SDK_INT >= 29) tile.subtitle = view.subtitle
        tile.contentDescription = view.label + ", " + view.subtitle
        tile.icon = if (view.iconNumber != null) numberIcon(view.iconNumber) else Icon.createWithResource(this, R.drawable.ic_speed)
        tile.updateTile()
    }

    /** Draws the speed itself as the tile icon, so it is readable even where subtitles are hidden. */
    private fun numberIcon(text: String): Icon {
        val size = 96
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
            textSize = 80f
        }
        while (p.measureText(text) > size - 4 && p.textSize > 20f) p.textSize -= 2f
        val fm = p.fontMetrics
        val y = size / 2f - (fm.ascent + fm.descent) / 2f
        c.drawText(text, size / 2f, y, p)
        return Icon.createWithBitmap(bmp)
    }
}

/** What the tile shows for a given state (pure, unit tested). */
data class TileView(val active: Boolean, val label: String, val subtitle: String, val iconNumber: String?) {
    companion object {
        fun from(s: SpeedState, decimals: Boolean): TileView {
            if (!s.running) {
                val sub = when (s.stopReason) {
                    StopReason.IDLE -> "Stopped · not moving"
                    StopReason.NO_PERMISSION -> "Tap to allow location"
                    StopReason.GPS_OFF -> "No GPS on this phone"
                    else -> "Tap to start"
                }
                return TileView(false, "Speed", sub, null)
            }
            return when (s.status) {
                Status.LIVE -> {
                    val v = SpeedMath.formatSpeed(s.speedKmh, decimals)
                    TileView(true, "$v km/h", s.accuracyM?.let { "GPS · ±${it.toInt()} m" } ?: "GPS speed", SpeedMath.formatSpeed(s.speedKmh, false))
                }
                Status.WEAK -> TileView(true, "-- km/h", "Weak GPS signal", null)
                Status.GPS_OFF -> TileView(true, "Speed", "Location is off", null)
                else -> TileView(true, "-- km/h", "Finding GPS…", null)
            }
        }
    }
}
