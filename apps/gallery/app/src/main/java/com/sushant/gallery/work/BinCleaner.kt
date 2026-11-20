package com.sushant.gallery.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sushant.gallery.GalleryApp
import java.util.concurrent.TimeUnit

/** Once a day: deletes Recycle Bin items older than 30 days. */
class BinCleaner(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GalleryApp
        runCatching { app.repo.purgeExpired() }
        return Result.success()
    }

    companion object {
        fun schedule(ctx: Context) {
            runCatching {
                WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
                    "bin-cleaner", ExistingPeriodicWorkPolicy.KEEP,
                    PeriodicWorkRequestBuilder<BinCleaner>(1, TimeUnit.DAYS).build(),
                )
            }
        }
    }
}
