package com.sushant.saveo

import android.app.KeyguardManager
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.saveo.ui.AppRoot
import com.sushant.saveo.ui.MainViewModel
import com.sushant.saveo.ui.theme.SaveoTheme
import com.sushant.saveo.ui.theme.ThemeMode
import com.sushant.saveo.work.Notifier
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private val app get() = application as SaveoApp

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        splash.setKeepOnScreenCondition { vm.isLoading && vm.settings.value.onboarded }
        enableEdgeToEdge()
        handleIntent(intent)

        // Keep Saveo out of Recents thumbnails / screenshots while App lock is on
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                vm.settings.collect { s ->
                    if (s.appLock) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }
        }

        setContent {
            val s by vm.settings.collectAsStateWithLifecycle()
            val dark = when (s.theme) { ThemeMode.SYSTEM -> isSystemInDarkTheme(); ThemeMode.LIGHT -> false; ThemeMode.DARK -> true }
            LaunchedEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            SaveoTheme(s.theme, s.dynamicColor) {
                AppRoot(
                    vm = vm,
                    authenticate = { ok -> authenticate(ok) },
                    lockAvailable = lockAvailable(),
                    onUnlockRequest = { authenticate { app.unlock() } },
                    onExit = { finish() },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(i: Intent?) {
        if (i?.getBooleanExtra(Notifier.EXTRA_OPEN_STATUSES, false) == true) {
            vm.closeViewer(); vm.setTab(0); vm.refresh()
        }
    }

    override fun onResume() {
        super.onResume()
        if (vm.settings.value.onboarded) vm.refresh()
    }

    private val authenticators get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun lockAvailable(): Boolean {
        if (Build.VERSION.SDK_INT >= 30) return BiometricManager.from(this).canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS
        val km = getSystemService(KeyguardManager::class.java)
        return km?.isDeviceSecure == true
    }

    private var prompting = false

    /** Fingerprint / face with phone PIN fallback. */
    fun authenticate(onOk: () -> Unit) {
        if (prompting) return
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { prompting = false; onOk() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { prompting = false }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.unlock_title)).apply {
            if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(authenticators)
            else {
                @Suppress("DEPRECATION")
                setDeviceCredentialAllowed(true)
            }
        }.build()
        prompting = true
        runCatching { prompt.authenticate(info) }.onFailure { prompting = false }
    }
}
