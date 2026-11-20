package com.sushant.saveo.ui.setup

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.saveo.R
import com.sushant.saveo.data.Source
import com.sushant.saveo.data.StatusScanner
import com.sushant.saveo.ui.MainViewModel
import com.sushant.saveo.ui.components.AppIcon
import com.sushant.saveo.ui.components.GlossyButton
import com.sushant.saveo.ui.components.ListCard
import com.sushant.saveo.ui.components.ListDivider
import com.sushant.saveo.ui.theme.LocalSaveo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun OnboardingScreen(onStart: () -> Unit) {
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))
            AppIcon(116.dp)
            Spacer(Modifier.height(32.dp))
            Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.onb_body), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 420.dp))
            Spacer(Modifier.height(28.dp))
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.widthIn(max = 480.dp).fillMaxWidth()) {
                Column {
                    Feature(Icons.Rounded.GridView, R.string.onb_1_t, R.string.onb_1_b); ListDivider()
                    Feature(Icons.Rounded.Lock, R.string.onb_2_t, R.string.onb_2_b); ListDivider()
                    Feature(Icons.Rounded.Favorite, R.string.onb_3_t, R.string.onb_3_b)
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        GlossyButton(stringResource(R.string.get_started), onStart, Modifier.fillMaxWidth().widthIn(max = 480.dp).padding(horizontal = 24.dp, vertical = 16.dp).align(Alignment.CenterHorizontally).testTag("get_started"))
    }
}

@Composable
private fun Feature(icon: ImageVector, t: Int, b: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(stringResource(t), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(b), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** "Connect WhatsApp": folder picker per app on Android 11+, one storage permission on Android 8–10. */
@Composable
fun SetupScreen(vm: MainViewModel, onContinue: () -> Unit, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val status by vm.status.collectAsStateWithLifecycle()
    val folders by vm.folders.collectAsStateWithLifecycle()
    val picker = vm.repo.scanner.usesFolderPicker()
    var pending by remember { mutableIntStateOf(-1) }
    var permTick by remember { mutableIntStateOf(0) }

    val treeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        val source = Source.entries.getOrNull(pending) ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ref = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                vm.repo.scanner.resolvePicked(uri)
            }
            if (ref == null) {
                runCatching { ctx.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
                vm.showSnack(ctx.getString(R.string.wrong_folder))
            } else vm.setFolder(source, ref)
        }
    }
    val legacyPerms = remember {
        if (Build.VERSION.SDK_INT <= 28) arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.repo.prefs.storageAsked = true
        permTick++
        vm.refresh()
    }
    val legacyGranted = remember(permTick) { legacyPerms.all { ContextCompat.checkSelfPermission(ctx, it) == PackageManager.PERMISSION_GRANTED } }

    val anyConnected = status.connected.isNotEmpty()
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
        Row(Modifier.padding(4.dp)) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(horizontal = 24.dp).widthIn(max = 560.dp)) {
                Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(8.dp))
                Text(stringResource(if (picker) R.string.setup_body else R.string.setup_body_legacy), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(22.dp))
            if (!picker && !legacyGranted) {
                GlossyButton(stringResource(R.string.allow_access), {
                    val activity = ctx as? android.app.Activity
                    val blocked = vm.repo.prefs.storageAsked && activity != null && legacyPerms.none { activity.shouldShowRequestPermissionRationale(it) }
                    if (blocked) ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null)))
                    else permLauncher.launch(legacyPerms)
                }, Modifier.padding(horizontal = 24.dp).testTag("allow_access"))
                Spacer(Modifier.height(18.dp))
            }
            ListCard(Modifier.widthIn(max = 600.dp)) {
                Source.entries.forEachIndexed { i, s ->
                    if (i > 0) ListDivider()
                    val connected = s in status.connected
                    val count = status.items.count { it.source == s }
                    val installed = s.pkg == null || vm.isInstalled(s.pkg)
                    val sub = when {
                        connected -> stringResource(R.string.connected_n, count)
                        s == Source.DUAL -> stringResource(R.string.dual_hint)
                        !installed && s != Source.DUAL -> stringResource(R.string.not_found)
                        s in status.lost -> stringResource(R.string.lost_title)
                        else -> stringResource(R.string.not_connected)
                    }
                    SourceRow(
                        s, sub, connected,
                        actionLabel = when {
                            connected && (picker || s == Source.DUAL) -> stringResource(R.string.disconnect)
                            connected -> null
                            s == Source.DUAL -> stringResource(R.string.add_folder)
                            picker -> stringResource(R.string.connect)
                            else -> null
                        },
                    ) {
                        if (connected && (picker || s == Source.DUAL)) {
                            folders[s]?.let { ref -> runCatching { ctx.contentResolver.releasePersistableUriPermission(StatusScanner.parseRef(ref).first, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
                            vm.setFolder(s, null)
                        } else {
                            pending = i
                            treeLauncher.launch(StatusScanner.initialUri(s))
                        }
                    }
                }
            }
            if (picker) {
                Surface(
                    shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.padding(16.dp).widthIn(max = 600.dp).fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.next_screen_hint), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onContinue, modifier = Modifier.testTag("skip")) { Text(stringResource(R.string.skip_for_now)) }
            Spacer(Modifier.weight(1f))
            GlossyButton(stringResource(R.string.continue_), onContinue, enabled = anyConnected, modifier = Modifier.testTag("continue"))
        }
    }
}

@Composable
private fun SourceRow(s: Source, sub: String, connected: Boolean, actionLabel: String?, onAction: () -> Unit) {
    val ex = LocalSaveo.current
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp))
                .background(if (connected) androidx.compose.ui.graphics.Brush.verticalGradient(listOf(Color(0xFF3CCB89), Color(0xFF148654))) else androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.surfaceContainerHigh)),
            contentAlignment = Alignment.Center,
        ) { Icon(if (connected) Icons.Rounded.Check else Icons.Rounded.Folder, null, tint = if (connected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp)) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(s.label, style = MaterialTheme.typography.titleMedium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (actionLabel != null) FilledTonalButton(onClick = onAction, modifier = Modifier.testTag("connect_${s.name}")) { Text(actionLabel) }
        else if (connected) Text(stringResource(R.string.done), color = ex.glossBottom, style = MaterialTheme.typography.labelLarge)
    }
}

