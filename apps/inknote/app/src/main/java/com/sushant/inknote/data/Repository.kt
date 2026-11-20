package com.sushant.inknote.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withContext
import java.io.File

data class NoteItem(val note: NoteEntity, val tags: List<String>)

class Repository(
    private val context: Context,
    val db: AppDatabase,
    val files: NoteFiles,
) {
    private val notes = db.notes(); private val pages = db.pages(); private val folders = db.folders()
    private val tags = db.tags(); private val recs = db.recordings(); private val search = db.search()

    val noteItems: Flow<List<NoteItem>> = combine(notes.liveNotes(), tags.observeLinks()) { list, links ->
        val byNote = links.groupBy({ it.noteId }, { it.name })
        list.map { NoteItem(it, byNote[it.id].orEmpty().sorted()) }
    }
    val trash = notes.trash()
    val folderList = folders.observe()
    val tagList = tags.observe()

    fun observeNote(id: Long) = notes.observe(id)
    fun observePages(id: Long) = pages.observe(id)
    fun observeRecordings(id: Long) = recs.observe(id)
    suspend fun note(id: Long) = notes.get(id)
    suspend fun pages(id: Long) = pages.list(id)
    suspend fun page(id: Long) = pages.get(id)
    suspend fun recordings(id: Long) = recs.list(id)
    suspend fun tagsFor(noteId: Long) = tags.forNote(noteId).map { it.name }

    private fun now() = System.currentTimeMillis()

    // ---------- create ----------
    suspend fun createNote(
        title: String, kind: String = Kind.INK, orientation: String = Orientation.PORTRAIT,
        paper: String = Paper.LINED, pageType: String = PageType.PAGES, folderId: Long? = null,
    ): Long = db.withTransaction {
        val t = now()
        val id = notes.insert(NoteEntity(title = title, folderId = folderId, createdAt = t, updatedAt = t, kind = kind,
            pageType = pageType, defaultOrientation = orientation, defaultPaper = paper))
        val (w, h) = PageSize.of(orientation)
        pages.insert(PageEntity(noteId = id, idx = 0, orientation = orientation, paper = paper, width = w, height = h))
        id
    }

    suspend fun createTextNote(title: String, text: String, folderId: Long? = null): Long {
        val id = createNote(title, Kind.TEXT, paper = Paper.PLAIN, folderId = folderId)
        val p = pages.list(id).first()
        val stored = com.sushant.inknote.ink.RichText.fromPlain(text)
        savePage(id, p.id, PageContent(texts = listOf(TextBox(Ids.next(), 60f, 60f, p.width - 120f, p.height - 120f, stored, text))))
        return id
    }

    suspend fun displayName(uri: Uri): String = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: "Document"
    }

    /** Copies a PDF into the note folder and returns its page sizes (in points). */
    private suspend fun copyPdf(noteId: Long, uri: Uri): Pair<String, List<Pair<Float, Float>>> = withContext(Dispatchers.IO) {
        val name = "doc_${Ids.next()}.pdf"
        val dest = files.pdf(noteId, name)
        files.pdfDir(noteId)
        context.contentResolver.openInputStream(uri)?.use { i -> dest.outputStream().use { i.copyTo(it) } }
            ?: error("Could not open that file")
        name to pdfSizes(dest)
    }

    fun pdfSizes(f: File): List<Pair<Float, Float>> =
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r -> (0 until r.pageCount).map { i -> r.openPage(i).use { it.width.toFloat() to it.height.toFloat() } } }
        }

    private fun pdfPageEntity(noteId: Long, idx: Int, file: String, i: Int, wh: Pair<Float, Float>): PageEntity {
        val landscape = wh.first > wh.second
        val w = if (landscape) PageSize.LONG else PageSize.SHORT
        return PageEntity(noteId = noteId, idx = idx, orientation = if (landscape) Orientation.LANDSCAPE else Orientation.PORTRAIT,
            paper = Paper.PLAIN, width = w, height = w * wh.second / wh.first, pdfFile = file, pdfPage = i)
    }

    suspend fun importPdf(uri: Uri, folderId: Long? = null): Long {
        val title = displayName(uri).removeSuffix(".pdf").removeSuffix(".PDF")
        val t = now()
        val id = notes.insert(NoteEntity(title = title, folderId = folderId, createdAt = t, updatedAt = t, kind = Kind.PDF, defaultPaper = Paper.PLAIN))
        try {
            val (file, sizes) = copyPdf(id, uri)
            if (sizes.isEmpty()) error("This PDF has no pages")
            pages.insertAll(sizes.mapIndexed { i, wh -> pdfPageEntity(id, i, file, i, wh) })
            notes.update(notes.get(id)!!.copy(pageCount = sizes.size))
        } catch (e: Exception) {
            notes.delete(id); files.deleteNote(id); throw e
        }
        return id
    }

    suspend fun insertPdfPages(noteId: Long, afterIdx: Int, uri: Uri): Int {
        val (file, sizes) = copyPdf(noteId, uri)
        db.withTransaction {
            val list = pages.list(noteId)
            val shifted = list.filter { it.idx > afterIdx }.map { it.copy(idx = it.idx + sizes.size) }
            pages.updateAll(shifted)
            pages.insertAll(sizes.mapIndexed { i, wh -> pdfPageEntity(noteId, afterIdx + 1 + i, file, i, wh) })
        }
        syncPageCount(noteId); notes.touch(noteId, now()); secureIfLocked(noteId)
        return sizes.size
    }

    // ---------- pages ----------
    suspend fun savePage(noteId: Long, pageId: Long, content: PageContent) {
        val n = notes.get(noteId) ?: return
        withContext(Dispatchers.IO) { files.writePage(noteId, pageId, content, n.locked) }
        val first = if (n.kind == Kind.TEXT || n.firstLine.isBlank()) content.texts.firstOrNull()?.plain?.lineSequence()?.firstOrNull { it.isNotBlank() }?.take(80) ?: n.firstLine else n.firstLine
        notes.update(n.copy(updatedAt = now(), thumbVersion = n.thumbVersion + 1, firstLine = first))
    }

    suspend fun readPage(noteId: Long, pageId: Long): PageContent = withContext(Dispatchers.IO) { files.readPage(noteId, pageId) }

    private suspend fun syncPageCount(noteId: Long) {
        val n = notes.get(noteId) ?: return
        notes.update(n.copy(pageCount = pages.list(noteId).size))
    }

    suspend fun addPage(noteId: Long, afterIdx: Int, orientation: String? = null, paper: String? = null): Long {
        val n = notes.get(noteId) ?: return -1
        val o = orientation ?: n.defaultOrientation
        val (w, h) = PageSize.of(o)
        val id = db.withTransaction {
            pages.updateAll(pages.list(noteId).filter { it.idx > afterIdx }.map { it.copy(idx = it.idx + 1) })
            pages.insert(PageEntity(noteId = noteId, idx = afterIdx + 1, orientation = o, paper = paper ?: n.defaultPaper, width = w, height = h))
        }
        syncPageCount(noteId); notes.touch(noteId, now())
        return id
    }

    suspend fun duplicatePage(noteId: Long, pageId: Long): Long {
        val p = pages.get(pageId) ?: return -1
        val c = readPage(noteId, pageId)
        val id = db.withTransaction {
            pages.updateAll(pages.list(noteId).filter { it.idx > p.idx }.map { it.copy(idx = it.idx + 1) })
            pages.insert(p.copy(id = 0, idx = p.idx + 1))
        }
        val fresh = PageContent(c.strokes.map { it.copy(id = Ids.next()) }, c.texts.map { it.copy(id = Ids.next()) }, c.images.map { it.copy(id = Ids.next()) })
        savePage(noteId, id, fresh); syncPageCount(noteId)
        return id
    }

    /** Deletes a page; a note always keeps at least one page. Returns false when it was the last page. */
    suspend fun deletePage(noteId: Long, pageId: Long): Boolean {
        val list = pages.list(noteId)
        if (list.size <= 1) return false
        db.withTransaction {
            pages.delete(pageId)
            pages.updateAll(pages.list(noteId).mapIndexed { i, p -> p.copy(idx = i) })
        }
        files.deletePage(noteId, pageId)
        syncPageCount(noteId); notes.touch(noteId, now())
        return true
    }

    suspend fun movePage(noteId: Long, from: Int, to: Int) {
        val list = pages.list(noteId).toMutableList()
        if (from !in list.indices || to !in list.indices) return
        val p = list.removeAt(from); list.add(to, p)
        pages.updateAll(list.mapIndexed { i, x -> x.copy(idx = i) })
        notes.touch(noteId, now())
    }

    /** Turns a page between vertical and horizontal. Content is scaled to fit — never cropped. */
    suspend fun turnPage(noteId: Long, pageId: Long): Boolean {
        val p = pages.get(pageId) ?: return false
        if (p.pdfFile != null) return false
        val n = notes.get(noteId) ?: return false
        if (n.pageType == PageType.ENDLESS) return false
        val newO = if (p.orientation == Orientation.PORTRAIT) Orientation.LANDSCAPE else Orientation.PORTRAIT
        val (w, h) = PageSize.of(newO)
        val s = Geometry.fitScale(p.width, p.height, w, h)
        val c = readPage(noteId, pageId)
        savePage(noteId, pageId, c.transformed(s, s))
        pages.update(p.copy(orientation = newO, width = w, height = h))
        return true
    }

    suspend fun setPaper(pageId: Long, paper: String) { pages.get(pageId)?.let { pages.update(it.copy(paper = paper)); notes.touch(it.noteId, now()) } }
    suspend fun updatePage(p: PageEntity) = pages.update(p)

    // ---------- note props ----------
    suspend fun update(n: NoteEntity) = notes.update(n)
    suspend fun rename(id: Long, title: String) { notes.get(id)?.let { notes.update(it.copy(title = title.trim().ifBlank { "Untitled" }, updatedAt = now())) } }
    suspend fun setPinned(ids: Collection<Long>, v: Boolean) = ids.forEach { id -> notes.get(id)?.let { notes.update(it.copy(pinned = v)) } }
    suspend fun setFavourite(id: Long, v: Boolean) { notes.get(id)?.let { notes.update(it.copy(favourite = v)) } }
    suspend fun moveToFolder(ids: Collection<Long>, folderId: Long?) = ids.forEach { id -> notes.get(id)?.let { notes.update(it.copy(folderId = folderId)) } }

    suspend fun setTags(noteId: Long, names: List<String>) = db.withTransaction {
        tags.clearNote(noteId)
        for (raw in names) {
            val name = raw.trim().removePrefix("#").trim(); if (name.isEmpty()) continue
            val id = tags.byName(name)?.id ?: tags.insert(TagEntity(name = name))
            tags.link(NoteTagEntity(noteId, id))
        }
        tags.dropUnused()
    }

    suspend fun setLocked(ids: Collection<Long>, v: Boolean) = ids.forEach { id ->
        val n = notes.get(id) ?: return@forEach
        if (n.locked == v) return@forEach
        withContext(Dispatchers.IO) { files.setLocked(id, v) }
        notes.update(n.copy(locked = v))
        if (v) search.clear(id.toString()) else notes.update(n.copy(locked = false, indexedAt = 0))
    }

    suspend fun moveToTrash(ids: Collection<Long>) = ids.forEach { id -> notes.get(id)?.let { notes.update(it.copy(deletedAt = now(), pinned = false)) } }
    suspend fun restore(ids: Collection<Long>) = ids.forEach { id -> notes.get(id)?.let { notes.update(it.copy(deletedAt = null)) } }

    suspend fun deleteForever(ids: Collection<Long>) = ids.forEach { id ->
        db.withTransaction { pages.deleteForNote(id); recs.deleteForNote(id); tags.clearNote(id); search.clear(id.toString()); notes.delete(id) }
        withContext(Dispatchers.IO) { files.deleteNote(id) }
        tags.dropUnused()
    }

    suspend fun purgeExpiredTrash(days: Int = 30) = deleteForever(notes.expiredTrash(now() - days * 24L * 3600_000).map { it.id })

    // ---------- folders ----------
    suspend fun addFolder(name: String, color: Int) = folders.insert(FolderEntity(name = name.trim(), color = color, sort = folders.all().size))
    suspend fun updateFolder(f: FolderEntity) = folders.update(f)
    suspend fun deleteFolder(id: Long) = db.withTransaction { folders.unlinkNotes(id); folders.delete(id) }

    // ---------- audio ----------
    /** Files added to a locked note (PDF pages, recordings, audio) are encrypted like the rest of it. */
    suspend fun secureIfLocked(noteId: Long) { if (notes.get(noteId)?.locked == true) withContext(Dispatchers.IO) { files.setLocked(noteId, true) } }

    suspend fun addRecording(r: RecordingEntity): Long {
        val id = recs.insert(r)
        notes.get(r.noteId)?.let { notes.update(it.copy(hasRecording = true, updatedAt = now())) }
        return id
    }

    suspend fun deleteRecording(r: RecordingEntity) {
        recs.delete(r.id); files.audio(r.noteId, r.file).delete()
        if (recs.list(r.noteId).isEmpty()) notes.get(r.noteId)?.let { notes.update(it.copy(hasRecording = false)) }
    }

    // ---------- search ----------
    suspend fun search(q: String): List<SearchHit> {
        val words = q.trim().split(Regex("\\s+")).map { it.replace(Regex("[^\\p{L}\\p{N}]"), "") }.filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        val match = words.joinToString(" ") { "$it*" }
        return runCatching { search.match(match) }.getOrDefault(emptyList())
    }

    suspend fun replaceIndex(noteId: Long, rows: List<SearchFts>) = db.withTransaction {
        search.clear(noteId.toString()); if (rows.isNotEmpty()) search.insert(rows); notes.markIndexed(noteId, now())
    }

    suspend fun notesNeedingIndex() = notes.needIndex()
}
