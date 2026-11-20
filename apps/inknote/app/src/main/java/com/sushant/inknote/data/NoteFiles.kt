package com.sushant.inknote.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import java.io.File

/** All note data on disk lives in app-private storage: notes/<noteId>/{pages,img,audio,pdf}. */
class NoteFiles(private val context: Context) {
    val root = File(context.filesDir, "notes")
    val thumbs = File(context.cacheDir, "thumbs")

    fun noteDir(noteId: Long) = File(root, noteId.toString())
    fun pageFile(noteId: Long, pageId: Long) = File(noteDir(noteId), "pages/$pageId.ink")
    fun imgDir(noteId: Long) = File(noteDir(noteId), "img").apply { mkdirs() }
    fun audioDir(noteId: Long) = File(noteDir(noteId), "audio").apply { mkdirs() }
    fun pdfDir(noteId: Long) = File(noteDir(noteId), "pdf").apply { mkdirs() }
    fun img(noteId: Long, name: String) = File(noteDir(noteId), "img/$name")
    fun audio(noteId: Long, name: String) = File(noteDir(noteId), "audio/$name")
    fun pdf(noteId: Long, name: String) = File(noteDir(noteId), "pdf/$name")
    fun thumb(noteId: Long) = File(thumbs, "$noteId.png")

    private val cache = object : LruCache<String, PageContent>(64) {}

    fun readPage(noteId: Long, pageId: Long): PageContent {
        val key = "$noteId/$pageId"
        cache.get(key)?.let { return it }
        val f = pageFile(noteId, pageId)
        if (!f.exists()) return PageContent()
        val raw = runCatching { f.readBytes() }.getOrNull() ?: return PageContent()
        val bytes = if (Crypto.isEncrypted(raw)) runCatching { Crypto.decrypt(raw) }.getOrNull() else raw
        val c = PageContent.decode(bytes)
        cache.put(key, c)
        return c
    }

    fun writePage(noteId: Long, pageId: Long, content: PageContent, locked: Boolean) {
        cache.put("$noteId/$pageId", content)
        val data = content.encode()
        atomicWrite(pageFile(noteId, pageId), if (locked) Crypto.encrypt(data) else data)
    }

    fun deletePage(noteId: Long, pageId: Long) { cache.remove("$noteId/$pageId"); pageFile(noteId, pageId).delete() }

    fun readImageBytes(noteId: Long, name: String): ByteArray? {
        val f = img(noteId, name); if (!f.exists()) return null
        val raw = runCatching { f.readBytes() }.getOrNull() ?: return null
        return if (Crypto.isEncrypted(raw)) runCatching { Crypto.decrypt(raw) }.getOrNull() else raw
    }

    private val bmpCache = object : LruCache<String, Bitmap>(24 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun imageBitmap(noteId: Long, name: String, maxSide: Int = 1600): Bitmap? {
        val key = "$noteId/$name/$maxSide"
        bmpCache.get(key)?.let { return it }
        val b = readImageBytes(noteId, name) ?: return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(b, 0, b.size, o)
        var s = 1; while (o.outWidth / (s * 2) >= maxSide || o.outHeight / (s * 2) >= maxSide) s *= 2
        val bmp = BitmapFactory.decodeByteArray(b, 0, b.size, BitmapFactory.Options().apply { inSampleSize = s }) ?: return null
        bmpCache.put(key, bmp); return bmp
    }

    fun writeImage(noteId: Long, name: String, bytes: ByteArray, locked: Boolean) =
        atomicWrite(img(noteId, name).also { it.parentFile?.mkdirs() }, if (locked) Crypto.encrypt(bytes) else bytes)

    /** Re-encrypts or decrypts every page and image of a note when it is locked or unlocked. */
    fun setLocked(noteId: Long, locked: Boolean) {
        val dirs = listOf("pages", "img", "pdf", "audio").map { File(noteDir(noteId), it) }
        for (d in dirs) d.listFiles()?.forEach { f ->
            val raw = f.readBytes()
            val enc = Crypto.isEncrypted(raw)
            if (locked && !enc) atomicWrite(f, Crypto.encrypt(raw))
            if (!locked && enc) atomicWrite(f, Crypto.decrypt(raw))
        }
        cache.evictAll(); bmpCache.evictAll(); if (locked) clearPlain()
    }

    private val plainDir get() = File(context.cacheDir, "plain")

    /** A readable copy of a note file: the file itself, or (for locked notes) a temporary decrypted copy in app cache. */
    fun readable(f: File): File {
        if (!f.exists()) return f
        val head = ByteArray(8)
        val n = runCatching { f.inputStream().use { it.read(head) } }.getOrDefault(0)
        if (n < 8 || !Crypto.isEncrypted(head + ByteArray(16))) return f
        val out = File(plainDir.apply { mkdirs() }, "${f.parentFile?.parentFile?.name}_${f.name}")
        if (!out.exists()) runCatching { atomicWrite(out, Crypto.decrypt(f.readBytes())) }
        return out
    }

    /** Removes decrypted temporary copies (when locked notes lock again). */
    fun clearPlain() { plainDir.deleteRecursively() }

    fun deleteNote(noteId: Long) { noteDir(noteId).deleteRecursively(); thumb(noteId).delete(); cache.evictAll() }

    fun clearCaches() { cache.evictAll(); bmpCache.evictAll() }

    companion object {
        fun atomicWrite(f: File, bytes: ByteArray) {
            f.parentFile?.mkdirs()
            val tmp = File(f.parentFile, f.name + ".tmp")
            tmp.writeBytes(bytes)
            if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
        }
    }
}
