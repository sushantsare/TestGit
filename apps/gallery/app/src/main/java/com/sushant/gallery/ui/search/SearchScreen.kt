package com.sushant.gallery.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sushant.gallery.R
import com.sushant.gallery.data.Source
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.EmptyState
import com.sushant.gallery.ui.components.GridGroup
import com.sushant.gallery.ui.components.MediaGrid
import com.sushant.gallery.ui.components.SectionHeader
import com.sushant.gallery.ui.components.pluralText
import com.sushant.gallery.ui.components.thumbModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchScreen(vm: MainViewModel, nav: Nav) {
    var query by rememberSaveable { mutableStateOf("") }
    var debounced by rememberSaveable { mutableStateOf("") }
    val items by vm.items.collectAsStateWithLifecycle()
    val albums by vm.albums.collectAsStateWithLifecycle()
    val favs by vm.favourites.collectAsStateWithLifecycle()
    val recent by vm.prefs.recentSearches.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    LaunchedEffect(query) { delay(250); debounced = query }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val q = debounced.trim()
    val albumHits = remember(albums, q) { if (q.isEmpty()) emptyList() else albums.filter { it.name.contains(q, true) } }
    val files = remember(items, q) { vm.repo.searchFiles(items, q) }

    val albumHeader: (@Composable () -> Unit)? = if (albumHits.isEmpty()) null else {
        {
                    Column {
                        SectionHeader(stringResource(R.string.tab_albums))
                        albumHits.forEach { a ->
                            Row(
                                Modifier.fillMaxWidth().clickable { vm.prefs.addRecent(q); nav.album(Source.Folder(a.folder)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
                                    a.cover?.let { AsyncImage(thumbModel(it), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                                }
                                Column {
                                    Text(highlight(a.name, q), style = MaterialTheme.typography.titleSmall)
                                    Text(pluralText(R.plurals.n_items, a.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
        }
    }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars).imePadding()) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { nav.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back)) }
            Box(Modifier.weight(1f)) {
                if (query.isEmpty()) Text(stringResource(R.string.search_hint), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vm.prefs.addRecent(query); kb?.hide() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search_field"),
                )
            }
            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, stringResource(R.string.clear)) }
        }
        when {
            q.isEmpty() -> Column {
                if (recent.isNotEmpty()) {
                    SectionHeader(stringResource(R.string.recent))
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        recent.forEach { r ->
                            InputChip(
                                selected = false, onClick = { query = r; debounced = r }, label = { Text(r) },
                                leadingIcon = { Icon(Icons.Rounded.History, null, Modifier.size(18.dp)) },
                                trailingIcon = { Icon(Icons.Rounded.Close, stringResource(R.string.remove), Modifier.size(18.dp).clickable { vm.prefs.removeRecent(r) }) },
                            )
                        }
                    }
                }
            }
            albumHits.isEmpty() && files.isEmpty() -> EmptyState(null, stringResource(R.string.no_results, q), stringResource(R.string.no_results_body))
            else -> MediaGrid(
                groups = listOf(GridGroup(if (files.isEmpty()) "" else pluralText(R.plurals.files_n, files.size), files)).filter { it.items.isNotEmpty() },
                columns = 4, selection = emptySet(), favourites = favs,
                onOpen = { m -> vm.prefs.addRecent(q); nav.viewer(Source.Search(q), files.indexOf(m).coerceAtLeast(0)) },
                onSelect = { _, _ -> },
                header = albumHeader,
            )
        }
    }
}

@Composable
private fun highlight(text: String, q: String) = buildAnnotatedString {
    val i = text.indexOf(q, ignoreCase = true)
    if (i < 0 || q.isEmpty()) { append(text); return@buildAnnotatedString }
    append(text.substring(0, i))
    withStyle(SpanStyle(background = MaterialTheme.colorScheme.primaryContainer, color = MaterialTheme.colorScheme.onPrimaryContainer)) { append(text.substring(i, i + q.length)) }
    append(text.substring(i + q.length))
}
