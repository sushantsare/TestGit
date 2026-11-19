package com.sushant.gymmanager.auth

import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sushant.gymmanager.data.local.GymSettingsEntity

enum class ProtectedAction { APP_LAUNCH, ADD_MEMBER, DELETE_MEMBER, RESTORE_BACKUP, RESET_DATA, CHANGE_SECURITY }

object BiometricHelper {

    fun status(context: Context): Int = BiometricManager.from(context).canAuthenticate(BIOMETRIC_WEAK)

    fun isAvailable(context: Context): Boolean = status(context) == BiometricManager.BIOMETRIC_SUCCESS

    fun statusMessage(context: Context): String = when (status(context)) {
        BiometricManager.BIOMETRIC_SUCCESS -> "Biometric authentication is ready on this device"
        BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint or face enrolled. Add one in phone settings."
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
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle(subtitle)
            .setAllowedAuthenticators(BIOMETRIC_WEAK)
            .setNegativeButtonText("Cancel")
            .build()
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
