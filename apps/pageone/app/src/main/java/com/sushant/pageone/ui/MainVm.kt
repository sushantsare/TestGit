package com.sushant.pageone.ui

import android.app.Application
import android.net.Uri
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sushant.pageone.PageOneApp
import com.sushant.pageone.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class MainVm(app: Application) : AndroidViewModel(app) {
    val a = app as PageOneApp
    val lib: Library = a.library
    val settings: StateFlow<Settings> = a.prefs.state
    val snackbar = SnackbarHostState()

    /** App lock: true while the lock screen should cover the app. */
    val locked = MutableStateFlow(a.prefs.state.value.appLock)
    val access = MutableStateFlow(lib.hasAllFilesAccess())

    init {
        if (access.value) lib.scan()
    }

    fun update(block: (Settings) -> Settings) = a.prefs.update(block)

    fun refreshAccess() {
        val now = lib.hasAllFilesAccess()
        val was = access.value
        access.value = now
        if (now && (!was || lib.pdfs.value.isEmpty())) lib.scan()
    }

    fun message(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
        viewModelScope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val r = snackbar.showSnackbar(text, action, withDismissAction = false, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }

    fun refOf(p: PdfEntity) = DocRef.ofFile(p.path)

    fun progressOf(fp: String, pages: Int): Float? {
        if (fp.isEmpty() || pages <= 0) return null
        val pos = lib.positions.value[fp] ?: return null
        return ((pos.page + 1f) / pages).coerceIn(0f, 1f)
    }

    fun removeRecent(r: RecentEntity) = viewModelScope.launch {
        lib.removeRecent(r.fingerprint)
        message("Removed from Recent", "Undo") { viewModelScope.launch { lib.restoreRecent(r) } }
    }

    fun clearRecents() = viewModelScope.launch { lib.clearRecents(); message("Recent cleared") }

    fun toggleStar(ref: DocRef, fp: String, pages: Int) = viewModelScope.launch {
        val wasStarred = lib.stars.value.any { it.fingerprint == fp }
        lib.toggleStar(ref, fp, pages)
        message(if (wasStarred) "Removed from Starred" else "Starred")
    }

    fun toggleStarPath(p: PdfEntity) = viewModelScope.launch {
        val fp = p.fingerprint.ifEmpty { lib.fingerprintOf(p.path) }
        toggleStar(refOf(p), fp, p.pages)
    }

    fun reorderStars(list: List<StarEntity>) = viewModelScope.launch { lib.reorderStars(list) }

    fun isStarred(fp: String) = fp.isNotEmpty() && lib.stars.value.any { it.fingerprint == fp }

    companion object {
        fun readerRoute(ref: DocRef): String =
            "reader?uri=${Uri.encode(ref.uri)}&path=${Uri.encode(ref.path ?: "")}&name=${Uri.encode(ref.name)}"
    }
}
