package com.sushant.saveo.data

import android.net.Uri
import java.util.Calendar

enum class MediaKind { IMAGE, VIDEO }

/** Where statuses come from. Package names are used to open/repost into that app. */
enum class Source(val label: String, val pkg: String?, val shortBadge: String?) {
    WHATSAPP("WhatsApp", "com.whatsapp", null),
    BUSINESS("WhatsApp Business", "com.whatsapp.w4b", "B"),
    DUAL("Dual WhatsApp", "com.whatsapp", "2");

    /** Path of the status folder under the shared storage root, Android 11+ layout first. */
    val relativePaths: List<String>
        get() = when (this) {
            WHATSAPP -> listOf("Android/media/com.whatsapp/WhatsApp/Media/.Statuses", "WhatsApp/Media/.Statuses")
            BUSINESS -> listOf("Android/media/com.whatsapp.w4b/WhatsApp Business/Media/.Statuses", "WhatsApp Business/Media/.Statuses")
            DUAL -> emptyList()
        }
}

/** A status file currently present in WhatsApp's status folder. */
data class StatusItem(
    val uri: Uri,
    val name: String,
    val kind: MediaKind,
    val source: Source,
    val modified: Long,
    val size: Long,
) {
    val key: String get() = uri.toString()
    /** Cheap identity used to show the tick without hashing every file. */
    val matchKey: String get() = matchKeyOf(name, size)
}

fun matchKeyOf(name: String, size: Long) = "$name|$size"

object MediaTypes {
    private val imageExt = setOf("jpg", "jpeg", "png", "webp", "gif", "heic")
    private val videoExt = setOf("mp4", "3gp", "mkv", "webm", "mov", "m4v")

    fun extension(name: String): String = name.substringAfterLast('.', "").lowercase()

    /** Decides by the real MIME type first (photo + music statuses arrive as MP4 video), then by extension. */
    fun kindOf(name: String, mime: String?): MediaKind? {
        if (mime != null) {
            if (mime.startsWith("video/")) return MediaKind.VIDEO
            if (mime.startsWith("image/")) return MediaKind.IMAGE
        }
        val ext = extension(name)
        return when (ext) {
            in imageExt -> MediaKind.IMAGE
            in videoExt -> MediaKind.VIDEO
            else -> null
        }
    }

    /** Skips WhatsApp's hidden helper files (.nomedia, temp files) and anything that is not a photo or video. */
    fun isStatusFile(name: String, mime: String?): Boolean =
        name.isNotBlank() && !name.startsWith(".") && !name.endsWith(".tmp") && kindOf(name, mime) != null

    fun mimeFor(name: String, kind: MediaKind): String = when (extension(name)) {
        "jpg", "jpeg" -> "image/jpeg"
        "png" -> "image/png"
        "webp" -> "image/webp"
        "gif" -> "image/gif"
        "heic" -> "image/heic"
        "3gp" -> "video/3gpp"
        "mkv" -> "video/x-matroska"
        "webm" -> "video/webm"
        "mov" -> "video/quicktime"
        "mp4", "m4v" -> "video/mp4"
        else -> if (kind == MediaKind.VIDEO) "video/mp4" else "image/jpeg"
    }
}

object Formats {
    fun duration(ms: Long): String {
        if (ms <= 0) return "0:00"
        val total = (ms + 500) / 1000
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    enum class DayGroup { TODAY, YESTERDAY, EARLIER }

    fun dayGroup(epochMs: Long, now: Long = System.currentTimeMillis()): DayGroup {
        val start = startOfDay(now)
        return when {
            epochMs >= start -> DayGroup.TODAY
            epochMs >= start - DAY -> DayGroup.YESTERDAY
            else -> DayGroup.EARLIER
        }
    }

    fun startOfDay(epochMs: Long): Long = Calendar.getInstance().apply {
        timeInMillis = epochMs
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    /** Whole days left before a recycle-bin item is removed (30-day rule), never below 0. */
    fun binDaysLeft(deletedAt: Long, now: Long = System.currentTimeMillis()): Int {
        val left = deletedAt + BIN_DAYS * DAY - now
        return if (left <= 0) 0 else ((left + DAY - 1) / DAY).toInt()
    }

    const val DAY = 24L * 60 * 60 * 1000
    const val BIN_DAYS = 30L
}
