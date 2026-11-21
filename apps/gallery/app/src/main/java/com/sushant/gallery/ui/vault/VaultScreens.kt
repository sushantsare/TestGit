package com.sushant.gallery.ui.vault

import androidx.activity.compose.BackHandler
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gallery.R
import com.sushant.gallery.data.Source
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.AppTopBar
import com.sushant.gallery.ui.components.ConfirmDialog
import com.sushant.gallery.ui.components.EmptyState
import com.sushant.gallery.ui.components.GridGroup
import com.sushant.gallery.ui.components.Illus
import com.sushant.gallery.ui.components.InfoBanner
import com.sushant.gallery.ui.components.MediaGrid
import com.sushant.gallery.ui.components.SelectionTopBar
import com.sushant.gallery.ui.components.pluralText
import com.sushant.gallery.ui.theme.LocalSpacing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Shows the PIN screen until the Hidden album is unlocked, then [content]. */
@Composable
fun VaultGate(vm: MainViewModel, nav: Nav, content: @Composable () -> Unit) {
    val unlocked by vm.vaultUnlocked.collectAsStateWithLifecycle()
    if (unlocked) content() else LockScreen(vm, nav)
}

@Composable
fun VaultScreen(vm: MainViewModel, nav: Nav) {
    VaultGate(vm, nav) { VaultGrid(vm, nav) }
}

@Composable
private fun VaultGrid(vm: MainViewModel, nav: Nav) {
    val items by vm.vaultItems.collectAsStateWithLifecycle()
    var sel by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selSet = remember(sel) { sel.toSet() }
    var confirm by rememberSaveable { mutableStateOf(false) }
    BackHandler(sel.isNotEmpty()) { sel = emptyList() }
    val chosen = items.filter { it.key in selSet }
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        if (sel.isNotEmpty()) {
            SelectionTopBar(pluralText(R.plurals.n_selected, sel.size), onClose = { sel = emptyList() }) {
                IconButton(onClick = { sel = items.map { it.key } }) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) }
                IconButton(onClick = { vm.unhide(chosen); sel = emptyList() }, modifier = Modifier.testTag("unhide")) { Icon(Icons.Rounded.Visibility, stringResource(R.string.unhide)) }
                IconButton(onClick = { confirm = true }) { Icon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_forever)) }
            }
        } else {
            AppTopBar(stringResource(R.string.hidden), subtitle = stringResource(R.string.hidden_sub, pluralText(R.plurals.n_items, items.size)), onBack = { nav.back() }) {
                IconButton(onClick = { vm.lockVault(); nav.back() }, modifier = Modifier.testTag("lock_now")) { Icon(Icons.Rounded.Lock, stringResource(R.string.lock_now)) }
            }
        }
        if (items.isEmpty()) {
            EmptyState(Illus.LOCK, stringResource(R.string.hidden_empty_title), stringResource(R.string.hidden_empty_body))
        } else {
            MediaGrid(
                groups = listOf(GridGroup("", items)), columns = 4, selection = selSet, favourites = emptySet(),
                onOpen = { m -> nav.viewer(Source.Vault, items.indexOf(m).coerceAtLeast(0)) },
                onSelect = { keys, on -> sel = if (on) (sel + keys).distinct() else sel - keys.toSet() },
                header = { InfoBanner(Icons.Rounded.Shield, stringResource(R.string.hidden_banner)) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }
    if (confirm) ConfirmDialog(
        stringResource(R.string.delete_forever_q), pluralText(R.plurals.delete_forever_body, sel.size), stringResource(R.string.delete_forever),
        onConfirm = { confirm = false; vm.deleteHidden(chosen); sel = emptyList() }, onDismiss = { confirm = false },
    )
}

@Composable
fun LockScreen(vm: MainViewModel, nav: Nav, change: Boolean = false, onDone: (() -> Unit)? = null) {
    val ctx = LocalContext.current
    val sp = LocalSpacing.current
    val scope = rememberCoroutineScope()
    val setup = change || !vm.vault.hasPin
    var pin by remember { mutableStateOf("") }
    var first by rememberSaveable { mutableStateOf<String?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val fpEnabled by vm.prefs.fingerprint.collectAsStateWithLifecycle()
    val canBio = remember {
        BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS
    }
    val wrongText = stringResource(R.string.pin_wrong)
    val mismatchText = stringResource(R.string.pin_mismatch)
    val waitText = stringResource(R.string.pin_wait)
    val bioTitle = stringResource(R.string.unlock_hidden)
    val usePin = stringResource(R.string.use_pin)

    fun biometric() {
        val act = ctx as? FragmentActivity ?: return
        val prompt = BiometricPrompt(act, ContextCompat.getMainExecutor(ctx), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { vm.unlockVault() }
        })
        runCatching {
            prompt.authenticate(
                BiometricPrompt.PromptInfo.Builder().setTitle(bioTitle).setNegativeButtonText(usePin)
                    .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_WEAK).build(),
            )
        }
    }
    LaunchedEffect(Unit) { if (!setup && fpEnabled && canBio) biometric() }

    fun submit(p: String) {
        if (setup) {
            val f = first
            if (f == null) { first = p; pin = "" }
            else if (f == p) { busy = true; scope.launch { withContext(Dispatchers.IO) { vm.vault.setPin(p) }; busy = false; if (!change) vm.unlockVault(); onDone?.invoke() } }
            else { first = null; pin = ""; message = mismatchText }
            return
        }
        busy = true
        scope.launch {
            val ok = withContext(Dispatchers.IO) { vm.vault.checkPin(p) }
            busy = false
            if (ok) vm.unlockVault() else {
                val w = vm.vault.waitSeconds()
                message = if (w > 0) waitText.format(w) else wrongText
                delay(300); pin = ""
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)
            .windowInsetsPadding(WindowInsets.navigationBars).testTag("lock_screen"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppTopBar("", onBack = { nav.back() })
        Column(
            Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = sp.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lock, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
            Spacer(Modifier.height(18.dp))
            Text(stringResource(R.string.hidden_album), style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(6.dp))
            Text(
                stringResource(if (!setup) R.string.enter_pin else if (first == null) (if (change) R.string.new_pin else R.string.create_pin) else R.string.confirm_pin),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (setup && first == null) {
                Spacer(Modifier.height(8.dp))
                Text(stringResource(R.string.pin_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
            }
            Row(Modifier.padding(vertical = 28.dp).semantics { contentDescription = "${pin.length} of 4 digits entered" }, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                repeat(4) { i ->
                    val filled = i < pin.length
                    Box(Modifier.size(14.dp).clip(CircleShape)
                        .background(if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.background)
                        .border(2.dp, if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline, CircleShape))
                }
            }
            Box(Modifier.height(24.dp)) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else message?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
            }
            Spacer(Modifier.height(12.dp))
            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "bio", "0", "del")
            keys.chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(26.dp), modifier = Modifier.padding(vertical = 7.dp)) {
                    row.forEach { k ->
                        val mod = Modifier.size(sp.pinKey).clip(CircleShape)
                        when (k) {
                            "bio" -> Box(
                                mod.then(if (!setup && canBio && fpEnabled) Modifier.clickable { biometric() } else Modifier),
                                contentAlignment = Alignment.Center,
                            ) { if (!setup && canBio && fpEnabled) Icon(Icons.Rounded.Fingerprint, stringResource(R.string.use_fingerprint), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp)) }
                            "del" -> Box(mod.clickable { pin = pin.dropLast(1); message = null }.testTag("pin_del"), contentAlignment = Alignment.Center) {
                                Icon(Icons.AutoMirrored.Rounded.Backspace, stringResource(R.string.delete_digit), modifier = Modifier.size(28.dp))
                            }
                            else -> Box(
                                mod.background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable(enabled = !busy) {
                                    message = null
                                    if (pin.length < 4) { pin += k; if (pin.length == 4) submit(pin) }
                                }.testTag("pin_$k"),
                                contentAlignment = Alignment.Center,
                            ) { Text(k, style = MaterialTheme.typography.headlineSmall) }
                        }
                    }
                }
            }
        }
    }
}

/** Settings › Change PIN: check the current PIN, then set a new one. First time: just create one. */
@Composable
fun PinScreen(vm: MainViewModel, nav: Nav) {
    val unlocked by vm.vaultUnlocked.collectAsStateWithLifecycle()
    val hadPin = remember { vm.vault.hasPin }
    val changed = stringResource(R.string.msg_pin_changed)
    when {
        !hadPin -> LockScreen(vm, nav, onDone = { nav.back() })
        !unlocked -> LockScreen(vm, nav)
        else -> LockScreen(vm, nav, change = true, onDone = { vm.toast(changed); nav.back() })
    }
}
