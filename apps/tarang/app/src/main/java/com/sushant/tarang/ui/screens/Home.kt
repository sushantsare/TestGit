@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.screens

import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.sushant.tarang.ui.theme.PageTitle
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

/** Hour used for the greeting (tests can pin it to check every time of day). */
internal object GreetingClock {
    var hourOverride by mutableStateOf<Int?>(null)
    fun hour(): Int = hourOverride ?: Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}

private fun greeting(): String = when (GreetingClock.hour()) {
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
    val counts by vm.counts.collectAsState()
    val liked by vm.liked.collectAsState()
    val cs = MaterialTheme.colorScheme

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val collapse = rememberCollapse(listState)
    ScrollTopOnReselect(R0.HOME) { listState.animateScrollToItem(0) }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize().testTag("home"), state = listState, contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
        item {
            HomeGreeting {
                TopActions(TopAction(Icons.Rounded.Search, "Search", "open_search") { nav.go(R0.SEARCH) },
                    TopAction(Icons.Rounded.Settings, "Settings", "open_settings") { nav.go(R0.SETTINGS) })
            }
        }
        if (!lib.loaded || (scanning && lib.songs.isEmpty())) {
            item { Box(Modifier.fillMaxWidth().padding(top = 32.dp, bottom = 12.dp), contentAlignment = Alignment.Center) { PixelLoader(72.dp) } }
            item { Text("Finding your music…", Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = cs.onSurfaceVariant) }
            items(6) { ShimmerRow(cs.surfaceContainerHigh) }
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
            itemsIndexed(top, key = { _, it -> "top${it.id}" }) { i, t ->
                SongRow(t, index = i, count = top.size, onClick = { vm.player.tapSong(t, top, "Mix", "Most played") }, onMore = { nav.songMenu(t) },
                    meta = "${t.artist} · ${plural(counts[t.id]?.plays ?: 0, "play")}")
            }
        }
    }
    CollapsedTitleBar(greeting(), collapse)
    }
}

/**
 * The greeting on two lines, always "Good" / "<time of day>": row 1 is "Good", the time-of-day icon right after it,
 * and the Search | Settings pill on the right; row 2 is the second word on its own. Both words shrink a little
 * only when a large system font would make them touch something.
 */
@Composable
private fun HomeGreeting(actions: @Composable () -> Unit) {
    val words = greeting().split(" ", limit = 2)
    val style = PageTitle.copy(color = MaterialTheme.colorScheme.onSurface)
    val fit = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 20.sp, maxFontSize = PageTitle.fontSize)
    Column(Modifier.fillMaxWidth().statusBarsPadding().padding(top = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(start = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                androidx.compose.foundation.text.BasicText(words[0], Modifier.weight(1f, fill = false).semantics { heading(); contentDescription = greeting() }.testTag("greet_word1"),
                    style = style, maxLines = 1, autoSize = fit)
                Spacer(Modifier.width(12.dp))
                GreetingIcon()
            }
            Spacer(Modifier.width(12.dp))
            actions()
        }
        androidx.compose.foundation.text.BasicText(words.getOrElse(1) { "" }, Modifier.testTag("greet_word2").padding(start = 20.dp, end = 20.dp).clearAndSetSemantics { },
            style = style, maxLines = 1, autoSize = fit)
    }
}

/** A small playful icon for the time of day, in a Pixel "cookie" shape, next to the greeting. */
@Composable
private fun GreetingIcon() {
    val h = GreetingClock.hour()
    val (icon, bg, fg) = when (h) {
        in 5..11 -> Triple(Icons.Rounded.WbSunny, androidx.compose.ui.graphics.Color(0xFFFFE08A), androidx.compose.ui.graphics.Color(0xFF7A4B00))
        in 12..16 -> Triple(Icons.Rounded.LightMode, androidx.compose.ui.graphics.Color(0xFFFFD49A), androidx.compose.ui.graphics.Color(0xFF7A3E00))
        in 17..21 -> Triple(Icons.Rounded.WbTwilight, androidx.compose.ui.graphics.Color(0xFFFFCFB0), androidx.compose.ui.graphics.Color(0xFF8A3A12))
        else -> Triple(Icons.Rounded.Bedtime, androidx.compose.ui.graphics.Color(0xFFCFD6FF), androidx.compose.ui.graphics.Color(0xFF26306B))
    }
    val spin by androidx.compose.animation.core.rememberInfiniteTransition(label = "gi").animateFloat(0f, 360f,
        androidx.compose.animation.core.infiniteRepeatable(androidx.compose.animation.core.tween(24000, easing = androidx.compose.animation.core.LinearEasing)), label = "r")
    Box(Modifier.size(46.dp).testTag("greeting_icon"), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().graphicsLayer { rotationZ = spin }.clip(CookieShape(9, 0.08f)).background(bg))
        Icon(icon, null, tint = fg, modifier = Modifier.size(26.dp))
    }
}

/** Pixel's own loader: a shape that keeps changing form while it spins (Material 3 Expressive). */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PixelLoader(size: androidx.compose.ui.unit.Dp) {
    ContainedLoadingIndicator(Modifier.size(size).testTag("pixel_loader"))
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
