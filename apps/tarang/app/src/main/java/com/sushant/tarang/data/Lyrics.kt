package com.sushant.tarang.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import java.io.File

data class LyricLine(val timeMs: Long, val text: String)

/** Lyrics for one song. synced = has [mm:ss.xx] timings. source = where they came from (shown in the panel). */
data class Lyrics(val lines: List<LyricLine>, val synced: Boolean, val source: String, val raw: String) {
    fun indexAt(posMs: Long): Int = if (!synced) -1 else lines.indexOfLast { it.timeMs <= posMs }
}

object LrcParser {
    private val tag = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
    private val meta = Regex("^\\[(ti|ar|al|by|offset|length|re|ve|au|#):.*]$", RegexOption.IGNORE_CASE)

    fun parse(text: String, source: String): Lyrics? {
        val clean = text.replace("\r", "").trim()
        if (clean.isEmpty()) return null
        var offset = 0L
        Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE).find(clean)?.let { offset = it.groupValues[1].toLongOrNull() ?: 0 }
        val timed = ArrayList<LyricLine>()
        val plain = ArrayList<String>()
        for (rawLine in clean.lines()) {
            val line = rawLine.trim()
            if (meta.matches(line)) continue
            val stamps = tag.findAll(line).toList()
            if (stamps.isEmpty()) { plain += line; continue }
            val words = line.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong(); val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) { 0 -> 0L; 1 -> frac.toLong() * 100; 2 -> frac.toLong() * 10; else -> frac.take(3).toLong() }
                timed += LyricLine((min * 60_000 + sec * 1000 + ms - offset).coerceAtLeast(0), words.ifEmpty { "♪" })
            }
        }
        return if (timed.size >= 2) Lyrics(timed.sortedBy { it.timeMs }, true, source, clean)
        else Lyrics(plain.map { LyricLine(0, it) }.dropWhile { it.text.isBlank() }, false, source, clean).takeIf { it.lines.isNotEmpty() }
    }
}

/**
 * Finds lyrics in this order: typed/imported in Tarang → .lrc file next to the song → lyrics saved inside the file.
 * Nothing is ever made up: no lyrics means an empty state with "Add lyrics".
 */
class LyricsFinder(private val ctx: Context, private val dao: LibraryDao, private val prefs: Prefs) {

    suspend fun find(song: Song): Lyrics? {
        dao.lyrics(song.id)?.let { e -> LrcParser.parse(e.text, e.source)?.let { return it } }
        sidecar(song)?.let { return it }
        return embedded(song)
    }

    private fun sidecar(song: Song): Lyrics? {
        val base = song.fileName.substringBeforeLast('.')
        // 1) Direct file access (Android 8–12 with storage permission; also works on many newer phones for media folders)
        if (song.path.isNotEmpty()) {
            val f = File(song.path.substringBeforeLast('.') + ".lrc")
            runCatching { if (f.canRead()) return LrcParser.parse(f.readText(), ".lrc file next to the song") }
        }
        // 2) The lyrics folder the user picked once (needed on Android 13+ where apps can't read other text files)
        val tree = prefs.now.lyricsTree ?: return null
        return runCatching { findInTree(Uri.parse(tree), "$base.lrc", song.folderName) }.getOrNull()
    }

    private fun findInTree(tree: Uri, want: String, folderHint: String): Lyrics? {
        val cr = ctx.contentResolver
        val rootDoc = DocumentsContract.getTreeDocumentId(tree)
        val queue = ArrayDeque<String>().apply { add(rootDoc) }
        var visited = 0
        while (queue.isNotEmpty() && visited < 400) {
            val docId = queue.removeFirst(); visited++
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId)
            cr.query(children, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE), null, null, null)?.use { c ->
                val dirs = ArrayList<Pair<String, String>>()
                while (c.moveToNext()) {
                    val id = c.getString(0); val name = c.getString(1) ?: ""; val mime = c.getString(2) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) dirs += id to name
                    else if (name.equals(want, ignoreCase = true)) {
                        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, id)
                        val text = cr.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: return null
                        return LrcParser.parse(text, ".lrc file next to the song")
                    }
                }
                // look in the song's own folder first
                dirs.sortedByDescending { it.second.equals(folderHint, true) }.forEach { queue.add(it.first) }
            }
        }
        return null
    }

    private fun embedded(song: Song): Lyrics? {
        org.jaudiotagger.tag.TagOptionSingleton.getInstance().isAndroid = true
        fun read(file: File): String? =
            org.jaudiotagger.audio.AudioFileIO.read(file).tag?.getFirst(org.jaudiotagger.tag.FieldKey.LYRICS)?.takeIf { it.isNotBlank() }
        // 1) straight from the file when Android allows it
        val direct = if (song.path.isNotEmpty()) runCatching { read(File(song.path)) }
            .onFailure { android.util.Log.w("TarangLyrics", "direct read failed: ${it.javaClass.simpleName} ${it.message}") }.getOrNull() else null
        // 2) otherwise from a temporary copy made through Android's content system (always allowed for music)
        val text = direct ?: runCatching {
            val ext = song.fileName.substringAfterLast('.', "mp3").lowercase()
            val tmp = File(ctx.cacheDir, "lyrics_probe.$ext")
            try {
                ctx.contentResolver.openInputStream(song.uri)?.use { inp -> tmp.outputStream().use { inp.copyTo(it) } } ?: return null
                read(tmp)
            } finally { tmp.delete() }
        }.onFailure { android.util.Log.w("TarangLyrics", "copy read failed: ${it.javaClass.simpleName} ${it.message}") }.getOrNull()
        return text?.let { LrcParser.parse(it, "Lyrics saved inside the song") }
    }

    suspend fun save(songId: Long, text: String, source: String) {
        if (text.isBlank()) dao.deleteLyrics(songId) else dao.putLyrics(LyricsEntity(songId, text.trim(), source))
    }

    companion object {
        fun canReadSidecarDirectly() = Build.VERSION.SDK_INT < 33
    }
}
