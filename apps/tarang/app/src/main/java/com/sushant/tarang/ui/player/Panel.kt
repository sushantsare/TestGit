@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.player

import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.tarang.data.*
import com.sushant.tarang.playback.PlayerUi
import com.sushant.tarang.ui.LocalPlayerActions
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.Cover
import com.sushant.tarang.ui.components.DancingBars
import com.sushant.tarang.ui.theme.CoverColors
import com.sushant.tarang.ui.theme.RoundTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
internal fun Panel(g: Any, c: CoverColors, ui: PlayerUi, song: Song, state: SheetState, alpha: Float, scope: kotlinx.coroutines.CoroutineScope,
                   collapse: PlayerDrag, lyrics: Lyrics?, dp: (Float) -> Float) {
    PanelImpl(g as PanelGeo, c, ui, song, state, alpha, scope, collapse, lyrics, dp)
}

/** The pieces of the player geometry the panel needs. */
internal interface PanelGeo { val fullH: Float; val fullW: Float; val panelTopY: Float; val panelPeek: Float; val panelTwoPane: Boolean; val panelPaneW: Float; val panelOpenTop: Float }

@Composable
private fun PanelImpl(g: PanelGeo, c: CoverColors, ui: PlayerUi, song: Song, state: SheetState, alpha: Float, scope: kotlinx.coroutines.CoroutineScope,
                      collapse: PlayerDrag, lyrics: Lyrics?, dp: (Float) -> Float) {
    val d = LocalDensity.current
    val vm = LocalVm.current
    if (g.panelTwoPane) {
        // tablet / landscape: Up next or Lyrics always shown beside the controls
        Column(
            Modifier.offset { IntOffset((g.panelPaneW + dp(16f)).toInt(), (g.panelTopY + dp(16f)).toInt()) }
                .size(with(d) { (g.fullW - g.panelPaneW - dp(32f)).toDp() }, with(d) { (g.fullH - g.panelTopY - dp(32f)).toDp() })
                .alpha(alpha).clip(RoundedCornerShape(32.dp)).background(c.panel)
        ) {
            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                listOf("Up next", "Lyrics").forEachIndexed { i, t ->
                    val on = state.tab == i
                    Box(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(if (i == 0) 22.dp else 8.dp, if (i == 0) 8.dp else 22.dp, if (i == 0) 8.dp else 22.dp, if (i == 0) 22.dp else 8.dp))
                        .background(if (on) c.accent else c.chip).clickable { state.tab = i }.testTag("tab_${t.replace(" ", "_").lowercase()}"), contentAlignment = Alignment.Center) {
                        Text(t, color = if (on) c.onAccent else c.onPanel, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
            if (state.tab == 0) UpNext(c, ui) else LyricsPage(c, ui, song, lyrics)
        }
        return
    }
    val q = state.panel.value
    val peekTop = g.fullH - g.panelPeek
    val openTop = g.panelOpenTop
    val top = peekTop + (openTop - peekTop) * q
    // one gesture = one job: pull up (or down while open) moves the page; pulling down on the closed strip closes the player
    var mode by remember { mutableIntStateOf(0) }   // 0 undecided, 1 page, 2 close player
    val drag = rememberDraggableState { delta ->
        if (mode == 0) {
            mode = if (state.panel.value <= 0.001f && delta > 0) 2 else 1
            if (mode == 2) collapse.onStart() else if (state.panel.value <= 0.001f) state.tab = 0
        }
        if (mode == 2) collapse.onDelta(delta)
        else scope.launch { state.panel.snapTo((state.panel.value - delta / (peekTop - openTop)).coerceIn(0f, 1f)) }
    }
    val openTab: (Int) -> Unit = { i -> state.tab = i; scope.launch { state.panel.animateTo(1f, snapSpec()) } }

    // what plays next, on the closed strip
    val lib by vm.library.collectAsState()
    val peekLine = when {
        ui.repeat == androidx.media3.common.Player.REPEAT_MODE_ONE -> "Repeating this song"
        ui.nextIndex in ui.queueIds.indices -> "Up next · " + (lib.byId[ui.queueIds[ui.nextIndex]]?.title ?: "")
        else -> "End of queue"
    }

    Column(
        Modifier.offset { IntOffset(0, top.roundToInt()) }.fillMaxWidth().height(with(d) { (g.fullH - top).toDp() })
            .alpha(alpha)
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .background(c.panel.copy(alpha = q))                    // no sheet at all while closed: it sits on the player's colour
            .draggable(drag, Orientation.Vertical, onDragStarted = { mode = 0 }, onDragStopped = { v ->
                if (mode == 2) collapse.onStop(v)
                else {
                    val open = if (kotlin.math.abs(v) > 600f) v < 0 else state.panel.value > 0.4f
                    state.panel.animateTo(if (open) 1f else 0f, snapSpec())
                }
                mode = 0
            })
            .testTag("panel")
    ) {
        Box(Modifier.fillMaxWidth().padding(top = 10.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(36.dp, 4.dp).clip(CircleShape).background(c.onFull.copy(alpha = 0.45f)))
        }
        Box(Modifier.fillMaxWidth().height(48.dp)) {
            // closed: "Up next · …"; tapping or pulling it up opens the queue
            val lineA = (1f - q * 3f).coerceIn(0f, 1f)
            if (lineA > 0f) Box(Modifier.fillMaxSize().alpha(lineA).clickable(onClickLabel = "Open up next") { openTab(0) }.testTag("peek_line"),
                contentAlignment = Alignment.Center) {
                Text(peekLine, color = c.onFull.copy(alpha = 0.9f), style = RoundTitle, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 32.dp))
            }
            if (q > 0.02f) Row(Modifier.fillMaxSize().alpha(((q - 0.1f) / 0.5f).coerceIn(0f, 1f)).padding(start = 24.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(if (state.tab == 0) "Up next" else "Lyrics", color = c.onPanel, style = RoundTitle, fontSize = 20.sp, modifier = Modifier.weight(1f).testTag("panel_title"))
            }
        }
        if (q > 0.02f) Box(Modifier.weight(1f).graphicsLayer {
            val k = ((q - 0.15f) / 0.85f).coerceIn(0f, 1f)
            this.alpha = k; translationY = (1f - k) * 48.dp.toPx()
        }) { if (state.tab == 0) UpNext(c, ui) else LyricsPage(c, ui, song, lyrics) }
    }
}

// ------------------------------------------------------------------ Up next (one queue)
@Composable
private fun UpNext(c: CoverColors, ui: PlayerUi) {
    val vm = LocalVm.current
    val act = LocalPlayerActions.current
    val lib by vm.library.collectAsState()
    val ctxLabel by vm.hub.contextLabel.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = ui.index.coerceAtLeast(0))
    val scope = rememberCoroutineScope()
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (ui.queueIds.isEmpty()) "The queue is empty" else "Playing from ${ctxLabel.ifEmpty { "your music" }} · ${ui.index + 1} of ${ui.queueIds.size}" +
                    if (ui.shuffle) " · shuffled" else "",
                color = c.onPanelVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { scope.launch { listState.animateScrollToItem((ui.index - 1).coerceAtLeast(0)) } }, modifier = Modifier.testTag("queue_locate")) {
                Icon(Icons.Rounded.MyLocation, "Jump to the song playing now", tint = c.onPanelVariant)
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("queue_menu")) { Icon(Icons.Rounded.MoreVert, "Queue options", tint = c.onPanelVariant) }
                DropdownMenu(menu, { menu = false }) {
                    DropdownMenuItem(text = { Text("Save as playlist") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, null) },
                        onClick = { menu = false; act.saveQueueAsPlaylist() }, modifier = Modifier.testTag("queue_save"))
                    DropdownMenuItem(text = { Text("Clear queue") }, leadingIcon = { Icon(Icons.Rounded.ClearAll, null) },
                        onClick = { menu = false; confirmClear = true }, modifier = Modifier.testTag("queue_clear"))
                }
            }
        }
        val rowH = with(LocalDensity.current) { 66.dp.toPx() }
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragY by remember { mutableFloatStateOf(0f) }
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp).testTag("queue_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(ui.queueIds, key = { i, id -> "$i-$id" }) { i, id ->
                val s = lib.byId[id] ?: return@itemsIndexed
                val now = i == ui.index
                val first = i == 0; val last = i == ui.queueIds.lastIndex
                val shape = RoundedCornerShape(if (first) 24.dp else 6.dp, if (first) 24.dp else 6.dp, if (last) 24.dp else 6.dp, if (last) 24.dp else 6.dp)
                val dismiss = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismiss.currentValue) { if (dismiss.currentValue != SwipeToDismissBoxValue.Settled) act.removeAt(i) }
                SwipeToDismissBox(dismiss, enableDismissFromStartToEnd = !now, enableDismissFromEndToStart = !now, modifier = Modifier.padding(bottom = 3.dp), backgroundContent = {
                    Box(Modifier.fillMaxSize().clip(shape).background(MaterialTheme.colorScheme.errorContainer).padding(horizontal = 24.dp), contentAlignment = Alignment.CenterEnd) {
                        Icon(Icons.Rounded.Delete, "Remove", tint = MaterialTheme.colorScheme.onErrorContainer)
                    }
                }) {
                    Row(
                        Modifier.fillMaxWidth().height(63.dp)
                            .graphicsLayer { if (dragFrom == i) { translationY = dragY; shadowElevation = 12f } }
                            .clip(shape)
                            .background(if (now) lerp(c.chip, c.accent, 0.16f) else c.chip)
                            .clickable { act.playAt(i) }.padding(start = 14.dp, end = 4.dp).testTag("queue_row_$i"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Cover(s, 46.dp, RoundedCornerShape(14.dp))
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = RoundTitle, fontSize = 15.sp, color = if (now) c.accent else c.onPanel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (now) "Now playing · " else "") + s.artist, color = c.onPanelVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (now) DancingBars(c.accent, ui.isPlaying, Modifier.padding(horizontal = 14.dp))
                        else Box(Modifier.size(48.dp).pointerInput(i) {
                            detectDragGestures(onDragStart = { dragFrom = i; dragY = 0f },
                                onDragEnd = { val to = (i + (dragY / rowH).roundToInt()).coerceIn(0, ui.queueIds.lastIndex); if (to != i) act.move(i, to); dragFrom = -1; dragY = 0f },
                                onDragCancel = { dragFrom = -1; dragY = 0f }) { ch, amount -> ch.consume(); dragY += amount.y }
                        }.testTag("queue_drag_$i"), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragHandle, "Drag to reorder", tint = c.onPanelVariant) }
                    }
                }
            }
        }
    }
    if (confirmClear) AlertDialog(onDismissRequest = { confirmClear = false }, title = { Text("Clear the queue?") },
        text = { Text("Everything except the song playing now is removed from the queue. Your songs stay on your phone.") },
        confirmButton = { TextButton(onClick = { act.clearQueue(); confirmClear = false }, modifier = Modifier.testTag("confirm")) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } })
}

// ------------------------------------------------------------------ Lyrics (opened with the Lyrics button)
@Composable
private fun LyricsPage(c: CoverColors, ui: PlayerUi, song: Song, lyrics: Lyrics?) {
    val vm = LocalVm.current
    val act = LocalPlayerActions.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) vm.importLrc(song.id, uri) }
    val l = lyrics
    if (l == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 32.dp).testTag("lyrics_empty"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(88.dp).clip(CircleShape).background(c.chip), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Lyrics, null, tint = c.accent, modifier = Modifier.size(44.dp))
            }
            Spacer(Modifier.height(18.dp))
            Text("No lyrics for this song", color = c.onPanel, style = RoundTitle, fontSize = 22.sp)
            Text("Add a synced .lrc file or type the words yourself. They can be saved into the song file.", color = c.onPanelVariant,
                style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
            Spacer(Modifier.height(24.dp))
            Button(onClick = { pick.launch(arrayOf("*/*")) }, colors = ButtonDefaults.buttonColors(c.accent, c.onAccent), modifier = Modifier.fillMaxWidth().height(56.dp).testTag("import_lrc")) {
                Icon(Icons.Rounded.UploadFile, null); Spacer(Modifier.width(8.dp)); Text("Add .lrc file")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { act.openLyricsEditor(song.id) }, modifier = Modifier.fillMaxWidth().height(56.dp).testTag("type_lyrics")) {
                Icon(Icons.Rounded.EditNote, null, tint = c.onPanel); Spacer(Modifier.width(8.dp)); Text("Type lyrics", color = c.onPanel)
            }
            Spacer(Modifier.height(48.dp))
        }
        return
    }
    val cur = l.indexAt(ui.positionMs)
    val ls = rememberLazyListState()
    LaunchedEffect(cur) { if (l.synced && cur >= 0) ls.animateScrollToItem((cur - 2).coerceAtLeast(0)) }
    val inFile = l.source.contains("inside the song")
    Column(Modifier.fillMaxSize()) {
        LazyColumn(state = ls, modifier = Modifier.weight(1f).testTag("lyrics"), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 16.dp)) {
            itemsIndexed(l.lines) { i, line ->
                val on = l.synced && i == cur
                val dist = if (l.synced && cur >= 0) kotlin.math.abs(i - cur) else 0
                Text(line.text.ifBlank { "♪" },
                    color = when { !l.synced -> c.onPanel; on -> c.onPanel; i < cur -> c.onPanelVariant.copy(alpha = 0.5f); else -> c.onPanelVariant },
                    style = RoundTitle, fontSize = if (on) 26.sp else if (l.synced) 21.sp else 18.sp, lineHeight = if (l.synced) 38.sp else 28.sp,
                    fontWeight = if (on) FontWeight.ExtraBold else FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                        // lyrics focus: lines around the current one are softly blurred (Android 12+)
                        .then(if (l.synced && !on && Build.VERSION.SDK_INT >= 31) Modifier.blur((minOf(dist, 3) * 0.6f).dp) else Modifier)
                        .clip(RoundedCornerShape(8.dp)).let { if (l.synced) it.clickable { act.seekTo(line.timeMs) } else it }.padding(vertical = 2.dp))
            }
        }
        Text((if (l.synced) "Synced · " else "") + l.source, color = c.onPanelVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 28.dp))
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!inFile) Button(onClick = { vm.requestSaveLyricsToFile(song, l.raw) }, colors = ButtonDefaults.buttonColors(c.accent, c.onAccent),
                shape = RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp), modifier = Modifier.weight(1f).height(56.dp).testTag("save_lyrics_file")) {
                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save into song file", maxLines = 1)
            } else Box(Modifier.weight(1f).height(56.dp).clip(RoundedCornerShape(28.dp, 10.dp, 10.dp, 28.dp)).background(c.chip), contentAlignment = Alignment.Center) {
                Text("Saved inside the song file", color = c.onPanel, style = MaterialTheme.typography.labelLarge)
            }
            Box(Modifier.size(64.dp, 56.dp).clip(RoundedCornerShape(10.dp, 28.dp, 28.dp, 10.dp)).background(c.chip).clickable { act.openLyricsEditor(song.id) }.testTag("edit_lyrics"),
                contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Edit, "Edit lyrics", tint = c.onPanel) }
        }
    }
}

// ------------------------------------------------------------------ Info (real file details, never made up)
data class FileInfo(val format: String, val bitrate: String, val sampleRate: String, val channels: String)

suspend fun readFileInfo(ctx: android.content.Context, song: Song): FileInfo = withContext(Dispatchers.IO) {
    var br = ""; var sr = ""; var chs = ""; val fmt = song.mime.substringAfter('/').uppercase().replace("MPEG", "MP3").replace("X-", "")
    runCatching {
        val r = MediaMetadataRetriever()
        try {
            r.setDataSource(ctx, song.uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)?.toLongOrNull()?.let { br = "${it / 1000} kbps" }
            if (Build.VERSION.SDK_INT >= 31) r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITS_PER_SAMPLE)?.toIntOrNull()?.let { sr = "$it-bit" }
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

