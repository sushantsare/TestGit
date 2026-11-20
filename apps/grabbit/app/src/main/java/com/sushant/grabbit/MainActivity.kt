package com.sushant.grabbit

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.LaunchedEffect
import com.sushant.grabbit.ui.theme.isDarkTheme
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sushant.grabbit.ui.GrabbitRoot
import com.sushant.grabbit.ui.theme.GrabbitTheme
import com.sushant.grabbit.ui.MainViewModel

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_TAB = "tab"
        const val EXTRA_URL = "url"
    }

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handleIntent(intent)
        setContent {
            // Status/navigation bar icons follow the app's own theme choice, not just the phone's
            val dark = isDarkTheme()
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            GrabbitTheme { GrabbitRoot(vm) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == Intent.ACTION_SEND) {
            val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                ?: intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
            if (!text.isNullOrBlank()) vm.onShared(text)
        } else if (intent.hasExtra(EXTRA_URL)) {
            intent.getStringExtra(EXTRA_URL)?.let { vm.onShared(it) }
        } else if (intent.hasExtra(EXTRA_TAB)) {
            vm.tab = intent.getIntExtra(EXTRA_TAB, 0)
        }
    }
}
