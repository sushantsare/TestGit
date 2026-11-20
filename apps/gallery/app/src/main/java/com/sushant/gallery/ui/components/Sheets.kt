package com.sushant.gallery.ui.components

import android.content.ClipData
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocationOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sushant.gallery.R
import com.sushant.gallery.data.Album
import com.sushant.gallery.data.AlbumSort
import com.sushant.gallery.data.FileOps
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.PhotoSort
import com.sushant.gallery.ui.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(onDismiss: () -> Unit, title: String? = null, content: @Composable () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp))
            content()
        }
    }
}

@Composable
fun SheetRow(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, onClick: () -> Unit, sub: String? = null, tag: String = "") {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 56.dp).padding(horizontal = 24.dp, vertical = 12.dp).testTag(tag),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(text, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SortSheet(sort: PhotoSort, desc: Boolean, onPick: (PhotoSort, Boolean) -> Unit, onDismiss: () -> Unit) {
    val labels = listOf(PhotoSort.DATE_TAKEN to R.string.sort_date_taken, PhotoSort.DATE_MODIFIED to R.string.sort_date_modified,
        PhotoSort.NAME to R.string.sort_name, PhotoSort.SIZE to R.string.sort_size)
    GallerySheet(onDismiss, stringResource(R.string.sort_photos_by)) {
        labels.forEach { (s, l) ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s, desc) }.heightIn(min = 52.dp).padding(horizontal = 12.dp)
                    .semantics(mergeDescendants = true) { role = Role.RadioButton },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s == sort, onClick = null, modifier = Modifier.padding(12.dp))
                Text(stringResource(l), style = MaterialTheme.typography.bodyLarge)
            }
        }
        val opts = if (sort == PhotoSort.NAME) listOf(R.string.sort_a_z, R.string.sort_z_a)
        else if (sort == PhotoSort.SIZE) listOf(R.string.sort_largest, R.string.sort_smallest)
        else listOf(R.string.sort_newest, R.string.sort_oldest)
        val firstIsDesc = sort != PhotoSort.NAME
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp)) {
            opts.forEachIndexed { i, l ->
                val isDesc = if (i == 0) firstIsDesc else !firstIsDesc
                SegmentedButton(
                    selected = desc == isDesc, onClick = { onPick(sort, isDesc) },
                    shape = SegmentedButtonDefaults.itemShape(i, opts.size), icon = {},
                ) { Text(stringResource(l), maxLines = 1) }
            }
        }
    }
}

@Composable
fun AlbumSortSheet(sort: AlbumSort, onPick: (AlbumSort) -> Unit, onDismiss: () -> Unit) {
    val labels = listOf(AlbumSort.NAME to R.string.sort_name, AlbumSort.DATE to R.string.sort_recent, AlbumSort.COUNT to R.string.sort_count)
    GallerySheet(onDismiss, stringResource(R.string.sort_albums_by)) {
        labels.forEach { (s, l) ->
            Row(
                Modifier.fillMaxWidth().clickable { onPick(s) }.heightIn(min = 52.dp).padding(horizontal = 12.dp)
                    .semantics(mergeDescendants = true) { role = Role.RadioButton },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = s == sort, onClick = null, modifier = Modifier.padding(12.dp))
                Text(stringResource(l), style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

/** "Move to album" / "Copy to album" picker, with "New album". */
@Composable
fun AlbumPickerSheet(copy: Boolean, albums: List<Album>, onPick: (java.io.File) -> Unit, onNew: (String) -> Unit, onDismiss: () -> Unit) {
    var naming by rememberSaveable { mutableStateOf(false) }
    if (naming) {
        NewAlbumDialog(onDone = { naming = false; onNew(it) }, onDismiss = { naming = false })
    }
    GallerySheet(onDismiss, stringResource(if (copy) R.string.copy_to_album else R.string.move_to_album)) {
        SheetRow(Icons.Rounded.Add, stringResource(R.string.new_album), { naming = true }, tag = "new_album")
        LazyColumn(Modifier.heightIn(max = 480.dp)) {
            items(albums, key = { it.folder }) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(java.io.File(a.folder)) }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))) {
                        a.cover?.let { AsyncImage(thumbModel(it), null, contentScale = ContentScale.Crop, modifier = Modifier.size(56.dp)) }
                    }
                    Column(Modifier.weight(1f)) {
                        Text(a.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(pluralText(R.plurals.n_items, a.count), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
fun NewAlbumDialog(onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    var touched by rememberSaveable { mutableStateOf(false) }
    val bad = name.isBlank() || name.contains('/') || name.startsWith(".")
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.new_album)) },
        text = {
            OutlinedTextField(
                value = name, onValueChange = { name = it.take(60); touched = true }, singleLine = true,
                label = { Text(stringResource(R.string.album_name)) },
                isError = touched && bad,
                supportingText = { if (touched && bad) Text(stringResource(R.string.album_name_error)) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { touched = true; if (!bad) onDone(name.trim()) }),
                modifier = Modifier.testTag("album_name"),
            )
        },
        confirmButton = { Button(onClick = { touched = true; if (!bad) onDone(name.trim()) }) { Text(stringResource(R.string.create)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
fun ShareSheet(count: Int, stripDefault: Boolean, hasPhotos: Boolean, onShare: (Boolean) -> Unit, onDismiss: () -> Unit) {
    var strip by rememberSaveable { mutableStateOf(stripDefault) }
    GallerySheet(onDismiss, pluralText(R.plurals.share_n, count)) {
        if (hasPhotos) {
            Row(
                Modifier.fillMaxWidth().clickable { strip = !strip }.padding(horizontal = 24.dp, vertical = 12.dp)
                    .semantics(mergeDescendants = true) { role = Role.Switch }.testTag("strip_switch"),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(Icons.Rounded.LocationOff, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.remove_location), style = MaterialTheme.typography.bodyLarge)
                    Text(stringResource(R.string.remove_location_sub), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Switch(checked = strip, onCheckedChange = null)
            }
        }
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 24.dp, top = 12.dp), horizontalArrangement = Arrangement.End) {
            Button(onClick = { onShare(strip) }, modifier = Modifier.testTag("share_go")) {
                Icon(Icons.Rounded.Share, null, modifier = Modifier.size(18.dp)); Spacer(Modifier.size(8.dp)); Text(stringResource(R.string.share))
            }
        }
    }
}

/** Overflow actions for a multi-selection. */
@Composable
fun SelectionMoreSheet(
    count: Int,
    onMove: () -> Unit, onCopy: () -> Unit, onFavourite: () -> Unit, onHide: () -> Unit, onSelectAll: () -> Unit, onDismiss: () -> Unit,
) {
    GallerySheet(onDismiss, pluralText(R.plurals.n_selected, count)) {
        SheetRow(Icons.Rounded.SelectAll, stringResource(R.string.select_all), onSelectAll, tag = "sel_all")
        SheetRow(Icons.Rounded.DriveFileMove, stringResource(R.string.move_to_album), onMove, tag = "sel_move")
        SheetRow(Icons.Rounded.ContentCopy, stringResource(R.string.copy_to_album), onCopy, tag = "sel_copy")
        SheetRow(Icons.Rounded.Favorite, stringResource(R.string.add_to_favourites), onFavourite, tag = "sel_fav")
        SheetRow(Icons.Rounded.VisibilityOff, stringResource(R.string.move_to_hidden), onHide, tag = "sel_hide")
    }
}

/** Opens Android's share menu. */
fun shareItems(ctx: Context, ops: FileOps, items: List<MediaItem>, strip: Boolean) {
    if (items.isEmpty()) return
    ops.clearShareCache()
    val uris = ArrayList(items.map { ops.shareUri(it, strip) })
    val mime = when {
        items.all { it.isVideo } -> "video/*"
        items.none { it.isVideo } -> "image/*"
        else -> "*/*"
    }
    val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
    else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    send.type = mime
    send.clipData = ClipData.newRawUri(null, uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
    send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    ctx.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** "Set as" (wallpaper, contact photo…) via the system picker. */
fun setAs(ctx: Context, ops: FileOps, item: MediaItem) {
    val uri = ops.contentUri(item)
    val i = Intent(Intent.ACTION_ATTACH_DATA).setDataAndType(uri, item.mime).putExtra("mimeType", item.mime)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    runCatching { ctx.startActivity(Intent.createChooser(i, ctx.getString(R.string.set_as)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
