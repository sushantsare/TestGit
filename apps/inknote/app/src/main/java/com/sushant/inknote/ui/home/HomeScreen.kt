package com.sushant.inknote.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.Menu
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.inknote.R
import com.sushant.inknote.data.NoteItem
import com.sushant.inknote.data.SortBy
import com.sushant.inknote.ui.Badge
import com.sushant.inknote.ui.Dates
import com.sushant.inknote.ui.Filter
import com.sushant.inknote.ui.HomeState
import com.sushant.inknote.ui.NoteThumb
import com.sushant.inknote.ui.theme.LocalInk

data class HomeActions(
    val openNote: (Long) -> Unit,
    val openDrawer: () -> Unit,
    val newNote: () -> Unit,
    val search: () -> Unit,
    val settings: () -> Unit,
    val importPdf: () -> Unit,
    val scan: () -> Unit,
    val setFilter: (Filter) -> Unit,
    val toggleSelect: (Long) -> Unit,
    val clearSelection: () -> Unit,
    val pin: (Set<Long>) -> Unit,
    val move: (Set<Long>) -> Unit,
    val lock: (Set<Long>, Boolean) -> Unit,
    val delete: (Set<Long>) -> Unit,
    val setGrid: (Boolean) -> Unit,
    val setSort: (Int) -> Unit,
)

fun filterTitle(f: Filter, s: HomeState, all: String, pinned: String, fav: String, locked: String) = when (f) {
    Filter.All -> all; Filter.Pinned -> pinned; Filter.Favourites -> fav; Filter.Locked -> locked
    is Filter.Folder -> s.folders.firstOrNull { it.id == f.id }?.name ?: all
    is Filter.Tag -> "#${f.name}"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(s: HomeState, selected: Set<Long>, grid: Boolean, sortBy: Int, expanded: Boolean, a: HomeActions) {
    val ink = LocalInk.current
    var menu by rememberSaveable { mutableStateOf(false) }
    var sortMenu by rememberSaveable { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    Box(Modifier.fillMaxSize().background(ink.bg).testTag("home")) {
        Column(Modifier.fillMaxSize().then(if (expanded) Modifier else Modifier.statusBarsPadding())) {
            if (selecting) {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = a.clearSelection) { Icon(Icons.Outlined.Close, stringResource(R.string.cancel_selection)) }
                    Text(androidx.compose.ui.res.pluralStringResource(R.plurals.n_selected, selected.size, selected.size), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = { a.pin(selected) }) { Icon(Icons.Outlined.PushPin, stringResource(R.string.pin)) }
                    IconButton(onClick = { a.move(selected) }) { Icon(Icons.Outlined.DriveFileMove, stringResource(R.string.move_to_folder)) }
                    val allLocked = selected.all { id -> s.all.firstOrNull { it.note.id == id }?.note?.locked == true }
                    IconButton(onClick = { a.lock(selected, !allLocked) }) { Icon(if (allLocked) Icons.Outlined.LockOpen else Icons.Outlined.Lock, stringResource(if (allLocked) R.string.unlock else R.string.lock)) }
                    IconButton(onClick = { a.delete(selected) }, Modifier.testTag("sel_delete")) { Icon(Icons.Outlined.Delete, stringResource(R.string.move_to_trash)) }
                }
            } else {
                Row(Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (!expanded) IconButton(onClick = a.openDrawer, Modifier.testTag("open_drawer")) { Icon(Icons.Outlined.Menu, stringResource(R.string.open_menu)) }
                    else Spacer(Modifier.width(12.dp))
                    Text(filterTitle(s.filter, s, stringResource(R.string.all_notes), stringResource(R.string.pinned), stringResource(R.string.favourites), stringResource(R.string.locked_notes)),
                        style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 4.dp))
                    IconButton(onClick = { a.setGrid(!grid) }, Modifier.testTag("view_toggle")) {
                        Icon(if (grid) Icons.AutoMirrored.Outlined.ViewList else Icons.Outlined.GridView, stringResource(if (grid) R.string.show_list else R.string.show_grid))
                    }
                    Box {
                        IconButton(onClick = { menu = true }, Modifier.testTag("home_more")) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text(stringResource(R.string.select_notes)) }, { menu = false; s.shown.firstOrNull()?.let { a.toggleSelect(it.note.id) } }, leadingIcon = { Icon(Icons.Outlined.TaskAlt, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.sort_by)) }, { menu = false; sortMenu = true }, leadingIcon = { Icon(Icons.Outlined.Sort, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.import_pdf)) }, { menu = false; a.importPdf() }, leadingIcon = { Icon(Icons.Outlined.PictureAsPdf, null) })
                            DropdownMenuItem({ Text(stringResource(R.string.scan_document)) }, { menu = false; a.scan() }, leadingIcon = { Icon(Icons.Outlined.DocumentScanner, null) })
                            if (!expanded) DropdownMenuItem({ Text(stringResource(R.string.settings)) }, { menu = false; a.settings() }, leadingIcon = { Icon(Icons.Outlined.Settings, null) })
                        }
                        DropdownMenu(sortMenu, { sortMenu = false }) {
                            listOf(SortBy.MODIFIED to R.string.sort_modified, SortBy.CREATED to R.string.sort_created, SortBy.TITLE to R.string.sort_title).forEach { (v, l) ->
                                DropdownMenuItem({ Text(stringResource(l)) }, { sortMenu = false; a.setSort(v) },
                                    trailingIcon = { if (sortBy == v) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary) })
                            }
                        }
                    }
                }
            }
            if (s.loaded && s.all.isEmpty()) { EmptyHome(a); return@Column }
            BoxWithConstraints(Modifier.weight(1f)) {
                val cols = when { maxWidth < 600.dp -> 2; maxWidth < 840.dp -> 3; else -> 4 }
                val bottomPad = PaddingValues(bottom = 110.dp)
                if (grid) {
                    LazyVerticalGrid(GridCells.Fixed(cols), Modifier.fillMaxSize().testTag("note_grid"), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 120.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(span = { GridItemSpan(maxLineSpan) }) { Chips(s, a) }
                        if (s.shown.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) { EmptyFilter() }
                        items(s.shown, key = { it.note.id }) { NoteCard(it, it.note.id in selected, selecting, a) }
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().testTag("note_list"), contentPadding = bottomPad) {
                        item { Chips(s, a) }
                        if (s.shown.isEmpty()) item { EmptyFilter() }
                        items(s.shown, key = { it.note.id }) { NoteRow(it, it.note.id in selected, selecting, a) }
                    }
                }
            }
        }
        if (!selecting && !(s.loaded && s.all.isEmpty())) {
            Surface(onClick = a.search, shape = RoundedCornerShape(16.dp), color = ink.card, shadowElevation = 6.dp,
                modifier = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, end = 92.dp, bottom = 24.dp).fillMaxWidth().height(56.dp).testTag("search_pill")) {
                Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = ink.muted)
                    Text(stringResource(R.string.search_hint), color = ink.muted, modifier = Modifier.padding(start = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            Fab(a.newNote, Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(end = 16.dp, bottom = 22.dp))
        }
    }
}

@Composable
fun Fab(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary, shadowElevation = 6.dp,
        modifier = modifier.size(60.dp).testTag("fab_new").semantics { contentDescription = "New note" }) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(30.dp)) }
    }
}

@Composable
private fun Chips(s: HomeState, a: HomeActions) {
    val chips = buildList {
        add(Filter.All to stringResource(R.string.all_n, s.all.size)); add(Filter.Pinned to stringResource(R.string.pinned))
        s.folders.forEach { add(Filter.Folder(it.id) to it.name) }
    }
    LazyRow(Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(chips, key = { Filter.encode(it.first) }) { (f, label) ->
            FilterChip(selected = s.filter == f, onClick = { a.setFilter(if (s.filter == f && f != Filter.All) Filter.All else f) }, label = { Text(label) },
                shape = RoundedCornerShape(12.dp), border = null,
                colors = FilterChipDefaults.filterChipColors(containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    selectedContainerColor = MaterialTheme.colorScheme.primary, selectedLabelColor = MaterialTheme.colorScheme.onPrimary))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteCard(item: NoteItem, sel: Boolean, selecting: Boolean, a: HomeActions) {
    val n = item.note; val ink = LocalInk.current
    val desc = buildString {
        append(n.title); append(", "); append(Dates.short(n.updatedAt))
        if (item.tags.isNotEmpty()) append(", tags ").append(item.tags.joinToString())
        if (n.pinned) append(", pinned"); if (n.locked) append(", locked"); if (n.hasRecording) append(", has recording")
    }
    Surface(shape = RoundedCornerShape(16.dp), color = ink.card, shadowElevation = 1.dp,
        border = if (sel) androidx.compose.foundation.BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else null,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClickLabel = "Open", onLongClickLabel = "Select", onLongClick = { a.toggleSelect(n.id) },
                onClick = { if (selecting) a.toggleSelect(n.id) else a.openNote(n.id) })
            .semantics(mergeDescendants = true) { contentDescription = desc }.testTag("note_${n.title}")) {
        Column {
            Box(Modifier.padding(6.dp)) {
                NoteThumb(n, Modifier.fillMaxWidth().height(140.dp))
                if (n.pinned) Box(Modifier.align(Alignment.TopEnd).padding(6.dp).size(24.dp).shadow(1.dp, CircleShape).background(ink.card, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PushPin, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))
                }
                if (selecting) Box(Modifier.align(Alignment.TopStart).padding(6.dp).size(24.dp).background(if (sel) MaterialTheme.colorScheme.primary else ink.card, CircleShape), contentAlignment = Alignment.Center) {
                    if (sel) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
                }
                Row(Modifier.align(Alignment.BottomStart).padding(7.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    if (n.hasRecording && !n.locked) Badge("● ${stringResource(R.string.audio)}", MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.onPrimary)
                    Spacer(Modifier.weight(1f))
                    if (n.kind == com.sushant.inknote.data.Kind.PDF) Badge("PDF", ink.pdfBadge, Color.White, Modifier.padding(end = 12.dp))
                }
            }
            Column(Modifier.padding(start = 11.dp, end = 11.dp, bottom = 11.dp)) {
                Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.short(n.updatedAt) + if (n.pageCount > 1) " · ${n.pageCount} pages" else "", style = MaterialTheme.typography.bodySmall, color = ink.muted, maxLines = 1)
                    item.tags.firstOrNull()?.let { Text(" · #$it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NoteRow(item: NoteItem, sel: Boolean, selecting: Boolean, a: HomeActions) {
    val n = item.note; val ink = LocalInk.current
    Row(Modifier.fillMaxWidth().background(if (sel) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .combinedClickable(onLongClick = { a.toggleSelect(n.id) }, onClick = { if (selecting) a.toggleSelect(n.id) else a.openNote(n.id) })
        .heightIn(min = 72.dp).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        NoteThumb(n, Modifier.size(width = 56.dp, height = 68.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(n.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val sub = n.firstLine.ifBlank { if (n.kind == com.sushant.inknote.data.Kind.PDF) "PDF · ${n.pageCount} pages" else if (n.locked) stringResource(R.string.locked_note) else "${n.pageCount} page" + if (n.pageCount > 1) "s" else "" }
            Text(sub, style = MaterialTheme.typography.bodyMedium, color = ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Dates.short(n.updatedAt) + item.tags.joinToString("") { " · #$it" }, style = MaterialTheme.typography.bodySmall, color = ink.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (n.pinned) Icon(Icons.Filled.PushPin, stringResource(R.string.pinned), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun EmptyFilter() {
    Text(stringResource(R.string.empty_filter), color = LocalInk.current.muted, modifier = Modifier.fillMaxWidth().padding(32.dp), style = MaterialTheme.typography.bodyLarge)
}

@Composable
private fun EmptyHome(a: HomeActions) {
    Column(Modifier.fillMaxSize().padding(32.dp).testTag("empty_home"), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        EmptyArt()
        Text(stringResource(R.string.empty_title), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 18.dp, bottom = 8.dp))
        Text(stringResource(R.string.empty_body), color = LocalInk.current.muted, style = MaterialTheme.typography.bodyLarge, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(22.dp))
        androidx.compose.material3.Button(onClick = a.newNote, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(48.dp).testTag("empty_create")) {
            Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.create_note), Modifier.padding(start = 8.dp))
        }
        TextButton(onClick = a.importPdf, modifier = Modifier.padding(top = 6.dp).heightIn(min = 48.dp)) { Text(stringResource(R.string.import_a_pdf)) }
    }
}

@Composable
fun EmptyArt() {
    val p = MaterialTheme.colorScheme.primary; val ink = LocalInk.current
    androidx.compose.foundation.Canvas(Modifier.size(width = 150.dp, height = 120.dp)) {
        val u = size.width / 150f
        drawRoundRect(ink.card, topLeft = androidx.compose.ui.geometry.Offset(30 * u, 14 * u), size = androidx.compose.ui.geometry.Size(78 * u, 96 * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8 * u))
        drawRoundRect(ink.divider, topLeft = androidx.compose.ui.geometry.Offset(30 * u, 14 * u), size = androidx.compose.ui.geometry.Size(78 * u, 96 * u), cornerRadius = androidx.compose.ui.geometry.CornerRadius(8 * u), style = androidx.compose.ui.graphics.drawscope.Stroke(2 * u))
        for (y in listOf(36, 50, 64, 78, 92)) drawLine(ink.paperLine, androidx.compose.ui.geometry.Offset(42 * u, y * u), androidx.compose.ui.geometry.Offset(96 * u, y * u), 2 * u)
        drawLine(p, androidx.compose.ui.geometry.Offset(112 * u, 10 * u), androidx.compose.ui.geometry.Offset(84 * u, 62 * u), 12 * u, cap = androidx.compose.ui.graphics.StrokeCap.Round)
    }
}
