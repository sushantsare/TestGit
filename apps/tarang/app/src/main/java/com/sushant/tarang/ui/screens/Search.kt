@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.plural
import com.sushant.tarang.ui.LocalDockPadding
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.R0
import com.sushant.tarang.ui.components.*

@Composable
fun SearchScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val ui by vm.player.ui.collectAsState()
    val recent by vm.recentSearches.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    val res = remember(q, lib) { vm.repo.search(q, lib) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focus.requestFocus() }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxSize().statusBarsPadding().testTag("search")) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            TopActions(TopAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", "back", nav::back))
            TextField(q, { q = it }, Modifier.weight(1f).focusRequester(focus).testTag("search_field"),
                placeholder = { Text("Songs, artists, albums, folders") }, singleLine = true, shape = RoundedCornerShape(28.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = cs.surfaceContainerHigh.copy(alpha = 0f), unfocusedIndicatorColor = cs.surfaceContainerHigh.copy(alpha = 0f)),
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Clear search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.rememberSearch(q); kb?.hide() }))
        }
        LazyColumn(Modifier.fillMaxSize().testTag("search_list"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
            if (q.isBlank()) {
                if (recent.isNotEmpty()) {
                    item { SectionLabel("Recent searches") }
                    items(recent) { r ->
                        ListItemRow(r, icon = Icons.Rounded.History, onClick = { q = r }, trailing = {
                            IconButton(onClick = { vm.forgetSearch(r) }) { Icon(Icons.Rounded.Close, "Remove $r from recent searches") }
                        })
                    }
                } else item { EmptyState(Icons.Rounded.Search, "Search your music", "Find any song, artist, album or folder on your phone.") }
                return@LazyColumn
            }
            if (res.empty) { item { EmptyState(Icons.Rounded.SearchOff, "Nothing found", "No songs, artists, albums or folders match “$q”.") }; return@LazyColumn }
            if (res.songs.isNotEmpty()) {
                item { SectionLabel("Songs") }
                itemsIndexed(res.songs, key = { _, it -> "s${it.id}" }) { i, s ->
                    SongRow(s, index = i, count = res.songs.size, onClick = { vm.rememberSearch(q); vm.player.tapSong(s, res.songs, "Search", "“$q”") }, onMore = { nav.songMenu(s) })
                }
            }
            if (res.artists.isNotEmpty()) {
                item { SectionLabel("Artists") }
                items(res.artists, key = { "a${it.name}" }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { vm.rememberSearch(q); nav.go(R0.artist(a.name)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        LetterAvatar(a.name, 52.dp); Spacer(Modifier.width(14.dp))
                        Column { Text(a.name, style = MaterialTheme.typography.bodyLarge); Text(plural(a.songs.size, "song"), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                    }
                }
            }
            if (res.albums.isNotEmpty()) {
                item { SectionLabel("Albums") }
                items(res.albums, key = { "al${it.id}" }) { a ->
                    Row(Modifier.fillMaxWidth().clickable { vm.rememberSearch(q); nav.go(R0.album(a.id)) }.padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Cover(a.songs.first(), 52.dp, RoundedCornerShape(14.dp)); Spacer(Modifier.width(14.dp))
                        Column { Text(a.name, style = MaterialTheme.typography.bodyLarge); Text(a.artist, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant) }
                    }
                }
            }
            if (res.folders.isNotEmpty()) {
                item { SectionLabel("Folders") }
                items(res.folders, key = { "f${it.path}" }) { f ->
                    ListItemRow(f.name, plural(f.songs.size, "song"), Icons.Rounded.Folder, iconTone = 1, onClick = { vm.rememberSearch(q); nav.go(R0.folder(f.path)) })
                }
            }
        }
    }
}
