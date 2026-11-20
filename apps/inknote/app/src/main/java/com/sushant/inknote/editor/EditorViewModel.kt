package com.sushant.inknote.editor

import android.app.Application
import android.media.MediaPlayer
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.createSavedStateHandle
import com.sushant.inknote.audio.Recorder
import com.sushant.inknote.data.Brush
import com.sushant.inknote.data.Ids
import com.sushant.inknote.data.Kind
import com.sushant.inknote.data.NoteEntity
import com.sushant.inknote.data.PageContent
import com.sushant.inknote.data.PageEntity
import com.sushant.inknote.data.PageType
import com.sushant.inknote.data.RecordingEntity
import com.sushant.inknote.data.TextBox
import com.sushant.inknote.ink.EditTool
import com.sushant.inknote.ink.PageRenderer
import com.sushant.inknote.ink.RichText
import com.sushant.inknote.ink.Selection
import com.sushant.inknote.ink.ToolState
import com.sushant.inknote.ml.Handwriting
import com.sushant.inknote.ui.ImageImport
import com.sushant.inknote.ui.app
import com.sushant.inknote.work.IndexWorker
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class EditingText(val pageId: Long, val boxId: Long, val x: Float, val y: Float, val w: Float, val initial: String)
data class Edit(val pageId: Long, val before: PageContent, val after: PageContent)
data class ConvertResult(val text: String?, val needsDownload: Boolean, val busy: Boolean)

class EditorViewModel(app: Application, private val saved: SavedStateHandle, val noteId: Long) : AndroidViewModel(app) {
    private val inkApp = app.app()
    val repo = inkApp.repo
    val files = inkApp.files
    val recorder = inkApp.recorder

    val note: StateFlow<NoteEntity?> = repo.observeNote(noteId).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val pages: StateFlow<List<PageEntity>> = repo.observePages(noteId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recordings: StateFlow<List<RecordingEntity>> = repo.observeRecordings(noteId).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val tags = mutableStateOf<List<String>>(emptyList())

    val contents = mutableStateMapOf<Long, PageContent>()
    private val loading = HashSet<Long>()

    var tools by mutableStateOf(ToolState(tool = EditTool.valueOf(saved["tool"] ?: EditTool.PEN.name), color = saved["color"] ?: 0xFF1F3F9A.toInt(),
        width = saved["width"] ?: 4f, brush = saved["brush"] ?: Brush.BALL))
        private set
    var selection by mutableStateOf<Selection?>(null)
    var editing by mutableStateOf<EditingText?>(null)
        private set
    var convert by mutableStateOf<ConvertResult?>(null)
    var currentPage by mutableStateOf(0)

    private val undo = ArrayDeque<Edit>(); private val redo = ArrayDeque<Edit>()
    var canUndo by mutableStateOf(false)
        private set
    var canRedo by mutableStateOf(false)
        private set

    private val _msg = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages: SharedFlow<String> = _msg

    private val saveJobs = HashMap<Long, Job>()

    // ---- playback ----
    var playingRec by mutableStateOf<RecordingEntity?>(null)
        private set
    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableStateOf(0L)
        private set
    private var player: MediaPlayer? = null
    private var tick: Job? = null

    init {
        viewModelScope.launch { tags.value = repo.tagsFor(noteId) }
        viewModelScope.launch { recordings.collectLatest { list -> if (playingRec != null && list.none { it.id == playingRec?.id }) stopPlayer() } }
    }

    fun msg(s: String) { _msg.tryEmit(s) }

    // ================= content =================
    fun content(pageId: Long): PageContent? {
        contents[pageId]?.let { return it }
        if (loading.add(pageId)) viewModelScope.launch { contents[pageId] = repo.readPage(noteId, pageId); loading.remove(pageId) }
        return null
    }

    fun updateTools(t: ToolState) {
        if (t.tool != tools.tool) { selection = null; if (t.tool != EditTool.TEXT) finishText() }
        tools = t
        saved["tool"] = t.tool.name; saved["color"] = t.color; saved["width"] = t.width; saved["brush"] = t.brush
    }

    fun commit(pageId: Long, newContent: PageContent, recordUndo: Boolean = true) {
        val before = contents[pageId] ?: PageContent()
        if (before === newContent) return
        contents[pageId] = newContent
        if (recordUndo) { undo.addLast(Edit(pageId, before, newContent)); if (undo.size > 150) undo.removeFirst(); redo.clear() }
        canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        scheduleSave(pageId)
    }

    private fun scheduleSave(pageId: Long) {
        saveJobs[pageId]?.cancel()
        saveJobs[pageId] = viewModelScope.launch {
            delay(400)
            contents[pageId]?.let { repo.savePage(noteId, pageId, it) }
            IndexWorker.schedule(getApplication(), 6)
        }
    }

    fun undo() { val e = undo.removeLastOrNull() ?: return; redo.addLast(e); contents[e.pageId] = e.before; selection = null; canUndo = undo.isNotEmpty(); canRedo = true; scheduleSave(e.pageId) }
    fun redo() { val e = redo.removeLastOrNull() ?: return; undo.addLast(e); contents[e.pageId] = e.after; selection = null; canUndo = true; canRedo = redo.isNotEmpty(); scheduleSave(e.pageId) }

    fun flush() {
        val ids = saveJobs.keys.toList()
        for (id in ids) { saveJobs[id]?.cancel(); contents[id]?.let { c -> inkApp.appScope.launch { repo.savePage(noteId, id, c) } } }
        saveJobs.clear()
    }

    override fun onCleared() { finishText(); flush(); player?.release(); player = null; super.onCleared() }

    // ================= endless page =================
    fun needSpace(pageId: Long) = viewModelScope.launch {
        val n = note.value ?: return@launch
        if (n.pageType != PageType.ENDLESS) return@launch
        val p = repo.page(pageId) ?: return@launch
        repo.updatePage(p.copy(height = p.height + 700f))
    }

    // ================= selection actions =================
    fun selectionRecolor(color: Int) = selected { c, s -> c.copy(strokes = c.strokes.map { if (it.id in s.strokes) it.copy(color = color) else it }) }
    fun selectionDelete() { selected { c, s -> PageContent(c.strokes.filter { it.id !in s.strokes }, c.texts.filter { it.id !in s.texts }, c.images.filter { it.id !in s.images }) }; selection = null }
    fun selectionDuplicate() {
        val s = selection ?: return; val c = contents[s.pageId] ?: return
        val off = 30f
        val ns = c.strokes.filter { it.id in s.strokes }.map { it.transformed(1f, 1f, off, off).copy(id = Ids.next()) }
        val nt = c.texts.filter { it.id in s.texts }.map { it.copy(id = Ids.next(), x = it.x + off, y = it.y + off) }
        val ni = c.images.filter { it.id in s.images }.map { it.copy(id = Ids.next(), x = it.x + off, y = it.y + off) }
        commit(s.pageId, PageContent(c.strokes + ns, c.texts + nt, c.images + ni))
        val b = s.bounds
        selection = Selection(s.pageId, ns.map { it.id }.toSet(), nt.map { it.id }.toSet(), ni.map { it.id }.toSet(), floatArrayOf(b[0] + off, b[1] + off, b[2] + off, b[3] + off))
    }

    private fun selected(f: (PageContent, Selection) -> PageContent) {
        val s = selection ?: return; val c = contents[s.pageId] ?: return
        commit(s.pageId, f(c, s))
    }

    /** Converts the selected handwriting (or the whole current page) to text. */
    fun startConvert(wholePage: PageEntity?) = viewModelScope.launch {
        convert = ConvertResult(null, false, true)
        if (!Handwriting.isDownloaded()) { convert = ConvertResult(null, true, false); return@launch }
        val strokes = selection?.let { s -> contents[s.pageId]?.strokes?.filter { it.id in s.strokes } }
            ?: wholePage?.let { p -> (contents[p.id] ?: repo.readPage(noteId, p.id)).also { contents[p.id] = it }.strokes } ?: emptyList()
        val text = Handwriting.recognize(strokes)
        convert = ConvertResult(text ?: "", false, false)
        if (selection == null && wholePage != null && strokes.isNotEmpty()) {
            val all = strokes.map { it.bounds() }
            selection = Selection(wholePage.id, strokes.map { it.id }.toSet(), emptySet(), emptySet(),
                floatArrayOf(all.minOf { it[0] }, all.minOf { it[1] }, all.maxOf { it[2] }, all.maxOf { it[3] }))
        }
    }

    fun downloadModel() = viewModelScope.launch {
        convert = ConvertResult(null, true, true)
        val err = Handwriting.download()
        if (err != null) { msg(err); convert = ConvertResult(null, true, false) } else { msg("Handwriting pack ready"); startConvert(pages.value.getOrNull(currentPage)) }
    }

    fun applyConvert(text: String, replace: Boolean) {
        val s = selection ?: run { convert = null; return }
        val c = contents[s.pageId] ?: return
        val p = pages.value.firstOrNull { it.id == s.pageId } ?: return
        val b = s.bounds
        val w = (b[2] - b[0]).coerceAtLeast(300f).coerceAtMost(p.width - b[0] - 20f)
        val stored = RichText.fromPlain(text)
        val y = if (replace) b[1] else b[3] + 16f
        val box = TextBox(Ids.next(), b[0], y, w, PageRenderer.textHeight(stored, w), stored, text)
        val strokes = if (replace) c.strokes.filter { it.id !in s.strokes } else c.strokes
        commit(s.pageId, c.copy(strokes = strokes, texts = c.texts + box))
        selection = null; convert = null
    }

    // ================= text =================
    fun tapText(pageId: Long, x: Float, y: Float, hit: Long?) {
        finishText()
        val p = pages.value.firstOrNull { it.id == pageId } ?: return
        val c = contents[pageId] ?: return
        val box = hit?.let { id -> c.texts.firstOrNull { it.id == id } }
        editing = if (box != null) EditingText(pageId, box.id, box.x, box.y, box.w, box.html)
        else {
            val w = (p.width - x - 40f).coerceAtLeast(240f).coerceAtMost(p.width - 40f)
            EditingText(pageId, Ids.next(), x.coerceAtMost(p.width - w - 20f).coerceAtLeast(20f), y, w, RichText.fromPlain(""))
        }
    }

    /** Live update while typing (no undo step per key). */
    private var pendingText: Pair<String, String>? = null
    fun textChanged(stored: String, plain: String) { pendingText = stored to plain }

    fun finishText() {
        val e = editing ?: return
        editing = null
        val (stored, plain) = pendingText ?: (e.initial to RichText.plain(e.initial))
        pendingText = null
        val c = contents[e.pageId] ?: return
        val exists = c.texts.any { it.id == e.boxId }
        val texts = when {
            plain.isBlank() -> c.texts.filter { it.id != e.boxId }
            exists -> c.texts.map { if (it.id == e.boxId) it.copy(html = stored, plain = plain, h = PageRenderer.textHeight(stored, it.w)) else it }
            else -> c.texts + TextBox(e.boxId, e.x, e.y, e.w, PageRenderer.textHeight(stored, e.w), stored, plain)
        }
        if (texts != c.texts) commit(e.pageId, c.copy(texts = texts))
    }

    /** Typed notes open straight into their text box. */
    fun openTypedNote() {
        val n = note.value ?: return
        if (n.kind != Kind.TEXT || editing != null) return
        val p = pages.value.firstOrNull() ?: return
        val c = contents[p.id] ?: return
        updateTools(tools.copy(tool = EditTool.TEXT))
        val box = c.texts.firstOrNull()
        editing = if (box != null) EditingText(p.id, box.id, box.x, box.y, box.w, box.html)
        else EditingText(p.id, Ids.next(), 60f, 60f, p.width - 120f, RichText.fromPlain(""))
    }

    // ================= pages =================
    fun addPage() = viewModelScope.launch { repo.addPage(noteId, pages.value.getOrNull(currentPage)?.idx ?: (pages.value.size - 1)); msg("Page added") }
    fun turnPage(p: PageEntity) = viewModelScope.launch {
        flush()
        if (p.pdfFile != null) { msg("PDF pages keep their own direction"); return@launch }
        if (note.value?.pageType == PageType.ENDLESS) { msg("A long page can't be turned"); return@launch }
        repo.turnPage(noteId, p.id); contents[p.id] = repo.readPage(noteId, p.id); selection = null
        undo.removeAll { it.pageId == p.id }; redo.removeAll { it.pageId == p.id }; canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        msg(if (p.orientation == com.sushant.inknote.data.Orientation.PORTRAIT) "Page turned to horizontal" else "Page turned to vertical")
    }
    fun setPaper(p: PageEntity, paper: String) = viewModelScope.launch { repo.setPaper(p.id, paper) }
    fun duplicatePage(p: PageEntity) = viewModelScope.launch { flush(); repo.duplicatePage(noteId, p.id); msg("Page duplicated") }
    fun deletePage(p: PageEntity) = viewModelScope.launch {
        if (!repo.deletePage(noteId, p.id)) msg("A note needs at least one page") else { contents.remove(p.id); undo.removeAll { it.pageId == p.id }; redo.removeAll { it.pageId == p.id }; canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty(); msg("Page deleted") }
    }
    fun movePage(from: Int, to: Int) = viewModelScope.launch { repo.movePage(noteId, from, to) }

    // ================= note =================
    fun rename(t: String) = viewModelScope.launch { repo.rename(noteId, t) }
    fun toggleFavourite() = viewModelScope.launch { note.value?.let { repo.setFavourite(noteId, !it.favourite); msg(if (it.favourite) "Removed from favourites" else "Added to favourites") } }
    fun setLocked(v: Boolean) = viewModelScope.launch { flush(); repo.setLocked(listOf(noteId), v); msg(if (v) "Note locked" else "Note unlocked") }
    fun setTagsAndFolder(t: List<String>, folder: Long?) = viewModelScope.launch { repo.setTags(noteId, t); repo.moveToFolder(listOf(noteId), folder); tags.value = repo.tagsFor(noteId) }
    fun trash() = viewModelScope.launch { flush(); repo.moveToTrash(listOf(noteId)) }

    // ================= inserts =================
    private fun currentPageEntity() = pages.value.getOrNull(currentPage) ?: pages.value.firstOrNull()

    fun insertImages(uris: List<Uri>) = viewModelScope.launch {
        val p = currentPageEntity() ?: return@launch
        flush()
        val n = ImageImport.addImages(getApplication(), repo, noteId, p.id, uris)
        pages.value.forEach { pg -> contents[pg.id] = repo.readPage(noteId, pg.id) }
        repo.pages(noteId).forEach { pg -> contents[pg.id] = repo.readPage(noteId, pg.id) }
        undo.clear(); redo.clear(); canUndo = false; canRedo = false
        msg(if (n == 0) "Couldn't add that picture" else if (n == 1) "Picture added" else "$n pictures added")
    }

    fun insertPdf(uri: Uri) = viewModelScope.launch {
        val p = currentPageEntity() ?: return@launch
        runCatching { repo.insertPdfPages(noteId, p.idx, uri) }.onSuccess { msg("$it PDF pages added") }.onFailure { msg("Couldn't open that PDF") }
    }

    fun insertAudio(uri: Uri) = viewModelScope.launch { msg(if (Recorder.importAudio(getApplication(), repo, noteId, uri)) "Audio added" else "Couldn't add that audio file") }

    fun startRecording() = viewModelScope.launch {
        stopPlayer()
        if (recorder.state.value != null) { msg("Already recording"); return@launch }
        if (!recorder.start(noteId)) msg("Couldn't start recording. Is another app using the microphone?")
    }

    fun stopRecording() { recorder.stop(); msg("Recording saved · synced to your writing") }

    // ================= playback =================
    fun play(r: RecordingEntity) {
        if (playingRec?.id == r.id && player != null) { player?.start(); isPlaying = true; startTick(); return }
        stopPlayer()
        val f = files.audio(noteId, r.file)
        val mp = MediaPlayer()
        try { mp.setDataSource(f.absolutePath); mp.prepare() } catch (e: Exception) { mp.release(); msg("Can't play this recording"); return }
        mp.setOnCompletionListener { isPlaying = false; positionMs = r.durationMs.coerceAtLeast(mp.duration.toLong()) }
        player = mp; playingRec = r; mp.start(); isPlaying = true; startTick()
    }
    fun select(r: RecordingEntity) { stopPlayer(); playingRec = r; positionMs = 0 }
    fun pause() { player?.pause(); isPlaying = false }
    fun seek(ms: Long) { val r = playingRec ?: return; if (player == null) play(r); player?.seekTo(ms.toInt()); positionMs = ms }
    fun seekToStroke(recId: Long, ms: Long) { val r = recordings.value.firstOrNull { it.id == recId } ?: return; if (playingRec?.id != r.id) play(r); seek(ms); if (!isPlaying) { player?.start(); isPlaying = true; startTick() } }
    fun stopPlayer() { tick?.cancel(); player?.release(); player = null; isPlaying = false }
    fun deleteRecording(r: RecordingEntity) = viewModelScope.launch { if (playingRec?.id == r.id) { stopPlayer(); playingRec = null }; repo.deleteRecording(r); msg("Recording deleted") }
    private fun startTick() { tick?.cancel(); tick = viewModelScope.launch { while (isActive && isPlaying) { positionMs = player?.currentPosition?.toLong() ?: positionMs; delay(100) } } }

    companion object {
        fun factory(noteId: Long) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                val app = extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]!!
                return EditorViewModel(app, extras.createSavedStateHandle(), noteId) as T
            }
        }
    }
}
