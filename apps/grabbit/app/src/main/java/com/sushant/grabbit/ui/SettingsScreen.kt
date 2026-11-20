package com.sushant.grabbit.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.clickable
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
    var subs by remember { mutableStateOf(Prefs.embedSubs) }
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
                                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                            ) { Text(l) }
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
                SettingsSwitch("Embed subtitles", "English subtitles inside the video, when available", Icons.Rounded.Subtitles, subs) {
                    subs = it; Prefs.embedSubs = it
                }
                SettingsDivider()
                SettingsSwitch("Album art in MP3", "Uses the video thumbnail as the cover", Icons.Rounded.Album, thumb) {
                    thumb = it; Prefs.embedThumb = it
                }
                SettingsDivider()
                SettingsItem("Saved to", "Movies/Grabbit and Music/Grabbit, so files show in your gallery and music player",
                    Icons.Rounded.Folder)
            }
        }

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
                            site.name, if (logged) "Logged in" else "Not logged in",
                            leading = { LetterAvatar(site.name.take(1)) },
                        ) {
                            if (logged) OutlinedButton(onClick = {
                                Cookies.logout(ctx, site); logged = false; UiMessages.say("Logged out of ${site.name}")
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
