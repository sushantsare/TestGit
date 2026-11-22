@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.R
import com.sushant.tarang.data.*
import com.sushant.tarang.ui.LocalDockPadding
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.R0
import com.sushant.tarang.ui.components.*
import java.util.Calendar

// ------------------------------------------------------------------ Welcome / permission
@Composable
fun WelcomeScreen(denied: Boolean, permanentlyDenied: Boolean, onAllow: () -> Unit, onOpenSettings: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier.fillMaxSize().background(cs.background).systemBarsPadding().padding(horizontal = 32.dp).testTag("welcome"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        AppIcon(132.dp)
        Spacer(Modifier.height(28.dp))
        Text(if (denied) "Tarang needs your music" else "Welcome to Tarang", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(Modifier.height(10.dp))
        Text(
            if (permanentlyDenied) "Music access is turned off. Open Settings → Permissions → Music and audio, and choose Allow."
            else "To play the songs on your phone, Tarang needs to see your music and audio files. It doesn't look at photos or other files, and nothing leaves your phone.",
            style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(32.dp))
        if (permanentlyDenied) Button(onClick = onOpenSettings, Modifier.fillMaxWidth().height(56.dp).testTag("open_settings")) { Text("Open Settings") }
        else Button(onClick = onAllow, Modifier.fillMaxWidth().height(56.dp).testTag("allow_music")) { Text("Allow access to music") }
    }
}

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"; in 12..16 -> "Good afternoon"; in 17..21 -> "Good evening"; else -> "Good night"
}

// ------------------------------------------------------------------ Home
@Composable
fun HomeScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val scanning by vm.scanning.collectAsState()
    val ui by vm.player.ui.collectAsState()
    val song by vm.currentSong.collectAsState()
    val played by vm.hub.playedThisSession.collectAsState()
    val counts by vm.counts.collectAsState()
    val liked by vm.liked.collectAsState()
    val cs = MaterialTheme.colorScheme

    LazyColumn(Modifier.fillMaxSize().testTag("home"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
        item {
            PageHeader(greeting(), Modifier.statusBarsPadding()) {
                IconButton(onClick = { nav.go(R0.SEARCH) }, Modifier.testTag("open_search")) { Icon(Icons.Rounded.Search, "Search") }
                IconButton(onClick = { nav.go(R0.SETTINGS) }, Modifier.testTag("open_settings")) { Icon(Icons.Rounded.Settings, "Settings") }
            }
        }
        if (!lib.loaded || (scanning && lib.songs.isEmpty())) {
            item { Box(Modifier.fillMaxWidth().padding(64.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            item { Text("Finding your music…", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = cs.onSurfaceVariant) }
            return@LazyColumn
        }
        if (lib.songs.isEmpty()) {
            item {
                EmptyState(Icons.Rounded.MusicOff, "No songs found", "Copy some music to your phone, or check which folders Tarang looks in.", Modifier.testTag("home_empty")) {
                    Button(onClick = { vm.rescan() }) { Text("Look again") }
                    OutlinedButton(onClick = { nav.go(R0.SCAN_FOLDERS) }) { Text("Folders to scan") }
                }
            }
            return@LazyColumn
        }
        // fresh start: pick up where you left off (the mini player stays hidden until something plays)
        val s = song
        if (s != null && ui.hasSong && !played && !ui.isPlaying) item {
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clickable { vm.player.play() }.testTag("continue_card"),
                shape = RoundedCornerShape(28.dp), colors = CardDefaults.cardColors(cs.primaryContainer, cs.onPrimaryContainer),
            ) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Cover(s, 72.dp, RoundedCornerShape(18.dp))
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Continue listening", style = MaterialTheme.typography.labelLarge)
                        Text(s.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("${s.artist} · ${formatDuration(ui.positionMs)} of ${formatDuration(s.durationMs)}", style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    PlayPauseButton(ui.isPlaying, { vm.player.play() }, cs.primary, cs.onPrimary, 56.dp, squareRadius = 18.dp, iconSize = 30.dp, tag = "continue_play")
                }
            }
        }
        // mixes
        item { SectionLabel("Made for you") }
        item {
            val mixes = MixKind.entries.map { it to vm.repo.mix(it, lib) }.filter { it.second.isNotEmpty() }
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                item { MixCard("Liked songs", plural(liked.size, "song"), Icons.Rounded.Favorite, 0, null, "mix_liked") { nav.go(R0.LIKED) } }
                items(mixes) { (k, l) -> MixCard(k.title, k.subtitle, null, k.ordinal + 1, l.firstOrNull(), "mix_${k.name.lowercase()}") { nav.go(R0.mix(k.name)) } }
                item { MixCard("Shuffle all", plural(lib.songs.size, "song"), Icons.Rounded.Shuffle, 2, null, "mix_shuffle") { vm.player.playList(lib.songs, shuffle = true, kind = "Library", label = "All songs") } }
            }
        }
        // folders
        if (lib.folders.isNotEmpty()) {
            item {
                Row(Modifier.fillMaxWidth().padding(end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Your folders", Modifier.weight(1f))
                    TextButton(onClick = { nav.tab(R0.FOLDERS) }) { Text("See all") }
                }
            }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(lib.folders.sortedByDescending { it.songs.size }.take(10), key = { it.path }) { f ->
                        Column(Modifier.width(132.dp).clip(RoundedCornerShape(20.dp)).clickable { nav.go(R0.folder(f.path)) }.padding(4.dp)) {
                            CoverStack(f.songs, 124.dp)
                            Spacer(Modifier.height(8.dp))
                            Text(f.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(plural(f.songs.size, "song"), style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        // most played
        val top = counts.values.sortedByDescending { it.plays }.mapNotNull { lib.byId[it.songId] }.take(5)
        if (top.isNotEmpty()) {
            item { SectionLabel("Most played") }
            items(top, key = { "top${it.id}" }) { t ->
                SongRow(t, playing = t.id == ui.currentId, onClick = { vm.player.tapSong(t, top, "Mix", "Most played") }, onMore = { nav.songMenu(t) },
                    meta = "${t.artist} · ${plural(counts[t.id]?.plays ?: 0, "play")}")
            }
        }
    }
}

@Composable
private fun MixCard(title: String, sub: String, icon: ImageVector?, tone: Int, cover: Song?, tag: String, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer)[tone % 3]
    Column(Modifier.width(156.dp).clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick).padding(12.dp).testTag(tag)) {
        Box(Modifier.size(132.dp).clip(RoundedCornerShape(18.dp)).background(fg.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
            if (cover != null) Cover(cover, 132.dp, RoundedCornerShape(18.dp))
            else if (icon != null) Icon(icon, null, tint = fg, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(10.dp))
        Text(title, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(sub, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 2, minLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

/** Up to three covers fanned out (folder / playlist art). */
@Composable
fun CoverStack(songs: List<Song>, size: androidx.compose.ui.unit.Dp) {
    val picks = songs.distinctBy { it.albumId }.take(3)
    Box(Modifier.size(size)) {
        if (picks.size >= 3) {
            Box(Modifier.align(Alignment.TopEnd).size(size * 0.62f)) { Cover(picks[2], size * 0.62f, RoundedCornerShape(16.dp)) }
            Box(Modifier.align(Alignment.BottomStart).size(size * 0.62f)) { Cover(picks[1], size * 0.62f, RoundedCornerShape(16.dp)) }
            Box(Modifier.align(Alignment.Center).size(size * 0.64f).clip(RoundedCornerShape(18.dp)).background(MaterialTheme.colorScheme.background).padding(3.dp)) {
                Cover(picks[0], size * 0.64f - 6.dp, RoundedCornerShape(16.dp))
            }
        } else if (picks.isNotEmpty()) Cover(picks[0], size, RoundedCornerShape(20.dp))
        else Box(Modifier.fillMaxSize().clip(RoundedCornerShape(20.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}


/** The launcher icon drawn from its two vector layers. */
@Composable
fun AppIcon(size: androidx.compose.ui.unit.Dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3))) {
        Image(painterResource(R.drawable.ic_launcher_background), null, Modifier.fillMaxSize(), contentScale = androidx.compose.ui.layout.ContentScale.Crop)
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize().graphicsLayerScale(1.5f))
    }
}

private fun Modifier.graphicsLayerScale(s: Float) = this.then(Modifier.graphicsLayer { scaleX = s; scaleY = s })
