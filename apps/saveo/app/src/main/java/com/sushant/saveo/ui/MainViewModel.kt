package com.sushant.saveo.ui

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sushant.saveo.R
import com.sushant.saveo.SaveoApp
import com.sushant.saveo.data.DeleteResult
import com.sushant.saveo.data.Diagnostics
import com.sushant.saveo.data.Formats
import com.sushant.saveo.data.LoadState
import com.sushant.saveo.data.MediaKind
import com.sushant.saveo.data.SaveResult
import com.sushant.saveo.data.SavedItem
import com.sushant.saveo.data.Settings
import com.sushant.saveo.data.Source
import com.sushant.saveo.data.StatusItem
import com.sushant.saveo.data.StatusState
import com.sushant.saveo.work.Background
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

enum class Screen { ONBOARD, SETUP, HOME, SETTINGS, BIN, DIAG, ABOUT }
enum class MediaFilter { ALL, PHOTOS, VIDEOS }
enum class DateRangeKind { ANY, TODAY, WEEK, MONTH, CUSTOM }

/** One thing shown in a grid tile or the viewer — either a live status or a saved copy. */
data class Media(
    val key: String,
    val uri: Uri,
    val kind: MediaKind,
    val time: Long,
    val source: Source,
    val sizeBytes: Long,
    val status: StatusItem? = null,
    val saved: SavedItem? = null,
)

fun StatusItem.toMedia() = Media(key, uri, kind, modified, source, size, status = this)
fun SavedItem.toMedia() = Media("saved:$id", Uri.parse(mediaUri), mediaKind, statusTime.takeIf { it > 0 } ?: savedAt, sourceEnum, sizeBytes, saved = this)

sealed interface UiEvent {
    data class Snack(val text: String, val action: String? = null, val onAction: (() -> Unit)? = null) : UiEvent
    data class Launch(val intent: Intent) : UiEvent
    data class ConfirmDelete(val sender: IntentSender, val ids: List<Long>) : UiEvent
    data object DismissSnack : UiEvent
}

/** Viewer session: the list is frozen when opened so saving/deleting never jumps pages. */
data class ViewerSession(val fromSaved: Boolean, val items: List<Media>, val startIndex: Int)

class MainViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val saveoApp = app as SaveoApp
    val repo = saveoApp.repository
    private val prefs = repo.prefs

    val settings: StateFlow<Settings> = prefs.settings
    val status: StateFlow<StatusState> = repo.status
    val saving: StateFlow<Set<String>> = repo.saving
    val locked: StateFlow<Boolean> = saveoApp.locked
    val folders = prefs.folders
    val usage = repo.diagnostics.usage

    val screen: StateFlow<Screen> = handle.getStateFlow("screen", if (prefs.settings.value.onboarded) Screen.HOME else Screen.ONBOARD)
    val tab: StateFlow<Int> = handle.getStateFlow("tab", 0)
    val filter: StateFlow<MediaFilter> = handle.getStateFlow("filter", MediaFilter.ALL)
    val selection: StateFlow<List<String>?> = handle.getStateFlow<ArrayList<String>?>("sel", null)
    val dateKind: StateFlow<DateRangeKind> = handle.getStateFlow("dateKind", DateRangeKind.ANY)
    private val customRange: StateFlow<LongArray?> = handle.getStateFlow<LongArray?>("range", null)

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    private val _viewer = kotlinx.coroutines.flow.MutableStateFlow<ViewerSession?>(null)
    val viewer: StateFlow<ViewerSession?> = _viewer

    val savedAll: StateFlow<List<SavedItem>> = repo.saved.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val bin: StateFlow<List<SavedItem>> = repo.bin.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val savedKeys: StateFlow<Set<String>> = repo.savedKeys.stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val statuses: StateFlow<List<Media>> = combine(status, settings, filter) { st, s, f ->
        st.items.asSequence()
            .filter { s.sourceFilter == null || it.source == s.sourceFilter }
            .filter { f == MediaFilter.ALL || (f == MediaFilter.PHOTOS) == (it.kind == MediaKind.IMAGE) }
            .map { it.toMedia() }.toList()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val saved: StateFlow<List<Media>> = combine(savedAll, settings, filter, dateKind, customRange) { list, s, f, dk, range ->
        val (from, to) = rangeFor(dk, range)
        val l = list.asSequence()
            .filter { f == MediaFilter.ALL || (f == MediaFilter.PHOTOS) == (it.mediaKind == MediaKind.IMAGE) }
            .filter { it.savedAt in from..to }
            .map { it.toMedia() }.toList()
        if (s.sortNewest) l else l.reversed()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        repo.diagnostics.count(Diagnostics.OPENS)
        refresh()
        viewModelScope.launch { repo.purgeBin(); repo.reconcileSaved() }
    }

    private fun str(id: Int, vararg args: Any) = getApplication<Application>().getString(id, *args)
    private fun plural(id: Int, n: Int) = getApplication<Application>().resources.getQuantityString(id, n, n)
    private fun snack(text: String, action: String? = null, onAction: (() -> Unit)? = null) { _events.tryEmit(UiEvent.Snack(text, action, onAction)) }

    fun rangeFor(dk: DateRangeKind, custom: LongArray?, now: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val today = Formats.startOfDay(now)
        return when (dk) {
            DateRangeKind.ANY -> 0L to Long.MAX_VALUE
            DateRangeKind.TODAY -> today to Long.MAX_VALUE
            DateRangeKind.WEEK -> today - 6 * Formats.DAY to Long.MAX_VALUE
            DateRangeKind.MONTH -> today - 29 * Formats.DAY to Long.MAX_VALUE
            DateRangeKind.CUSTOM -> if (custom != null && custom.size == 2) Formats.startOfDay(custom[0]) to (Formats.startOfDay(custom[1]) + Formats.DAY - 1) else 0L to Long.MAX_VALUE
        }
    }

    // ---- navigation ---------------------------------------------------------
    fun go(s: Screen) { handle["screen"] = s }
    fun setTab(t: Int) { if (tab.value != t) { handle["tab"] = t; clearSelection(); if (twoPane) closeViewer() } }
    fun setFilter(f: MediaFilter) { handle["filter"] = f }
    fun setDates(kind: DateRangeKind, from: Long? = null, to: Long? = null) {
        handle["dateKind"] = kind
        handle["range"] = if (kind == DateRangeKind.CUSTOM && from != null && to != null) longArrayOf(from, to) else null
    }
    val customRangeValue: LongArray? get() = customRange.value

    /** Set by the UI: grid and viewer are side by side. */
    var twoPane = false

    /** Handles Back. Returns false when the app should close. */
    fun back(): Boolean {
        when {
            _viewer.value != null && !twoPane -> closeViewer()
            selection.value != null -> clearSelection()
            screen.value in setOf(Screen.BIN, Screen.DIAG, Screen.ABOUT) -> go(Screen.SETTINGS)
            screen.value == Screen.SETTINGS -> go(Screen.HOME)
            screen.value == Screen.SETUP && !settings.value.onboarded -> go(Screen.ONBOARD)
            screen.value == Screen.SETUP -> go(if (handle.get<Boolean>("setupFromSettings") == true) Screen.SETTINGS else Screen.HOME)
            screen.value == Screen.HOME && tab.value == 1 -> setTab(0)
            else -> return false
        }
        return true
    }

    fun openSetup(fromSettings: Boolean) { handle["setupFromSettings"] = fromSettings; go(Screen.SETUP) }

    fun finishOnboarding() {
        prefs.update { it.copy(onboarded = true) }
        go(Screen.HOME)
        refresh()
    }

    // ---- statuses -----------------------------------------------------------
    fun refresh(userAsked: Boolean = false) {
        viewModelScope.launch {
            val r = repo.refresh()
            repo.markSeenAndGetNew(r.items)
            if (userAsked) snack(str(R.string.snack_updated))
        }
    }

    fun save(m: Media) {
        val st = m.status ?: return
        viewModelScope.launch {
            when (val r = repo.save(st)) {
                is SaveResult.Saved -> snack(str(if (st.kind == MediaKind.VIDEO) R.string.snack_video_saved else R.string.snack_photo_saved))
                is SaveResult.AlreadySaved -> snack(str(R.string.snack_already))
                is SaveResult.Failed -> snack(str(R.string.snack_failed, r.reason))
            }
        }
    }

    fun saveMany(items: List<Media>) {
        val list = items.mapNotNull { it.status }
        clearSelection()
        viewModelScope.launch {
            val (ok, dup, fail) = repo.saveAll(list)
            when {
                ok > 0 -> snack(plural(R.plurals.snack_n_saved, ok))
                fail > 0 -> snack(str(R.string.snack_failed, "$fail"))
                dup > 0 -> snack(str(R.string.snack_all_already))
            }
        }
    }

    fun unsavedStatuses(): List<Media> = statuses.value.filter { it.status != null && it.status.matchKey !in savedKeys.value }

    // ---- selection ----------------------------------------------------------
    fun startSelection(first: String? = null) {
        _events.tryEmit(UiEvent.DismissSnack)
        handle["sel"] = ArrayList<String>().apply { if (first != null) add(first) }
        if (!prefs.hintSelectShown) prefs.hintSelectShown = true
    }
    fun toggleSelect(key: String) {
        val cur = selection.value ?: return startSelection(key)
        val n = ArrayList(cur); if (key in n) n.remove(key) else n.add(key)
        handle["sel"] = if (n.isEmpty()) null else n
    }
    fun selectAll(keys: List<String>) { handle["sel"] = ArrayList(keys) }
    fun clearSelection() { handle["sel"] = null }

    fun maybeShowSelectHint() {
        if (!prefs.hintSelectShown) { prefs.hintSelectShown = true; snack(str(R.string.hint_select)) }
    }

    // ---- saved --------------------------------------------------------------
    fun delete(ids: List<Long>) {
        clearSelection()
        viewModelScope.launch {
            when (val r = repo.moveToBin(ids)) {
                is DeleteResult.Done -> if (r.ids.isNotEmpty()) snack(plural(R.plurals.snack_moved_bin, r.ids.size), str(R.string.snack_undo)) { restore(r.ids, quiet = true) }
                is DeleteResult.NeedsConfirm -> _events.tryEmit(UiEvent.ConfirmDelete(r.sender, r.ids))
            }
        }
    }

    fun deleteConfirmed(ids: List<Long>) {
        viewModelScope.launch {
            repo.confirmDeleted(ids)
            snack(plural(R.plurals.snack_moved_bin, ids.size), str(R.string.snack_undo)) { restore(ids, quiet = true) }
        }
    }

    fun restore(ids: List<Long>, quiet: Boolean = false) {
        viewModelScope.launch { repo.restore(ids); if (!quiet) snack(str(R.string.snack_restored)) }
    }
    fun deleteForever(ids: List<Long>) { viewModelScope.launch { repo.deleteForever(ids) } }
    fun emptyBin() { viewModelScope.launch { repo.deleteForever(bin.value.map { it.id }); snack(str(R.string.snack_bin_emptied)) } }

    // ---- viewer -------------------------------------------------------------
    fun openViewer(fromSaved: Boolean, key: String) {
        val list = if (fromSaved) saved.value else statuses.value
        val idx = list.indexOfFirst { it.key == key }.coerceAtLeast(0)
        if (list.isEmpty()) return
        _viewer.value = ViewerSession(fromSaved, list, idx)
        repo.diagnostics.count(Diagnostics.VIEWED)
    }
    fun closeViewer() { _viewer.value = null }
    /** Drops deleted items from an open viewer. */
    fun viewerRemoved(key: String) {
        val v = _viewer.value ?: return
        val idx = v.items.indexOfFirst { it.key == key }
        val n = v.items.filterNot { it.key == key }
        _viewer.value = if (n.isEmpty()) null else v.copy(items = n, startIndex = idx.coerceIn(0, n.size - 1))
    }

    // ---- share / repost / open --------------------------------------------
    private fun contentUriFor(m: Media, f: File?): Uri =
        if (f != null) FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.files", f) else m.uri

    fun share(items: List<Media>, repostTo: String? = null) {
        if (items.isEmpty()) return
        clearSelection()
        viewModelScope.launch {
            val uris = ArrayList<Uri>()
            for (m in items) {
                val f = if (m.status != null) runCatching { repo.shareableCopy(m.status) }.getOrNull() else null
                uris += contentUriFor(m, f)
            }
            val mime = when {
                items.all { it.kind == MediaKind.IMAGE } -> "image/*"
                items.all { it.kind == MediaKind.VIDEO } -> "video/*"
                else -> "*/*"
            }
            val send = if (uris.size == 1) Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
            else Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            send.type = mime
            send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (repostTo != null) {
                val pkg = installedWhatsApp(repostTo)
                if (pkg == null) { snack(str(R.string.snack_no_whatsapp)); return@launch }
                send.setPackage(pkg)
                repo.diagnostics.count(Diagnostics.REPOSTED)
                _events.emit(UiEvent.Launch(send))
            } else {
                repo.diagnostics.count(Diagnostics.SHARED)
                _events.emit(UiEvent.Launch(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
            }
        }
    }

    /** Picks WhatsApp for reposting: the item's own app if installed, else any installed WhatsApp. */
    private fun installedWhatsApp(preferred: String): String? {
        val candidates = listOf(preferred, "com.whatsapp", "com.whatsapp.w4b").distinct()
        return candidates.firstOrNull { isInstalled(it) }
    }

    fun isInstalled(pkg: String): Boolean = try {
        getApplication<Application>().packageManager.getPackageInfo(pkg, 0); true
    } catch (_: PackageManager.NameNotFoundException) { false }

    fun openWhatsApp() {
        val pm = getApplication<Application>().packageManager
        val pref = settings.value.sourceFilter?.pkg
        val pkg = listOfNotNull(pref, "com.whatsapp", "com.whatsapp.w4b").firstOrNull { isInstalled(it) }
        val intent = pkg?.let { pm.getLaunchIntentForPackage(it) }
        if (intent == null) snack(str(R.string.snack_no_whatsapp)) else _events.tryEmit(UiEvent.Launch(intent))
    }

    // ---- settings -----------------------------------------------------------
    fun updateSettings(block: (Settings) -> Settings) {
        prefs.update(block)
        val s = prefs.settings.value
        Background.sync(getApplication(), s.alerts, s.autoSave)
    }

    fun setSourceFilter(s: Source?) { prefs.update { it.copy(sourceFilter = s) } }

    fun setFolder(source: Source, ref: String?) { prefs.setFolder(source, ref); refresh() }

    fun exportLog() {
        viewModelScope.launch {
            val f = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repo.diagnostics.exportFile() }
            val uri = FileProvider.getUriForFile(getApplication(), "${getApplication<Application>().packageName}.files", f)
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            _events.emit(UiEvent.Launch(Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)))
        }
    }

    fun clearCrashes() { repo.diagnostics.clearCrashes(); snack(str(R.string.snack_log_cleared)); crashTick.value++ }
    val crashTick = kotlinx.coroutines.flow.MutableStateFlow(0)

    /** Ids waiting for Android's delete confirmation. */
    var pendingDeleteIds: List<Long> = emptyList()

    fun emit(e: UiEvent) { _events.tryEmit(e) }
    fun showSnack(text: String) = snack(text)

    val isLoading: Boolean get() = status.value.load == LoadState.LOADING
}
