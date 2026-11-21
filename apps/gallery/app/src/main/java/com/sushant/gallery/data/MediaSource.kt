package com.sushant.gallery.data

import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.provider.OpenableColumns
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

/** Reads photos and videos from MediaStore (plus dot-folders when "Show hidden files" is on). */
class MediaSource(private val ctx: Context) {
    private val cr = ctx.contentResolver

    fun load(showHidden: Boolean): List<MediaItem> {
        val out = ArrayList<MediaItem>(4096)
        query(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, video = false, out = out)
        query(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, video = true, out = out)
        if (showHidden) {
            val known = out.mapTo(HashSet()) { it.path }
            scanHidden(known, out)
        }
        return out
    }

    @Suppress("DEPRECATION")
    private fun query(base: Uri, video: Boolean, out: MutableList<MediaItem>) {
        val cols = mutableListOf(
            MediaStore.MediaColumns._ID, MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED,
            MediaStore.MediaColumns.WIDTH, MediaStore.MediaColumns.HEIGHT,
            if (video) MediaStore.Video.VideoColumns.DATE_TAKEN else MediaStore.Images.ImageColumns.DATE_TAKEN,
        )
        if (video) cols += MediaStore.Video.VideoColumns.DURATION
        val sel = "${MediaStore.MediaColumns.SIZE} > 0 AND ${MediaStore.MediaColumns.DATA} NOT LIKE ?"
        val args = arrayOf("%/${FileOps.TRASH_DIR}/%")
        runCatching {
            cr.query(base, cols.toTypedArray(), sel, args, null)?.use { c ->
                val iId = c.getColumnIndexOrThrow(cols[0]); val iData = c.getColumnIndexOrThrow(cols[1])
                val iName = c.getColumnIndexOrThrow(cols[2]); val iMime = c.getColumnIndexOrThrow(cols[3])
                val iSize = c.getColumnIndexOrThrow(cols[4]); val iMod = c.getColumnIndexOrThrow(cols[5])
                val iW = c.getColumnIndexOrThrow(cols[6]); val iH = c.getColumnIndexOrThrow(cols[7])
                val iTaken = c.getColumnIndexOrThrow(cols[8]); val iDur = if (video) c.getColumnIndexOrThrow(cols[9]) else -1
                while (c.moveToNext()) {
                    val path = c.getString(iData) ?: continue
                    val id = c.getLong(iId)
                    val mod = c.getLong(iMod) * 1000L
                    val taken = c.getLong(iTaken).takeIf { it > 0 } ?: mod
                    val file = File(path)
                    val folder = file.parent ?: continue
                    out += MediaItem(
                        id = id, uri = ContentUris.withAppendedId(base, id), path = path,
                        name = c.getString(iName) ?: file.name,
                        mime = c.getString(iMime) ?: if (video) "video/mp4" else "image/jpeg",
                        isVideo = video, dateTaken = taken, dateModified = mod, size = c.getLong(iSize),
                        width = c.getInt(iW), height = c.getInt(iH), durationMs = if (iDur >= 0) c.getLong(iDur) else 0L,
                        folder = folder, bucket = Formats.folderName(folder),
                    )
                }
            }
        }
    }

    private fun scanHidden(known: Set<String>, out: MutableList<MediaItem>) {
        val root = Environment.getExternalStorageDirectory()
        var budget = 20_000
        fun walk(dir: File, depth: Int, inHidden: Boolean) {
            if (depth > 6 || budget <= 0) return
            val children = runCatching { dir.listFiles() }.getOrNull() ?: return
            val hiddenHere = inHidden || dir.name.startsWith(".") || children.any { it.name == ".nomedia" }
            for (f in children) {
                if (--budget <= 0) return
                if (f.isDirectory) {
                    if (f.name == FileOps.TRASH_DIR || f.name == ".thumbnails" || f.name == ".Trash" ||
                        (depth == 0 && f.name == "Android")) continue
                    walk(f, depth + 1, hiddenHere)
                } else if (hiddenHere && f.path !in known) {
                    val mime = mimeOf(f.name) ?: continue
                    out += fromFile(f, mime)
                }
            }
        }
        walk(root, 0, false)
    }

    fun fromFile(f: File, mime: String = mimeOf(f.name) ?: "image/jpeg"): MediaItem {
        val folder = f.parent ?: ""
        return MediaItem(
            id = -(f.path.hashCode().toLong() and 0x7fffffff) - 1, uri = Uri.fromFile(f), path = f.path, name = f.name, mime = mime,
            isVideo = mime.startsWith("video"), dateTaken = f.lastModified(), dateModified = f.lastModified(), size = f.length(),
            width = 0, height = 0, durationMs = 0, folder = folder, bucket = Formats.folderName(folder),
        )
    }

    /** Builds an item for a file opened from another app. Returns null if unreadable. */
    @Suppress("DEPRECATION")
    fun fromExternalUri(uri: Uri, mimeHint: String?): MediaItem? = runCatching {
        var name = uri.lastPathSegment ?: "Photo"
        var size = 0L
        var path: String? = null
        cr.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
                c.getColumnIndex(MediaStore.MediaColumns.DATA).takeIf { it >= 0 }?.let { path = c.getString(it) }
            }
        }
        if (uri.scheme == "file") path = uri.path
        val mime = mimeHint ?: cr.getType(uri) ?: mimeOf(name) ?: "image/jpeg"
        val p = path
        if (p != null && File(p).canRead()) return@runCatching fromFile(File(p), mime).copy(uri = uri)
        MediaItem(
            id = -1, uri = uri, path = uri.toString(), name = name, mime = mime, isVideo = mime.startsWith("video"),
            dateTaken = System.currentTimeMillis(), dateModified = 0, size = size, width = 0, height = 0, durationMs = 0,
            folder = "", bucket = "",
        )
    }.getOrNull()

    fun observe(onChange: () -> Unit): ContentObserver {
        val o = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) { onChange() }
        }
        runCatching {
            cr.registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, true, o)
            cr.registerContentObserver(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, true, o)
        }
        return o
    }

    fun unobserve(o: ContentObserver) { runCatching { cr.unregisterContentObserver(o) } }

    /** Asks the media scanner to (re)index or forget these paths; waits up to 10 s. */
    suspend fun scan(paths: List<String>) {
        if (paths.isEmpty()) return
        withTimeoutOrNull(10_000) {
            suspendCancellableCoroutine { cont ->
                val left = AtomicInteger(paths.size)
                MediaScannerConnection.scanFile(ctx, paths.toTypedArray(), null) { _, _ ->
                    if (left.decrementAndGet() == 0 && cont.isActive) cont.resume(Unit)
                }
            }
        }
    }

    companion object {
        private val IMG = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "heif", "bmp", "avif", "dng")
        private val VID = setOf("mp4", "mkv", "webm", "3gp", "mov", "m4v", "avi", "ts")
        fun mimeOf(name: String): String? {
            val ext = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            return when (ext) {
                in IMG -> if (ext == "jpg") "image/jpeg" else "image/$ext"
                in VID -> when (ext) { "mkv" -> "video/x-matroska"; "3gp" -> "video/3gpp"; "mov" -> "video/quicktime"; "m4v" -> "video/mp4"; "ts" -> "video/mp2t"; "avi" -> "video/x-msvideo"; else -> "video/$ext" }
                else -> null
            }
        }
    }
}
