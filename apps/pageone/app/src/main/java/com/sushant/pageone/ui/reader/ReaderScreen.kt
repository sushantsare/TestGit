package com.sushant.pageone.ui.reader

import android.app.Activity
import android.app.Application
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import com.sushant.pageone.KeyBus
import com.sushant.pageone.data.DocRef
import com.sushant.pageone.data.Format
import com.sushant.pageone.data.Outputs
import com.sushant.pageone.data.PageMode
import com.sushant.pageone.pdf.Annot
import com.sushant.pageone.ui.MainVm
import com.sushant.pageone.ui.Routes
import com.sushant.pageone.ui.components.EmptyState
import com.sushant.pageone.ui.components.EmptyType
import com.sushant.pageone.ui.components.ExpressiveKind
import com.sushant.pageone.ui.screens.ExpressiveSpinner
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderScreen(main: MainVm, nav: NavHostController, ref: DocRef) {
    val context = LocalContext.current
    val app = context.applicationContext as Application
    val vm: ReaderVm = viewModel(key = "reader:" + ref.uri, initializer = { ReaderVm(app, ref) })
    val s by main.settings.collectAsState()
    val stars by main.lib.stars.collectAsState()
    val history by main.lib.searchHistory.collectAsState()
    val bookmarks by vm.bookmarks.collectAsState()
    val scope = rememberCoroutineScope()
    val doc = vm.doc
    val title = vm.ref.name.removeSuffix(".pdf").ifEmpty { "PDF" }
    val pages = doc?.pageCount ?: 0
    var confirmDelete by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var noteText by remember { mutableStateOf("") }

    // keep screen on, lock rotation, volume keys
    val view = LocalView.current
    DisposableEffect(s.keepScreenOn) { view.keepScreenOn = s.keepScreenOn; onDispose { view.keepScreenOn = false } }
    DisposableEffect(vm.lockRotation) {
        val act = context as? Activity
        act?.requestedOrientation = if (vm.lockRotation) ActivityInfo.SCREEN_ORIENTATION_LOCKED else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(Unit) { KeyBus.listening = true; onDispose { KeyBus.listening = false } }
    LaunchedEffect(vm.speechMessage) { vm.speechMessage?.let { main.message(it); vm.speechMessage = null } }

    BackHandler {
        when {
            vm.sheet != Sheet.NONE -> vm.sheet = Sheet.NONE
            vm.selection != null -> vm.selection = null
            vm.mode == Mode.FIND -> vm.closeFind()
            vm.mode == Mode.ANNOTATE -> if (vm.hasUnsaved) vm.showSave = true else vm.mode = Mode.READ
            vm.hasUnsaved -> vm.showSave = true
            else -> nav.popBackStack()
        }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHigh).testTag("reader")) {
        when (val st = vm.status) {
            Status.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { ExpressiveSpinner() }
            is Status.Failed -> EmptyState(EmptyType.FIRST_TIME, "Can’t open this PDF", "The file looks damaged or isn’t a real PDF. Try downloading it again.",
                Modifier.align(Alignment.Center), icon = Icons.Rounded.ErrorOutline, kind = ExpressiveKind.FLOWER,
                container = MaterialTheme.colorScheme.errorContainer, content = MaterialTheme.colorScheme.onErrorContainer,
                actionLabel = "Go back", onAction = { nav.popBackStack() }, secondLabel = "Open with…", onSecond = { runCatching { Outputs.openWith(context, vm.ref) } })
            is Status.NeedPassword -> PasswordDialog(st.wrong, vm.ref.name, onOpen = { vm.open(it) }, onCancel = { nav.popBackStack() })
            Status.Ready -> if (doc != null) Body(vm, main, doc, s.pageMode, s.cropMargins, s.pageByPage, s.twoPages)
        }

        // ---------- top
        AnimatedVisibility(vm.mode == Mode.FIND, enter = fadeIn() + slideInVertically { -it / 3 }, exit = fadeOut()) { FindBar(vm, history) }
        AnimatedVisibility(vm.mode == Mode.ANNOTATE, enter = fadeIn(), exit = fadeOut()) {
            ReaderTop(title, "Editing · ${vm.page + 1} of $pages", onBack = { if (vm.hasUnsaved) vm.showSave = true else vm.mode = Mode.READ }) {
                CircleButton(Icons.AutoMirrored.Rounded.Redo, "Redo", { vm.redo() }, "tool_redo")
            }
        }
        AnimatedVisibility(vm.mode == Mode.READ && (vm.chrome || vm.status !is Status.Ready), enter = fadeIn() + slideInVertically { -it / 4 }, exit = fadeOut() + slideOutVertically { -it / 4 }) {
            ReaderTop(title, if (pages > 0) "${vm.page + 1} of $pages" else "", onBack = { if (vm.hasUnsaved) vm.showSave = true else nav.popBackStack() }) {
                if (doc != null) SplitPill(onSearch = { vm.mode = Mode.FIND; vm.selection = null }) { open, close ->
                    ReaderMenu(open, close, vm, main, nav, starred = stars.any { it.fingerprint == vm.fingerprint },
                        onRename = { renaming = true }, onDetails = { details = true }, onDelete = { confirmDelete = true })
                }
            }
        }

        // ---------- bottom
        val bottomMod = Modifier.align(Alignment.BottomStart).navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 24.dp)
        if (doc != null && vm.status == Status.Ready) {
            when {
                vm.mode == Mode.ANNOTATE -> AnnotateBar(vm, bottomMod.fillMaxWidth()) { if (vm.hasUnsaved) vm.showSave = true else vm.mode = Mode.READ }
                vm.speech.active -> ReadAloudCard(vm, bottomMod, onMessage = { main.message(it) }) { p ->
                    if (p in 0 until doc.pageCount) { vm.goTo?.invoke(p); vm.speakFrom(p) }
                }
                vm.mode == Mode.READ -> {
                    AnimatedVisibility(vm.chrome, modifier = bottomMod, enter = fadeIn() + slideInVertically { it / 3 }, exit = fadeOut() + slideOutVertically { it / 3 }) {
                        Box(Modifier.fillMaxWidth()) {
                            FloatingTools(Modifier.align(Alignment.CenterStart), s.pageMode == PageMode.NIGHT, vm.page in bookmarks,
                                onPages = { vm.sheet = Sheet.PAGES }, onOptions = { vm.sheet = Sheet.OPTIONS },
                                onNight = { main.update { it.copy(pageMode = if (it.pageMode == PageMode.NIGHT) PageMode.NORMAL else PageMode.NIGHT) } },
                                onSpeak = { vm.speakFrom(vm.page) },
                                onBookmark = { val had = vm.page in bookmarks; vm.toggleBookmark(); main.message(if (had) "Bookmark removed" else "Page ${vm.page + 1} bookmarked") })
                            PenFab(Modifier.align(Alignment.CenterEnd)) {
                                if (vm.rotation != 0) main.message("Turn pages upright to edit") else { vm.mode = Mode.ANNOTATE; vm.selection = null; vm.chrome = true }
                            }
                        }
                    }
                    AnimatedVisibility(!vm.chrome, modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp), enter = fadeIn(), exit = fadeOut()) {
                        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface) {
                            Text("${vm.page + 1} / $pages", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp).testTag("page_pill"))
                        }
                    }
                }
            }
        }
    }

    // ---------- sheets
    if (vm.sheet != Sheet.NONE && doc != null) {
        ModalBottomSheet(onDismissRequest = { vm.sheet = Sheet.NONE }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            when (vm.sheet) {
                Sheet.PAGES, Sheet.CONTENTS, Sheet.BOOKMARKS -> PagesSheet(vm, doc, bookmarks) { p -> vm.sheet = Sheet.NONE; vm.goTo?.invoke(p) }
                Sheet.OPTIONS -> OptionsSheet(vm, s, { main.update(it) }, doc)
                Sheet.RESULTS -> ResultsSheet(vm) { i -> vm.hitIndex = i; vm.sheet = Sheet.NONE }
                Sheet.NONE -> {}
            }
        }
    }

    // ---------- dialogs
    if (vm.showSave) AlertDialog(
        onDismissRequest = { if (!vm.saving) vm.showSave = false },
        icon = { Icon(Icons.Rounded.Save, null) },
        title = { Text("Save your changes?") },
        text = { Text("You added ${if (vm.history.all.isEmpty()) "form entries" else vm.history.summary()} to ${vm.ref.name}.") },
        confirmButton = {
            Row {
                TextButton(onClick = { vm.discard() }, enabled = !vm.saving, modifier = Modifier.testTag("save_discard")) { Text("Discard") }
                TextButton(onClick = { vm.save(asCopy = true) { main.message(it) } }, enabled = !vm.saving, modifier = Modifier.testTag("save_copy")) { Text("Save as copy") }
                TextButton(onClick = { vm.save(asCopy = false) { main.message(it) } }, enabled = !vm.saving, modifier = Modifier.testTag("save_save")) { Text("Save") }
            }
        },
    )
    vm.noteAt?.let { (p, at) ->
        AlertDialog(
            onDismissRequest = { vm.noteAt = null },
            title = { Text("Add a note") },
            text = { OutlinedTextField(noteText, { noteText = it }, minLines = 3, modifier = Modifier.fillMaxWidth().testTag("note_field")) },
            confirmButton = { TextButton(onClick = { if (noteText.isNotBlank()) vm.addAnnot(Annot.Note(p, at, noteText.trim())); noteText = ""; vm.noteAt = null },
                modifier = Modifier.testTag("note_add")) { Text("Add") } },
            dismissButton = { TextButton(onClick = { noteText = ""; vm.noteAt = null }) { Text("Cancel") } },
        )
    }
    if (renaming) {
        var name by remember { mutableStateOf(vm.ref.name.removeSuffix(".pdf")) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            title = { Text("Rename") },
            text = { OutlinedTextField(name, { name = it }, singleLine = true, suffix = { Text(".pdf") }, modifier = Modifier.testTag("rename_field")) },
            confirmButton = { TextButton(onClick = {
                val path = vm.ref.path ?: return@TextButton
                val old = File(path); val target = File(old.parentFile, Format.fileName(name))
                renaming = false
                if (target.exists() && target != old) main.message("A file with that name already exists")
                else if (old.renameTo(target)) {
                    android.media.MediaScannerConnection.scanFile(context, arrayOf(old.path, target.path), null, null)
                    vm.renamed(target.path); main.lib.scan(force = true); main.message("Renamed")
                } else main.message("Couldn’t rename this file")
            }, enabled = name.isNotBlank(), modifier = Modifier.testTag("rename_ok")) { Text("Rename") } },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel") } },
        )
    }
    if (details && doc != null) AlertDialog(
        onDismissRequest = { details = false },
        title = { Text("Details") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                val f = vm.ref.path?.let { File(it) }
                Detail("Name", vm.ref.name)
                Detail("Pages", "${doc.pageCount}")
                Detail("Size", Format.size(f?.length() ?: doc.sourceFile.length()))
                Detail("Location", f?.parent ?: "Shared from another app")
                if (f != null) Detail("Modified", Format.shortDate(f.lastModified()) + ", " + Format.time(f.lastModified()))
                Detail("Protected", if (doc.password != null) "Yes, password" else "No")
            }
        },
        confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } },
    )
    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        icon = { Icon(Icons.Rounded.Delete, null) },
        title = { Text("Delete this PDF?") },
        text = { Text("${vm.ref.name} will be removed from your phone. This can't be undone.") },
        confirmButton = { TextButton(onClick = {
            confirmDelete = false
            val path = vm.ref.path ?: return@TextButton
            if (File(path).delete()) {
                android.media.MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                scope.launch { main.lib.removeRecent(vm.fingerprint) }
                main.lib.scan(force = true); main.message("Deleted"); nav.popBackStack()
            } else main.message("Couldn’t delete this file")
        }, modifier = Modifier.testTag("confirm_delete")) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Detail(k: String, v: String) {
    Column { Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(v, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun ReaderMenu(open: Boolean, close: () -> Unit, vm: ReaderVm, main: MainVm, nav: NavHostController, starred: Boolean,
                       onRename: () -> Unit, onDetails: () -> Unit, onDelete: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val doc = vm.doc ?: return
    val isFile = vm.ref.path != null
    DropdownMenu(open, close, modifier = Modifier.testTag("reader_menu_list")) {
        @Composable fun item(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tag: String, action: () -> Unit) =
            DropdownMenuItem({ Text(label) }, { close(); action() }, leadingIcon = { Icon(icon, null) }, modifier = Modifier.testTag(tag))
        item(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (starred) "Remove star" else "Star", "m_star") { main.toggleStar(vm.ref, vm.fingerprint, doc.pageCount) }
        item(Icons.Rounded.Share, "Share", "m_share") { Outputs.share(context, vm.ref) }
        item(Icons.Rounded.OpenInNew, "Open with", "m_open_with") { runCatching { Outputs.openWith(context, vm.ref) } }
        item(Icons.Rounded.Print, "Print", "m_print") { printFile(context, vm.ref.name, doc.sourceFile, doc.pageCount) }
        item(Icons.Rounded.Image, "Save page as image", "m_save_image") {
            scope.launch {
                val bmp = doc.render(vm.page, 2000)
                if (bmp == null) { main.message("Couldn’t save the page"); return@launch }
                runCatching { Outputs.saveImage(context, bmp, vm.ref.name.removeSuffix(".pdf") + " p${vm.page + 1}.png") }
                    .onSuccess { main.message("Page ${vm.page + 1} saved to Pictures/PageOne") }.onFailure { main.message("Couldn’t save the page") }
            }
        }
        HorizontalDivider()
        val seed = vm.ref.path ?: vm.ref.uri
        item(Icons.Rounded.MergeType, "Merge with PDFs & images…", "m_merge") { nav.navigate(Routes.merge(seed)) }
        item(Icons.Rounded.ContentCut, "Split or extract pages", "m_split") { nav.navigate(Routes.split(if (isFile) seed else vm.ref.uri)) }
        item(Icons.Rounded.Compress, "Compress", "m_compress") { nav.navigate(Routes.compress(if (isFile) seed else vm.ref.uri)) }
        HorizontalDivider()
        if (isFile) item(Icons.Rounded.Edit, "Rename", "m_rename") { onRename() }
        item(Icons.Rounded.Info, "Details", "m_details") { onDetails() }
        if (isFile) item(Icons.Rounded.Delete, "Delete", "m_delete") { onDelete() }
    }
}

/** The page area: scroll or page-by-page, pinch zoom, reflow, fast-scroll handle. */
@Composable
private fun BoxScope.Body(vm: ReaderVm, main: MainVm, doc: com.sushant.pageone.pdf.PdfDoc, pageMode: PageMode, crop: Boolean, paged: Boolean, twoPages: Boolean) {
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var view by remember { mutableStateOf(IntOffset(1, 1)) }
    var boxInRoot by remember { mutableStateOf(Offset.Zero) }
    val list = rememberLazyListState()
    val pager = rememberPagerState { doc.pageCount }
    val wide = with(density) { view.x.toDp() } >= 840.dp && view.x > view.y
    val perRow = if (twoPages && wide && !paged) 2 else 1      // one page per screen unless asked for
    val gap = with(density) { 10.dp.roundToPx() }
    // sideways (and, page by page, up/down) position of zoomed pages
    var ox by remember { mutableFloatStateOf(0f) }
    var oy by remember { mutableFloatStateOf(0f) }
    var zoomAnim by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun maxOx(z: Float = vm.zoom) = (view.x * z - view.x).coerceAtLeast(0f)
    /** The zoom has stopped moving: draw the pages sharp at it and remember it. */
    fun settle() {
        vm.renderZoom = vm.zoom
        vm.savePosition(if (paged) pager.currentPage else vm.page)
    }

    /**
     * Zoom to [target] keeping the bit of page that was under [from] under [to] (both in this box).
     * Pages are laid out for real at the new size in the same frame, so nothing jumps when the fingers lift.
     */
    fun applyZoom(target: Float, from: Offset, to: Offset) {
        val old = vm.zoom
        val nz = target.coerceIn(1f, 5f)
        if (paged) {
            val i = pager.currentPage
            val g0 = pagedGeo(doc, vm, i, view.x, view.y, old, ox, oy)
            val fx = (from.x - g0.x) / g0.w; val fy = (from.y - g0.y) / g0.h
            val g1 = pagedGeo(doc, vm, i, view.x, view.y, nz, 0f, 0f)
            ox = (fx * g1.w - to.x).coerceIn(0f, (g1.w - view.x).coerceAtLeast(0f))
            oy = (fy * g1.h - to.y).coerceIn(0f, (g1.h - view.y).coerceAtLeast(0f))
            vm.zoom = nz
            return
        }
        val k = nz / old
        ox = ((ox + from.x) * k - to.x).coerceIn(0f, maxOx(nz))
        val info = list.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isNotEmpty()) {
            val base = -info.viewportStartOffset                     // screen y of an item = offset + base
            val it = items.firstOrNull { from.y >= it.offset + base && from.y < it.offset + base + it.size }
                ?: items.minBy { kotlin.math.abs(it.offset + base + it.size / 2f - from.y) }
            val rows = (0 until doc.pageCount).chunked(perRow)
            fun rowH(r: Int, z: Float) = rowHeightPx(doc, vm, rows[r], view.x, z, gap)
            val frac = (from.y - (it.offset + base)) / it.size
            val newSize = it.size * rowH(it.index, nz) / rowH(it.index, old)
            val newTop = to.y - frac * newSize
            // scroll offset 0 puts the first visible item where it sits now minus its current offset
            val first = items.firstOrNull { i -> i.index == list.firstVisibleItemIndex } ?: items.first()
            val c = first.offset + base + (if (first.index == list.firstVisibleItemIndex) list.firstVisibleItemScrollOffset else 0)
            var idx = it.index
            var off = c - newTop
            while (off < 0 && idx > 0) { idx--; off += rowH(idx, nz) + gap }
            list.requestScrollToItem(idx, off.coerceAtLeast(0f).roundToInt())
        }
        vm.zoom = nz
    }

    fun go(p: Int) = scope.launch {
        if (paged) { ox = 0f; oy = 0f; pager.scrollToPage(p) } else list.scrollToItem(p / perRow)
        vm.savePosition(p)
    }
    SideEffect { vm.goTo = { p -> go(p) } }

    // restore where you stopped
    LaunchedEffect(vm.restore, view.x > 1) {
        val r = vm.restore ?: return@LaunchedEffect
        if (view.x <= 1) return@LaunchedEffect
        vm.zoom = r.second; vm.renderZoom = r.second; ox = 0f; oy = 0f
        delay(30)
        if (paged) pager.scrollToPage(r.first) else list.scrollToItem(r.first / perRow)
        vm.restore = null
    }
    // a new page in page-by-page mode starts at its top-left
    LaunchedEffect(paged) { if (paged) snapshotFlow { pager.currentPage }.distinctUntilChanged().collect { ox = 0f; oy = 0f } }
    // remember the page as you read
    LaunchedEffect(paged, perRow) {
        if (paged) snapshotFlow { pager.currentPage }.distinctUntilChanged().collectLatest { delay(400); vm.savePosition(it) }
        else snapshotFlow {
            val info = list.layoutInfo
            val first = info.visibleItemsInfo.firstOrNull()
            if (first == null) list.firstVisibleItemIndex * perRow
            else {
                val mid = info.viewportEndOffset / 3
                val item = info.visibleItemsInfo.firstOrNull { it.offset + it.size > mid } ?: first
                item.index * perRow
            }
        }.distinctUntilChanged().collectLatest { p -> vm.page = p.coerceIn(0, doc.pageCount - 1); delay(400); vm.savePosition(vm.page) }
    }
    // jump to the current search match
    LaunchedEffect(vm.hitIndex) { vm.hits.getOrNull(vm.hitIndex)?.let { if (it.page != vm.page) go(it.page) } }
    // volume keys
    LaunchedEffect(Unit) { KeyBus.events.collect { d -> go((vm.page + d).coerceIn(0, doc.pageCount - 1)) } }
    // read aloud follows the pages
    SideEffect {
        vm.speech.onPageDone = {
            val next = vm.speech.page + 1
            if (next < doc.pageCount) { go(next); vm.speakFrom(next) } else vm.speech.stop()
        }
    }
    // double-tap: fit → closer → closer still → back to fit, around the tapped spot
    vm.doubleTap = { root ->
        val p = root - boxInRoot
        val levels = listOf(1f, 2f, 3.5f)
        val target = levels.firstOrNull { it > vm.zoom + 0.05f } ?: 1f
        zoomAnim?.cancel()
        zoomAnim = scope.launch {
            val start = vm.zoom
            androidx.compose.animation.core.animate(0f, 1f, animationSpec = androidx.compose.animation.core.tween(260)) { t, _ ->
                applyZoom(start + (target - start) * t, p, p)
            }
            settle()
        }
    }

    Box(
        Modifier.fillMaxSize().testTag("reader_area")
            .onSizeChanged { view = IntOffset(it.width, it.height) }
            .onGloballyPositioned { boxInRoot = it.positionInRoot() }
            .zoomGestures(vm,
                onZoom = { z, from, to -> zoomAnim?.cancel(); applyZoom(vm.zoom * z, from, to) },
                onEnd = { settle() })
            // one finger moves zoomed pages sideways (scroll mode) — up/down stays with the page list and its fling
            .scrollable(
                rememberScrollableState { d -> val o = ox; ox = (ox - d).coerceIn(0f, maxOx()); o - ox },
                Orientation.Horizontal,
                enabled = !paged && vm.zoom > 1.01f && vm.mode != Mode.ANNOTATE,
            )
            // page by page, a zoomed page pans freely
            .pointerInput(paged, vm.zoom > 1.01f, vm.mode) {
                if (paged && vm.zoom > 1.01f && vm.mode != Mode.ANNOTATE) detectDragGestures { ch, d ->
                    ch.consume()
                    val g = pagedGeo(doc, vm, pager.currentPage, view.x, view.y, vm.zoom, 0f, 0f)
                    ox = (ox - d.x).coerceIn(0f, (g.w - view.x).coerceAtLeast(0f))
                    oy = (oy - d.y).coerceIn(0f, (g.h - view.y).coerceAtLeast(0f))
                }
            },
    ) {
        when {
            vm.reflow -> Reflow(vm, doc, pageMode)
            paged -> PagedPages(vm, doc, pager, view.x, view.y, ox, oy, pageMode, crop)
            else -> ScrollPages(vm, doc, list, ox, view.x, pageMode, crop, spread = perRow == 2)
        }
    }
    if (!paged && !vm.reflow && doc.pageCount > 3) FastScroll(vm, doc.pageCount, list, perRow)
}

/** Drag handle on the right edge with a page bubble. */
@Composable
private fun BoxScope.FastScroll(vm: ReaderVm, count: Int, list: androidx.compose.foundation.lazy.LazyListState, perRow: Int) {
    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var trackH by remember { mutableIntStateOf(1) }
    val frac = if (count <= 1) 0f else vm.page / (count - 1f)
    val visible = vm.chrome || dragging || list.isScrollInProgress
    AnimatedVisibility(visible, modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight().padding(top = 120.dp, bottom = 130.dp), enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxHeight().width(44.dp).onSizeChanged { trackH = it.height }.testTag("fast_scroll")
            .pointerInput(count) {
                detectVerticalDragGestures(onDragStart = { dragging = true }, onDragEnd = { dragging = false }, onDragCancel = { dragging = false }) { ch, _ ->
                    ch.consume()
                    val p = ((ch.position.y / trackH).coerceIn(0f, 1f) * (count - 1)).roundToInt()
                    vm.page = p
                    scope.launch { list.scrollToItem(p / perRow) }
                }
            }) {
            val y = with(LocalDensity.current) { ((trackH - 56.dp.toPx()) * frac).toDp() }
            Box(Modifier.align(Alignment.TopEnd).offset(y = y).padding(end = 6.dp).size(if (dragging) 10.dp else 6.dp, 56.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.primary))
            if (dragging) Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.inverseSurface, contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                modifier = Modifier.align(Alignment.TopEnd).offset(x = (-24).dp, y = y + 14.dp)) {
                Text("${vm.page + 1}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** Reflow: the page text re-flowed to fit the phone. Scanned pages have no text. */
@Composable
private fun Reflow(vm: ReaderVm, doc: com.sushant.pageone.pdf.PdfDoc, pageMode: PageMode) {
    val bg = when (pageMode) { PageMode.NIGHT -> androidx.compose.ui.graphics.Color(0xFF121212); PageMode.SEPIA -> androidx.compose.ui.graphics.Color(0xFFF6ECD6); else -> androidx.compose.ui.graphics.Color.White }
    val fg = if (pageMode == PageMode.NIGHT) androidx.compose.ui.graphics.Color(0xFFE6E6E6) else androidx.compose.ui.graphics.Color(0xFF1F1F1F)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = vm.page)
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().background(bg).testTag("reflow"), contentPadding = PaddingValues(top = 110.dp, bottom = 120.dp, start = 22.dp, end = 22.dp)) {
        items(doc.pageCount) { i ->
            val text by produceState<String?>(null, i) { value = doc.pageText(i) }
            Column(Modifier.padding(bottom = 22.dp)) {
                Text("Page ${i + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(6.dp))
                Text(text?.ifBlank { "No text on this page (it may be a scanned image)." } ?: "…", style = MaterialTheme.typography.bodyLarge.copy(lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.15f), color = fg)
            }
        }
    }
}
