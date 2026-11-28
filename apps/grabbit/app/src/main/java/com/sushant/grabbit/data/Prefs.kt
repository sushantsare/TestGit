package com.sushant.grabbit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Prefs {
    lateinit var p: SharedPreferences
        private set

    /** 0 = follow system, 1 = light, 2 = dark */
    val themeFlow = MutableStateFlow(0)
    /** Pure black backgrounds in dark mode (OLED) */
    val amoledFlow = MutableStateFlow(false)
    /** Android 12+ wallpaper colours instead of the Grabbit palette */
    val dynamicFlow = MutableStateFlow(false)

    fun init(ctx: Context) {
        p = ctx.getSharedPreferences("grabbit", Context.MODE_PRIVATE)
        themeFlow.value = p.getInt("theme", 0)
        amoledFlow.value = p.getBoolean("amoled", false)
        dynamicFlow.value = p.getBoolean("dynamic", false)
    }

    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply(); themeFlow.value = v }

    /** Browser identity used during the last login; the engine reuses it so the session stays valid */
    var loginUserAgent: String
        get() = p.getString("login_ua", "") ?: ""
        set(v) = p.edit().putString("login_ua", v).apply()

    var amoled: Boolean
        get() = p.getBoolean("amoled", false)
        set(v) { p.edit().putBoolean("amoled", v).apply(); amoledFlow.value = v }

    var dynamic: Boolean
        get() = p.getBoolean("dynamic", false)
        set(v) { p.edit().putBoolean("dynamic", v).apply(); dynamicFlow.value = v }

    /** "best", "h1080", "h720" … or "audio" */
    var defaultQuality: String
        get() = p.getString("quality", "best") ?: "best"
        set(v) = p.edit().putString("quality", v).apply()

    /** Default subtitle language ("" = off). Older versions had an on/off switch for English. */
    var subLang: String
        get() = p.getString("sub_lang", null) ?: if (p.getBoolean("embed_subs", false)) "en" else ""
        set(v) = p.edit().putString("sub_lang", v).apply()

    /** "embed", "file" or "both" */
    /** Setup guide finished or skipped (shown only on a fresh install). */
    var onboarded: Boolean
        get() = p.getBoolean("onboarded", false)
        set(v) = p.edit().putBoolean("onboarded", v).apply()

    /** Photos: "img" = original file, "imgjpg" = smaller JPG. */
    var imageFormat: String
        get() = p.getString("image_format", "img") ?: "img"
        set(v) = p.edit().putString("image_format", v).apply()

    /** Folder (tree uri) the next download goes to; "" = Default (Movies / Music / Pictures). */
    var saveFolder: String
        get() = p.getString("save_folder", "") ?: ""
        set(v) = p.edit().putString("save_folder", v).apply()

    /** Up to 3 folders picked before, newest first (tree uris, one per line). */
    var recentFolders: List<String>
        get() = (p.getString("recent_folders", "") ?: "").lines().filter { it.isNotBlank() }
        set(v) = p.edit().putString("recent_folders", v.joinToString("\n")).apply()

    /** Start the next download in the folder used last. */
    var keepLastFolder: Boolean
        get() = p.getBoolean("keep_last_folder", true)
        set(v) = p.edit().putBoolean("keep_last_folder", v).apply()

    var subMode: String
        get() = p.getString("sub_mode", "embed") ?: "embed"
        set(v) = p.edit().putString("sub_mode", v).apply()

    /** 0 = most compatible, 1 = highest quality */
    var videoFormat: Int
        get() = p.getInt("video_format", 0)
        set(v) = p.edit().putInt("video_format", v).apply()

    /** "mp3", "m4a", "opus", "best" */
    var audioFormat: String
        get() = p.getString("audio_format", "mp3") ?: "mp3"
        set(v) = p.edit().putString("audio_format", v).apply()

    var fasterDownloads: Boolean
        get() = p.getBoolean("faster", true)
        set(v) = p.edit().putBoolean("faster", v).apply()

    /** SponsorBlock categories removed from YouTube videos (empty = off) */
    var sponsorCats: Set<String>
        get() = p.getStringSet("sponsor_cats", emptySet())?.toSet() ?: emptySet()
        set(v) = p.edit().putStringSet("sponsor_cats", v).apply()

    var embedThumb: Boolean
        get() = p.getBoolean("embed_thumb", true)
        set(v) = p.edit().putBoolean("embed_thumb", v).apply()

    /** Download engine updates: "nightly" (YouTube fixes within a day or two, default) or "stable". */
    var engineChannel: String
        get() = p.getString("engine_channel", "nightly") ?: "nightly"
        set(v) = p.edit().putString("engine_channel", v).apply()

    /** Channel the installed engine came from; a different one means update now. */
    var engineChannelInstalled: String
        get() = p.getString("engine_channel_installed", "stable") ?: "stable"
        set(v) = p.edit().putString("engine_channel_installed", v).apply()

    var autoUpdate: Boolean
        get() = p.getBoolean("auto_update", true)
        set(v) = p.edit().putBoolean("auto_update", v).apply()

    var lastUpdate: Long
        get() = p.getLong("last_update", 0L)
        set(v) = p.edit().putLong("last_update", v).apply()

    /** Finished/failed download rows the user dismissed from the list */
    var hidden: Set<String>
        get() = p.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) = p.edit().putStringSet("hidden", v).apply()

    /** Notification permission is asked once, at the first download */
    var notifAsked: Boolean
        get() = p.getBoolean("notif_asked", false)
        set(v) = p.edit().putBoolean("notif_asked", v).apply()
}
