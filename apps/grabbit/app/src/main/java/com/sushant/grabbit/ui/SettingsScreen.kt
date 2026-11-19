package com.sushant.grabbit.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sushant.grabbit.BuildConfig
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.data.Sites

@Composable
fun SettingsScreen(vm: MainViewModel, modifier: Modifier) {
    val ctx = LocalContext.current
    val theme by Prefs.themeFlow.collectAsState()
    val version by vm.engineVersion.collectAsState()
    val updating by vm.engineUpdating.collectAsState()
    var quality by remember { mutableStateOf(Prefs.defaultQuality) }
    var subs by remember { mutableStateOf(Prefs.embedSubs) }
    var thumb by remember { mutableStateOf(Prefs.embedThumb) }
    var auto by remember { mutableStateOf(Prefs.autoUpdate) }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text("Settings", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }

        item {
            Section("Appearance") {
                Text("Theme", style = MaterialTheme.typography.bodyMedium)
                val labels = listOf("System", "Light", "Dark")
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    labels.forEachIndexed { i, l ->
                        SegmentedButton(
                            selected = theme == i,
                            onClick = { Prefs.theme = i },
                            shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                        ) { Text(l) }
                    }
                }
            }
        }

        item {
            Section("Downloads") {
                QualityPicker(quality) { quality = it; Prefs.defaultQuality = it }
                ToggleRow("Embed subtitles", "English/Hindi subtitles inside the video, when available", subs) {
                    subs = it; Prefs.embedSubs = it
                }
                ToggleRow("Album art in MP3", "Adds the video thumbnail as the song cover", thumb) {
                    thumb = it; Prefs.embedThumb = it
                }
                Text(
                    "Videos are saved in Movies/Grabbit and audio in Music/Grabbit, so they appear in your gallery and music player.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item {
            Section("Accounts") {
                Text(
                    "Log in to download private posts, stories and age-restricted videos. Your login stays only on this phone.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                key(vm.loginTick) {
                    Sites.logins.forEachIndexed { i, site ->
                        var logged by remember { mutableStateOf(Cookies.isLoggedIn(site)) }
                        if (i > 0) HorizontalDivider()
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(site.name, fontWeight = FontWeight.SemiBold)
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (logged) Icon(Icons.Rounded.CheckCircle, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                                    if (logged) Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (logged) "Logged in" else "Not logged in",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (logged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            if (logged) {
                                OutlinedButton(onClick = { Cookies.logout(ctx, site); logged = false; ctx.toast("Logged out of ${site.name}") }) {
                                    Text("Log out")
                                }
                            } else {
                                FilledTonalButton(onClick = { vm.loginSite = site }) { Text("Log in") }
                            }
                        }
                    }
                }
            }
        }

        item {
            Section("Download engine") {
                Text("yt-dlp version: ${version ?: "starting…"}", style = MaterialTheme.typography.bodyMedium)
                ToggleRow("Auto-update daily", "Keeps downloads working when sites change", auto) {
                    auto = it; Prefs.autoUpdate = it
                }
                FilledTonalButton(onClick = { vm.updateEngine() }, enabled = !updating, modifier = Modifier.fillMaxWidth()) {
                    if (updating) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Rounded.SystemUpdate, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (updating) "Updating…" else "Update engine now")
                }
            }
        }

        item {
            Section("About") {
                Text("Grabbit v${BuildConfig.VERSION_NAME}", fontWeight = FontWeight.SemiBold)
                Text("Developed by Sushant Kumar", style = MaterialTheme.typography.bodyMedium)
                Row(
                    Modifier.fillMaxWidth().clickable {
                        runCatching {
                            ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com")))
                        }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Email, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("sushant.sare@gmail.com")
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:+918877612216"))) }
                    }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Phone, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("+91 88776 12216")
                }
                Row(
                    Modifier.fillMaxWidth().clickable { ctx.openLink("https://wa.me/918877612216") }.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                    Text("Message on WhatsApp")
                }
                Text(
                    "Download only content you have the right to save. Powered by yt-dlp and FFmpeg.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun QualityPicker(value: String, onPick: (String) -> Unit) {
    val opts = listOf("best" to "Best available", "h2160" to "4K", "h1440" to "2K", "h1080" to "1080p",
        "h720" to "720p", "h480" to "480p", "h360" to "360p", "audio" to "MP3 audio")
    var open by remember { mutableStateOf(false) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Default quality", style = MaterialTheme.typography.bodyLarge)
            Text("Pre-selected when you grab a video", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            TextButton(onClick = { open = true }) { Text(opts.firstOrNull { it.first == value }?.second ?: "Best available") }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
                opts.forEach { (k, l) ->
                    DropdownMenuItem(text = { Text(l) }, onClick = { open = false; onPick(k) })
                }
            }
        }
    }
}
