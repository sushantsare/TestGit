package com.sushant.pageone.data

import android.content.ContentResolver
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/** A PDF to open: a real file path, or a content:// Uri handed over by another app. */
data class DocRef(val uri: String, val path: String?, val name: String) {
    val isFile get() = path != null
    companion object {
        fun ofFile(path: String) = DocRef(Uri.fromFile(File(path)).toString(), path, File(path).name)
    }
}

data class ScanState(val running: Boolean = false, val found: Int = 0, val done: Boolean = false)

data class FolderInfo(val path: String, val name: String, val count: Int, val size: Long, val modified: Long)

class Library(private val context: Context, private val db: PageOneDb, private val prefs: Prefs, private val scope: CoroutineScope) {
    private val tag = "PageOneLib"
    val pdfs: StateFlow<List<PdfEntity>> = db.pdfs().all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val recents: StateFlow<List<RecentEntity>> = db.reading().recents().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val stars: StateFlow<List<StarEntity>> = db.reading().stars().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val positions: StateFlow<Map<String, PositionEntity>> = db.reading().positions()
        .map { l -> l.associateBy { it.fingerprint } }.stateIn(scope, SharingStarted.Eagerly, emptyMap())
    val searchHistory = db.reading().searchHistory().map { l -> l.map { it.query } }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _scan = MutableStateFlow(ScanState())
    val scan: StateFlow<ScanState> = _scan.asStateFlow()
    /** Bumped when a thumbnail is written so rows reload it. */
    val thumbVersion = MutableStateFlow(0)
    private val io = Dispatchers.IO.limitedParallelism(2)
    private var scanJob: Job? = null

    fun hasAllFilesAccess(): Boolean = Environment.isExternalStorageManager()

    fun folders(list: List<PdfEntity>, sort: SortKey, asc: Boolean): List<FolderInfo> {
        val f = list.groupBy { it.folder }.map { (path, items) ->
            FolderInfo(path, Format.folderName(path), items.size, items.sumOf { it.size }, items.maxOf { it.modified })
        }
        val sorted = when (sort) {
            SortKey.NAME -> f.sortedBy { it.name.lowercase() }
            SortKey.DATE -> f.sortedBy { it.modified }
            SortKey.SIZE -> f.sortedBy { it.size }
        }
        return if (asc) sorted else sorted.reversed()
    }

    fun sortPdfs(list: List<PdfEntity>, sort: SortKey, asc: Boolean): List<PdfEntity> {
        val s = when (sort) {
            SortKey.NAME -> list.sortedBy { it.name.lowercase() }
            SortKey.DATE -> list.sortedBy { it.modified }
            SortKey.SIZE -> list.sortedBy { it.size }
        }
        return if (asc) s else s.reversed()
    }

    /** Rescan the phone for PDFs (needs All files access). */
    fun scan(force: Boolean = false) {
        if (!hasAllFilesAccess()) return
        if (scanJob?.isActive == true && !force) return
        scanJob = scope.launch(Dispatchers.IO) {
            _scan.value = ScanState(running = true, found = 0)
            try {
                val indexed = queryMediaStore()
                // files the phone hasn't indexed yet (just copied, just downloaded) are found by walking storage
                val known = indexed.mapTo(HashSet()) { it.path }
                val found = indexed + walkStorage().filter { it.path !in known }
                _scan.value = ScanState(running = true, found = found.size)
                val existing = db.pdfs().allOnce().associateBy { it.path }
                val merged = found.map { n ->
                    val old = existing[n.path]
                    if (old != null && old.size == n.size && old.modified == n.modified) n.copy(pages = old.pages, fingerprint = old.fingerprint) else n
                }
                db.pdfs().upsert(merged)
                val gone = existing.keys - found.map { it.path }.toSet()
                if (gone.isNotEmpty()) db.pdfs().delete(gone.toList())
                prefs.update { it.copy(lastScan = System.currentTimeMillis()) }
                cleanRecents()
            } catch (e: Exception) {
                Log.w(tag, "scan failed", e)
            } finally {
                _scan.value = ScanState(running = false, found = pdfs.value.size, done = true)
            }
            enrichAll()
        }
    }

    private fun queryMediaStore(): List<PdfEntity> {
        val uri = MediaStore.Files.getContentUri("external")
        val proj = arrayOf(
            MediaStore.Files.FileColumns._ID, MediaStore.Files.FileColumns.DATA, MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE, MediaStore.Files.FileColumns.DATE_MODIFIED,
        )
        val sel = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? OR ${MediaStore.Files.FileColumns.DATA} LIKE ?"
        val out = ArrayList<PdfEntity>()
        context.contentResolver.query(uri, proj, sel, arrayOf("application/pdf", "%.pdf"), null)?.use { c ->
            while (c.moveToNext()) {
                val path = c.getString(1) ?: continue
                val f = File(path)
                if (!path.endsWith(".pdf", true) || !f.exists() || f.isDirectory) continue
                if (path.contains("/.")) continue     // hidden folders and trash
                out += PdfEntity(
                    path = path,
                    name = c.getString(2) ?: f.name,
                    folder = f.parent ?: "/",
                    size = c.getLong(3).takeIf { it > 0 } ?: f.length(),
                    modified = c.getLong(4) * 1000L,
                    mediaId = c.getLong(0),
                )
            }
        }
        return out.distinctBy { it.path }
    }

    private fun walkStorage(): List<PdfEntity> {
        val root = android.os.Environment.getExternalStorageDirectory()
        val skip = setOf("${root.path}/Android/data", "${root.path}/Android/obb")
        val out = ArrayList<PdfEntity>()
        fun walk(dir: File, depth: Int) {
            if (depth > 8 || dir.path in skip || out.size > 20_000) return
            val kids = dir.listFiles() ?: return
            for (f in kids) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) walk(f, depth + 1)
                else if (f.name.endsWith(".pdf", true) && f.length() > 0)
                    out += PdfEntity(path = f.path, name = f.name, folder = f.parent ?: "/", size = f.length(), modified = f.lastModified(), mediaId = 0L)
            }
        }
        runCatching { walk(root, 0) }.onFailure { Log.w(tag, "walk failed", it) }
        return out
    }

    /** Page counts and fingerprints for rows that don't have them yet, plus first-page thumbnails. */
    private suspend fun enrichAll() = coroutineScope {
        val todo = db.pdfs().allOnce().filter { it.pages == 0 || it.fingerprint.isEmpty() || !thumbFile(it.path).exists() }
        todo.map { p -> async(io) { enrich(p) } }.awaitAll()
    }

    private fun enrich(p: PdfEntity) {
        runCatching {
            val f = File(p.path)
            val fp = if (p.fingerprint.isNotEmpty()) p.fingerprint else fingerprintFile(f)
            var pages = p.pages
            val tf = thumbFile(p.path)
            ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                runCatching {
                    PdfRenderer(pfd).use { r ->
                        pages = r.pageCount
                        if (!tf.exists() && r.pageCount > 0) writeThumb(r, tf)
                    }
                }.onFailure { if (pages == 0) pages = -1 }  // protected or damaged
            }
            runBlocking { db.pdfs().setInfo(p.path, pages, fp) }
            thumbVersion.value++
        }.onFailure { Log.w(tag, "enrich ${p.path}: ${it.message}") }
    }

    fun thumbFile(key: String): File = File(context.filesDir, "thumbs/${key.hashCode().toUInt()}.png")

    private fun writeThumb(r: PdfRenderer, out: File) {
        r.openPage(0).use { page ->
            val w = 180
            val h = (w * page.height.toFloat() / page.width).toInt().coerceIn(60, 360)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            bmp.eraseColor(Color.WHITE)
            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            out.parentFile?.mkdirs()
            FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.PNG, 90, it) }
            bmp.recycle()
        }
    }

    /** Thumbnail for anything we can open (recents from other apps included). */
    suspend fun ensureThumb(ref: DocRef): File? = withContext(io) {
        val key = ref.path ?: ref.uri
        val tf = thumbFile(key)
        if (tf.exists()) return@withContext tf
        runCatching {
            openPfd(ref)?.use { pfd -> PdfRenderer(pfd).use { if (it.pageCount > 0) writeThumb(it, tf) } }
            thumbVersion.value++
            tf
        }.getOrNull()
    }

    fun openPfd(ref: DocRef): ParcelFileDescriptor? = runCatching {
        if (ref.path != null && File(ref.path).canRead()) ParcelFileDescriptor.open(File(ref.path), ParcelFileDescriptor.MODE_READ_ONLY)
        else context.contentResolver.openFileDescriptor(Uri.parse(ref.uri), "r")
    }.getOrNull()

    // ---------- reading state

    suspend fun recordOpen(ref: DocRef, fp: String, pages: Int) {
        db.reading().upsertRecent(RecentEntity(fp, ref.name, ref.uri, ref.path, pages, System.currentTimeMillis()))
        stars.value.find { it.fingerprint == fp }?.let { db.reading().upsertStar(it.copy(uri = ref.uri, path = ref.path ?: it.path, pages = pages)) }
    }

    suspend fun removeRecent(fp: String) = db.reading().deleteRecent(fp)
    suspend fun restoreRecent(r: RecentEntity) = db.reading().upsertRecent(r)
    suspend fun clearRecents() = db.reading().clearRecents()
    suspend fun lastRecent() = db.reading().lastRecent()

    suspend fun toggleStar(ref: DocRef, fp: String, pages: Int) {
        if (stars.value.any { it.fingerprint == fp }) db.reading().deleteStar(fp)
        else db.reading().upsertStar(StarEntity(fp, ref.name, ref.uri, ref.path, pages, (stars.value.maxOfOrNull { it.sort } ?: 0) + 1))
    }

    suspend fun reorderStars(ordered: List<StarEntity>) = db.reading().upsertStars(ordered.mapIndexed { i, s -> s.copy(sort = i) })

    suspend fun position(fp: String) = db.reading().position(fp)
    suspend fun savePosition(fp: String, page: Int, zoom: Float, offset: Int) =
        db.reading().savePosition(PositionEntity(fp, page, zoom, offset, System.currentTimeMillis()))

    fun bookmarks(fp: String) = db.reading().bookmarks(fp)
    suspend fun toggleBookmark(fp: String, page: Int, existing: Boolean) {
        if (existing) db.reading().removeBookmark(fp, page)
        else db.reading().addBookmark(BookmarkEntity(fp, page, "Page ${page + 1}", System.currentTimeMillis()))
    }

    suspend fun addSearch(q: String) { if (q.isNotBlank()) db.reading().addSearch(SearchHistoryEntity(q.trim(), System.currentTimeMillis())) }
    suspend fun clearSearchHistory() = db.reading().clearSearchHistory()

    /** Fingerprint of a library file (filled during scans). */
    suspend fun fingerprintOf(path: String): String = db.pdfs().byPath(path)?.fingerprint?.takeIf { it.isNotEmpty() }
        ?: withContext(Dispatchers.IO) { fingerprintFile(File(path)) }

    /** Remove Recent entries whose file is gone (deleted or moved) and no copy is known. */
    suspend fun cleanRecents() {
        val lib = db.pdfs().allOnce()
        for (r in db.reading().recents().first()) {
            val pathGone = r.path != null && !File(r.path).exists()
            val uriGone = r.path == null && runCatching {
                context.contentResolver.openFileDescriptor(Uri.parse(r.uri), "r")?.close() ?: throw IllegalStateException()
            }.isFailure
            if ((pathGone || uriGone) && lib.none { it.fingerprint == r.fingerprint }) db.reading().deleteRecent(r.fingerprint)
            else if (pathGone) lib.firstOrNull { it.fingerprint == r.fingerprint }?.let {
                db.reading().upsertRecent(r.copy(path = it.path, uri = Uri.fromFile(File(it.path)).toString()))
            }
        }
    }

    /** Best way to open something from Recent/Starred: the remembered path, a library copy, or the Uri. */
    suspend fun resolve(fp: String, name: String, uri: String, path: String?): DocRef {
        if (path != null && File(path).canRead()) return DocRef.ofFile(path)
        db.pdfs().byFingerprint(fp).firstOrNull { File(it.path).canRead() }?.let { return DocRef.ofFile(it.path) }
        return DocRef(uri, null, name)
    }

    fun afterFileChanged(path: String) {
        MediaScannerConnection.scanFile(context, arrayOf(path), arrayOf("application/pdf"), null)
        thumbFile(path).delete()
        scope.launch { delay(800); scan(force = true) }
    }

    companion object {
        /** size + first 64 KB + last 64 KB → short SHA-1. Same bytes → same fingerprint, so copies share position/star. */
        fun fingerprint(size: Long, head: ByteArray, tail: ByteArray): String {
            val md = MessageDigest.getInstance("SHA-1")
            md.update(size.toString().toByteArray()); md.update(head); md.update(tail)
            return md.digest().take(10).joinToString("") { "%02x".format(it) }
        }

        fun fingerprintFile(f: File): String = FileInputStream(f).use { fingerprintStream(it, f.length()) }

        fun fingerprintStream(input: FileInputStream, size: Long): String {
            val ch = input.channel
            val n = 65536
            val head = ByteArray(minOf(n.toLong(), size).toInt())
            ch.position(0); java.nio.ByteBuffer.wrap(head).let { b -> while (b.hasRemaining() && ch.read(b) > 0) {} }
            val tailLen = minOf(n.toLong(), size).toInt()
            val tail = ByteArray(tailLen)
            ch.position(maxOf(0, size - tailLen)); java.nio.ByteBuffer.wrap(tail).let { b -> while (b.hasRemaining() && ch.read(b) > 0) {} }
            return fingerprint(size, head, tail)
        }

        fun displayName(cr: ContentResolver, uri: Uri): String? = runCatching {
            cr.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        }.getOrNull()

        /** Real path behind a content Uri when the provider exposes one (MediaStore, some file managers). */
        fun pathFromUri(cr: ContentResolver, uri: Uri): String? {
            if (uri.scheme == "file") return uri.path
            return runCatching {
                cr.query(uri, arrayOf(MediaStore.MediaColumns.DATA), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.takeIf { File(it).canRead() } else null
                }
            }.getOrNull()
        }
    }
}
