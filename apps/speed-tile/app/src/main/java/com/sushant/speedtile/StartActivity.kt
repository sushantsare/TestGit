package com.sushant.speedtile

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity

/**
 * Invisible screen the tile opens for a split second. Android only lets an app start
 * GPS while it is on screen, so the tile hands off here, GPS starts, and this closes.
 */
class StartActivity : ComponentActivity() {
    private var done = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
    }

    override fun onResume() {
        super.onResume()
        if (done) return
        done = true
        when {
            !hasLocationPermission(this) -> {
                startActivity(
                    Intent(this, MainActivity::class.java)
                        .setAction(MainActivity.ACTION_START_FROM_TILE)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                )
            }
            !isGpsOn(this) -> {
                Toast.makeText(this, "Turn on Location to see your speed", Toast.LENGTH_LONG).show()
                runCatching {
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            }
            else -> {
                SpeedService.start(this)
                Toast.makeText(this, "Speed on · pull down to see it", Toast.LENGTH_SHORT).show()
            }
        }
        window.decorView.postDelayed({ finish(); overridePendingTransition(0, 0) }, 300)
    }
}
