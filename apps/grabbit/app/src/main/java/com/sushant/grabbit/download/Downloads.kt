package com.sushant.grabbit.download

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.yausername.youtubedl_android.YoutubeDL
import java.util.UUID

/** Download job details are also kept as tags so the list can show them before the job starts. */
object Downloads {
    const val TAG = "grabbit"

    data class Job(
        val url: String, val title: String, val thumb: String,
        val site: String, val quality: String, val playlist: Boolean,
        val opts: DlOpts = DlOpts(),
        /** Stays the same across pause/resume and retry, so the half-downloaded part is found again. */
        val key: String = "",
        /** Photo-post items to download (kept in a file by key, not in job data). */
        val items: List<com.sushant.grabbit.engine.GalleryItem> = emptyList(),
        val gallery: Boolean = items.isNotEmpty(),
    )

    fun enqueue(ctx: Context, job0: Job, resume: Boolean = false): UUID {
        val job = if (job0.key.isBlank()) job0.copy(key = UUID.randomUUID().toString()) else job0
        // "Keep my last folder": the next download starts where this one goes
        if (!resume && com.sushant.grabbit.data.Prefs.keepLastFolder) com.sushant.grabbit.data.Folders.select(job.opts.folder)
        if (job.items.isNotEmpty()) Gallery.save(ctx, job.key, job.items)
        val req = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(
                workDataOf(
                    DownloadWorker.K_URL to job.url,
                    DownloadWorker.K_TITLE to job.opts.name.ifBlank { job.title },
                    DownloadWorker.K_THUMB to job.thumb,
                    DownloadWorker.K_SITE to job.site,
                    DownloadWorker.K_QUALITY to job.quality,
                    DownloadWorker.K_PLAYLIST to job.playlist,
                    DownloadWorker.K_OPTS to job.opts.encode(),
                    DownloadWorker.K_KEY to job.key,
                    DownloadWorker.K_RESUME to resume,
                    DownloadWorker.K_GALLERY to job.gallery,
                )
            )
            .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
            .addTag(TAG)
            .addTag("u:${job.url}")
            .addTag("t:${job.opts.name.ifBlank { job.title }.take(200)}")
            .addTag("i:${job.thumb}")
            .addTag("s:${job.site}")
            .addTag("q:${job.quality}")
            .addTag("p:${job.playlist}")
            .addTag("o:${job.opts.encode()}")
            .addTag("k:${job.key}")
            .addTag("g:${if (job.gallery) 1 else 0}")
            .addTag("c:${System.currentTimeMillis()}")
            .build()
        WorkManager.getInstance(ctx).enqueue(req)
        return req.id
    }

    fun cancel(ctx: Context, id: UUID) {
        runCatching { YoutubeDL.getInstance().destroyProcessById(id.toString()) }
        WorkManager.getInstance(ctx).cancelWorkById(id)
    }

    fun jobOf(w: WorkInfo): Job = Job(
        url = w.tag("u:"),
        title = w.tag("t:"),
        thumb = w.tag("i:"),
        site = w.tag("s:"),
        quality = w.tag("q:").ifBlank { "best" },
        playlist = w.tag("p:") == "true",
        opts = DlOpts.decode(w.tag("o:")),
        key = w.tag("k:"),
        gallery = w.tag("g:") == "1",
    )

    /** Folder holding a job's half-finished files. */
    fun partDir(ctx: Context, key: String) = java.io.File(ctx.cacheDir, "dl/$key")

    private fun hasPart(ctx: Context, key: String): Boolean =
        partDir(ctx, key).walkTopDown().any { it.isFile && it.length() > 0 }

    /** Stops a running download but keeps what it already downloaded. */
    fun pause(ctx: Context, w: WorkInfo) {
        val job = jobOf(w).let { if (it.key.isBlank()) it.copy(key = w.id.toString()) else it }
        val p = w.progress
        com.sushant.grabbit.data.Paused.add(
            com.sushant.grabbit.data.PausedItem(
                job, p.getFloat(DownloadWorker.P_PCT, 0f), p.getString(DownloadWorker.P_SIZE).orEmpty(),
                canContinue = hasPart(ctx, job.key), time = System.currentTimeMillis(),
            )
        )
        // Stop the job first so the worker sees it was stopped (not failed) and keeps the part file
        WorkManager.getInstance(ctx).cancelWorkById(w.id)
        runCatching { YoutubeDL.getInstance().destroyProcessById(w.id.toString()) }
    }

    /** Starts a paused download again; yt-dlp continues from the part file. */
    fun resume(ctx: Context, item: com.sushant.grabbit.data.PausedItem): UUID {
        com.sushant.grabbit.data.Paused.remove(item.job.key)
        return enqueue(ctx, item.job, resume = true)
    }

    /** Cancels a paused download and deletes its part file. */
    fun discard(ctx: Context, item: com.sushant.grabbit.data.PausedItem) {
        com.sushant.grabbit.data.Paused.remove(item.job.key)
        partDir(ctx, item.job.key).deleteRecursively()
        Gallery.delete(ctx, item.job.key)
    }

    fun created(w: WorkInfo): Long = w.tag("c:").toLongOrNull() ?: 0L
}

fun WorkInfo.tag(prefix: String): String =
    tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix) ?: ""
