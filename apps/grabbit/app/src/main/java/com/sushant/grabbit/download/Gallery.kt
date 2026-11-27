package com.sushant.grabbit.download

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.sushant.grabbit.engine.GalleryItem
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/** Photo-post downloads: the chosen items are kept in a small file (too big for job data), then fetched one by one. */
object Gallery {
    const val Q_ORIGINAL = "img"
    const val Q_JPG = "imgjpg"

    fun isGalleryQuality(q: String) = q == Q_ORIGINAL || q == Q_JPG

    private fun file(ctx: Context, key: String) = File(ctx.filesDir, "jobs/$key.json")

    fun save(ctx: Context, key: String, items: List<GalleryItem>) {
        val arr = JSONArray()
        items.forEach { arr.put(JSONObject().put("u", it.url).put("v", it.video).put("t", it.thumb).put("d", it.duration)) }
        file(ctx, key).apply { parentFile?.mkdirs() }.writeText(arr.toString())
    }

    fun load(ctx: Context, key: String): List<GalleryItem> = runCatching {
        val arr = JSONArray(file(ctx, key).readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            GalleryItem(o.getString("u"), o.optBoolean("v"), o.optString("t"), o.optLong("d"))
        }
    }.getOrDefault(emptyList())

    fun delete(ctx: Context, key: String) { file(ctx, key).delete() }

    /**
     * Downloads [url] into [f]. If part of it is already there (paused), asks the server for the rest.
     * [onBytes] gets (done, total or -1).
     */
    fun fetch(url: String, f: File, stopped: () -> Boolean, onBytes: (Long, Long) -> Unit) {
        val have = if (f.exists()) f.length() else 0L
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 30_000
            setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
            if (url.contains("cdninstagram") || url.contains("fbcdn")) setRequestProperty("Referer", "https://www.instagram.com/")
            if (have > 0) setRequestProperty("Range", "bytes=$have-")
        }
        try {
            val code = c.responseCode
            if (code == 416) return // already complete
            if (code !in 200..299) throw java.io.IOException("HTTP $code")
            val append = code == 206 && have > 0
            val total = c.contentLengthLong.let { if (it > 0) it + (if (append) have else 0) else -1 }
            var done = if (append) have else 0L
            c.inputStream.use { inp ->
                FileOutputStream(f, append).use { out ->
                    val buf = ByteArray(256 * 1024)
                    while (true) {
                        if (stopped()) throw InterruptedException("stopped")
                        val n = inp.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        onBytes(done, total)
                    }
                }
            }
        } finally {
            c.disconnect()
        }
    }

    /** "Smaller (JPG)": re-saves a photo as JPEG at quality 90 and returns the new file. */
    fun toJpg(f: File): File {
        if (f.extension.lowercase() in setOf("jpg", "jpeg")) return f
        val bmp = BitmapFactory.decodeFile(f.absolutePath) ?: return f
        val out = File(f.parentFile, f.nameWithoutExtension + ".jpg")
        FileOutputStream(out).use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
        bmp.recycle()
        f.delete()
        return out
    }
}
