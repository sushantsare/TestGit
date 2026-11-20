package com.sushant.inknote.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sushant.inknote.R
import com.sushant.inknote.data.NoteItem
import com.sushant.inknote.data.SearchHit
import com.sushant.inknote.data.Source
import com.sushant.inknote.ui.NoteThumb
import com.sushant.inknote.ui.app
import com.sushant.inknote.ui.theme.LocalInk
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map

data class SearchRow(val item: NoteItem, val hit: SearchHit)

@Composable
fun SearchScreen(notes: List<NoteItem>, onBack: () -> Unit, onOpen: (Long, Int) -> Unit, expanded: Boolean) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableStateOf("ALL") }
    var hits by remember { mutableStateOf<List<SearchRow>>(emptyList()) }
    var searched by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val kb = LocalSoftwareKeyboardController.current
    val indexing by remember { WorkManager.getInstance(ctx).getWorkInfosForUniqueWorkFlow("index").map { l -> l.any { it.state == WorkInfo.State.RUNNING } } }.collectAsState(false)
    LaunchedEffect(Unit) { delay(150); runCatching { focus.requestFocus() } }
    LaunchedEffect(q, notes) {
        delay(220)
        if (q.isBlank()) { hits = emptyList(); searched = false; return@LaunchedEffect }
        val byId = notes.associateBy { it.note.id }
        val raw = ctx.app().repo.search(q)
        val titleHits = notes.filter { it.note.title.contains(q.trim(), true) || it.tags.any { t -> t.contains(q.trim().removePrefix("#"), true) } }
            .map { SearchRow(it, SearchHit(it.note.id, -1, 0, Source.TITLE, it.note.title)) }
        hits = (titleHits + raw.mapNotNull { h -> byId[h.noteId]?.let { SearchRow(it, h) } }).distinctBy { it.hit.noteId to it.hit.pageId to it.hit.source }
        searched = true
    }
    val shown = hits.filter { filter == "ALL" || it.hit.source == filter }
    Column(Modifier.fillMaxSize().background(ink.bg).then(if (expanded) Modifier else Modifier.statusBarsPadding()).testTag("search_screen")) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            TextField(q, { q = it }, placeholder = { Text(stringResource(R.string.search_hint)) }, singleLine = true, shape = RoundedCornerShape(24.dp),
                colors = TextFieldDefaults.colors(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant, unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant),
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Close, stringResource(R.string.clear)) } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { kb?.hide() }),
                modifier = Modifier.weight(1f).padding(end = 8.dp).focusRequester(focus).testTag("search_field"))
        }
        val chips = listOf("ALL" to R.string.f_all, Source.TYPED to R.string.f_typed, Source.INK to R.string.f_handwriting, Source.PDF to R.string.f_pdfs, Source.PHOTO to R.string.f_photos, Source.TITLE to R.string.f_titles)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(chips) { (k, l) -> FilterChip(filter == k, { filter = k }, { Text(stringResource(l) + if (k == "ALL" && searched) " ${hits.size}" else "") }) }
        }
        if (indexing) {
            LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp))
            Text(stringResource(R.string.indexing), style = MaterialTheme.typography.bodySmall, color = ink.muted, modifier = Modifier.padding(horizontal = 16.dp))
        }
        when {
            !searched -> Text(stringResource(R.string.search_help), color = ink.muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp))
            shown.isEmpty() -> Text(stringResource(R.string.no_matches), color = ink.muted, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(24.dp).testTag("no_matches"))
            else -> LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                item { Text(stringResource(R.string.results_count, shown.size, shown.map { it.hit.noteId }.distinct().size), color = ink.muted, style = MaterialTheme.typography.bodyMedium) }
                items(shown, key = { "${it.hit.noteId}_${it.hit.pageId}_${it.hit.source}" }) { r -> ResultCard(r, q) { onOpen(r.hit.noteId, r.hit.pageIdx) } }
            }
        }
    }
}

@Composable
private fun ResultCard(r: SearchRow, q: String, onClick: () -> Unit) {
    val ink = LocalInk.current
    val (icon, label) = when (r.hit.source) {
        Source.PDF -> Icons.Outlined.PictureAsPdf to stringResource(R.string.in_pdf, r.hit.pageIdx + 1)
        Source.INK -> Icons.Outlined.Draw to stringResource(R.string.in_hw, r.hit.pageIdx + 1)
        Source.PHOTO -> Icons.Outlined.CameraAlt to stringResource(R.string.in_photo)
        Source.TYPED -> Icons.Outlined.TextFields to stringResource(R.string.in_typed)
        else -> Icons.Outlined.Title to stringResource(R.string.in_title)
    }
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = ink.card, shadowElevation = 1.dp, modifier = Modifier.fillMaxWidth().testTag("result")) {
        Row(Modifier.padding(12.dp)) {
            NoteThumb(r.item.note, Modifier.size(width = 62.dp, height = 76.dp))
            Column(Modifier.weight(1f).padding(start = 12.dp)) {
                Text(r.item.note.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(snippet(r.hit.text, q, MaterialTheme.colorScheme.onSurface, ink.highlight), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 3.dp))
                Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) { SourceIcon(icon); Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp)) }
            }
        }
    }
}

@Composable private fun SourceIcon(i: ImageVector) = Icon(i, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(14.dp))

fun snippet(text: String, q: String, fg: Color, hl: Color): AnnotatedString {
    val words = q.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val flat = text.replace('\n', ' ')
    val first = words.mapNotNull { w -> flat.indexOf(w, ignoreCase = true).takeIf { it >= 0 } }.minOrNull() ?: 0
    val start = (first - 30).coerceAtLeast(0)
    val s = (if (start > 0) "…" else "") + flat.substring(start).take(160)
    return buildAnnotatedString {
        append(s)
        for (w in words) { var i = s.indexOf(w, ignoreCase = true); while (i >= 0) { addStyle(SpanStyle(background = hl, color = fg), i, i + w.length); i = s.indexOf(w, i + w.length, ignoreCase = true) } }
    }
}
