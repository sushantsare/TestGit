package com.sushant.grabbit.download

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.sushant.grabbit.data.SavedItem
import java.io.File
import java.io.IOException
import java.util.UUID

/** Moves finished files from app cache into the phone's public Movies/Music folders. */
object Saver {
    private val SKIP = setOf("part", "ytdl", "json", "temp", "tmp", "jpg", "jpeg", "webp", "png", "vtt", "srt", "ass", "description", "txt")

    fun mimeOf(ext: String): String = when (ext.lowercase()) {
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "mkv" -> "video/x-matroska"
        "mov" -> "video/quicktime"
        "3gp" -> "video/3gpp"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "aac" -> "audio/aac"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
    }

    private val SUBS = setOf("srt", "vtt", "ass")

    /**
     * Saves every finished media file. With [keepSubs], subtitle files are also saved (to Download/Grabbit,
     * next to nothing else, since Movies/ only accepts video) but they don't appear in the downloads list.
     */
    fun saveAll(ctx: Context, dir: File, title: String, site: String, thumb: String, url: String, keepSubs: Boolean = false): List<SavedItem> {
        val all = dir.walkTopDown().filter { it.isFile && it.length() > 0 }.sortedBy { it.name }.toList()
        val files = all.filter { it.extension.lowercase() !in SKIP }
        val single = files.size == 1
        val items = files.map { f ->
            val cleanName = f.nameWithoutExtension.replace(Regex("""\s*\[[^\]]+]$"""), "")
                .removePrefix(DownloadArgs.CHAPTER_PREFIX)
            save(ctx, f, if (single) title else cleanName, site, thumb, url)
        }
        if (keepSubs) all.filter { it.extension.lowercase() in SUBS && it.exists() }.forEach { f ->
            runCatching { save(ctx, f, f.nameWithoutExtension, site, thumb, url) }
        }
        return items
    }

    private fun save(ctx: Context, f: File, title: String, site: String, thumb: String, url: String): SavedItem {
        val mime = mimeOf(f.extension)
        val isAudio = mime.startsWith("audio")
        val isVideo = mime.startsWith("video")
        val dirType = when {
            isAudio -> Environment.DIRECTORY_MUSIC
            isVideo -> Environment.DIRECTORY_MOVIES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        val name = f.name
        val uri: Uri
        var path: String? = null

        if (Build.VERSION.SDK_INT >= 29) {
            val vol = MediaStore.VOLUME_EXTERNAL_PRIMARY
            val coll = when {
                isAudio -> MediaStore.Audio.Media.getContentUri(vol)
                isVideo -> MediaStore.Video.Media.getContentUri(vol)
                else -> MediaStore.Downloads.getContentUri(vol)
            }
            val cr = ctx.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirType/Grabbit")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            uri = cr.insert(coll, values) ?: throw IOException("Couldn't create the file in phone storage")
            try {
                (cr.openOutputStream(uri) ?: throw IOException("Couldn't open phone storage")).use { out ->
                    f.inputStream().use { it.copyTo(out, 256 * 1024) }
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                cr.update(uri, values, null, null)
            } catch (e: Exception) {
                runCatching { cr.delete(uri, null, null) }
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val base = File(Environment.getExternalStoragePublicDirectory(dirType), "Grabbit").apply { mkdirs() }
            var dest = File(base, name)
            var n = 1
            while (dest.exists()) dest = File(base, "${f.nameWithoutExtension} (${n++}).${f.extension}")
            f.copyTo(dest)
            path = dest.absolutePath
            MediaScannerConnection.scanFile(ctx, arrayOf(dest.absolutePath), arrayOf(mime), null)
            uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", dest)
        }
        val size = f.length()
        f.delete()
        return SavedItem(
            id = UUID.randomUUID().toString(),
            title = title,
            site = site,
            thumb = thumb,
            uri = uri.toString(),
            fileName = name,
            mime = mime,
            size = size,
            time = System.currentTimeMillis(),
            sourceUrl = url,
            path = path,
        )
    }
}
