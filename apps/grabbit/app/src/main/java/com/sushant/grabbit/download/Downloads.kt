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
    )

    fun enqueue(ctx: Context, job: Job): UUID {
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
    )

    fun created(w: WorkInfo): Long = w.tag("c:").toLongOrNull() ?: 0L
}

fun WorkInfo.tag(prefix: String): String =
    tags.firstOrNull { it.startsWith(prefix) }?.removePrefix(prefix) ?: ""
