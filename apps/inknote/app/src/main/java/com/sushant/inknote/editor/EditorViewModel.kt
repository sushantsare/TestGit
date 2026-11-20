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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
    private val _pages = kotlinx.coroutines.flow.MutableStateFlow<List<PageEntity>>(emptyList())
    /** Pages of the note: follows the database and is also reloaded right after every page change. */
    val pages: StateFlow<List<PageEntity>> = _pages
    private fun reloadPages() = viewModelScope.launch { _pages.value = repo.pages(noteId) }
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
        viewModelScope.launch { repo.observePages(noteId).collect { _pages.value = it } }
        viewModelScope.launch { tags.value = repo.tagsFor(noteId) }
        viewModelScope.launch { recordings.collectLatest { list -> if (playingRec != null && list.none { it.id == playingRec?.id }) stopPlayer() } }
    }

    fun msg(s: String) { _msg.tryEmit(s) }
    private fun str(id: Int, vararg a: Any) = getApplication<Application>().getString(id, *a)
    private fun qty(id: Int, n: Int) = getApplication<Application>().resources.getQuantityString(id, n, n)

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
            contents[pageId]?.let { save(pageId, it) }
            IndexWorker.schedule(getApplication(), 6)
        }
    }

    fun undo() { val e = undo.removeLastOrNull() ?: return; redo.addLast(e); contents[e.pageId] = e.before; selection = null; canUndo = undo.isNotEmpty(); canRedo = true; scheduleSave(e.pageId) }
    fun redo() { val e = redo.removeLastOrNull() ?: return; undo.addLast(e); contents[e.pageId] = e.after; selection = null; canUndo = true; canRedo = redo.isNotEmpty(); scheduleSave(e.pageId) }

    fun flush() {
        val ids = saveJobs.keys.toList()
        for (id in ids) { saveJobs[id]?.cancel(); contents[id]?.let { c -> inkApp.appScope.launch { runCatching { repo.savePage(noteId, id, c) } } } }
        saveJobs.clear()
    }

    /** Saves pending page edits and waits until they are on disk. */
    suspend fun flushNow() {
        val ids = saveJobs.keys.toList()
        ids.forEach { saveJobs[it]?.cancel() }; saveJobs.clear()
        for (id in ids) contents[id]?.let { save(id, it) }
    }

    /** Writes a page; if the phone is out of space the edit stays in memory and the user is told in plain words. */
    private suspend fun save(pageId: Long, c: PageContent): Boolean = try { repo.savePage(noteId, pageId, c); true } catch (e: Exception) {
        if (e is kotlinx.coroutines.CancellationException) throw e
        msg(str(com.sushant.inknote.R.string.msg_save_failed)); false
    }

    override fun onCleared() { finishText(); flush(); player?.release(); player = null; super.onCleared() }

    // ================= endless page =================
    fun needSpace(pageId: Long) = viewModelScope.launch {
        val n = note.value ?: return@launch
        if (n.pageType != PageType.ENDLESS) return@launch
        val p = repo.page(pageId) ?: return@launch
        repo.updatePage(p.copy(height = p.height + 700f)); reloadPages()
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
        if (err != null) { msg(err); convert = ConvertResult(null, true, false) } else { msg(str(com.sushant.inknote.R.string.msg_pack_ready)); startConvert(pages.value.getOrNull(currentPage)) }
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
    fun addPage() = viewModelScope.launch { repo.addPage(noteId, pages.value.getOrNull(currentPage)?.idx ?: (pages.value.size - 1)); _pages.value = repo.pages(noteId); msg(str(com.sushant.inknote.R.string.msg_page_added)) }
    fun turnPage(p: PageEntity) = viewModelScope.launch {
        flushNow()
        if (p.pdfFile != null) { msg(str(com.sushant.inknote.R.string.msg_pdf_pages_fixed)); return@launch }
        if (note.value?.pageType == PageType.ENDLESS) { msg(str(com.sushant.inknote.R.string.msg_long_page_fixed)); return@launch }
        repo.turnPage(noteId, p.id); _pages.value = repo.pages(noteId); contents[p.id] = repo.readPage(noteId, p.id); selection = null
        undo.removeAll { it.pageId == p.id }; redo.removeAll { it.pageId == p.id }; canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty()
        msg(str(if (p.orientation == com.sushant.inknote.data.Orientation.PORTRAIT) com.sushant.inknote.R.string.msg_turned_h else com.sushant.inknote.R.string.msg_turned_v))
    }
    fun setPaper(p: PageEntity, paper: String) = viewModelScope.launch { repo.setPaper(p.id, paper); _pages.value = repo.pages(noteId) }
    fun duplicatePage(p: PageEntity) = viewModelScope.launch { flushNow(); repo.duplicatePage(noteId, p.id); _pages.value = repo.pages(noteId); msg(str(com.sushant.inknote.R.string.msg_page_duplicated)) }
    fun deletePage(p: PageEntity) = viewModelScope.launch {
        if (!repo.deletePage(noteId, p.id)) msg(str(com.sushant.inknote.R.string.msg_need_one_page)) else { _pages.value = repo.pages(noteId); contents.remove(p.id); undo.removeAll { it.pageId == p.id }; redo.removeAll { it.pageId == p.id }; canUndo = undo.isNotEmpty(); canRedo = redo.isNotEmpty(); msg(str(com.sushant.inknote.R.string.msg_page_deleted)) }
    }
    fun movePage(from: Int, to: Int) = viewModelScope.launch { repo.movePage(noteId, from, to); _pages.value = repo.pages(noteId) }

    // ================= note =================
    fun rename(t: String) = viewModelScope.launch { repo.rename(noteId, t) }
    fun toggleFavourite() = viewModelScope.launch { note.value?.let { repo.setFavourite(noteId, !it.favourite); msg(str(if (it.favourite) com.sushant.inknote.R.string.msg_fav_removed else com.sushant.inknote.R.string.msg_fav_added)) } }
    fun setLocked(v: Boolean) = viewModelScope.launch { flushNow(); repo.setLocked(listOf(noteId), v); msg(str(if (v) com.sushant.inknote.R.string.msg_locked else com.sushant.inknote.R.string.msg_unlocked)) }
    fun setTagsAndFolder(t: List<String>, folder: Long?) = viewModelScope.launch { repo.setTags(noteId, t); repo.moveToFolder(listOf(noteId), folder); tags.value = repo.tagsFor(noteId) }
    fun trash() = viewModelScope.launch { flushNow(); repo.moveToTrash(listOf(noteId)) }

    // ================= inserts =================
    private fun currentPageEntity() = pages.value.getOrNull(currentPage) ?: pages.value.firstOrNull()

    fun insertImages(uris: List<Uri>) = viewModelScope.launch {
        val p = currentPageEntity() ?: return@launch
        flushNow()
        val n = runCatching { ImageImport.addImages(getApplication(), repo, noteId, p.id, uris) }.getOrDefault(0)
        pages.value.forEach { pg -> contents[pg.id] = repo.readPage(noteId, pg.id) }
        repo.pages(noteId).forEach { pg -> contents[pg.id] = repo.readPage(noteId, pg.id) }
        undo.clear(); redo.clear(); canUndo = false; canRedo = false; reloadPages()
        msg(if (n == 0) str(com.sushant.inknote.R.string.msg_picture_failed) else qty(com.sushant.inknote.R.plurals.msg_pictures_added, n))
    }

    fun insertPdf(uri: Uri) = viewModelScope.launch {
        val p = currentPageEntity() ?: return@launch
        runCatching { repo.insertPdfPages(noteId, p.idx, uri) }.onSuccess { reloadPages(); msg(qty(com.sushant.inknote.R.plurals.msg_pdf_pages_added, it)) }.onFailure { msg(str(com.sushant.inknote.R.string.pdf_failed)) }
    }

    fun insertAudio(uri: Uri) = viewModelScope.launch { msg(str(if (Recorder.importAudio(getApplication(), repo, noteId, uri)) com.sushant.inknote.R.string.msg_audio_added else com.sushant.inknote.R.string.msg_audio_failed)) }

    fun startRecording() = viewModelScope.launch {
        stopPlayer()
        if (recorder.state.value != null) { msg(str(com.sushant.inknote.R.string.already_recording)); return@launch }
        if (!recorder.start(noteId)) msg(str(com.sushant.inknote.R.string.msg_rec_failed))
    }

    fun stopRecording() { recorder.stop(); msg(str(com.sushant.inknote.R.string.msg_rec_saved)) }

    // ================= playback =================
    fun play(r: RecordingEntity) {
        if (playingRec?.id == r.id && player != null) { if (!requestFocus()) return; player?.start(); isPlaying = true; startTick(); return }
        stopPlayer()
        playingRec = r
        viewModelScope.launch {
            // file access and decoding stay off the main thread
            val mp = withContext(Dispatchers.IO) {
                runCatching { MediaPlayer().apply { setDataSource(files.readable(files.audio(noteId, r.file)).absolutePath); prepare() } }.getOrNull()
            }
            if (mp == null) { msg(str(com.sushant.inknote.R.string.msg_cant_play)); return@launch }
            if (playingRec?.id != r.id) { mp.release(); return@launch }
            mp.setOnCompletionListener { isPlaying = false; positionMs = r.durationMs.coerceAtLeast(mp.duration.toLong()) }
            player = mp
            if (pendingSeek >= 0) { mp.seekTo(pendingSeek.toInt()); positionMs = pendingSeek; pendingSeek = -1 }
            if (!requestFocus()) return@launch
            mp.start(); isPlaying = true; startTick()
        }
    }
    private var pendingSeek = -1L
    fun select(r: RecordingEntity) { stopPlayer(); playingRec = r; positionMs = 0 }
    fun pause() { player?.pause(); isPlaying = false }

    // ---- audio focus: other apps' sound pauses our playback and vice versa ----
    private val audio = app.getSystemService(android.media.AudioManager::class.java)
    private val focusRequest = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN)
        .setAudioAttributes(android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_MEDIA).setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH).build())
        .setOnAudioFocusChangeListener { if (it <= 0) pause() }.build()
    private fun requestFocus() = audio.requestAudioFocus(focusRequest) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    fun seek(ms: Long) { val r = playingRec ?: return; if (player == null) { pendingSeek = ms; positionMs = ms; play(r); return }; player?.seekTo(ms.toInt()); positionMs = ms }
    fun seekToStroke(recId: Long, ms: Long) {
        val r = recordings.value.firstOrNull { it.id == recId } ?: return
        if (playingRec?.id != r.id || player == null) { pendingSeek = ms; positionMs = ms; play(r); return }
        seek(ms); if (!isPlaying && requestFocus()) { player?.start(); isPlaying = true; startTick() }
    }
    fun stopPlayer() { tick?.cancel(); player?.release(); player = null; isPlaying = false; audio.abandonAudioFocusRequest(focusRequest) }
    fun deleteRecording(r: RecordingEntity) = viewModelScope.launch { if (playingRec?.id == r.id) { stopPlayer(); playingRec = null }; repo.deleteRecording(r); msg(str(com.sushant.inknote.R.string.msg_rec_deleted)) }
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
