package com.sushant.pageone.ui.reader

import android.content.Context
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Article
import androidx.compose.material.icons.automirrored.rounded.List
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.pageone.data.PageMode
import com.sushant.pageone.data.Settings
import com.sushant.pageone.pdf.PdfDoc
import com.sushant.pageone.pdf.Tool
import com.sushant.pageone.ui.components.ConnectedButtons
import com.sushant.pageone.ui.components.Group
import com.sushant.pageone.ui.components.SettingNav
import com.sushant.pageone.ui.components.SettingSwitch
import com.sushant.pageone.ui.theme.PageOneCorners
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

private val floatColor @Composable get() = MaterialTheme.colorScheme.surfaceContainerHighest

/** Floating back button in its own circle. */
@Composable
fun CircleButton(icon: ImageVector, description: String, onClick: () -> Unit, tag: String) {
    Surface(onClick = onClick, shape = CircleShape, color = floatColor, shadowElevation = 3.dp, modifier = Modifier.size(52.dp).testTag(tag)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description) }
    }
}

/** Reader top: back circle · plain title and page · split pill (search | ⋮). */
@Composable
fun ReaderTop(title: String, subtitle: String, onBack: () -> Unit, right: @Composable () -> Unit) {
    val c = MaterialTheme.colorScheme
    Box(Modifier.fillMaxWidth().background(Brush.verticalGradient(0f to c.surfaceContainerHigh, 0.55f to c.surfaceContainerHigh.copy(alpha = 0.92f), 1f to Color.Transparent))
        .statusBarsPadding().padding(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 22.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CircleButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back", onBack, "reader_back")
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.testTag("reader_title"))
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant, modifier = Modifier.testTag("reader_page"))
            }
            right()
        }
    }
}

@Composable
fun SplitPill(onSearch: () -> Unit, menu: @Composable (Boolean, () -> Unit) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Surface(onClick = onSearch, shape = PageOneCorners.splitLeft, color = floatColor, shadowElevation = 3.dp, modifier = Modifier.size(52.dp).testTag("reader_find")) {
            Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Search, "Find in PDF") }
        }
        Box {
            Surface(onClick = { open = true }, shape = PageOneCorners.splitRight, color = floatColor, shadowElevation = 3.dp, modifier = Modifier.size(52.dp).testTag("reader_menu")) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.MoreVert, "More options") }
            }
            menu(open) { open = false }
        }
    }
}

/** Bottom floating toolbar (M3 Expressive): pages · reading options · night · read aloud · bookmark. */
@Composable
fun FloatingTools(modifier: Modifier, night: Boolean, bookmarked: Boolean, onPages: () -> Unit, onOptions: () -> Unit, onNight: () -> Unit, onSpeak: () -> Unit, onBookmark: () -> Unit) {
    Surface(shape = RoundedCornerShape(32.dp), color = floatColor, shadowElevation = 6.dp, modifier = modifier.height(64.dp).testTag("floating_toolbar")) {
        Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            ToolIcon(Icons.Rounded.GridView, "Pages, contents and bookmarks", false, onPages, "tb_pages")
            ToolIcon(Icons.AutoMirrored.Rounded.MenuBook, "Reading options", false, onOptions, "tb_options")
            ToolIcon(if (night) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, if (night) "Normal pages" else "Night mode", night, onNight, "tb_night")
            ToolIcon(Icons.Rounded.RecordVoiceOver, "Read aloud", false, onSpeak, "tb_speak")
            ToolIcon(if (bookmarked) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkAdd, if (bookmarked) "Remove bookmark" else "Bookmark this page", bookmarked, onBookmark, "tb_bookmark")
        }
    }
}

@Composable
fun ToolIcon(icon: ImageVector, description: String, on: Boolean, onClick: () -> Unit, tag: String) {
    IconButton(onClick = onClick, modifier = Modifier.testTag(tag), colors = if (on) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors()) {
        Icon(icon, description)
    }
}

@Composable
fun PenFab(modifier: Modifier, onClick: () -> Unit) {
    Surface(onClick = onClick, shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp, modifier = modifier.size(64.dp).testTag("pen_fab")) {
        Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Edit, "Edit and annotate", Modifier.size(26.dp)) }
    }
}

val HIGHLIGHTS = listOf(0x99F7E26B.toInt(), 0x999BE7A8.toInt(), 0x998EC5FF.toInt(), 0x99F9A8C9.toInt(), 0x99FFB36B.toInt())
val INKS = listOf(0xFFB3261E.toInt(), 0xFF1A1A1A.toInt(), 0xFF1D4ED8.toInt(), 0xFF2E6B3F.toInt(), 0xFF7A3EB1.toInt())

@Composable
fun AnnotateBar(vm: ReaderVm, modifier: Modifier, onDone: () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (vm.tool != Tool.ERASER && vm.tool != Tool.NOTE) {
            val pen = vm.tool == Tool.PEN || vm.tool == Tool.UNDERLINE
            val colors = if (pen) INKS else HIGHLIGHTS
            Surface(shape = RoundedCornerShape(26.dp), color = floatColor, shadowElevation = 3.dp, modifier = Modifier.height(52.dp)) {
                Row(Modifier.padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                    colors.forEachIndexed { i, c ->
                        val sel = if (pen) vm.penColor == c else vm.color == c
                        Box(Modifier.size(30.dp).then(if (sel) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape).padding(5.dp) else Modifier)
                            .clip(CircleShape).background(Color(c).copy(alpha = 1f)).clickable { if (pen) vm.penColor = c else vm.color = c }.testTag("color_$i"))
                    }
                }
            }
        }
        Surface(shape = RoundedCornerShape(32.dp), color = floatColor, shadowElevation = 6.dp, modifier = Modifier.fillMaxWidth().height(64.dp).testTag("annotate_bar")) {
            Row(Modifier.padding(horizontal = 6.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                ToolIcon(Icons.Rounded.BorderColor, "Highlight", vm.tool == Tool.HIGHLIGHT, { vm.tool = Tool.HIGHLIGHT }, "tool_highlight")
                ToolIcon(Icons.Rounded.FormatUnderlined, "Underline", vm.tool == Tool.UNDERLINE, { vm.tool = Tool.UNDERLINE }, "tool_underline")
                ToolIcon(Icons.Rounded.Draw, "Pen", vm.tool == Tool.PEN, { vm.tool = Tool.PEN }, "tool_pen")
                ToolIcon(Icons.Rounded.CleaningServices, "Eraser", vm.tool == Tool.ERASER, { vm.tool = Tool.ERASER }, "tool_eraser")
                ToolIcon(Icons.Rounded.EditNote, "Note", vm.tool == Tool.NOTE, { vm.tool = Tool.NOTE }, "tool_note")
                ToolIcon(Icons.AutoMirrored.Rounded.Undo, "Undo", false, { vm.undo() }, "tool_undo")
                Surface(onClick = onDone, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(52.dp, 48.dp).testTag("annotate_done")) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Check, "Done") } }
            }
        }
    }
}

@Composable
fun SelectionPopup(vm: ReaderVm, modifier: Modifier) {
    val context = LocalContext.current
    var more by remember { mutableStateOf(false) }
    Surface(shape = RoundedCornerShape(24.dp), color = floatColor, shadowElevation = 6.dp, modifier = modifier.height(48.dp).testTag("selection_popup")) {
        Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            PopupItem("Copy", "sel_copy") {
                val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                cm.setPrimaryClip(android.content.ClipData.newPlainText("PDF text", vm.selection?.text ?: ""))
                vm.selection = null
            }
            PopupItem("Highlight", "sel_highlight") { vm.markSelection(false) }
            PopupItem("Underline", "sel_underline") { vm.markSelection(true) }
            PopupItem("Share", "sel_share") { com.sushant.pageone.data.Outputs.shareText(context, vm.selection?.text ?: ""); vm.selection = null }
            Box {
                IconButton(onClick = { more = true }, modifier = Modifier.size(40.dp)) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(more, { more = false }) {
                    DropdownMenuItem({ Text("Read aloud from here") }, {
                        more = false
                        val s = vm.selection ?: return@DropdownMenuItem
                        vm.speakFrom(s.page, s.text)
                        vm.selection = null
                    }, leadingIcon = { Icon(Icons.Rounded.RecordVoiceOver, null) })
                }
            }
        }
    }
}

@Composable
private fun PopupItem(label: String, tag: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.testTag(tag), contentPadding = PaddingValues(horizontal = 10.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

/** Find bar: field with "3 of 12", next/previous, and options. */
@Composable
fun FindBar(vm: ReaderVm, history: List<String>) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Column(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerHigh).statusBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp).testTag("find_bar")) {
        Surface(shape = RoundedCornerShape(28.dp), color = floatColor, shadowElevation = 2.dp, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Row(Modifier.padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.closeFind() }, modifier = Modifier.testTag("find_close")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Close find") }
                Box(Modifier.weight(1f)) {
                    if (vm.query.isEmpty()) Text("Find in this PDF", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(vm.query, { vm.query = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { vm.runSearch() }), modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("find_field"))
                }
                if (vm.hits.isNotEmpty() || vm.searching) Text(if (vm.hits.isEmpty()) "…" else "${vm.hitIndex + 1} of ${vm.hits.size}${if (vm.searching) "+" else ""}",
                    style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 4.dp).testTag("find_count"))
                else if (vm.query.isNotBlank() && !vm.searching && vm.searchDone) Text("No matches", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                IconButton(onClick = { vm.nextHit(-1) }, enabled = vm.hits.isNotEmpty()) { Icon(Icons.Rounded.KeyboardArrowUp, "Previous match") }
                IconButton(onClick = { vm.nextHit(1) }, enabled = vm.hits.isNotEmpty(), modifier = Modifier.testTag("find_next")) { Icon(Icons.Rounded.KeyboardArrowDown, "Next match") }
            }
        }
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilterChip(vm.wholeWord, { vm.wholeWord = !vm.wholeWord; vm.runSearch() }, { Text("Whole word") }, modifier = Modifier.testTag("find_whole"))
            FilterChip(vm.matchCase, { vm.matchCase = !vm.matchCase; vm.runSearch() }, { Text("Match case") }, modifier = Modifier.testTag("find_case"))
            Spacer(Modifier.weight(1f))
            AssistChip({ vm.sheet = Sheet.RESULTS }, { Text("All results") }, leadingIcon = { Icon(Icons.AutoMirrored.Rounded.List, null, Modifier.size(18.dp)) },
                modifier = Modifier.testTag("find_all"))
        }
        if (vm.query.isEmpty() && history.isNotEmpty()) Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(Icons.Rounded.History, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            history.take(4).forEach { h -> SuggestionChip({ vm.query = h; vm.runSearch() }, { Text(h, maxLines = 1) }) }
        }
    }
}

/** Sheet with segmented Pages | Contents | Bookmarks. */
@Composable
fun PagesSheet(vm: ReaderVm, doc: PdfDoc, bookmarks: List<Int>, onGo: (Int) -> Unit) {
    val tab = when (vm.sheet) { Sheet.CONTENTS -> 1; Sheet.BOOKMARKS -> 2; else -> 0 }
    Column(Modifier.fillMaxWidth().heightIn(min = 420.dp).padding(bottom = 16.dp)) {
        ConnectedButtons(listOf("Pages", "Contents", "Bookmarks"), tab, { vm.sheet = listOf(Sheet.PAGES, Sheet.CONTENTS, Sheet.BOOKMARKS)[it] },
            Modifier.padding(horizontal = 24.dp).fillMaxWidth(), height = 40.dp, fill = true, tagPrefix = "sheet_tab")
        Spacer(Modifier.height(14.dp))
        when (tab) {
            0 -> {
                var goto by remember { mutableStateOf("") }
                val n = goto.toIntOrNull()
                Row(Modifier.padding(horizontal = 24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(goto, { goto = it.filter { c -> c.isDigit() }.take(5) }, singleLine = true, placeholder = { Text("Go to page (1–${doc.pageCount})") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { if (n != null && n in 1..doc.pageCount) onGo(n - 1) }),
                        isError = goto.isNotEmpty() && (n == null || n !in 1..doc.pageCount), modifier = Modifier.weight(1f).testTag("goto_field"))
                    Button(onClick = { if (n != null && n in 1..doc.pageCount) onGo(n - 1) }, enabled = n != null && n in 1..doc.pageCount, modifier = Modifier.height(52.dp).testTag("goto_go")) { Text("Go") }
                }
                Spacer(Modifier.height(14.dp))
                LazyVerticalGrid(GridCells.Adaptive(100.dp), Modifier.heightIn(max = 520.dp).padding(horizontal = 20.dp).testTag("thumb_grid"),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    items((0 until doc.pageCount).toList(), key = { it }) { i -> Thumb(doc, i, i == vm.page) { onGo(i) } }
                }
            }
            1 -> {
                val o = vm.outline
                when {
                    o == null -> Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    o.isEmpty() -> Text("This PDF has no table of contents.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(24.dp))
                    else -> {
                        val currentIdx = o.indexOfLast { it.page in 0..vm.page }
                        LazyColumn(Modifier.heightIn(max = 520.dp).testTag("toc")) {
                            itemsIndexed(o) { i, it ->
                                val on = i == currentIdx
                                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp).clip(RoundedCornerShape(28.dp))
                                    .background(if (on) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                                    .clickable(enabled = it.page >= 0) { onGo(it.page) }.padding(start = 12.dp + 16.dp * it.level, end = 16.dp, top = 14.dp, bottom = 14.dp)) {
                                    Text(it.title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        color = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface)
                                    if (it.page >= 0) Text("${it.page + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }
            else -> {
                if (bookmarks.isEmpty()) Text("No bookmarks yet. Tap the bookmark button in the toolbar to save a page.", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(24.dp))
                else LazyColumn(Modifier.heightIn(max = 520.dp).testTag("bookmark_list")) {
                    items(bookmarks) { p ->
                        Row(Modifier.fillMaxWidth().clickable { onGo(p) }.padding(horizontal = 24.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Bookmark, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(16.dp))
                            Text("Page ${p + 1}", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            IconButton(onClick = { vm.removeBookmark(p) }) { Icon(Icons.Rounded.Close, "Remove bookmark") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Thumb(doc: PdfDoc, i: Int, current: Boolean, onClick: () -> Unit) {
    val bmp by produceState<android.graphics.Bitmap?>(null, i) { value = doc.render(i, 220) }
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick).testTag("thumb_$i")) {
        val sz = doc.sizes[i]
        Box(Modifier.fillMaxWidth().aspectRatio(sz.width / sz.height).shadow(1.dp, RoundedCornerShape(10.dp)).clip(RoundedCornerShape(10.dp)).background(Color.White)
            .then(if (current) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)) {
            bmp?.let { Image(it.asImageBitmap(), null, Modifier.fillMaxSize()) }
        }
        Text("${i + 1}", style = MaterialTheme.typography.bodySmall, fontWeight = if (current) FontWeight.Bold else FontWeight.Normal,
            color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(top = 5.dp))
    }
}

@Composable
fun ResultsSheet(vm: ReaderVm, onGo: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(if (vm.hits.size == 1) "1 result for “${vm.query.trim()}”" else "${vm.hits.size} results for “${vm.query.trim()}”", style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        LazyColumn(Modifier.heightIn(max = 520.dp).testTag("results_list")) {
            itemsIndexed(vm.hits) { i, h ->
                Row(Modifier.fillMaxWidth().clickable { onGo(i) }.padding(horizontal = 24.dp, vertical = 10.dp)) {
                    Text("p.${h.page + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.width(52.dp))
                    Text(com.sushant.pageone.ui.screens.highlight(h.snippet.ifEmpty { "Page ${h.page + 1}" }, vm.query.trim()), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
fun OptionsSheet(vm: ReaderVm, s: Settings, update: ((Settings) -> Settings) -> Unit, doc: PdfDoc) {
    Column(Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
        Text("Reading options", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(Triple(PageMode.NORMAL, Icons.Rounded.LightMode, "Normal"), Triple(PageMode.NIGHT, Icons.Rounded.DarkMode, "Night"), Triple(PageMode.SEPIA, Icons.Rounded.Coffee, "Sepia"))
                .forEach { (m, ic, l) ->
                    val on = s.pageMode == m
                    Surface(onClick = { update { it.copy(pageMode = m) } }, shape = RoundedCornerShape(20.dp),
                        color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.weight(1f).testTag("mode_${m.name.lowercase()}")) {
                        Column(Modifier.padding(vertical = 14.dp), horizontalAlignment = Alignment.CenterHorizontally) { Icon(ic, null); Spacer(Modifier.height(6.dp)); Text(l, style = MaterialTheme.typography.labelMedium) }
                    }
                }
        }
        Spacer(Modifier.height(14.dp))
        ConnectedButtons(listOf("Scroll", "Page by page"), if (s.pageByPage) 1 else 0, { i -> update { it.copy(pageByPage = i == 1) } },
            Modifier.padding(horizontal = 24.dp).fillMaxWidth(), height = 40.dp, fill = true, tagPrefix = "scroll_mode")
        Spacer(Modifier.height(14.dp))
        val wideScreen = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
        val n = if (wideScreen) 7 else 6
        Group {
            SettingSwitch(Icons.AutoMirrored.Rounded.Article, "Reflow text", if (doc.hasRichText) "Fit text to the screen" else "Fit text to the screen (text PDFs)", vm.reflow, { vm.reflow = it }, 0, n, "opt_reflow")
            SettingSwitch(Icons.Rounded.Crop, "Crop white margins", null, s.cropMargins, { v -> update { it.copy(cropMargins = v) } }, 1, n, "opt_crop")
            if (wideScreen) SettingSwitch(Icons.Rounded.AutoStories, "Two pages side by side", "When the screen is wide", s.twoPages, { v -> update { it.copy(twoPages = v) } }, 2, n, "opt_two_pages")
            val o = if (wideScreen) 1 else 0
            SettingNav(Icons.Rounded.Rotate90DegreesCw, "Rotate pages", if (vm.rotation == 0) "Upright" else "${vm.rotation}°", { vm.rotation = (vm.rotation + 90) % 360; vm.selection = null }, 2 + o, n, "opt_rotate", chevron = false)
            SettingSwitch(Icons.Rounded.ScreenLockRotation, "Lock rotation", null, vm.lockRotation, { vm.lockRotation = it }, 3 + o, n, "opt_lock_rotation")
            SettingSwitch(Icons.Rounded.LightMode, "Keep screen on", null, s.keepScreenOn, { v -> update { it.copy(keepScreenOn = v) } }, 4 + o, n, "opt_screen_on")
            SettingSwitch(Icons.AutoMirrored.Rounded.VolumeUp, "Volume keys turn pages", null, s.volumeKeys, { v -> update { it.copy(volumeKeys = v) } }, 5 + o, n, "opt_volume")
        }
    }
}

@Composable
fun ReadAloudCard(vm: ReaderVm, modifier: Modifier, onMessage: (String) -> Unit, onPage: (Int) -> Unit) {
    val sp = vm.speech
    Surface(shape = RoundedCornerShape(32.dp), color = floatColor, shadowElevation = 6.dp, modifier = modifier.fillMaxWidth().testTag("read_aloud")) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.RecordVoiceOver, null, tint = MaterialTheme.colorScheme.primary); Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("Reading aloud · page ${sp.page + 1}", style = MaterialTheme.typography.titleSmall)
                    Text("${sp.voiceName} · ${sp.rate}×", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = { sp.stop() }, modifier = Modifier.testTag("speak_close")) { Icon(Icons.Rounded.Close, "Stop reading") }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { sp.cycleRate() }) { Icon(Icons.Rounded.Speed, "Speed") }
                IconButton(onClick = { onPage(sp.page - 1) }, enabled = sp.page > 0) { Icon(Icons.Rounded.SkipPrevious, "Previous page") }
                Surface(onClick = { sp.toggle() }, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(64.dp).testTag("speak_toggle")) {
                    Box(contentAlignment = Alignment.Center) { Icon(if (sp.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (sp.playing) "Pause" else "Play", Modifier.size(30.dp)) }
                }
                IconButton(onClick = { onPage(sp.page + 1) }) { Icon(Icons.Rounded.SkipNext, "Next page") }
                IconButton(onClick = { onMessage(sp.cycleSleep()) }) { Icon(if (sp.sleepAt != null) Icons.Rounded.TimerOff else Icons.Rounded.Timer, "Sleep timer") }
            }
        }
    }
}

@Composable
fun PasswordDialog(wrong: Boolean, name: String, onOpen: (String) -> Unit, onCancel: () -> Unit) {
    var pw by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onCancel,
        icon = { Icon(Icons.Rounded.Lock, null) },
        title = { Text("This PDF is protected") },
        text = {
            Column(Modifier.semantics { testTagsAsResourceId = true }) {
                Text("Enter the password to open $name.")
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(pw, { pw = it }, singleLine = true, label = { Text("Password") }, isError = wrong,
                    visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { if (pw.isNotEmpty()) onOpen(pw) }),
                    trailingIcon = { IconButton(onClick = { show = !show }) { Icon(if (show) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (show) "Hide password" else "Show password") } },
                    supportingText = { Text(if (wrong) "Password incorrect. Try again." else "The password is not saved") },
                    modifier = Modifier.fillMaxWidth().testTag("password_field"))
            }
        },
        confirmButton = { TextButton(onClick = { onOpen(pw) }, enabled = pw.isNotEmpty(), modifier = Modifier.semantics { testTagsAsResourceId = true }.testTag("password_open")) { Text("Open") } },
        dismissButton = { TextButton(onClick = onCancel) { Text("Cancel") } },
    )
}

/** Hands the file to Android's print service (no Cloud Print). */
fun printFile(context: Context, name: String, file: File, pages: Int) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    pm.print(name, object : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, cb: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) { cb.onLayoutCancelled(); return }
            cb.onLayoutFinished(PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).setPageCount(pages).build(), true)
        }
        override fun onWrite(range: Array<out PageRange>?, dest: ParcelFileDescriptor, cancel: CancellationSignal?, cb: WriteResultCallback) {
            runCatching {
                FileInputStream(file).use { i -> FileOutputStream(dest.fileDescriptor).use { o -> i.copyTo(o) } }
                cb.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            }.onFailure { cb.onWriteFailed(it.message) }
        }
    }, null)
}
