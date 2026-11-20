package com.sushant.gallery.data

import android.content.Context
import com.sushant.gallery.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Settings. Applied instantly; each value is a StateFlow so screens update live. */
class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("gallery_prefs", Context.MODE_PRIVATE)

    private fun <T> flow(v: T) = MutableStateFlow(v)

    private val _theme = flow(runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM))
    val theme: StateFlow<ThemeMode> = _theme.asStateFlow()
    fun setTheme(v: ThemeMode) { sp.edit().putString("theme", v.name).apply(); _theme.value = v }

    private val _dynamic = flow(sp.getBoolean("dynamic", false))
    val dynamicColor: StateFlow<Boolean> = _dynamic.asStateFlow()
    fun setDynamic(v: Boolean) { sp.edit().putBoolean("dynamic", v).apply(); _dynamic.value = v }

    private val _cols = flow(sp.getInt("cols", 4).coerceIn(MIN_COLS, MAX_COLS))
    val gridColumns: StateFlow<Int> = _cols.asStateFlow()
    fun setColumns(v: Int) { val c = v.coerceIn(MIN_COLS, MAX_COLS); sp.edit().putInt("cols", c).apply(); _cols.value = c }

    private val _hidden = flow(sp.getBoolean("showHidden", false))
    val showHiddenFiles: StateFlow<Boolean> = _hidden.asStateFlow()
    fun setShowHidden(v: Boolean) { sp.edit().putBoolean("showHidden", v).apply(); _hidden.value = v }

    private val _strip = flow(sp.getBoolean("stripLocation", true))
    val stripLocation: StateFlow<Boolean> = _strip.asStateFlow()
    fun setStripLocation(v: Boolean) { sp.edit().putBoolean("stripLocation", v).apply(); _strip.value = v }

    private val _fp = flow(sp.getBoolean("fingerprint", true))
    val fingerprint: StateFlow<Boolean> = _fp.asStateFlow()
    fun setFingerprint(v: Boolean) { sp.edit().putBoolean("fingerprint", v).apply(); _fp.value = v }

    private val _sort = flow(runCatching { PhotoSort.valueOf(sp.getString("sort", "DATE_TAKEN")!!) }.getOrDefault(PhotoSort.DATE_TAKEN))
    val photoSort: StateFlow<PhotoSort> = _sort.asStateFlow()
    private val _desc = flow(sp.getBoolean("desc", true))
    val sortDesc: StateFlow<Boolean> = _desc.asStateFlow()
    fun setPhotoSort(s: PhotoSort, desc: Boolean) {
        sp.edit().putString("sort", s.name).putBoolean("desc", desc).apply(); _sort.value = s; _desc.value = desc
    }

    private val _albumSort = flow(runCatching { AlbumSort.valueOf(sp.getString("albumSort", "NAME")!!) }.getOrDefault(AlbumSort.NAME))
    val albumSort: StateFlow<AlbumSort> = _albumSort.asStateFlow()
    fun setAlbumSort(s: AlbumSort) { sp.edit().putString("albumSort", s.name).apply(); _albumSort.value = s }

    private val _recent = flow(sp.getString("recent", "")!!.split('\n').filter { it.isNotBlank() })
    val recentSearches: StateFlow<List<String>> = _recent.asStateFlow()
    fun addRecent(q: String) {
        val t = q.trim(); if (t.isEmpty()) return
        val l = (listOf(t) + _recent.value.filterNot { it.equals(t, true) }).take(8)
        sp.edit().putString("recent", l.joinToString("\n")).apply(); _recent.value = l
    }
    fun removeRecent(q: String) {
        val l = _recent.value - q
        sp.edit().putString("recent", l.joinToString("\n")).apply(); _recent.value = l
    }

    /** One-time hints: shown the first 3 times only. */
    fun hintCount(key: String): Int = sp.getInt("hint_$key", 0)
    fun bumpHint(key: String) { sp.edit().putInt("hint_$key", hintCount(key) + 1).apply() }

    var onboarded: Boolean
        get() = sp.getBoolean("onboarded", false)
        set(v) { sp.edit().putBoolean("onboarded", v).apply() }

    companion object {
        const val MIN_COLS = 2
        const val MAX_COLS = 6
        const val BIN_DAYS = 30
    }
}
