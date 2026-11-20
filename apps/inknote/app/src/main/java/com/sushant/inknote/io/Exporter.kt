package com.sushant.inknote.io

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.DocumentsContract
import androidx.core.content.FileProvider
import com.sushant.inknote.data.NoteEntity
import com.sushant.inknote.data.PageEntity
import com.sushant.inknote.data.Repository
import com.sushant.inknote.ink.PagePalette
import com.sushant.inknote.ink.PageRenderer
import com.sushant.inknote.ml.Handwriting
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

enum class ExportFormat(val ext: String, val mime: String) {
    PDF("pdf", "application/pdf"), PNG("png", "image/png"), TEXT("txt", "text/plain"), INKNOTE("inknote", "application/octet-stream")
}

/** Turns a note into shareable files. Exports always use light paper. */
class Exporter(private val context: Context, private val repo: Repository) {
    private val dir get() = File(context.cacheDir, "export").apply { mkdirs() }

    fun safeName(title: String) = title.replace(Regex("[\\\\/:*?\"<>|]"), " ").trim().ifBlank { "Note" }.take(60)

    suspend fun export(note: NoteEntity, format: ExportFormat, onlyPage: PageEntity?): List<File> = withContext(Dispatchers.IO) {
        dir.listFiles()?.forEach { it.delete() }
        val pages = onlyPage?.let { listOf(it) } ?: repo.pages(note.id)
        val base = safeName(note.title)
        when (format) {
            ExportFormat.PDF -> listOf(pdf(note, pages, File(dir, "$base.pdf")))
            ExportFormat.PNG -> pages.mapIndexed { i, p ->
                val f = File(dir, if (pages.size == 1) "$base.png" else "$base - page ${p.idx + 1}.png")
                val bmp = PageRenderer.bitmap(repo.files, note.id, p, repo.readPage(note.id, p.id), 1600, PagePalette.LIGHT)
                FileOutputStream(f).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }; bmp.recycle(); f
            }
            ExportFormat.TEXT -> listOf(File(dir, "$base.txt").apply { writeText(text(note, pages)) })
            ExportFormat.INKNOTE -> listOf(File(dir, "$base.inknote").also { Backup(context, repo).writeNotes(it, listOf(note.id), pinForLocked = null) })
        }
    }

    fun pdf(note: NoteEntity, pages: List<PageEntity>, out: File): File {
        val doc = PdfDocument()
        try {
            for ((i, p) in pages.withIndex()) {
                val k = 0.595f // A4: 1000 page units = 595 pt
                val info = PdfDocument.PageInfo.Builder((p.width * k).toInt(), (p.height * k).toInt(), i + 1).create()
                val pg = doc.startPage(info)
                PageRenderer.draw(pg.canvas, repo.files, note.id, p, repo.files.readPage(note.id, p.id), PagePalette.LIGHT, k, sync = true, hiRes = true)
                doc.finishPage(pg)
            }
            FileOutputStream(out).use { doc.writeTo(it) }
        } finally { doc.close() }
        return out
    }

    private suspend fun text(note: NoteEntity, pages: List<PageEntity>): String {
        val sb = StringBuilder(note.title).append("\n\n")
        for (p in pages) {
            val c = repo.readPage(note.id, p.id)
            if (pages.size > 1) sb.append("— Page ${p.idx + 1} —\n")
            c.texts.sortedBy { it.y }.forEach { sb.append(it.plain.trim()).append("\n\n") }
            if (c.strokes.isNotEmpty()) Handwriting.recognize(c.strokes)?.let { sb.append(it).append("\n\n") }
        }
        return sb.toString().trim() + "\n"
    }

    fun uri(f: File): Uri = FileProvider.getUriForFile(context, "${context.packageName}.files", f)

    fun shareIntent(files: List<File>, format: ExportFormat): Intent {
        val uris = ArrayList(files.map { uri(it) })
        val i = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        i.type = format.mime
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return Intent.createChooser(i, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    /** Writes one file to a location the user picked with the system "Save" screen. */
    suspend fun saveTo(file: File, dest: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.openOutputStream(dest, "wt")?.use { o -> file.inputStream().use { it.copyTo(o) } } != null }.getOrDefault(false)
    }

    /** Writes several files into a folder the user picked. */
    suspend fun saveToTree(files: List<File>, tree: Uri, mime: String): Int = withContext(Dispatchers.IO) {
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        var n = 0
        for (f in files) {
            val doc = runCatching { DocumentsContract.createDocument(context.contentResolver, parent, mime, f.name) }.getOrNull() ?: continue
            if (saveTo(f, doc)) n++
        }
        n
    }
}
