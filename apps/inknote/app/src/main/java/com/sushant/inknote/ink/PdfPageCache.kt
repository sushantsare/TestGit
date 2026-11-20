package com.sushant.inknote.ink

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/** Renders PDF pages to bitmaps off the main thread, one renderer at a time, with an LRU cache. */
object PdfPageCache {
    private val exec = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(48 * 1024 * 1024) { override fun sizeOf(k: String, v: Bitmap) = v.byteCount }
    private val pending = HashSet<String>()

    private fun bucket(w: Int) = ((w.coerceIn(200, 2200) + 199) / 200) * 200
    private fun key(f: File, page: Int, w: Int) = "${f.absolutePath}#$page@${bucket(w)}"

    fun get(f: File, page: Int, widthPx: Int): Bitmap? = cache.get(key(f, page, widthPx))

    fun request(f: File, page: Int, widthPx: Int, done: () -> Unit) {
        val k = key(f, page, widthPx)
        if (cache.get(k) != null) { done(); return }
        synchronized(pending) { if (!pending.add(k)) return }
        exec.execute {
            val bmp = renderNow(f, page, bucket(widthPx))
            synchronized(pending) { pending.remove(k) }
            if (bmp != null) { cache.put(k, bmp); main.post(done) }
        }
    }

    /** Synchronous render (used by export, OCR and tests). */
    fun renderNow(f: File, page: Int, widthPx: Int): Bitmap? = runCatching {
        ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { r ->
                r.openPage(page).use { p ->
                    val h = (widthPx * p.height / p.width.toFloat()).toInt().coerceAtLeast(1)
                    val bmp = Bitmap.createBitmap(widthPx, h, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    p.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    bmp
                }
            }
        }
    }.getOrNull()

    fun clear() = cache.evictAll()
}
