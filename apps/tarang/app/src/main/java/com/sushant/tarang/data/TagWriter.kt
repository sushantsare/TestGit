package com.sushant.tarang.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaScannerConnection
import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.AndroidArtwork
import java.io.ByteArrayOutputStream
import java.io.File

data class TagEdit(val title: String, val artist: String, val album: String, val albumArtist: String, val year: String, val genre: String, val cover: Uri?)

/**
 * Writes edited details (and an optional new cover) into the song file itself, so other apps see them too.
 * Works on a temporary copy, then writes the bytes back through Android's content system (allowed after the
 * user tapped Allow on the system prompt). Returns null on success, or a plain message.
 */
object TagWriter {
    fun write(ctx: Context, song: Song, e: TagEdit): String? {
        TagOptionSingleton.getInstance().isAndroid = true
        val ext = song.fileName.substringAfterLast('.', "mp3").lowercase()
        if (ext !in setOf("mp3", "m4a", "mp4", "flac", "ogg", "opus", "wma", "wav", "aif", "aiff", "dsf")) return "This file type can't hold song details"
        val dir = File(ctx.cacheDir, "tagedit").apply { mkdirs() }
        val tmp = File(dir, "edit.$ext")
        return try {
            ctx.contentResolver.openInputStream(song.uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } } ?: return "Couldn't open the file"
            val af = AudioFileIO.read(tmp)
            val tag = af.tagOrCreateAndSetDefault
            fun put(k: FieldKey, v: String) { if (v.isBlank()) runCatching { tag.deleteField(k) } else tag.setField(k, v.trim()) }
            put(FieldKey.TITLE, e.title); put(FieldKey.ARTIST, e.artist); put(FieldKey.ALBUM, e.album)
            put(FieldKey.ALBUM_ARTIST, e.albumArtist); put(FieldKey.YEAR, e.year); put(FieldKey.GENRE, e.genre)
            if (e.cover != null) {
                val bytes = coverBytes(ctx, e.cover) ?: return "Couldn't read that picture"
                val art = AndroidArtwork().apply { binaryData = bytes; mimeType = "image/jpeg"; pictureType = 3 }
                tag.deleteArtworkField(); tag.setField(art)
            }
            af.commit()
            ctx.contentResolver.openOutputStream(song.uri, "wt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } } ?: return "Couldn't save the file"
            if (song.path.isNotEmpty()) MediaScannerConnection.scanFile(ctx, arrayOf(song.path), null, null)
            null
        } catch (t: Throwable) {
            "Couldn't save: ${t.message?.take(80) ?: "unknown problem"}"
        } finally { tmp.delete() }
    }

    /** Writes [text] (.lrc or plain lyrics) into the song file's lyrics field, so any player can show them. */
    fun writeLyrics(ctx: Context, song: Song, text: String): String? {
        TagOptionSingleton.getInstance().isAndroid = true
        val ext = song.fileName.substringAfterLast('.', "mp3").lowercase()
        if (ext !in setOf("mp3", "m4a", "mp4", "flac", "ogg", "opus", "wma", "aif", "aiff", "dsf")) return "This file type can't hold lyrics"
        val dir = File(ctx.cacheDir, "tagedit").apply { mkdirs() }
        val tmp = File(dir, "lyrics.$ext")
        return try {
            ctx.contentResolver.openInputStream(song.uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } } ?: return "Couldn't open the file"
            val af = AudioFileIO.read(tmp)
            val tag = af.tagOrCreateAndSetDefault
            if (text.isBlank()) runCatching { tag.deleteField(FieldKey.LYRICS) } else tag.setField(FieldKey.LYRICS, text.trim())
            af.commit()
            ctx.contentResolver.openOutputStream(song.uri, "wt")?.use { out -> tmp.inputStream().use { it.copyTo(out) } } ?: return "Couldn't save the file"
            if (song.path.isNotEmpty()) MediaScannerConnection.scanFile(ctx, arrayOf(song.path), null, null)
            null
        } catch (t: Throwable) {
            "Couldn't save: ${t.message?.take(80) ?: "unknown problem"}"
        } finally { tmp.delete() }
    }

    private fun coverBytes(ctx: Context, uri: Uri): ByteArray? = runCatching {
        val src = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return null
        val side = minOf(src.width, src.height)
        val sq = Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        val scaled = if (side > 800) Bitmap.createScaledBitmap(sq, 800, 800, true) else sq
        ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()
    }.getOrNull()
}
