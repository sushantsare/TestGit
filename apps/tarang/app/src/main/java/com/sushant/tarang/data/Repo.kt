package com.sushant.tarang.data

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.MediaStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar

data class Library(
    val all: List<Song> = emptyList(),          // every scanned song (used by "Folders to scan")
    val songs: List<Song> = emptyList(),        // what the user sees (allowed folders, not too short)
    val byId: Map<Long, Song> = emptyMap(),
    val folders: List<Folder> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val loaded: Boolean = false,
)

data class StatsPeriod(val from: Long, val prevFrom: Long, val buckets: List<Pair<String, Long>>)

/** One place for everything the screens and the player need from the database and the scanner. */
class Repo(private val ctx: Context, val db: TarangDb, val prefs: Prefs, private val appScope: CoroutineScope) {
    val dao = db.dao()
    val covers = Covers(ctx)
    val lyrics = LyricsFinder(ctx, dao, prefs)
    private val scanner = Scanner(ctx)

    val scanning = MutableStateFlow(false)

    val library: StateFlow<Library> = combine(dao.songs(), prefs.settings.map { it.excludedFolders to it.foldersCustomized to it.skipShortSec }.distinctUntilChanged()) { rows, _ ->
        buildLibrary(rows.map { it.toSong() })
    }.flowOn(Dispatchers.Default).stateIn(appScope, SharingStarted.Eagerly, Library())

    val likedIds: StateFlow<List<Long>> = dao.likedIds().stateIn(appScope, SharingStarted.Eagerly, emptyList())
    val playCounts: StateFlow<Map<Long, PlayCount>> = dao.playCounts().map { l -> l.associateBy { it.songId } }
        .stateIn(appScope, SharingStarted.Eagerly, emptyMap())
    val playlists: StateFlow<List<Playlist>> = combine(dao.playlists(), dao.playlistSongs()) { ps, rows ->
        val by = rows.groupBy { it.playlistId }
        ps.map { p -> Playlist(p.id, p.name, p.createdAt, by[p.id].orEmpty().sortedBy { it.position }.map { it.songId }) }
    }.stateIn(appScope, SharingStarted.Eagerly, emptyList())

    fun isFolderVisible(path: String, s: Settings = prefs.now): Boolean =
        if (s.foldersCustomized) path !in s.excludedFolders else !Prefs.hiddenByDefault(path)

    private fun buildLibrary(all: List<Song>): Library {
        val s = prefs.now
        val minMs = s.skipShortSec * 1000L
        val songs = all.filter { isFolderVisible(it.folderPath, s) && (minMs == 0L || it.durationMs >= minMs) }
        val folders = songs.groupBy { it.folderPath }.map { (p, l) -> Folder(p, l.first().folderName, l) }
            .sortedBy { it.name.lowercase() }
        val albums = songs.groupBy { it.albumId }.map { (id, l) ->
            val f = l.first()
            Album(id, f.album, f.albumArtist, l.maxOf { it.year }, l.sortedWith(compareBy({ it.track }, { it.title.lowercase() })))
        }.sortedBy { it.name.lowercase() }
        val artistMap = HashMap<String, MutableList<Song>>()
        songs.forEach { sg -> sg.artists.forEach { a -> artistMap.getOrPut(a) { ArrayList() } += sg } }
        val artists = artistMap.map { (n, l) -> Artist(n, l) }.sortedBy { it.name.lowercase() }
        return Library(all, songs, songs.associateBy { it.id }, folders, albums, artists, loaded = true)
    }

    // ---------------- scanning
    suspend fun scan() = withContext(Dispatchers.IO) {
        if (scanning.value) return@withContext
        scanning.value = true
        val t0 = SystemClock.elapsedRealtime()
        try {
            val found = scanner.scan()
            dao.replaceLibrary(found)
            prefs.lastScanAt = System.currentTimeMillis()
            prefs.lastScanMs = SystemClock.elapsedRealtime() - t0
        } catch (e: SecurityException) {
            // permission was taken away: nothing to scan
        } finally { scanning.value = false }
    }

    private var observer: ContentObserver? = null
    private var rescanJob: Job? = null
    /** Rescan automatically (debounced) when songs are added or removed on the phone. */
    fun watchMediaStore() {
        if (observer != null) return
        observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                rescanJob?.cancel()
                rescanJob = appScope.launch { delay(1500); scan() }
            }
        }
        runCatching { ctx.contentResolver.registerContentObserver(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, true, observer!!) }
    }

    // ---------------- likes / playlists
    fun isLiked(id: Long) = id in likedIds.value
    suspend fun toggleLike(id: Long) { if (dao.isLiked(id) > 0) dao.unlike(id) else dao.like(LikeEntity(id)) }

    suspend fun createPlaylist(name: String): Long = dao.insertPlaylist(PlaylistEntity(name = name.trim()))
    suspend fun renamePlaylist(id: Long, name: String) = dao.renamePlaylist(id, name.trim())
    suspend fun deletePlaylist(id: Long) { dao.clearPlaylist(id); dao.deletePlaylistRow(id) }
    suspend fun addToPlaylist(pid: Long, ids: List<Long>) {
        val rows = dao.playlistRows(pid); var pos = (rows.maxOfOrNull { it.position } ?: -1) + 1
        ids.filter { id -> rows.none { it.songId == id } }.forEach { dao.addToPlaylist(PlaylistSongEntity(pid, it, pos++)) }
    }
    suspend fun removeFromPlaylist(pid: Long, sid: Long) = dao.removeFromPlaylist(pid, sid)
    suspend fun reorderPlaylist(pid: Long, ids: List<Long>) {
        dao.clearPlaylist(pid); dao.putPlaylistRows(ids.mapIndexed { i, s -> PlaylistSongEntity(pid, s, i) })
    }

    // ---------------- songs
    fun sort(list: List<Song>, by: SongSort): List<Song> = when (by) {
        SongSort.NAME -> list.sortedBy { it.title.lowercase() }
        SongSort.DATE_ADDED -> list.sortedByDescending { it.dateAdded }
        SongSort.FILE_NAME -> list.sortedBy { it.fileName.lowercase() }
        SongSort.MOST_PLAYED -> list.sortedWith(compareByDescending<Song> { playCounts.value[it.id]?.plays ?: 0 }.thenBy { it.title.lowercase() })
        SongSort.DURATION -> list.sortedByDescending { it.durationMs }
    }

    /** Smart mixes from real listening only. Empty until there is enough history (never made up). */
    fun mix(kind: MixKind, lib: Library = library.value): List<Song> {
        val now = System.currentTimeMillis(); val day = 86_400_000L
        val counts = playCounts.value
        return when (kind) {
            MixKind.ON_REPEAT -> lib.songs.filter { (counts[it.id]?.plays ?: 0) >= 2 && now - (counts[it.id]?.lastPlayed ?: 0) < 30 * day }
                .sortedByDescending { counts[it.id]?.plays ?: 0 }.take(30)
            MixKind.FORGOTTEN -> lib.songs.filter { s ->
                val c = counts[s.id]
                (isLiked(s.id) || (c?.plays ?: 0) >= 3) && (c == null || now - c.lastPlayed > 45 * day)
            }.sortedByDescending { counts[it.id]?.plays ?: 0 }.take(30)
            MixKind.RECENT -> lib.songs.filter { now / 1000 - it.dateAdded < 30 * 86_400 }.sortedByDescending { it.dateAdded }.take(50)
        }
    }

    fun search(q: String, lib: Library = library.value): SearchResult {
        val t = q.trim().lowercase()
        if (t.isEmpty()) return SearchResult()
        return SearchResult(
            songs = lib.songs.filter { it.title.lowercase().contains(t) || it.artist.lowercase().contains(t) || it.album.lowercase().contains(t) }
                .sortedByDescending { it.title.lowercase().startsWith(t) }.take(100),
            artists = lib.artists.filter { it.name.lowercase().contains(t) }.take(10),
            albums = lib.albums.filter { it.name.lowercase().contains(t) }.take(10),
            folders = lib.folders.filter { it.name.lowercase().contains(t) }.take(10),
        )
    }

    // ---------------- stats
    fun statsPeriod(kind: Int, now: Long = System.currentTimeMillis()): StatsPeriod {
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        return when (kind) {
            0 -> { // this week, Monday first
                val dow = (c.get(Calendar.DAY_OF_WEEK) + 5) % 7
                c.add(Calendar.DAY_OF_YEAR, -dow); val from = c.timeInMillis
                val labels = listOf("M", "T", "W", "T", "F", "S", "S")
                StatsPeriod(from, from - 7 * 86_400_000L, labels.mapIndexed { i, l -> l to from + i * 86_400_000L })
            }
            1 -> { // this month, by week of the month
                c.set(Calendar.DAY_OF_MONTH, 1); val from = c.timeInMillis
                val prev = (c.clone() as Calendar).apply { add(Calendar.MONTH, -1) }.timeInMillis
                StatsPeriod(from, prev, (0 until 5).map { "W${it + 1}" to from + it * 7 * 86_400_000L })
            }
            2 -> { // this year, by month
                c.set(Calendar.DAY_OF_YEAR, 1); val from = c.timeInMillis
                val prev = (c.clone() as Calendar).apply { add(Calendar.YEAR, -1) }.timeInMillis
                val names = listOf("J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D")
                StatsPeriod(from, prev, names.mapIndexed { i, n -> n to (c.clone() as Calendar).apply { add(Calendar.MONTH, i) }.timeInMillis })
            }
            else -> StatsPeriod(0L, 0L, emptyList())
        }
    }

    suspend fun resetStats() = dao.clearPlays()

    suspend fun forgetSong(id: Long) {
        dao.deleteSongs(listOf(id)); dao.removeSongEverywhere(id); dao.unlike(id); dao.deleteLyrics(id); covers.forget(id)
    }
}

data class SearchResult(
    val songs: List<Song> = emptyList(), val artists: List<Artist> = emptyList(),
    val albums: List<Album> = emptyList(), val folders: List<Folder> = emptyList(),
) { val empty get() = songs.isEmpty() && artists.isEmpty() && albums.isEmpty() && folders.isEmpty() }

/** Streak = days in a row (ending today or yesterday) with at least one counted play. */
fun streakDays(playDays: Set<Long>, today: Long): Int {
    var d = today; var n = 0
    if (d !in playDays) d -= 1
    while (d in playDays) { n++; d -= 1 }
    return n
}
fun dayIndex(ms: Long): Long {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    return (ms + c.get(Calendar.ZONE_OFFSET) + c.get(Calendar.DST_OFFSET)) / 86_400_000L
}
