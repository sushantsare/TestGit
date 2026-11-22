@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.TarangApp
import com.sushant.tarang.data.*
import com.sushant.tarang.playback.PlayerConnection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Something the screen must hand to Android (delete / write permission prompts). */
sealed class SystemAsk {
    data class Delete(val sender: IntentSender, val song: Song) : SystemAsk()
    data class Write(val sender: IntentSender, val song: Song, val edit: TagEdit) : SystemAsk()
    data class LegacyWrite(val song: Song, val edit: TagEdit?) : SystemAsk()
}


class MainVm(application: Application) : AndroidViewModel(application) {
    val app = application as TarangApp
    val repo = app.repo
    val prefs = app.prefs
    val hub = app.hub
    val player = PlayerConnection(application, viewModelScope)

    val settings = prefs.settings
    val library = repo.library
    val liked = repo.likedIds
    val playlists = repo.playlists
    val counts = repo.playCounts
    val scanning = repo.scanning

    val snack = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val systemAsk = MutableStateFlow<SystemAsk?>(null)

    val currentSong: StateFlow<Song?> = combine(player.ui.map { it.currentId }.distinctUntilChanged(), library) { id, lib ->
        id?.let { lib.byId[it] ?: runCatching { repo.dao.song(it)?.toSong() }.getOrNull() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Safety net so the splash never hangs if the database is slow. */
    @Volatile var startupTimedOut = false
    val recentSearches = MutableStateFlow(prefs.recentSearches)

    init {
        viewModelScope.launch { kotlinx.coroutines.delay(1500); startupTimedOut = true }
        player.connect()
        viewModelScope.launch { hub.error.filterNotNull().collect { snack.tryEmit(it); hub.error.value = null } }
    }

    override fun onCleared() { player.release(); super.onCleared() }

    fun onPermissionGranted() {
        viewModelScope.launch { repo.scan(); repo.watchMediaStore() }
    }
    fun rescan() = viewModelScope.launch { repo.scan(); snack.tryEmit("Library updated · ${plural(library.value.songs.size, "song")}") }

    fun setSettings(f: (Settings) -> Settings) = prefs.update(f)

    // ---- likes / playlists
    fun toggleLike(id: Long) = viewModelScope.launch {
        val was = repo.isLiked(id); repo.toggleLike(id)
        snack.tryEmit(if (was) "Removed from Liked songs" else "Added to Liked songs")
    }
    fun createPlaylist(name: String, addIds: List<Long> = emptyList()) = viewModelScope.launch {
        val id = repo.createPlaylist(name); if (addIds.isNotEmpty()) repo.addToPlaylist(id, addIds)
        snack.tryEmit("Playlist “${name.trim()}” created")
    }
    fun setPlaylistMembership(songIds: List<Long>, wanted: Set<Long>) = viewModelScope.launch {
        playlists.value.forEach { p ->
            songIds.forEach { sid ->
                val inIt = sid in p.songIds
                if (p.id in wanted && !inIt) repo.addToPlaylist(p.id, listOf(sid))
                if (p.id !in wanted && inIt && songIds.size == 1) repo.removeFromPlaylist(p.id, sid)
            }
        }
    }
    fun likeMany(ids: List<Long>, like: Boolean) = viewModelScope.launch {
        ids.forEach { if (repo.isLiked(it) != like) repo.toggleLike(it) }
    }
    fun renamePlaylist(id: Long, name: String) = viewModelScope.launch { repo.renamePlaylist(id, name) }
    fun deletePlaylist(id: Long) = viewModelScope.launch { repo.deletePlaylist(id); snack.tryEmit("Playlist deleted") }
    fun removeFromPlaylist(pid: Long, sid: Long) = viewModelScope.launch { repo.removeFromPlaylist(pid, sid) }
    fun reorderPlaylist(pid: Long, ids: List<Long>) = viewModelScope.launch { repo.reorderPlaylist(pid, ids) }

    // ---- delete the real file (Android shows its own "Allow" box first on Android 11+)
    fun requestDelete(song: Song) = viewModelScope.launch {
        val cr = app.contentResolver
        when {
            Build.VERSION.SDK_INT >= 30 -> systemAsk.value = SystemAsk.Delete(MediaStore.createDeleteRequest(cr, listOf(song.uri)).intentSender, song)
            Build.VERSION.SDK_INT == 29 -> try {
                withContext(Dispatchers.IO) { cr.delete(song.uri, null, null) }; afterDelete(song)
            } catch (e: RecoverableSecurityException) {
                systemAsk.value = SystemAsk.Delete(e.userAction.actionIntent.intentSender, song)
            }
            else -> systemAsk.value = SystemAsk.LegacyWrite(song, null)
        }
    }
    /** Android 8–9: after the storage permission was granted. */
    fun deleteLegacy(song: Song) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { runCatching { app.contentResolver.delete(song.uri, null, null) > 0 || File(song.path).delete() }.getOrDefault(false) }
        if (ok) afterDelete(song) else snack.tryEmit("Couldn't delete “${song.title}”")
    }
    fun afterDelete(song: Song) = viewModelScope.launch {
        // check the file is really gone before saying so
        val gone = withContext(Dispatchers.IO) {
            app.contentResolver.query(song.uri, arrayOf(MediaStore.Audio.Media._ID), null, null, null)?.use { it.count == 0 } ?: true
        }
        if (gone) {
            player.dropSong(song.id); repo.forgetSong(song.id)
            snack.tryEmit("Deleted “${song.title}”")
        } else snack.tryEmit("“${song.title}” was not deleted")
    }

    // ---- edit details written into the file
    fun requestEdit(song: Song, edit: TagEdit) = viewModelScope.launch {
        val cr = app.contentResolver
        when {
            Build.VERSION.SDK_INT >= 30 -> systemAsk.value = SystemAsk.Write(MediaStore.createWriteRequest(cr, listOf(song.uri)).intentSender, song, edit)
            Build.VERSION.SDK_INT == 29 -> runCatching { cr.openFileDescriptor(song.uri, "rw")?.close() }.fold(
                onSuccess = { writeTags(song, edit) },
                onFailure = { e -> if (e is RecoverableSecurityException) systemAsk.value = SystemAsk.Write(e.userAction.actionIntent.intentSender, song, edit) else snack.tryEmit("Can't change this file") })
            else -> systemAsk.value = SystemAsk.LegacyWrite(song, edit)
        }
    }

    fun writeTags(song: Song, edit: TagEdit) = viewModelScope.launch {
        val res = withContext(Dispatchers.IO) { TagWriter.write(app, song, edit) }
        if (res == null) {
            repo.covers.forget(song.id)
            // update our copy at once; MediaStore catches up after its own scan
            repo.dao.upsertSongs(listOf(SongEntity(song.id, edit.title.ifBlank { song.title }, edit.artist.ifBlank { UNKNOWN_ARTIST },
                edit.album.ifBlank { UNKNOWN_ALBUM }, song.albumId, edit.albumArtist.ifBlank { edit.artist.ifBlank { UNKNOWN_ARTIST } },
                song.durationMs, song.sizeBytes, song.mime, song.path, song.folderPath, song.folderName, song.fileName, song.dateAdded,
                edit.year.toIntOrNull() ?: song.year, song.track)))
            snack.tryEmit("Saved into the song file")
        } else snack.tryEmit(res)
    }

    // ---- lyrics
    fun saveLyrics(songId: Long, text: String, source: String) = viewModelScope.launch {
        repo.lyrics.save(songId, text, source); snack.tryEmit(if (text.isBlank()) "Lyrics removed" else "Lyrics saved")
    }
    fun importLrc(songId: Long, uri: Uri) = viewModelScope.launch {
        val text = withContext(Dispatchers.IO) { runCatching { app.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull() }
        if (text.isNullOrBlank() || LrcParser.parse(text, "") == null) snack.tryEmit("That file has no lyrics in it")
        else { repo.lyrics.save(songId, text, "Imported .lrc file"); snack.tryEmit("Lyrics added") }
    }

    fun resetStats() = viewModelScope.launch { repo.resetStats(); snack.tryEmit("Stats cleared") }

    fun rememberSearch(q: String) { val t = q.trim(); if (t.length >= 2) { prefs.recentSearches = (listOf(t) + prefs.recentSearches.filter { !it.equals(t, true) }).take(8); recentSearches.value = prefs.recentSearches } }
    fun forgetSearch(q: String) { prefs.recentSearches = prefs.recentSearches.filter { it != q }; recentSearches.value = prefs.recentSearches }

    @Suppress("unused") private fun values() = ContentValues()
}
