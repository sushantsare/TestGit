package com.sushant.grabbit.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.work.WorkInfo
import coil.compose.AsyncImage
import com.sushant.grabbit.data.SavedItem
import com.sushant.grabbit.download.DownloadWorker
import com.sushant.grabbit.download.Downloads

@Composable
fun DownloadsScreen(vm: MainViewModel, modifier: Modifier) {
    val works by vm.works.collectAsState()
    val library by vm.library.collectAsState()
    var filter by remember { mutableStateOf("All") }
    var query by remember { mutableStateOf("") }
    var sort by remember { mutableStateOf(0) }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.checkMissing() }
    LaunchedEffect(library.size) { vm.checkMissing() }

    val active = works
        .filter { it.state != WorkInfo.State.SUCCEEDED && it.state != WorkInfo.State.CANCELLED }
        .filter { it.id.toString() !in vm.hidden }
        .sortedByDescending { Downloads.created(it) }
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
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Downloads", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f))
                if (works.any { it.state.isFinished }) TextButton(onClick = { vm.clearFinished() }) { Text("Clear finished") }
            }
        }

        if (active.isNotEmpty()) {
            item { SectionTitle("In progress") }
            items(active, key = { it.id.toString() }) { w -> ActiveRow(w, vm) }
        }

        item { SectionTitle("Saved (${library.size})") }
        if (library.isEmpty()) {
            item { EmptyState() }
        } else {
            item { SearchSortRow(query, { query = it }, sort, { sort = it }) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sites) { s -> FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) }) }
                }
            }
            if (shown.isEmpty()) item {
                Text(
                    if (q.isNotEmpty()) "No downloads match \"$q\"" else "Nothing in this filter",
                    Modifier.fillMaxWidth().padding(vertical = 24.dp), textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            items(shown, key = { it.id }) { item -> SavedRow(item, item.id in vm.missing, vm) }
        }
    }
}

@Composable
private fun SectionTitle(t: String) =
    Text(t, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)

@Composable
private fun Thumb(url: String, audio: Boolean) {
    Box(
        Modifier.width(112.dp).height(64.dp).clip(RoundedCornerShape(12.dp))
            .background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isNotBlank()) {
            AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        if (audio) Box(
            Modifier.size(28.dp).clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.55f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.MusicNote, null, tint = Color.White, modifier = Modifier.size(18.dp)) }
    }
}

@Composable
private fun ActiveRow(w: WorkInfo, vm: MainViewModel) {
    val job = Downloads.jobOf(w)
    val failed = w.state == WorkInfo.State.FAILED
    var showRaw by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (failed) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
    ) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Thumb(job.thumb, job.quality == "audio")
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(job.title.ifBlank { job.url }, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                    Text(
                        listOf(job.site, qualityText(job.quality)).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (failed) {
                    IconButton(onClick = { vm.hide(w.id) }) { Icon(Icons.Rounded.Close, "Dismiss") }
                } else {
                    IconButton(onClick = { vm.cancel(w.id) }) { Icon(Icons.Rounded.Close, "Cancel") }
                }
            }
            if (failed) {
                val err = w.outputData.getString(DownloadWorker.K_ERROR) ?: "Download failed"
                val raw = w.outputData.getString(DownloadWorker.K_RAW).orEmpty()
                Text(err, style = MaterialTheme.typography.bodyMedium)
                if (showRaw && raw.isNotBlank()) Text(raw.takeLast(1500), style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = { vm.retry(w) }) {
                        Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Retry")
                    }
                    FilledTonalButton(onClick = { vm.tryAnotherFormat(w) }) {
                        Icon(Icons.Rounded.Tune, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Other format")
                    }
                }
                if (raw.isNotBlank()) TextButton(onClick = { showRaw = !showRaw }) {
                    Text(if (showRaw) "Hide details" else "Show details")
                }
            } else {
                val p = w.progress
                val pct = p.getFloat(DownloadWorker.P_PCT, 0f)
                val status = p.getString(DownloadWorker.P_STATUS)
                    ?: if (w.state == WorkInfo.State.RUNNING) "Starting…" else "Waiting…"
                val speed = p.getString(DownloadWorker.P_SPEED).orEmpty()
                val eta = fmtEta(p.getLong(DownloadWorker.P_ETA, 0))
                val size = p.getString(DownloadWorker.P_SIZE).orEmpty()
                if (pct > 0f) {
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(status, Modifier.weight(1f), style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (pct > 0f) Text("${pct.toInt()}%", style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                val detail = listOf(size, speed, eta).filter { it.isNotBlank() }.joinToString("  ·  ")
                Text(
                    detail.ifEmpty { "Connecting to the site…" },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun qualityText(q: String) = when {
    q == "audio" -> "MP3"
    q == "best" -> "Best"
    q.startsWith("h") -> q.drop(1).toIntOrNull()?.let { qualityLabel(it) } ?: q
    else -> q
}

@Composable
private fun SavedRow(item: SavedItem, missing: Boolean, vm: MainViewModel) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        vm.afterDeleteConfirm(item)
    }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable {
            if (missing) confirmRemove = true else ctx.openUri(item.uri, item.mime)
        },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(item.thumb, item.isAudio)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                if (missing) Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.ErrorOutline, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(Modifier.width(4.dp))
                    Text("File missing · tap to remove", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error, maxLines = 1)
                } else Text(
                    listOf(item.site, fmtSize(item.size), fmtDate(item.time)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    if (!missing) {
                        DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                            onClick = { menu = false; ctx.openUri(item.uri, item.mime) })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                            onClick = { menu = false; ctx.shareUri(item.uri, item.mime) })
                    }
                    if (item.sourceUrl.isNotBlank()) DropdownMenuItem(text = { Text("Open original post") },
                        leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                        onClick = { menu = false; ctx.openLink(item.sourceUrl) })
                    if (missing) DropdownMenuItem(text = { Text("Remove from list") }, leadingIcon = { Icon(Icons.Rounded.Close, null) },
                        onClick = { menu = false; vm.removeFromList(item) })
                    else DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; confirm = true })
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete this file?") },
        text = { Text("\"${item.title}\" will be deleted from your phone.") },
        confirmButton = {
            TextButton(onClick = {
                confirm = false
                vm.delete(item) { sender -> deleteLauncher.launch(IntentSenderRequest.Builder(sender).build()) }
            }) { Text("Delete") }
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

private val SORTS = listOf("Newest first", "Oldest first", "Name (A–Z)", "Largest first")

@Composable
private fun SearchSortRow(query: String, onQuery: (String) -> Unit, sort: Int, onSort: (Int) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            modifier = Modifier.weight(1f).testTag("searchField"),
            placeholder = { Text("Search downloads") },
            leadingIcon = { Icon(Icons.Rounded.Search, null) },
            trailingIcon = {
                if (query.isNotEmpty()) IconButton(onClick = { onQuery("") }) { Icon(Icons.Rounded.Close, "Clear search") }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
        )
        Spacer(Modifier.width(8.dp))
        Box {
            IconButton(onClick = { open = true }, modifier = Modifier.testTag("sortBtn")) {
                Icon(Icons.Rounded.SwapVert, "Sort: ${SORTS[sort]}")
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
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
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Rounded.DownloadDone, null, Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Nothing here yet", fontWeight = FontWeight.Bold)
        Text(
            "Videos you download will show up here and in your gallery (Movies/Grabbit).",
            textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
