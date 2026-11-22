package com.sushant.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.*
import com.sushant.tarang.ui.LocalDockPadding
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.R0
import com.sushant.tarang.ui.components.*

@UnstableApi
@Composable
fun FoldersScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val counts by vm.counts.collectAsState()
    var order by rememberSaveable { mutableIntStateOf(0) }
    val cs = MaterialTheme.colorScheme
    val folders = remember(lib, order, counts) {
        when (order) {
            1 -> lib.folders.sortedByDescending { it.songs.size }
            2 -> lib.folders.sortedByDescending { f -> f.songs.maxOfOrNull { counts[it.id]?.lastPlayed ?: 0L } ?: 0L }
            else -> lib.folders.sortedBy { it.name.lowercase() }
        }
    }
    LazyColumn(Modifier.fillMaxSize().testTag("folders"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
        item {
            PageHeader("Folders", Modifier.statusBarsPadding()) {
                IconButton(onClick = { nav.go(R0.SEARCH) }) { Icon(Icons.Rounded.Search, "Search") }
            }
        }
        if (lib.folders.isEmpty()) {
            item { EmptyState(Icons.Rounded.FolderOff, "No music folders", "Folders with songs show up here.") { OutlinedButton(onClick = { nav.go(R0.SCAN_FOLDERS) }) { Text("Folders to scan") } } }
            return@LazyColumn
        }
        item { FilterChipRow(listOf("Name", "Most songs", "Recently played"), order, { order = it }) }
        item { Text(plural(folders.size, "folder"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
        items(folders, key = { it.path }) { f ->
            Row(Modifier.fillMaxWidth().clickable { nav.go(R0.folder(f.path)) }.padding(horizontal = 16.dp, vertical = 8.dp).testTag("folder_${f.name}"), verticalAlignment = Alignment.CenterVertically) {
                CoverStack(f.songs, 64.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(f.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${plural(f.songs.size, "song")} · ${formatListen(f.durationMs)}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    Text(f.path, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                IconButton(onClick = { vm.player.playList(f.songs, kind = "Folder", label = f.name) }) { Icon(Icons.Rounded.PlayCircle, "Play ${f.name}", tint = cs.primary) }
            }
        }
    }
}

/** Settings → Folders to scan: switch each music folder on or off. */
@UnstableApi
@Composable
fun ScanFoldersScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val s by vm.settings.collectAsState()
    val all = remember(lib.all) { lib.all.groupBy { it.folderPath }.map { (p, l) -> Folder(p, l.first().folderName, l) }.sortedBy { it.name.lowercase() } }
    fun visible(path: String) = vm.repo.isFolderVisible(path, s)
    Column(Modifier.fillMaxSize()) {
        InnerTopBar("Folders to scan", nav::back)
        LazyColumn(Modifier.fillMaxSize().testTag("scan_folders"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
            item {
                Text("Songs in switched-off folders are hidden everywhere in Tarang. Folders like WhatsApp audio and call recordings start switched off.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            }
            items(all, key = { it.path }) { f ->
                val on = visible(f.path)
                ListItemRow(f.name, "${plural(f.songs.size, "file")} · ${f.path}", Icons.Rounded.Folder,
                    onClick = { toggle(vm, f.path, on, all) }, modifier = Modifier.testTag("scanf_${f.name}"),
                    trailing = { Switch(on, { toggle(vm, f.path, on, all) }) })
            }
            item {
                TextButton(onClick = { vm.rescan() }, Modifier.padding(16.dp)) { Icon(Icons.Rounded.Refresh, null); Spacer(Modifier.width(8.dp)); Text("Rescan now") }
            }
        }
    }
}

@UnstableApi
private fun toggle(vm: com.sushant.tarang.ui.MainVm, path: String, on: Boolean, all: List<Folder>) {
    vm.setSettings { st ->
        // the first change freezes today's defaults into an explicit list
        val base = if (st.foldersCustomized) st.excludedFolders else all.map { it.path }.filter { Prefs.hiddenByDefault(it) }.toSet()
        st.copy(foldersCustomized = true, excludedFolders = if (on) base + path else base - path)
    }
}
