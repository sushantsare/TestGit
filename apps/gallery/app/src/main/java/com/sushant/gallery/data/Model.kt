package com.sushant.gallery.data

import android.net.Uri
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

/** One photo or video. [path] is the stable key (MediaStore ids change after moves). */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val dateTaken: Long,
    val dateModified: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val folder: String,
    val bucket: String,
    /** Set for items in the Hidden album (decrypted on demand). */
    val vaultId: String? = null,
) {
    val key: String get() = vaultId ?: path
    /** Changes whenever the file changes, so image caches reload after rotate/edit. */
    val cacheKey: String get() = "$key#$dateModified#$size"
    val isJpeg: Boolean get() = mime == "image/jpeg" || name.endsWith(".jpg", true) || name.endsWith(".jpeg", true)
}

data class Album(
    val folder: String,
    val name: String,
    val count: Int,
    val cover: MediaItem?,
    val latest: Long,
)

enum class PhotoSort { DATE_TAKEN, DATE_MODIFIED, NAME, SIZE }
enum class AlbumSort { NAME, DATE, COUNT }

/** Where a viewer list comes from; encoded into navigation routes. */
sealed class Source {
    data object All : Source()
    data object Favourites : Source()
    data object Videos : Source()
    data object Vault : Source()
    data object External : Source()
    data class Folder(val folder: String) : Source()
    data class Search(val query: String) : Source()

    fun encode(): String = when (this) {
        All -> "all"; Favourites -> "fav"; Videos -> "videos"; Vault -> "vault"; External -> "external"
        is Folder -> "folder:" + enc(folder)
        is Search -> "search:" + enc(query)
    }

    companion object {
        private fun enc(v: String): String = java.net.URLEncoder.encode(v, "UTF-8")
        private fun dec(v: String): String = java.net.URLDecoder.decode(v, "UTF-8")
        fun decode(s: String): Source = when {
            s == "fav" -> Favourites
            s == "videos" -> Videos
            s == "vault" -> Vault
            s == "external" -> External
            s.startsWith("folder:") -> Folder(dec(s.removePrefix("folder:")))
            s.startsWith("search:") -> Search(dec(s.removePrefix("search:")))
            else -> All
        }
    }
}

object Dates {
    private val day = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
    private val dayYear = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
    private val month = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val full = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)

    fun local(ms: Long, zone: ZoneId = ZoneId.systemDefault()): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()

    /** Header for the photo grid: Today / Yesterday / day (last 30 days) / month. */
    fun groupLabel(ms: Long, today: LocalDate = LocalDate.now(), zone: ZoneId = ZoneId.systemDefault()): String {
        val d = local(ms, zone)
        val diff = ChronoUnit.DAYS.between(d, today)
        return when {
            diff == 0L -> "Today"
            diff == 1L -> "Yesterday"
            diff in 2..30 -> if (d.year == today.year) day.format(d) else dayYear.format(d)
            else -> month.format(d)
        }
    }

    fun titleFor(ms: Long, today: LocalDate = LocalDate.now()): String {
        val d = local(ms)
        val diff = ChronoUnit.DAYS.between(d, today)
        return when (diff) {
            0L -> "Today"
            1L -> "Yesterday"
            else -> full.format(d)
        }
    }

    fun timeOf(ms: Long): String = time.format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()))
    fun fullDate(ms: Long): String = full.format(local(ms))
}

object Formats {
    fun size(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> String.format(Locale.ENGLISH, "%.1f GB", bytes / 1e9)
        bytes >= 1_000_000 -> String.format(Locale.ENGLISH, "%.1f MB", bytes / 1e6)
        bytes >= 1_000 -> String.format(Locale.ENGLISH, "%.0f KB", bytes / 1e3)
        else -> "$bytes B"
    }

    fun duration(ms: Long): String {
        val total = (ms / 1000).coerceAtLeast(0)
        val h = total / 3600; val m = (total % 3600) / 60; val s = total % 60
        return if (h > 0) String.format(Locale.ENGLISH, "%d:%02d:%02d", h, m, s) else String.format(Locale.ENGLISH, "%d:%02d", m, s)
    }

    fun count(n: Int): String = String.format(Locale("en", "IN"), "%,d", n)

    /** Days left before a binned item is deleted forever (never below 0). */
    fun daysLeft(deletedAt: Long, now: Long, keepDays: Int = 30): Int {
        val elapsed = ((now - deletedAt) / 86_400_000L).toInt()
        return (keepDays - elapsed).coerceIn(0, keepDays)
    }

    /** `name.jpg` → `name (1).jpg` until [exists] is false. */
    fun uniqueName(name: String, exists: (String) -> Boolean): String {
        if (!exists(name)) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (exists("$base ($i)$ext")) i++
        return "$base ($i)$ext"
    }

    fun folderName(path: String): String = File(path).name.ifBlank { "Storage" }
}

object Sorting {
    fun photos(list: List<MediaItem>, sort: PhotoSort, desc: Boolean): List<MediaItem> {
        val c: Comparator<MediaItem> = when (sort) {
            PhotoSort.DATE_TAKEN -> compareBy<MediaItem> { it.dateTaken }.thenBy { it.name }
            PhotoSort.DATE_MODIFIED -> compareBy<MediaItem> { it.dateModified }.thenBy { it.name }
            PhotoSort.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
            PhotoSort.SIZE -> compareBy<MediaItem> { it.size }.thenBy { it.name }
        }
        return list.sortedWith(if (desc) c.reversed() else c)
    }

    fun albums(list: List<Album>, sort: AlbumSort): List<Album> {
        val camera = list.filter { it.name.equals("Camera", true) }
        val rest = list - camera.toSet()
        val sorted = when (sort) {
            AlbumSort.NAME -> rest.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })
            AlbumSort.DATE -> rest.sortedByDescending { it.latest }
            AlbumSort.COUNT -> rest.sortedByDescending { it.count }
        }
        return camera + sorted
    }

    /** Groups for the date grid. Only meaningful for date sorts; other sorts get one group. */
    fun groups(list: List<MediaItem>, sort: PhotoSort, today: LocalDate = LocalDate.now()): List<Pair<String, List<MediaItem>>> {
        if (list.isEmpty()) return emptyList()
        if (sort == PhotoSort.NAME || sort == PhotoSort.SIZE) return listOf("" to list)
        val out = ArrayList<Pair<String, MutableList<MediaItem>>>()
        for (m in list) {
            val label = Dates.groupLabel(if (sort == PhotoSort.DATE_TAKEN) m.dateTaken else m.dateModified, today)
            if (out.isEmpty() || out.last().first != label) out += label to mutableListOf(m) else out.last().second += m
        }
        return out
    }
}
