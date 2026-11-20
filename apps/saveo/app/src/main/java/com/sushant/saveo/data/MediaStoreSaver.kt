package com.sushant.saveo.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.InputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Copies files into the shared gallery album "Saveo" (Pictures/Saveo, Movies/Saveo). No permission needed on Android 10+. */
class MediaStoreSaver(private val ctx: Context) {
    private val cr get() = ctx.contentResolver

    fun sha256(uri: Uri): String {
        val md = MessageDigest.getInstance("SHA-256")
        open(uri).use { input ->
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n <= 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    fun open(uri: Uri): InputStream =
        if (uri.scheme == "file") File(uri.path!!).inputStream()
        else cr.openInputStream(uri) ?: error("Cannot open file")

    fun durationMs(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            if (uri.scheme == "file") r.setDataSource(uri.path) else r.setDataSource(ctx, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) { 0L } finally { runCatching { r.release() } }
    }

    /** Writes [input] into the gallery. Returns the new content URI. */
    fun insert(input: () -> InputStream, originalName: String, kind: MediaKind, mime: String, takenAt: Long, hashTag: String): Uri {
        val ext = MediaTypes.extension(originalName).ifBlank { if (kind == MediaKind.VIDEO) "mp4" else "jpg" }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(if (takenAt > 0) takenAt else System.currentTimeMillis()))
        val display = "Saveo_${stamp}_${hashTag.take(6)}.$ext"
        val isVideo = kind == MediaKind.VIDEO
        return if (Build.VERSION.SDK_INT >= 29) {
            val collection = if (isVideo) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            else MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val v = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, display)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, if (isVideo) "${Environment.DIRECTORY_MOVIES}/Saveo" else "${Environment.DIRECTORY_PICTURES}/Saveo")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
                if (takenAt > 0) put(MediaStore.MediaColumns.DATE_TAKEN, takenAt)
            }
            val uri = cr.insert(collection, v) ?: error("Gallery is not available")
            try {
                cr.openOutputStream(uri)?.use { out -> input().use { it.copyTo(out) } } ?: error("Cannot write to gallery")
                cr.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
                uri
            } catch (e: Exception) {
                runCatching { cr.delete(uri, null, null) }
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(if (isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES), "Saveo")
            dir.mkdirs()
            val file = File(dir, display)
            file.outputStream().use { out -> input().use { it.copyTo(out) } }
            val v = ContentValues().apply {
                @Suppress("DEPRECATION") put(MediaStore.MediaColumns.DATA, file.absolutePath)
                put(MediaStore.MediaColumns.DISPLAY_NAME, display)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                if (takenAt > 0) put(MediaStore.Images.ImageColumns.DATE_TAKEN, takenAt)
            }
            val collection = if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            cr.insert(collection, v) ?: Uri.fromFile(file)
        }
    }

    fun exists(uri: Uri): Boolean = try {
        if (uri.scheme == "file") File(uri.path!!).exists()
        else cr.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.moveToFirst() } ?: false
    } catch (_: SecurityException) { true } catch (_: Exception) { false }

    /** Deletes a gallery item Saveo created. Throws SecurityException if Android needs the user's OK. */
    fun delete(uri: Uri): Boolean {
        if (uri.scheme == "file") return File(uri.path!!).delete()
        if (Build.VERSION.SDK_INT < 29) {
            // Also remove the real file on old Android
            cr.query(uri, arrayOf(@Suppress("DEPRECATION") MediaStore.MediaColumns.DATA), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0)?.let { p -> File(p).delete() }
            }
        }
        return cr.delete(uri, null, null) > 0 || !exists(uri)
    }

    fun idOf(uri: Uri): Long = runCatching { ContentUris.parseId(uri) }.getOrDefault(-1)
}
