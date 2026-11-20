package com.sushant.saveo.work

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sushant.saveo.MainActivity
import com.sushant.saveo.R
import com.sushant.saveo.SaveoApp
import com.sushant.saveo.data.MediaKind
import java.util.concurrent.TimeUnit

object Background {
    private const val CHECK = "status-check"
    private const val PURGE = "bin-purge"

    /** Runs the 15-minute check only while "New status alerts" or "Auto-save" is on. */
    fun sync(ctx: Context, alerts: Boolean, autoSave: Boolean) {
        val wm = WorkManager.getInstance(ctx)
        if (alerts || autoSave) {
            wm.enqueueUniquePeriodicWork(CHECK, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<StatusCheckWorker>(15, TimeUnit.MINUTES).build())
        } else wm.cancelUniqueWork(CHECK)
        wm.enqueueUniquePeriodicWork(PURGE, ExistingPeriodicWorkPolicy.KEEP, PeriodicWorkRequestBuilder<BinPurgeWorker>(1, TimeUnit.DAYS).build())
    }
}

class StatusCheckWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as SaveoApp
        val repo = app.repository
        val s = repo.prefs.settings.value
        if (!s.alerts && !s.autoSave) return Result.success()
        val scan = repo.refresh()
        val fresh = repo.markSeenAndGetNew(scan.items)
        if (fresh.isEmpty()) return Result.success()
        var savedNow = 0
        if (s.autoSave) savedNow = repo.saveAll(fresh).first
        if (s.alerts) Notifier.newStatuses(applicationContext, fresh.size, fresh.count { it.kind == MediaKind.VIDEO }, if (s.autoSave) savedNow else -1)
        return Result.success()
    }
}

class BinPurgeWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {
    override suspend fun doWork(): Result {
        (applicationContext as SaveoApp).repository.purgeBin()
        return Result.success()
    }
}

object Notifier {
    const val CHANNEL = "new_statuses"
    const val EXTRA_OPEN_STATUSES = "open_statuses"

    fun createChannel(ctx: Context) {
        val ch = NotificationChannel(CHANNEL, ctx.getString(R.string.channel_name), NotificationManager.IMPORTANCE_DEFAULT).apply {
            description = ctx.getString(R.string.channel_desc)
        }
        ctx.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    fun canNotify(ctx: Context): Boolean =
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(ctx).areNotificationsEnabled()

    /** [autoSaved] = -1 when auto-save is off. */
    @SuppressLint("MissingPermission")
    fun newStatuses(ctx: Context, count: Int, videos: Int, autoSaved: Int) {
        if (!canNotify(ctx)) return
        val open = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_STATUSES, true)
        }
        val pi = PendingIntent.getActivity(ctx, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val title = ctx.resources.getQuantityString(R.plurals.notif_new_title, count, count)
        val text = when {
            autoSaved > 0 -> ctx.resources.getQuantityString(R.plurals.notif_autosaved, autoSaved, autoSaved)
            videos > 0 -> ctx.resources.getQuantityString(R.plurals.notif_videos, videos, videos)
            else -> ctx.getString(R.string.notif_tap)
        }
        val n = NotificationCompat.Builder(ctx, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_saveo)
            .setColor(ContextCompat.getColor(ctx, R.color.brand))
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        try { NotificationManagerCompat.from(ctx).notify(42, n) } catch (_: SecurityException) { }
    }
}
