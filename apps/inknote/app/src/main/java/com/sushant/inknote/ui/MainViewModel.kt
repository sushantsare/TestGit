package com.sushant.inknote.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.sushant.inknote.data.FolderEntity
import com.sushant.inknote.data.Kind
import com.sushant.inknote.data.NoteItem
import com.sushant.inknote.data.SortBy
import com.sushant.inknote.work.IndexWorker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Home filter: all / pinned / favourites / locked / folder:<id> / tag:<name>. */
sealed interface Filter {
    data object All : Filter; data object Pinned : Filter; data object Favourites : Filter; data object Locked : Filter
    data class Folder(val id: Long) : Filter; data class Tag(val name: String) : Filter
    companion object {
        fun encode(f: Filter) = when (f) { All -> "all"; Pinned -> "pin"; Favourites -> "fav"; Locked -> "lock"; is Folder -> "f:${f.id}"; is Tag -> "t:${f.name}" }
        fun decode(s: String?): Filter = when {
            s == null || s == "all" -> All; s == "pin" -> Pinned; s == "fav" -> Favourites; s == "lock" -> Locked
            s.startsWith("f:") -> s.drop(2).toLongOrNull()?.let { Folder(it) } ?: All
            s.startsWith("t:") -> Tag(s.drop(2)); else -> All
        }
    }
}

data class HomeState(
    val loaded: Boolean = false,
    val all: List<NoteItem> = emptyList(),
    val shown: List<NoteItem> = emptyList(),
    val folders: List<FolderEntity> = emptyList(),
    val folderCounts: Map<Long, Int> = emptyMap(),
    val tags: List<String> = emptyList(),
    val filter: Filter = Filter.All,
    val trashCount: Int = 0,
)

class MainViewModel(app: Application, private val saved: SavedStateHandle) : AndroidViewModel(app) {
    private val inkApp = app.app()
    private val repo = inkApp.repo
    val settings = inkApp.settings.state

    private val _filter = MutableStateFlow(Filter.decode(saved["filter"]))
    val filter: StateFlow<Filter> = _filter.asStateFlow()

    private val _selected = MutableStateFlow<Set<Long>>(saved.get<LongArray>("sel")?.toSet() ?: emptySet())
    val selected: StateFlow<Set<Long>> = _selected.asStateFlow()

    /** Notes unlocked during this visit; cleared when the app goes to the background. */
    val unlocked = MutableStateFlow<Set<Long>>(emptySet())

    val home: StateFlow<HomeState> = combine(repo.noteItems, repo.folderList, repo.trash, _filter, settings) { items, folders, trash, f, st ->
        val sorted = when (st.sortBy) {
            SortBy.CREATED -> items.sortedByDescending { it.note.createdAt }
            SortBy.TITLE -> items.sortedBy { it.note.title.lowercase() }
            else -> items.sortedByDescending { it.note.updatedAt }
        }.sortedByDescending { it.note.pinned }
        val shown = when (f) {
            Filter.All -> sorted; Filter.Pinned -> sorted.filter { it.note.pinned }
            Filter.Favourites -> sorted.filter { it.note.favourite }; Filter.Locked -> sorted.filter { it.note.locked }
            is Filter.Folder -> sorted.filter { it.note.folderId == f.id }; is Filter.Tag -> sorted.filter { f.name in it.tags }
        }
        HomeState(true, sorted, shown, folders, items.groupingBy { it.note.folderId ?: -1L }.eachCount(),
            items.flatMap { it.tags }.distinct().sorted(), f, trash.size)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    val trash = repo.trash.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init { IndexWorker.schedule(app, 2) }

    fun setFilter(f: Filter) { _filter.value = f; saved["filter"] = Filter.encode(f); clearSelection() }
    fun toggleSelect(id: Long) { _selected.value = _selected.value.let { if (id in it) it - id else it + id }; saved["sel"] = _selected.value.toLongArray() }
    fun clearSelection() { _selected.value = emptySet(); saved["sel"] = LongArray(0) }
    fun setGrid(v: Boolean) = inkApp.settings.update { it.copy(gridView = v) }
    fun setSort(v: Int) = inkApp.settings.update { it.copy(sortBy = v) }

    fun pin(ids: Set<Long>) = viewModelScope.launch {
        val allPinned = ids.all { id -> home.value.all.firstOrNull { it.note.id == id }?.note?.pinned == true }
        repo.setPinned(ids, !allPinned); clearSelection()
    }
    fun trashNotes(ids: Set<Long>) = viewModelScope.launch { repo.moveToTrash(ids); clearSelection() }
    fun restore(ids: Set<Long>) = viewModelScope.launch { repo.restore(ids) }
    fun deleteForever(ids: Set<Long>) = viewModelScope.launch { repo.deleteForever(ids) }
    fun emptyTrash() = viewModelScope.launch { repo.deleteForever(trash.value.map { it.id }) }
    fun move(ids: Set<Long>, folder: Long?) = viewModelScope.launch { repo.moveToFolder(ids, folder); clearSelection() }
    fun lock(ids: Set<Long>, v: Boolean) = viewModelScope.launch { repo.setLocked(ids, v); clearSelection() }

    fun addFolder(name: String, color: Int) = viewModelScope.launch { repo.addFolder(name, color) }
    fun renameFolder(f: FolderEntity, name: String, color: Int) = viewModelScope.launch { repo.updateFolder(f.copy(name = name.trim(), color = color)) }
    fun deleteFolder(id: Long) = viewModelScope.launch { repo.deleteFolder(id); if (_filter.value == Filter.Folder(id)) setFilter(Filter.All) }

    private fun currentFolder(): Long? = (_filter.value as? Filter.Folder)?.id

    suspend fun newNote(kind: String, orientation: String, paper: String, pageType: String): Long {
        val count = home.value.all.size + 1
        val title = if (kind == Kind.TEXT) "Note $count" else "Note $count"
        return repo.createNote(title, kind, orientation, paper, pageType, currentFolder())
    }

    suspend fun importPdf(uri: Uri): Result<Long> = runCatching { repo.importPdf(uri, currentFolder()) }.also { IndexWorker.schedule(getApplication()) }
    suspend fun createTextNote(title: String, text: String) = repo.createTextNote(title, text, currentFolder())
    suspend fun createWithImages(uris: List<Uri>): Long {
        val id = repo.createNote("Images", Kind.INK, paper = com.sushant.inknote.data.Paper.PLAIN, folderId = currentFolder())
        ImageImport.addImages(getApplication(), repo, id, repo.pages(id).first().id, uris)
        return id
    }
}
