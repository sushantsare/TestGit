package com.sushant.gymmanager

import android.Manifest
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.data.local.ThemeMode
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.LocalLang
import com.sushant.gymmanager.ui.lock.AppLockScreen
import com.sushant.gymmanager.ui.navigation.MainNavigation
import com.sushant.gymmanager.ui.theme.GymManagerTheme
import com.sushant.gymmanager.ui.theme.isAppInDarkTheme
import com.sushant.gymmanager.work.GymWidget
import com.sushant.gymmanager.work.Notifications
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {
    /** Relock after the app has been in the background for this long. */
    private val relockAfterMs = 60_000L

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val app = application as GymApp
        lifecycleScope.launch { app.backupManager.runAutoBackupIfDue() }
        setContent { AppRoot(app) }
    }

    override fun onStop() {
        super.onStop()
        val app = application as GymApp
        app.appScope.launch { runCatching { GymWidget.update(app) } }
    }

    @Composable
    private fun AppRoot(app: GymApp) {
        val settings by app.repository.settingsOrNull.collectAsStateWithLifecycle(initialValue = null)
        var unlocked by rememberSaveable { mutableStateOf(false) }
        var backgroundAt by rememberSaveable { mutableStateOf(0L) }
        val s = settings
        val themeMode = s?.themeMode ?: ThemeMode.SYSTEM
        val dark = isAppInDarkTheme(themeMode)
        val lockEnabled = s != null && s.biometricMasterEnabled && s.biometricAppLaunch

        LifecycleEventEffect(Lifecycle.Event.ON_STOP) { backgroundAt = SystemClock.elapsedRealtime() }
        LifecycleEventEffect(Lifecycle.Event.ON_START) {
            if (lockEnabled && unlocked && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt > relockAfterMs) {
                unlocked = false
            }
        }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(s?.dailyReminderEnabled) {
            if (s?.dailyReminderEnabled == true && Build.VERSION.SDK_INT >= 33 && !Notifications.canPost(this@MainActivity)) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        LaunchedEffect(dark) {
            enableEdgeToEdge(
                statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
            )
        }

        CompositionLocalProvider(LocalLang provides (s?.language ?: "en")) {
            GymManagerTheme(themeMode) {
                if (s == null) {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
                } else {
                    // Keep the app composed underneath so nothing is lost when it relocks.
                    Box(Modifier.fillMaxSize()) {
                        MainNavigation(settings = s)
                        if (lockEnabled && !unlocked) {
                            val unlock = {
                                BiometricHelper.authenticate(
                                    activity = this@MainActivity,
                                    title = I18n.tr(s.language, "Unlock %s", s.gymName),
                                    subtitle = I18n.tr(s.language, "Verify your identity to open Gym Manager"),
                                    onSuccess = { unlocked = true }
                                )
                            }
                            LaunchedEffect(Unit) { unlock() }
                            AppLockScreen(gymName = s.gymName, onUnlock = unlock)
                        }
                    }
                }
            }
        }
    }
}
