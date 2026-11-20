package com.sushant.gallery.ui.albums

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sushant.gallery.R
import com.sushant.gallery.data.Album
import com.sushant.gallery.data.AlbumSort
import com.sushant.gallery.data.Formats
import com.sushant.gallery.data.Source
import com.sushant.gallery.data.Sorting
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.AlbumSortSheet
import com.sushant.gallery.ui.components.AppTopBar
import com.sushant.gallery.ui.components.EmptyState
import com.sushant.gallery.ui.components.GridGroup
import com.sushant.gallery.ui.components.Illus
import com.sushant.gallery.ui.components.MediaGrid
import com.sushant.gallery.ui.components.PinButton
import com.sushant.gallery.ui.components.SectionHeader
import com.sushant.gallery.ui.components.SortSheet
import com.sushant.gallery.ui.components.pluralText
import com.sushant.gallery.ui.components.thumbModel
import com.sushant.gallery.ui.photos.SelectionBar
import com.sushant.gallery.ui.theme.LocalSpacing

@Composable
fun AlbumsScreen(vm: MainViewModel, nav: Nav, expanded: Boolean) {
    val albums by vm.albums.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val trash by vm.trash.collectAsStateWithLifecycle()
    val albumSort by vm.prefs.albumSort.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var sortSheet by rememberSaveable { mutableStateOf(false) }
    var picked by rememberSaveable { mutableStateOf<String?>(null) }
    val sp = LocalSpacing.current

    val pins: @Composable () -> Unit = {
        Row(Modifier.fillMaxWidth().padding(horizontal = sp.s, vertical = sp.xs), horizontalArrangement = Arrangement.SpaceAround) {
            PinButton(Icons.Rounded.Favorite, stringResource(R.string.favourites), { nav.album(Source.Favourites) }, modifier = Modifier.testTag("pin_fav"))
            PinButton(Icons.Rounded.SmartDisplay, stringResource(R.string.videos), { nav.album(Source.Videos) }, modifier = Modifier.testTag("pin_videos"))
            PinButton(Icons.Rounded.Delete, stringResource(R.string.recycle_bin), { nav.go("bin") }, badge = trash.size, modifier = Modifier.testTag("pin_bin"))
            PinButton(Icons.Rounded.Lock, stringResource(R.string.hidden), { nav.go("vault") }, modifier = Modifier.testTag("pin_hidden"))
        }
    }
    val sortLabel = stringResource(when (albumSort) { AlbumSort.NAME -> R.string.sort_name; AlbumSort.DATE -> R.string.sort_recent; AlbumSort.COUNT -> R.string.sort_count })
    val heading: @Composable () -> Unit = {
        SectionHeader(stringResource(R.string.albums_count, albums.size)) {
            TextButton(onClick = { sortSheet = true }, modifier = Modifier.testTag("album_sort")) {
                Icon(Icons.AutoMirrored.Rounded.Sort, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text(sortLabel)
            }
        }
    }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        AppTopBar(stringResource(R.string.tab_albums)) {
            IconButton(onClick = { nav.go("search") }) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("more")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort_albums)) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) }, onClick = { menu = false; sortSheet = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, leadingIcon = { Icon(Icons.Rounded.Settings, null) }, onClick = { menu = false; nav.go("settings") })
                }
            }
        }
        if (!loaded) { Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }; return@Column }
        if (expanded) {
            val sel = albums.firstOrNull { it.folder == picked } ?: albums.firstOrNull()
            Row(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.width(360.dp).fillMaxHeight()) {
                    item { pins() }
                    item { heading() }
                    items(albums, key = { it.folder }) { a -> AlbumRow(a, a == sel) { picked = a.folder } }
                }
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    if (sel != null) FolderGrid(vm, nav, Source.Folder(sel.folder), sel.name)
                    else EmptyState(Illus.PHOTOS, stringResource(R.string.empty_photos_title), stringResource(R.string.empty_photos_body))
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(150.dp), contentPadding = PaddingValues(start = sp.l, end = sp.l, bottom = sp.xl),
                horizontalArrangement = Arrangement.spacedBy(sp.m), verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize().testTag("albums"),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.padding(horizontal = 0.dp)) { pins() } }
                item(span = { GridItemSpan(maxLineSpan) }) { heading() }
                items(albums, key = { it.folder }) { a -> AlbumCard(a) { nav.album(Source.Folder(a.folder)) } }
            }
        }
    }
    if (sortSheet) AlbumSortSheet(albumSort, onPick = { vm.prefs.setAlbumSort(it); sortSheet = false }, onDismiss = { sortSheet = false })
}

@Composable
private fun AlbumCard(a: Album, onClick: () -> Unit) {
    val count = pluralText(R.plurals.n_items, a.count)
    Column(Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "${a.name}, $count" }.testTag("album_card")) {
        Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            a.cover?.let { AsyncImage(thumbModel(it), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 8.dp))
        Text(Formats.count(a.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AlbumRow(a: Album, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp).clip(RoundedCornerShape(16.dp))
            .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background)
            .clickable(onClick = onClick).padding(8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceContainer)) {
            a.cover?.let { AsyncImage(thumbModel(it), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
        }
        Column(Modifier.weight(1f)) {
            Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(pluralText(R.plurals.n_items, a.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Grid of one album (folder, Favourites or Videos) with selection. */
@Composable
fun FolderGrid(vm: MainViewModel, nav: Nav, source: Source, title: String, onBack: (() -> Unit)? = null) {
    val items by vm.items.collectAsStateWithLifecycle()
    val favs by vm.favourites.collectAsStateWithLifecycle()
    val sort by vm.prefs.photoSort.collectAsStateWithLifecycle()
    val cols by vm.prefs.gridColumns.collectAsStateWithLifecycle()
    val selList by vm.selection.collectAsStateWithLifecycle()
    val sel = remember(selList) { selList.toSet() }
    val list = remember(items, favs, source) { vm.repo.listFor(source, items, favs, emptyList(), null) }
    val groups = remember(list, sort) { Sorting.groups(list, sort).map { GridGroup(it.first, it.second) } }
    var menu by remember { mutableStateOf(false) }
    var sortSheet by rememberSaveable { mutableStateOf(false) }
    BackHandler(sel.isNotEmpty()) { vm.clearSelection() }
    Column(Modifier.fillMaxSize()) {
        if (sel.isNotEmpty()) SelectionBar(vm, list.filter { it.key in sel }, onSelectAll = { vm.select(list.map { it.key }, true) })
        else AppTopBar(title, subtitle = pluralText(R.plurals.n_items, list.size), onBack = onBack) {
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.testTag("more")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.sort)) }, onClick = { menu = false; sortSheet = true })
                    DropdownMenuItem(text = { Text(stringResource(R.string.select)) }, onClick = { menu = false; list.firstOrNull()?.let { vm.toggle(it.key) } })
                }
            }
        }
        if (list.isEmpty()) {
            EmptyState(
                Illus.PHOTOS, stringResource(R.string.empty_album_title),
                stringResource(if (source == Source.Favourites) R.string.empty_fav_body else R.string.empty_album_body),
            )
        } else {
            MediaGrid(
                groups = groups, columns = cols, selection = sel, favourites = favs,
                onOpen = { m -> nav.viewer(source, list.indexOf(m).coerceAtLeast(0)) },
                onSelect = { k, on -> vm.select(k, on) },
                onColumns = { vm.prefs.setColumns(it) },
            )
        }
    }
    if (sortSheet) SortSheet(sort, vm.prefs.sortDesc.value, onPick = { s, d -> vm.prefs.setPhotoSort(s, d) }, onDismiss = { sortSheet = false })
}

@Composable
fun AlbumScreen(vm: MainViewModel, nav: Nav, source: Source) {
    val title = when (source) {
        Source.Favourites -> stringResource(R.string.favourites)
        Source.Videos -> stringResource(R.string.videos)
        is Source.Folder -> Formats.folderName(source.folder)
        else -> stringResource(R.string.tab_photos)
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        FolderGrid(vm, nav, source, title, onBack = { vm.clearSelection(); nav.back() })
    }
    Spacer(Modifier.height(0.dp))
}
