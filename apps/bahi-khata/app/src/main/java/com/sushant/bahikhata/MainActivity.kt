package com.sushant.bahikhata

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.sushant.bahikhata.data.Diagnostics
import com.sushant.bahikhata.data.ThemeMode
import com.sushant.bahikhata.ui.AppRoot
import com.sushant.bahikhata.ui.AppVm
import com.sushant.bahikhata.ui.theme.BahiTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: AppVm by viewModels()
    private var locked by mutableStateOf(false)
    private var pendingRoute by mutableStateOf<String?>(null)
    private var backgroundAt = 0L
    private val startedAt = SystemClock.elapsedRealtime()

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) { backgroundAt = SystemClock.elapsedRealtime() }
        override fun onStart(owner: LifecycleOwner) {
            if (!(application as App).ready) return
            // decrypted copies handed to a viewer app are removed as soon as the user is back
            val app = application as App
            app.appScope.launch { java.io.File(cacheDir, "open").deleteRecursively() }
            val s = vm.prefs.now
            if (s.lockOn && backgroundAt > 0 && SystemClock.elapsedRealtime() - backgroundAt >= s.lockAfterSec * 1000L) locked = true
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        if (!(application as App).ready) {
            enableEdgeToEdge()
            setContent { BahiTheme(ThemeMode.SYSTEM) { com.sushant.bahikhata.ui.StorageLocked { finish() } } }
            return
        }
        splash.setKeepOnScreenCondition { !vm.loaded.value }
        enableEdgeToEdge()
        if (savedInstanceState == null) locked = vm.prefs.now.lockOn
        else locked = savedInstanceState.getBoolean("locked", vm.prefs.now.lockOn)
        // after the system restores the app (rotation, or process death then Recents) the old launch intent must not
        // be replayed — it would throw away the screen and typing the user came back to
        if (savedInstanceState == null && (intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY) == 0) pendingRoute = routeFrom(intent)
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        lifecycleScope.launch {
            vm.settings.collect { s ->
                if (s.secureScreens || s.lockOn) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        setContent {
            val s by vm.settings.collectAsState()
            val dark = com.sushant.bahikhata.ui.theme.isDark(s.theme)
            androidx.compose.runtime.DisposableEffect(dark) {
                // status/navigation bar icons follow the app's own theme, not only the phone's
                val t = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                    navigationBarStyle = if (dark) androidx.activity.SystemBarStyle.dark(t) else androidx.activity.SystemBarStyle.light(t, t),
                )
                onDispose { }
            }
            BahiTheme(s.theme, s.pureBlack) {
                AppRoot(vm = vm, locked = locked, onUnlock = { unlock() }, onLockBack = { finish() },
                    pendingRoute = pendingRoute, onRouteConsumed = { pendingRoute = null })
            }
        }
        Diagnostics.time(this, "cold_start_ms", SystemClock.elapsedRealtime() - startedAt)
    }

    override fun onSaveInstanceState(outState: Bundle) { super.onSaveInstanceState(outState); outState.putBoolean("locked", locked) }

    /** Keyboard / Chromebook shortcuts: Ctrl+N new entry, Ctrl+F search, Esc back. */
    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        if ((application as App).ready && !locked && event.action == android.view.KeyEvent.ACTION_DOWN) {
            when {
                event.isCtrlPressed && event.keyCode == android.view.KeyEvent.KEYCODE_N -> { pendingRoute = "add"; return true }
                event.isCtrlPressed && event.keyCode == android.view.KeyEvent.KEYCODE_F -> { pendingRoute = "search"; return true }
                event.keyCode == android.view.KeyEvent.KEYCODE_ESCAPE -> { onBackPressedDispatcher.onBackPressed(); return true }
            }
        }
        val handled = super.dispatchKeyEvent(event)
        // keys nobody used (no text field focused) go to the screen that wants them, e.g. typing an amount
        if (!handled && event.action == android.view.KeyEvent.ACTION_DOWN) return KeyBus.keys.subscriptionCount.value > 0 && KeyBus.wanted(event.keyCode) && KeyBus.keys.tryEmit(event.keyCode)
        return handled
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); routeFrom(intent)?.let { pendingRoute = it } }

    override fun onResume() {
        super.onResume()
        val app = application as App
        if (!app.ready) return
        app.sms.startObserver()
        app.appScope.launch { app.sms.scanSince(null, notify = false); app.repo.runRecurring() }
        if (locked) unlock()
    }

    override fun onDestroy() { if (!(application as App).ready) { super.onDestroy(); return }; ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver); super.onDestroy() }

    private fun routeFrom(i: Intent?): String? = i?.getStringExtra(EXTRA_ROUTE) ?: when (i?.action) {
        ACTION_ADD_EXPENSE -> "add?type=EXPENSE"; ACTION_ADD_INCOME -> "add?type=INCOME"; ACTION_UDHAR -> "udhar"; else -> null
    }

    private var prompting = false
    fun unlock() {
        if (prompting || !locked) return
        val bm = BiometricManager.from(this)
        val strong = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL else BiometricManager.Authenticators.BIOMETRIC_WEAK
        val can = bm.canAuthenticate(strong)
        val deviceSecure = (getSystemService(KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure
        if (can != BiometricManager.BIOMETRIC_SUCCESS && !deviceSecure) { locked = false; return } // no lock set on the phone: nothing to check against
        prompting = true
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; locked = false }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
            override fun onAuthenticationFailed() {}
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle("Unlock Bahi Khata").setSubtitle("Use fingerprint or phone PIN").apply {
            if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(strong)
            else { @Suppress("DEPRECATION") setDeviceCredentialAllowed(true) }
        }.build()
        prompt.authenticate(info)
    }

    companion object {
        const val EXTRA_ROUTE = "route"
        const val ACTION_ADD_EXPENSE = "com.sushant.bahikhata.ADD_EXPENSE"
        const val ACTION_ADD_INCOME = "com.sushant.bahikhata.ADD_INCOME"
        const val ACTION_UDHAR = "com.sushant.bahikhata.UDHAR"
    }
}

/** Hardware keys not used by any focused field, for screens that accept typing (the amount keypad). */
object KeyBus {
    val keys = kotlinx.coroutines.flow.MutableSharedFlow<Int>(extraBufferCapacity = 16)
    fun wanted(code: Int) = code in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 ||
        code in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 || code == android.view.KeyEvent.KEYCODE_DEL ||
        code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER || code == android.view.KeyEvent.KEYCODE_PERIOD
}
