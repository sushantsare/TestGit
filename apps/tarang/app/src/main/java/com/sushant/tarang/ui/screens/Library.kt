package com.sushant.tarang.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.sushant.tarang.ui.sheets.Sheet

private val LIB_TABS = listOf("Songs", "Albums", "Artists", "Playlists")

@UnstableApi
@Composable
fun LibraryScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val s by vm.settings.collectAsState()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var songSort by rememberSaveable { mutableStateOf(s.defaultSort) }
    var albumSort by rememberSaveable { mutableIntStateOf(0) }
    var artistSort by rememberSaveable { mutableIntStateOf(0) }
    var sortDialog by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf(setOf<Long>()) }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { selected = emptySet() }

    Column(Modifier.fillMaxSize().testTag("library")) {
        if (selecting) SelectionBar(selected, lib.songs.map { it.id }, onClear = { selected = emptySet() }, onAll = { selected = lib.songs.map { it.id }.toSet() })
        else PageHeader("Library", Modifier.statusBarsPadding()) {
            IconButton(onClick = { nav.go(R0.SEARCH) }) { Icon(Icons.Rounded.Search, "Search") }
            if (tab != 3) IconButton(onClick = { sortDialog = true }, Modifier.testTag("sort")) { Icon(Icons.Rounded.Sort, "Sort") }
        }
        PrimaryTabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
            LIB_TABS.forEachIndexed { i, t -> Tab(tab == i, { tab = i; selected = emptySet() }, text = { Text(t) }, modifier = Modifier.testTag("libtab_${t.lowercase()}")) }
        }
        when (tab) {
            0 -> SongsTab(vm.repo.sort(lib.songs, songSort), songSort, selected, { selected = it })
            1 -> AlbumsTab(when (albumSort) { 1 -> lib.albums.sortedBy { it.artist.lowercase() }; 2 -> lib.albums.sortedByDescending { it.year }; else -> lib.albums.sortedBy { it.name.lowercase() } })
            2 -> ArtistsTab(if (artistSort == 1) lib.artists.sortedByDescending { it.songs.size } else lib.artists, artistSort)
            else -> PlaylistsTab()
        }
    }
    if (sortDialog) when (tab) {
        0 -> ChoiceDialog("Sort songs", SongSort.entries, songSort, { it.label }, { sortDialog = false }) { songSort = it; sortDialog = false }
        1 -> ChoiceDialog("Sort albums", listOf(0, 1, 2), albumSort, { listOf("Name A–Z", "Artist", "Newest year first")[it] }, { sortDialog = false }) { albumSort = it; sortDialog = false }
        2 -> ChoiceDialog("Sort artists", listOf(0, 1), artistSort, { listOf("Name A–Z", "Most songs")[it] }, { sortDialog = false }) { artistSort = it; sortDialog = false }
    }
}

@UnstableApi
@Composable
fun SelectionBar(selected: Set<Long>, all: List<Long>, onClear: () -> Unit, onAll: () -> Unit) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val songs = all.filter { it in selected }.mapNotNull { lib.byId[it] }
    Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(horizontal = 4.dp).testTag("selection_bar"), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onClear) { Icon(Icons.Rounded.Close, "Stop selecting") }
        Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onAll, Modifier.testTag("select_all")) { Icon(Icons.Rounded.SelectAll, "Select all") }
        IconButton(onClick = { vm.player.playList(songs, kind = "Library", label = "Selected songs"); onClear() }, Modifier.testTag("sel_play")) { Icon(Icons.Rounded.PlayArrow, "Play selected") }
        IconButton(onClick = { vm.player.addToQueue(songs); vm.snack.tryEmit("Added ${plural(songs.size, "song")} to the queue"); onClear() }, Modifier.testTag("sel_queue")) {
            Icon(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue")
        }
        IconButton(onClick = { nav.sheet(Sheet.AddToPlaylist(songs.map { it.id })); onClear() }, Modifier.testTag("sel_playlist")) { Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist") }
        IconButton(onClick = { vm.likeMany(songs.map { it.id }, true); onClear() }, Modifier.testTag("sel_like")) { Icon(Icons.Rounded.FavoriteBorder, "Like selected") }
    }
}

@UnstableApi
@Composable
private fun SongsTab(songs: List<Song>, sort: SongSort, selected: Set<Long>, setSelected: (Set<Long>) -> Unit) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val ui by vm.player.ui.collectAsState()
    val liked by vm.liked.collectAsState()
    val cs = MaterialTheme.colorScheme
    var longFor by remember { mutableStateOf<Long?>(null) }
    LazyColumn(Modifier.fillMaxSize().testTag("songs_list"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
        if (songs.isEmpty()) { item { EmptyState(Icons.Rounded.MusicOff, "No songs yet", "Songs on your phone show up here.") }; return@LazyColumn }
        item {
            Row(Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Icons.Rounded.Favorite, "Liked songs", plural(liked.size, "song"), cs.primaryContainer, cs.onPrimaryContainer, Modifier.weight(1f).testTag("tile_liked")) { nav.go(R0.LIKED) }
                Tile(Icons.Rounded.Shuffle, "Shuffle all", plural(songs.size, "song"), cs.tertiaryContainer, cs.onTertiaryContainer, Modifier.weight(1f).testTag("tile_shuffle")) {
                    vm.player.playList(songs, shuffle = true, kind = "Library", label = "All songs")
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${plural(songs.size, "song")} · ${sort.label}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
                TextButton(onClick = { setSelected(if (selected.isEmpty()) setOf(songs.first().id) else emptySet()) }, Modifier.testTag("select")) { Text(if (selected.isEmpty()) "Select" else "Done") }
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { i, s ->
            Box {
                SongRow(s, playing = s.id == ui.currentId, selected = s.id in selected,
                    onClick = { if (selected.isNotEmpty()) setSelected(if (s.id in selected) selected - s.id else selected + s.id) else vm.player.tapSong(s, songs, "Library", "All songs") },
                    onLongClick = { if (selected.isNotEmpty()) setSelected(selected + s.id) else longFor = s.id },
                    onMore = { nav.songMenu(s) })
                DropdownMenu(longFor == s.id, { longFor = null }, offset = androidx.compose.ui.unit.DpOffset(72.dp, 0.dp)) {
                    DropdownMenuItem(text = { Text("Play from here") }, leadingIcon = { Icon(Icons.Rounded.PlayArrow, null) },
                        onClick = { longFor = null; vm.player.playList(songs, i, kind = "Library", label = "All songs") }, modifier = Modifier.testTag("play_from_here"))
                    DropdownMenuItem(text = { Text("Select") }, leadingIcon = { Icon(Icons.Rounded.CheckCircleOutline, null) }, onClick = { longFor = null; setSelected(setOf(s.id)) })
                }
            }
        }
    }
}

@Composable
private fun Tile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, sub: String, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(RoundedCornerShape(24.dp)).background(bg).clickable(onClick = onClick).padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = fg)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = fg, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(sub, color = fg.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@UnstableApi
@Composable
private fun AlbumsTab(albums: List<Album>) {
    val nav = LocalNav.current
    val cs = MaterialTheme.colorScheme
    if (albums.isEmpty()) { EmptyState(Icons.Rounded.Album, "No albums", "Albums appear when your songs have album details."); return }
    LazyVerticalGrid(GridCells.Adaptive(156.dp), Modifier.fillMaxSize().testTag("albums_grid"),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = LocalDockPadding.current), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Text(plural(albums.size, "album"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(8.dp))
        }
        items(albums, key = { it.id }) { a ->
            Column(Modifier.clip(RoundedCornerShape(20.dp)).clickable { nav.go(R0.album(a.id)) }.padding(8.dp).testTag("album_${a.id}")) {
                BoxWithConstraints { Cover(a.songs.first(), maxWidth, RoundedCornerShape(20.dp)) }
                Spacer(Modifier.height(8.dp))
                Text(a.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(a.artist + if (a.year > 0) " · ${a.year}" else "", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@UnstableApi
@Composable
private fun ArtistsTab(artists: List<Artist>, sort: Int) {
    val nav = LocalNav.current
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().testTag("artists_list"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
        if (artists.isEmpty()) { item { EmptyState(Icons.Rounded.Person, "No artists", "Artists appear when your songs have artist details.") }; return@LazyColumn }
        item {
            Text("${plural(artists.size, "artist")} · ${if (sort == 1) "Most songs" else "A–Z"}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))
        }
        items(artists, key = { it.name }) { a ->
            Row(Modifier.fillMaxWidth().clickable { nav.go(R0.artist(a.name)) }.padding(horizontal = 20.dp, vertical = 10.dp).testTag("artist_${a.name}"), verticalAlignment = Alignment.CenterVertically) {
                LetterAvatar(a.name, 52.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(a.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${plural(a.songs.size, "song")} · ${plural(a.albumCount, "album")}", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun LetterAvatar(name: String, size: androidx.compose.ui.unit.Dp) {
    val cs = MaterialTheme.colorScheme
    val (bg, fg) = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer)[(name.hashCode() and 0x7fffffff) % 3]
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "#", color = fg, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
    }
}

@UnstableApi
@Composable
private fun PlaylistsTab() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lists by vm.playlists.collectAsState()
    val lib by vm.library.collectAsState()
    var creating by remember { mutableStateOf(false) }
    val cs = MaterialTheme.colorScheme
    LazyColumn(Modifier.fillMaxSize().testTag("playlists"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
        item {
            Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 20.dp, vertical = 14.dp).testTag("create_playlist"), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(cs.primary), contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, null, tint = cs.onPrimary) }
                Spacer(Modifier.width(16.dp))
                Text("New playlist", style = MaterialTheme.typography.titleMedium, color = cs.primary)
            }
        }
        if (lists.isEmpty()) item { Text("Make a playlist to keep songs together. Add songs from any song's ⋮ menu.", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.padding(20.dp)) }
        items(lists, key = { it.id }) { p ->
            val songs = p.songIds.mapNotNull { lib.byId[it] }
            Row(Modifier.fillMaxWidth().clickable { nav.go(R0.playlist(p.id)) }.padding(horizontal = 20.dp, vertical = 8.dp).testTag("playlist_${p.id}"), verticalAlignment = Alignment.CenterVertically) {
                CoverStack(songs, 56.dp)
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(plural(songs.size, "song"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                }
            }
        }
    }
    if (creating) TextInputDialog("New playlist", "", "Create", onDismiss = { creating = false }) { n -> creating = false; if (n.isNotBlank()) vm.createPlaylist(n) }
}
