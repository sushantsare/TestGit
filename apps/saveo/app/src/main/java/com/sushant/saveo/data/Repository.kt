package com.sushant.saveo.data

import android.app.RecoverableSecurityException
import android.content.Context
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

enum class LoadState { LOADING, READY }

data class StatusState(
    val load: LoadState = LoadState.LOADING,
    val items: List<StatusItem> = emptyList(),
    val connected: Set<Source> = emptySet(),
    val lost: Set<Source> = emptySet(),
    val lastScan: Long = 0,
)

sealed interface SaveResult {
    data class Saved(val item: SavedItem) : SaveResult
    data class AlreadySaved(val item: SavedItem) : SaveResult
    data class Failed(val reason: String) : SaveResult
}

sealed interface DeleteResult {
    data class Done(val ids: List<Long>) : DeleteResult
    /** Android wants the user to confirm (files created before a reinstall). */
    data class NeedsConfirm(val sender: IntentSender, val ids: List<Long>) : DeleteResult
}

class Repository(
    private val ctx: Context,
    val db: SaveoDb,
    val prefs: Prefs,
    val diagnostics: Diagnostics,
) {
    val scanner = StatusScanner(ctx, prefs)
    val saver = MediaStoreSaver(ctx)
    private val dao get() = db.dao()
    private val saveLock = Mutex()

    private val _status = MutableStateFlow(StatusState())
    val status: StateFlow<StatusState> = _status.asStateFlow()

    val saved: Flow<List<SavedItem>> = dao.observeSaved()
    val bin: Flow<List<SavedItem>> = dao.observeBin()
    val savedKeys: Flow<Set<String>> = saved.map { list -> list.mapTo(HashSet()) { it.matchKey } }

    /** Keys of statuses being saved right now (to show a spinner in the save button). */
    private val _saving = MutableStateFlow<Set<String>>(emptySet())
    val saving: StateFlow<Set<String>> = _saving.asStateFlow()

    private val durationCache = LruCache<String, Long>(800)

    suspend fun refresh(): ScanResult = withContext(Dispatchers.IO) {
        val r = scanner.scan()
        _status.value = StatusState(LoadState.READY, r.items, r.connected, r.lost, System.currentTimeMillis())
        r
    }

    /** Returns the statuses that were never seen before and remembers them as seen. */
    suspend fun markSeenAndGetNew(items: List<StatusItem>): List<StatusItem> = withContext(Dispatchers.IO) {
        val firstRun = dao.seenCount() == 0
        val seen = dao.seenKeys().toHashSet()
        val fresh = items.filter { it.matchKey !in seen }
        if (fresh.isNotEmpty()) dao.markSeen(fresh.map { Seen(it.matchKey, System.currentTimeMillis()) })
        if (firstRun) emptyList() else fresh
    }

    suspend fun durationOf(uri: Uri): Long {
        val k = uri.toString()
        durationCache.get(k)?.let { return it }
        val d = withContext(Dispatchers.IO) { saver.durationMs(uri) }
        durationCache.put(k, d)
        return d
    }

    fun cachedDuration(uri: Uri): Long? = durationCache.get(uri.toString())

    suspend fun save(item: StatusItem): SaveResult = withContext(Dispatchers.IO) {
        _saving.value = _saving.value + item.key
        try {
            saveLock.withLock { saveLocked(item) }
        } catch (e: Exception) {
            SaveResult.Failed(e.message ?: "Could not save")
        } finally {
            _saving.value = _saving.value - item.key
        }
    }

    private suspend fun saveLocked(item: StatusItem): SaveResult {
        val hash = saver.sha256(item.uri)
        dao.byHash(hash)?.let { existing ->
            if (existing.deletedAt == null && saver.exists(Uri.parse(existing.mediaUri))) return SaveResult.AlreadySaved(existing)
            if (existing.deletedAt != null && existing.binPath != null && File(existing.binPath).exists()) {
                return SaveResult.Saved(restoreInternal(existing))
            }
            dao.delete(existing.id) // stale row (file removed from the gallery) — save fresh
        }
        val mime = MediaTypes.mimeFor(item.name, item.kind)
        val uri = saver.insert({ saver.open(item.uri) }, item.name, item.kind, mime, item.modified, hash)
        val duration = if (item.kind == MediaKind.VIDEO) (cachedDuration(item.uri) ?: saver.durationMs(item.uri)) else 0L
        val row = SavedItem(
            hash = hash, matchKey = item.matchKey, mediaUri = uri.toString(), kind = item.kind.name, mime = mime,
            source = item.source.name, originalName = item.name, sizeBytes = item.size, durationMs = duration,
            statusTime = item.modified, savedAt = System.currentTimeMillis(),
        )
        val id = dao.insert(row)
        diagnostics.count(Diagnostics.SAVED)
        return SaveResult.Saved(row.copy(id = id))
    }

    /** Saves many; returns (newly saved, already saved, failed). */
    suspend fun saveAll(items: List<StatusItem>): Triple<Int, Int, Int> {
        var ok = 0; var dup = 0; var fail = 0
        for (it in items) when (save(it)) {
            is SaveResult.Saved -> ok++
            is SaveResult.AlreadySaved -> dup++
            is SaveResult.Failed -> fail++
        }
        return Triple(ok, dup, fail)
    }

    /** Moves saved items to the recycle bin: a private copy is kept, the gallery copy is removed. */
    suspend fun moveToBin(ids: List<Long>): DeleteResult = withContext(Dispatchers.IO) {
        val binDir = File(ctx.filesDir, "bin").apply { mkdirs() }
        val needOk = ArrayList<Uri>()
        val needIds = ArrayList<Long>()
        val done = ArrayList<Long>()
        var recoverable: IntentSender? = null
        for (id in ids) {
            val it = dao.byId(id) ?: continue
            if (it.deletedAt != null) continue
            val uri = Uri.parse(it.mediaUri)
            val ext = MediaTypes.extension(it.originalName).ifBlank { "bin" }
            val copy = File(binDir, "${it.id}.$ext")
            if (!copy.exists()) runCatching { saver.open(uri).use { input -> copy.outputStream().use { o -> input.copyTo(o) } } }
            if (!copy.exists()) { // file already gone from the gallery: just drop the row
                dao.delete(it.id); done += it.id; continue
            }
            try {
                saver.delete(uri)
                dao.update(it.copy(deletedAt = System.currentTimeMillis(), binPath = copy.absolutePath))
                done += it.id
            } catch (e: SecurityException) {
                needOk += uri; needIds += it.id
                if (Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException) recoverable = e.userAction.actionIntent.intentSender
            }
        }
        if (needOk.isNotEmpty()) {
            val sender = if (Build.VERSION.SDK_INT >= 30) MediaStore.createDeleteRequest(ctx.contentResolver, needOk).intentSender else recoverable
            if (sender != null) return@withContext DeleteResult.NeedsConfirm(sender, needIds)
        }
        DeleteResult.Done(done)
    }

    /** Called after the user allowed Android's delete prompt. */
    suspend fun confirmDeleted(ids: List<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) {
            val it = dao.byId(id) ?: continue
            val uri = Uri.parse(it.mediaUri)
            if (saver.exists(uri)) runCatching { saver.delete(uri) }
            if (!saver.exists(uri)) {
                val ext = MediaTypes.extension(it.originalName).ifBlank { "bin" }
                dao.update(it.copy(deletedAt = System.currentTimeMillis(), binPath = File(File(ctx.filesDir, "bin"), "${it.id}.$ext").absolutePath))
            }
        }
    }

    suspend fun restore(ids: List<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) dao.byId(id)?.let { runCatching { restoreInternal(it) } }
    }

    private suspend fun restoreInternal(item: SavedItem): SavedItem {
        val path = item.binPath ?: error("Missing bin file")
        val f = File(path)
        val uri = saver.insert({ f.inputStream() }, item.originalName, item.mediaKind, item.mime, item.statusTime, item.hash)
        val restored = item.copy(mediaUri = uri.toString(), deletedAt = null, binPath = null, savedAt = System.currentTimeMillis())
        dao.update(restored)
        f.delete()
        return restored
    }

    suspend fun deleteForever(ids: List<Long>) = withContext(Dispatchers.IO) {
        for (id in ids) dao.byId(id)?.let { it.binPath?.let { p -> File(p).delete() }; dao.delete(it.id) }
    }

    suspend fun purgeBin(now: Long = System.currentTimeMillis()) = withContext(Dispatchers.IO) {
        dao.binOlderThan(now - Formats.BIN_DAYS * Formats.DAY).forEach { it.binPath?.let { p -> File(p).delete() }; dao.delete(it.id) }
    }

    /** Removes saved rows whose gallery file was deleted outside Saveo (e.g. in the Gallery app). */
    suspend fun reconcileSaved() = withContext(Dispatchers.IO) {
        dao.allSaved().forEach { if (!saver.exists(Uri.parse(it.mediaUri))) dao.delete(it.id) }
    }

    /** Copies a status into the share cache so other apps can read it. Saved items are shared from the gallery directly. */
    suspend fun shareableCopy(item: StatusItem): File = withContext(Dispatchers.IO) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.filter { System.currentTimeMillis() - it.lastModified() > Formats.DAY }?.forEach { it.delete() }
        val f = File(dir, item.name)
        if (!f.exists() || f.length() != item.size) saver.open(item.uri).use { i -> f.outputStream().use { o -> i.copyTo(o) } }
        f
    }

    // ---- test helpers -----------------------------------------------------
    suspend fun testInsertSaved(item: SavedItem): Long = dao.insert(item)
}
