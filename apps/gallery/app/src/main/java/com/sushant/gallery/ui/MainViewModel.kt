package com.sushant.gallery.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import com.sushant.gallery.GalleryApp
import com.sushant.gallery.R
import com.sushant.gallery.data.Diagnostics
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.Source
import com.sushant.gallery.data.TrashEntity
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.io.File

data class Snack(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null)

class MainViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val g = app as GalleryApp
    val repo = g.repo
    val prefs = g.prefs
    val diag = g.diag
    val vault = g.vault
    private val scope = g.appScope

    val items = repo.items
    val albums = repo.albums
    val favourites = repo.favourites
    val trash = repo.trash
    val vaultItems = repo.vaultItems
    val loaded = repo.loaded

    private val _snacks = Channel<Snack>(Channel.BUFFERED)
    val snacks = _snacks.receiveAsFlow()

    private val _access = MutableStateFlow(true)
    val hasAccess: StateFlow<Boolean> = _access.asStateFlow()
    fun setAccess(v: Boolean) {
        val was = _access.value
        _access.value = v
        if (v && (!was || !started)) { started = true; repo.start() }
    }
    private var started = false

    private val _unlocked = g.vaultUnlocked
    val vaultUnlocked: StateFlow<Boolean> = _unlocked.asStateFlow()
    fun unlockVault() { _unlocked.value = true }
    fun lockVault() { if (_unlocked.value) { _unlocked.value = false; vault.lock() } }

    var external: MediaItem? = null
        private set

    // ---------- navigation registry: routes carry a small number, the real value lives here ----------
    private fun keys(): ArrayList<String> = saved.get<ArrayList<String>>("navKeys") ?: arrayListOf<String>().also { saved["navKeys"] = it }
    fun reg(value: String): Int { val k = keys(); k += value; saved["navKeys"] = k; return k.size - 1 }
    fun key(id: Int): String = keys().getOrNull(id) ?: "all"

    fun viewerRoute(source: Source, index: Int): String = "viewer/${reg(source.encode() + "|" + index)}"
    fun viewerArgs(id: Int): Pair<Source, Int> {
        val v = key(id); val i = v.lastIndexOf('|')
        return if (i < 0) Source.All to 0 else Source.decode(v.substring(0, i)) to (v.substring(i + 1).toIntOrNull() ?: 0)
    }

    // ---------- selection (survives rotation and process death) ----------
    val selection: StateFlow<List<String>> = saved.getStateFlow("sel", emptyList())
    fun toggle(key: String) { val s = selection.value; saved["sel"] = if (key in s) s - key else s + key }
    fun select(keys: Collection<String>, on: Boolean) {
        val s = selection.value.toMutableSet(); if (on) s.addAll(keys) else s.removeAll(keys.toSet()); saved["sel"] = s.toList()
    }
    fun clearSelection() { saved["sel"] = emptyList<String>() }

    fun list(source: Source): List<MediaItem> =
        repo.listFor(source, items.value, favourites.value, vaultItems.value, external)

    // ---------- intents ----------
    fun openExternal(intent: Intent?): MediaItem? {
        val uri: Uri = intent?.data ?: return null
        if (intent.action != Intent.ACTION_VIEW) return null
        external = g.media.fromExternalUri(uri, intent.type)
        return external
    }

    private fun str(id: Int, vararg a: Any): String = getApplication<Application>().getString(id, *a)
    private fun plural(id: Int, n: Int): String = getApplication<Application>().resources.getQuantityString(id, n, n)
    private fun snack(s: Snack) { _snacks.trySend(s) }
    fun toast(text: String) = snack(Snack(text))

    // ---------- actions ----------
    fun moveToBin(list: List<MediaItem>, fromFilmstrip: Boolean = false, onUndo: ((List<TrashEntity>) -> Unit)? = null) {
        if (list.isEmpty()) return
        scope.launch {
            val done = repo.moveToBin(list, fromFilmstrip)
            if (done.isEmpty()) { snack(Snack(str(R.string.msg_delete_failed))); return@launch }
            undoStack.addLast(done)
            val text = if (done.size == 1) str(R.string.msg_moved_to_bin) else plural(R.plurals.msg_moved_n_to_bin, done.size)
            snack(Snack(text, str(R.string.undo)) {
                undoStack.remove(done)
                scope.launch { repo.restore(done); onUndo?.invoke(done) }
            })
        }
    }

    private val undoStack = ArrayDeque<List<TrashEntity>>()

    /** Ctrl+Z in the filmstrip: brings back the last flicked item(s). */
    fun undoLast() {
        val last = undoStack.removeLastOrNull() ?: return
        scope.launch { repo.restore(last) }
    }

    fun restore(list: List<TrashEntity>) = scope.launch {
        val n = repo.restore(list)
        snack(Snack(if (n == list.size) plural(R.plurals.msg_restored, n) else str(R.string.msg_restore_failed)))
    }

    fun deleteForever(list: List<TrashEntity>) = scope.launch {
        repo.deleteForever(list); snack(Snack(plural(R.plurals.msg_deleted_forever, list.size)))
    }

    fun setFavourite(list: List<MediaItem>, fav: Boolean, quiet: Boolean = false) = scope.launch {
        repo.setFavourite(list, fav)
        if (!quiet) snack(Snack(if (fav) plural(R.plurals.msg_added_fav, list.size) else str(R.string.msg_removed_fav)))
    }

    fun transfer(list: List<MediaItem>, folder: File, copy: Boolean) = scope.launch {
        val n = repo.transfer(list, folder, copy)
        val name = folder.name
        snack(Snack(if (n == 0) str(R.string.msg_transfer_failed) else if (copy) str(R.string.msg_copied_to, name) else str(R.string.msg_moved_to, name)))
    }

    fun rotate(item: MediaItem) = scope.launch {
        snack(Snack(str(if (repo.rotate(item)) R.string.msg_rotated else R.string.msg_rotate_failed)))
    }

    fun hide(list: List<MediaItem>) = scope.launch {
        val n = repo.hide(list)
        snack(Snack(if (n > 0) plural(R.plurals.msg_hidden, n) else str(R.string.msg_hide_failed)))
    }

    fun unhide(list: List<MediaItem>) = scope.launch {
        val n = repo.unhide(list); snack(Snack(plural(R.plurals.msg_unhidden, n)))
    }

    fun deleteHidden(list: List<MediaItem>) = scope.launch {
        repo.deleteHidden(list); snack(Snack(plural(R.plurals.msg_deleted_forever, list.size)))
    }

    fun countEdit() = diag.count(Diagnostics.EDITED)
    fun countShare(n: Int) = diag.count(Diagnostics.SHARED, n.toLong())
}
