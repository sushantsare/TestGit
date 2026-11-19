package com.sushant.lekhak

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import com.sushant.lekhak.core.LekhakServiceManager
import com.sushant.lekhak.service.FloatingBubbleService

/** Invisible entry point: wakes the bubble for returning users, then opens Settings. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        LekhakServiceManager.initialize(this)
        if (LekhakServiceManager.serviceEnabled && Settings.canDrawOverlays(this)) FloatingBubbleService.start(this)
        startActivity(Intent(this, SettingsActivity::class.java))
        finish()
    }
}
