package com.sushant.inknote.ui.lock

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.sushant.inknote.R
import com.sushant.inknote.ui.app
import com.sushant.inknote.ui.theme.LocalInk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun canUseBiometric(a: FragmentActivity) = BiometricManager.from(a).canAuthenticate(BIOMETRIC_STRONG or BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS

fun showBiometric(a: FragmentActivity, title: String, onOk: () -> Unit) {
    val prompt = BiometricPrompt(a, ContextCompat.getMainExecutor(a), object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = onOk()
    })
    prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(title).setSubtitle(a.getString(R.string.unlock_subtitle))
        .setAllowedAuthenticators(BIOMETRIC_STRONG or BIOMETRIC_WEAK).setNegativeButtonText(a.getString(R.string.use_pin)).build())
}

/** Full-screen unlock for a locked note: fingerprint first, app PIN as backup. */
@Composable
fun LockScreen(title: String, onUnlocked: () -> Unit, onBack: () -> Unit, expanded: Boolean) {
    val ctx = LocalContext.current
    val activity = ctx as FragmentActivity
    val app = ctx.app()
    val bio = app.settings.state.value.biometric && canUseBiometric(activity)
    var pin by rememberSaveable { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val unlockTitle = stringResource(R.string.unlock_note_title, title)
    LaunchedEffect(Unit) { if (bio) showBiometric(activity, unlockTitle, onUnlocked) }
    fun check() {
        val wait = app.securePrefs.waitMs()
        if (wait > 0) { error = ctx.getString(R.string.pin_wait, (wait / 1000).toInt() + 1); pin = ""; return }
        val p = pin
        scope.launch {
            val ok = withContext(Dispatchers.Default) { app.securePrefs.checkPin(p) }
            if (ok) onUnlocked() else { error = ctx.getString(R.string.wrong_pin); pin = "" }
        }
    }
    Box(Modifier.fillMaxSize().background(LocalInk.current.bg).testTag("lock_screen")) {
        IconButton(onClick = onBack, modifier = Modifier.then(if (expanded) Modifier else Modifier.statusBarsPadding()).padding(4.dp)) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).padding(top = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Outlined.Lock, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(40.dp))
            Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 14.dp))
            Text(stringResource(R.string.note_is_locked), color = LocalInk.current.muted, modifier = Modifier.padding(top = 6.dp))
            if (bio) {
                Surface(onClick = { showBiometric(activity, unlockTitle, onUnlocked) }, shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(top = 24.dp).size(84.dp).semantics { contentDescription = "Unlock with fingerprint" }) {
                    Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Fingerprint, null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(44.dp)) }
                }
                Text(stringResource(R.string.touch_or_pin), color = LocalInk.current.muted, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 10.dp))
            }
            Row(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(maxOf(4, pin.length)) { i ->
                    Box(Modifier.size(14.dp).then(if (i < pin.length) Modifier.background(MaterialTheme.colorScheme.primary, CircleShape) else Modifier.border(2.dp, LocalInk.current.muted, CircleShape)))
                }
            }
            Text(error ?: " ", color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp).height(20.dp))
            PinPad(onDigit = { if (pin.length < 6) { pin += it; error = null } }, onBackspace = { pin = pin.dropLast(1) }, onOk = { if (pin.length >= 4) check() }, okEnabled = pin.length >= 4)
        }
    }
}

@Composable
fun PinPad(onDigit: (Char) -> Unit, onBackspace: () -> Unit, onOk: () -> Unit, okEnabled: Boolean) {
    Column(Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf("123", "456", "789").forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { row.forEach { d -> Key(d.toString(), "pin_$d") { onDigit(d) } } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            KeyIcon({ Icon(Icons.AutoMirrored.Outlined.Backspace, stringResource(R.string.delete_digit)) }, "pin_del", true, onBackspace)
            Key("0", "pin_0") { onDigit('0') }
            KeyIcon({ Icon(Icons.Filled.Check, stringResource(R.string.unlock), tint = if (okEnabled) MaterialTheme.colorScheme.onPrimary else LocalInk.current.muted) }, "pin_ok", okEnabled, onOk, primary = true)
        }
    }
}

@Composable
private fun Key(label: String, tag: String, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(72.dp).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) { Text(label, fontSize = 26.sp, style = MaterialTheme.typography.titleLarge) }
    }
}

@Composable
private fun KeyIcon(icon: @Composable () -> Unit, tag: String, enabled: Boolean, onClick: () -> Unit, primary: Boolean = false) {
    Surface(onClick = onClick, enabled = enabled, shape = CircleShape, color = if (primary && enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(72.dp).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) { icon() }
    }
}

/** Create or change the app PIN (4–6 digits). */
@Composable
fun SetPinDialog(onDismiss: () -> Unit, onDone: () -> Unit) {
    val ctx = LocalContext.current
    var a by rememberSaveable { mutableStateOf("") }
    var b by rememberSaveable { mutableStateOf("") }
    var err by rememberSaveable { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.set_pin)) },
        text = {
            Column {
                Text(stringResource(R.string.set_pin_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(a, { v -> a = v.filter { it.isDigit() }.take(6); err = null }, label = { Text(stringResource(R.string.new_pin)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.testTag("pin_a"))
                OutlinedTextField(b, { v -> b = v.filter { it.isDigit() }.take(6); err = null }, label = { Text(stringResource(R.string.repeat_pin)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.padding(top = 8.dp).testTag("pin_b"))
                err?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                when {
                    a.length < 4 -> err = ctx.getString(R.string.pin_too_short)
                    a != b -> err = ctx.getString(R.string.pin_mismatch)
                    else -> scope.launch { withContext(Dispatchers.Default) { ctx.app().securePrefs.setPin(a) }; onDone() }
                }
            }, modifier = Modifier.testTag("pin_save")) { Text(stringResource(R.string.save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

/** Asks for the PIN (used before backing up or restoring locked notes). */
@Composable
fun AskPinDialog(title: String, body: String, onDismiss: () -> Unit, onPin: (String) -> Unit) {
    var a by rememberSaveable { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(a, { v -> a = v.filter { it.isDigit() }.take(6) }, label = { Text(stringResource(R.string.pin)) }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword), modifier = Modifier.padding(top = 10.dp).testTag("ask_pin"))
            }
        },
        confirmButton = { TextButton(onClick = { onPin(a) }, enabled = a.length >= 4, modifier = Modifier.testTag("ask_pin_ok")) { Text(stringResource(R.string.continue_)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}
