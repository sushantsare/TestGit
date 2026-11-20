package com.sushant.inknote.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sushant.inknote.InkApp
import com.sushant.inknote.data.SearchFts
import com.sushant.inknote.data.Source
import com.sushant.inknote.ink.PdfPageCache
import com.sushant.inknote.ml.Handwriting
import com.sushant.inknote.ml.Ocr
import java.io.File
import java.util.concurrent.TimeUnit

/** Builds the offline search index: titles, typed text, handwriting, text in photos and PDF pages. */
class IndexWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result = if (Indexer.run(applicationContext as InkApp) { isStopped }) Result.success() else Result.retry()

    companion object {
        fun schedule(context: Context, delaySec: Long = 4) {
            WorkManager.getInstance(context).enqueueUniqueWork("index", ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<IndexWorker>().setInitialDelay(delaySec, TimeUnit.SECONDS).build())
        }
    }
}

object Indexer {
    /** Indexes every changed note. Returns false when stopped early. */
    suspend fun run(app: InkApp, stopped: () -> Boolean = { false }): Boolean {
        val repo = app.repo
        val ocrDir = File(app.filesDir, "ocr").apply { mkdirs() }
        val inkReady = Handwriting.isDownloaded()
        for (n in repo.notesNeedingIndex()) {
            if (stopped()) return false
            if (n.locked) { repo.replaceIndex(n.id, emptyList()); continue }
            val rows = ArrayList<SearchFts>()
            rows += SearchFts(n.id, -1, 0, Source.TITLE, n.title)
            for (p in repo.pages(n.id)) {
                val c = repo.readPage(n.id, p.id)
                c.texts.forEach { rows += SearchFts(n.id, p.id, p.idx, Source.TYPED, it.plain) }
                for (im in c.images) {
                    val cache = File(ocrDir, "img_${n.id}_${im.file}.txt")
                    val text = if (cache.exists()) cache.readText() else {
                        val bmp = repo.files.imageBitmap(n.id, im.file, 2000)
                        (if (bmp != null) Ocr.read(bmp) else "").also { cache.writeText(it) }
                    }
                    if (text.isNotBlank()) rows += SearchFts(n.id, p.id, p.idx, Source.PHOTO, text)
                }
                p.pdfFile?.let { pdf ->
                    val cache = File(ocrDir, "pdf_${n.id}_${pdf}_${p.pdfPage}.txt")
                    val text = if (cache.exists()) cache.readText() else {
                        val bmp = PdfPageCache.renderNow(repo.files.pdf(n.id, pdf), p.pdfPage, 1600)
                        (if (bmp != null) Ocr.read(bmp).also { bmp.recycle() } else "").also { cache.writeText(it) }
                    }
                    if (text.isNotBlank()) rows += SearchFts(n.id, p.id, p.idx, Source.PDF, text)
                }
                if (inkReady && c.strokes.isNotEmpty()) Handwriting.recognize(c.strokes)?.let { rows += SearchFts(n.id, p.id, p.idx, Source.INK, it) }
            }
            repo.replaceIndex(n.id, rows)
        }
        return true
    }
}

/** Removes notes that have been in Trash for more than 30 days. */
class PurgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as InkApp
        if (app.settings.state.value.autoEmptyTrash) app.repo.purgeExpiredTrash()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("purge", ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<PurgeWorker>(1, TimeUnit.DAYS).build())
        }
    }
}
