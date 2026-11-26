@file:OptIn(androidx.media3.common.util.UnstableApi::class)

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
import androidx.compose.ui.draw.alpha
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

    // this week's listening for the Stats card at the top
    val period0 = remember { vm.repo.statsPeriod(0) }
    val rows by remember { vm.repo.dao.playsSince(minOf(period0.prevFrom, System.currentTimeMillis() - 400L * 86_400_000L)) }.collectAsState(null)
    val week = remember(rows, lib) { rows?.let { buildStats(it, period0, 0, lib.byId, System.currentTimeMillis()) } }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().testTag("settings")) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = LocalDockPadding.current)) {
            PageHeader("Settings", Modifier.statusBarsPadding(), onBack = nav::back)
            // ---- Stats card (Stats lives here now)
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(32.dp)).background(cs.primaryContainer)
                .clickable(onClickLabel = "Open stats") { nav.go(R0.STATS) }.padding(horizontal = 20.dp, vertical = 18.dp).testTag("set_stats"),
                verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Insights, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp))
                        Text("Your stats", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
                    }
                    Text(week?.let { formatListen(it.totalMs) } ?: "…", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = cs.onPrimaryContainer)
                    Text("listened this week" + (week?.streak?.takeIf { it > 1 }?.let { " · $it-day streak" } ?: ""), style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer)
                }
                week?.bars?.takeIf { it.isNotEmpty() }?.let { b -> Box(Modifier.width(96.dp)) { Bars(b, cs.primary, cs.onPrimaryContainer, height = 56.dp, labels = false) } }
                Icon(Icons.Rounded.ChevronRight, null, tint = cs.onPrimaryContainer, modifier = Modifier.padding(start = 8.dp))
            }

            SectionLabel("Playback")
            SettingsGroup {
                GroupSwitch("Gapless playback", "No silence between songs of the same album", s.gapless, "set_gapless", Icons.Rounded.LinearScale, Tone.BLUE) { v -> vm.setSettings { it.copy(gapless = v, crossfadeSec = if (v) 0 else it.crossfadeSec) } }
                GroupRow("Crossfade", if (s.crossfadeSec == 0) "Off" else "Fade out and in over ${s.crossfadeSec} s", Icons.Rounded.Gradient, Tone.BLUE, "set_crossfade") { dialog = "crossfade" }
                GroupSwitch("Resume on headphones", "Start playing when headphones or Bluetooth connect", s.resumeOnConnect, "set_resume", Icons.Rounded.Headphones, Tone.BLUE) { v -> vm.setSettings { it.copy(resumeOnConnect = v) } }
                GroupSwitch("Keep shuffle on", "Shuffle stays on for new lists and after closing the app", s.persistentShuffle, "set_persistent_shuffle", Icons.Rounded.Shuffle, Tone.BLUE) { v -> vm.setSettings { it.copy(persistentShuffle = v) } }
            }

            SectionLabel("Lyrics")
            SettingsGroup {
                GroupSwitch("Always save lyrics into the song file", "Lyrics you add are written into the song, so other players show them too", s.alwaysSaveLyrics, "set_always_save_lyrics", Icons.Rounded.Save, Tone.PURPLE) { v -> vm.setSettings { it.copy(alwaysSaveLyrics = v) } }
                GroupRow("Lyrics folder", if (s.lyricsTree == null) "Pick the folder where your .lrc files are" else Uri.parse(s.lyricsTree).lastPathSegment?.substringAfter(':')?.ifBlank { "Phone storage" } ?: "Set",
                    Icons.AutoMirrored.Rounded.Notes, Tone.PURPLE, "set_lyrics_folder",
                    trailing = if (s.lyricsTree != null) { { IconButton(onClick = { vm.setSettings { it.copy(lyricsTree = null) } }) { Icon(Icons.Rounded.Close, "Forget lyrics folder") } } } else null) { treePicker.launch(null) }
            }

            SectionLabel("Appearance")
            SettingsGroup {
                GroupRow("Theme", s.themeMode.label, Icons.Rounded.Contrast, Tone.PINK, "set_theme") { dialog = "theme" }
                if (dark) GroupSwitch("Pure black", "Darker dark mode for OLED screens", s.pureBlack, "set_pure_black", Icons.Rounded.DarkMode, Tone.PINK) { v -> vm.setSettings { it.copy(pureBlack = v) } }
                if (Build.VERSION.SDK_INT >= 31) GroupSwitch("Use wallpaper colours", "The app takes its colours from your wallpaper", s.wallpaperColors, "set_wallpaper", Icons.Rounded.Palette, Tone.PINK) { v -> vm.setSettings { it.copy(wallpaperColors = v) } }
                GroupRow("Cover swipe", if (s.carouselPeek) "One peek: the next cover shows at the edge" else "No peek: one cover at a time", Icons.Rounded.ViewCarousel, Tone.PINK, "set_carousel") { dialog = "carousel" }
            }

            SectionLabel("Library & folders")
            SettingsGroup {
                GroupRow("Folders to scan", "${plural(lib.folders.size, "folder")} shown", Icons.Rounded.FolderOpen, Tone.GREEN, "set_folders") { nav.go(R0.SCAN_FOLDERS) }
                GroupRow("Skip short audio", if (s.skipShortSec == 0) "Show every file" else "Hide files shorter than ${s.skipShortSec} s", Icons.Rounded.ContentCut, Tone.GREEN, "set_short") { dialog = "short" }
                GroupRow("Default sort", s.defaultSort.label, Icons.Rounded.Sort, Tone.GREEN, "set_sort") { dialog = "sort" }
                GroupRow("Rescan now", if (scanning) "Scanning…" else vm.prefs.lastScanAt.takeIf { it > 0 }?.let { "Last scan ${fmt.format(Date(it))} · ${plural(lib.songs.size, "song")}" } ?: "Look for new songs",
                    Icons.Rounded.Refresh, Tone.GREEN, "set_rescan", enabled = !scanning) { vm.rescan() }
            }

            SectionLabel("Notification")
            SettingsGroup {
                GroupRow("Extra buttons", s.notifButtons.joinToString(" · ") { it.label }, Icons.Rounded.Notifications, Tone.AMBER, "set_buttons") { dialog = "buttons" }
                GroupSwitch("Cover on lock screen", "Show the song's cover on the lock screen", s.lockCover, "set_lock_cover", Icons.Rounded.Lock, Tone.AMBER) { v -> vm.setSettings { it.copy(lockCover = v) } }
            }

            SectionLabel("Feel")
            SettingsGroup {
                GroupSwitch("Haptics", "Small taps when you press buttons and change songs", s.haptics, "set_haptics", Icons.Rounded.Vibration, Tone.GREY) { v -> vm.setSettings { it.copy(haptics = v) } }
            }

            SectionLabel("Stats")
            SettingsGroup {
                GroupRow("When a play counts", "After 30 seconds of real listening", Icons.Rounded.Timer, Tone.GREY, "set_count_info", onClick = null)
                GroupRow("Reset stats", "Clear all listening history", Icons.Rounded.RestartAlt, Tone.GREY, "set_reset") { dialog = "reset" }
            }

            Row(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(24.dp)).background(cs.secondaryContainer).padding(16.dp)) {
                Icon(Icons.Rounded.Shield, null, tint = cs.onSecondaryContainer)
                Spacer(Modifier.width(14.dp))
                Text("Tarang has no internet access. Your music, stats and lyrics never leave your phone.", style = MaterialTheme.typography.bodyMedium, color = cs.onSecondaryContainer)
            }
            SettingsGroup {
                GroupRow("About", "Version, developer", Icons.Rounded.Info, Tone.GREY, "set_about") { nav.go(R0.ABOUT) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (dialog) {
        "theme" -> ChoiceDialog("Theme", ThemeMode.entries, s.themeMode, { it.label }, { dialog = "" }) { m -> vm.setSettings { it.copy(themeMode = m) }; dialog = "" }
        "crossfade" -> ChoiceDialog("Crossfade", listOf(0, 2, 4, 6, 8, 10), s.crossfadeSec, { if (it == 0) "Off" else "$it seconds" }, { dialog = "" }) { v ->
            vm.setSettings { it.copy(crossfadeSec = v, gapless = if (v > 0) false else it.gapless) }; dialog = ""
        }
        "short" -> ChoiceDialog("Skip short audio", listOf(0, 10, 30, 60), s.skipShortSec, { if (it == 0) "Show every file" else "Shorter than $it seconds" }, { dialog = "" }) { v -> vm.setSettings { it.copy(skipShortSec = v) }; dialog = "" }
        "sort" -> ChoiceDialog("Default sort", SongSort.entries, s.defaultSort, { it.label }, { dialog = "" }) { v -> vm.setSettings { it.copy(defaultSort = v) }; dialog = "" }
        "carousel" -> ChoiceDialog("Cover swipe", listOf(false, true), s.carouselPeek, { if (it) "One peek (next cover at the edge)" else "No peek (one cover at a time)" }, { dialog = "" }) { v -> vm.setSettings { it.copy(carouselPeek = v) }; dialog = "" }
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
            Spacer(Modifier.height(6.dp))
            Text("Your offline music player", style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(28.dp))
            // tapping Version 5 times opens Diagnostics (not mentioned anywhere in the app)
            var taps by remember { mutableIntStateOf(0) }
            var lastTap by remember { mutableLongStateOf(0L) }
            SettingsGroup(Modifier.padding(horizontal = 0.dp)) {
                GroupRow("Version", "${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})", Icons.Rounded.NewReleases, Tone.GREY, "about_version") {
                    val now = System.currentTimeMillis()
                    taps = if (now - lastTap < 1500) taps + 1 else 1
                    lastTap = now
                    if (taps >= 5) { taps = 0; nav.go(R0.DIAGNOSTICS) }
                }
                GroupRow("Developed by", "Sushant Kumar", Icons.Rounded.Person, Tone.GREY, "about_dev", onClick = null)
                GroupRow("Email", "sushant.sare@gmail.com", Icons.Rounded.Email, Tone.GREY, "about_email") {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com")).putExtra(Intent.EXTRA_SUBJECT, "Tarang")) }
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Built with Jetpack Compose, Media3 and Room. Song details editing uses jaudiotagger (LGPL). Fonts: Google Sans Flex (OFL).",
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Colour pairs for the round icons in Settings (Android 16 style). */
enum class Tone { BLUE, PURPLE, PINK, GREEN, AMBER, GREY }

@Composable
private fun toneColors(t: Tone): Pair<androidx.compose.ui.graphics.Color, androidx.compose.ui.graphics.Color> {
    val dark = LocalIsDark.current
    fun c(v: Long) = androidx.compose.ui.graphics.Color(v)
    return when (t) {
        Tone.BLUE -> if (dark) c(0xFF1F3A5C) to c(0xFFB9D7FF) else c(0xFFCDE6FF) to c(0xFF0A4A7A)
        Tone.PURPLE -> if (dark) c(0xFF3D2A55) to c(0xFFE6C9FF) else c(0xFFF3D9FF) to c(0xFF5A2A7A)
        Tone.PINK -> if (dark) c(0xFF55283A) to c(0xFFFFC9DC) else c(0xFFFFD8E4) to c(0xFF7A1E3E)
        Tone.GREEN -> if (dark) c(0xFF1F4430) to c(0xFFB5EDC6) else c(0xFFD2E8D4) to c(0xFF1F4A2F)
        Tone.AMBER -> if (dark) c(0xFF4A3414) to c(0xFFFFD9A8) else c(0xFFFFDDB0) to c(0xFF7A3E00)
        Tone.GREY -> if (dark) c(0xFF2E3030) to c(0xFFDADADA) else c(0xFFE3E3E3) to c(0xFF333333)
    }
}

/** A group of settings rows: big rounded outer corners, tight inner ones, small gaps (Android 16 style). */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 14.dp).clip(RoundedCornerShape(26.dp)), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

@Composable
private fun GroupRow(title: String, sub: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Tone, tag: String, enabled: Boolean = true,
                     trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)?) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = toneColors(tone)
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(cs.surfaceContainer)
        .let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
        .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 10.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(androidx.compose.foundation.shape.CircleShape).background(bg), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = fg, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.4f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = cs.onSurface)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
private fun GroupSwitch(title: String, sub: String?, on: Boolean, tag: String, icon: androidx.compose.ui.graphics.vector.ImageVector, tone: Tone, onChange: (Boolean) -> Unit) =
    GroupRow(title, sub, icon, tone, tag, trailing = { Switch(on, onChange) }) { onChange(!on) }
