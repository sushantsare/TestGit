package com.sushant.grabbit.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HighQuality
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sushant.grabbit.BuildConfig
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.data.Sites
import com.sushant.grabbit.ui.components.BrandMark
import com.sushant.grabbit.ui.components.LetterAvatar
import com.sushant.grabbit.ui.components.RowButton
import com.sushant.grabbit.ui.components.ScreenTitle
import com.sushant.grabbit.ui.components.SectionLabel
import com.sushant.grabbit.ui.components.SettingsDivider
import com.sushant.grabbit.ui.components.SettingsGroup
import com.sushant.grabbit.ui.components.SettingsItem
import com.sushant.grabbit.ui.components.SettingsSwitch
import com.sushant.grabbit.ui.theme.LocalSizes
import com.sushant.grabbit.ui.theme.LocalSpacing
import com.sushant.grabbit.ui.theme.isDarkTheme

private val QUALITIES = listOf(
    "best" to "Best available", "h2160" to "4K", "h1440" to "2K", "h1080" to "1080p",
    "h720" to "720p", "h480" to "480p", "h360" to "360p", "audio" to "MP3 audio",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier) {
    val sp = LocalSpacing.current
    val ctx = LocalContext.current
    val theme by Prefs.themeFlow.collectAsState()
    val amoled by Prefs.amoledFlow.collectAsState()
    val dynamic by Prefs.dynamicFlow.collectAsState()
    val dark = isDarkTheme()
    val version by vm.engineVersion.collectAsState()
    val updating by vm.engineUpdating.collectAsState()
    var quality by remember { mutableStateOf(Prefs.defaultQuality) }
    var subLang by remember { mutableStateOf(Prefs.subLang) }
    var subMode by remember { mutableStateOf(Prefs.subMode) }
    var videoFormat by remember { mutableStateOf(Prefs.videoFormat) }
    var audioFormat by remember { mutableStateOf(Prefs.audioFormat) }
    var faster by remember { mutableStateOf(Prefs.fasterDownloads) }
    var sponsor by remember { mutableStateOf(Prefs.sponsorCats) }
    var sheet by remember { mutableStateOf<String?>(null) }
    var thumb by remember { mutableStateOf(Prefs.embedThumb) }
    var auto by remember { mutableStateOf(Prefs.autoUpdate) }
    var qualitySheet by remember { mutableStateOf(false) }

    LazyColumn(
        modifier.fillMaxSize().testTag("settingsList"),
        contentPadding = PaddingValues(start = sp.screen, end = sp.screen, bottom = sp.xl),
        verticalArrangement = Arrangement.spacedBy(sp.s),
    ) {
        item { ScreenTitle("Settings") }

        item { SectionLabel("Appearance") }
        item {
            SettingsGroup {
                Column(Modifier.padding(sp.l)) {
                    Text("Theme", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.size(sp.m))
                    val labels = listOf("System", "Light", "Dark")
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        labels.forEachIndexed { i, l ->
                            SegmentedButton(
                                selected = theme == i, onClick = { Prefs.theme = i },
                                shape = SegmentedButtonDefaults.itemShape(i, labels.size), icon = {},
                            ) { FitText(l) }
                        }
                    }
                }
                if (dark) SettingsSwitch("Pure black", "Darker dark mode for OLED screens", Icons.Rounded.Contrast, amoled) {
                    Prefs.amoled = it
                }
                if (Build.VERSION.SDK_INT >= 31) SettingsSwitch(
                    "Wallpaper colours", "Match the app to your wallpaper", Icons.Rounded.Palette, dynamic,
                ) { Prefs.dynamic = it }
            }
        }

        item { SectionLabel("Downloads") }
        item {
            SettingsGroup {
                SettingsItem(
                    "Default quality", QUALITIES.firstOrNull { it.first == quality }?.second ?: "Best available",
                    Icons.Rounded.HighQuality, onClick = { qualitySheet = true },
                ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                SettingsDivider()
                SettingsItem("Video format", VIDEO_FORMATS[videoFormat].second, Icons.Rounded.Movie, onClick = { sheet = "video" }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                SettingsDivider()
                SettingsItem("Audio format", AUDIO_FORMATS_SET.first { it.first == audioFormat }.second, Icons.Rounded.MusicNote,
                    onClick = { sheet = "audio" }) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                SettingsDivider()
                SettingsItem(
                    "Subtitles",
                    if (subLang.isEmpty()) "Off" else "${SUB_LANGS.firstOrNull { it.first == subLang }?.second ?: subLang} · " +
                        SUB_MODES.first { it.first == subMode }.second.lowercase(),
                    Icons.Rounded.Subtitles, onClick = { sheet = "subs" },
                ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                SettingsDivider()
                SettingsSwitch("Faster downloads", "Many connections at once, so big files finish sooner", Icons.Rounded.Speed, faster) {
                    faster = it; Prefs.fasterDownloads = it
                }
                SettingsDivider()
                BatteryRow()
                SettingsDivider()
                SettingsItem(
                    "SponsorBlock",
                    if (sponsor.isEmpty()) "Off · cut sponsor segments from YouTube videos"
                    else "Removes " + SPONSOR_CATS.filter { it.first in sponsor }.joinToString(", ") { it.second.lowercase() },
                    Icons.Rounded.ContentCut, onClick = { sheet = "sponsor" },
                ) { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                SettingsDivider()
                SettingsSwitch("Album art in MP3", "Uses the video thumbnail as the cover", Icons.Rounded.Album, thumb) {
                    thumb = it; Prefs.embedThumb = it
                }
            }
        }

        item { SectionLabel("Save location") }
        item { SettingsGroup { SaveLocationSettings() } }

        item { SectionLabel("Accounts") }
        item {
            SettingsGroup {
                SettingsItem("Log in for private videos",
                    "Needed for private posts, stories and age-restricted videos. Your login stays on this phone.",
                    Icons.Rounded.Lock)
                key(vm.loginTick) {
                    Sites.logins.forEach { site ->
                        var logged by remember { mutableStateOf(Cookies.isLoggedIn(site)) }
                        SettingsDivider()
                        SettingsItem(
                            site.name,
                            when {
                                !logged -> "Not logged in"
                                Cookies.sessionSaved(site) -> "Logged in · session saved ✓"
                                else -> "Login incomplete · log out and log in again"
                            },
                            leading = { LetterAvatar(site.name.take(1)) },
                        ) {
                            if (logged) OutlinedButton(onClick = {
                                Cookies.logout(site); logged = false; UiMessages.say("Logged out of ${site.name}")
                            }) { Text("Log out") }
                            else RowButton("Log in", onClick = { vm.loginSite = site })
                        }
                    }
                }
            }
        }

        item { SectionLabel("Download engine") }
        item {
            SettingsGroup {
                SettingsItem("yt-dlp", version ?: "Starting…", Icons.Rounded.Memory)
                SettingsDivider()
                SettingsSwitch("Auto-update daily", "Keeps downloads working when sites change", Icons.Rounded.Update, auto) {
                    auto = it; Prefs.autoUpdate = it
                }
                SettingsDivider()
                SettingsItem(
                    if (updating) "Updating…" else "Update engine now", "Get the latest site fixes",
                    Icons.Rounded.SystemUpdate, onClick = if (updating) null else ({ vm.updateEngine() }),
                ) { if (updating) CircularProgressIndicator(Modifier.size(LocalSizes.current.icon), strokeWidth = 2.5.dp) }
            }
        }

        item { SectionLabel("About") }
        item {
            SettingsGroup {
                Row(Modifier.fillMaxWidth().padding(sp.l), verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(48.dp)
                    Spacer(Modifier.width(sp.l))
                    Column {
                        Text("Grabbit", style = MaterialTheme.typography.titleLarge)
                        Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                SettingsDivider()
                SettingsItem("Developed by Sushant Kumar", null, Icons.Rounded.Person)
                SettingsDivider()
                SettingsItem("sushant.sare@gmail.com", "Send feedback", Icons.Rounded.Email, onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com"))) }
                })
                SettingsDivider()
                SettingsItem("Your data stays on this phone",
                    "Download only content you have the right to save. Powered by yt-dlp and FFmpeg.", Icons.Rounded.Info)
            }
        }
    }

    when (sheet) {
        "video" -> ChoiceSheet("Video format", "Used for every video download", VIDEO_FORMATS.map { "${it.first}" to it.second },
            VIDEO_FORMAT_NOTES, videoFormat.toString(), { videoFormat = it.toInt(); Prefs.videoFormat = videoFormat }) { sheet = null }
        "audio" -> ChoiceSheet("Audio format", "Used when you choose Audio", AUDIO_FORMATS_SET, AUDIO_FORMAT_NOTES, audioFormat,
            { audioFormat = it; Prefs.audioFormat = it }) { sheet = null }
        "subs" -> SubtitleDefaultsSheet(subLang, subMode, { subLang = it; Prefs.subLang = it }, { subMode = it; Prefs.subMode = it }) { sheet = null }
        "sponsor" -> SponsorSheet(sponsor, { sponsor = it; Prefs.sponsorCats = it }) { sheet = null }
    }

    if (qualitySheet) {
        val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(onDismissRequest = { qualitySheet = false }, sheetState = state) {
            Column(Modifier.navigationBarsPadding().padding(bottom = sp.l)) {
                Text("Default quality", style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = sp.xl, vertical = sp.s))
                Text("Pre-selected when you grab a video", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = sp.xl))
                Spacer(Modifier.size(sp.s))
                QUALITIES.forEach { (k, l) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 56.dp)
                            .clickable(role = Role.RadioButton) { quality = k; Prefs.defaultQuality = k; qualitySheet = false }
                            .padding(horizontal = sp.xl),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = quality == k, onClick = null)
                        Spacer(Modifier.width(sp.l))
                        Text(l, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        }
    }
}

private val VIDEO_FORMATS = listOf(0 to "Most compatible", 1 to "Highest quality")
private val VIDEO_FORMAT_NOTES = mapOf(
    "0" to "H.264 MP4. Plays in every gallery, player and app",
    "1" to "VP9/AV1 when available. Sometimes sharper, may not play everywhere",
)
private val AUDIO_FORMATS_SET = listOf("mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus", "best" to "Original")
private val AUDIO_FORMAT_NOTES = mapOf(
    "mp3" to "Plays everywhere",
    "m4a" to "Smaller files, same quality as MP3",
    "opus" to "Smallest files, best quality per MB",
    "best" to "The site's own audio file, no conversion",
)
private val SUB_LANGS = listOf("" to "Off", "en" to "English", "hi" to "Hindi", "bn" to "Bengali", "ta" to "Tamil",
    "te" to "Telugu", "mr" to "Marathi", "ur" to "Urdu", "es" to "Spanish", "fr" to "French", "ar" to "Arabic")
private val SUB_MODES = listOf("embed" to "Inside the video", "file" to "Separate file", "both" to "Both")
private val SPONSOR_CATS = listOf(
    "sponsor" to "Sponsors", "selfpromo" to "Self-promotion", "interaction" to "Subscribe reminders",
    "intro" to "Intros", "outro" to "Outros / end cards", "music_offtopic" to "Non-music parts in songs",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceSheet(
    title: String, subtitle: String, options: List<Pair<String, String>>, notes: Map<String, String>,
    selected: String, onPick: (String) -> Unit, onClose: () -> Unit,
) {
    val sp = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = sp.l)) {
            Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = sp.xl, vertical = sp.s))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = sp.xl))
            Spacer(Modifier.size(sp.s))
            options.forEach { (k, l) ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp)
                        .clickable(role = Role.RadioButton) { onPick(k); onClose() }
                        .padding(horizontal = sp.xl, vertical = sp.s),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = selected == k, onClick = null)
                    Spacer(Modifier.width(sp.l))
                    Column {
                        Text(l, style = MaterialTheme.typography.bodyLarge)
                        notes[k]?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleDefaultsSheet(
    lang: String, mode: String, onLang: (String) -> Unit, onMode: (String) -> Unit, onClose: () -> Unit,
) {
    val sp = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = sp.l)) {
            Text("Subtitles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = sp.xl, vertical = sp.s))
            Text("Pre-selected for every video. You can still pick another language per video.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = sp.xl))
            Spacer(Modifier.size(sp.m))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = sp.xl)) {
                SUB_MODES.forEachIndexed { i, (k, l) ->
                    SegmentedButton(selected = mode == k, onClick = { onMode(k) }, shape = SegmentedButtonDefaults.itemShape(i, SUB_MODES.size), icon = {}) {
                        FitText(l.replace("Inside the video", "Inside video"))
                    }
                }
            }
            Spacer(Modifier.size(sp.s))
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                SUB_LANGS.forEach { (k, l) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.RadioButton) { onLang(k) }
                            .padding(horizontal = sp.xl),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = lang == k, onClick = null)
                        Spacer(Modifier.width(sp.l))
                        Text(l, style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = sp.xl)) { Text("Done") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SponsorSheet(cats: Set<String>, onChange: (Set<String>) -> Unit, onClose: () -> Unit) {
    val sp = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = sp.l)) {
            Text("SponsorBlock", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = sp.xl, vertical = sp.s))
            Text("Cuts these parts out of YouTube videos, using SponsorBlock's community-marked segments. Untick all to turn it off.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = sp.xl))
            Spacer(Modifier.size(sp.s))
            SPONSOR_CATS.forEach { (k, l) ->
                val on = k in cats
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 52.dp)
                        .clickable(role = Role.Checkbox) { onChange(if (on) cats - k else cats + k) }
                        .padding(horizontal = sp.xl),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = on, onCheckedChange = null)
                    Spacer(Modifier.width(sp.l))
                    Text(l, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = sp.xl)) { Text("Done") }
        }
    }
}


/** Battery: lets downloads keep full speed with the screen off. */
@Composable
private fun BatteryRow() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val pm = remember { ctx.getSystemService(android.os.PowerManager::class.java) }
    var free by remember { mutableStateOf(pm.isIgnoringBatteryOptimizations(ctx.packageName)) }
    androidx.lifecycle.compose.LifecycleResumeEffect(Unit) {
        free = pm.isIgnoringBatteryOptimizations(ctx.packageName)
        onPauseOrDispose { }
    }
    SettingsItem(
        "Download with screen off",
        if (free) "Allowed · downloads keep full speed in the background"
        else "Tap to allow, so Android doesn't slow downloads when the screen is off",
        Icons.Rounded.BatteryChargingFull,
        onClick = if (free) null else ({ BatteryAccess.request(ctx) }),
    ) {
        if (free) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
        else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}


/** Default folders, "Keep my last folder", and the remembered folders (each can be forgotten). */
@Composable
private fun SaveLocationSettings() {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val recent by com.sushant.grabbit.data.Folders.recent.collectAsState()
    var keep by remember { mutableStateOf(Prefs.keepLastFolder) }
    SettingsItem("Default folders", "Videos › Movies/Grabbit · Audio › Music/Grabbit · Images › Pictures/Grabbit", Icons.Rounded.Folder)
    SettingsDivider()
    SettingsSwitch("Keep my last folder", "The next download starts where the last one was saved", Icons.Rounded.History, keep) {
        keep = it; Prefs.keepLastFolder = it
        if (!it) com.sushant.grabbit.data.Folders.current.value = ""
    }
    if (recent.isEmpty()) {
        SettingsDivider()
        SettingsItem("No remembered folders", "Pick a folder with Save to when you download; your last 3 show up here", Icons.Rounded.FolderOpen)
    }
    recent.forEach { f ->
        SettingsDivider()
        SettingsItem(f.name, f.path, Icons.Rounded.FolderOpen) {
            IconButton(onClick = { com.sushant.grabbit.data.Folders.forget(ctx, f.uri) }, modifier = Modifier.testTag("forget_${f.name}")) {
                Icon(Icons.Rounded.Close, "Forget ${f.name}", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
