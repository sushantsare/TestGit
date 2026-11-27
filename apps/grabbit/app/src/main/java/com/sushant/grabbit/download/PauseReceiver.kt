package com.sushant.grabbit.download

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.WorkManager
import java.util.UUID
import kotlin.concurrent.thread

/** "Pause" button on the download notification. */
class PauseReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        val id = intent.getStringExtra(EXTRA_ID)?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return
        val pending = goAsync()
        thread {
            try {
                WorkManager.getInstance(ctx).getWorkInfoById(id).get()?.let { Downloads.pause(ctx.applicationContext, it) }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val EXTRA_ID = "work_id"
    }
}
