package com.sushant.grabbit.download

import java.util.Locale

/** Reads sizes out of yt-dlp progress lines, e.g. "[download]  45.3% of ~ 141.32MiB at 2.31MiB/s ETA 00:40". */
object Progress {
    private val TOTAL = Regex("""of\s+~?\s*([\d.]+)\s*([KMGT]?i?B)\b""")
    private val UNITS = mapOf(
        "B" to 1.0,
        "KiB" to 1024.0, "MiB" to 1024.0 * 1024, "GiB" to 1024.0 * 1024 * 1024, "TiB" to 1024.0 * 1024 * 1024 * 1024,
        "KB" to 1e3, "MB" to 1e6, "GB" to 1e9, "TB" to 1e12,
    )

    /** Total size in bytes mentioned in the line, or null. */
    fun totalBytes(line: String): Long? {
        val m = TOTAL.find(line) ?: return null
        val n = m.groupValues[1].toDoubleOrNull() ?: return null
        val unit = UNITS[m.groupValues[2]] ?: return null
        return (n * unit).toLong().takeIf { it > 0 }
    }

    /** "63.9 MB of 141.3 MB", or "" when the total is unknown. */
    fun sizeText(pct: Float, total: Long): String {
        if (total <= 0) return ""
        val done = (total * (pct.coerceIn(0f, 100f) / 100f)).toLong()
        return "${bytes(done)} of ${bytes(total)}"
    }

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
        else -> String.format(Locale.US, "%d KB", b / 1024)
    }
}
