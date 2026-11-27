package com.sushant.pageone.ui.reader

import android.app.Application
import android.graphics.PointF
import android.graphics.RectF
import android.util.Log
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sushant.pageone.PageOneApp
import com.sushant.pageone.data.DocRef
import com.sushant.pageone.data.Library
import com.sushant.pageone.data.Outputs
import com.sushant.pageone.pdf.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

sealed class Status {
    data object Loading : Status()
    data class NeedPassword(val wrong: Boolean) : Status()
    data class Failed(val message: String) : Status()
    data object Ready : Status()
}

enum class Mode { READ, FIND, ANNOTATE }
enum class Sheet { NONE, PAGES, CONTENTS, BOOKMARKS, OPTIONS, RESULTS }

data class Hit(val page: Int, val rects: List<RectF>, val snippet: String)

data class Selection(val page: Int, val text: String, val rects: List<RectF>, val start: PointF, val end: PointF)

class ReaderVm(app: Application, initial: DocRef) : AndroidViewModel(app) {
    private val a = app as PageOneApp
    val lib: Library = a.library
    var ref by mutableStateOf(initial)
        private set
    var status by mutableStateOf<Status>(Status.Loading)
        private set
    var doc by mutableStateOf<PdfDoc?>(null)
        private set
    var page by mutableIntStateOf(0)
    var restore by mutableStateOf<Pair<Int, Float>?>(null)          // page + zoom to jump to once after opening
    var chrome by mutableStateOf(true)
    var mode by mutableStateOf(Mode.READ)
    var sheet by mutableStateOf(Sheet.NONE)
    var rotation by mutableIntStateOf(0)
    var reflow by mutableStateOf(false)
    var lockRotation by mutableStateOf(false)
    var zoom by mutableFloatStateOf(1f)
    /** The zoom the page images were last drawn sharp at; follows [zoom] once a gesture settles. */
    var renderZoom by mutableFloatStateOf(1f)
    /** Uptime of the last touch with two or more fingers, so a pinch is never read as a tap. */
    var lastMultiTouch = 0L
    fun touchIsTap() = android.os.SystemClock.uptimeMillis() - lastMultiTouch > 350
    /** Set by the screen: double-tap zoom around a point on a page. */
    /** Set by the screen: scroll to a page (0-based). */
    var goTo: ((Int) -> Unit)? = null
    /** Set by the screen: double-tap zoom around a point given in root coordinates. */
    var doubleTap: ((androidx.compose.ui.geometry.Offset) -> Unit)? = null
    private var password: String? = null

    // find
    var query by mutableStateOf("")
    var matchCase by mutableStateOf(false)
    var wholeWord by mutableStateOf(false)
    val hits = mutableStateListOf<Hit>()
    var hitIndex by mutableIntStateOf(-1)
    var searching by mutableStateOf(false)
    var searchDone by mutableStateOf(false)
    private var searchJob: Job? = null

    // selection
    var selection by mutableStateOf<Selection?>(null)

    // annotate
    val history = AnnotHistory()
    var annotVersion by mutableIntStateOf(0)
    var tool by mutableStateOf(Tool.HIGHLIGHT)
    var color by mutableIntStateOf(0x99F7E26B.toInt())
    var penColor by mutableIntStateOf(0xFFB3261E.toInt())
    var draft by mutableStateOf<Annot.Ink?>(null)
    var showSave by mutableStateOf(false)
    var saving by mutableStateOf(false)
    var noteAt by mutableStateOf<Pair<Int, PointF>?>(null)

    // forms
    var fields by mutableStateOf<List<FormField>>(emptyList())
    val formValues = mutableStateMapOf<String, String>()
    val formChecks = mutableStateMapOf<String, Boolean>()
    val hasUnsaved get() = history.all.isNotEmpty() || formValues.isNotEmpty() || formChecks.isNotEmpty()

    // contents
    var outline by mutableStateOf<List<OutlineItem>?>(null)
    val fingerprint get() = doc?.fingerprint ?: ""
    val bookmarks: StateFlow<List<Int>> get() = _bookmarks
    private val _bookmarks = MutableStateFlow<List<Int>>(emptyList())
    private var bmJob: Job? = null

    // read aloud
    val speech = Speech(app)

    init { open(null) }

    fun open(pw: String?) {
        status = Status.Loading
        password = pw
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { PdfDoc.open(a, ref, pw) } }
            r.onSuccess { d ->
                doc?.close()
                doc = d
                status = Status.Ready
                a.local.count("open")
                val pos = lib.position(d.fingerprint)
                if (pos != null && pos.page in 0 until d.pageCount) { restore = pos.page to pos.zoom.coerceIn(1f, 5f); page = pos.page }
                else restore = 0 to 1f
                lib.recordOpen(ref, d.fingerprint, d.pageCount)
                bmJob?.cancel()
                bmJob = launch { lib.bookmarks(d.fingerprint).collect { l -> _bookmarks.value = l.map { it.page } } }
                launch(Dispatchers.IO) {
                    fields = PdfTools.formFields(d.sourceFile, pw)
                    outline = PdfTools.outline(d.sourceFile, pw)
                }
            }.onFailure { e ->
                Log.w("PageOneReader", "open failed", e)
                status = when (e) {
                    is PasswordNeeded -> Status.NeedPassword(e.wrong)
                    else -> Status.Failed(e.message ?: "")
                }
            }
        }
    }

    fun savePosition(p: Int) {
        page = p
        val fp = fingerprint.ifEmpty { return }
        viewModelScope.launch { lib.savePosition(fp, p, zoom, 0) }
    }

    fun removeBookmark(p: Int) { val fp = fingerprint.ifEmpty { return }; viewModelScope.launch { lib.toggleBookmark(fp, p, true) } }

    /** Start reading [page] aloud, from [fromText] when given (selection → "Read aloud from here"). */
    fun speakFrom(page: Int, fromText: String? = null) {
        val d = doc ?: return
        viewModelScope.launch {
            val text = d.pageText(page)
            if (text.isBlank()) { speechMessage = "No text to read on this page"; return@launch }
            val at = fromText?.let { text.indexOf(it.take(20)).coerceAtLeast(0) } ?: 0
            speech.start(page, text, at)
        }
    }
    var speechMessage by mutableStateOf<String?>(null)

    fun toggleBookmark() {
        val fp = fingerprint.ifEmpty { return }
        val has = page in _bookmarks.value
        viewModelScope.launch { lib.toggleBookmark(fp, page, has) }
    }

    // ---------- find

    fun runSearch() {
        val d = doc ?: return
        searchJob?.cancel()
        hits.clear(); hitIndex = -1
        val q = query.trim()
        if (q.isEmpty()) return
        viewModelScope.launch { lib.addSearch(q) }
        searching = true; searchDone = false
        searchJob = viewModelScope.launch {
            // search from the current page onwards, then wrap around
            val order = (page until d.pageCount) + (0 until page)
            val found = ArrayList<Hit>()
            for (p in order) {
                val ms = d.search(p, q, matchCase, wholeWord)
                if (ms.isEmpty()) continue
                val text = d.pageText(p)
                for (m in ms) found += Hit(p, m.rects, snippet(text, m.start, q.length))
                val sorted = found.sortedWith(compareBy({ it.page }, { it.rects.firstOrNull()?.top ?: 0f }))
                hits.clear(); hits.addAll(sorted)
                if (hitIndex < 0) hitIndex = hits.indexOfFirst { it.page >= page }.coerceAtLeast(0)
            }
            searching = false; searchDone = true
        }
    }

    private fun snippet(text: String, start: Int, len: Int): String {
        if (start < 0 || start >= text.length) return ""
        val a = (start - 30).coerceAtLeast(0); val b = (start + len + 40).coerceAtMost(text.length)
        return (if (a > 0) "…" else "") + text.substring(a, b).replace('\n', ' ') + (if (b < text.length) "…" else "")
    }

    fun nextHit(dir: Int) { if (hits.isNotEmpty()) hitIndex = (hitIndex + dir + hits.size) % hits.size }

    fun closeFind() { searchJob?.cancel(); hits.clear(); hitIndex = -1; mode = Mode.READ; searching = false }

    // ---------- selection & annotations

    fun select(page: Int, at: PointF) {
        val d = doc ?: return
        viewModelScope.launch {
            val w = d.wordAt(page, at)
            selection = w?.let { Selection(page, it.text, it.rects, PointF(it.rects.first().left + 1, it.rects.first().centerY()), PointF(it.rects.last().right - 1, it.rects.last().centerY())) }
        }
    }

    fun moveSelection(start: PointF, end: PointF) {
        val d = doc ?: return; val s = selection ?: return
        viewModelScope.launch {
            d.select(s.page, start, end)?.let { r -> selection = s.copy(text = r.text, rects = r.rects, start = start, end = end) }
        }
    }

    fun markSelection(underline: Boolean) {
        val s = selection ?: return
        history.add(Annot.Mark(s.page, s.rects.map { RectF(it) }, if (underline) 0xFFB3261E.toInt() else color, underline))
        annotVersion++
        selection = null
        showSave = true
    }

    fun addAnnot(an: Annot) { history.add(an); annotVersion++ }
    fun removeAnnot(an: Annot) { history.remove(an); annotVersion++ }
    fun undo() { if (history.undo()) annotVersion++ }
    fun redo() { if (history.redo()) annotVersion++ }

    fun eraseAt(page: Int, at: PointF) {
        val hit = history.all.lastOrNull { it.page == page && RectF(it.bounds()).apply { inset(-6f, -6f) }.contains(at.x, at.y) } ?: return
        removeAnnot(hit)
    }

    /** Write the edits. [asCopy] = keep the original and save "Name (edited).pdf" next to it (or in Documents/PageOne). */
    fun save(asCopy: Boolean, done: (String) -> Unit) {
        val d = doc ?: return
        saving = true
        viewModelScope.launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val annots = history.all.toList()
                    val forms = formValues.toMap(); val checks = formChecks.toMap()
                    val path = ref.path
                    if (!asCopy && path != null && File(path).canWrite()) {
                        val tmp = File(File(path).parentFile, ".${File(path).name}.saving")
                        tmp.outputStream().use { PdfTools.saveEdits(d.sourceFile, password, annots, forms, checks, it) }
                        if (!tmp.renameTo(File(path))) { tmp.copyTo(File(path), true); tmp.delete() }
                        lib.afterFileChanged(path)
                        ref to "Saved"
                    } else if (!asCopy && path == null && runCatching { a.contentResolver.openOutputStream(android.net.Uri.parse(ref.uri), "wt")?.close() }.isSuccess) {
                        a.contentResolver.openOutputStream(android.net.Uri.parse(ref.uri), "wt")!!.use { PdfTools.saveEdits(d.sourceFile, password, annots, forms, checks, it) }
                        ref to "Saved"
                    } else {
                        val base = ref.name.removeSuffix(".pdf") + " (edited)"
                        val parent = path?.let { File(it).parentFile }?.takeIf { it.canWrite() }
                        if (parent != null) {
                            val f = Outputs.unique(parent, "$base.pdf")
                            f.outputStream().use { PdfTools.saveEdits(d.sourceFile, password, annots, forms, checks, it) }
                            lib.afterFileChanged(f.path)
                            DocRef.ofFile(f.path) to "Saved as ${f.name}"
                        } else {
                            val out = Outputs.create(a, base) { PdfTools.saveEdits(d.sourceFile, password, annots, forms, checks, it) }
                            out.path?.let { lib.afterFileChanged(it) }
                            (if (out.path != null) DocRef.ofFile(out.path) else DocRef(out.uri.toString(), null, out.name)) to "Saved as ${out.name}"
                        }
                    }
                }
            }
            saving = false
            r.onSuccess { (newRef, msg) ->
                history.clear(); formValues.clear(); formChecks.clear(); annotVersion++
                showSave = false; mode = Mode.READ
                a.local.count("save")
                val keepPage = page
                ref = newRef
                d.close(); doc = null
                open(password)
                restore = keepPage to zoom
                done(msg)
            }.onFailure { done("Couldn’t save: ${it.message ?: "unknown error"}") }
        }
    }

    fun discard() { history.clear(); formValues.clear(); formChecks.clear(); annotVersion++; showSave = false; mode = Mode.READ }

    fun renamed(newPath: String) { ref = DocRef.ofFile(newPath); viewModelScope.launch { lib.recordOpen(ref, fingerprint, doc?.pageCount ?: 0) } }

    override fun onCleared() {
        speech.shutdown()
        doc?.close()
        super.onCleared()
    }
}
