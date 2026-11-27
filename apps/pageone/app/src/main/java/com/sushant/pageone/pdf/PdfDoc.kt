package com.sushant.pageone.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Point
import android.graphics.RectF
import android.graphics.pdf.LoadParams
import android.graphics.pdf.PdfRenderer
import android.graphics.pdf.models.selection.SelectionBoundary
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.LruCache
import android.util.SizeF
import androidx.annotation.RequiresApi
import com.sushant.pageone.data.DocRef
import com.sushant.pageone.data.Library
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.concurrent.Executors

/** A line or run of text on a page, with its rectangles in page points (top-left origin). */
data class TextRun(val text: String, val rects: List<RectF>)

data class PageSelectionResult(val text: String, val rects: List<RectF>)

data class Match(val start: Int, val rects: List<RectF>)

class PasswordNeeded(val wrong: Boolean) : Exception(if (wrong) "Wrong password" else "Password needed")

/**
 * One open PDF. Android's PdfRenderer can only work on one page at a time, so every call runs on this
 * document's own single thread. Page sizes are in PDF points (1/72 inch).
 */
class PdfDoc private constructor(
    val ref: DocRef,
    private val pfd: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    val pageCount: Int,
    val sizes: androidx.compose.runtime.snapshots.SnapshotStateList<SizeF>,
    val fingerprint: String,
    val sourceFile: File,          // readable file with the same bytes (original path, or a private copy)
    val password: String?,
) {
    private val exec = Executors.newSingleThreadExecutor()
    private val thread = exec.asCoroutineDispatcher()
    private val textCache = HashMap<Int, List<TextRun>>()
    private val plainText = HashMap<Int, String>()
    private val boundsCache = HashMap<Int, RectF?>()
    @Volatile var closed = false
        private set

    /** Rendered bitmaps, keyed "page:width:crop:rot". Sized to a slice of the app's memory. */
    val cache = object : LruCache<String, Bitmap>((Runtime.getRuntime().maxMemory() / 1024 / 5).toInt()) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount / 1024
    }

    val hasRichText get() = Build.VERSION.SDK_INT >= 35

    private fun measureRest(from: Int) {
        exec.execute {
            for (i in from until pageCount) {
                if (closed) return@execute
                val sz = runCatching { renderer.openPage(i).use { SizeF(it.width.toFloat(), it.height.toFloat()) } }.getOrNull() ?: continue
                if (sz != sizes[i]) android.os.Handler(android.os.Looper.getMainLooper()).post { if (i < sizes.size) sizes[i] = sz }
            }
        }
    }

    /** Render [page] [widthPx] wide (after optional crop/rotation). White paper behind transparent PDFs. */
    suspend fun render(page: Int, widthPx: Int, crop: RectF? = null, rotation: Int = 0): Bitmap? = withContext(thread) {
        if (closed || page !in 0 until pageCount || widthPx <= 0) return@withContext null
        val key = "$page:$widthPx:${crop?.toShortString()}:$rotation"
        cache.get(key)?.takeIf { !it.isRecycled }?.let { return@withContext it }
        runCatching {
            renderer.openPage(page).use { p ->
                val area = crop ?: RectF(0f, 0f, p.width.toFloat(), p.height.toFloat())
                val rotated = rotation % 180 != 0
                val contentW = if (rotated) area.height() else area.width()
                val contentH = if (rotated) area.width() else area.height()
                val scale = widthPx / contentW
                val h = (contentH * scale).toInt().coerceAtLeast(1)
                // keep any single page under ~12 megapixels
                val maxPx = 12_000_000f
                val f = if (widthPx.toFloat() * h > maxPx) kotlin.math.sqrt(maxPx / (widthPx.toFloat() * h)) else 1f
                val bw = (widthPx * f).toInt().coerceAtLeast(1); val bh = (h * f).toInt().coerceAtLeast(1)
                val s = scale * f
                val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                val m = Matrix()
                m.postTranslate(-area.left, -area.top)
                m.postScale(s, s)
                when (rotation % 360) {
                    90 -> { m.postRotate(90f); m.postTranslate(area.height() * s, 0f) }
                    180 -> { m.postRotate(180f); m.postTranslate(area.width() * s, area.height() * s) }
                    270 -> { m.postRotate(270f); m.postTranslate(0f, area.width() * s) }
                }
                p.render(bmp, null, m, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                cache.put(key, bmp)
                bmp
            }
        }.onFailure { Log.w("PageOneDoc", "render $page failed: ${it.message}") }.getOrNull()
    }

    /** Tight box around the ink on a page (for "crop white margins"), in points. Null = no margins worth cropping. */
    suspend fun contentBounds(page: Int): RectF? = withContext(thread) {
        if (boundsCache.containsKey(page)) return@withContext boundsCache[page]
        val r = runCatching {
            renderer.openPage(page).use { p ->
                val w = 240; val sc = w / p.width.toFloat(); val h = (p.height * sc).toInt().coerceAtLeast(1)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888); bmp.eraseColor(Color.WHITE)
                p.render(bmp, null, Matrix().apply { postScale(sc, sc) }, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h); bmp.recycle()
                var l = w; var t = h; var rr = -1; var b = -1
                for (y in 0 until h) for (x in 0 until w) {
                    val c = px[y * w + x]
                    if (Color.red(c) < 235 || Color.green(c) < 235 || Color.blue(c) < 235) {
                        if (x < l) l = x; if (x > rr) rr = x; if (y < t) t = y; if (y > b) b = y
                    }
                }
                if (rr < 0) null else {
                    val pad = 6f
                    val box = RectF((l / sc - pad).coerceAtLeast(0f), (t / sc - pad).coerceAtLeast(0f),
                        (rr / sc + pad).coerceAtMost(p.width.toFloat()), (b / sc + pad).coerceAtMost(p.height.toFloat()))
                    // only worth it if it trims at least 8% of the width
                    if (box.width() > p.width * 0.92f) null else box
                }
            }
        }.getOrNull()
        boundsCache[page] = r
        r
    }

    /** Text on a page with positions (Android 15+). Empty list on older Android or scanned pages. */
    suspend fun textRuns(page: Int): List<TextRun> = withContext(thread) {
        textCache[page]?.let { return@withContext it }
        val runs = if (Build.VERSION.SDK_INT >= 35) runCatching { textRuns35(page) }.getOrDefault(emptyList()) else emptyList()
        textCache[page] = runs
        runs
    }

    @RequiresApi(35)
    private fun textRuns35(page: Int): List<TextRun> = renderer.openPage(page).use { p ->
        p.textContents.map { TextRun(it.text, it.bounds) }
    }

    /** Plain text of a page (positions not needed): for reflow, read aloud and older-Android search. */
    suspend fun pageText(page: Int): String {
        plainText[page]?.let { return it }
        val runs = textRuns(page)
        val t = if (runs.isNotEmpty()) runs.joinToString("\n") { it.text } else PdfTools.pageText(sourceFile, password, page)
        plainText[page] = t
        return t
    }

    /** Matches of [query] on one page, with where they start in the page text (for snippets) and their rectangles. */
    suspend fun search(page: Int, query: String, matchCase: Boolean, wholeWord: Boolean): List<Match> {
        if (query.isBlank()) return emptyList()
        val text = pageText(page)
        if (Build.VERSION.SDK_INT >= 35) {
            val raw = withContext(thread) { runCatching { renderer.openPage(page).use { p -> p.searchText(query).map { Match(it.textStartIndex, it.bounds) } } }.getOrDefault(emptyList()) }
            if (raw.isNotEmpty() || text.isBlank()) return raw.filter { TextMatch.ok(text, it.start, query, matchCase, wholeWord) }
        }
        return TextMatch.indices(text, query, matchCase, wholeWord).map { Match(it, emptyList()) }
    }

    /** Select text between two points (page points). Android 15+. */
    suspend fun select(page: Int, start: android.graphics.PointF, end: android.graphics.PointF): PageSelectionResult? {
        if (Build.VERSION.SDK_INT < 35) return null
        return withContext(thread) { runCatching { select35(page, start, end) }.getOrNull() }
    }

    @RequiresApi(35)
    private fun select35(page: Int, start: android.graphics.PointF, end: android.graphics.PointF): PageSelectionResult? =
        renderer.openPage(page).use { p ->
            val sel = p.selectContent(
                SelectionBoundary(Point(start.x.toInt(), start.y.toInt())),
                SelectionBoundary(Point(end.x.toInt(), end.y.toInt())),
            ) ?: return null
            val contents = sel.selectedTextContents
            val text = contents.joinToString("") { it.text }
            val rects = contents.flatMap { it.bounds }
            if (text.isBlank() || rects.isEmpty()) null else PageSelectionResult(text, rects)
        }

    /** The word or line under a point (long-press). */
    suspend fun wordAt(page: Int, at: android.graphics.PointF): PageSelectionResult? {
        select(page, at, android.graphics.PointF(at.x + 1f, at.y))?.let { if (it.text.trim().length > 1) return it }
        val runs = textRuns(page)
        val hit = runs.firstOrNull { r -> r.rects.any { it.contains(at.x, at.y) } } ?: return null
        return PageSelectionResult(hit.text, hit.rects)
    }

    fun close() {
        if (closed) return
        closed = true
        exec.execute {
            runCatching { renderer.close() }; runCatching { pfd.close() }
        }
        exec.shutdown()
        cache.evictAll()
    }

    companion object {
        /**
         * Open a PDF. Content from other apps is first copied to a private file when its descriptor can't seek,
         * so huge books still open instantly from WhatsApp/Telegram. Throws [PasswordNeeded].
         */
        fun open(context: Context, ref: DocRef, password: String?): PdfDoc {
            val src: File = ref.path?.let { File(it) }?.takeIf { it.canRead() } ?: copyToCache(context, ref)
            var openFile = src
            if (password != null && Build.VERSION.SDK_INT < 35) {
                // older Android can't pass a password to PdfRenderer: decrypt to a private copy first
                openFile = PdfTools.decryptCopy(context, src, password)
            }
            val pfd = ParcelFileDescriptor.open(openFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = try {
                if (password != null && Build.VERSION.SDK_INT >= 35) open35(pfd, password) else PdfRenderer(pfd)
            } catch (e: SecurityException) {
                pfd.close(); throw PasswordNeeded(wrong = password != null)
            } catch (e: Exception) {
                pfd.close()
                if (password == null && PdfTools.isEncrypted(src)) throw PasswordNeeded(false)
                throw e
            }
            val count = renderer.pageCount
            // first pages now (so the first screen is right), the rest in the background: big books open instantly
            val first = minOf(count, 25)
            val sizes = androidx.compose.runtime.mutableStateListOf<SizeF>()
            for (i in 0 until first) renderer.openPage(i).use { sizes += SizeF(it.width.toFloat(), it.height.toFloat()) }
            val guess = sizes.lastOrNull() ?: SizeF(595f, 842f)
            for (i in first until count) sizes += guess
            val fp = FileInputStream(src).use { Library.fingerprintStream(it, src.length()) }
            return PdfDoc(ref, pfd, renderer, count, sizes, fp, src, password).also { if (count > first) it.measureRest(first) }
        }

        @RequiresApi(35)
        private fun open35(pfd: ParcelFileDescriptor, password: String) =
            PdfRenderer(pfd, LoadParams.Builder().setPassword(password).build())

        private fun copyToCache(context: Context, ref: DocRef): File {
            val dir = File(context.cacheDir, "open").apply { mkdirs() }
            // keep the cache small: remove copies older than a day
            dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > 86_400_000L }?.forEach { it.delete() }
            val out = File(dir, "${ref.uri.hashCode().toUInt()}.pdf")
            context.contentResolver.openInputStream(Uri.parse(ref.uri))?.use { input ->
                FileOutputStream(out).use { input.copyTo(it, 1 shl 16) }
            } ?: throw IllegalStateException("Can't read this file")
            return out
        }
    }
}

/** Case / whole-word matching helpers (unit-tested). */
object TextMatch {
    fun ok(text: String, start: Int, q: String, matchCase: Boolean, wholeWord: Boolean): Boolean {
        if (start < 0 || start + q.length > text.length) return true    // can't check: keep it
        val hit = text.substring(start, start + q.length)
        if (matchCase && hit != q) return false
        if (wholeWord) {
            val before = if (start > 0) text[start - 1] else ' '
            val after = if (start + q.length < text.length) text[start + q.length] else ' '
            if (before.isLetterOrDigit() || after.isLetterOrDigit()) return false
        }
        return true
    }

    fun indices(text: String, q: String, matchCase: Boolean, wholeWord: Boolean): List<Int> {
        if (q.isEmpty()) return emptyList()
        val out = ArrayList<Int>()
        var i = text.indexOf(q, 0, ignoreCase = !matchCase)
        while (i >= 0) {
            if (ok(text, i, q, matchCase, wholeWord)) out += i
            i = text.indexOf(q, i + 1, ignoreCase = !matchCase)
        }
        return out
    }
}
