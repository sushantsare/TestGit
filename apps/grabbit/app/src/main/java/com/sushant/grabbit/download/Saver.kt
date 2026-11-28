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
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "avif" -> "image/avif"
        else -> MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext.lowercase()) ?: "application/octet-stream"
    }

    private val SUBS = setOf("srt", "vtt", "ass")
    private val IMAGE_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "avif")

    /** True if a file with this name already exists in that phone folder (Android 10+). */
    @androidx.annotation.RequiresApi(29)
    fun taken(ctx: Context, name: String, relPath: String, exceptUri: Uri? = null): Boolean = runCatching {
        val except = exceptUri?.lastPathSegment?.toLongOrNull() ?: -1L
        ctx.contentResolver.query(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY),
            arrayOf(MediaStore.MediaColumns._ID),
            "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?",
            arrayOf(name, relPath), null,
        )?.use { c -> var hit = false; while (c.moveToNext()) if (c.getLong(0) != except) hit = true; hit } ?: false
    }.getOrDefault(false)

    /**
     * Saves every finished media file. With [keepSubs], subtitle files are also saved (to Download/Grabbit,
     * next to nothing else, since Movies/ only accepts video) but they don't appear in the downloads list.
     */
    fun saveAll(
        ctx: Context, dir: File, title: String, site: String, thumb: String, url: String,
        keepSubs: Boolean = false, name: String = "",
        onProgress: (done: Long, total: Long) -> Unit = { _, _ -> },
        folder: String = "",
        images: Boolean = false,
        sub: String = "",
    ): List<SavedItem> {
        val all = dir.walkTopDown().filter { it.isFile && it.length() > 0 }.sortedBy { it.name }.toList()
        val files = all.filter { val e = it.extension.lowercase(); e !in SKIP || (images && e in IMAGE_EXT) }
        val single = files.size == 1
        val grand = files.sumOf { it.length() }.coerceAtLeast(1)
        var before = 0L
        val custom = FileNames.clean(name)
        val mainBase = custom.ifBlank { FileNames.clean(title) }.ifBlank { "Grabbit" }
        val items = files.mapIndexed { i, f ->
            val base = when {
                single -> mainBase
                images -> FileNames.numbered(custom.ifBlank { mainBase }, i + 1, files.size)
                custom.isNotBlank() -> FileNames.numbered(custom, i + 1, files.size)
                else -> FileNames.clean(
                    f.nameWithoutExtension.replace(Regex("""\s*\[[^\]]+]$"""), "").removePrefix(DownloadArgs.CHAPTER_PREFIX)
                ).ifBlank { FileNames.numbered(mainBase, i + 1, files.size) }
            }
            val len = f.length()
            val report: (Long) -> Unit = { d -> onProgress(before + d, grand) }
            (if (folder.isNotBlank()) saveToFolder(ctx, f, base, folder, sub, site, thumb, url, report)
            else save(ctx, f, base, base, site, thumb, url, report, sub)).also { before += len }
        }
        if (keepSubs) all.filter { it.extension.lowercase() in SUBS && it.exists() }.forEach { f ->
            // "title [id].en.srt" -> "<name>.en.srt"
            val lang = f.nameWithoutExtension.substringAfterLast('.', "").takeIf { it.length in 2..8 && ' ' !in it }
            val subBase = if (lang != null) "$mainBase.$lang" else mainBase
            runCatching {
                if (folder.isNotBlank()) saveToFolder(ctx, f, subBase, folder, sub, site, thumb, url) {}
                else save(ctx, f, mainBase, subBase, site, thumb, url, sub = sub)
            }
        }
        return items
    }

    /** Saves into a folder the user picked (Android's folder picker / SAF). */
    private fun saveToFolder(
        ctx: Context, f: File, base: String, folder: String, sub: String, site: String, thumb: String, url: String, onBytes: (Long) -> Unit,
    ): SavedItem {
        val treeUri = Uri.parse(folder)
        val tree = androidx.documentfile.provider.DocumentFile.fromTreeUri(ctx, treeUri)?.takeIf { it.canWrite() }
            ?: throw IOException("The chosen folder isn't available any more. Pick it again in Save to.")
        // Playlist videos go into a sub-folder named after the playlist
        val target = if (sub.isBlank()) tree else (tree.findFile(sub)?.takeIf { it.isDirectory } ?: tree.createDirectory(sub)
            ?: throw IOException("Couldn't make the playlist folder"))
        val mime = mimeOf(f.extension)
        val wanted = FileNames.unique(base, f.extension) { target.findFile(it) != null }
        val doc = target.createFile(mime, wanted) ?: throw IOException("Couldn't create the file in that folder")
        try {
            (ctx.contentResolver.openOutputStream(doc.uri, "w") ?: throw IOException("Couldn't open that folder")).use { copy(f, it, onBytes) }
        } catch (e: Exception) {
            runCatching { doc.delete() }
            throw e
        }
        val name = doc.name ?: wanted
        // Let the gallery / music apps find it straight away
        runCatching {
            val docId = android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            if (docId.startsWith("primary:")) {
                val dir = docId.removePrefix("primary:")
                val path = File(Environment.getExternalStorageDirectory(), if (dir.isBlank()) name else "$dir/$name").absolutePath
                MediaScannerConnection.scanFile(ctx, arrayOf(path), arrayOf(mime), null)
            }
        }
        val size = f.length()
        f.delete()
        return SavedItem(
            id = UUID.randomUUID().toString(), title = base, site = site, thumb = thumb, uri = doc.uri.toString(),
            fileName = name, mime = mime, size = size, time = System.currentTimeMillis(), sourceUrl = url, path = null,
        )
    }

    /** Copies with a 1 MB buffer and reports bytes written. */
    private fun copy(f: File, out: java.io.OutputStream, onBytes: (Long) -> Unit) {
        val buf = ByteArray(1 shl 20)
        var done = 0L
        var lastReport = 0L
        f.inputStream().use { inp ->
            while (true) {
                val n = inp.read(buf)
                if (n < 0) break
                out.write(buf, 0, n)
                done += n
                val now = android.os.SystemClock.elapsedRealtime()
                if (now - lastReport > 300) { lastReport = now; onBytes(done) }
            }
        }
        onBytes(done)
    }

    private fun subPath(sub: String) = if (sub.isBlank()) "" else "/$sub"

    private fun save(ctx: Context, f: File, title: String, base: String, site: String, thumb: String, url: String, onBytes: (Long) -> Unit = {}, sub: String = ""): SavedItem {
        val mime = mimeOf(f.extension)
        val isAudio = mime.startsWith("audio")
        val isVideo = mime.startsWith("video")
        val isImage = mime.startsWith("image")
        val dirType = when {
            isAudio -> Environment.DIRECTORY_MUSIC
            isVideo -> Environment.DIRECTORY_MOVIES
            isImage -> Environment.DIRECTORY_PICTURES
            else -> Environment.DIRECTORY_DOWNLOADS
        }
        var name = "$base.${f.extension}"
        val uri: Uri
        var path: String? = null

        if (Build.VERSION.SDK_INT >= 29) {
            val vol = MediaStore.VOLUME_EXTERNAL_PRIMARY
            val coll = when {
                isAudio -> MediaStore.Audio.Media.getContentUri(vol)
                isVideo -> MediaStore.Video.Media.getContentUri(vol)
                isImage -> MediaStore.Images.Media.getContentUri(vol)
                else -> MediaStore.Downloads.getContentUri(vol)
            }
            val cr = ctx.contentResolver
            name = FileNames.unique(base, f.extension) { n -> taken(ctx, n, "$dirType/Grabbit${subPath(sub)}/") }
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, "$dirType/Grabbit${subPath(sub)}")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            uri = cr.insert(coll, values) ?: throw IOException("Couldn't create the file in phone storage")
            try {
                (cr.openOutputStream(uri) ?: throw IOException("Couldn't open phone storage")).use { out ->
                    copy(f, out, onBytes)
                }
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                cr.update(uri, values, null, null)
                // Android may still adjust the name; keep what was really saved
                cr.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                    if (c.moveToFirst()) c.getString(0)?.let { name = it }
                }
            } catch (e: Exception) {
                runCatching { cr.delete(uri, null, null) }
                throw e
            }
        } else {
            @Suppress("DEPRECATION")
            val folder = File(Environment.getExternalStoragePublicDirectory(dirType), "Grabbit").apply { mkdirs() }
            name = FileNames.unique(base, f.extension) { File(folder, it).exists() }
            val dest = File(folder, name)
            dest.outputStream().use { copy(f, it, onBytes) }
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
