package com.sushant.speedtile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

/**
 * Invisible screen the tile opens for a moment. Android only lets an app start GPS while it is
 * on screen, so the tile hands off here. The first time, it shows the system location prompt.
 */
class StartActivity : ComponentActivity() {
    private var handled = false

    private val askLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        app.prefs.askedLocation = true
        val coarse = res[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        when {
            hasLocationPermission(this) -> startSpeed()
            coarse -> {
                toast("Speed needs Precise location. Turn on Use precise location.")
                openAppSettings()
            }
            else -> toast("Speed needs location to measure km/h")
        }
        close()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        overridePendingTransition(0, 0)
        handled = savedInstanceState?.getBoolean("handled") ?: false
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("handled", handled)
    }

    override fun onResume() {
        super.onResume()
        if (handled) return
        handled = true
        when {
            hasLocationPermission(this) -> { startSpeed(); close() }
            // Asked before and the user chose "Don't allow" twice: only Settings can fix it now.
            app.prefs.askedLocation && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED -> {
                toast("Allow Location for Speed Tile, then tap the tile again")
                openAppSettings()
                close()
            }
            else -> askLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun startSpeed() {
        if (!isGpsOn(this)) {
            toast("Turn on Location, then tap the tile again")
            runCatching {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            return
        }
        SpeedService.start(this)
        toast("Speed on · pull down to see it")
    }

    private fun openAppSettings() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun toast(msg: String) = Toast.makeText(applicationContext, msg, Toast.LENGTH_LONG).show()

    private fun close() {
        window.decorView.postDelayed({ finish(); overridePendingTransition(0, 0) }, 300)
    }
}
