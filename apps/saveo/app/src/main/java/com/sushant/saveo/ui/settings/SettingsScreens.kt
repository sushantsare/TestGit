package com.sushant.saveo.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.saveo.BuildConfig
import com.sushant.saveo.R
import com.sushant.saveo.data.Diagnostics
import com.sushant.saveo.data.Formats
import com.sushant.saveo.ui.MainViewModel
import com.sushant.saveo.ui.Screen
import com.sushant.saveo.ui.components.AppIcon
import com.sushant.saveo.ui.components.ColorIcon
import com.sushant.saveo.ui.components.EmptyState
import com.sushant.saveo.ui.components.GlossyButton
import com.sushant.saveo.ui.components.ListCard
import com.sushant.saveo.ui.components.ListDivider
import com.sushant.saveo.ui.components.MediaImage
import com.sushant.saveo.ui.components.SectionLabel
import com.sushant.saveo.ui.components.SettingRow
import com.sushant.saveo.ui.components.dpToPx
import com.sushant.saveo.ui.theme.ThemeMode
import com.sushant.saveo.work.Notifier

@Composable
fun SubTopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(4.dp).heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.back)) }
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke()
    }
}

/** Settings. [authenticate] shows the fingerprint / PIN sheet (needed before turning App lock on). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, authenticate: (onOk: () -> Unit) -> Unit, lockAvailable: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val status by vm.status.collectAsStateWithLifecycle()
    val bin by vm.bin.collectAsStateWithLifecycle()
    var themeSheet by rememberSaveable { mutableStateOf(false) }
    var notifAsk by rememberSaveable { mutableStateOf(false) }
    var resumeTick by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumeTick++ }
    val canNotify = remember(resumeTick, s.alerts) { Notifier.canNotify(ctx) }

    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        vm.repo.prefs.notifAsked = true
        if (ok) vm.updateSettings { it.copy(alerts = true) }
        resumeTick++
    }
    fun openNotifSettings() {
        val i = if (Build.VERSION.SDK_INT >= 26) Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName)
        else Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))
        ctx.startActivity(i)
    }
    fun toggleAlerts() {
        if (s.alerts) { vm.updateSettings { it.copy(alerts = false) }; return }
        val needsPerm = Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPerm) notifAsk = true
        else if (!Notifier.canNotify(ctx)) openNotifSettings()
        else vm.updateSettings { it.copy(alerts = true) }
    }

    Column(Modifier.fillMaxSize()) {
        SubTopBar(stringResource(R.string.settings), onBack)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp).widthIn(max = 720.dp)) {
            SectionLabel(stringResource(R.string.sec_saving))
            ListCard {
                SettingRow(Icons.Rounded.AutoAwesome, Color(0xFF187C51), stringResource(R.string.set_autosave), stringResource(R.string.set_autosave_d), checked = s.autoSave) {
                    vm.updateSettings { it.copy(autoSave = !it.autoSave) }
                }
                ListDivider()
                val blocked = s.alerts && !canNotify
                SettingRow(
                    Icons.Rounded.NotificationsActive, Color(0xFFB36A00), stringResource(R.string.set_alerts),
                    stringResource(if (blocked) R.string.set_alerts_blocked else R.string.set_alerts_d),
                    checked = s.alerts, subtitleColor = if (blocked) MaterialTheme.colorScheme.error else null,
                ) { if (blocked) openNotifSettings() else toggleAlerts() }
                ListDivider()
                val connected = status.connected.joinToString(" · ") { it.label }.ifBlank { stringResource(R.string.not_connected) }
                SettingRow(Icons.Rounded.Folder, Color(0xFF3D6FC9), stringResource(R.string.set_folders), connected) { vm.openSetup(true) }
            }
            SectionLabel(stringResource(R.string.sec_privacy))
            ListCard {
                SettingRow(
                    Icons.Rounded.Lock, Color(0xFF6E4FC8), stringResource(R.string.set_lock),
                    stringResource(if (lockAvailable) R.string.set_lock_d else R.string.set_lock_unavailable),
                    checked = s.appLock, enabled = lockAvailable || s.appLock,
                ) {
                    if (s.appLock) vm.updateSettings { it.copy(appLock = false) }
                    else authenticate { vm.updateSettings { it.copy(appLock = true) } }
                }
                ListDivider()
                SettingRow(Icons.Rounded.DeleteOutline, Color(0xFFC6423D), stringResource(R.string.set_bin), pluralStringResource(R.plurals.set_bin_d, bin.size, bin.size)) { vm.go(Screen.BIN) }
            }
            SectionLabel(stringResource(R.string.sec_appearance))
            ListCard {
                SettingRow(Icons.Rounded.DarkMode, Color(0xFF3A4A41), stringResource(R.string.set_theme), stringResource(themeLabel(s.theme))) { themeSheet = true }
                if (Build.VERSION.SDK_INT >= 31) {
                    ListDivider()
                    SettingRow(Icons.Rounded.Palette, Color(0xFF3D6373), stringResource(R.string.set_dynamic), stringResource(R.string.set_dynamic_d), checked = s.dynamicColor) {
                        vm.updateSettings { it.copy(dynamicColor = !it.dynamicColor) }
                    }
                }
            }
            SectionLabel(stringResource(R.string.sec_more))
            ListCard {
                SettingRow(Icons.Rounded.BugReport, Color(0xFF55645B), stringResource(R.string.set_diag), stringResource(R.string.set_diag_d)) { vm.go(Screen.DIAG) }
                ListDivider()
                SettingRow(Icons.Rounded.Info, Color(0xFF187C51), stringResource(R.string.set_about), stringResource(R.string.version_x, BuildConfig.VERSION_NAME)) { vm.go(Screen.ABOUT) }
            }
        }
    }

    if (themeSheet) ModalBottomSheet(onDismissRequest = { themeSheet = false }) {
        Text(stringResource(R.string.set_theme), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        ThemeMode.entries.forEach { m ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { vm.updateSettings { it.copy(theme = m) }; themeSheet = false }
                    .padding(horizontal = 16.dp).heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s.theme == m, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(themeLabel(m)), style = MaterialTheme.typography.bodyLarge)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
    if (notifAsk) AlertDialog(
        onDismissRequest = { notifAsk = false },
        title = { Text(stringResource(R.string.notif_reason_title)) },
        text = { Text(stringResource(R.string.notif_reason_body)) },
        confirmButton = {
            TextButton(onClick = {
                notifAsk = false
                val activity = ctx as? android.app.Activity
                val permanentlyDenied = vm.repo.prefs.notifAsked && activity != null && Build.VERSION.SDK_INT >= 33 &&
                    !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
                if (permanentlyDenied) openNotifSettings()
                else if (Build.VERSION.SDK_INT >= 33) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }) { Text(stringResource(R.string.allow)) }
        },
        dismissButton = { TextButton(onClick = { notifAsk = false }) { Text(stringResource(R.string.not_now)) } },
    )
}

private fun themeLabel(m: ThemeMode) = when (m) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
fun BinScreen(vm: MainViewModel, onBack: () -> Unit) {
    val bin by vm.bin.collectAsStateWithLifecycle()
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var picked by remember { mutableStateOf<Long?>(null) }
    val px = dpToPx(190.dp)
    Column(Modifier.fillMaxSize()) {
        SubTopBar(stringResource(R.string.bin_title), onBack) {
            if (bin.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }) { Text(stringResource(R.string.empty_bin), color = MaterialTheme.colorScheme.error) }
        }
        LazyVerticalGrid(
            GridCells.Adaptive(160.dp), Modifier.fillMaxSize().navigationBarsPadding(),
            contentPadding = PaddingValues(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Text(stringResource(R.string.bin_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(14.dp))
                }
            }
            if (bin.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyState(stringResource(R.string.bin_empty_title), stringResource(R.string.bin_empty_body)) }
            items(bin.size, key = { bin[it].id }) { i ->
                val it = bin[i]
                val uri = it.binPath?.let { p -> Uri.fromFile(java.io.File(p)) } ?: Uri.parse(it.mediaUri)
                Column(Modifier.animateItem()) {
                    Box(Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.large).background(MaterialTheme.colorScheme.surfaceContainerHigh).clickable { picked = it.id }) {
                        MediaImage(uri, it.mediaKind, Modifier.fillMaxSize(), sizePx = px)
                        Row(
                            Modifier.align(Alignment.BottomEnd).padding(8.dp).heightIn(min = 40.dp).clip(CircleShape).background(Color.White)
                                .clickable(role = Role.Button) { vm.restore(listOf(it.id)) }.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Restore, null, tint = Color(0xFF14804F), modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.restore), color = Color(0xFF14804F), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    val days = Formats.binDaysLeft(it.deletedAt ?: 0)
                    Text(pluralStringResource(R.plurals.days_left, days, days), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
                }
            }
        }
    }
    if (confirmEmpty) AlertDialog(
        onDismissRequest = { confirmEmpty = false },
        title = { Text(pluralStringResource(R.plurals.empty_bin_q, bin.size, bin.size)) },
        text = { Text(stringResource(R.string.empty_bin_body)) },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; vm.emptyBin() }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } },
    )
    picked?.let { id ->
        AlertDialog(
            onDismissRequest = { picked = null },
            title = { Text(stringResource(R.string.bin_title)) },
            text = { Text(stringResource(R.string.empty_bin_body)) },
            confirmButton = { TextButton(onClick = { picked = null; vm.restore(listOf(id)) }) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { picked = null; vm.deleteForever(listOf(id)) }) { Text(stringResource(R.string.delete_forever), color = MaterialTheme.colorScheme.error) } },
        )
    }
}

@Composable
fun DiagnosticsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val usage by vm.usage.collectAsStateWithLifecycle()
    val tick by vm.crashTick.collectAsStateWithLifecycle()
    val saved by vm.savedAll.collectAsStateWithLifecycle()
    val crashes = remember(tick) { vm.repo.diagnostics.crashes() }
    var open by remember { mutableStateOf<String?>(null) }
    Column(Modifier.fillMaxSize()) {
        SubTopBar(stringResource(R.string.set_diag), onBack)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).widthIn(max = 720.dp)) {
            Text(stringResource(R.string.diag_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp))
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(stringResource(R.string.diag_saved), saved.size.toLong(), Modifier.weight(1f))
                    Stat(stringResource(R.string.diag_shared), usage[Diagnostics.SHARED] ?: 0, Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Stat(stringResource(R.string.diag_reposted), usage[Diagnostics.REPOSTED] ?: 0, Modifier.weight(1f))
                    Stat(stringResource(R.string.diag_opens), usage[Diagnostics.OPENS] ?: 0, Modifier.weight(1f))
                }
            }
            SectionLabel(stringResource(R.string.diag_crashes))
            ListCard {
                if (crashes.isEmpty()) Text(stringResource(R.string.diag_no_crashes), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(16.dp))
                crashes.forEachIndexed { i, c ->
                    if (i > 0) ListDivider()
                    Row(Modifier.fillMaxWidth().clickable { open = c.text }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        ColorIcon(Icons.Rounded.BugReport, MaterialTheme.colorScheme.errorContainer, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(Diagnostics.fmt(c.time), style = MaterialTheme.typography.bodyLarge)
                            Text(c.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                        }
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = vm::clearCrashes, modifier = Modifier.weight(1f).heightIn(min = 52.dp)) { Text(stringResource(R.string.clear)) }
            GlossyButton(stringResource(R.string.export_log), vm::exportLog, Modifier.weight(2f), icon = Icons.Rounded.Share)
        }
    }
    open?.let { text ->
        AlertDialog(
            onDismissRequest = { open = null },
            confirmButton = { TextButton(onClick = { open = null }) { Text(stringResource(R.string.done)) } },
            text = { Text(text, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.verticalScroll(rememberScrollState())) },
        )
    }
}

@Composable
private fun Stat(label: String, value: Long, modifier: Modifier = Modifier) {
    Surface(modifier, shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp).semantics(mergeDescendants = true) {}) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$value", style = MaterialTheme.typography.headlineMedium.merge(com.sushant.saveo.ui.theme.TabularNumbers))
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val email = stringResource(R.string.dev_email)
    val subject = stringResource(R.string.email_subject)
    Column(Modifier.fillMaxSize()) {
        SubTopBar(stringResource(R.string.set_about), onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 24.dp).widthIn(max = 720.dp)) {
            Column(Modifier.fillMaxWidth().padding(top = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                AppIcon(104.dp)
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(R.string.version_x, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            }
            SectionLabel(stringResource(R.string.about_developer))
            ListCard {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:$email")).putExtra(Intent.EXTRA_SUBJECT, subject)) }
                    }.padding(16.dp).semantics(mergeDescendants = true) {},
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
                        Text("SK", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge)
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.dev_name), style = MaterialTheme.typography.titleMedium)
                        Text(email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                    Icon(Icons.Rounded.Mail, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SectionLabel(stringResource(R.string.about_info))
            ListCard {
                InfoRow(Icons.Rounded.Lock, Color(0xFF6E4FC8), stringResource(R.string.about_privacy), stringResource(R.string.about_privacy_d))
                ListDivider()
                InfoRow(Icons.Rounded.Description, Color(0xFF55645B), stringResource(R.string.about_licences), stringResource(R.string.about_licences_d))
            }
        }
    }
}

@Composable
private fun InfoRow(icon: androidx.compose.ui.graphics.vector.ImageVector, bg: Color, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(16.dp).semantics(mergeDescendants = true) {}, verticalAlignment = Alignment.CenterVertically) {
        ColorIcon(icon, bg)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun LockScreen(onUnlock: () -> Unit) {
    LaunchedEffect(Unit) { onUnlock() }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).clickable(enabled = true, onClick = {}).testTag("lock_screen"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            AppIcon(96.dp)
            Spacer(Modifier.height(24.dp))
            Text(stringResource(R.string.locked_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.locked_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
            Spacer(Modifier.height(56.dp))
            Box(
                Modifier.size(84.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer).clickable(role = Role.Button, onClick = onUnlock),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.Fingerprint, stringResource(R.string.unlock), tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(44.dp)) }
        }
    }
}
