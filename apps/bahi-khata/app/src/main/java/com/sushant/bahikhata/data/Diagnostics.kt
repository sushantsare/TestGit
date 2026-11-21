package com.sushant.bahikhata.data

import android.content.Context
import android.os.Build
import com.sushant.bahikhata.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Local-only crash log and usage counters (nothing leaves the phone; no personal content is logged). */
object Diagnostics {
    private fun file(c: Context) = File(c.filesDir, "crash.log")

    fun install(context: Context) {
        val app = context.applicationContext
        val prev = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val sw = StringWriter(); e.printStackTrace(PrintWriter(sw))
                val f = file(app)
                if (f.length() > 200_000) f.writeText("")
                f.appendText("=== ${java.util.Date()} · v${BuildConfig.VERSION_NAME} · ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} · thread ${t.name}\n$sw\n")
            }
            prev?.uncaughtException(t, e)
        }
    }

    fun crashLog(context: Context): String = file(context).takeIf { it.exists() }?.readText().orEmpty()
    fun crashCount(context: Context, sinceMs: Long): Int = crashLog(context).lines().count { it.startsWith("=== ") } // whole log is ≤200 KB
    fun clear(context: Context) { file(context).delete() }

    // counters are written on one background thread, never the main thread
    private val io = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun count(context: Context, key: String, n: Int = 1) = io.execute {
        val sp = context.applicationContext.getSharedPreferences("diag", Context.MODE_PRIVATE)
        sp.edit().putInt(key, sp.getInt(key, 0) + n).apply()
    }

    fun time(context: Context, key: String, ms: Long) = io.execute {
        val sp = context.applicationContext.getSharedPreferences("diag", Context.MODE_PRIVATE)
        val n = sp.getInt("${key}_n", 0)
        val avg = sp.getLong("${key}_avg", 0)
        sp.edit().putInt("${key}_n", n + 1).putLong("${key}_avg", (avg * n + ms) / (n + 1)).apply()
    }

    fun counters(context: Context): Map<String, Any?> = context.getSharedPreferences("diag", Context.MODE_PRIVATE).all
}
