package com.sushant.bahikhata.ui

import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/** Fingerprint (or the phone's PIN / pattern / password) before something private is shown. */
object Auth {
    private fun authenticators() = if (Build.VERSION.SDK_INT >= 30) BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        else BiometricManager.Authenticators.BIOMETRIC_WEAK

    /** True when the phone has a fingerprint or screen lock to check against. */
    fun available(c: Context): Boolean =
        BiometricManager.from(c).canAuthenticate(authenticators()) == BiometricManager.BIOMETRIC_SUCCESS ||
            (c.getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure

    /** Asks, then runs [onOk]. With no lock set on the phone there is nothing to check against, so it just runs. */
    fun ask(c: Context, title: String, subtitle: String = "Use fingerprint or phone PIN", onOk: () -> Unit) {
        val act = c as? FragmentActivity ?: (c as? android.content.ContextWrapper)?.baseContext as? FragmentActivity
        if (act == null || !available(c)) { onOk(); return }
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(act), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onOk() }
        })
        val info = BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(subtitle).apply {
            if (Build.VERSION.SDK_INT >= 30) setAllowedAuthenticators(authenticators())
            else { @Suppress("DEPRECATION") setDeviceCredentialAllowed(true) }
        }.build()
        prompt.authenticate(info)
    }
}
