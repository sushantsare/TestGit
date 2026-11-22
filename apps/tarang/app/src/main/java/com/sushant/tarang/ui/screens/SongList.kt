package com.sushant.tarang.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.*
import com.sushant.tarang.ui.LocalDockPadding
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.*
import kotlin.math.roundToInt

sealed class ListKind {
    data object Liked : ListKind()
    data class FolderList(val path: String) : ListKind()
    data class AlbumList(val id: Long) : ListKind()
    data class ArtistList(val name: String) : ListKind()
    data class PlaylistList(val id: Long) : ListKind()
    data class Mix(val kind: String) : ListKind()
}

private data class ListModel(val title: String, val subtitle: String, val kind: String, val songs: List<Song>, val numbered: Boolean, val icon: androidx.compose.ui.graphics.vector.ImageVector?)

@UnstableApi
@Composable
fun SongListScreen(kind: ListKind) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val liked by vm.liked.collectAsState()
    val lists by vm.playlists.collectAsState()
    val counts by vm.counts.collectAsState()
    val ui by vm.player.ui.collectAsState()
    val cs = MaterialTheme.colorScheme
    val playlist = (kind as? ListKind.PlaylistList)?.let { k -> lists.firstOrNull { it.id == k.id } }
    val model = remember(kind, lib, liked, playlist, counts) {
        when (kind) {
            ListKind.Liked -> ListModel("Liked songs", "", "Liked songs", liked.mapNotNull { lib.byId[it] }, false, Icons.Rounded.Favorite)
            is ListKind.FolderList -> lib.folders.firstOrNull { it.path == kind.path }.let { f ->
                ListModel(f?.name ?: kind.path.substringAfterLast('/'), kind.path, "Folder", f?.songs?.sortedWith(compareBy({ it.track.takeIf { t -> t > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() })) ?: emptyList(), true, null)
            }
            is ListKind.AlbumList -> lib.albums.firstOrNull { it.id == kind.id }.let { a ->
                ListModel(a?.name ?: UNKNOWN_ALBUM, listOfNotNull(a?.artist, a?.year?.takeIf { it > 0 }?.toString()).joinToString(" · "), "Album",
                    a?.songs?.sortedWith(compareBy({ it.track.takeIf { t -> t > 0 } ?: Int.MAX_VALUE }, { it.title.lowercase() })) ?: emptyList(), true, null)
            }
            is ListKind.ArtistList -> lib.artists.firstOrNull { it.name == kind.name }.let { a ->
                ListModel(kind.name, a?.let { plural(it.albumCount, "album") } ?: "", "Artist", a?.songs?.sortedBy { it.title.lowercase() } ?: emptyList(), false, Icons.Rounded.Person)
            }
            is ListKind.PlaylistList -> ListModel(playlist?.name ?: "Playlist", "Playlist", "Playlist", playlist?.songIds?.mapNotNull { lib.byId[it] } ?: emptyList(), true, null)
            is ListKind.Mix -> MixKind.entries.firstOrNull { it.name == kind.kind }.let { m ->
                ListModel(m?.title ?: "Mix", m?.subtitle ?: "", "Mix", m?.let { vm.repo.mix(it, lib) } ?: emptyList(), false, Icons.Rounded.AutoAwesome)
            }
        }
    }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    var hiding by remember { mutableStateOf(false) }
    var reorder by remember { mutableStateOf(false) }
    var order by remember(model.songs) { mutableStateOf(model.songs) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    BackHandler(selected.isNotEmpty()) { selected = emptySet() }
    BackHandler(reorder) { reorder = false }
    val label = model.title
    val play: (Int, Boolean) -> Unit = { i, shuffle -> vm.player.playList(model.songs, i, shuffle, model.kind, label) }

    Column(Modifier.fillMaxSize().testTag("song_list")) {
        if (selected.isNotEmpty()) SelectionBar(selected, model.songs.map { it.id }, onClear = { selected = emptySet() }, onAll = { selected = model.songs.map { it.id }.toSet() })
        else InnerTopBar(if (reorder) "Reorder" else "", { if (reorder) reorder = false else nav.back() }) {
            if (reorder) TextButton(onClick = { playlist?.let { vm.reorderPlaylist(it.id, order.map { s -> s.id }) }; reorder = false }, Modifier.testTag("reorder_done")) { Text("Done") }
            else if (kind is ListKind.PlaylistList || kind is ListKind.FolderList) Box {
                IconButton(onClick = { menu = true }, Modifier.testTag("list_menu")) { Icon(Icons.Rounded.MoreVert, "More options") }
                DropdownMenu(menu, { menu = false }) {
                    if (kind is ListKind.PlaylistList) {
                        DropdownMenuItem(text = { Text("Rename") }, leadingIcon = { Icon(Icons.Rounded.Edit, null) }, onClick = { menu = false; renaming = true })
                        if (model.songs.size > 1) DropdownMenuItem(text = { Text("Reorder songs") }, leadingIcon = { Icon(Icons.Rounded.SwapVert, null) }, onClick = { menu = false; order = model.songs; reorder = true })
                        DropdownMenuItem(text = { Text("Delete playlist") }, leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; deleting = true })
                    } else {
                        DropdownMenuItem(text = { Text("Hide this folder") }, leadingIcon = { Icon(Icons.Rounded.VisibilityOff, null) }, onClick = { menu = false; hiding = true })
                    }
                }
            }
        }
        val rowH = with(LocalDensity.current) { 60.dp.toPx() }
        var dragFrom by remember { mutableIntStateOf(-1) }
        var dragY by remember { mutableFloatStateOf(0f) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
            item {
                // header card
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).clip(RoundedCornerShape(28.dp)).background(cs.surfaceContainer).padding(20.dp).testTag("list_header")) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (model.icon != null && kind !is ListKind.Mix) Box(Modifier.size(112.dp).clip(RoundedCornerShape(24.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
                            if (kind is ListKind.ArtistList) LetterAvatar(model.title, 96.dp) else Icon(model.icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(48.dp))
                        } else CoverStack(model.songs, 112.dp)
                        Spacer(Modifier.width(18.dp))
                        Column(Modifier.weight(1f)) {
                            Text(model.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (model.subtitle.isNotBlank()) Text(model.subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text("${plural(model.songs.size, "song")} · ${formatListen(model.songs.sumOf { it.durationMs })}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    if (model.songs.isNotEmpty() && !reorder) {
                        Spacer(Modifier.height(16.dp))
                        PlayButtons(onPlay = { play(0, false) }, onShuffle = { play(0, true) }, compactShuffle = true, sidePadding = 0.dp)
                    }
                }
            }
            if (model.songs.isEmpty()) item {
                EmptyState(if (kind is ListKind.Liked) Icons.Rounded.FavoriteBorder else Icons.Rounded.MusicNote,
                    when (kind) { ListKind.Liked -> "No liked songs yet"; is ListKind.PlaylistList -> "This playlist is empty"; is ListKind.Mix -> "Nothing here yet"; else -> "No songs" },
                    when (kind) { ListKind.Liked -> "Tap the heart in the player to keep songs here."; is ListKind.PlaylistList -> "Add songs from any song's ⋮ menu."; is ListKind.Mix -> "Play some music and this mix fills itself."; else -> "" })
            }
            val rows = if (reorder) order else model.songs
            itemsIndexed(rows, key = { _, s -> s.id }) { i, s ->
                if (reorder) Row(
                    Modifier.fillMaxWidth().height(60.dp).graphicsLayer { if (dragFrom == i) { translationY = dragY; shadowElevation = 12f } }
                        .background(cs.background).padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically,
                ) {
                    Cover(s, 44.dp, RoundedCornerShape(12.dp)); Spacer(Modifier.width(14.dp))
                    Text(s.title, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.size(56.dp).pointerInput(i) {
                        detectDragGestures(onDragStart = { dragFrom = i; dragY = 0f },
                            onDragEnd = {
                                val to = (i + (dragY / rowH).roundToInt()).coerceIn(0, order.lastIndex)
                                if (to != i) order = order.toMutableList().apply { add(to, removeAt(i)) }
                                dragFrom = -1; dragY = 0f
                            }, onDragCancel = { dragFrom = -1; dragY = 0f }) { c, a -> c.consume(); dragY += a.y }
                    }.testTag("drag_$i"), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragHandle, "Drag to reorder") }
                } else SongRow(
                    s, playing = s.id == ui.currentId, number = if (model.numbered) i + 1 else null, selected = s.id in selected,
                    onClick = { if (selected.isNotEmpty()) selected = if (s.id in selected) selected - s.id else selected + s.id else vm.player.tapSong(s, model.songs, model.kind, label) },
                    onLongClick = { selected = selected + s.id },
                    onMore = { nav.songMenu(s, (kind as? ListKind.PlaylistList)?.id) },
                    meta = if (model.numbered) "${s.artist} · ${formatDuration(s.durationMs)}" else null,
                )
            }
        }
    }
    if (renaming && playlist != null) TextInputDialog("Rename playlist", playlist.name, "Rename", { renaming = false }) { n -> renaming = false; if (n.isNotBlank()) vm.renamePlaylist(playlist.id, n) }
    if (deleting && playlist != null) ConfirmDialog("Delete “${playlist.name}”?", "Only the playlist is deleted. The songs stay on your phone.", "Delete",
        { deleting = false }, { deleting = false; vm.deletePlaylist(playlist.id); nav.back() })
    if (hiding && kind is ListKind.FolderList) ConfirmDialog("Hide “${model.title}”?", "Its songs won't show anywhere in Tarang. You can switch it back on in Settings → Folders to scan.", "Hide",
        { hiding = false }, {
            hiding = false
            vm.setSettings { st ->
                val base = if (st.foldersCustomized) st.excludedFolders else lib.all.map { it.folderPath }.distinct().filter { Prefs.hiddenByDefault(it) }.toSet()
                st.copy(foldersCustomized = true, excludedFolders = base + kind.path)
            }
            nav.back()
        }, destructive = false)
}
