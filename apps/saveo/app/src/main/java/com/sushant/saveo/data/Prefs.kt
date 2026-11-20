package com.sushant.saveo.data

import android.content.Context
import android.content.SharedPreferences
import com.sushant.saveo.ui.theme.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class Settings(
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val autoSave: Boolean = false,
    val alerts: Boolean = false,
    val appLock: Boolean = false,
    val sortNewest: Boolean = true,
    /** null = all connected sources */
    val sourceFilter: Source? = null,
    /** false (default) = new saves stay hidden inside Saveo; true = they go straight to the Gallery. */
    val newSavesVisible: Boolean = false,
)

/**
 * Two files: "saveo_settings" (backed up) and "saveo_device" (folder access + one-time flags,
 * excluded from backup because folder permissions never move to another phone).
 */
class Prefs(ctx: Context) {
    private val sp: SharedPreferences = ctx.getSharedPreferences("saveo_settings", Context.MODE_PRIVATE)
    private val dev: SharedPreferences = ctx.getSharedPreferences("saveo_device", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _folders = MutableStateFlow(readFolders())
    /** Source -> stored folder reference ("treeUri" or "treeUri\ndocId"). */
    val folders: StateFlow<Map<Source, String>> = _folders.asStateFlow()

    private fun read() = Settings(
        onboarded = sp.getBoolean("onboarded", false),
        theme = runCatching { ThemeMode.valueOf(sp.getString("theme", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        dynamicColor = sp.getBoolean("dynamic", false),
        autoSave = sp.getBoolean("autoSave", false),
        alerts = sp.getBoolean("alerts", false),
        appLock = sp.getBoolean("appLock", false),
        sortNewest = sp.getBoolean("sortNewest", true),
        sourceFilter = sp.getString("source", null)?.let { s -> Source.entries.firstOrNull { it.name == s } },
        newSavesVisible = sp.getBoolean("newSavesVisible", false),
    )

    fun update(block: (Settings) -> Settings) {
        val n = block(_settings.value)
        sp.edit()
            .putBoolean("onboarded", n.onboarded)
            .putString("theme", n.theme.name)
            .putBoolean("dynamic", n.dynamicColor)
            .putBoolean("autoSave", n.autoSave)
            .putBoolean("alerts", n.alerts)
            .putBoolean("appLock", n.appLock)
            .putBoolean("sortNewest", n.sortNewest)
            .putString("source", n.sourceFilter?.name)
            .putBoolean("newSavesVisible", n.newSavesVisible)
            .apply()
        _settings.value = n
    }

    private fun readFolders(): Map<Source, String> =
        Source.entries.mapNotNull { s -> dev.getString("folder_${s.name}", null)?.let { s to it } }.toMap()

    fun setFolder(source: Source, ref: String?) {
        dev.edit().apply { if (ref == null) remove("folder_${source.name}") else putString("folder_${source.name}", ref) }.apply()
        _folders.value = readFolders()
    }

    /** Test hook: when set, statuses are read from <dir>/<SOURCE> folders instead of WhatsApp. */
    var testDir: String?
        get() = dev.getString("testDir", null)
        set(v) { dev.edit().putString("testDir", v).apply(); _folders.value = readFolders() }

    /** Test helper: forget everything (settings, folders, flags). */
    @android.annotation.SuppressLint("ApplySharedPref")
    fun resetAll() {
        sp.edit().clear().commit(); dev.edit().clear().commit()
        _settings.value = read(); _folders.value = readFolders()
    }

    var notifAsked: Boolean
        get() = dev.getBoolean("notifAsked", false)
        set(v) { dev.edit().putBoolean("notifAsked", v).apply() }

    var storageAsked: Boolean
        get() = dev.getBoolean("storageAsked", false)
        set(v) { dev.edit().putBoolean("storageAsked", v).apply() }

    var privateInfoShown: Boolean
        get() = dev.getBoolean("privateInfo", false)
        set(v) { dev.edit().putBoolean("privateInfo", v).apply() }

    var hintSelectShown: Boolean
        get() = dev.getBoolean("hintSelect", false)
        set(v) { dev.edit().putBoolean("hintSelect", v).apply() }
}
