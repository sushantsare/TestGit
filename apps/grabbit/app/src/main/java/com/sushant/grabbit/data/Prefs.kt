package com.sushant.grabbit.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow

object Prefs {
    lateinit var p: SharedPreferences
        private set

    /** 0 = follow system, 1 = light, 2 = dark */
    val themeFlow = MutableStateFlow(0)

    fun init(ctx: Context) {
        p = ctx.getSharedPreferences("grabbit", Context.MODE_PRIVATE)
        themeFlow.value = p.getInt("theme", 0)
    }

    var theme: Int
        get() = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply(); themeFlow.value = v }

    /** "best", "h1080", "h720" … or "audio" */
    var defaultQuality: String
        get() = p.getString("quality", "best") ?: "best"
        set(v) = p.edit().putString("quality", v).apply()

    var embedSubs: Boolean
        get() = p.getBoolean("embed_subs", false)
        set(v) = p.edit().putBoolean("embed_subs", v).apply()

    var embedThumb: Boolean
        get() = p.getBoolean("embed_thumb", true)
        set(v) = p.edit().putBoolean("embed_thumb", v).apply()

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
