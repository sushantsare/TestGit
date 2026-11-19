package com.sushant.grabbit.ui

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
import androidx.compose.material.icons.rounded.Close
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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

    val active = works
        .filter { it.state != WorkInfo.State.SUCCEEDED && it.state != WorkInfo.State.CANCELLED }
        .filter { it.id.toString() !in vm.hidden }
        .sortedByDescending { Downloads.created(it) }
    val sites = listOf("All") + library.map { it.site }.filter { it.isNotBlank() }.distinct().sorted() +
        (if (library.any { it.isAudio }) listOf("Audio") else emptyList())
    val shown = when (filter) {
        "All" -> library
        "Audio" -> library.filter { it.isAudio }
        else -> library.filter { it.site == filter }
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
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(sites) { s -> FilterChip(selected = filter == s, onClick = { filter = s }, label = { Text(s) }) }
                }
            }
            items(shown, key = { it.id }) { item -> SavedRow(item, vm) }
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
                if (pct > 0f) {
                    LinearProgressIndicator(progress = { pct / 100f }, modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                } else {
                    LinearProgressIndicator(Modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)))
                }
                Text(
                    listOf(status, if (pct > 0f) "${pct.toInt()}%" else "", speed, eta)
                        .filter { it.isNotBlank() }.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
private fun SavedRow(item: SavedItem, vm: MainViewModel) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth().clickable { ctx.openUri(item.uri, item.mime) },
    ) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumb(item.thumb, item.isAudio)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(item.title, maxLines = 2, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.SemiBold)
                Text(
                    listOf(item.site, fmtSize(item.size), fmtDate(item.time)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Play") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        onClick = { menu = false; ctx.openUri(item.uri, item.mime) })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                        onClick = { menu = false; ctx.shareUri(item.uri, item.mime) })
                    if (item.sourceUrl.isNotBlank()) DropdownMenuItem(text = { Text("Open original post") },
                        leadingIcon = { Icon(Icons.Rounded.OpenInBrowser, null) },
                        onClick = { menu = false; ctx.openLink(item.sourceUrl) })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menu = false; confirm = true })
                }
            }
        }
    }
    if (confirm) AlertDialog(
        onDismissRequest = { confirm = false },
        title = { Text("Delete this file?") },
        text = { Text(item.title) },
        confirmButton = { TextButton(onClick = { confirm = false; vm.delete(item) }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } },
    )
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
