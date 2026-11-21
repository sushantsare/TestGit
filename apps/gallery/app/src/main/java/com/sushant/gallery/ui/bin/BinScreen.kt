package com.sushant.gallery.ui.bin

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.SelectAll
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.sushant.gallery.R
import com.sushant.gallery.data.Formats
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.Prefs
import com.sushant.gallery.data.TrashEntity
import com.sushant.gallery.ui.MainViewModel
import com.sushant.gallery.ui.Nav
import com.sushant.gallery.ui.components.AppTopBar
import com.sushant.gallery.ui.components.ConfirmDialog
import com.sushant.gallery.ui.components.EmptyState
import com.sushant.gallery.ui.components.GallerySheet
import com.sushant.gallery.ui.components.GridGroup
import com.sushant.gallery.ui.components.Illus
import com.sushant.gallery.ui.components.InfoBanner
import com.sushant.gallery.ui.components.MediaGrid
import com.sushant.gallery.ui.components.SelectionTopBar
import com.sushant.gallery.ui.components.SheetRow
import com.sushant.gallery.ui.components.pluralText
import com.sushant.gallery.ui.components.thumbModel
import java.io.File

/** Bin entries shown as media items (key "bin:<id>"). */
private fun TrashEntity.asItem() = MediaItem(
    id = -3, uri = android.net.Uri.fromFile(File(trashPath)), path = trashPath, name = name, mime = mime, isVideo = isVideo,
    dateTaken = dateTaken, dateModified = deletedAt, size = size, width = 0, height = 0, durationMs = durationMs,
    folder = File(trashPath).parent ?: "", bucket = "Bin",
)

@Composable
fun BinScreen(vm: MainViewModel, nav: Nav) {
    val trash by vm.trash.collectAsStateWithLifecycle()
    val now = remember { System.currentTimeMillis() }
    val byKey = remember(trash) { trash.associateBy { it.trashPath } }
    val items = remember(trash) { trash.map { it.asItem() } }
    var sel by rememberSaveable { mutableStateOf(listOf<String>()) }
    val selSet = remember(sel) { sel.toSet() }
    var opened by rememberSaveable { mutableStateOf<String?>(null) }
    var confirm by rememberSaveable { mutableStateOf(0) } // 1 empty bin, 2 delete selected forever, 3 delete opened forever
    BackHandler(sel.isNotEmpty()) { sel = emptyList() }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).windowInsetsPadding(WindowInsets.statusBars)) {
        if (sel.isNotEmpty()) {
            SelectionTopBar(pluralText(R.plurals.n_selected, sel.size), onClose = { sel = emptyList() }) {
                IconButton(onClick = { sel = items.map { it.key } }) { Icon(Icons.Rounded.SelectAll, stringResource(R.string.select_all)) }
                IconButton(onClick = { vm.restore(sel.mapNotNull { byKey[it] }); sel = emptyList() }, modifier = Modifier.testTag("bin_restore_sel")) {
                    Icon(Icons.Rounded.History, stringResource(R.string.restore))
                }
                IconButton(onClick = { confirm = 2 }) { Icon(Icons.Rounded.DeleteForever, stringResource(R.string.delete_forever)) }
            }
        } else {
            AppTopBar(stringResource(R.string.recycle_bin), subtitle = pluralText(R.plurals.n_items, trash.size), onBack = { nav.back() }) {
                if (trash.isNotEmpty()) TextButton(onClick = { confirm = 1 }, modifier = Modifier.testTag("empty_bin")) { Text(stringResource(R.string.empty_action)) }
            }
        }
        if (trash.isEmpty()) {
            EmptyState(Illus.BIN, stringResource(R.string.bin_empty_title), stringResource(R.string.bin_empty_body))
        } else {
            MediaGrid(
                groups = listOf(GridGroup("", items)), columns = 4, selection = selSet, favourites = emptySet(),
                onOpen = { m -> opened = m.key },
                onSelect = { keys, on -> sel = if (on) (sel + keys).distinct() else sel - keys.toSet() },
                daysLeft = { m -> byKey[m.key]?.let { Formats.daysLeft(it.deletedAt, now, Prefs.BIN_DAYS) } ?: 0 },
                bottomPadding = 24.dp,
                header = { InfoBanner(Icons.Outlined.Info, stringResource(R.string.bin_banner)) },
                modifier = Modifier.windowInsetsPadding(WindowInsets.navigationBars),
            )
        }
    }

    val e = opened?.let { byKey[it] }
    if (e != null) {
        GallerySheet({ opened = null }) {
            Row(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp))) { AsyncImage(thumbModel(e.asItem()), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
                Column {
                    Text(e.name, style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.deleted_in_days, Formats.daysLeft(e.deletedAt, now, Prefs.BIN_DAYS)), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            SheetRow(Icons.Rounded.History, stringResource(R.string.restore), { vm.restore(listOf(e)); opened = null }, tag = "bin_restore")
            SheetRow(Icons.Rounded.DeleteForever, stringResource(R.string.delete_forever), { confirm = 3 }, tag = "bin_forever")
        }
    }
    when (confirm) {
        1 -> ConfirmDialog(
            stringResource(R.string.empty_bin_q), pluralText(R.plurals.empty_bin_body, trash.size), pluralText(R.plurals.delete_n_items, trash.size),
            onConfirm = { confirm = 0; vm.deleteForever(trash) }, onDismiss = { confirm = 0 },
        )
        2 -> ConfirmDialog(
            stringResource(R.string.delete_forever_q), pluralText(R.plurals.delete_forever_body, sel.size), stringResource(R.string.delete_forever),
            onConfirm = { confirm = 0; vm.deleteForever(sel.mapNotNull { byKey[it] }); sel = emptyList() }, onDismiss = { confirm = 0 },
        )
        3 -> ConfirmDialog(
            stringResource(R.string.delete_forever_q), stringResource(R.string.delete_forever_body_1), stringResource(R.string.delete_forever),
            onConfirm = { confirm = 0; e?.let { vm.deleteForever(listOf(it)) }; opened = null }, onDismiss = { confirm = 0 },
        )
    }
}
