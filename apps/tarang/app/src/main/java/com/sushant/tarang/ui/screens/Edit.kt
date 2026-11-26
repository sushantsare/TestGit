@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.screens

import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.*
import com.sushant.tarang.ui.LocalDockPadding
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Edit details: written into the song file itself (Android asks "Allow" first). */
@Composable
fun EditDetailsScreen(songId: Long) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val lib by vm.library.collectAsState()
    val song = lib.byId[songId]
    if (song == null) { Column { InnerTopBar("Edit details", nav::back); EmptyState(Icons.Rounded.MusicOff, "Song not found", "It may have been deleted.") }; return }
    var title by rememberSaveable { mutableStateOf(song.title) }
    var artist by rememberSaveable { mutableStateOf(song.artist.takeIf { it != UNKNOWN_ARTIST } ?: "") }
    var album by rememberSaveable { mutableStateOf(song.album.takeIf { it != UNKNOWN_ALBUM } ?: "") }
    var albumArtist by rememberSaveable { mutableStateOf(song.albumArtist.takeIf { it != UNKNOWN_ARTIST } ?: "") }
    var year by rememberSaveable { mutableStateOf(song.year.takeIf { it > 0 }?.toString() ?: "") }
    var genre by rememberSaveable { mutableStateOf<String?>(null) }
    var genreOrig by rememberSaveable { mutableStateOf<String?>(null) }
    var cover by rememberSaveable { mutableStateOf<String?>(null) }
    var leave by remember { mutableStateOf(false) }
    LaunchedEffect(song.id) {
        if (genreOrig == null) genreOrig = withContext(Dispatchers.IO) {
            runCatching { val r = MediaMetadataRetriever(); try { r.setDataSource(ctx, song.uri); r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE) } finally { r.release() } }.getOrNull() ?: ""
        }
        if (genre == null) genre = genreOrig
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { u -> if (u != null) cover = u.toString() }
    val picked by produceState<android.graphics.Bitmap?>(null, cover) {
        value = cover?.let { c -> withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openInputStream(Uri.parse(c))?.use { android.graphics.BitmapFactory.decodeStream(it) } }.getOrNull() } }
    }
    val changed = title != song.title || artist != (song.artist.takeIf { it != UNKNOWN_ARTIST } ?: "") || album != (song.album.takeIf { it != UNKNOWN_ALBUM } ?: "") ||
        albumArtist != (song.albumArtist.takeIf { it != UNKNOWN_ARTIST } ?: "") || year != (song.year.takeIf { it > 0 }?.toString() ?: "") || cover != null || (genre != null && genre != genreOrig)
    BackHandler(changed) { leave = true }
    val yearOk = year.isEmpty() || (year.length == 4 && year.all { it.isDigit() })

    Column(Modifier.fillMaxSize().imePadding().testTag("edit_details")) {
        InnerTopBar("Edit details", { if (changed) leave = true else nav.back() }) {
            TextButton(onClick = {
                vm.requestEdit(song, TagEdit(title.ifBlank { song.title }, artist, album, albumArtist, year, genre ?: "", cover?.let(Uri::parse))); nav.back()
            }, enabled = changed && genreOrig != null && title.isNotBlank() && yearOk, modifier = Modifier.testTag("edit_save")) { Text("Save") }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = LocalDockPadding.current)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(120.dp).clip(RoundedCornerShape(24.dp)).clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }.testTag("edit_cover")) {
                    val b = picked
                    if (b != null) Image(b.asImageBitmap(), "New cover", Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Cover(song, 120.dp, RoundedCornerShape(24.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                        Icon(Icons.Rounded.Image, null); Spacer(Modifier.width(8.dp)); Text("Change cover")
                    }
                    if (cover != null) TextButton(onClick = { cover = null }) { Text("Keep old cover") }
                }
            }
            Spacer(Modifier.height(16.dp))
            OutlinedTextField(title, { title = it }, Modifier.fillMaxWidth().testTag("edit_title"), label = { Text("Title") }, singleLine = true, isError = title.isBlank())
            OutlinedTextField(artist, { artist = it }, Modifier.fillMaxWidth().padding(top = 8.dp).testTag("edit_artist"), label = { Text("Artist") }, singleLine = true,
                supportingText = { Text("Separate several artists with commas") })
            OutlinedTextField(album, { album = it }, Modifier.fillMaxWidth().padding(top = 4.dp), label = { Text("Album") }, singleLine = true)
            OutlinedTextField(albumArtist, { albumArtist = it }, Modifier.fillMaxWidth().padding(top = 8.dp), label = { Text("Album artist") }, singleLine = true)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(year, { if (it.length <= 4) year = it.filter { c -> c.isDigit() } }, Modifier.weight(1f), label = { Text("Year") }, singleLine = true,
                    isError = !yearOk, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(genre ?: "", { genre = it }, Modifier.weight(1f), label = { Text("Genre") }, singleLine = true)
            }
            Text("Changes are saved into the song file, so other music apps see them too. Android asks for your permission first.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
        }
    }
    if (leave) ConfirmDialog("Discard changes?", "Your edits to this song haven't been saved.", "Discard", { leave = false }, { leave = false; nav.back() })
}

/** Type, paste or fix lyrics for one song (timed lines like [01:23.45] make them scroll with the song). */
@Composable
fun LyricsEditScreen(songId: Long) {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val lib by vm.library.collectAsState()
    val song = lib.byId[songId]
    if (song == null) { Column { InnerTopBar("Lyrics", nav::back); EmptyState(Icons.Rounded.MusicOff, "Song not found", "It may have been deleted.") }; return }
    var original by remember { mutableStateOf<String?>(null) }
    var text by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(song.id) {
        val found = withContext(Dispatchers.IO) { vm.repo.lyrics.find(song) }
        original = found?.raw ?: ""
        if (text == null) text = original
    }
    var leave by remember { mutableStateOf(false) }
    var remove by remember { mutableStateOf(false) }
    val changed = text != null && text != original
    BackHandler(changed) { leave = true }
    Column(Modifier.fillMaxSize().imePadding().testTag("lyrics_edit")) {
        InnerTopBar("Lyrics", { if (changed) leave = true else nav.back() }) {
            if (!original.isNullOrBlank()) TopActions(TopAction(Icons.Rounded.DeleteOutline, "Remove lyrics", "lyrics_remove") { remove = true })
            TextButton(onClick = { vm.saveLyrics(song.id, text.orEmpty(), "Typed in Tarang"); nav.back() }, enabled = changed, modifier = Modifier.testTag("lyrics_save")) { Text("Save") }
        }
        Text(song.title + " · " + song.artist, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
        Text("Plain text is fine. Lines starting with a time like [01:23.45] scroll along with the song.", style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp))
        if (text == null) Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        else OutlinedTextField(text!!, { text = it }, Modifier.fillMaxWidth().weight(1f).padding(horizontal = 16.dp).padding(bottom = LocalDockPadding.current).testTag("lyrics_field"),
            placeholder = { Text("Type or paste lyrics here") }, textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = if (text!!.trimStart().startsWith("[")) FontFamily.Monospace else MaterialTheme.typography.bodyLarge.fontFamily))
    }
    if (leave) ConfirmDialog("Discard changes?", "Your lyrics haven't been saved.", "Discard", { leave = false }, { leave = false; nav.back() })
    if (remove) ConfirmDialog("Remove lyrics?", "Lyrics you typed or imported for this song are removed. Lyrics inside the song file or in .lrc files stay.", "Remove", { remove = false }, {
        remove = false; vm.saveLyrics(song.id, "", "Typed in Tarang"); nav.back()
    })
}
