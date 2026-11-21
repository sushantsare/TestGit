package com.sushant.gallery.data

import android.content.Context
import android.os.Build
import com.sushant.gallery.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class CrashEntry(val file: File, val time: Long, val title: String, val text: String)

/**
 * Local-only crash log + usage counters. Nothing leaves the phone and no personal
 * content (file names, pictures) is ever written — only counts, app version, device and stack traces.
 */
class Diagnostics(private val ctx: Context) {
    private val dir = File(ctx.filesDir, "crashes").apply { mkdirs() }
    private val sp = ctx.getSharedPreferences("gallery_usage", Context.MODE_PRIVATE)
    private val _usage = MutableStateFlow(readUsage())
    val usage: StateFlow<Map<String, Long>> = _usage.asStateFlow()

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t, e ->
            runCatching {
                val now = System.currentTimeMillis()
                val body = buildString {
                    appendLine("Time: ${fmt(now)}")
                    appendLine("App: Gallery ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                    appendLine("Thread: ${t.name}")
                    appendLine()
                    append(e.stackTraceToString())
                }
                File(dir, "crash_$now.txt").writeText(body)
                // keep the newest 30
                dir.listFiles()?.sortedByDescending { it.name }?.drop(30)?.forEach { it.delete() }
            }
            previous?.uncaughtException(t, e)
        }
    }

    fun crashes(): List<CrashEntry> = (dir.listFiles() ?: emptyArray()).sortedByDescending { it.name }.map { f ->
        val text = runCatching { f.readText() }.getOrDefault("")
        val time = f.name.removePrefix("crash_").removeSuffix(".txt").toLongOrNull() ?: f.lastModified()
        val firstApp = text.lines().firstOrNull { it.trim().startsWith("at com.sushant") }?.trim()?.removePrefix("at ")
        val ex = text.lines().firstOrNull { it.contains("Exception") || it.contains("Error") }?.substringBefore(':')?.substringAfterLast('.')
        CrashEntry(f, time, listOfNotNull(ex, firstApp?.substringBefore('(')?.substringAfterLast('.', "")).joinToString(" · ").ifBlank { "Crash" }, text)
    }

    fun clearCrashes() { dir.listFiles()?.forEach { it.delete() } }

    /** One text file with every crash, for sharing. */
    fun exportFile(): File {
        val out = File(ctx.cacheDir, "logs").apply { mkdirs() }
        val f = File(out, "gallery-log.txt")
        f.writeText(buildString {
            appendLine("Gallery ${BuildConfig.VERSION_NAME} diagnostics · ${fmt(System.currentTimeMillis())}")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}")
            appendLine("Usage: " + _usage.value.entries.joinToString { "${it.key}=${it.value}" })
            appendLine()
            crashes().forEach { appendLine("==== ${fmt(it.time)}"); appendLine(it.text); appendLine() }
            if (crashes().isEmpty()) appendLine("No crashes recorded.")
        })
        return f
    }

    fun count(key: String, by: Long = 1) {
        val v = sp.getLong(key, 0) + by
        sp.edit().putLong(key, v).apply()
        _usage.value = _usage.value + (key to v)
    }

    private fun readUsage(): Map<String, Long> = sp.all.mapNotNull { (k, v) -> (v as? Long)?.let { k to it } }.toMap()

    companion object {
        const val OPENS = "app_opens"
        const val FLICKED = "flicked_to_bin"
        const val DELETED = "deleted"
        const val RESTORED = "restored"
        const val EDITED = "edited"
        const val SHARED = "shared"
        fun fmt(t: Long): String = SimpleDateFormat("dd MMM yyyy, h:mm a", Locale.ENGLISH).format(Date(t))
    }
}
