@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.player

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.*
import com.sushant.tarang.playback.PlayerUi
import com.sushant.tarang.ui.LocalPlayerActions
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.Cover
import com.sushant.tarang.ui.components.PlayPauseButton
import com.sushant.tarang.ui.theme.CoverColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val TABS = listOf("Up next", "Lyrics", "Info")

@Composable
internal fun Panel(g: Any, c: CoverColors, ui: PlayerUi, song: Song, state: SheetState, alpha: Float, scope: kotlinx.coroutines.CoroutineScope,
                   collapse: PlayerDrag, dp: (Float) -> Float) {
    val geo = g as PanelGeo
    PanelImpl(geo, c, ui, song, state, alpha, scope, collapse, dp)
}

/** The pieces of [Geometry] the panel needs. */
internal interface PanelGeo { val fullH: Float; val fullW: Float; val panelTopY: Float; val panelPeek: Float; val panelTwoPane: Boolean; val panelPaneW: Float }

@Composable
private fun PanelImpl(g: PanelGeo, c: CoverColors, ui: PlayerUi, song: Song, state: SheetState, alpha: Float, scope: kotlinx.coroutines.CoroutineScope,
                      collapse: PlayerDrag, dp: (Float) -> Float) {
    val d = LocalDensity.current
    val act = LocalPlayerActions.current
    val vm = LocalVm.current
    if (g.panelTwoPane) {
        // tablet / landscape: panel always open beside the controls
        Column(
            Modifier.offset { IntOffset((g.panelPaneW + dp(16f)).toInt(), (g.panelTopY + dp(16f)).toInt()) }
                .size(with(d) { (g.fullW - g.panelPaneW - dp(32f)).toDp() }, with(d) { (g.fullH - g.panelTopY - dp(32f)).toDp() })
                .alpha(alpha).clip(RoundedCornerShape(32.dp)).background(c.panel)
        ) {
            Tabs(c, state.tab, 1f) { state.tab = it }
            PanelContent(state.tab, c, ui, song)
        }
        return
    }
    val q = state.panel.value
    val peekTop = g.fullH - g.panelPeek
    val openTop = g.panelTopY + dp(80f)
    val top = peekTop + (openTop - peekTop) * q
    // one gesture = one job: pull up (or down while open) moves the panel; pulling down on the closed strip closes the player
    var mode by remember { mutableIntStateOf(0) }   // 0 undecided, 1 panel, 2 close player
    val drag = rememberDraggableState { delta ->
        if (mode == 0) mode = if (state.panel.value <= 0.001f && delta > 0) 2 else 1
        if (mode == 2) collapse.onDelta(delta)
        else scope.launch { state.panel.snapTo((state.panel.value - delta / (peekTop - openTop)).coerceIn(0f, 1f)) }
    }
    val openPanel: (Int) -> Unit = { i -> state.tab = i; scope.launch { state.panel.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 420f)) } }

    // small "now playing" bar — only while the panel is really open (never left behind invisible)
    val headerA = ((q - 0.15f) / 0.85f).coerceIn(0f, 1f)
    if (headerA > 0f) Row(
        Modifier.offset { IntOffset(0, (g.panelTopY + dp(8f)).toInt()) }.fillMaxWidth().height(64.dp).alpha(alpha * headerA).padding(horizontal = 16.dp)
            .testTag("panel_header"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(song, 56.dp, RoundedCornerShape(14.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f).clickable { scope.launch { state.panel.animateTo(0f, spring()) } }) {
            Text(song.title, color = c.onFull, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(artistLine(song, c.onFull.copy(alpha = 0.8f), c.onFull.copy(alpha = 0.8f), showAlbum = false), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (q > 0.5f) PlayPauseButton(ui.isPlaying, { act.togglePlay() }, c.accent, c.onAccent, 48.dp, squareRadius = 16.dp, iconSize = 26.dp, tag = "panel_play")
    }

    // what's next, shown on the closed strip (YouTube-Music style: just a handle and one line)
    val lib by vm.library.collectAsState()
    val peekLine = when {
        ui.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeating this song"
        ui.nextIndex in ui.queueIds.indices -> "Up next · " + (lib.byId[ui.queueIds[ui.nextIndex]]?.title ?: "")
        else -> "End of queue · Pull up for lyrics & info"
    }

    Column(
        Modifier.offset { IntOffset(0, top.toInt()) }.fillMaxWidth().height(with(d) { (g.fullH - top).toDp() })
            .alpha(alpha)
            .clip(RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp))
            .background(c.panel.copy(alpha = q))                    // no sheet at all while closed: it sits on the player's colour
            .draggable(drag, Orientation.Vertical, onDragStarted = { mode = 0 }, onDragStopped = { v ->
                if (mode == 2) collapse.onStop(v)
                else {
                    val open = if (kotlin.math.abs(v) > 600f) v < 0 else state.panel.value > 0.4f
                    state.panel.animateTo(if (open) 1f else 0f, spring(dampingRatio = 0.9f, stiffness = 420f))
                }
                mode = 0
            })
            .testTag("panel")
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(32.dp, 4.dp).clip(CircleShape).background(c.onFull.copy(alpha = 0.45f)))
        }
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            // closed: one line; it fades out as the tabs fade in
            val lineA = (1f - q * 3f).coerceIn(0f, 1f)
            if (lineA > 0f) Box(Modifier.fillMaxSize().alpha(lineA).clickable(onClickLabel = "Open up next") { openPanel(0) }.testTag("peek_line"),
                contentAlignment = Alignment.Center) {
                Text(peekLine, color = c.onFull.copy(alpha = 0.9f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
            if (q > 0.02f) Box(Modifier.fillMaxSize().alpha(((q - 0.1f) / 0.5f).coerceIn(0f, 1f))) { Column { Tabs(c, state.tab, q) { i -> openPanel(i) } } }
        }
        if (q > 0.02f) Box(Modifier.weight(1f).graphicsLayer {
            val k = ((q - 0.15f) / 0.85f).coerceIn(0f, 1f)
            this.alpha = k; translationY = (1f - k) * 48.dp.toPx()
        }) { PanelContent(state.tab, c, ui, song) }
    }
}

@Composable
private fun Tabs(c: CoverColors, selected: Int, q: Float, onTab: (Int) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth().height(48.dp)) {
        val w = maxWidth / TABS.size
        val x by animateDpAsState(w * selected.coerceAtLeast(0), spring(dampingRatio = 0.8f, stiffness = 500f), label = "tabLine")
        val lineAlpha by animateFloatAsState(if (selected >= 0) 1f else 0f, tween(200), label = "tabLineA")
        Row(Modifier.fillMaxSize()) {
            TABS.forEachIndexed { i, t ->
                val on = i == selected
                val col by animateColorAsState(if (on) c.accent else lerp(c.onFull, c.onPanelVariant, q), tween(200), label = "tabC")
                Box(Modifier.weight(1f).fillMaxHeight().clickable { onTab(i) }.testTag("tab_${t.replace(" ", "_").lowercase()}"), contentAlignment = Alignment.Center) {
                    Text(t, color = col, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Box(Modifier.align(Alignment.BottomStart).offset(x = x + w * 0.2f).width(w * 0.6f).height(3.dp).alpha(lineAlpha)
            .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp)).background(c.accent))
    }
    HorizontalDivider(color = c.outline.copy(alpha = q))
}

@Composable
private fun PanelContent(tab: Int, c: CoverColors, ui: PlayerUi, song: Song) {
    AnimatedContent(
        targetState = tab,
        transitionSpec = {
            val dir = if (targetState > initialState) 1 else -1
            (slideInHorizontally(tween(300, easing = FastOutSlowInEasing)) { it / 3 * dir } + fadeIn(tween(220, delayMillis = 60))) togetherWith
                (slideOutHorizontally(tween(300, easing = FastOutSlowInEasing)) { -it / 3 * dir } + fadeOut(tween(150)))
        },
        label = "panelTab",
    ) { t ->
        when (t) {
            0 -> UpNext(c, ui)
            1 -> LyricsTab(c, ui, song)
            else -> InfoTab(c, song)
        }
    }
}

// ------------------------------------------------------------------ Up next (3 queues)
@Composable
private fun UpNext(c: CoverColors, ui: PlayerUi) {
    val vm = LocalVm.current
    val act = LocalPlayerActions.current
    val lib by vm.library.collectAsState()
    val active by vm.hub.activeQueue.collectAsState()
    val sizes by vm.hub.queueSizes.collectAsState()
    val ctxLabel by vm.hub.contextLabel.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.index.coerceAtLeast(0))
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            (1..3).forEach { s ->
                FilterChip(selected = s == active, onClick = { act.switchQueue(s) },
                    label = { Text("Queue $s" + (sizes.getOrNull(s - 1)?.takeIf { it > 0 && s != active }?.let { " · $it" } ?: "")) },
                    leadingIcon = if (s == active) { { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) } } else null,
                    modifier = Modifier.padding(end = 8.dp).testTag("queue_$s"))
            }
            Spacer(Modifier.weight(1f))
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Queue options", tint = c.onPanelVariant) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Clear Queue $active") }, leadingIcon = { Icon(Icons.Rounded.ClearAll, null) }, onClick = { menu = false; confirmClear = true })
                }
            }
        }
        Text(
            if (ui.queueIds.isEmpty()) "This queue is empty" else "Playing from ${ctxLabel.ifEmpty { "your music" }} · ${ui.index + 1} of ${ui.queueIds.size}",
            color = c.onPanelVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        if (ui.queueIds.isEmpty()) {
            Text("Switch to another queue, or play a song to start this one. Each queue remembers its own song and place.",
                color = c.onPanelVariant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(20.dp))
        }
        val rowH = with(LocalDensity.current) { 64.dp.toPx() }
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragY by remember { mutableFloatStateOf(0f) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().testTag("queue_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(ui.queueIds, key = { i, id -> "$i-$id" }) { i, id ->
                val s = lib.byId[id] ?: return@itemsIndexed
                val now = i == ui.index
                val dismiss = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismiss.currentValue) { if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) act.removeAt(i) }
                SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = !now, enableDismissFromEndToStart = !now, backgroundContent = {
                    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Rounded.Delete, "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }) {
                    Row(
                        Modifier.fillMaxWidth().height(64.dp)
                            .graphicsLayer { if (dragFrom == i) { translationY = dragY; shadowElevation = 12f } }
                            .background(if (now) lerp(c.panel, c.accent, 0.12f) else c.panel)
                            .clickable { act.playAt(i) }.padding(start = 20.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(s, 48.dp, RoundedCornerShape(12.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, color = if (now) c.accent else c.onPanel, fontWeight = if (now) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (now) "Now playing · " else "") + s.artist, color = c.onPanelVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (now) Icon(Icons.Rounded.GraphicEq, "Playing now", tint = c.accent, modifier = Modifier.padding(12.dp))
                        else Box(Modifier.size(48.dp).pointerInput(i) {
                            detectDragGestures(onDragStart = { dragFrom = i; dragY = 0f },
                                onDragEnd = { val to = (i + (dragY / rowH).roundToInt()).coerceIn(0, ui.queueIds.lastIndex); if (to != i) act.move(i, to); dragFrom = -1; dragY = 0f },
                                onDragCancel = { dragFrom = -1; dragY = 0f }) { ch, amount -> ch.consume(); dragY += amount.y }
                        }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = c.onPanelVariant) }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear Queue $active?") },
        text = { Text("The songs are only removed from this queue, not from your phone.") },
        confirmButton = { TextButton(onClick = { act.clearQueue(active); confirmClear = false }) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

// ------------------------------------------------------------------ Lyrics
@Composable
private fun LyricsTab(c: CoverColors, ui: PlayerUi, song: Song) {
    val vm = LocalVm.current
    val act = LocalPlayerActions.current
    val stored by remember(song.id) { vm.repo.dao.lyricsFlow(song.id) }.collectAsState(null)
    var loading by remember(song.id) { mutableStateOf(true) }
    val lyrics by produceState<Lyrics?>(null, song.id, stored) { value = withContext(Dispatchers.IO) { vm.repo.lyrics.find(song) }; loading = false }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importLrc(song.id, uri) }
    val l = lyrics
    if (l == null) {
        Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(24.dp))
            Icon(Icons.Rounded.Lyrics, null, tint = c.onPanelVariant, modifier = Modifier.size(56.dp))
            Spacer(Modifier.height(12.dp))
            Text(if (loading) "Looking for lyrics…" else "No lyrics for this song", color = c.onPanel, style = MaterialTheme.typography.titleMedium)
            Text("Add a .lrc file or type them in. Tarang never makes lyrics up.", color = c.onPanelVariant, style = MaterialTheme.typography.bodyMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 6.dp))
            Spacer(Modifier.height(20.dp))
            Button(onClick = { pick.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(c.accent, c.onAccent), modifier = Modifier.fillMaxWidth().testTag("import_lrc")) {
                Icon(Icons.Rounded.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Import .lrc file")
            }
            Spacer(Modifier.height(10.dp))
            FilledTonalButton(onClick = { act.openLyricsEditor(song.id) }, modifier = Modifier.fillMaxWidth().testTag("type_lyrics")) {
                Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Type or paste lyrics")
            }
        }
        return
    }
    val cur = l.indexAt(ui.positionMs)
    val ls = rememberLazyListState()
    LaunchedEffect(cur) { if (l.synced && cur >= 0) ls.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(state = ls, modifier = Modifier.weight(1f).testTag("lyrics"), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 20.dp)) {
            itemsIndexed(l.lines) { i, line ->
                val on = l.synced && i == cur
                Text(line.text.ifBlank { " " },
                    color = when { !l.synced -> c.onPanel; on -> c.onPanel; i < cur -> c.onPanelVariant.copy(alpha = 0.55f); else -> c.onPanelVariant },
                    fontSize = if (on) 24.sp else if (l.synced) 20.sp else 17.sp, lineHeight = if (l.synced) 38.sp else 26.sp,
                    fontWeight = if (on) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).let { if (l.synced) it.clickable { act.seekTo(line.timeMs) } else it }.padding(vertical = 2.dp))
            }
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AssistChip(onClick = {}, label = { Text(if (l.synced) "Synced" else "Plain") }, leadingIcon = { Icon(if (l.synced) Icons.Rounded.Check else Icons.Rounded.Notes, null, Modifier.size(18.dp)) })
            Text(l.source, color = c.onPanelVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(horizontal = 10.dp))
            IconButton(onClick = { act.openLyricsEditor(song.id) }) { Icon(Icons.Rounded.Edit, "Edit lyrics", tint = c.onPanelVariant) }
            IconButton(onClick = { pick.launch(arrayOf("*/*")) }) { Icon(Icons.Rounded.UploadFile, "Import .lrc file", tint = c.onPanelVariant) }
        }
    }
}

// ------------------------------------------------------------------ Info (real file details, never made up)
data class FileInfo(val format: String, val bitrate: String, val sampleRate: String, val channels: String)

suspend fun readFileInfo(ctx: android.content.Context, song: Song): FileInfo = withContext(Dispatchers.IO) {
    var br = ""; var sr = ""; var chs = ""; var fmt = song.mime.substringAfter('/').uppercase().replace("MPEG", "MP3").replace("X-", "")
    runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, song.uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { br = "${it / 1000} kbps" }
            if (android.os.Build.VERSION.SDK_INT >= 31) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let { sr = "$it-bit" }
        } finally { r.release() }
    }
    runCatching {
        val ex = MediaExtractor(); ex.setDataSource(ctx, song.uri, null)
        for (i in 0 until ex.trackCount) {
            val f = ex.getTrackFormat(i)
            if (f.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
                val rate = f.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                sr = "%.1f kHz".format(rate / 1000.0).replace(".0 ", " ") + if (sr.isNotEmpty()) " · $sr" else ""
                chs = when (val n = f.getInteger(MediaFormat.KEY_CHANNEL_COUNT)) { 1 -> "1 channel (mono)"; 2 -> "2 channels (stereo)"; else -> "$n channels" }
                break
            }
        }
        ex.release()
    }
    FileInfo(fmt, br.ifEmpty { "—" }, sr.ifEmpty { "—" }, chs.ifEmpty { "—" })
}

@Composable
fun InfoRows(song: Song, color: androidx.compose.ui.graphics.Color, variant: androidx.compose.ui.graphics.Color, divider: androidx.compose.ui.graphics.Color) {
    val vm = LocalVm.current
    val ctx = LocalContext.current
    val info by produceState<FileInfo?>(null, song.id) { value = readFileInfo(ctx, song) }
    val counts by vm.counts.collectAsState()
    val pc = counts[song.id]
    val fmt = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.ENGLISH)
    val rows = listOf(
        "Format" to (info?.let { "${it.format} · ${it.channels}" } ?: "…"),
        "Bitrate" to (info?.bitrate ?: "…"),
        "Sample rate" to (info?.sampleRate ?: "…"),
        "Size" to formatSize(song.sizeBytes),
        "Length" to formatDuration(song.durationMs),
        "Album" to song.album + if (song.year > 0) " · ${song.year}" else "",
        "Plays" to (pc?.let { "${it.plays} · last ${fmt.format(Date(it.lastPlayed))}" } ?: "Not played yet"),
        "Added" to fmt.format(Date(song.dateAdded * 1000)),
        "Location" to (song.path.ifEmpty { song.folderPath + "/" + song.fileName }),
    )
    rows.forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            Text(k, color = variant, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
            Text(v, color = color, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        }
        HorizontalDivider(color = divider)
    }
}

@Composable
private fun InfoTab(c: CoverColors, song: Song) {
    val act = LocalPlayerActions.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).testTag("info")) {
        InfoRows(song, c.onPanel, c.onPanelVariant, c.outline)
        FilledTonalButton(onClick = { act.openEdit(song.id) }, modifier = Modifier.padding(24.dp).testTag("edit_details")) {
            Icon(Icons.Rounded.Edit, null); Spacer(Modifier.width(8.dp)); Text("Edit details")
        }
    }
}
