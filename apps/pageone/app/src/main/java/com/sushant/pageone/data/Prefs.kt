package com.sushant.pageone.data

import android.content.Context
import com.sushant.pageone.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SortKey { NAME, DATE, SIZE }
enum class PageMode { NORMAL, NIGHT, SEPIA }

data class Settings(
    val setupDone: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val wallpaperColors: Boolean = true,
    val pureBlack: Boolean = false,
    val rowThumb: Boolean = true,
    val rowPages: Boolean = true,
    val rowSizeDate: Boolean = true,
    val rowProgress: Boolean = true,
    val pageByPage: Boolean = false,
    val keepScreenOn: Boolean = true,
    /** Wide screens only: two pages side by side. Off = one page per screen everywhere. */
    val twoPages: Boolean = false,
    val volumeKeys: Boolean = false,
    val cropMargins: Boolean = false,
    val pageMode: PageMode = PageMode.NORMAL,
    val appLock: Boolean = false,
    val folderSort: SortKey = SortKey.NAME,
    val folderSortAsc: Boolean = true,
    val pdfSort: SortKey = SortKey.DATE,
    val pdfSortAsc: Boolean = false,
    val lastScan: Long = 0,
)

/** App settings. Changes apply instantly (no Save button). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("pageone_prefs", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(read())
    val state: StateFlow<Settings> = _state.asStateFlow()

    private fun read() = Settings(
        setupDone = sp.getBoolean("setupDone", false),
        themeMode = runCatching { ThemeMode.valueOf(sp.getString("themeMode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        wallpaperColors = sp.getBoolean("wallpaperColors", true),
        pureBlack = sp.getBoolean("pureBlack", false),
        rowThumb = sp.getBoolean("rowThumb", true),
        rowPages = sp.getBoolean("rowPages", true),
        rowSizeDate = sp.getBoolean("rowSizeDate", true),
        rowProgress = sp.getBoolean("rowProgress", true),
        pageByPage = sp.getBoolean("pageByPage", false),
        keepScreenOn = sp.getBoolean("keepScreenOn", true),
        twoPages = sp.getBoolean("twoPages", false),
        volumeKeys = sp.getBoolean("volumeKeys", false),
        cropMargins = sp.getBoolean("cropMargins", false),
        pageMode = runCatching { PageMode.valueOf(sp.getString("pageMode", "NORMAL")!!) }.getOrDefault(PageMode.NORMAL),
        appLock = sp.getBoolean("appLock", false),
        folderSort = runCatching { SortKey.valueOf(sp.getString("folderSort", "NAME")!!) }.getOrDefault(SortKey.NAME),
        folderSortAsc = sp.getBoolean("folderSortAsc", true),
        pdfSort = runCatching { SortKey.valueOf(sp.getString("pdfSort", "DATE")!!) }.getOrDefault(SortKey.DATE),
        pdfSortAsc = sp.getBoolean("pdfSortAsc", false),
        lastScan = sp.getLong("lastScan", 0),
    )

    fun update(block: (Settings) -> Settings) {
        val n = block(_state.value)
        sp.edit()
            .putBoolean("setupDone", n.setupDone)
            .putString("themeMode", n.themeMode.name)
            .putBoolean("wallpaperColors", n.wallpaperColors)
            .putBoolean("pureBlack", n.pureBlack)
            .putBoolean("rowThumb", n.rowThumb)
            .putBoolean("rowPages", n.rowPages)
            .putBoolean("rowSizeDate", n.rowSizeDate)
            .putBoolean("rowProgress", n.rowProgress)
            .putBoolean("pageByPage", n.pageByPage)
            .putBoolean("keepScreenOn", n.keepScreenOn)
            .putBoolean("twoPages", n.twoPages)
            .putBoolean("volumeKeys", n.volumeKeys)
            .putBoolean("cropMargins", n.cropMargins)
            .putString("pageMode", n.pageMode.name)
            .putBoolean("appLock", n.appLock)
            .putString("folderSort", n.folderSort.name)
            .putBoolean("folderSortAsc", n.folderSortAsc)
            .putString("pdfSort", n.pdfSort.name)
            .putBoolean("pdfSortAsc", n.pdfSortAsc)
            .putLong("lastScan", n.lastScan)
            .apply()
        _state.value = n
    }
}

/** Device-only counters and flags. Excluded from backup and device transfer. */
class LocalPrefs(context: Context) {
    private val sp = context.getSharedPreferences("pageone_local", Context.MODE_PRIVATE)
    fun count(key: String) { sp.edit().putInt("count_$key", sp.getInt("count_$key", 0) + 1).apply() }
    fun counts(): Map<String, Int> = sp.all.filterKeys { it.startsWith("count_") }
        .mapKeys { it.key.removePrefix("count_") }.mapValues { (it.value as? Int) ?: 0 }
}
