package com.sushant.grabbit.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sushant.grabbit.data.Folders
import com.sushant.grabbit.data.PlaylistPick
import com.sushant.grabbit.data.PlaylistQueue
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.engine.Media
import com.sushant.grabbit.engine.MediaInfo
import com.sushant.grabbit.engine.PlEntry
import com.sushant.grabbit.ui.components.AppCard
import com.sushant.grabbit.ui.components.ConnectedGroup
import com.sushant.grabbit.ui.components.SectionLabel
import com.sushant.grabbit.ui.components.SegOption
import com.sushant.grabbit.ui.components.SplitButton
import com.sushant.grabbit.ui.components.groupShape
import com.sushant.grabbit.ui.theme.LocalSpacing
import kotlinx.coroutines.launch

private val VIDEO_Q = listOf("best" to "Best", "h1080" to "1080p", "h720" to "720p", "h480" to "480p", "h360" to "360p")
private val AUDIO_Q = listOf("audio" to "Best", "a160" to "160k", "a128" to "128k", "a64" to "64k")
private val ORDERS = listOf(PlaylistPick.IN_ORDER to "In order", PlaylistPick.REVERSED to "Reversed",
    PlaylistPick.NEWEST to "Newest first", PlaylistPick.RANDOM to "Random")

/** Playlist: pick videos (ticks, range, only new, first N, order), one setting for all, folder per playlist. */
@Composable
fun PlaylistCard(info: MediaInfo, framed: Boolean, onDownload: (MediaInfo, String, DlOpts) -> Unit) {
    val sp = LocalSpacing.current
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val focus = LocalFocusManager.current
    val st = remember(info) { OptsState(info) }
    val entries = remember(info) { mutableStateListOf<PlEntry>().apply { addAll(info.entries) } }
    var hasMore by remember(info) { mutableStateOf(info.hasMore) }
    var loading by remember { mutableStateOf(false) }
    fun downloaded(e: PlEntry) = PlaylistQueue.Archive.has(info.playlistId, e.id)
    fun pickable(e: PlEntry) = e.available && !downloaded(e)
    val picked = remember(info) { mutableStateListOf<Int>().apply { addAll(info.entries.filter(::pickable).map { it.index }) } }
    var audio by remember { mutableStateOf(false) }
    var vq by remember { mutableStateOf("h720") }
    var aq by remember { mutableStateOf("audio") }
    var order by remember { mutableIntStateOf(PlaylistPick.IN_ORDER) }
    val seed = remember { System.nanoTime() }
    var firstN by remember { mutableIntStateOf(0) }
    var askN by remember { mutableStateOf(false) }
    var range by remember { mutableStateOf("") }
    var rangeBad by remember { mutableStateOf(false) }
    var ownFolder by remember { mutableStateOf(true) }
    val total = maxOf(info.count, entries.size)
    val ordered = PlaylistPick.order(entries.toList(), order, seed, { it.index }, { it.timestamp })
    val known = entries.count { downloaded(it) }
    val newOnes = entries.count { pickable(it) }

    fun applyRange() {
        val set = PlaylistPick.range(range, total)
        rangeBad = set == null
        if (set != null) { picked.clear(); picked.addAll(entries.filter { it.available && it.index in set }.map { it.index }) }
        focus.clearFocus()
    }

    val body: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = sp.xs, vertical = sp.xs)) {
            // Header: stacked cover, name (pen renames the playlist folder), count
            EditableName(st.name, { st.name = it }) { m ->
                Row(m.padding(top = sp.xs), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(96.dp, 60.dp)) {
                        Box(Modifier.offset(8.dp, (-6).dp).size(80.dp, 54.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.outlineVariant))
                        Box(Modifier.offset(4.dp, (-3).dp).size(88.dp, 57.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.outline))
                        AsyncImage(info.thumbnail.ifBlank { null }, null, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(96.dp, 60.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
                        Text("≡ $total", color = Color.White, style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.align(Alignment.BottomEnd).padding(4.dp).clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 1.dp))
                    }
                    Spacer(Modifier.width(sp.m))
                    Column(Modifier.weight(1f)) {
                        Text(st.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(listOf(info.site, info.uploader, "$total videos${if (hasMore && info.count <= entries.size) "+" else ""}")
                            .filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (known > 0) Text("$known downloaded before", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(sp.m))
            ConnectedGroup(
                listOf(SegOption("video", "Video", Icons.Rounded.Movie, tag = "pl_video"), SegOption("audio", "Audio", Icons.Rounded.MusicNote, tag = "pl_audio")),
                if (audio) "audio" else "video", { audio = it == "audio" },
            )
            Spacer(Modifier.height(sp.l))
            SectionLabel(if (audio) "Audio quality · for all videos" else "Quality · for all videos")
            Spacer(Modifier.height(sp.xs))
            val qs = if (audio) AUDIO_Q else VIDEO_Q
            ConnectedGroup(qs.map { (k, l) -> SegOption(k, l, tag = "plq_$k") }, if (audio) aq else vq,
                { if (audio) aq = it else vq = it }, height = 52.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh, onColor = MaterialTheme.colorScheme.onSurface)
            if (!audio && vq != "best") Text("If a video doesn't have ${vq.removePrefix("h")}p, it gets the nearest lower one.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = sp.xs, start = sp.xs))

            Spacer(Modifier.height(sp.l))
            SectionLabel("Order")
            Spacer(Modifier.height(sp.xs))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(sp.s)) {
                ORDERS.forEach { (k, l) ->
                    FilterChip(selected = order == k, onClick = { order = k }, label = { Text(l) },
                        shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("order_$k"))
                }
                FilterChip(selected = firstN > 0, onClick = { askN = true },
                    label = { Text(if (firstN > 0) "First $firstN" else "First N…") },
                    shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("firstN"))
            }

            Spacer(Modifier.height(sp.l))
            SectionLabel("Videos")
            Spacer(Modifier.height(sp.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = range, onValueChange = { range = it; rangeBad = false },
                    modifier = Modifier.weight(1f).testTag("rangeField"),
                    placeholder = { Text("Range, e.g. 1-10, 15") },
                    singleLine = true, isError = rangeBad,
                    shape = RoundedCornerShape(24.dp),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { applyRange() }),
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                )
                Spacer(Modifier.width(sp.s))
                if (range.isNotBlank()) FilledTonalButton(onClick = { applyRange() }, modifier = Modifier.heightIn(min = 48.dp).testTag("rangeApply")) { Text("Apply") }
                else FilledTonalButton(onClick = {
                    picked.clear(); picked.addAll(entries.filter(::pickable).map { it.index }); firstN = 0
                }, modifier = Modifier.heightIn(min = 48.dp).testTag("onlyNew")) { Text("Only new ($newOnes)") }
            }
            if (rangeBad) Text("Use numbers like 1-10, 15, -3 (-3 = third from the end)", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = sp.s, top = sp.xs))
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = sp.s)) {
                Text("${picked.size} of $total selected", Modifier.weight(1f).padding(start = sp.xs).testTag("plCount"),
                    style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val all = entries.filter { it.available }.all { it.index in picked }
                TextButton(onClick = {
                    picked.clear(); firstN = 0
                    if (!all) picked.addAll(entries.filter { it.available }.map { it.index })
                }, modifier = Modifier.testTag("plSelectAll")) { Text(if (all) "Clear" else "Select all") }
            }
            val n = ordered.size + if (hasMore) 1 else 0
            ordered.forEachIndexed { i, e ->
                PlRow(e, e.index in picked, downloaded(e), total, groupShape(i, n, 24.dp)) {
                    if (e.index in picked) picked.remove(e.index) else picked.add(e.index)
                }
                Spacer(Modifier.height(3.dp))
            }
            if (hasMore) Surface(
                onClick = {
                    if (loading) return@Surface
                    loading = true
                    scope.launch {
                        val more = runCatching { Media.playlistPage(ctx, info.url, (entries.maxOfOrNull { it.index } ?: 0) + 1, info.site) }.getOrNull()
                        if (more != null) {
                            val fresh = more.filter { m -> entries.none { it.index == m.index } }
                            entries.addAll(fresh)
                            // New videos are ticked like the rest (or by the range, if one is set)
                            val set = PlaylistPick.range(range, total)
                            picked.addAll(fresh.filter { pickable(it) && (set == null || it.index in set) }.map { it.index })
                            hasMore = fresh.size >= com.sushant.grabbit.engine.PlaylistParser.PAGE && (info.count <= 0 || entries.size < info.count)
                        } else UiMessages.say("Couldn't load more videos. Check your connection and try again.")
                        loading = false
                    }
                },
                shape = groupShape(n - 1, n, 24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("loadMore"),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(if (loading) "Loading…" else "Load ${if (info.count > 0) minOf(100, info.count - entries.size) else 100} more",
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
            }

            Spacer(Modifier.height(sp.l))
            SaveToSection(st.folder, { st.folder = it }, audio)
            Spacer(Modifier.height(sp.s))
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth()) {
                Row(Modifier.padding(horizontal = sp.m, vertical = sp.s), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Own folder for this playlist", style = MaterialTheme.typography.titleSmall)
                        val base = Folders.label(ctx, st.folder, audio)
                        val first = ordered.firstOrNull { it.index in picked }
                        val ext = if (audio) (if (st.audioFormat == "best") "m4a" else st.audioFormat) else "mp4"
                        val file = first?.let { PlaylistPick.fileName(it.index, total, it.title) + ".$ext" } ?: ""
                        Text(listOf(base, if (ownFolder) st.name else "", file).filter { it.isNotBlank() }.joinToString(" › "),
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                            overflow = TextOverflow.Ellipsis)
                    }
                    Switch(ownFolder, { ownFolder = it }, Modifier.testTag("ownFolder"))
                }
            }
            Spacer(Modifier.height(sp.l))
            // In the chosen order; "First N" keeps only the first N picked
            var chosen = ordered.filter { it.index in picked }
            if (firstN > 0) chosen = chosen.take(firstN)
            val qLabel = if (audio) (AUDIO_Q.first { it.first == aq }.second + " " + AUDIO_FORMAT_LABELS.firstOrNull { it.first == st.audioFormat }?.second.orEmpty()).trim()
                else VIDEO_Q.first { it.first == vq }.second
            SplitButton(
                label = {
                    Icon(Icons.Rounded.Download, null)
                    Spacer(Modifier.width(sp.s))
                    Text(if (chosen.isEmpty()) "Pick at least one video" else "Download ${chosen.size} video${if (chosen.size > 1) "s" else ""} · $qLabel",
                        style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                enabled = chosen.isNotEmpty(),
                onClick = {
                    val base = st.toOpts(audio)
                    val opts = base.copy(
                        gt = st.name, sub = if (ownFolder) com.sushant.grabbit.download.FileNames.clean(st.name) else "",
                        // Subtitles follow the Settings default for every video (skipped where a video has none)
                        subLang = if (audio) "" else Prefs.subLang, subAuto = false,
                    )
                    onDownload(info.copy(entries = chosen, title = st.name), if (audio) aq else vq, opts)
                },
                menuDescription = if (audio) "Audio format" else "Video format",
            ) { close -> FormatMenu(audio, st, close) }
            Text("${PlaylistQueue.PARALLEL} download at a time · the rest wait their turn", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(top = sp.s),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
    }
    if (framed) AppCard(padding = sp.m) { body() } else Column { body() }

    if (askN) {
        var text by remember { mutableStateOf(if (firstN > 0) "$firstN" else "10") }
        AlertDialog(
            onDismissRequest = { askN = false },
            title = { Text("Only the first…") },
            text = {
                OutlinedTextField(text, { t -> text = t.filter { it.isDigit() }.take(4) }, label = { Text("Number of videos") },
                    singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.testTag("firstNField"))
            },
            confirmButton = { TextButton(onClick = { firstN = text.toIntOrNull() ?: 0; askN = false }, modifier = Modifier.testTag("firstNOk")) { Text("Apply") } },
            dismissButton = { TextButton(onClick = { firstN = 0; askN = false }) { Text(if (firstN > 0) "Turn off" else "Cancel") } },
        )
    }
}

@Composable
private fun PlRow(e: PlEntry, on: Boolean, done: Boolean, total: Int, shape: androidx.compose.ui.graphics.Shape, onToggle: () -> Unit) {
    val sp = LocalSpacing.current
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceContainerLowest, modifier = Modifier.fillMaxWidth().testTag("plRow_${e.index}")) {
        Row(
            Modifier.clickable(enabled = e.available, onClick = onToggle).alpha(if (e.available) 1f else 0.45f)
                .padding(start = sp.s, end = sp.xs, top = sp.s, bottom = sp.s),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(e.index.toString().padStart(if (total >= 100) 3 else 2, '0'), Modifier.width(32.dp),
                style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            AsyncImage(e.thumb.ifBlank { null }, null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(76.dp, 44.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest))
            Spacer(Modifier.width(sp.m))
            Column(Modifier.weight(1f)) {
                Text(e.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Row {
                    if (done) Text("Downloaded ✓ · ", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary, fontWeight = FontWeight.Bold)
                    Text(
                        if (!e.available) "Unavailable · private or deleted" else if (e.duration > 0) fmtDuration(e.duration) else "",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Checkbox(on, { onToggle() }, enabled = e.available)
        }
    }
}
