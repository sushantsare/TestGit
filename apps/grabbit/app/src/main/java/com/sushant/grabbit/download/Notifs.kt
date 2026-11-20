package com.sushant.grabbit.download

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import com.sushant.grabbit.data.SavedItem
import android.os.Build
import androidx.core.app.NotificationCompat
import com.sushant.grabbit.MainActivity
import com.sushant.grabbit.R
import java.util.UUID

object Notifs {
    const val CH_PROGRESS = "downloads"
    const val CH_DONE = "finished"

    fun createChannels(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT)
        )
    }

    private fun openApp(ctx: Context): PendingIntent = PendingIntent.getActivity(
        ctx, 0,
        Intent(ctx, MainActivity::class.java).putExtra(MainActivity.EXTRA_TAB, 1)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    fun progress(ctx: Context, title: String, text: String, pct: Int, indeterminate: Boolean, workId: UUID): Notification {
        val cancel = androidx.work.WorkManager.getInstance(ctx).createCancelPendingIntent(workId)
        return NotificationCompat.Builder(ctx, CH_PROGRESS)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF059669.toInt())
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(100, pct, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openApp(ctx))
            .addAction(0, "Cancel", cancel)
            .build()
    }

    fun done(ctx: Context, id: Int, title: String, ok: Boolean, text: String, item: SavedItem? = null) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ctx.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val b = NotificationCompat.Builder(ctx, CH_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(0xFF059669.toInt())
            .setContentTitle(if (ok) "Saved: $title" else "Download failed: $title")
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setAutoCancel(true)
            .setContentIntent(openApp(ctx))
        if (ok && item != null) {
            val uri = Uri.parse(item.uri)
            val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, item.mime)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            val send = Intent.createChooser(
                Intent(Intent.ACTION_SEND).setType(item.mime).putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                "Share via",
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val flags = PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            b.setContentIntent(PendingIntent.getActivity(ctx, id * 3, view, flags))
                .addAction(0, if (item.isAudio) "Play" else "Watch", PendingIntent.getActivity(ctx, id * 3 + 1, view, flags))
                .addAction(0, "Share", PendingIntent.getActivity(ctx, id * 3 + 2, send, flags))
        }
        ctx.getSystemService(NotificationManager::class.java).notify(id, b.build())
    }
}
