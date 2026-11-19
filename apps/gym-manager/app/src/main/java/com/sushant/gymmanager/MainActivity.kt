package com.sushant.gymmanager

import android.graphics.Color
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.data.local.ThemeMode
import com.sushant.gymmanager.ui.lock.AppLockScreen
import com.sushant.gymmanager.ui.navigation.MainNavigation
import com.sushant.gymmanager.ui.theme.GymManagerTheme
import com.sushant.gymmanager.ui.theme.isAppInDarkTheme

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as GymApp
        setContent { AppRoot(app) }
    }

    @Composable
    private fun AppRoot(app: GymApp) {
        val settings by app.repository.settingsOrNull.collectAsStateWithLifecycle(initialValue = null)
        var unlocked by rememberSaveable { mutableStateOf(false) }
        val s = settings
        val themeMode = s?.themeMode ?: ThemeMode.SYSTEM
        val dark = isAppInDarkTheme(themeMode)

        LaunchedEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT)
                else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }

        GymManagerTheme(themeMode) {
            val locked = s != null && s.biometricMasterEnabled && s.biometricAppLaunch && !unlocked
            when {
                s == null -> Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                locked -> {
                    val unlock = {
                        BiometricHelper.authenticate(
                            activity = this,
                            title = "Unlock ${s.gymName}",
                            subtitle = "Verify your identity to open Gym Manager",
                            onSuccess = { unlocked = true }
                        )
                    }
                    LaunchedEffect(Unit) { unlock() }
                    AppLockScreen(gymName = s.gymName, onUnlock = unlock)
                }
                else -> MainNavigation(settings = s)
            }
        }
    }
}
