package com.sushant.gallery.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * File operations on shared storage (needs "All files access" on Android 11+, or the storage permission on 8–10).
 * Every operation is plain file I/O followed by a media-scanner update, so it never shows system popups.
 */
class FileOps(private val ctx: Context, private val media: MediaSource) {
    private val cr = ctx.contentResolver

    val trashDir: File
        get() = File(Environment.getExternalStorageDirectory(), TRASH_DIR).apply {
            mkdirs(); runCatching { File(this, ".nomedia").createNewFile() }
        }

    /** Moves the file into the bin folder. Returns the bin entry (not yet saved) or null on failure. */
    suspend fun moveToTrash(item: MediaItem, wasFav: Boolean, now: Long = System.currentTimeMillis()): TrashEntity? {
        val src = File(item.path)
        if (!src.exists()) return null
        val dest = File(trashDir, "${now}_${src.name}")
        if (!moveFile(src, dest)) return null
        forgetFromMediaStore(item)
        media.scan(listOf(item.path))
        return TrashEntity(
            originalPath = item.path, trashPath = dest.path, name = item.name, mime = item.mime, isVideo = item.isVideo,
            size = item.size, dateTaken = item.dateTaken, durationMs = item.durationMs, wasFavourite = wasFav, deletedAt = now,
        )
    }

    /** Puts a binned file back where it was (or next to it if the name is taken). Returns the restored path. */
    suspend fun restore(e: TrashEntity): String? {
        val src = File(e.trashPath)
        if (!src.exists()) return null
        val orig = File(e.originalPath)
        val parent = orig.parentFile ?: return null
        parent.mkdirs()
        val name = Formats.uniqueName(orig.name) { File(parent, it).exists() }
        val dest = File(parent, name)
        if (!moveFile(src, dest)) return null
        media.scan(listOf(dest.path))
        return dest.path
    }

    fun deleteForever(e: TrashEntity): Boolean {
        val f = File(e.trashPath)
        return !f.exists() || f.delete()
    }

    /** Move (or copy) into [folder]. Returns new path. */
    suspend fun transfer(item: MediaItem, folder: File, copy: Boolean): String? {
        val src = File(item.path)
        if (!src.exists()) return null
        if (src.parentFile?.path == folder.path && !copy) return src.path
        folder.mkdirs()
        val name = Formats.uniqueName(src.name) { File(folder, it).exists() }
        val dest = File(folder, name)
        val ok = if (copy) copyFile(src, dest) else moveFile(src, dest)
        if (!ok) return null
        if (!copy) forgetFromMediaStore(item)
        media.scan(if (copy) listOf(dest.path) else listOf(item.path, dest.path))
        return dest.path
    }

    fun albumFolder(name: String): File =
        File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), name.trim().replace('/', '-'))

    /** Rotates 90° clockwise. JPEG: lossless (EXIF orientation). Others: re-encoded. */
    suspend fun rotate(item: MediaItem, degrees: Int = 90): Boolean = runCatching {
        val f = File(item.path)
        if (item.isJpeg) {
            val exif = ExifInterface(f.path)
            exif.rotate(degrees)
            exif.saveAttributes()
        } else {
            val bmp = BitmapFactory.decodeFile(f.path) ?: return@runCatching false
            val m = Matrix().apply { postRotate(degrees.toFloat()) }
            val r = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
            val fmt = if (item.mime == "image/png") Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG
            FileOutputStream(f).use { r.compress(fmt, 95, it) }
        }
        media.scan(listOf(f.path))
        true
    }.getOrDefault(false)

    /** A shareable content:// uri. When [stripLocation] is on, JPEG/WebP/PNG photos are copied without GPS data. */
    fun shareUri(item: MediaItem, stripLocation: Boolean): Uri {
        if (stripLocation && !item.isVideo && hasExif(item)) {
            runCatching {
                val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
                val copy = File(dir, item.name)
                File(item.path).copyTo(copy, overwrite = true)
                val exif = ExifInterface(copy.path)
                GPS_TAGS.forEach { exif.setAttribute(it, null) }
                exif.saveAttributes()
                return FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", copy)
            }
        }
        return contentUri(item)
    }

    fun contentUri(item: MediaItem): Uri {
        if (item.uri.scheme == "content") return item.uri
        return runCatching { FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", File(item.path)) }.getOrDefault(item.uri)
    }

    fun clearShareCache() { File(ctx.cacheDir, "share").deleteRecursively() }

    private fun hasExif(item: MediaItem) = item.mime in setOf("image/jpeg", "image/png", "image/webp", "image/heic", "image/heif")

    private fun forgetFromMediaStore(item: MediaItem) {
        if (item.id <= 0) return
        runCatching {
            val base = if (item.isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            cr.delete(ContentUris.withAppendedId(base, item.id), null, null)
        }
    }

    companion object {
        const val TRASH_DIR = ".gallery_trash"
        val GPS_TAGS = listOf(
            ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE,
            ExifInterface.TAG_GPS_LONGITUDE_REF, ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
            ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP, ExifInterface.TAG_GPS_PROCESSING_METHOD,
            ExifInterface.TAG_GPS_AREA_INFORMATION, ExifInterface.TAG_GPS_SPEED, ExifInterface.TAG_GPS_SPEED_REF,
            ExifInterface.TAG_GPS_IMG_DIRECTION, ExifInterface.TAG_GPS_IMG_DIRECTION_REF, ExifInterface.TAG_GPS_DEST_LATITUDE,
            ExifInterface.TAG_GPS_DEST_LONGITUDE, ExifInterface.TAG_GPS_MAP_DATUM,
        )

        /** Rename when possible (same volume), else copy + delete. Keeps the modified time. */
        fun moveFile(src: File, dest: File): Boolean {
            val mod = src.lastModified()
            if (src.renameTo(dest)) { dest.setLastModified(mod); return true }
            if (!copyFile(src, dest)) return false
            return if (src.delete()) true else { dest.delete(); false }
        }

        fun copyFile(src: File, dest: File): Boolean = runCatching {
            src.inputStream().use { i -> FileOutputStream(dest).use { o -> i.copyTo(o, 1 shl 16) } }
            if (dest.length() != src.length()) { dest.delete(); return@runCatching false }
            dest.setLastModified(src.lastModified())
            true
        }.getOrDefault(false)
    }
}
