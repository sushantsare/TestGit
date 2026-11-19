package com.sushant.grabbit.ui

import android.app.Application
import android.net.Uri
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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
            } catch (e: Throwable) {
                val raw = e.message ?: e.toString()
                FetchState.Failed(Errors.friendly(raw), raw)
            }
        }
    }

    fun download(info: MediaInfo, quality: String) {
        Downloads.enqueue(
            app,
            Downloads.Job(info.url, info.title, info.thumbnail, info.site, quality, info.isPlaylist),
        )
        app.toast(if (info.isPlaylist) "Added ${info.count} videos to downloads" else "Download started")
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

    fun delete(item: SavedItem) {
        val ok = runCatching {
            if (item.path != null) File(item.path).delete()
            else app.contentResolver.delete(Uri.parse(item.uri), null, null)
        }.isSuccess
        Library.remove(item.id)
        app.toast(if (ok) "Deleted" else "Removed from list (delete the file from your gallery)")
    }

    fun restartEngine() = Engine.start(app)

    fun updateEngine() {
        viewModelScope.launch { app.toast(Engine.update(app)) }
    }
}
