package com.sushant.tarang.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.BuildConfig
import com.sushant.tarang.Diagnostics
import com.sushant.tarang.TarangApp
import com.sushant.tarang.data.*
import com.sushant.tarang.ui.LocalDockPadding
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.R0
import com.sushant.tarang.ui.components.*
import com.sushant.tarang.ui.theme.LocalIsDark
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
private fun SwitchRow(title: String, sub: String?, on: Boolean, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) =
    ListItemRow(title, sub, icon, onClick = { onChange(!on) }, enabled = enabled, modifier = Modifier.testTag(tag), trailing = { Switch(on, onChange, enabled = enabled) })

@UnstableApi
@Composable
fun SettingsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val s by vm.settings.collectAsState()
    val lib by vm.library.collectAsState()
    val scanning by vm.scanning.collectAsState()
    var dialog by remember { mutableStateOf("") }
    val dark = LocalIsDark.current
    val treePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching { ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
            vm.setSettings { it.copy(lyricsTree = uri.toString()) }; vm.snack.tryEmit("Lyrics folder set")
        }
    }
    val fmt = SimpleDateFormat("d MMM, h:mm a", Locale.ENGLISH)

    Column(Modifier.fillMaxSize().testTag("settings")) {
        InnerTopBar("Settings", nav::back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockPadding.current)) {
            SectionLabel("Appearance")
            ListItemRow("Theme", s.themeMode.label, Icons.Rounded.Contrast, onClick = { dialog = "theme" }, modifier = Modifier.testTag("set_theme"))
            if (dark) SwitchRow("Pure black", "Darker dark mode for OLED screens", s.pureBlack, "set_pure_black", Icons.Rounded.DarkMode) { v -> vm.setSettings { it.copy(pureBlack = v) } }
            if (Build.VERSION.SDK_INT >= 31) SwitchRow("Use wallpaper colours", "The app takes its colours from your wallpaper", s.wallpaperColors, "set_wallpaper", Icons.Rounded.Palette) { v -> vm.setSettings { it.copy(wallpaperColors = v) } }

            SectionLabel("Playback")
            SwitchRow("Gapless playback", "No silence between songs of the same album", s.gapless, "set_gapless", Icons.Rounded.LinearScale) { v -> vm.setSettings { it.copy(gapless = v, crossfadeSec = if (v) 0 else it.crossfadeSec) } }
            ListItemRow("Crossfade", if (s.crossfadeSec == 0) "Off" else "Fade out and in over ${s.crossfadeSec} s", Icons.Rounded.Gradient,
                onClick = { dialog = "crossfade" }, modifier = Modifier.testTag("set_crossfade"))
            SwitchRow("Resume on headphones", "Start playing when headphones or Bluetooth connect", s.resumeOnConnect, "set_resume", Icons.Rounded.Headphones) { v -> vm.setSettings { it.copy(resumeOnConnect = v) } }

            SectionLabel("Library")
            ListItemRow("Folders to scan", "${plural(lib.folders.size, "folder")} shown", Icons.Rounded.FolderOpen, onClick = { nav.go(R0.SCAN_FOLDERS) }, modifier = Modifier.testTag("set_folders"))
            ListItemRow("Skip short audio", if (s.skipShortSec == 0) "Show every file" else "Hide files shorter than ${s.skipShortSec} s", Icons.Rounded.ContentCut,
                onClick = { dialog = "short" }, modifier = Modifier.testTag("set_short"))
            ListItemRow("Default sort", s.defaultSort.label, Icons.Rounded.Sort, onClick = { dialog = "sort" }, modifier = Modifier.testTag("set_sort"))
            ListItemRow("Lyrics folder", if (s.lyricsTree == null) "Pick the folder where your .lrc files are" else Uri.parse(s.lyricsTree).lastPathSegment?.substringAfter(':')?.ifBlank { "Phone storage" } ?: "Set",
                Icons.AutoMirrored.Rounded.Notes, onClick = { treePicker.launch(null) }, modifier = Modifier.testTag("set_lyrics_folder"),
                trailing = if (s.lyricsTree != null) { { IconButton(onClick = { vm.setSettings { it.copy(lyricsTree = null) } }) { Icon(Icons.Rounded.Close, "Forget lyrics folder") } } } else null)
            ListItemRow("Rescan now", if (scanning) "Scanning…" else vm.prefs.lastScanAt.takeIf { it > 0 }?.let { "Last scan ${fmt.format(Date(it))} · ${plural(lib.songs.size, "song")}" } ?: "Look for new songs",
                Icons.Rounded.Refresh, onClick = { vm.rescan() }, enabled = !scanning, modifier = Modifier.testTag("set_rescan"))

            SectionLabel("Notification")
            ListItemRow("Extra buttons", s.notifButtons.joinToString(" · ") { it.label }, Icons.Rounded.Notifications, onClick = { dialog = "buttons" }, modifier = Modifier.testTag("set_buttons"))
            SwitchRow("Cover on lock screen", "Show the song's cover on the lock screen", s.lockCover, "set_lock_cover", Icons.Rounded.Lock) { v -> vm.setSettings { it.copy(lockCover = v) } }

            SectionLabel("Stats")
            ListItemRow("When a play counts", "After 30 seconds of real listening", Icons.Rounded.Timer)
            ListItemRow("Reset stats", "Clear all listening history", Icons.Rounded.RestartAlt, onClick = { dialog = "reset" }, modifier = Modifier.testTag("set_reset"))

            Row(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(16.dp)) {
                Icon(Icons.Rounded.Shield, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                Spacer(Modifier.width(14.dp))
                Text("Tarang has no internet access. Your music, stats and lyrics never leave your phone.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            ListItemRow("Diagnostics", "App details and crash reports", Icons.Rounded.BugReport, onClick = { nav.go(R0.DIAGNOSTICS) }, modifier = Modifier.testTag("set_diagnostics"))
            ListItemRow("About", "Version ${BuildConfig.VERSION_NAME}", Icons.Rounded.Info, onClick = { nav.go(R0.ABOUT) }, modifier = Modifier.testTag("set_about"))
        }
    }

    when (dialog) {
        "theme" -> ChoiceDialog("Theme", ThemeMode.entries, s.themeMode, { it.label }, { dialog = "" }) { m -> vm.setSettings { it.copy(themeMode = m) }; dialog = "" }
        "crossfade" -> ChoiceDialog("Crossfade", listOf(0, 2, 4, 6, 8, 10), s.crossfadeSec, { if (it == 0) "Off" else "$it seconds" }, { dialog = "" }) { v ->
            vm.setSettings { it.copy(crossfadeSec = v, gapless = if (v > 0) false else it.gapless) }; dialog = ""
        }
        "short" -> ChoiceDialog("Skip short audio", listOf(0, 10, 30, 60), s.skipShortSec, { if (it == 0) "Show every file" else "Shorter than $it seconds" }, { dialog = "" }) { v -> vm.setSettings { it.copy(skipShortSec = v) }; dialog = "" }
        "sort" -> ChoiceDialog("Default sort", SongSort.entries, s.defaultSort, { it.label }, { dialog = "" }) { v -> vm.setSettings { it.copy(defaultSort = v) }; dialog = "" }
        "reset" -> ConfirmDialog("Reset stats?", "All listening history, play counts and streaks are cleared. Your songs and playlists stay.", "Reset", { dialog = "" }, { dialog = ""; vm.resetStats() })
        "buttons" -> {
            var pick by remember { mutableStateOf(s.notifButtons.toSet()) }
            AlertDialog(onDismissRequest = { dialog = "" }, title = { Text("Extra buttons") }, text = {
                Column {
                    Text("Choose 2 buttons to show next to play, previous and next.", style = MaterialTheme.typography.bodyMedium)
                    NotifButton.entries.forEach { b ->
                        val on = b in pick
                        Row(Modifier.fillMaxWidth().clickable(enabled = on || pick.size < 2) { pick = if (on) pick - b else pick + b }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(on, { pick = if (on) pick - b else pick + b }, enabled = on || pick.size < 2)
                            Text(b.label)
                        }
                    }
                }
            }, confirmButton = {
                TextButton(enabled = pick.size == 2, onClick = { vm.setSettings { it.copy(notifButtons = NotifButton.entries.filter { b -> b in pick }) }; dialog = "" }) { Text("Save") }
            }, dismissButton = { TextButton(onClick = { dialog = "" }) { Text("Cancel") } })
        }
    }
}

@UnstableApi
@Composable
fun DiagnosticsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val lib by vm.library.collectAsState()
    var crashes by remember { mutableStateOf(Diagnostics.crashes(TarangApp.instance)) }
    var clear by remember { mutableStateOf(false) }
    val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)
    val info = listOf(
        "App" to "Tarang ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
        "Android" to "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
        "Phone" to "${Build.MANUFACTURER} ${Build.MODEL}",
        "Songs" to "${lib.songs.size} shown · ${lib.all.size} found",
        "Last scan" to (vm.prefs.lastScanAt.takeIf { it > 0 }?.let { "${fmt.format(Date(it))} · took ${vm.prefs.lastScanMs} ms" } ?: "Never"),
    )
    Column(Modifier.fillMaxSize().testTag("diagnostics")) {
        InnerTopBar("Diagnostics", nav::back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockPadding.current)) {
            info.forEach { (k, v) -> ListItemRow(k, v) }
            SectionLabel("Crash reports")
            if (crashes.isEmpty()) Text("No crashes recorded. 🎉", Modifier.padding(horizontal = 20.dp, vertical = 8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            crashes.reversed().take(5).forEach { c ->
                Text(c.take(1200), fontFamily = FontFamily.Monospace, fontSize = 11.sp, lineHeight = 15.sp,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer).padding(12.dp))
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                FilledTonalButton(onClick = {
                    val text = info.joinToString("\n") { "${it.first}: ${it.second}" } + "\n\n" + crashes.takeLast(3).joinToString("\n")
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT, "Tarang diagnostics").putExtra(Intent.EXTRA_TEXT, text), "Share report"))
                }, Modifier.testTag("share_report")) { Icon(Icons.Rounded.Share, null); Spacer(Modifier.width(8.dp)); Text("Share report") }
                if (crashes.isNotEmpty()) OutlinedButton(onClick = { clear = true }) { Text("Clear reports") }
            }
        }
    }
    if (clear) ConfirmDialog("Clear crash reports?", "The saved crash details are deleted from this phone.", "Clear", { clear = false }, {
        clear = false; Diagnostics.file(TarangApp.instance).delete(); crashes = emptyList()
    })
}

@Composable
fun AboutScreen() {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxSize().testTag("about")) {
        InnerTopBar("About", nav::back)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp).padding(bottom = LocalDockPadding.current), horizontalAlignment = Alignment.CenterHorizontally) {
            AppIcon(112.dp)
            Spacer(Modifier.height(16.dp))
            Text("Tarang", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text("An offline music player for the songs on your phone.", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer)) {
                ListItemRow("Developed by", "Sushant Kumar", Icons.Rounded.Person)
                ListItemRow("Email", "sushant.sare@gmail.com", Icons.Rounded.Email, modifier = Modifier.testTag("about_email"), onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com")).putExtra(Intent.EXTRA_SUBJECT, "Tarang")) }
                })
            }
            Spacer(Modifier.height(20.dp))
            Text("Built with Jetpack Compose, Media3 and Room. Song details editing uses jaudiotagger (LGPL). Fonts: Google Sans Flex (OFL).",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}
