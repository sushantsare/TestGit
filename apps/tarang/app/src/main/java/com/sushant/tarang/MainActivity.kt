@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang

import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.ui.AppRoot
import com.sushant.tarang.ui.MainVm
import com.sushant.tarang.ui.theme.TarangTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : AppCompatActivity() {
    internal val vm: MainVm by viewModels()
    /** Incoming request from a notification, widget or another app (open player / play a file). */
    val pendingIntent = MutableStateFlow<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // keep the splash only until the saved library has loaded from the database (no fake timer)
        splash.setKeepOnScreenCondition { !vm.library.value.loaded && !vm.startupTimedOut }
        pendingIntent.value = intent
        setContent {
            val s by vm.settings.collectAsState()
            TarangTheme(s.themeMode, s.pureBlack, s.wallpaperColors) { AppRoot(vm, this) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingIntent.value = intent
    }

    override fun onStart() { super.onStart(); vm.player.connect() }

    companion object {
        const val EXTRA_OPEN_PLAYER = "open_player"
        const val EXTRA_ROUTE = "route"
    }
}
