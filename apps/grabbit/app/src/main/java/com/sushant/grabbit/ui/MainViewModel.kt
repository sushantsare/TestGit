package com.sushant.grabbit.ui

import android.app.Application
import android.app.RecoverableSecurityException
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sushant.grabbit.data.Library
import com.sushant.grabbit.data.LoginSite
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.data.SavedItem
import com.sushant.grabbit.data.Sites
import com.sushant.grabbit.download.Downloads
import com.sushant.grabbit.engine.Engine
import com.sushant.grabbit.engine.Errors
import com.sushant.grabbit.engine.Media
import com.sushant.grabbit.engine.MediaInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

sealed interface FetchState {
    data object Idle : FetchState
    data object Loading : FetchState
    data class Failed(val message: String, val raw: String) : FetchState
    data class Loaded(val info: MediaInfo) : FetchState
}

class MainViewModel(private val app: Application) : AndroidViewModel(app) {
    var tab by mutableIntStateOf(0)
    var url by mutableStateOf("")
    var fetchState by mutableStateOf<FetchState>(FetchState.Idle)
        private set
    var loginSite by mutableStateOf<LoginSite?>(null)

    /** The header mark's drop-in animation plays once per app open. */
    var playIntro = false

    /** After the setup guide: put the cursor in the link field. */
    var focusUrl by mutableStateOf(false)
    var loginTick by mutableIntStateOf(0)
    var hidden by mutableStateOf(Prefs.hidden)
        private set

    val engine = Engine.state
    val engineVersion = Engine.version
    val engineUpdating = Engine.updating
    val library = Library.items
    val works: StateFlow<List<WorkInfo>> = WorkManager.getInstance(app)
        .getWorkInfosByTagFlow(Downloads.TAG)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private var fetchJob: Job? = null

    fun onShared(text: String) {
        val link = Sites.extractUrl(text) ?: return
        url = link
        tab = 0
        fetch()
    }

    fun resetFetch() {
        fetchJob?.cancel()
        Media.cancelFetch()
        fetchState = FetchState.Idle
    }

    fun fetch() {
        val link = Sites.extractUrl(url.trim())
        if (link == null) {
            fetchState = FetchState.Failed("Paste a valid link that starts with http:// or https://", "")
            return
        }
        url = link
        fetchJob?.cancel()
        fetchState = FetchState.Loading
        fetchJob = viewModelScope.launch {
            fetchState = try {
                FetchState.Loaded(Media.fetchInfo(app, link))
            } catch (e: CancellationException) {
                throw e
            } catch (e: com.sushant.grabbit.engine.FriendlyError) {
                FetchState.Failed(e.message ?: "", e.raw)
            } catch (e: Throwable) {
                val raw = e.message ?: e.toString()
                FetchState.Failed(Errors.friendly(raw), raw)
            }
        }
    }

    fun download(info: MediaInfo, quality: String, opts: com.sushant.grabbit.download.DlOpts = com.sushant.grabbit.download.DlOpts()) {
        Downloads.enqueue(
            app,
            Downloads.Job(info.url, info.title, info.thumbnail, info.site, quality, info.isPlaylist, opts, items = info.gallery),
        )
        app.toast(if (info.isPlaylist) "Added ${info.count} videos to downloads" else "Download started")
        // The job is queued: start the Grab tab fresh for the next link
        url = ""
        resetFetch()
        tab = 1
    }

    fun retry(w: WorkInfo) {
        hide(w.id)
        Downloads.enqueue(app, Downloads.jobOf(w))
    }

    fun tryAnotherFormat(w: WorkInfo) {
        hide(w.id)
        url = Downloads.jobOf(w).url
        tab = 0
        fetch()
    }

    fun cancel(id: UUID) = Downloads.cancel(app, id)

    val paused = com.sushant.grabbit.data.Paused.items

    fun pause(w: WorkInfo) = Downloads.pause(app, w)
    fun resume(p: com.sushant.grabbit.data.PausedItem) { Downloads.resume(app, p) }
    fun discard(p: com.sushant.grabbit.data.PausedItem) = Downloads.discard(app, p)
    fun pauseAll(running: List<WorkInfo>) = running.forEach { Downloads.pause(app, it) }
    fun resumeAll() = paused.value.forEach { Downloads.resume(app, it) }

    fun hide(id: UUID) {
        hidden = hidden + id.toString()
        Prefs.hidden = hidden
    }

    fun clearFinished() {
        val done = works.value.filter { it.state.isFinished }.map { it.id.toString() }
        hidden = hidden + done
        Prefs.hidden = hidden
        WorkManager.getInstance(app).pruneWork()
    }

    /** Library items whose file no longer exists on the phone (deleted from the gallery, etc.) */
    var missing by mutableStateOf<Set<String>>(emptySet())
        private set

    fun checkMissing() {
        viewModelScope.launch {
            missing = withContext(Dispatchers.IO) { findMissing(Library.items.value) }
        }
    }

    fun findMissing(items: List<SavedItem>): Set<String> = items.filterNot { fileExists(it) }.map { it.id }.toSet()

    /** True if the file is still on the phone. Unknown/errors count as "exists" so nothing is hidden by mistake. */
    /** Files saved into a folder the user picked are reached through the folder picker's documents. */
    private fun isDoc(item: SavedItem) = runCatching {
        android.provider.DocumentsContract.isDocumentUri(app, Uri.parse(item.uri))
    }.getOrDefault(false)

    fun fileExists(item: SavedItem): Boolean {
        if (item.path != null) return File(item.path).exists()
        if (isDoc(item)) return runCatching {
            androidx.documentfile.provider.DocumentFile.fromSingleUri(app, Uri.parse(item.uri))?.exists() ?: true
        }.getOrDefault(true)
        return runCatching {
            app.contentResolver.query(Uri.parse(item.uri), arrayOf(MediaStore.MediaColumns._ID), null, null, null)
                ?.use { it.count > 0 } ?: true
        }.getOrDefault(true)
    }

    sealed interface DeleteResult {
        data object Done : DeleteResult
        data object AlreadyGone : DeleteResult
        data class NeedsConfirm(val sender: IntentSender) : DeleteResult
        data class Failed(val message: String) : DeleteResult
    }

    /** Deletes the real file. Android may need the user to confirm (files the app no longer owns). */
    fun deleteNow(item: SavedItem): DeleteResult {
        if (!fileExists(item)) {
            Library.remove(item.id)
            return DeleteResult.AlreadyGone
        }
        if (isDoc(item)) {
            runCatching { android.provider.DocumentsContract.deleteDocument(app.contentResolver, Uri.parse(item.uri)) }
            return if (!fileExists(item)) { Library.remove(item.id); DeleteResult.Done }
            else DeleteResult.Failed("Couldn't delete the file")
        }
        if (item.path != null) {
            File(item.path).delete()
            return if (!fileExists(item)) { Library.remove(item.id); DeleteResult.Done }
            else DeleteResult.Failed("Couldn't delete the file")
        }
        val uri = Uri.parse(item.uri)
        var error: Exception? = null
        try {
            app.contentResolver.delete(uri, null, null)
        } catch (e: SecurityException) {
            error = e
        }
        if (!fileExists(item)) {
            Library.remove(item.id)
            return DeleteResult.Done
        }
        val sender: IntentSender? = when {
            Build.VERSION.SDK_INT >= 30 ->
                MediaStore.createDeleteRequest(app.contentResolver, listOf(uri)).intentSender
            Build.VERSION.SDK_INT == 29 && error is RecoverableSecurityException ->
                error.userAction.actionIntent.intentSender
            else -> null
        }
        return if (sender != null) DeleteResult.NeedsConfirm(sender)
        else DeleteResult.Failed("Couldn't delete the file. Delete it from your gallery.")
    }

    fun delete(item: SavedItem, confirm: (IntentSender) -> Unit) {
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { deleteNow(item) }) {
                DeleteResult.Done -> app.toast("Deleted")
                DeleteResult.AlreadyGone -> app.toast("The file was already gone. Removed from the list.")
                is DeleteResult.NeedsConfirm -> confirm(r.sender)
                is DeleteResult.Failed -> app.toast(r.message)
            }
        }
    }

    /** Called after Android's "Allow delete?" prompt closes: check what really happened. */
    fun afterDeleteConfirm(item: SavedItem) {
        viewModelScope.launch {
            val gone = withContext(Dispatchers.IO) { !fileExists(item) }
            if (gone) {
                Library.remove(item.id)
                app.toast("Deleted")
            } else app.toast("The file was not deleted")
        }
    }

    sealed interface RenameResult {
        data class Done(val name: String) : RenameResult
        data class NeedsConfirm(val sender: IntentSender) : RenameResult
        data class Failed(val message: String) : RenameResult
    }

    /** Renames the real file on the phone and the entry in the list. [base] is the name without extension. */
    fun renameNow(item: SavedItem, base: String): RenameResult {
        val clean = com.sushant.grabbit.download.FileNames.clean(base)
        if (clean.isBlank()) return RenameResult.Failed("Type a name first")
        if (!fileExists(item)) return RenameResult.Failed("The file is missing, so it can't be renamed")
        val ext = item.fileName.substringAfterLast('.', "").takeIf { '.' in item.fileName && it.length in 1..5 }
            ?: android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(item.mime).orEmpty()
        val unique = com.sushant.grabbit.download.FileNames::unique
        if (isDoc(item)) {
            val cr = app.contentResolver
            var n = 1
            while (n < 20) {
                val newName = if (n == 1) (if (ext.isBlank()) clean else "$clean.$ext") else (if (ext.isBlank()) "$clean ($n)" else "$clean ($n).$ext")
                if (newName == item.fileName) return RenameResult.Done(newName)
                val newUri = runCatching { android.provider.DocumentsContract.renameDocument(cr, Uri.parse(item.uri), newName) }.getOrNull()
                if (newUri != null) {
                    val real = androidx.documentfile.provider.DocumentFile.fromSingleUri(app, newUri)?.name ?: newName
                    Library.update(item.copy(title = clean, fileName = real, uri = newUri.toString()))
                    return RenameResult.Done(real)
                }
                n++
            }
            return RenameResult.Failed("Couldn't rename the file")
        }
        if (item.path != null) {
            val old = File(item.path)
            val newName = unique(clean, ext) { n -> n != old.name && File(old.parentFile, n).exists() }
            val dest = File(old.parentFile, newName)
            if (dest.absolutePath != old.absolutePath && !old.renameTo(dest)) return RenameResult.Failed("Couldn't rename the file")
            android.media.MediaScannerConnection.scanFile(app, arrayOf(old.absolutePath, dest.absolutePath), null, null)
            val uri = androidx.core.content.FileProvider.getUriForFile(app, "${app.packageName}.files", dest)
            Library.update(item.copy(title = clean, fileName = newName, path = dest.absolutePath, uri = uri.toString()))
            return RenameResult.Done(newName)
        }
        if (Build.VERSION.SDK_INT < 29) return RenameResult.Failed("Couldn't rename the file")
        val uri = Uri.parse(item.uri)
        val cr = app.contentResolver
        val rel = runCatching {
            cr.query(uri, arrayOf(MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: return RenameResult.Failed("Couldn't find the file")
        val newName = unique(clean, ext) { n -> com.sushant.grabbit.download.Saver.taken(app, n, rel, uri) }
        try {
            cr.update(uri, android.content.ContentValues().apply { put(MediaStore.MediaColumns.DISPLAY_NAME, newName) }, null, null)
        } catch (e: SecurityException) {
            val sender: IntentSender? = when {
                Build.VERSION.SDK_INT >= 30 -> MediaStore.createWriteRequest(cr, listOf(uri)).intentSender
                Build.VERSION.SDK_INT == 29 && e is RecoverableSecurityException -> e.userAction.actionIntent.intentSender
                else -> null
            }
            return if (sender != null) RenameResult.NeedsConfirm(sender)
            else RenameResult.Failed("Android didn't allow renaming this file")
        }
        val real = runCatching {
            cr.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
        }.getOrNull() ?: newName
        Library.update(item.copy(title = clean, fileName = real))
        return RenameResult.Done(real)
    }

    fun rename(item: SavedItem, base: String, confirm: (IntentSender) -> Unit) {
        viewModelScope.launch {
            when (val r = withContext(Dispatchers.IO) { renameNow(item, base) }) {
                is RenameResult.Done -> app.toast("Renamed to ${r.name}")
                is RenameResult.NeedsConfirm -> confirm(r.sender)
                is RenameResult.Failed -> app.toast(r.message)
            }
        }
    }

    fun removeFromList(item: SavedItem) {
        Library.remove(item.id)
        missing = missing - item.id
    }

    fun restartEngine() = Engine.start(app)

    fun updateEngine() {
        viewModelScope.launch { app.toast(Engine.update(app)) }
    }
}
