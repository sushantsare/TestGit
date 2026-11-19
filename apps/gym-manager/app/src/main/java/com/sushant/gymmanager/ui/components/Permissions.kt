package com.sushant.gymmanager.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.sushant.gymmanager.auth.findFragmentActivity
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.util.toast

/** Asks for one runtime permission in context: a short explanation first, then the system prompt. */
class PermissionRequester internal constructor(
    private val context: Context,
    private val permission: String,
    private val start: (onGranted: () -> Unit, onDenied: () -> Unit) -> Unit
) {
    fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    /** Runs [onGranted] now if allowed, otherwise explains and asks. */
    fun request(onDenied: () -> Unit = {}, onGranted: () -> Unit) {
        if (isGranted()) onGranted() else start(onGranted, onDenied)
    }
}

fun openAppSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        context.toast("Open phone Settings > Apps > Gym Manager > Permissions")
    }
}

/**
 * @param title short title of the explanation dialog
 * @param reason one line on why the app needs it
 * @param deniedMessage shown when the user says no (the app keeps working)
 */
@Composable
fun rememberPermissionRequester(permission: String, title: String, reason: String, deniedMessage: String): PermissionRequester {
    val context = LocalContext.current
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var denied by remember { mutableStateOf<(() -> Unit)?>(null) }
    var explain by remember { mutableStateOf(false) }
    var blocked by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val action = pending
        val onDenied = denied
        pending = null
        denied = null
        if (granted) {
            action?.invoke()
        } else {
            onDenied?.invoke()
            val activity = context.findFragmentActivity()
            val canAskAgain = activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            if (canAskAgain) context.toast(deniedMessage, long = true) else blocked = true
        }
    }

    if (explain) {
        AlertDialog(
            onDismissRequest = { explain = false; pending = null; denied?.invoke(); denied = null },
            icon = { Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text(title) },
            text = { Text(reason, style = MaterialTheme.typography.bodyMedium) },
            confirmButton = {
                Button(onClick = { explain = false; launcher.launch(permission) }, modifier = Modifier.testTag("perm_continue")) {
                    Text(t("Continue"))
                }
            },
            dismissButton = { TextButton(onClick = { explain = false; pending = null; denied?.invoke(); denied = null }) { Text(t("Not now")) } }
        )
    }
    if (blocked) {
        AlertDialog(
            onDismissRequest = { blocked = false },
            title = { Text(title) },
            text = { Text(deniedMessage + " " + t("You can allow it in Settings."), style = MaterialTheme.typography.bodyMedium) },
            confirmButton = { Button(onClick = { blocked = false; openAppSettings(context) }) { Text(t("Open Settings")) } },
            dismissButton = { TextButton(onClick = { blocked = false }) { Text(t("Not now")) } }
        )
    }

    return remember(permission) {
        PermissionRequester(context, permission) { onGranted, onDenied ->
            pending = onGranted
            denied = onDenied
            explain = true
        }
    }
}

/**
 * Saving into Downloads needs no permission on Android 10+. On Android 8 and 9 it needs storage access,
 * which is asked here (with a reason) right before the first save.
 */
@Composable
fun rememberDownloadsAccess(): (onDenied: () -> Unit, proceed: () -> Unit) -> Unit {
    val req = rememberPermissionRequester(
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        title = t("Save to Downloads"),
        reason = t("Allow storage access so backups and PDF reports can be kept in your Downloads/GymManager folder."),
        deniedMessage = t("Files can't be copied to Downloads without storage access.")
    )
    return { onDenied, proceed ->
        if (android.os.Build.VERSION.SDK_INT >= 29) proceed() else req.request(onDenied, proceed)
    }
}
