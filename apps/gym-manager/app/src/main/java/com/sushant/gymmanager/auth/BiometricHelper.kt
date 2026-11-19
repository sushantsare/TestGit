package com.sushant.gymmanager.auth

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import android.app.KeyguardManager
import android.os.Build
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sushant.gymmanager.data.local.GymSettingsEntity

enum class ProtectedAction { APP_LAUNCH, ADD_MEMBER, DELETE_MEMBER, RESTORE_BACKUP, RESET_DATA, CHANGE_SECURITY }

object BiometricHelper {

    /** Fingerprint/face, with the phone's PIN, pattern or password as a fallback (e.g. wet fingers). */
    private val allowed: Int
        get() = if (Build.VERSION.SDK_INT >= 30) BIOMETRIC_WEAK or DEVICE_CREDENTIAL else BIOMETRIC_WEAK

    fun status(context: Context): Int {
        val s = BiometricManager.from(context).canAuthenticate(allowed)
        if (s != BiometricManager.BIOMETRIC_SUCCESS && Build.VERSION.SDK_INT < 30 && isDeviceSecure(context)) {
            return BiometricManager.BIOMETRIC_SUCCESS
        }
        return s
    }

    private fun isDeviceSecure(context: Context): Boolean =
        context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true

    fun isAvailable(context: Context): Boolean = status(context) == BiometricManager.BIOMETRIC_SUCCESS

    fun statusMessage(context: Context): String = when (status(context)) {
        BiometricManager.BIOMETRIC_SUCCESS -> "App lock is ready: fingerprint, face or your phone's screen lock"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "Set up a screen lock or fingerprint in phone settings first."
        BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "This device has no biometric hardware"
        BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware is currently unavailable"
        else -> "Biometric authentication is not supported on this device"
    }

    fun isRequired(settings: GymSettingsEntity, action: ProtectedAction): Boolean {
        if (!settings.biometricMasterEnabled) return false
        return when (action) {
            ProtectedAction.APP_LAUNCH -> settings.biometricAppLaunch
            ProtectedAction.ADD_MEMBER -> settings.biometricAddMember
            ProtectedAction.DELETE_MEMBER -> settings.biometricDeleteMember
            ProtectedAction.RESTORE_BACKUP -> settings.biometricRestoreBackup
            ProtectedAction.RESET_DATA -> settings.biometricResetData
            ProtectedAction.CHANGE_SECURITY -> true
        }
    }

    fun authenticate(
        activity: FragmentActivity,
        title: String,
        subtitle: String,
        onSuccess: () -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        if (!isAvailable(activity)) {
            // Protection cannot work without enrolled biometrics, so never lock the owner out.
            Toast.makeText(activity, statusMessage(activity), Toast.LENGTH_SHORT).show()
            onSuccess()
            return
        }
        val prompt = BiometricPrompt(
            activity,
            ContextCompat.getMainExecutor(activity),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onSuccess()
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = onFailure(errString.toString())
            }
        )
        val builder = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
        if (Build.VERSION.SDK_INT >= 30) {
            builder.setAllowedAuthenticators(BIOMETRIC_WEAK or DEVICE_CREDENTIAL)
        } else {
            @Suppress("DEPRECATION")
            builder.setDeviceCredentialAllowed(true)
        }
        val info = builder.build()
        prompt.authenticate(info)
    }

    /** Runs [block] directly, or after a successful biometric check when [action] is protected. */
    fun guard(
        context: Context,
        settings: GymSettingsEntity,
        action: ProtectedAction,
        title: String,
        block: () -> Unit
    ) {
        if (!isRequired(settings, action)) {
            block(); return
        }
        val activity = context.findFragmentActivity() ?: run { block(); return }
        authenticate(activity, title, com.sushant.gymmanager.i18n.I18n.tr(settings.language, "Confirm it's you to continue"), onSuccess = block, onFailure = { msg ->
            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        })
    }
}

fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}
