package com.sushant.inknote.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CropLandscape
import androidx.compose.material.icons.outlined.CropPortrait
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.TextFields
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewDay
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.inknote.R
import com.sushant.inknote.data.AppSettings
import com.sushant.inknote.data.FolderEntity
import com.sushant.inknote.data.Kind
import com.sushant.inknote.data.Orientation
import com.sushant.inknote.data.PageType
import com.sushant.inknote.data.Paper
import com.sushant.inknote.ui.Filter
import com.sushant.inknote.ui.HomeState
import com.sushant.inknote.ui.theme.FolderColors
import com.sushant.inknote.ui.theme.LocalInk

// ---------------- navigation (drawer / side panel) ----------------
enum class Place { HOME, TRASH, SETTINGS }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NavContent(
    s: HomeState, place: Place, onFilter: (Filter) -> Unit, onTrash: () -> Unit, onSettings: () -> Unit,
    onNewFolder: () -> Unit, onEditFolder: (FolderEntity) -> Unit,
) {
    val ink = LocalInk.current
    Column(Modifier.verticalScroll(rememberScrollState()).padding(bottom = 20.dp).testTag("nav")) {
        Row(Modifier.padding(start = 26.dp, top = 20.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Draw, null, tint = MaterialTheme.colorScheme.onPrimary)
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 12.dp))
        }
        val home = place == Place.HOME
        NavItem(Icons.Outlined.Description, stringResource(R.string.all_notes), s.all.size, home && s.filter == Filter.All) { onFilter(Filter.All) }
        NavItem(Icons.Outlined.StarOutline, stringResource(R.string.favourites), s.all.count { it.note.favourite }, home && s.filter == Filter.Favourites) { onFilter(Filter.Favourites) }
        NavItem(Icons.Outlined.PushPin, stringResource(R.string.pinned), s.all.count { it.note.pinned }, home && s.filter == Filter.Pinned) { onFilter(Filter.Pinned) }
        NavItem(Icons.Outlined.Lock, stringResource(R.string.locked_notes), s.all.count { it.note.locked }, home && s.filter == Filter.Locked) { onFilter(Filter.Locked) }
        Row(Modifier.fillMaxWidth().padding(start = 26.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.folders), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
            IconButton(onClick = onNewFolder, Modifier.testTag("new_folder")) { Icon(Icons.Outlined.Add, stringResource(R.string.new_folder), tint = MaterialTheme.colorScheme.primary) }
        }
        if (s.folders.isEmpty()) Text(stringResource(R.string.no_folders), color = ink.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 26.dp, vertical = 6.dp))
        for (f in s.folders) {
            NavItem(null, f.name, s.folderCounts[f.id] ?: 0, home && s.filter == Filter.Folder(f.id), dot = Color(f.color),
                onLong = { onEditFolder(f) }) { onFilter(Filter.Folder(f.id)) }
        }
        if (s.tags.isNotEmpty()) {
            Text(stringResource(R.string.tags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 26.dp, top = 14.dp, bottom = 6.dp))
            FlowRow(Modifier.padding(horizontal = 22.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                s.tags.forEach { t ->
                    val on = home && s.filter == Filter.Tag(t)
                    Surface(onClick = { onFilter(Filter.Tag(t)) }, shape = RoundedCornerShape(12.dp), color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant) {
                        Text("#$t", Modifier.padding(horizontal = 12.dp, vertical = 8.dp), color = if (on) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(horizontal = 24.dp, vertical = 12.dp), color = ink.divider)
        NavItem(Icons.Outlined.Delete, stringResource(R.string.trash), s.trashCount, place == Place.TRASH, tag = "nav_trash", onClick = onTrash)
        NavItem(Icons.Outlined.Settings, stringResource(R.string.settings), null, place == Place.SETTINGS, tag = "nav_settings", onClick = onSettings)
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun NavItem(icon: ImageVector?, label: String, count: Int?, on: Boolean, dot: Color? = null, tag: String? = null, onLong: (() -> Unit)? = null, onClick: () -> Unit) {
    Row(Modifier.padding(horizontal = 12.dp).fillMaxWidth().heightIn(min = 52.dp).clip(RoundedCornerShape(26.dp))
        .background(if (on) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
        .then(if (onLong != null) Modifier.combinedClickable(onLongClick = onLong, onClick = onClick) else Modifier.clickable(onClick = onClick))
        .padding(horizontal = 14.dp).testTag(tag ?: "nav_$label"), verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        if (dot != null) Box(Modifier.padding(horizontal = 6.dp).size(12.dp).background(dot, CircleShape))
        Text(label, Modifier.weight(1f).padding(start = 14.dp), color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (count != null) Text("$count", color = LocalInk.current.muted, style = MaterialTheme.typography.bodySmall)
    }
}

// ---------------- folder dialog ----------------
@Composable
fun FolderDialog(initial: FolderEntity?, onDismiss: () -> Unit, onSave: (String, Int) -> Unit, onDelete: (() -> Unit)?) {
    var name by rememberSaveable { mutableStateOf(initial?.name ?: "") }
    var color by rememberSaveable { mutableIntStateOf(initial?.color ?: FolderColors[0]) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    if (confirmDelete) {
        AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text(stringResource(R.string.delete_folder_q)) },
            text = { Text(stringResource(R.string.delete_folder_body)) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; onDelete?.invoke() }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } })
        return
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(if (initial == null) R.string.new_folder else R.string.edit_folder)) },
        text = {
            Column {
                OutlinedTextField(name, { name = it.take(40) }, singleLine = true, label = { Text(stringResource(R.string.folder_name)) }, modifier = Modifier.fillMaxWidth().testTag("folder_name"))
                Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FolderColors.take(6).forEach { c ->
                        Box(Modifier.size(32.dp).clip(CircleShape).background(Color(c)).clickable { color = c }
                            .then(if (c == color) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape) else Modifier), contentAlignment = Alignment.Center) {
                            if (c == color) Icon(Icons.Filled.Check, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (onDelete != null) TextButton(onClick = { confirmDelete = true }, Modifier.padding(top = 8.dp)) { Text(stringResource(R.string.delete_folder), color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, color) }, enabled = name.isNotBlank(), modifier = Modifier.testTag("folder_save")) { Text(stringResource(if (initial == null) R.string.create else R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

// ---------------- move-to-folder sheet ----------------
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoveSheet(folders: List<FolderEntity>, current: Long?, onDismiss: () -> Unit, onPick: (Long?) -> Unit, onNewFolder: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp)) {
            Text(stringResource(R.string.move_to_folder), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
            SheetRow(null, stringResource(R.string.no_folder), current == null) { onPick(null) }
            folders.forEach { f -> SheetRow(Color(f.color), f.name, current == f.id) { onPick(f.id) } }
            TextButton(onClick = onNewFolder, Modifier.padding(horizontal = 12.dp)) { Icon(Icons.Outlined.Add, null); Text(stringResource(R.string.new_folder), Modifier.padding(start = 8.dp)) }
        }
    }
}

@Composable
fun SheetRow(dot: Color?, label: String, checked: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(onClick = onClick).padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
        if (dot != null) Box(Modifier.size(14.dp).background(dot, CircleShape)) else Spacer(Modifier.width(14.dp))
        Text(label, Modifier.weight(1f).padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
        if (checked) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
    }
}

// ---------------- new note sheet ----------------
data class NewNoteSpec(val kind: String, val orientation: String, val paper: String, val pageType: String)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun NewNoteSheet(
    st: AppSettings, paperOnly: Boolean, currentPaper: String? = null, onDismiss: () -> Unit,
    onCreate: (NewNoteSpec) -> Unit, onOpenPdf: () -> Unit, onVoice: () -> Unit,
) {
    var kind by rememberSaveable { mutableStateOf(Kind.INK) }
    var orientation by rememberSaveable { mutableStateOf(st.defaultOrientation) }
    var paper by rememberSaveable { mutableStateOf(currentPaper ?: st.defaultPaper) }
    var pageType by rememberSaveable { mutableStateOf(st.defaultPageType) }
    val ink = LocalInk.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 16.dp).testTag("new_sheet")) {
            Text(stringResource(if (paperOnly) R.string.paper_style else R.string.new_note), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            if (!paperOnly) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KindTile(Icons.Outlined.Draw, stringResource(R.string.handwrite), kind == Kind.INK, Modifier.weight(1f)) { kind = Kind.INK }
                    KindTile(Icons.Outlined.TextFields, stringResource(R.string.type), kind == Kind.TEXT, Modifier.weight(1f)) { kind = Kind.TEXT }
                    KindTile(Icons.Outlined.PictureAsPdf, stringResource(R.string.open_pdf), false, Modifier.weight(1f), onOpenPdf)
                    KindTile(Icons.Outlined.Mic, stringResource(R.string.voice_note), false, Modifier.weight(1f), onVoice)
                }
            }
            if (!paperOnly) {
                SectionLabel(stringResource(R.string.page_direction))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    SegmentedButton(orientation == Orientation.PORTRAIT, { orientation = Orientation.PORTRAIT }, SegmentedButtonDefaults.itemShape(0, 2),
                        icon = { Icon(Icons.Outlined.CropPortrait, null) }, modifier = Modifier.testTag("dir_vertical")) { Text(stringResource(R.string.vertical)) }
                    SegmentedButton(orientation == Orientation.LANDSCAPE, { orientation = Orientation.LANDSCAPE }, SegmentedButtonDefaults.itemShape(1, 2),
                        icon = { Icon(Icons.Outlined.CropLandscape, null) }, modifier = Modifier.testTag("dir_horizontal")) { Text(stringResource(R.string.horizontal)) }
                }
            }
            SectionLabel(stringResource(R.string.paper))
            Paper.ALL.chunked(4).forEach { rowPapers -> Row(Modifier.padding(horizontal = 20.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowPapers.forEach { p ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(10.dp)).clickable { paper = p }.testTag("paper_$p"), horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(Modifier.fillMaxWidth().aspectRatio(if (orientation == Orientation.LANDSCAPE && !paperOnly) 4f / 3f else 3f / 4f).clip(RoundedCornerShape(8.dp))
                            .background(if (ink.dark) Color(0xFF1E2024) else Color.White)
                            .border(if (paper == p) BorderStroke(2.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, ink.divider), RoundedCornerShape(8.dp))) {
                            Canvas(Modifier.matchParentSize()) { paperPreview(p, ink.paperLine, ink.muted) }
                        }
                        Text(paperName(p), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            } }
            if (!paperOnly && kind == Kind.INK) {
                SectionLabel(stringResource(R.string.page_type))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    SegmentedButton(pageType == PageType.PAGES, { pageType = PageType.PAGES }, SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Outlined.ViewAgenda, null) }) { Text(stringResource(R.string.pages)) }
                    SegmentedButton(pageType == PageType.ENDLESS, { pageType = PageType.ENDLESS }, SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Outlined.ViewDay, null) }) { Text(stringResource(R.string.one_long_page)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onCreate(NewNoteSpec(kind, orientation, paper, if (kind == Kind.TEXT) PageType.PAGES else pageType)) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("create_note")) {
                    Text(stringResource(if (paperOnly) R.string.apply else R.string.create))
                }
            }
        }
    }
}

@Composable
private fun SectionLabel(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp))

@Composable
private fun KindTile(icon: ImageVector, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(16.dp), color = if (on) MaterialTheme.colorScheme.primaryContainer else LocalInk.current.surf2, modifier = modifier.testTag("kind_$label")) {
        Column(Modifier.padding(vertical = 12.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, tint = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
            Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 6.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
fun paperName(p: String): String = stringResource(when (p) {
    Paper.PLAIN -> R.string.paper_plain; Paper.LINED -> R.string.paper_lined; Paper.GRID -> R.string.paper_grid; Paper.DOTTED -> R.string.paper_dotted
    Paper.CORNELL -> R.string.paper_cornell; Paper.TODO -> R.string.paper_todo; Paper.PLANNER -> R.string.paper_planner; else -> R.string.paper_timetable
})

private fun DrawScope.paperPreview(p: String, line: Color, label: Color) {
    val w = size.width; val h = size.height; val g = w / 8f
    when (p) {
        Paper.LINED -> { var y = g * 1.5f; while (y < h) { drawLine(line, Offset(g * 0.6f, y), Offset(w - g * 0.6f, y), 1.5f); y += g } }
        Paper.GRID -> { var x = g; while (x < w) { drawLine(line, Offset(x, 0f), Offset(x, h), 1.2f); x += g }; var y = g; while (y < h) { drawLine(line, Offset(0f, y), Offset(w, y), 1.2f); y += g } }
        Paper.DOTTED -> { var y = g; while (y < h) { var x = g; while (x < w) { drawCircle(line, 1.8f, Offset(x, y)); x += g }; y += g } }
        Paper.CORNELL -> { drawLine(line, Offset(0f, h * 0.12f), Offset(w, h * 0.12f), 2f); drawLine(line, Offset(0f, h * 0.78f), Offset(w, h * 0.78f), 2f); drawLine(line, Offset(w * 0.28f, h * 0.12f), Offset(w * 0.28f, h * 0.78f), 2f) }
        Paper.TODO -> for (i in 0 until 6) { val y = h * (0.16f + i * 0.13f); drawRect(label, Offset(w * 0.12f, y - g * 0.4f), androidx.compose.ui.geometry.Size(g * 0.55f, g * 0.55f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f)); drawLine(line, Offset(w * 0.3f, y), Offset(w * 0.88f, y), 1.5f) }
        Paper.PLANNER -> for (i in 0 until 7) drawRoundRect(line, Offset(w * 0.08f, h * (0.06f + i * 0.13f)), androidx.compose.ui.geometry.Size(w * 0.84f, h * 0.11f), androidx.compose.ui.geometry.CornerRadius(4f), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5f))
        Paper.TIMETABLE -> { val l = w * 0.1f; val t = h * 0.1f; val r = w * 0.9f; val b = h * 0.9f
            for (i in 0..5) drawLine(line, Offset(l + (r - l) * i / 5, t), Offset(l + (r - l) * i / 5, b), 1.2f)
            for (j in 0..7) drawLine(line, Offset(l, t + (b - t) * j / 7), Offset(r, t + (b - t) * j / 7), 1.2f) }
    }
}

