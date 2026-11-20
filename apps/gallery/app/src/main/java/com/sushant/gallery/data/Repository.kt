package com.sushant.gallery.data

import android.database.ContentObserver
import android.net.Uri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Single source of truth for the library, bin, favourites and hidden album. */
class Repository(
    val db: GalleryDb,
    val media: MediaSource,
    val ops: FileOps,
    val vault: Vault,
    val prefs: Prefs,
    val diag: Diagnostics,
    private val scope: CoroutineScope,
) {
    private val raw = MutableStateFlow<List<MediaItem>>(emptyList())
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    private val io = Mutex()
    private var observer: ContentObserver? = null
    private var pending: Job? = null

    val favourites: StateFlow<Set<String>> = db.favourites().paths().map { it.toSet() }
        .stateIn(scope, SharingStarted.Eagerly, emptySet())
    val trash: StateFlow<List<TrashEntity>> = db.trash().all().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val vaultEntities: StateFlow<List<VaultEntity>> = db.vault().all().stateIn(scope, SharingStarted.Eagerly, emptyList())

    val items: StateFlow<List<MediaItem>> = combine(raw, prefs.photoSort, prefs.sortDesc) { l, s, d -> Sorting.photos(l, s, d) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val albums: StateFlow<List<Album>> = combine(raw, prefs.albumSort) { l, s -> Sorting.albums(buildAlbums(l), s) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    val vaultItems: StateFlow<List<MediaItem>> = vaultEntities.map { l -> l.map { it.toItem() } }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    init {
        scope.launch { prefs.showHiddenFiles.drop(1).collect { refresh() } }
    }

    fun start() {
        if (observer == null) observer = media.observe { refreshSoon() }
        refresh()
    }

    fun stop() { observer?.let { media.unobserve(it) }; observer = null }

    fun refresh() {
        scope.launch(Dispatchers.IO) {
            val l = media.load(prefs.showHiddenFiles.value)
            raw.value = l
            _loaded.value = true
        }
    }

    private fun refreshSoon() {
        pending?.cancel()
        pending = scope.launch { delay(400); refresh() }
    }

    fun listFor(source: Source, all: List<MediaItem>, favs: Set<String>, vault: List<MediaItem>, external: MediaItem?): List<MediaItem> = when (source) {
        Source.All -> all
        Source.Favourites -> all.filter { it.path in favs }
        Source.Videos -> all.filter { it.isVideo }
        Source.Vault -> vault
        Source.External -> listOfNotNull(external)
        is Source.Folder -> all.filter { it.folder == source.folder }
        is Source.Search -> searchFiles(all, source.query)
    }

    fun searchFiles(all: List<MediaItem>, q: String): List<MediaItem> {
        val t = q.trim(); if (t.isEmpty()) return emptyList()
        return all.filter { it.name.contains(t, ignoreCase = true) || it.bucket.contains(t, ignoreCase = true) }
    }

    // ---------- bin ----------
    /** Moves to the Recycle Bin. The items disappear from lists at once. Returns the bin entries (for Undo). */
    suspend fun moveToBin(list: List<MediaItem>, fromFilmstrip: Boolean = false): List<TrashEntity> {
        val keys = list.map { it.key }.toSet()
        raw.value = raw.value.filterNot { it.key in keys }
        val favs = favourites.value
        val done = withContext(Dispatchers.IO) {
            io.withLock {
                list.mapNotNull { m ->
                    val e = ops.moveToTrash(m, m.path in favs) ?: return@mapNotNull null
                    val id = db.trash().insert(e)
                    if (m.path in favs) db.favourites().remove(m.path)
                    e.copy(id = id)
                }
            }
        }
        diag.count(if (fromFilmstrip) Diagnostics.FLICKED else Diagnostics.DELETED, done.size.toLong())
        if (done.size < list.size) refresh()
        return done
    }

    suspend fun restore(list: List<TrashEntity>): Int = withContext(Dispatchers.IO) {
        var n = 0
        io.withLock {
            for (e in list) {
                val path = ops.restore(e) ?: continue
                db.trash().delete(e.id)
                if (e.wasFavourite) db.favourites().add(FavouriteEntity(path, System.currentTimeMillis()))
                n++
            }
        }
        if (n > 0) diag.count(Diagnostics.RESTORED, n.toLong())
        refresh(); n
    }

    suspend fun deleteForever(list: List<TrashEntity>) = withContext(Dispatchers.IO) {
        io.withLock { list.forEach { if (ops.deleteForever(it)) db.trash().delete(it.id) } }
    }

    suspend fun purgeExpired(now: Long = System.currentTimeMillis()): Int = withContext(Dispatchers.IO) {
        val old = db.trash().olderThan(now - Prefs.BIN_DAYS * 86_400_000L)
        deleteForever(old); old.size
    }

    // ---------- favourites / albums / edits ----------
    suspend fun setFavourite(list: List<MediaItem>, fav: Boolean) = withContext(Dispatchers.IO) {
        list.filter { it.vaultId == null }.forEach {
            if (fav) db.favourites().add(FavouriteEntity(it.path, System.currentTimeMillis())) else db.favourites().remove(it.path)
        }
    }

    suspend fun transfer(list: List<MediaItem>, folder: File, copy: Boolean): Int = withContext(Dispatchers.IO) {
        var n = 0
        io.withLock {
            for (m in list) {
                val to = ops.transfer(m, folder, copy) ?: continue
                if (!copy && m.path in favourites.value && to != m.path) db.favourites().move(m.path, to)
                n++
            }
        }
        refresh(); n
    }

    suspend fun rotate(item: MediaItem): Boolean = withContext(Dispatchers.IO) { ops.rotate(item).also { refresh() } }

    // ---------- hidden album ----------
    suspend fun hide(list: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        val keys = list.map { it.key }.toSet()
        raw.value = raw.value.filterNot { it.key in keys }
        var n = 0
        io.withLock { for (m in list) { if (vault.hide(m)) { db.favourites().remove(m.path); n++ } } }
        if (n < list.size) refresh()
        n
    }

    suspend fun unhide(list: List<MediaItem>): Int = withContext(Dispatchers.IO) {
        var n = 0
        io.withLock {
            for (m in list) {
                val e = m.vaultId?.let { db.vault().get(it) } ?: continue
                if (vault.unhide(e) != null) n++
            }
        }
        refresh(); n
    }

    suspend fun deleteHidden(list: List<MediaItem>) = withContext(Dispatchers.IO) {
        io.withLock { for (m in list) { m.vaultId?.let { db.vault().get(it) }?.let { vault.remove(it) } } }
    }

    fun vaultEntity(id: String): VaultEntity? = vaultEntities.value.firstOrNull { it.id == id }

    companion object {
        fun buildAlbums(list: List<MediaItem>): List<Album> = list.groupBy { it.folder }.map { (folder, l) ->
            val cover = l.maxByOrNull { it.dateTaken }
            Album(folder, l.first().bucket, l.size, cover, cover?.dateTaken ?: 0)
        }

        fun VaultEntity.toItem() = MediaItem(
            id = -2, uri = Uri.EMPTY, path = "vault:$id", name = name, mime = mime, isVideo = isVideo, dateTaken = dateTaken,
            dateModified = addedAt, size = size, width = width, height = height, durationMs = durationMs,
            folder = "vault", bucket = "Hidden", vaultId = id,
        )
    }
}
