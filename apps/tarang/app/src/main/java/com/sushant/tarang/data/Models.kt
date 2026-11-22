package com.sushant.tarang.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mime: String,
    val path: String,
    val folderPath: String,
    val folderName: String,
    val fileName: String,
    val dateAdded: Long,
    val year: Int,
    val track: Int,
) {
    val uri: Uri get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
    /** Individual artists ("A, B & C feat. D" → [A, B, C, D]). */
    val artists: List<String> get() = splitArtists(artist)
}

fun splitArtists(s: String): List<String> =
    s.split(Regex("\\s*(,|&|;|/| feat\\.? | ft\\.? | x )\\s*", RegexOption.IGNORE_CASE))
        .map { it.trim() }.filter { it.isNotEmpty() }.ifEmpty { listOf(UNKNOWN_ARTIST) }

const val UNKNOWN_ARTIST = "Unknown artist"
const val UNKNOWN_ALBUM = "Unknown album"

fun SongEntity.toSong() = Song(id, title, artist, album, albumId, albumArtist, durationMs, sizeBytes, mime, path,
    folderPath, folderName, fileName, dateAdded, year, track)

data class Folder(val path: String, val name: String, val songs: List<Song>) {
    val durationMs get() = songs.sumOf { it.durationMs }
}

data class Album(val id: Long, val name: String, val artist: String, val year: Int, val songs: List<Song>)
data class Artist(val name: String, val songs: List<Song>) {
    val albumCount get() = songs.map { it.albumId }.distinct().size
}

data class Playlist(val id: Long, val name: String, val createdAt: Long, val songIds: List<Long>)

enum class MixKind(val title: String, val subtitle: String) {
    ON_REPEAT("On repeat", "Your most played lately"),
    FORGOTTEN("Forgotten favourites", "Songs you loved and haven't played in a while"),
    RECENT("Recently added", "New on your phone"),
}

enum class SongSort(val label: String) { NAME("Name A–Z"), DATE_ADDED("Newest added first"), FILE_NAME("File name"), MOST_PLAYED("Most played"), DURATION("Length") }

fun formatDuration(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

/** "27 h 40 m", "3 h 20 m", "45 m", "0 m". */
fun formatListen(ms: Long): String {
    val totalMin = ms / 60_000
    val h = totalMin / 60; val m = totalMin % 60
    return if (h > 0) "$h h $m m" else "$m m"
}

fun plural(n: Int, one: String, many: String = one + "s") = "%,d %s".format(n, if (n == 1) one else many)

fun formatSize(bytes: Long): String = when {
    bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
    bytes >= 1_000 -> "%d KB".format(bytes / 1_000)
    else -> "$bytes B"
}
