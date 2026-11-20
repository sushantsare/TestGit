package com.sushant.inknote.editor

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDropDown
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.TextSnippet
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.inknote.R
import com.sushant.inknote.data.FolderEntity
import com.sushant.inknote.data.RecordingEntity
import com.sushant.inknote.io.ExportFormat
import com.sushant.inknote.ui.Dates
import com.sushant.inknote.ui.home.SheetRow
import com.sushant.inknote.ui.theme.LocalInk

@Composable
fun LiveRecordingBar(elapsedMs: Long, onStop: () -> Unit) {
    val red = Color(0xFFD93025)
    val pulse by rememberInfiniteTransition(label = "rec").animateFloat(1f, 0.3f, infiniteRepeatable(tween(700), RepeatMode.Reverse), label = "dot")
    Surface(shape = RoundedCornerShape(14.dp), color = LocalInk.current.card, border = BorderStroke(1.dp, red), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("live_rec")) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(12.dp).alpha(pulse).background(red, CircleShape))
            Text(stringResource(R.string.recording_time, Dates.mmss(elapsedMs)), color = red, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Button(onClick = onStop, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = red, contentColor = Color.White), shape = RoundedCornerShape(10.dp), modifier = Modifier.testTag("rec_stop")) {
                Text(stringResource(R.string.stop))
            }
        }
    }
}

@Composable
fun PlayerBar(
    recs: List<RecordingEntity>, current: RecordingEntity?, playing: Boolean, pos: Long,
    onPlay: (RecordingEntity) -> Unit, onPause: () -> Unit, onSeek: (Long) -> Unit, onSelect: (RecordingEntity) -> Unit, onDelete: (RecordingEntity) -> Unit,
) {
    val ink = LocalInk.current
    val r = current ?: recs.last()
    val posLabel = stringResource(R.string.position)
    var menu by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<RecordingEntity?>(null) }
    Column(Modifier.padding(horizontal = 12.dp, vertical = 4.dp).testTag("player")) {
        Surface(shape = RoundedCornerShape(14.dp), color = ink.card, border = BorderStroke(1.dp, ink.divider)) {
            Row(Modifier.padding(start = 6.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { if (playing) onPause() else onPlay(r) }, modifier = Modifier.testTag("play")) {
                    Box(Modifier.size(36.dp).background(MaterialTheme.colorScheme.primary, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, stringResource(if (playing) R.string.pause else R.string.play), tint = MaterialTheme.colorScheme.onPrimary)
                    }
                }
                val dur = r.durationMs.coerceAtLeast(1)
                Slider((if (current?.id == r.id) pos else 0L).coerceAtMost(dur).toFloat(), { onSeek(it.toLong()) }, valueRange = 0f..dur.toFloat(),
                    modifier = Modifier.weight(1f).semantics { contentDescription = posLabel })
                Text("${Dates.mmss(if (current?.id == r.id) pos else 0)} / ${Dates.mmss(r.durationMs)}", style = MaterialTheme.typography.bodySmall, color = ink.muted, modifier = Modifier.padding(horizontal = 6.dp))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.ArrowDropDown, stringResource(R.string.recordings)) }
                    DropdownMenu(menu, { menu = false }) {
                        recs.forEach { x ->
                            DropdownMenuItem(text = { Column { Text(x.name, maxLines = 1, overflow = TextOverflow.Ellipsis); Text(Dates.mmss(x.durationMs), style = MaterialTheme.typography.bodySmall, color = ink.muted) } },
                                onClick = { menu = false; onSelect(x) }, leadingIcon = { if (x.id == r.id) Icon(Icons.Filled.Check, null) },
                                trailingIcon = { IconButton(onClick = { menu = false; confirm = x }) { Icon(Icons.Outlined.Delete, stringResource(R.string.delete_recording)) } })
                        }
                    }
                }
            }
        }
        Row(Modifier.padding(top = 4.dp, start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Info, null, tint = ink.muted, modifier = Modifier.size(15.dp))
            Text(stringResource(if (playing) R.string.sync_tip_playing else R.string.sync_tip), style = MaterialTheme.typography.bodySmall, color = ink.muted, modifier = Modifier.padding(start = 6.dp))
        }
    }
    confirm?.let { x ->
        AlertDialog(onDismissRequest = { confirm = null }, title = { Text(stringResource(R.string.delete_recording_q)) }, text = { Text(x.name) },
            confirmButton = { TextButton(onClick = { confirm = null; onDelete(x) }) { Text(stringResource(R.string.delete)) } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConvertSheet(r: ConvertResult, onDownload: () -> Unit, onApply: (String, Boolean) -> Unit, onDismiss: () -> Unit) {
    var text by rememberSaveable(r.text) { mutableStateOf(r.text ?: "") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = 20.dp).padding(bottom = 16.dp).testTag("convert_sheet")) {
            Text(stringResource(R.string.hw_to_text), style = MaterialTheme.typography.titleLarge)
            Text(stringResource(R.string.hw_offline), style = MaterialTheme.typography.bodyMedium, color = LocalInk.current.muted, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp))
            when {
                r.busy -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 20.dp)) {
                    CircularProgressIndicator(Modifier.size(28.dp)); Text(stringResource(if (r.needsDownload) R.string.downloading_pack else R.string.reading_hw), Modifier.padding(start = 14.dp))
                }
                r.needsDownload -> {
                    Text(stringResource(R.string.need_pack), style = MaterialTheme.typography.bodyLarge)
                    Button(onClick = onDownload, modifier = Modifier.padding(top = 14.dp).testTag("download_pack"), shape = RoundedCornerShape(12.dp)) { Text(stringResource(R.string.download_pack)) }
                }
                r.text.isNullOrBlank() -> Text(stringResource(R.string.no_hw_found), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 12.dp))
                else -> {
                    OutlinedTextField(text, { text = it }, modifier = Modifier.fillMaxWidth().heightIn(min = 100.dp), label = { Text(stringResource(R.string.result_edit)) })
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                        OutlinedButton(onClick = { onApply(text, false) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.add_below)) }
                        Button(onClick = { onApply(text, true) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("convert_replace")) { Text(stringResource(R.string.replace)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportSheet(title: String, pageCount: Int, busy: Boolean, onSave: (ExportFormat, Boolean) -> Unit, onShare: (ExportFormat, Boolean) -> Unit, onDismiss: () -> Unit) {
    var fmt by rememberSaveable { mutableStateOf(ExportFormat.PDF) }
    var onlyThis by rememberSaveable { mutableStateOf(false) }
    val ink = LocalInk.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp).testTag("export_sheet")) {
            Text(stringResource(R.string.export_title, title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp), maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(stringResource(R.string.export_sub), style = MaterialTheme.typography.bodyMedium, color = ink.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            listOf(
                Triple(ExportFormat.PDF, Icons.Outlined.PictureAsPdf, R.string.fmt_pdf to R.string.fmt_pdf_sub),
                Triple(ExportFormat.PNG, Icons.Outlined.Image, R.string.fmt_png to R.string.fmt_png_sub),
                Triple(ExportFormat.TEXT, Icons.Outlined.TextSnippet, R.string.fmt_text to R.string.fmt_text_sub),
                Triple(ExportFormat.INKNOTE, Icons.Outlined.Description, R.string.fmt_inknote to R.string.fmt_inknote_sub),
            ).forEach { (f, icon, l) ->
                Row(Modifier.fillMaxWidth().background(if (fmt == f) ink.surf2 else Color.Transparent).clickable { fmt = f }.heightIn(min = 60.dp).padding(horizontal = 20.dp).testTag("fmt_${f.name}"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                    Column(Modifier.weight(1f).padding(start = 16.dp)) { Text(stringResource(l.first), style = MaterialTheme.typography.bodyLarge); Text(stringResource(l.second), style = MaterialTheme.typography.bodySmall, color = ink.muted) }
                    if (fmt == f) Icon(Icons.Filled.Check, null, tint = MaterialTheme.colorScheme.primary)
                }
            }
            if (fmt != ExportFormat.INKNOTE && pageCount > 1) {
                Text(stringResource(R.string.pages), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                    SegmentedButton(!onlyThis, { onlyThis = false }, SegmentedButtonDefaults.itemShape(0, 2)) { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.all_n_pages, pageCount, pageCount)) }
                    SegmentedButton(onlyThis, { onlyThis = true }, SegmentedButtonDefaults.itemShape(1, 2)) { Text(stringResource(R.string.this_page)) }
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 16.dp), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                if (busy) CircularProgressIndicator(Modifier.size(24.dp).padding(end = 8.dp))
                OutlinedButton(onClick = { onSave(fmt, onlyThis) }, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("export_save")) { Icon(Icons.Outlined.Save, null); Text(stringResource(R.string.save), Modifier.padding(start = 6.dp)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onShare(fmt, onlyThis) }, enabled = !busy, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("export_share")) { Icon(Icons.Outlined.Share, null); Text(stringResource(R.string.share), Modifier.padding(start = 6.dp)) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TagsSheet(folders: List<FolderEntity>, folder: Long?, allTags: List<String>, noteTags: List<String>, onSave: (List<String>, Long?) -> Unit, onDismiss: () -> Unit) {
    var f by rememberSaveable { mutableStateOf(folder) }
    var tags by rememberSaveable { mutableStateOf(noteTags) }
    var newTag by rememberSaveable { mutableStateOf("") }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().verticalScroll(rememberScrollState()).padding(bottom = 16.dp).testTag("tags_sheet")) {
            Text(stringResource(R.string.folder_and_tags), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Text(stringResource(R.string.folder), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 12.dp))
            SheetRow(null, stringResource(R.string.no_folder), f == null) { f = null }
            folders.forEach { x -> SheetRow(Color(x.color), x.name, f == x.id) { f = x.id } }
            Text(stringResource(R.string.tags), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp))
            FlowRow(Modifier.padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (allTags + tags).distinct().sorted().forEach { t ->
                    FilterChip(t in tags, { tags = if (t in tags) tags - t else tags + t }, { Text("#$t") })
                }
            }
            Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newTag, { newTag = it.replace(" ", "").removePrefix("#").take(30) }, singleLine = true, label = { Text(stringResource(R.string.new_tag)) }, modifier = Modifier.weight(1f).testTag("new_tag"))
                IconButton(onClick = { if (newTag.isNotBlank()) { tags = (tags + newTag).distinct(); newTag = "" } }, enabled = newTag.isNotBlank()) { Icon(Icons.Outlined.Add, stringResource(R.string.add_tag)) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
                Button(onClick = { onSave(if (newTag.isNotBlank()) (tags + newTag).distinct() else tags, f) }, shape = RoundedCornerShape(12.dp), modifier = Modifier.testTag("tags_done")) { Text(stringResource(R.string.done)) }
            }
        }
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, label: String, onDismiss: () -> Unit, onOk: (String) -> Unit) {
    var v by rememberSaveable { mutableStateOf(initial) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(v, { v = it.take(80) }, singleLine = true, label = { Text(label) }, modifier = Modifier.fillMaxWidth().testTag("text_input")) },
        confirmButton = { TextButton(onClick = { onOk(v) }, enabled = v.isNotBlank(), modifier = Modifier.testTag("text_input_ok")) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } })
}

