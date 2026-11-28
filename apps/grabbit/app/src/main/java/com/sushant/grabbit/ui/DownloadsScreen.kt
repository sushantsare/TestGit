package com.sushant.grabbit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.material3.OutlinedButton
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import com.sushant.grabbit.ui.components.groupShape
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.work.WorkInfo
import com.sushant.grabbit.data.SavedItem
import com.sushant.grabbit.download.DownloadWorker
import com.sushant.grabbit.download.Downloads
import com.sushant.grabbit.engine.audioKbps
import com.sushant.grabbit.engine.isAudioQuality
import com.sushant.grabbit.ui.components.AppCard
import com.sushant.grabbit.ui.components.EmptyState
import com.sushant.grabbit.ui.components.EmptyType
import com.sushant.grabbit.ui.components.ScreenTitle
import com.sushant.grabbit.ui.components.SectionLabel
import com.sushant.grabbit.ui.components.VideoThumb
import com.sushant.grabbit.ui.theme.LocalSizes
import com.sushant.grabbit.ui.theme.LocalSpacing
import com.sushant.grabbit.ui.theme.PillShape
import com.sushant.grabbit.ui.theme.TabularNums

private val SORTS = listOf("Newest first", "Oldest first", "Name (A–Z)", "Largest first")

@Composable
fun DownloadsScreen(vm: MainViewModel, modifier: Modifier) {
    val sp = LocalSpacing.current
    val works by vm.works.collectAsState()
    val library by vm.library.collectAsState()
    val paused0 by vm.paused.collectAsState()
    val runs by com.sushant.grabbit.data.PlaylistQueue.runs.collectAsState()
    var filter by rememberSaveable { mutableStateOf("All") }
    var query by rememberSaveable { mutableStateOf("") }
    var sort by rememberSaveable { mutableIntStateOf(0) }
    var searchOpen by rememberSaveable { mutableStateOf(false) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkMissing() }
    LaunchedEffect(library.size) { vm.checkMissing() }

    val active0 = works
        .filter { it.state != WorkInfo.State.SUCCEEDED && it.state != WorkInfo.State.CANCELLED }
        .filter { it.id.toString() !in vm.hidden }
        .sortedByDescending { Downloads.created(it) }
    // Playlist videos are shown inside one card per playlist
    fun grpOf(w: WorkInfo) = Downloads.jobOf(w).opts.grp
    val active = active0.filter { grpOf(it).isBlank() }
    val paused = paused0.filter { it.job.opts.grp.isBlank() }
    val groups = (runs.keys + active0.map(::grpOf) + paused0.map { it.job.opts.grp }).filter { it.isNotBlank() }.distinct()
    val sites = listOf("All") + library.map { it.site }.filter { it.isNotBlank() }.distinct().sorted() +
        (if (library.any { it.isAudio }) listOf("Audio") else emptyList())
    val byChip = when (filter) {
        "All" -> library
        "Audio" -> library.filter { it.isAudio }
        else -> library.filter { it.site == filter }
    }
    val q = query.trim()
    val matched = if (q.isEmpty()) byChip else byChip.filter {
        it.title.contains(q, true) || it.site.contains(q, true) || it.fileName.contains(q, true)
    }
    val shown = when (sort) {
        1 -> matched.sortedBy { it.time }
        2 -> matched.sortedBy { it.title.lowercase() }
        3 -> matched.sortedByDescending { it.size }
        else -> matched.sortedByDescending { it.time }
    }

    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = sp.screen, end = sp.screen, bottom = sp.xl),
        // 3dp between grouped cards; sections add their own space above
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        item(key = "title") {
            ScreenTitle("Downloads") {
                if (library.isNotEmpty()) SearchSortPair(searchOpen || query.isNotEmpty(), { searchOpen = !searchOpen; if (!searchOpen) query = "" }, sort) { sort = it }
            }
        }

        if (active.isEmpty() && paused.isEmpty() && groups.isEmpty() && library.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    EmptyType.FIRST_TIME, "No downloads yet", "Videos you grab show up here and in your gallery",
                    Modifier.padding(top = sp.xxxl), actionLabel = "Grab a video", actionIcon = Icons.Rounded.Link,
                    onAction = { vm.tab = 0 },
                )
            }
            return@LazyColumn
        }

        if (active.isNotEmpty() || paused.isNotEmpty() || groups.isNotEmpty()) {
            val running = active0.filter { !it.state.isFinished }
            item(key = "activeLabel") {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = sp.s, bottom = sp.xs)) {
                    SectionLabel("In progress · ${active.size + paused.size + groups.size}", Modifier.weight(1f))
                    if (active.any { it.state == WorkInfo.State.FAILED }) TextButton(onClick = { vm.clearFinished() }) { Text("Clear failed") }
                    if (running.size + paused0.size >= 2) {
                        if (running.isNotEmpty()) TextButton(onClick = { vm.pauseAll(running) }, modifier = Modifier.testTag("pauseAll")) { Text("Pause all") }
                        else TextButton(onClick = { vm.resumeAll() }, modifier = Modifier.testTag("resumeAll")) { Text("Resume all") }
                    }
                }
            }
            items(groups, key = { "grp_$it" }) { g ->
                PlaylistGroupCard(g, runs[g], active0.filter { grpOf(it) == g }, paused0.filter { it.job.opts.grp == g },
                    library.filter { it.group == g }, vm, Modifier.animateItem().padding(bottom = sp.s))
            }
            val total = active.size + paused.size
            itemsIndexed(active, key = { _, w -> w.id.toString() }) { i, w -> ActiveCard(w, vm, Modifier.animateItem(), groupShape(i, total)) }
            itemsIndexed(paused, key = { _, p -> "p_" + p.job.key }) { i, p -> PausedCard(p, vm, Modifier.animateItem(), groupShape(active.size + i, total)) }
            item(key = "activeGap") { Spacer(Modifier.height(sp.s)) }
        }

        if (library.isNotEmpty()) {
            item(key = "savedLabel") { SectionLabel("Saved · ${library.size}", Modifier.padding(top = sp.l, bottom = sp.xs)) }
            if (searchOpen || query.isNotEmpty()) item(key = "search") { SearchField(query) { query = it } }
            item(key = "chips") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(sp.s), contentPadding = PaddingValues(vertical = sp.xs)) {
                    items(sites) { s ->
                        val sel = filter == s
                        FilterChip(
                            selected = sel, onClick = { filter = s }, label = { Text(s) },
                            leadingIcon = if (sel) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                            shape = MaterialTheme.shapes.small,
                        )
                    }
                }
            }
            if (shown.isEmpty()) item(key = "noResults") {
                EmptyState(
                    EmptyType.NO_RESULTS,
                    if (q.isNotEmpty()) "No results for \"$q\"" else "Nothing in this filter",
                    if (q.isNotEmpty()) "Check the spelling or try fewer words" else "Try another site or show everything",
                    actionLabel = "Clear filters", onAction = { query = ""; filter = "All" },
                )
            }
            item(key = "chipsGap") { Spacer(Modifier.height(sp.xs)) }
            // A playlist shows as one row (tap to see its videos); searching lists the videos one by one
            val rows: List<Any> = if (q.isNotEmpty()) shown else buildList {
                val seen = HashSet<String>()
                shown.forEach { if (it.group.isBlank()) add(it) else if (seen.add(it.group)) add(it.group) }
            }
            itemsIndexed(rows, key = { _, r -> if (r is String) "sg_$r" else (r as SavedItem).id }) { i, r ->
                val m = Modifier.animateItem().clip(groupShape(i, rows.size)).background(MaterialTheme.colorScheme.surfaceContainerLow)
                if (r is String) SavedPlaylistRow(r, library.filter { it.group == r }.sortedBy { it.groupIndex }, vm, m)
                else SavedRow(r as SavedItem, r.id in vm.missing, vm, m)
            }
        }
    }
}

@Composable
private fun ActiveCard(w: WorkInfo, vm: MainViewModel, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge) {
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    val job = Downloads.jobOf(w)
    val failed = w.state == WorkInfo.State.FAILED
    if (failed) {
        val err = w.outputData.getString(DownloadWorker.K_ERROR) ?: "Download failed"
        val raw = w.outputData.getString(DownloadWorker.K_RAW).orEmpty()
        com.sushant.grabbit.ui.components.ErrorCard(
            title = job.title.ifBlank { "Download failed" }, message = err, raw = raw,
            onRetry = { vm.retry(w) }, modifier = modifier,
            extraAction = {
                FilledTonalButton(onClick = { vm.tryAnotherFormat(w) }) {
                    Icon(Icons.Rounded.Tune, null, Modifier.size(sz.iconSmall)); Spacer(Modifier.width(sp.s)); Text("Other format")
                }
                IconButton(onClick = { vm.hide(w.id) }) { Icon(Icons.Rounded.Close, "Dismiss") }
            },
        )
        return
    }
    AppCard(modifier, padding = sp.m, shape = shape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VideoThumb(job.thumb, isAudioQuality(job.quality), Modifier.width(sz.rowThumbW).height(sz.rowThumbH))
            Spacer(Modifier.width(sp.m))
            Column(Modifier.weight(1f)) {
                Text(job.title.ifBlank { job.url }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(job.site, qualityText(job.quality)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = { vm.pause(w) }, modifier = Modifier.testTag("pauseBtn")) { Icon(Icons.Rounded.Pause, "Pause download") }
            IconButton(onClick = { vm.cancel(w.id) }) { Icon(Icons.Rounded.Close, "Cancel download") }
        }
        val p = w.progress
        val pct = p.getFloat(DownloadWorker.P_PCT, 0f)
        val status = p.getString(DownloadWorker.P_STATUS) ?: if (w.state == WorkInfo.State.RUNNING) "Starting…" else "Waiting…"
        val speed = p.getString(DownloadWorker.P_SPEED).orEmpty()
        val eta = fmtEta(p.getLong(DownloadWorker.P_ETA, 0))
        val size = p.getString(DownloadWorker.P_SIZE).orEmpty()
        // While merging/converting there is no percentage: show a moving bar instead of a stuck 99%
        val working = p.getString(DownloadWorker.P_STAGE) == com.sushant.grabbit.download.Stages.WORK
        Spacer(Modifier.height(sp.m))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(status, Modifier.weight(1f), style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (pct > 0f && !working) Text("${pct.toInt()}%", style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNums),
                color = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(sp.s))
        val barMod = Modifier.fillMaxWidth().height(sz.progress)
        // Expressive wavy bar; while merging/converting there is no percentage, so it just keeps moving
        com.sushant.grabbit.ui.components.WavyProgress(if (pct > 0f && !working) pct / 100f else null, Modifier.testTag("wavyProgress"))
        Spacer(Modifier.height(sp.s))
        val detail = if (working) "Almost done · big files take a little longer"
            else listOf(size, speed, eta).filter { it.isNotBlank() }.joinToString(" · ")
        Text(detail.ifEmpty { "Connecting to the site…" },
            style = MaterialTheme.typography.bodySmall.copy(fontFeatureSettings = TabularNums),
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

private fun qualityText(q: String) = when {
    q == com.sushant.grabbit.download.Gallery.Q_ORIGINAL -> "Photos"
    q == com.sushant.grabbit.download.Gallery.Q_JPG -> "Photos · JPG"
    q == "audio" -> "MP3"
    audioKbps(q) != null -> "MP3 · ${audioKbps(q)} kbps"
    q == "best" -> "Best"
    q.startsWith("h") -> q.drop(1).toIntOrNull()?.let { qualityLabel(it) } ?: q
    else -> q
}

@Composable
private fun SavedRow(item: SavedItem, missing: Boolean, vm: MainViewModel, modifier: Modifier) {
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var pendingName by remember { mutableStateOf("") }
    val renameLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        // Android asked "Allow Grabbit to modify this file?": rename again if allowed
        if (r.resultCode == android.app.Activity.RESULT_OK) vm.rename(item, pendingName) {}
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.afterDeleteConfirm(item)
    }
    Row(
        modifier.fillMaxWidth().heightIn(min = 72.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable { if (missing) confirmRemove = true else ctx.openUri(item.uri, item.mime) }
            .padding(vertical = sp.s, horizontal = sp.xs)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumb(item.thumb, item.isAudio, Modifier.width(sz.rowThumbW).height(sz.rowThumbH))
        Spacer(Modifier.width(sp.m))
        Column(Modifier.weight(1f)) {
            Text(item.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(sp.xxs))
            if (missing) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                Spacer(Modifier.width(sp.xs))
                Text("File missing · tap to remove", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error, maxLines = 1)
            } else Text(
                listOf(item.site, if (item.isAudio) "MP3" else "", fmtSize(item.size), fmtDate(item.time))
                    .filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, shape = MaterialTheme.shapes.medium) {
                if (!missing) {
                    DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        onClick = { menu = false; ctx.openUri(item.uri, item.mime) })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { menu = false; ctx.shareUri(item.uri, item.mime) })
                    DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menu = false; renaming = true })
                }
                if (item.sourceUrl.isNotBlank()) DropdownMenuItem(text = { Text("Open original post") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                    onClick = { menu = false; ctx.openLink(item.sourceUrl) })
                if (missing) DropdownMenuItem(text = { Text("Remove from list") }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                    onClick = { menu = false; vm.removeFromList(item) })
                else DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    leadingIcon = { Icon(Icons.Rounded.Delete, null, tint = MaterialTheme.colorScheme.error) },
                    onClick = { menu = false; confirm = true })
            }
        }
    }
    if (renaming) RenameDialog(
        current = item.fileName.substringBeforeLast('.').ifBlank { item.title },
        onDismiss = { renaming = false },
    ) { newName ->
        renaming = false
        pendingName = newName
        vm.rename(item, newName) { sender -> renameLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Delete this file?") },
        text = { Text("\"${item.title}\" will be removed from your phone. This can't be undone.") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                vm.delete(item) { sender -> deleteLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false },
        title = { Text("File not found") },
        text = { Text("This file was deleted or moved outside Grabbit. Remove it from the list?") },
        confirmButton = { TextButton(onClick = { confirmRemove = false; vm.removeFromList(item) }) { Text("Remove") } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text("Keep") } },
    )
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    TextField(
        value = query,
        onValueChange = onQuery,
        modifier = Modifier.fillMaxWidth().padding(bottom = LocalSpacing.current.s).focusRequester(focus).testTag("searchField"),
        placeholder = { Text("Search downloads") },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
        },
        singleLine = true,
        shape = PillShape,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedIndicatorColor = Color.Transparent,
            unfocusedIndicatorColor = Color.Transparent,
        ),
    )
}

/** Search + Sort as a connected pair in the title row (Expressive). */
@Composable
private fun SearchSortPair(searchOn: Boolean, onSearch: () -> Unit, sort: Int, onSort: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(
            onClick = onSearch, shape = RoundedCornerShape(22.dp, 8.dp, 8.dp, 22.dp),
            color = if (searchOn) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.size(width = 52.dp, height = 48.dp).testTag("searchBtn"),
        ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, if (searchOn) "Close search" else "Search downloads") } }
        Box {
            Surface(
                onClick = { open = true }, shape = RoundedCornerShape(8.dp, 22.dp, 22.dp, 8.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(width = 52.dp, height = 48.dp).testTag("sortBtn"),
            ) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.SwapVert, "Sort: ${SORTS[sort]}") } }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.medium) {
                SORTS.forEachIndexed { i, label ->
                    DropdownMenuItem(
                        text = { Text(label) },
                        leadingIcon = { if (i == sort) Icon(Icons.Rounded.Check, null) else Spacer(Modifier.size(24.dp)) },
                        onClick = { open = false; onSort(i) },
                    )
                }
            }
        }
    }
}

@Composable
private fun PausedCard(p: com.sushant.grabbit.data.PausedItem, vm: MainViewModel, modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.extraLarge) {
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    val job = p.job
    AppCard(modifier.testTag("pausedCard"), padding = sp.m, shape = shape) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VideoThumb(job.thumb, isAudioQuality(job.quality), Modifier.width(sz.rowThumbW).height(sz.rowThumbH))
            Spacer(Modifier.width(sp.m))
            Column(Modifier.weight(1f)) {
                Text(job.title.ifBlank { job.url }, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(listOf(job.site, qualityText(job.quality)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = { vm.resume(p) }, modifier = Modifier.testTag("resumeBtn")) { Icon(Icons.Rounded.PlayArrow, "Resume download") }
            IconButton(onClick = { vm.discard(p) }) { Icon(Icons.Rounded.Close, "Cancel download") }
        }
        Spacer(Modifier.height(sp.m))
        Text(
            if (!p.canContinue) "Paused · will start again from the beginning"
            else listOf("Paused", if (p.pct > 0) "${p.pct.toInt()}%" else "", p.size).filter { it.isNotBlank() }.joinToString(" · "),
            style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(sp.s))
        com.sushant.grabbit.ui.components.WavyProgress(if (p.canContinue) p.pct / 100f else 0f, color = MaterialTheme.colorScheme.outline)
    }
}


/** One card per playlist download: progress of the whole playlist and every video inside. */
@Composable
private fun PlaylistGroupCard(
    grp: String, run: com.sushant.grabbit.data.PlaylistRun?, works: List<WorkInfo>,
    paused: List<com.sushant.grabbit.data.PausedItem>, saved: List<SavedItem>, vm: MainViewModel, modifier: Modifier,
) {
    val sp = LocalSpacing.current
    var open by rememberSaveable(grp) { mutableStateOf(true) }
    val running = works.filter { !it.state.isFinished }
    val failed = works.filter { it.state == WorkInfo.State.FAILED }
    val waiting = run?.pending.orEmpty()
    val title = run?.title?.ifBlank { null } ?: works.firstOrNull()?.let { Downloads.jobOf(it).opts.gt }
        ?: paused.firstOrNull()?.job?.opts?.gt ?: saved.firstOrNull()?.groupTitle ?: "Playlist"
    val total = maxOf(run?.total ?: 0, saved.size + failed.size + running.size + waiting.size + paused.size)
    val isPaused = run?.paused == true || (running.isEmpty() && waiting.isNotEmpty() && paused.isNotEmpty())
    val runPct = running.sumOf { it.progress.getFloat(com.sushant.grabbit.download.DownloadWorker.P_PCT, 0f).toDouble() } / 100.0
    val frac = if (total > 0) ((saved.size + runPct) / total).toFloat() else 0f
    val thumb = run?.thumb?.ifBlank { null } ?: saved.firstOrNull()?.thumb ?: works.firstOrNull()?.let { Downloads.jobOf(it).thumb }.orEmpty()
    AppCard(modifier.testTag("playlistCard"), padding = sp.m) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            VideoThumb(thumb, false, Modifier.width(LocalSizes.current.rowThumbW).height(LocalSizes.current.rowThumbH))
            Spacer(Modifier.width(sp.m))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull("${saved.size} of $total saved", if (failed.isNotEmpty()) "${failed.size} failed" else null,
                        if (isPaused) "paused" else if (waiting.isNotEmpty()) "${waiting.size} waiting" else null).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("playlistStatus"),
                )
            }
            if (running.isNotEmpty() || waiting.isNotEmpty() || paused.isNotEmpty()) {
                if (isPaused || running.isEmpty() && paused.isNotEmpty())
                    IconButton(onClick = { vm.resumeGroup(grp) }, modifier = Modifier.testTag("plResume")) { Icon(Icons.Rounded.PlayArrow, "Resume playlist") }
                else IconButton(onClick = { vm.pauseGroup(grp) }, modifier = Modifier.testTag("plPause")) { Icon(Icons.Rounded.Pause, "Pause playlist") }
            }
            IconButton(onClick = { vm.cancelGroup(grp, works) }, modifier = Modifier.testTag("plCancel")) {
                Icon(Icons.Rounded.Close, if (running.isEmpty() && waiting.isEmpty() && paused.isEmpty()) "Remove from list" else "Cancel playlist")
            }
        }
        Spacer(Modifier.height(sp.m))
        com.sushant.grabbit.ui.components.WavyProgress(if (isPaused) frac else frac.coerceAtLeast(0.01f),
            color = if (isPaused) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary)
        val speed = running.mapNotNull { it.progress.getString(com.sushant.grabbit.download.DownloadWorker.P_SPEED)?.takeIf { s -> s.isNotBlank() } }
        if (speed.isNotEmpty()) Text("${running.size} downloading · ${speed.joinToString(" + ")}", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = sp.xs))
        Row(horizontalArrangement = Arrangement.spacedBy(sp.s), modifier = Modifier.padding(top = sp.s)) {
            if (failed.isNotEmpty()) FilledTonalButton(onClick = { vm.retryFailed(grp, failed) }, modifier = Modifier.testTag("retryFailed")) {
                Text("Retry failed (${failed.size})")
            }
            OutlinedButton(onClick = { open = !open }) { Text(if (open) "Hide list" else "Show list") }
        }
        if (open) {
            Spacer(Modifier.height(sp.s))
            data class Line(val index: Int, val title: String, val status: String, val pct: Float?, val kind: Int, val work: WorkInfo?, val pausedItem: com.sushant.grabbit.data.PausedItem?)
            val lines = buildList {
                running.forEach { w ->
                    val j = Downloads.jobOf(w)
                    val p = w.progress
                    val pct = p.getFloat(com.sushant.grabbit.download.DownloadWorker.P_PCT, 0f)
                    val st = p.getString(com.sushant.grabbit.download.DownloadWorker.P_STATUS).orEmpty().ifBlank { "Starting…" }
                    add(Line(j.opts.gi, j.title, if (pct > 0) "$st ${pct.toInt()}%" else st, pct / 100f, 0, w, null))
                }
                failed.forEach { w ->
                    val j = Downloads.jobOf(w)
                    add(Line(j.opts.gi, j.title, "Failed · " + w.outputData.getString(com.sushant.grabbit.download.DownloadWorker.K_ERROR).orEmpty().ifBlank { "couldn't download" }, null, 1, w, null))
                }
                paused.forEach { add(Line(it.job.opts.gi, it.job.title, "Paused" + if (it.pct > 0) " · ${it.pct.toInt()}%" else "", null, 2, null, it)) }
                waiting.take(30).forEach { add(Line(it.opts.gi, it.title, "Waiting", null, 3, null, null)) }
                saved.forEach { add(Line(it.groupIndex, it.title, "Saved · ${fmtSize(it.size)}", null, 4, null, null)) }
            }.sortedWith(compareBy({ it.kind == 4 }, { it.index }))
            val pad = if (total >= 100) 3 else 2
            lines.take(60).forEachIndexed { i, l ->
                Surface(shape = groupShape(i, minOf(lines.size, 60), 18.dp, 6.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 3.dp)) {
                    Column(Modifier.padding(horizontal = sp.m, vertical = sp.s)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(l.index.toString().padStart(pad, '0'), Modifier.width(28.dp), style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Column(Modifier.weight(1f)) {
                                Text(l.title.substringAfter(" - ").ifBlank { l.title }, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(l.status, style = MaterialTheme.typography.bodySmall, maxLines = 2,
                                    color = when (l.kind) { 1 -> MaterialTheme.colorScheme.error; 4 -> MaterialTheme.colorScheme.tertiary; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                            }
                            when (l.kind) {
                                0 -> IconButton(onClick = { l.work?.let { vm.pause(it) } }) { Icon(Icons.Rounded.Pause, "Pause video") }
                                1 -> TextButton(onClick = { l.work?.let { vm.retryFailed(grp, listOf(it)) } }) { Text("Retry") }
                                2 -> IconButton(onClick = { l.pausedItem?.let { vm.resume(it) } }) { Icon(Icons.Rounded.PlayArrow, "Resume video") }
                                else -> {}
                            }
                        }
                        if (l.kind == 0) com.sushant.grabbit.ui.components.WavyProgress(if ((l.pct ?: 0f) > 0f) l.pct else null, Modifier.padding(top = 4.dp))
                    }
                }
            }
            if (waiting.size > 30) Text("…and ${waiting.size - 30} more waiting", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = sp.xs, start = sp.s))
        }
    }
}

/** A finished playlist in Saved: one row; tap to see its videos. */
@Composable
private fun SavedPlaylistRow(grp: String, items: List<SavedItem>, vm: MainViewModel, modifier: Modifier) {
    val sp = LocalSpacing.current
    var open by rememberSaveable(grp) { mutableStateOf(false) }
    Column(modifier.testTag("savedPlaylist")) {
        Row(
            Modifier.fillMaxWidth().clickable { open = !open }.padding(horizontal = sp.m, vertical = sp.m),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            VideoThumb(items.firstOrNull()?.thumb.orEmpty(), items.all { it.isAudio },
                Modifier.width(LocalSizes.current.rowThumbW).height(LocalSizes.current.rowThumbH))
            Spacer(Modifier.width(sp.m))
            Column(Modifier.weight(1f)) {
                Text(items.firstOrNull()?.groupTitle?.ifBlank { null } ?: "Playlist", style = MaterialTheme.typography.titleSmall,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text("${items.size} video${if (items.size > 1) "s" else ""} · ${fmtSize(items.sumOf { it.size })} · ${if (open) "tap to hide" else "tap to see them"}",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(if (open) Icons.Rounded.KeyboardArrowUp else Icons.Rounded.KeyboardArrowDown, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (open) items.forEach { SavedRow(it, it.id in vm.missing, vm, Modifier.padding(start = sp.m)) }
    }
}
