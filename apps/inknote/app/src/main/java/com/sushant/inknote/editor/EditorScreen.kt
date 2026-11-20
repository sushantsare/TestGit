package com.sushant.inknote.editor

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.LockOpen
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.NoteAdd
import androidx.compose.material.icons.outlined.ScreenRotation
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material.icons.outlined.VerticalSplit
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.sushant.inknote.R
import com.sushant.inknote.data.AppSettings
import com.sushant.inknote.data.FolderEntity
import com.sushant.inknote.data.Orientation
import com.sushant.inknote.data.PageContent
import com.sushant.inknote.data.PageEntity
import com.sushant.inknote.data.PageType
import com.sushant.inknote.ink.EditTool
import com.sushant.inknote.ink.InkPageView
import com.sushant.inknote.ink.PagePalette
import com.sushant.inknote.ink.PageListener
import com.sushant.inknote.ink.PageRenderer
import com.sushant.inknote.ink.Selection
import com.sushant.inknote.io.ExportFormat
import com.sushant.inknote.ui.app
import com.sushant.inknote.ui.home.NewNoteSheet
import com.sushant.inknote.ui.theme.LocalInk
import com.sushant.inknote.ui.toast
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

private enum class Sheet { NONE, PEN, INSERT, EXPORT, TAGS, PAPER, RENAME, TRASH, MIC_WHY, MIC_SETTINGS, SETPIN }

@Composable
fun EditorPane(
    vm: EditorViewModel, second: Boolean, canSplit: Boolean, folders: List<FolderEntity>, allTags: List<String>, settings: AppSettings,
    updateSettings: ((AppSettings) -> AppSettings) -> Unit, onBack: () -> Unit, onPages: () -> Unit, onPickBeside: () -> Unit,
    initialPage: Int = -1,
    autoVoice: Boolean = false,
) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val note by vm.note.collectAsState()
    val pages by vm.pages.collectAsState()
    val recs by vm.recordings.collectAsState()
    val rec by vm.recorder.state.collectAsState()
    val recHere = rec?.noteId == vm.noteId
    var sheet by rememberSaveable { mutableStateOf(Sheet.NONE) }
    var menu by remember { mutableStateOf(false) }
    var exportBusy by remember { mutableStateOf(false) }
    var pendingExport by remember { mutableStateOf<Pair<List<File>, ExportFormat>?>(null) }
    val holder = remember { TextEditorHolder() }

    LaunchedEffect(vm) { vm.messages.collect { ctx.toast(it) } }
    if (!second) LaunchedEffect(vm) { com.sushant.inknote.Shortcuts.events.collect { if (it == com.sushant.inknote.Shortcut.UNDO) { vm.finishText(); vm.undo() } else if (it == com.sushant.inknote.Shortcut.REDO) vm.redo() } }
    BackHandler(enabled = vm.editing != null || vm.selection != null) { if (vm.editing != null) { holder.hideKeyboard(); vm.finishText() } else vm.selection = null }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) { vm.finishText(); vm.flush(); vm.pause() } }
        lifecycle.addObserver(obs); onDispose { lifecycle.removeObserver(obs) }
    }
    // typed notes open straight into typing
    val firstLoaded = pages.firstOrNull()?.let { vm.contents[it.id] } != null
    LaunchedEffect(note?.kind, firstLoaded) { if (firstLoaded) vm.openTypedNote() }

    // ---------- launchers ----------
    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { if (it.isNotEmpty()) vm.insertImages(it) }
    var cameraUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> if (ok) cameraUri?.let { vm.insertImages(listOf(it)) } }
    val scanner = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.map { it.imageUri }?.takeIf { it.isNotEmpty() }?.let { vm.insertImages(it) }
    }
    val pickAudio = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { vm.insertAudio(it) } }
    val pickPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { it?.let { vm.insertPdf(it) } }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startRecording() }
    fun startRec() {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            val p = ctx.getSharedPreferences("asked", 0)
            if (!p.getBoolean("notif", false)) { p.edit().putBoolean("notif", true).apply(); notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS); return }
        }
        vm.startRecording()
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRec()
        else if (!(ctx as Activity).shouldShowRequestPermissionRationale(Manifest.permission.RECORD_AUDIO)) sheet = Sheet.MIC_SETTINGS
        else ctx.toast(ctx.getString(R.string.mic_needed))
    }
    val saveOne = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
        val pe = pendingExport; pendingExport = null
        if (uri != null && pe != null) scope.launch { ctx.toast(if (ctx.app().exporter.saveTo(pe.first.first(), uri)) ctx.getString(R.string.saved) else ctx.getString(R.string.save_failed)) }
    }
    val saveMany = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        val pe = pendingExport; pendingExport = null
        if (uri != null && pe != null) scope.launch { val n = ctx.app().exporter.saveToTree(pe.first, uri, pe.second.mime); ctx.toast(ctx.resources.getQuantityString(R.plurals.saved_n, n, n)) }
    }

    fun onInsert(k: InsertKind) {
        sheet = Sheet.NONE
        when (k) {
            InsertKind.IMAGE -> pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            InsertKind.CAMERA -> {
                val f = File(ctx.cacheDir, "export").apply { mkdirs() }.let { File(it, "camera_${System.currentTimeMillis()}.jpg") }
                val u = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f); cameraUri = u
                runCatching { takePhoto.launch(u) }.onFailure { ctx.toast(ctx.getString(R.string.no_camera_app)) }
            }
            InsertKind.SCAN -> {
                val opts = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG)
                    .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
                GmsDocumentScanning.getClient(opts).getStartScanIntent(ctx as Activity)
                    .addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
                    .addOnFailureListener { ctx.toast(ctx.getString(R.string.scan_unavailable)) }
            }
            InsertKind.VOICE -> when {
                recHere -> ctx.toast(ctx.getString(R.string.already_recording))
                ContextCompat.checkSelfPermission(ctx, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startRec()
                else -> sheet = Sheet.MIC_WHY
            }
            InsertKind.AUDIO -> pickAudio.launch(arrayOf("audio/*"))
            InsertKind.PDF -> pickPdf.launch(arrayOf("application/pdf"))
        }
    }

    var voiceDone by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoVoice) { if (autoVoice && !voiceDone) { voiceDone = true; delay(300); onInsert(InsertKind.VOICE) } }

    fun export(fmt: ExportFormat, onlyThis: Boolean, share: Boolean) {
        val n = note ?: return
        exportBusy = true
        scope.launch {
            vm.finishText(); vm.flushNow()
            val files = runCatching { ctx.app().exporter.export(n, fmt, if (onlyThis) pages.getOrNull(vm.currentPage) else null) }.getOrElse { emptyList() }
            exportBusy = false
            if (files.isEmpty()) { ctx.toast(ctx.getString(R.string.export_failed)); return@launch }
            sheet = Sheet.NONE
            if (share) runCatching { ctx.startActivity(ctx.app().exporter.shareIntent(files, fmt)) }
            else { pendingExport = files to fmt; if (files.size == 1) saveOne.launch(files.first().name) else saveMany.launch(null) }
        }
    }

    val n = note
    Column(Modifier.fillMaxSize().background(ink.bg).imePadding().testTag(if (second) "editor2" else "editor")) {
        // ---------- top bar ----------
        Row(Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.finishText(); vm.flush(); onBack() }, Modifier.testTag("editor_back")) {
                Icon(if (second) Icons.Outlined.Close else Icons.AutoMirrored.Outlined.ArrowBack, stringResource(if (second) R.string.close_note else R.string.back))
            }
            Text(n?.title ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable(onClickLabel = stringResource(R.string.rename)) { sheet = Sheet.RENAME }.padding(vertical = 12.dp, horizontal = 4.dp).testTag("editor_title"))
            IconButton(onClick = { vm.finishText(); vm.undo() }, enabled = vm.canUndo, modifier = Modifier.testTag("undo")) { Icon(Icons.AutoMirrored.Outlined.Undo, stringResource(R.string.undo)) }
            IconButton(onClick = { vm.redo() }, enabled = vm.canRedo, modifier = Modifier.testTag("redo")) { Icon(Icons.AutoMirrored.Outlined.Redo, stringResource(R.string.redo)) }
            Box {
                IconButton(onClick = { menu = true }, Modifier.testTag("editor_more")) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.more_options)) }
                DropdownMenu(menu, { menu = false }) {
                    val cur = pages.getOrNull(vm.currentPage)
                    MenuItem(Icons.Outlined.ViewModule, R.string.pages_and_direction, "m_pages") { menu = false; vm.finishText(); vm.flush(); onPages() }
                    if (cur != null && cur.pdfFile == null && n?.pageType != PageType.ENDLESS)
                        MenuItem(Icons.Outlined.ScreenRotation, if (cur.orientation == Orientation.PORTRAIT) R.string.make_horizontal else R.string.make_vertical, "m_turn") { menu = false; vm.turnPage(cur) }
                    MenuItem(Icons.Outlined.NoteAdd, R.string.add_page, "m_add_page") { menu = false; vm.addPage() }
                    if (canSplit && !second) MenuItem(Icons.Outlined.VerticalSplit, R.string.open_beside, "m_beside") { menu = false; onPickBeside() }
                    MenuItem(Icons.Outlined.Translate, R.string.convert_hw, "m_convert") { menu = false; vm.startConvert(cur) }
                    MenuItem(Icons.Outlined.DriveFileMove, R.string.folder_and_tags, "m_tags") { menu = false; sheet = Sheet.TAGS }
                    MenuItem(if (n?.locked == true) Icons.Outlined.LockOpen else Icons.Outlined.Lock, if (n?.locked == true) R.string.unlock_note else R.string.lock_note, "m_lock") {
                        menu = false
                        if (n?.locked != true && !ctx.app().securePrefs.hasPin.value) sheet = Sheet.SETPIN else vm.setLocked(n?.locked != true)
                    }
                    MenuItem(Icons.Outlined.IosShare, R.string.export_share, "m_export") { menu = false; sheet = Sheet.EXPORT }
                    MenuItem(if (n?.favourite == true) Icons.Outlined.Star else Icons.Outlined.StarOutline, if (n?.favourite == true) R.string.remove_favourite else R.string.add_favourite, "m_fav") { menu = false; vm.toggleFavourite() }
                    DropdownMenuItem(text = { Text(stringResource(R.string.stylus_only)) }, onClick = { menu = false; updateSettings { it.copy(stylusOnly = !it.stylusOnly) } },
                        leadingIcon = { Icon(Icons.Filled.Check, null, tint = if (settings.stylusOnly) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent) })
                    HorizontalDivider()
                    MenuItem(Icons.Outlined.Delete, R.string.move_to_trash, "m_trash") { menu = false; sheet = Sheet.TRASH }
                }
            }
        }
        if (n == null) return@Column
        // ---------- recording / playback ----------
        if (recHere) {
            var elapsed by remember { mutableLongStateOf(0L) }
            LaunchedEffect(rec) { while (true) { elapsed = vm.recorder.elapsedMs(); delay(500) } }
            LiveRecordingBar(elapsed) { vm.stopRecording() }
        } else if (recs.isNotEmpty()) {
            PlayerBar(recs, vm.playingRec, vm.isPlaying, vm.positionMs, vm::play, vm::pause, vm::seek, vm::select) { vm.deleteRecording(it) }
        }
        // ---------- pages ----------
        Box(Modifier.weight(1f).fillMaxWidth()) {
            PagesArea(vm, pages, settings, holder, ink.dark, initialPage)
            vm.selection?.let { _ ->
                SelectionBar(onColor = vm::selectionRecolor, onToText = { vm.startConvert(null) }, onDuplicate = vm::selectionDuplicate,
                    onDelete = vm::selectionDelete, onDone = { vm.selection = null }, modifier = Modifier.align(Alignment.BottomCenter).padding(12.dp))
            }
        }
        ToolPanel(vm.tools, vm.editing != null, holder, onTools = { vm.updateTools(it) }, onPenSheet = { sheet = Sheet.PEN }, onInsert = { vm.finishText(); sheet = Sheet.INSERT },
            onDoneText = { holder.hideKeyboard(); vm.finishText() }, recentColors = listOf(0xFF1F3F9A, 0xFF1C1B1F, 0xFFC0392B, 0xFF0E8A6B, 0xFFE67E22).map { it.toInt() }.let { base -> if (vm.tools.color in base) base else listOf(vm.tools.color) + base.take(4) })
    }

    // ---------- sheets & dialogs ----------
    when (sheet) {
        Sheet.PEN -> PenSheet(vm.tools, SavedPen.decodeAll(settings.savedPens), { vm.updateTools(it) }, { l -> updateSettings { it.copy(savedPens = SavedPen.encodeAll(l)) } }) { sheet = Sheet.NONE }
        Sheet.INSERT -> InsertSheet(::onInsert) { sheet = Sheet.NONE }
        Sheet.EXPORT -> ExportSheet(n?.title ?: "", pages.size, exportBusy, { f, o -> export(f, o, false) }, { f, o -> export(f, o, true) }) { sheet = Sheet.NONE }
        Sheet.TAGS -> TagsSheet(folders, n?.folderId, allTags, vm.tags.value, { t, f -> vm.setTagsAndFolder(t, f); sheet = Sheet.NONE }) { sheet = Sheet.NONE }
        Sheet.PAPER -> pages.getOrNull(vm.currentPage)?.let { p -> NewNoteSheet(settings, true, p.paper, { sheet = Sheet.NONE }, { spec -> vm.setPaper(p, spec.paper); sheet = Sheet.NONE }, {}, {}) }
        Sheet.RENAME -> TextInputDialog(stringResource(R.string.rename_note), n?.title ?: "", stringResource(R.string.title), { sheet = Sheet.NONE }) { vm.rename(it); sheet = Sheet.NONE }
        Sheet.TRASH -> AlertDialog(onDismissRequest = { sheet = Sheet.NONE }, title = { Text(stringResource(R.string.trash_q)) }, text = { Text(stringResource(R.string.trash_body)) },
            confirmButton = { TextButton(onClick = { sheet = Sheet.NONE; vm.trash(); onBack() }, modifier = Modifier.testTag("confirm_trash")) { Text(stringResource(R.string.move_to_trash)) } },
            dismissButton = { TextButton(onClick = { sheet = Sheet.NONE }) { Text(stringResource(R.string.cancel)) } })
        Sheet.MIC_WHY -> AlertDialog(onDismissRequest = { sheet = Sheet.NONE }, title = { Text(stringResource(R.string.mic_title)) }, text = { Text(stringResource(R.string.mic_why)) },
            confirmButton = { TextButton(onClick = { sheet = Sheet.NONE; micPerm.launch(Manifest.permission.RECORD_AUDIO) }) { Text(stringResource(R.string.continue_)) } },
            dismissButton = { TextButton(onClick = { sheet = Sheet.NONE }) { Text(stringResource(R.string.not_now)) } })
        Sheet.MIC_SETTINGS -> AlertDialog(onDismissRequest = { sheet = Sheet.NONE }, title = { Text(stringResource(R.string.mic_title)) }, text = { Text(stringResource(R.string.mic_blocked)) },
            confirmButton = { TextButton(onClick = { sheet = Sheet.NONE; ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${ctx.packageName}"))) }) { Text(stringResource(R.string.open_settings)) } },
            dismissButton = { TextButton(onClick = { sheet = Sheet.NONE }) { Text(stringResource(R.string.cancel)) } })
        Sheet.SETPIN -> com.sushant.inknote.ui.lock.SetPinDialog({ sheet = Sheet.NONE }) { sheet = Sheet.NONE; vm.setLocked(true) }
        Sheet.NONE -> Unit
    }
    vm.convert?.let { c -> ConvertSheet(c, vm::downloadModel, { t, r -> vm.applyConvert(t, r) }) { vm.convert = null } }
}

@Composable
private fun MenuItem(icon: androidx.compose.ui.graphics.vector.ImageVector, label: Int, tag: String, onClick: () -> Unit) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = onClick, leadingIcon = { Icon(icon, null) }, modifier = Modifier.testTag(tag))
}

/** Scrollable, zoomable list of pages. Two fingers pan and pinch-zoom; one finger (or stylus) writes. */
@Composable
private fun PagesArea(vm: EditorViewModel, pages: List<PageEntity>, settings: AppSettings, holder: TextEditorHolder, dark: Boolean, initialPage: Int) {
    val listState = rememberLazyListState()
    var jumped by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(initialPage, pages.size) { if (!jumped && initialPage in pages.indices) { listState.scrollToItem(initialPage); jumped = true } }
    val hScroll = rememberScrollState()
    var zoom by rememberSaveable { mutableFloatStateOf(1f) }
    LaunchedEffect(listState) { snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }.collect { (i, off) -> vm.currentPage = if (off > 400) i + 1 else i } }
    BoxWithConstraints(Modifier.fillMaxSize().pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            do {
                val ev = awaitPointerEvent(PointerEventPass.Initial)
                if (ev.changes.count { it.pressed } >= 2) {
                    val z = ev.calculateZoom(); val pan = ev.calculatePan()
                    zoom = (zoom * z).coerceIn(1f, 4f)
                    listState.dispatchRawDelta(-pan.y); hScroll.dispatchRawDelta(-pan.x)
                    ev.changes.forEach { it.consume() }
                }
            } while (ev.changes.any { it.pressed })
        }
    }.testTag("pages_area")) {
        val viewport = maxWidth
        val base = viewport - 24.dp
        val widest = pages.maxOfOrNull { if (it.width > it.height) 1100.dp else 820.dp } ?: 820.dp
        val contentWidth = maxOf(viewport, min(base, widest) * zoom + 24.dp)
        Box(Modifier.fillMaxSize().horizontalScroll(hScroll, enabled = zoom > 1f)) {
            LazyColumn(state = listState, modifier = Modifier.width(contentWidth).fillMaxHeight(), contentPadding = PaddingValues(top = 4.dp, bottom = 200.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                    val maxW = if (p.width > p.height) 1100.dp else 820.dp
                    PageItem(vm, p, i, pages.size, min(base, maxW) * zoom, settings, holder, dark)
                }
            }
        }
    }
}

@Composable
private fun PageItem(vm: EditorViewModel, p: PageEntity, index: Int, count: Int, width: Dp, settings: AppSettings, holder: TextEditorHolder, dark: Boolean) {
    val ctx = LocalContext.current
    val density = LocalDensity.current
    val content: PageContent? = vm.content(p.id)
    var viewRef by remember { mutableStateOf<InkPageView?>(null) }
    val pxPerUnit = with(density) { width.toPx() } / p.width
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(bottom = 14.dp)) {
        Box(Modifier.width(width).aspectRatio(p.width / p.height).shadow(2.dp, RoundedCornerShape(6.dp)).background(if (dark) PagePaletteColors.darkPaper else PagePaletteColors.lightPaper)
            .pointerInput(vm.isPlaying) {
                if (vm.isPlaying) detectTapGestures { off -> viewRef?.strokeAt(off.x, off.y)?.let { vm.seekToStroke(it.recId, it.recMs) } }
            }.testTag("page_$index")) {
            val listener = remember(vm, p.id) {
                object : PageListener {
                    override fun onCommit(pageId: Long, content: PageContent) = vm.commit(pageId, content)
                    override fun onSelect(pageId: Long, sel: Selection?) { vm.selection = sel }
                    override fun onTextTap(pageId: Long, x: Float, y: Float, hitTextId: Long?) = vm.tapText(pageId, x, y, hitTextId)
                    override fun onStylusSeen() { val a = ctx.app(); if (!a.settings.state.value.stylusSeen) { a.settings.update { it.copy(stylusSeen = true, stylusOnly = true) }; ctx.toast(ctx.getString(R.string.stylus_detected)) } }
                    override fun onNeedSpace(pageId: Long) { vm.needSpace(pageId) }
                    override fun stamp(): Pair<Long, Long> = vm.recorder.stamp(vm.noteId)
                }
            }
            AndroidView(factory = { c -> InkPageView(c, vm.files).also { viewRef = it } }, modifier = Modifier.fillMaxSize(), update = { v ->
                v.noteId = vm.noteId
                v.palette = if (dark) PagePalette.DARK else PagePalette.LIGHT
                v.page = p
                if (content != null) v.content = content
                v.tools = vm.tools
                v.stylusOnly = settings.stylusOnly
                v.shapeHold = settings.shapeHold
                v.readOnly = vm.isPlaying || content == null
                v.listener = listener
                v.selection = vm.selection?.takeIf { it.pageId == p.id }
                v.editingTextId = vm.editing?.takeIf { it.pageId == p.id }?.boxId ?: -1
                v.playback = if (vm.isPlaying || vm.positionMs > 0) vm.playingRec?.let { it.id to vm.positionMs } else null
                v.contentDescription = ctx.getString(R.string.page_x_of_y, index + 1, count)
            })
            vm.editing?.takeIf { it.pageId == p.id }?.let { e ->
                key(e.boxId) {
                    val xDp = with(density) { (e.x * pxPerUnit).toDp() }; val yDp = with(density) { (e.y * pxPerUnit).toDp() }
                    val wDp = with(density) { (e.w * pxPerUnit).toDp() }
                    AndroidView(factory = { c -> holder.create(c, e.initial, PageRenderer.TEXT_SIZE * pxPerUnit, dark) { stored, plain -> vm.textChanged(stored, plain) } },
                        modifier = Modifier.offset(xDp, yDp).width(wDp).testTag("text_box"))
                    LaunchedEffect(e.boxId) { delay(120); holder.focus() }
                }
            }
        }
        Text("${index + 1} / $count" + if (p.orientation == Orientation.LANDSCAPE) " · " + stringResource(R.string.horizontal) else "",
            style = MaterialTheme.typography.bodySmall, color = LocalInk.current.muted, modifier = Modifier.padding(top = 4.dp))
    }
}

private object PagePaletteColors {
    val lightPaper = androidx.compose.ui.graphics.Color.White
    val darkPaper = androidx.compose.ui.graphics.Color(0xFF1E2024)
}

