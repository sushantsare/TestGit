package com.sushant.inknote.editor

import android.graphics.Typeface
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.FormatAlignLeft
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AudioFile
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckBox
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DocumentScanner
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FormatBold
import androidx.compose.material.icons.outlined.FormatColorText
import androidx.compose.material.icons.outlined.FormatItalic
import androidx.compose.material.icons.outlined.FormatUnderlined
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Title
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush as GBrush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.inknote.R
import com.sushant.inknote.data.Brush
import com.sushant.inknote.ink.EditTool
import com.sushant.inknote.ink.ToolState
import com.sushant.inknote.ui.theme.LocalInk

val PEN_COLORS = listOf(0xFF1F3F9A, 0xFF1C1B1F, 0xFFC0392B, 0xFF0E8A6B, 0xFFE67E22, 0xFF8A3FB8, 0xFF2B5BD7, 0xFFD81B60,
    0xFF795548, 0xFF607D8B, 0xFF00897B, 0xFFF9A825, 0xFF5E35B1, 0xFF43A047, 0xFF039BE5, 0xFFFFFFFF).map { it.toInt() }
val HL_COLORS = listOf(0xFFFFD43B, 0xFF8CE99A, 0xFFFFA8C5, 0xFF91C8FF, 0xFFFFC078).map { it.toInt() }

@Composable
fun ToolPanel(
    t: ToolState, editingText: Boolean, holder: TextEditorHolder, onTools: (ToolState) -> Unit, onPenSheet: () -> Unit,
    onInsert: () -> Unit, onDoneText: () -> Unit, recentColors: List<Int>,
) {
    val ink = LocalInk.current
    Surface(color = ink.card, shadowElevation = 4.dp, modifier = Modifier.fillMaxWidth().testTag("tool_panel")) {
        Column(Modifier.navigationBarsPadding().padding(top = 6.dp, bottom = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.widthIn(max = 640.dp).fillMaxWidth().heightIn(min = 44.dp).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                when (t.tool) {
                    EditTool.PEN, EditTool.SHAPE -> Row(verticalAlignment = Alignment.CenterVertically) {
                        recentColors.take(5).forEach { c -> ColorDot(c, t.color == c) { onTools(t.copy(color = c)) } }
                        RainbowDot(onPenSheet)
                        Spacer(Modifier.weight(1f))
                        if (t.tool == EditTool.SHAPE) Text(stringResource(R.string.shape_hint), style = MaterialTheme.typography.bodySmall, color = ink.muted, maxLines = 2, modifier = Modifier.widthIn(max = 150.dp))
                        else SizeSlider(t.width, 1f..30f) { onTools(t.copy(width = it)) }
                    }
                    EditTool.HIGHLIGHTER -> Row(verticalAlignment = Alignment.CenterVertically) {
                        HL_COLORS.forEach { c -> ColorDot(c, t.hlColor == c) { onTools(t.copy(hlColor = c)) } }
                        Spacer(Modifier.weight(1f)); SizeSlider(t.hlWidth, 10f..60f) { onTools(t.copy(hlWidth = it)) }
                    }
                    EditTool.ERASER -> Row(verticalAlignment = Alignment.CenterVertically) {
                        FilterChip(!t.eraserArea, { onTools(t.copy(eraserArea = false)) }, { Text(stringResource(R.string.stroke_eraser)) })
                        Spacer(Modifier.width(8.dp))
                        FilterChip(t.eraserArea, { onTools(t.copy(eraserArea = true)) }, { Text(stringResource(R.string.area_eraser)) })
                        Spacer(Modifier.weight(1f)); SizeSlider(t.eraserSize, 6f..80f) { onTools(t.copy(eraserSize = it)) }
                    }
                    EditTool.LASSO -> Text(stringResource(R.string.lasso_hint), style = MaterialTheme.typography.bodyMedium, color = ink.muted)
                    EditTool.TEXT -> if (editingText) FormatBar(holder, onDoneText) else Text(stringResource(R.string.text_hint), style = MaterialTheme.typography.bodyMedium, color = ink.muted)
                }
            }
            Row(Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.SpaceAround) {
                ToolButton(Icons.Outlined.Edit, stringResource(R.string.tool_pen), t.tool == EditTool.PEN) { if (t.tool == EditTool.PEN) onPenSheet() else onTools(t.copy(tool = EditTool.PEN)) }
                ToolButton(Icons.Outlined.Draw, stringResource(R.string.tool_highlighter), t.tool == EditTool.HIGHLIGHTER) { onTools(t.copy(tool = EditTool.HIGHLIGHTER)) }
                ToolButton(Icons.Outlined.AutoFixHigh, stringResource(R.string.tool_eraser), t.tool == EditTool.ERASER) { onTools(t.copy(tool = EditTool.ERASER)) }
                ToolButton(Icons.Outlined.Gesture, stringResource(R.string.tool_select), t.tool == EditTool.LASSO) { onTools(t.copy(tool = EditTool.LASSO)) }
                ToolButton(Icons.Outlined.Interests, stringResource(R.string.tool_shapes), t.tool == EditTool.SHAPE) { onTools(t.copy(tool = EditTool.SHAPE)) }
                ToolButton(Icons.Outlined.Title, stringResource(R.string.tool_text), t.tool == EditTool.TEXT) { onTools(t.copy(tool = EditTool.TEXT)) }
                ToolButton(Icons.Outlined.Add, stringResource(R.string.tool_insert), false, onInsert)
            }
        }
    }
}

@Composable
private fun ToolButton(icon: ImageVector, label: String, on: Boolean, onClick: () -> Unit) {
    val p = MaterialTheme.colorScheme.primary
    Box(Modifier.size(48.dp).clip(RoundedCornerShape(13.dp)).background(if (on) p.copy(alpha = 0.16f) else Color.Transparent)
        .clickable(onClickLabel = label, onClick = onClick).semantics { contentDescription = label; role = Role.Tab; selected = on }
        .testTag("tool_$label"), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (on) p else MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun ColorDot(c: Int, on: Boolean, size: Int = 26, onClick: () -> Unit) {
    val p = MaterialTheme.colorScheme.primary
    Box(Modifier.size(44.dp).clickable(onClickLabel = "Colour", onClick = onClick).semantics { contentDescription = "Colour"; selected = on }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(size.dp).then(if (on) Modifier.border(2.5.dp, p, CircleShape).padding(4.dp) else Modifier)
            .clip(CircleShape).background(Color(c)).border(1.dp, Color(0x55888888), CircleShape))
    }
}

@Composable
private fun RainbowDot(onClick: () -> Unit) {
    Box(Modifier.size(44.dp).clickable(onClickLabel = "More colours and pens", onClick = onClick).semantics { contentDescription = "More colours and pens" }, contentAlignment = Alignment.Center) {
        Box(Modifier.size(26.dp).clip(CircleShape).background(GBrush.sweepGradient(listOf(Color.Red, Color(0xFFFF9800), Color.Yellow, Color.Green, Color.Blue, Color(0xFF9C27B0), Color.Red))))
    }
}

@Composable
private fun SizeSlider(v: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(R.string.size), style = MaterialTheme.typography.bodySmall, color = LocalInk.current.muted)
        Slider(v, onChange, valueRange = range, modifier = Modifier.width(110.dp).padding(start = 6.dp).semantics { contentDescription = "Size" })
    }
}

@Composable
private fun FormatBar(h: TextEditorHolder, onDone: () -> Unit) {
    var colors by remember { mutableStateOf(false) }
    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
        FmtButton(Icons.Outlined.FormatBold, "Bold") { h.toggleStyle(Typeface.BOLD) }
        FmtButton(Icons.Outlined.FormatItalic, "Italic") { h.toggleStyle(Typeface.ITALIC) }
        FmtButton(Icons.Outlined.FormatUnderlined, "Underline") { h.toggleUnderline() }
        FmtButton(Icons.Outlined.Title, "Heading") { h.toggleHeading() }
        FmtButton(Icons.AutoMirrored.Outlined.FormatListBulleted, "Bullet list") { h.toggleBullet() }
        FmtButton(Icons.Outlined.CheckBox, "Checklist") { h.toggleCheck() }
        Box {
            FmtButton(Icons.Outlined.FormatColorText, "Text colour") { colors = true }
            androidx.compose.material3.DropdownMenu(colors, { colors = false }) {
                Row(Modifier.padding(horizontal = 8.dp)) {
                    listOf(0xFF1C1B1F, 0xFFC0392B, 0xFF2B5BD7, 0xFF0E8A6B, 0xFFE67E22, 0xFF8A3FB8).forEach { c ->
                        ColorDot(c.toInt(), false) { h.setColor(c.toInt()); colors = false }
                    }
                }
            }
        }
        FmtButton(Icons.AutoMirrored.Outlined.FormatAlignLeft, "Alignment") { h.cycleAlign() }
        TextButton(onClick = onDone, modifier = Modifier.testTag("text_done")) { Icon(Icons.Filled.Check, null); Text(stringResource(R.string.done), Modifier.padding(start = 4.dp)) }
    }
}

@Composable
private fun FmtButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.semantics { contentDescription = label }) { Icon(icon, null) }
}

// ---------------- selection actions ----------------
@Composable
fun SelectionBar(onColor: (Int) -> Unit, onToText: () -> Unit, onDuplicate: () -> Unit, onDelete: () -> Unit, onDone: () -> Unit, modifier: Modifier = Modifier) {
    var colors by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(16.dp), color = LocalInk.current.card, shadowElevation = 8.dp, modifier = modifier.testTag("selection_bar")) {
        Column {
            Text(stringResource(R.string.selection_hint), style = MaterialTheme.typography.bodySmall, color = LocalInk.current.muted, modifier = Modifier.padding(start = 14.dp, top = 8.dp, end = 14.dp))
            Row(Modifier.padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box {
                    SelAction(Icons.Outlined.Palette, stringResource(R.string.colour)) { colors = true }
                    androidx.compose.material3.DropdownMenu(colors, { colors = false }) {
                        FlowRowColors { onColor(it); colors = false }
                    }
                }
                SelAction(Icons.Outlined.Translate, stringResource(R.string.to_text), onToText)
                SelAction(Icons.Outlined.ContentCopy, stringResource(R.string.duplicate), onDuplicate)
                SelAction(Icons.Outlined.Delete, stringResource(R.string.delete), onDelete)
                SelAction(Icons.Filled.Check, stringResource(R.string.done), onDone)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowRowColors(pick: (Int) -> Unit) {
    FlowRow(Modifier.width(240.dp).padding(8.dp), maxItemsInEachRow = 5) { PEN_COLORS.take(15).forEach { c -> ColorDot(c, false) { pick(c) } } }
}

@Composable
private fun SelAction(icon: ImageVector, label: String, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).widthIn(min = 56.dp).padding(horizontal = 6.dp, vertical = 6.dp).testTag("sel_$label"),
        horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, null); Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

// ---------------- pen sheet ----------------
data class SavedPen(val brush: Int, val color: Int, val width: Float, val alpha: Float) {
    fun encode() = "$brush:$color:$width:$alpha"
    companion object {
        fun decodeAll(s: String) = s.split(';').mapNotNull { p -> p.split(':').takeIf { it.size == 4 }?.let { runCatching { SavedPen(it[0].toInt(), it[1].toInt(), it[2].toFloat(), it[3].toFloat()) }.getOrNull() } }
        fun encodeAll(l: List<SavedPen>) = l.joinToString(";") { it.encode() }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PenSheet(t: ToolState, saved: List<SavedPen>, onTools: (ToolState) -> Unit, onSavePen: (List<SavedPen>) -> Unit, onDismiss: () -> Unit) {
    val ink = LocalInk.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).testTag("pen_sheet")) {
            Text(stringResource(R.string.pen), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf(Brush.BALL to R.string.brush_ball, Brush.FOUNTAIN to R.string.brush_fountain, Brush.PENCIL to R.string.brush_pencil, Brush.BRUSH to R.string.brush_brush).forEach { (b, l) ->
                    val on = t.brush == b
                    Surface(onClick = { onTools(t.copy(brush = b, tool = EditTool.PEN)) }, shape = RoundedCornerShape(14.dp), color = if (on) MaterialTheme.colorScheme.primaryContainer else ink.surf2, modifier = Modifier.weight(1f)) {
                        Column(Modifier.padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            val c = if (on) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            Canvas(Modifier.size(width = 40.dp, height = 18.dp)) {
                                val w = when (b) { Brush.FOUNTAIN -> 5f; Brush.PENCIL -> 2.5f; Brush.BRUSH -> 9f; else -> 3.5f }
                                drawLine(c, Offset(4f, size.height * 0.7f), Offset(size.width * 0.5f, size.height * 0.3f), w, StrokeCap.Round)
                                drawLine(c, Offset(size.width * 0.5f, size.height * 0.3f), Offset(size.width - 4f, size.height * 0.6f), w * (if (b == Brush.FOUNTAIN || b == Brush.BRUSH) 0.5f else 1f), StrokeCap.Round)
                            }
                            Text(stringResource(l), style = MaterialTheme.typography.labelMedium, color = c, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }
            Text(stringResource(R.string.colour), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            FlowRow(Modifier.padding(horizontal = 12.dp), maxItemsInEachRow = 8) { PEN_COLORS.forEach { c -> ColorDot(c, t.color == c, 30) { onTools(t.copy(color = c)) } } }
            Text(stringResource(R.string.thickness), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 8.dp))
            Slider(t.width, { onTools(t.copy(width = it)) }, valueRange = 1f..30f, modifier = Modifier.padding(horizontal = 20.dp).semantics { contentDescription = "Thickness" })
            Text(stringResource(R.string.opacity), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp))
            Slider(t.alpha, { onTools(t.copy(alpha = it)) }, valueRange = 0.2f..1f, modifier = Modifier.padding(horizontal = 20.dp).semantics { contentDescription = "Opacity" })
            Text(stringResource(R.string.saved_pens), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp))
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                saved.forEach { sp ->
                    Surface(onClick = { onTools(t.copy(tool = EditTool.PEN, brush = sp.brush, color = sp.color, width = sp.width, alpha = sp.alpha)) }, shape = RoundedCornerShape(14.dp), color = ink.surf2, modifier = Modifier.size(52.dp)) {
                        Canvas(Modifier.padding(12.dp)) { drawLine(Color(sp.color), Offset(0f, size.height * 0.7f), Offset(size.width, size.height * 0.3f), (sp.width * 1.2f).coerceIn(2f, 14f), StrokeCap.Round) }
                    }
                }
                if (saved.size < 5) Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).border(BorderStroke(1.5.dp, ink.muted), RoundedCornerShape(14.dp))
                    .clickable(onClickLabel = "Save this pen") { onSavePen(saved + SavedPen(t.brush, t.color, t.width, t.alpha)) }, contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Add, "Save this pen", tint = ink.muted) }
            }
        }
    }
}

// ---------------- insert sheet ----------------
enum class InsertKind { IMAGE, CAMERA, SCAN, VOICE, AUDIO, PDF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsertSheet(onPick: (InsertKind) -> Unit, onDismiss: () -> Unit) {
    val items = listOf(
        Triple(InsertKind.IMAGE, Icons.Outlined.Image, R.string.ins_image to R.string.ins_image_sub),
        Triple(InsertKind.CAMERA, Icons.Outlined.CameraAlt, R.string.ins_camera to R.string.ins_camera_sub),
        Triple(InsertKind.SCAN, Icons.Outlined.DocumentScanner, R.string.ins_scan to R.string.ins_scan_sub),
        Triple(InsertKind.VOICE, Icons.Outlined.Mic, R.string.ins_voice to R.string.ins_voice_sub),
        Triple(InsertKind.AUDIO, Icons.Outlined.AudioFile, R.string.ins_audio to R.string.ins_audio_sub),
        Triple(InsertKind.PDF, Icons.Outlined.PictureAsPdf, R.string.ins_pdf to R.string.ins_pdf_sub),
    )
    val ink = LocalInk.current
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = 16.dp).testTag("insert_sheet")) {
            Text(stringResource(R.string.insert), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
            items.chunked(3).forEach { row ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (k, icon, labels) ->
                        Surface(onClick = { onPick(k) }, shape = RoundedCornerShape(16.dp), color = ink.surf2, modifier = Modifier.weight(1f).testTag("ins_${k.name}")) {
                            Column(Modifier.padding(vertical = 14.dp, horizontal = 4.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                    Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                                }
                                Text(stringResource(labels.first), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(stringResource(labels.second), style = MaterialTheme.typography.bodySmall, color = ink.muted, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
        }
    }
}

