package com.sushant.pageone.data

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.ln
import kotlin.math.pow

/** Pure helpers (unit-tested). */
object Format {
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        val exp = (ln(bytes.toDouble()) / ln(1024.0)).toInt().coerceIn(1, units.size)
        val v = bytes / 1024.0.pow(exp)
        val txt = if (v >= 100 || exp == 1) String.format(Locale.US, "%.0f", v) else String.format(Locale.US, "%.1f", v).removeSuffix(".0")
        return "$txt ${units[exp - 1]}"
    }

    /** "12 Nov" this year, "12 Nov 2025" for other years. */
    fun shortDate(millis: Long, now: Long = System.currentTimeMillis()): String {
        val c = Calendar.getInstance().apply { timeInMillis = millis }
        val n = Calendar.getInstance().apply { timeInMillis = now }
        val pattern = if (c.get(Calendar.YEAR) == n.get(Calendar.YEAR)) "d MMM" else "d MMM yyyy"
        return SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(millis))
    }

    fun time(millis: Long): String = SimpleDateFormat("h:mm a", Locale.ENGLISH).format(Date(millis))

    enum class Day { TODAY, YESTERDAY, EARLIER }

    fun dayGroup(millis: Long, now: Long = System.currentTimeMillis()): Day {
        val start = Calendar.getInstance().apply {
            timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return when {
            millis >= start -> Day.TODAY
            millis >= start - 86_400_000L -> Day.YESTERDAY
            else -> Day.EARLIER
        }
    }

    /** "Opened 9:12 AM" today, "Opened yesterday 7:15 PM", "Opened 28 Sep". */
    fun opened(millis: Long, now: Long = System.currentTimeMillis()): String = when (dayGroup(millis, now)) {
        Day.TODAY -> "Opened ${time(millis)}"
        Day.YESTERDAY -> "Opened yesterday ${time(millis)}"
        Day.EARLIER -> "Opened ${shortDate(millis, now)}"
    }

    /** Readable folder name from a path: ".../WhatsApp/Media/WhatsApp Documents" → "WhatsApp Documents", "/storage/emulated/0" → "Phone storage". */
    fun folderName(path: String): String {
        val trimmed = path.trimEnd('/')
        if (trimmed == "/storage/emulated/0" || trimmed.isEmpty()) return "Phone storage"
        val last = trimmed.substringAfterLast('/')
        return if (last.equals("Download", true)) "Download" else last
    }

    /**
     * Parse page ranges like "1-3, 7, 10-12" (1-based, inclusive) into 0-based page indices in order, without duplicates.
     * Returns null when the text is invalid or out of 1..pageCount.
     */
    fun pageRanges(text: String, pageCount: Int): List<Int>? {
        val out = LinkedHashSet<Int>()
        val parts = text.split(',').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        for (p in parts) {
            val m = Regex("^(\\d+)\\s*(?:[-–]\\s*(\\d+))?$").find(p) ?: return null
            val a = m.groupValues[1].toIntOrNull() ?: return null
            val b = m.groupValues[2].ifEmpty { m.groupValues[1] }.toIntOrNull() ?: return null
            if (a < 1 || b < 1 || a > pageCount || b > pageCount) return null
            val range = if (a <= b) a..b else a downTo b
            for (i in range) out += i - 1
        }
        return out.toList()
    }

    /** Safe file name for a new PDF. */
    fun fileName(raw: String): String {
        val base = raw.trim().ifEmpty { "PageOne" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (base.endsWith(".pdf", true)) base else "$base.pdf"
    }

    fun percent(page: Int, pages: Int): Int = if (pages <= 0) 0 else ((page + 1) * 100 / pages).coerceIn(0, 100)
}
