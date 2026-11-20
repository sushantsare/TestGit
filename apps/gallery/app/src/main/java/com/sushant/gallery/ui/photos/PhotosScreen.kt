package com.sushant.gallery.ui.photos

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gallery.R
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.Sorting
import com.sushant.gallery.data.Source
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.AppTopBar
import com.sushant.gallery.ui.components.EmptyState
import com.sushant.gallery.ui.components.GridGroup
import com.sushant.gallery.ui.components.Illus
import com.sushant.gallery.ui.components.MediaGrid
import com.sushant.gallery.ui.components.SelectionTopBar
import com.sushant.gallery.ui.components.SortSheet
import com.sushant.gallery.ui.components.pluralText
import java.time.LocalDate

@Composable
fun PhotosScreen(vm: MainViewModel, nav: Nav, wide: Boolean) {
    val items by vm.items.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val sort by vm.prefs.photoSort.collectAsStateWithLifecycle()
    val cols by vm.prefs.gridColumns.collectAsStateWithLifecycle()
    val favs by vm.favourites.collectAsStateWithLifecycle()
    val selList by vm.selection.collectAsStateWithLifecycle()
    val sel = remember(selList) { selList.toSet() }
    var menu by remember { mutableStateOf(false) }
    var sortSheet by rememberSaveable { mutableStateOf(false) }
    val today = remember { LocalDate.now() }
    val groups = remember(items, sort, today) { Sorting.groups(items, sort, today).map { GridGroup(it.first, it.second) } }

    BackHandler(sel.isNotEmpty()) { vm.clearSelection() }

    Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBars)) {
        if (sel.isNotEmpty()) {
            SelectionBar(vm, items.filter { it.key in sel }, onSelectAll = { vm.select(items.map { it.key }, true) })
        } else {
            AppTopBar(stringResource(R.string.tab_photos)) {
                IconButton(onClick = { nav.go("search") }, modifier = Modifier.testTag("search")) { Icon(Icons.Rounded.Search, stringResource(R.string.search)) }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.testTag("more")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { androidx.compose.material3.Text(stringResource(R.string.sort)) }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Sort, null) },
                            onClick = { menu = false; sortSheet = true }, modifier = Modifier.testTag("menu_sort"))
                        DropdownMenuItem(text = { androidx.compose.material3.Text(stringResource(R.string.grid_size)) }, leadingIcon = { Icon(Icons.Rounded.GridView, null) },
                            onClick = { menu = false; vm.prefs.setColumns(if (cols >= 5) 3 else cols + 1) })
                        DropdownMenuItem(text = { androidx.compose.material3.Text(stringResource(R.string.select)) }, leadingIcon = { Icon(Icons.Rounded.CheckBox, null) },
                            onClick = { menu = false; items.firstOrNull()?.let { vm.toggle(it.key) } }, modifier = Modifier.testTag("menu_select"))
                        DropdownMenuItem(text = { androidx.compose.material3.Text(stringResource(R.string.settings)) }, leadingIcon = { Icon(Icons.Rounded.Settings, null) },
                            onClick = { menu = false; nav.go("settings") }, modifier = Modifier.testTag("menu_settings"))
                    }
                }
            }
        }
        when {
            !loaded -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> EmptyState(Illus.PHOTOS, stringResource(R.string.empty_photos_title), stringResource(R.string.empty_photos_body))
            else -> MediaGrid(
                groups = groups, columns = if (wide) cols * 2 else cols, selection = sel, favourites = favs,
                onOpen = { m -> nav.viewer(Source.All, items.indexOf(m).coerceAtLeast(0)) },
                onSelect = { k, on -> vm.select(k, on) },
                onColumns = { c -> vm.prefs.setColumns(if (wide) c / 2 else c) },
            )
        }
    }
    if (sortSheet) SortSheet(sort, vm.prefs.sortDesc.value, onPick = { s, d -> vm.prefs.setPhotoSort(s, d) }, onDismiss = { sortSheet = false })
}

/** Contextual top bar for a multi-selection, with share, delete and more. Shared by grid screens. */
@Composable
fun SelectionBar(vm: MainViewModel, selected: List<MediaItem>, onSelectAll: () -> Unit) {
    val ctx = LocalContext.current
    var share by rememberSaveable { mutableStateOf(false) }
    var more by rememberSaveable { mutableStateOf(false) }
    var picker by rememberSaveable { mutableStateOf(0) } // 1 move, 2 copy
    val albums by vm.albums.collectAsStateWithLifecycle()
    SelectionTopBar(pluralText(R.plurals.n_selected, selected.size), onClose = { vm.clearSelection() }) {
        IconButton(onClick = { share = true }, modifier = Modifier.testTag("sel_share")) { Icon(Icons.Rounded.Share, stringResource(R.string.share)) }
        IconButton(onClick = { vm.moveToBin(selected); vm.clearSelection() }, modifier = Modifier.testTag("sel_delete")) {
            Icon(Icons.Rounded.Delete, stringResource(R.string.delete))
        }
        IconButton(onClick = { more = true }, modifier = Modifier.testTag("sel_more")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
    }
    if (share) com.sushant.gallery.ui.components.ShareSheet(
        selected.size, vm.prefs.stripLocation.value, hasPhotos = selected.any { !it.isVideo },
        onShare = { strip -> share = false; com.sushant.gallery.ui.components.shareItems(ctx, vm.repo.ops, selected, strip); vm.countShare(selected.size) },
        onDismiss = { share = false },
    )
    if (more) com.sushant.gallery.ui.components.SelectionMoreSheet(
        selected.size,
        onMove = { more = false; picker = 1 }, onCopy = { more = false; picker = 2 },
        onFavourite = { more = false; vm.setFavourite(selected, true); vm.clearSelection() },
        onHide = { more = false; vm.hide(selected); vm.clearSelection() },
        onSelectAll = { more = false; onSelectAll() },
        onDismiss = { more = false },
    )
    if (picker != 0) com.sushant.gallery.ui.components.AlbumPickerSheet(
        copy = picker == 2, albums = albums,
        onPick = { f -> vm.transfer(selected, f, picker == 2); picker = 0; vm.clearSelection() },
        onNew = { name -> vm.transfer(selected, vm.repo.ops.albumFolder(name), picker == 2); picker = 0; vm.clearSelection() },
        onDismiss = { picker = 0 },
    )
}
