@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.sheets

import android.content.Intent
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.Song
import com.sushant.tarang.data.plural
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.MainVm
import com.sushant.tarang.ui.R0
import com.sushant.tarang.ui.components.ConfirmDialog
import com.sushant.tarang.ui.components.Cover
import com.sushant.tarang.ui.components.TextInputDialog
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

sealed class Sheet {
    data class SongMenu(val song: Song, val playlistId: Long?) : Sheet()
    data class PlayerMenu(val song: Song) : Sheet()
    data object Sleep : Sheet()
    data object Speed : Sheet()
    data object Sound : Sheet()
    data class AddToPlaylist(val ids: List<Long>) : Sheet()
}

fun shareSong(ctx: android.content.Context, song: Song) {
    val send = Intent(Intent.ACTION_SEND).setType(song.mime.ifBlank { "audio/*" })
        .putExtra(Intent.EXTRA_STREAM, song.uri).putExtra(Intent.EXTRA_TITLE, song.title)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, "Share “${song.title}”"))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetHost(sheet: Sheet?, vm: MainVm, set: (Sheet?) -> Unit) {
    if (sheet == null) return
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val close: () -> Unit = { scope.launch { state.hide() }.invokeOnCompletion { set(null) } }
    ModalBottomSheet(onDismissRequest = { set(null) }, sheetState = state, modifier = Modifier.testTag("sheet")) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 12.dp)) {
            when (sheet) {
                is Sheet.SongMenu -> SongMenu(sheet.song, sheet.playlistId, vm, close, set, fromPlayer = false)
                is Sheet.PlayerMenu -> SongMenu(sheet.song, null, vm, close, set, fromPlayer = true)
                Sheet.Sleep -> SleepSheet(vm, close)
                Sheet.Speed -> SpeedSheet(vm)
                Sheet.Sound -> com.sushant.tarang.ui.screens.SoundSheetContent()
                is Sheet.AddToPlaylist -> AddToPlaylistSheet(sheet.ids, vm, close)
            }
        }
    }
}

@Composable
private fun MenuItem(icon: ImageVector, text: String, tag: String, danger: Boolean = false, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 24.dp, vertical = 14.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = if (danger) cs.error else cs.onSurfaceVariant)
        Spacer(Modifier.width(20.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = if (danger) cs.error else cs.onSurface)
    }
}

@Composable
private fun SongMenu(song: Song, playlistId: Long?, vm: MainVm, close: () -> Unit, set: (Sheet?) -> Unit, fromPlayer: Boolean) {
    val ctx = LocalContext.current
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val liked by vm.liked.collectAsState()
    val isLiked = song.id in liked
    var confirmDelete by remember { mutableStateOf(false) }
    var pickArtist by remember { mutableStateOf(false) }
    fun go(route: String) { close(); nav.goFromPlayer(route) }
    Row(Modifier.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Cover(song, 56.dp, RoundedCornerShape(14.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
    HorizontalDivider(Modifier.padding(vertical = 8.dp))
    if (!fromPlayer) {
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistPlay, "Play next", "menu_play_next") { vm.player.playNext(listOf(song)); vm.snack.tryEmit("Plays next"); close() }
        MenuItem(Icons.AutoMirrored.Rounded.QueueMusic, "Add to queue", "menu_add_queue") { vm.player.addToQueue(listOf(song)); vm.snack.tryEmit("Added to the queue"); close() }
        MenuItem(Icons.AutoMirrored.Rounded.PlaylistAdd, "Add to playlist", "menu_add_playlist") { set(Sheet.AddToPlaylist(listOf(song.id))) }
        MenuItem(if (isLiked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder, if (isLiked) "Remove from Liked" else "Like", "menu_like") { vm.toggleLike(song.id); close() }
    }
    MenuItem(Icons.Rounded.Album, "Go to album", "menu_album") { go(R0.album(song.albumId)) }
    MenuItem(Icons.Rounded.Person, if (song.artists.size > 1) "Go to artist…" else "Go to artist", "menu_artist") {
        if (song.artists.size > 1) pickArtist = true else go(R0.artist(song.artists.firstOrNull() ?: song.artist))
    }
    MenuItem(Icons.Rounded.Folder, "Go to folder", "menu_folder") { go(R0.folder(song.folderPath)) }
    if (!fromPlayer) MenuItem(Icons.Rounded.Edit, "Edit details", "menu_edit") { go(R0.edit(song.id)) }
    MenuItem(Icons.Rounded.Share, "Share", "menu_share") { close(); shareSong(ctx, song) }
    if (playlistId != null) MenuItem(Icons.Rounded.RemoveCircleOutline, "Remove from this playlist", "menu_remove_pl") { vm.removeFromPlaylist(playlistId, song.id); close() }
    if (fromPlayer) MenuItem(Icons.Rounded.StopCircle, "End the queue", "menu_end_queue") {
        vm.player.endQueue(); close(); nav.collapsePlayer()
    }
    MenuItem(Icons.Rounded.DeleteOutline, "Delete from phone", "menu_delete", danger = true) { confirmDelete = true }

    if (confirmDelete) ConfirmDialog("Delete “${song.title}”?", "The song file is removed from your phone for good. This can't be undone.", "Delete",
        onDismiss = { confirmDelete = false }, onConfirm = { confirmDelete = false; close(); vm.requestDelete(song) }, icon = Icons.Rounded.DeleteOutline)
    if (pickArtist) AlertDialog(onDismissRequest = { pickArtist = false }, title = { Text("Which artist?") }, text = {
        Column { song.artists.forEach { a -> Text(a, Modifier.fillMaxWidth().clickable { pickArtist = false; go(R0.artist(a)) }.padding(vertical = 12.dp), style = MaterialTheme.typography.bodyLarge) } }
    }, confirmButton = { TextButton(onClick = { pickArtist = false }) { Text("Cancel") } })
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun SleepSheet(vm: MainVm, close: () -> Unit) {
    val endsAt by vm.hub.sleepEndsAt.collectAsState()
    val afterSong by vm.hub.sleepAfterSong.collectAsState()
    var fade by remember { mutableStateOf(vm.hub.sleepFade.value) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { kotlinx.coroutines.delay(1000); now = System.currentTimeMillis() } }
    val active = endsAt > now || afterSong
    Text("Sleep timer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Text(
        when { afterSong -> "Music stops when this song ends"; endsAt > now -> "Music stops in ${((endsAt - now) / 60000) + 1} min"; else -> "Stop the music after a while" },
        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 24.dp))
    Spacer(Modifier.height(12.dp))
    FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(15, 30, 45, 60, 90).forEach { m ->
            AssistChip(onClick = { vm.player.sleep(m, false, fade); close() }, label = { Text("$m min") }, modifier = Modifier.testTag("sleep_$m"))
        }
        AssistChip(onClick = { vm.player.sleep(0, true, fade); close() }, label = { Text("End of song") }, modifier = Modifier.testTag("sleep_end"))
    }
    Row(Modifier.fillMaxWidth().clickable { fade = !fade }.padding(horizontal = 24.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Fade out", style = MaterialTheme.typography.bodyLarge)
            Text("Lower the volume slowly over the last minute", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(fade, { fade = it })
    }
    if (active) TextButton(onClick = { vm.player.sleep(0, false, fade); close() }, modifier = Modifier.padding(horizontal = 12.dp).testTag("sleep_off")) {
        Icon(Icons.Rounded.TimerOff, null); Spacer(Modifier.width(8.dp)); Text("Turn off timer")
    }
}

@Composable
private fun SpeedSheet(vm: MainVm) {
    val s by vm.settings.collectAsState()
    var speed by remember { mutableFloatStateOf(s.speed) }
    fun apply(v: Float, pitch: Boolean = s.keepPitch) {
        speed = v; vm.player.setSpeed(v, pitch); vm.setSettings { it.copy(speed = v, keepPitch = pitch) }
    }
    Text("Playback speed", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text("%.2fx".format(speed), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, modifier = Modifier.testTag("speed_value"))
    }
    Slider(speed, { speed = (it * 20).roundToInt() / 20f }, valueRange = 0.5f..2f, steps = 29, onValueChangeFinished = { apply(speed) },
        modifier = Modifier.padding(horizontal = 24.dp).testTag("speed_slider"))
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        listOf(0.75f, 1f, 1.25f, 1.5f).forEach { v ->
            FilterChip(selected = speed == v, onClick = { apply(v) }, label = { Text(if (v == 1f) "Normal" else "${v}x") })
        }
    }
    Row(Modifier.fillMaxWidth().clickable { apply(speed, !s.keepPitch) }.padding(horizontal = 24.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("Keep voice natural", style = MaterialTheme.typography.bodyLarge)
            Text("Voices don't sound higher or lower when the speed changes", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(s.keepPitch, { apply(speed, it) })
    }
}

@Composable
private fun AddToPlaylistSheet(ids: List<Long>, vm: MainVm, close: () -> Unit) {
    val lists by vm.playlists.collectAsState()
    var picked by remember(lists) { mutableStateOf(lists.filter { p -> ids.all { it in p.songIds } }.map { it.id }.toSet()) }
    var creating by remember { mutableStateOf(false) }
    Text(if (ids.size == 1) "Add to playlist" else "Add ${plural(ids.size, "song")} to playlist", style = MaterialTheme.typography.titleLarge,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
    Row(Modifier.fillMaxWidth().clickable { creating = true }.padding(horizontal = 24.dp, vertical = 14.dp).testTag("new_playlist"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(20.dp))
        Text("New playlist", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
    }
    lists.forEach { p ->
        val on = p.id in picked
        Row(Modifier.fillMaxWidth().clickable { picked = if (on) picked - p.id else picked + p.id }.padding(horizontal = 12.dp).testTag("pl_${p.id}"), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(on, { picked = if (on) picked - p.id else picked + p.id })
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.bodyLarge)
                Text(plural(p.songIds.size, "song"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
    Button(onClick = { vm.setPlaylistMembership(ids, picked); close() }, modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp).testTag("pl_done")) { Text("Done") }
    if (creating) TextInputDialog("New playlist", "", "Create", onDismiss = { creating = false }, onConfirm = { name ->
        creating = false; if (name.isNotBlank()) { vm.createPlaylist(name, ids); close() }
    })
}
