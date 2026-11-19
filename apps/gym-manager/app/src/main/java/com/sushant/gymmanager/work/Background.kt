package com.sushant.gymmanager.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.MainActivity
import com.sushant.gymmanager.R
import com.sushant.gymmanager.data.repository.WidgetStats
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.Money
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/** Runs once a day: renewals, automatic backup, the morning summary notification and the widget refresh. */
class DailyWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as GymApp
        return runCatching {
            app.repository.checkAndGenerateAutoRenewalCycles()
            app.backupManager.runAutoBackupIfDue()
            val settings = app.repository.getSettings()
            val stats = app.repository.widgetStats()
            if (settings.dailyReminderEnabled) Notifications.showDaily(applicationContext, stats, settings.language)
            GymWidget.update(applicationContext)
            Result.success()
        }.getOrElse { Result.retry() }
    }

    companion object {
        private const val NAME = "daily_summary"

        fun schedule(context: Context, hour: Int, replace: Boolean = false) {
            val now = LocalDateTime.now()
            var next = now.withHour(hour.coerceIn(0, 23)).withMinute(0).withSecond(0).withNano(0)
            if (!next.isAfter(now)) next = next.plusDays(1)
            val delay = Duration.between(now, next).toMinutes()
            val request = PeriodicWorkRequestBuilder<DailyWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                NAME,
                if (replace) ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE else ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}

object Notifications {
    private const val CHANNEL = "daily_summary"
    private const val ID = 1001

    fun ensureChannel(context: Context, lang: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val ch = NotificationChannel(CHANNEL, I18n.tr(lang, "Daily summary"), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = I18n.tr(lang, "Expiring memberships and pending dues every morning")
            }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
        }
    }

    fun canPost(context: Context): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun showDaily(context: Context, s: WidgetStats, lang: String) {
        if (!canPost(context)) return
        if (s.expiring == 0 && s.pending <= 0.0) return
        ensureChannel(context, lang)
        val title = I18n.tr(lang, "%s • Today's summary", s.gymName)
        val parts = buildList {
            if (s.expiring > 0) add(I18n.tr(lang, "%d memberships expiring soon", s.expiring))
            if (s.pending > 0) add(I18n.tr(lang, "%s pending from %d members", Money.format(s.pending), s.membersWithDues))
        }
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_gym)
            .setColor(0xFFEA580C.toInt())
            .setContentTitle(title)
            .setContentText(parts.joinToString(" • "))
            .setStyle(NotificationCompat.BigTextStyle().bigText(parts.joinToString("\n")))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(context).notify(ID, n)
        } catch (_: SecurityException) {
        }
    }
}

class GymWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            try {
                GymWidget.update(context)
            } finally {
                pending.finish()
            }
        }
    }
}

object GymWidget {
    suspend fun update(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, GymWidgetProvider::class.java))
        if (ids.isEmpty()) return
        val app = context.applicationContext as GymApp
        val s = app.repository.widgetStats()
        val lang = app.repository.getSettings().language
        fun tr(t: String) = I18n.tr(lang, t)
        val views = RemoteViews(context.packageName, R.layout.widget_summary).apply {
            setTextViewText(R.id.w_gym, s.gymName)
            setTextViewText(R.id.w_date, DateUtils.formatShort(DateUtils.today()))
            setTextViewText(R.id.w_active, s.active.toString())
            setTextViewText(R.id.w_active_label, tr("Active"))
            setTextViewText(R.id.w_present, "${s.presentToday}/${s.expectedToday}")
            setTextViewText(R.id.w_present_label, tr("Present"))
            setTextViewText(R.id.w_dues, Money.format(s.pending))
            setTextViewText(R.id.w_dues_label, tr("Dues"))
            setTextViewText(R.id.w_expiring, s.expiring.toString())
            setTextViewText(R.id.w_expiring_label, tr("Expiring"))
            val open = PendingIntent.getActivity(
                context, 1, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            setOnClickPendingIntent(R.id.w_root, open)
        }
        manager.updateAppWidget(ids, views)
    }
}
