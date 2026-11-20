package com.sushant.gallery.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.Mail
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gallery.BuildConfig
import com.sushant.gallery.R
import com.sushant.gallery.data.Diagnostics
import com.sushant.gallery.data.Prefs
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.AppTopBar
import com.sushant.gallery.ui.components.GallerySheet
import com.sushant.gallery.ui.components.InfoBanner
import com.sushant.gallery.ui.components.SettingsGroup
import com.sushant.gallery.ui.components.SettingsRow
import com.sushant.gallery.ui.components.SwitchRow
import com.sushant.gallery.ui.theme.LocalSpacing
import com.sushant.gallery.ui.theme.ThemeMode

@Composable
private fun Page(title: String, nav: Nav, content: @Composable () -> Unit) {
    val sp = LocalSpacing.current
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        AppTopBar(title, onBack = { nav.back() })
        Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.TopCenter) {
            Column(
                Modifier.widthIn(max = sp.maxContentWidth).fillMaxWidth().verticalScroll(rememberScrollState())
                    .windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = sp.xl),
            ) { content() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val p = vm.prefs
    val theme by p.theme.collectAsStateWithLifecycle()
    val dynamic by p.dynamicColor.collectAsStateWithLifecycle()
    val cols by p.gridColumns.collectAsStateWithLifecycle()
    val hidden by p.showHiddenFiles.collectAsStateWithLifecycle()
    val strip by p.stripLocation.collectAsStateWithLifecycle()
    val fp by p.fingerprint.collectAsStateWithLifecycle()
    var colsSheet by rememberSaveable { mutableStateOf(false) }
    Page(stringResource(R.string.settings), nav) {
        SettingsGroup(stringResource(R.string.appearance))
        SettingsRow(Icons.Rounded.DarkMode, stringResource(R.string.theme))
        val modes = listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 4.dp).testTag("theme_seg")) {
            modes.forEachIndexed { i, (m, l) ->
                SegmentedButton(selected = theme == m, onClick = { p.setTheme(m) }, shape = SegmentedButtonDefaults.itemShape(i, modes.size), icon = {},
                    modifier = Modifier.testTag("theme_${m.name}")) { Text(stringResource(l), maxLines = 1) }
            }
        }
        if (Build.VERSION.SDK_INT >= 31) SwitchRow(Icons.Rounded.Palette, stringResource(R.string.wallpaper_colours), dynamic, { p.setDynamic(it) }, stringResource(R.string.wallpaper_colours_sub))

        SettingsGroup(stringResource(R.string.gallery))
        SettingsRow(Icons.Rounded.GridView, stringResource(R.string.photos_per_row), subtitle = cols.toString(), onClick = { colsSheet = true })
        SwitchRow(Icons.Rounded.Visibility, stringResource(R.string.show_hidden_files), hidden, { p.setShowHidden(it) }, stringResource(R.string.show_hidden_files_sub), tag = "sw_hidden")
        SwitchRow(Icons.Rounded.LocationOff, stringResource(R.string.remove_location_sharing), strip, { p.setStripLocation(it) }, stringResource(R.string.remove_location_sharing_sub))

        SettingsGroup(stringResource(R.string.recycle_bin))
        SettingsRow(Icons.Rounded.Delete, stringResource(R.string.recycle_bin), subtitle = stringResource(R.string.bin_keep, Prefs.BIN_DAYS), chevron = true, onClick = { nav.go("bin") })

        SettingsGroup(stringResource(R.string.hidden_album))
        SettingsRow(Icons.Rounded.Password, stringResource(if (vm.vault.hasPin) R.string.change_pin else R.string.set_pin), chevron = true, onClick = { nav.go("pin") })
        SwitchRow(Icons.Rounded.Fingerprint, stringResource(R.string.unlock_fingerprint), fp, { p.setFingerprint(it) })

        SettingsGroup(stringResource(R.string.privacy))
        SettingsRow(Icons.Rounded.FolderOpen, stringResource(R.string.file_access), subtitle = stringResource(R.string.file_access_sub), chevron = true, onClick = {
            val i = if (Build.VERSION.SDK_INT >= 30) Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${ctx.packageName}"))
            else Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))
            runCatching { ctx.startActivity(i) }
        })

        SettingsGroup(stringResource(R.string.about))
        SettingsRow(Icons.Rounded.BugReport, stringResource(R.string.diagnostics), subtitle = stringResource(R.string.diagnostics_sub), chevron = true, onClick = { nav.go("diag") }, modifier = Modifier.testTag("row_diag"))
        SettingsRow(Icons.Outlined.Info, stringResource(R.string.about_gallery), subtitle = stringResource(R.string.version_x, BuildConfig.VERSION_NAME), chevron = true, onClick = { nav.go("about") }, modifier = Modifier.testTag("row_about"))
    }
    if (colsSheet) GallerySheet({ colsSheet = false }, stringResource(R.string.photos_per_row)) {
        (Prefs.MIN_COLS..Prefs.MAX_COLS).forEach { n ->
            Row(
                Modifier.fillMaxWidth().clickable { p.setColumns(n); colsSheet = false }.heightIn(min = 52.dp).padding(horizontal = 12.dp)
                    .semantics(mergeDescendants = true) { role = Role.RadioButton },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = n == cols, onClick = null, modifier = Modifier.padding(12.dp))
                Text(n.toString(), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

@Composable
fun DiagnosticsScreen(vm: MainViewModel, nav: Nav) {
    val ctx = LocalContext.current
    val usage by vm.diag.usage.collectAsStateWithLifecycle()
    var crashes by remember { mutableStateOf(vm.diag.crashes()) }
    val sp = LocalSpacing.current
    val saved = stringResource(R.string.msg_log_saved)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri != null) runCatching {
            val f = vm.diag.exportFile()
            ctx.contentResolver.openOutputStream(uri)?.use { o -> f.inputStream().use { it.copyTo(o) } }
            vm.toast(saved)
        }
    }
    Page(stringResource(R.string.diagnostics), nav) {
        InfoBanner(Icons.Rounded.Shield, stringResource(R.string.diag_banner))
        SettingsGroup(stringResource(R.string.usage))
        val tiles = listOf(
            R.string.stat_opens to (usage[Diagnostics.OPENS] ?: 0), R.string.stat_flicked to (usage[Diagnostics.FLICKED] ?: 0),
            R.string.stat_restored to (usage[Diagnostics.RESTORED] ?: 0), R.string.stat_edited to (usage[Diagnostics.EDITED] ?: 0),
        )
        tiles.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(horizontal = sp.l, vertical = sp.xs), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { (l, v) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(14.dp)) {
                        Text(stringResource(l), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(String.format(java.util.Locale("en", "IN"), "%,d", v), style = MaterialTheme.typography.headlineMedium)
                    }
                }
            }
        }
        SettingsGroup(stringResource(R.string.crash_log))
        if (crashes.isEmpty()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = sp.l, vertical = sp.xs).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp)).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.no_crashes), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            crashes.take(10).forEach { c ->
                SettingsRow(Icons.Rounded.BugReport, c.title, subtitle = Diagnostics.fmt(c.time))
            }
            TextButton(onClick = { vm.diag.clearCrashes(); crashes = emptyList() }, modifier = Modifier.padding(horizontal = sp.m)) { Text(stringResource(R.string.clear_log)) }
        }
        OutlinedButton(onClick = { export.launch("gallery-log.txt") }, modifier = Modifier.padding(sp.l).testTag("export_log")) {
            Icon(Icons.Rounded.Download, null, Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.export_log))
        }
    }
}

@Composable
fun AboutScreen(nav: Nav) {
    val ctx = LocalContext.current
    val sp = LocalSpacing.current
    Page(stringResource(R.string.about), nav) {
        Column(Modifier.fillMaxWidth().padding(top = sp.xl, bottom = sp.s), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(96.dp).clip(RoundedCornerShape(26.dp)).background(androidx.compose.ui.graphics.Color(0xFFF1FAF8)), contentAlignment = Alignment.Center) {
                Image(painterResource(R.drawable.ic_launcher_fg), null, modifier = Modifier.size(144.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.version_x, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        Column(
            Modifier.fillMaxWidth().padding(sp.l).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
                .clip(RoundedCornerShape(16.dp))
                .clickable { runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com"))) } }
                .padding(16.dp).testTag("developer"),
        ) {
            Text(stringResource(R.string.developer), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text(stringResource(R.string.developed_by), style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Mail, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                Text("sushant.sare@gmail.com", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        SettingsRow(Icons.Rounded.Shield, stringResource(R.string.privacy_title), subtitle = stringResource(R.string.privacy_body))
        SettingsRow(Icons.Rounded.Description, stringResource(R.string.licences), subtitle = stringResource(R.string.licences_body))
    }
}
