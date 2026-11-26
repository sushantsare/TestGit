package com.sushant.tarang.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ThemeMode(val label: String) { SYSTEM("Follow phone"), LIGHT("Light"), DARK("Dark") }
enum class NotifButton(val label: String) { LIKE("Like"), SHUFFLE("Shuffle"), REPEAT("Repeat") }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val wallpaperColors: Boolean = false,
    val gapless: Boolean = true,
    val crossfadeSec: Int = 0,
    val resumeOnConnect: Boolean = false,
    val excludedFolders: Set<String> = emptySet(),
    val foldersCustomized: Boolean = false,
    val skipShortSec: Int = 30,
    val defaultSort: SongSort = SongSort.NAME,
    val notifButtons: List<NotifButton> = listOf(NotifButton.LIKE, NotifButton.SHUFFLE),
    val lockCover: Boolean = true,
    val speed: Float = 1f,
    val keepPitch: Boolean = true,
    val eqEnabled: Boolean = false,
    val eqPreset: String = "Flat",
    val eqBands: List<Float> = List(10) { 0f },
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val myPreset: List<Float>? = null,
    val lyricsTree: String? = null,
    val haptics: Boolean = true,
    val persistentShuffle: Boolean = false,
    val carouselPeek: Boolean = false,
    val alwaysSaveLyrics: Boolean = false,
)

/** Settings live in SharedPreferences and are exposed as one StateFlow; every change applies at once. */
class Prefs(ctx: Context) {
    private val p: SharedPreferences = ctx.getSharedPreferences("settings", Context.MODE_PRIVATE)
    /** "Already asked" flags: kept apart and excluded from backup so a restored phone asks normally. */
    val ask: SharedPreferences = ctx.getSharedPreferences("ask_state", Context.MODE_PRIVATE)
    private val _s = MutableStateFlow(read())
    val settings: StateFlow<Settings> = _s.asStateFlow()
    val now: Settings get() = _s.value

    private fun floats(s: String?): List<Float>? = s?.split(",")?.mapNotNull { it.toFloatOrNull() }?.takeIf { it.size == 10 }

    private fun read() = Settings(
        themeMode = runCatching { ThemeMode.valueOf(p.getString("themeMode", "SYSTEM")!!) }.getOrDefault(ThemeMode.SYSTEM),
        pureBlack = p.getBoolean("pureBlack", false),
        wallpaperColors = p.getBoolean("wallpaperColors", false),
        gapless = p.getBoolean("gapless", true),
        crossfadeSec = p.getInt("crossfadeSec", 0),
        resumeOnConnect = p.getBoolean("resumeOnConnect", false),
        excludedFolders = p.getStringSet("excludedFolders", emptySet())!!.toSet(),
        foldersCustomized = p.getBoolean("foldersCustomized", false),
        skipShortSec = p.getInt("skipShortSec", 30),
        defaultSort = runCatching { SongSort.valueOf(p.getString("defaultSort", "NAME")!!) }.getOrDefault(SongSort.NAME),
        notifButtons = p.getString("notifButtons", "LIKE,SHUFFLE")!!.split(",").mapNotNull { runCatching { NotifButton.valueOf(it) }.getOrNull() },
        lockCover = p.getBoolean("lockCover", true),
        speed = p.getFloat("speed", 1f),
        keepPitch = p.getBoolean("keepPitch", true),
        eqEnabled = p.getBoolean("eqEnabled", false),
        eqPreset = p.getString("eqPreset", "Flat")!!,
        eqBands = floats(p.getString("eqBands", null)) ?: List(10) { 0f },
        bassBoost = p.getInt("bassBoost", 0),
        virtualizer = p.getInt("virtualizer", 0),
        myPreset = floats(p.getString("myPreset", null)),
        lyricsTree = p.getString("lyricsTree", null),
        haptics = p.getBoolean("haptics", true),
        persistentShuffle = p.getBoolean("persistentShuffle", false),
        carouselPeek = p.getBoolean("carouselPeek", false),
        alwaysSaveLyrics = p.getBoolean("alwaysSaveLyrics", false),
    )

    fun update(f: (Settings) -> Settings) {
        val n = f(_s.value)
        p.edit()
            .putString("themeMode", n.themeMode.name).putBoolean("pureBlack", n.pureBlack)
            .putBoolean("wallpaperColors", n.wallpaperColors).putBoolean("gapless", n.gapless)
            .putInt("crossfadeSec", n.crossfadeSec).putBoolean("resumeOnConnect", n.resumeOnConnect)
            .putStringSet("excludedFolders", n.excludedFolders).putBoolean("foldersCustomized", n.foldersCustomized)
            .putInt("skipShortSec", n.skipShortSec).putString("defaultSort", n.defaultSort.name)
            .putString("notifButtons", n.notifButtons.joinToString(",") { it.name })
            .putBoolean("lockCover", n.lockCover).putFloat("speed", n.speed).putBoolean("keepPitch", n.keepPitch)
            .putBoolean("eqEnabled", n.eqEnabled).putString("eqPreset", n.eqPreset)
            .putString("eqBands", n.eqBands.joinToString(",")).putInt("bassBoost", n.bassBoost).putInt("virtualizer", n.virtualizer)
            .putString("myPreset", n.myPreset?.joinToString(",")).putString("lyricsTree", n.lyricsTree)
            .putBoolean("haptics", n.haptics).putBoolean("persistentShuffle", n.persistentShuffle)
            .putBoolean("carouselPeek", n.carouselPeek).putBoolean("alwaysSaveLyrics", n.alwaysSaveLyrics)
            .apply()
        _s.value = n
    }

    // ---- small app state (not settings)
    var activeQueue: Int
        get() = p.getInt("activeQueue", 1)
        set(v) { p.edit().putInt("activeQueue", v).apply() }
    var lastScanAt: Long
        get() = p.getLong("lastScanAt", 0)
        set(v) { p.edit().putLong("lastScanAt", v).apply() }
    var lastScanMs: Long
        get() = p.getLong("lastScanMs", 0)
        set(v) { p.edit().putLong("lastScanMs", v).apply() }
    var welcomeDone: Boolean
        get() = p.getBoolean("welcomeDone", false)
        set(v) { p.edit().putBoolean("welcomeDone", v).apply() }
    /** Shuffle is a real reordering of the one queue; the order before shuffling is kept here (song ids). */
    var shuffleOn: Boolean
        get() = p.getBoolean("shuffleOn", false)
        set(v) { p.edit().putBoolean("shuffleOn", v).apply() }
    var orderBeforeShuffle: List<Long>
        get() = p.getString("orderBeforeShuffle", "")!!.split(",").mapNotNull { it.toLongOrNull() }
        set(v) { p.edit().putString("orderBeforeShuffle", v.joinToString(",")).apply() }
    var repeatMode: Int
        get() = p.getInt("repeatMode", 0)
        set(v) { p.edit().putInt("repeatMode", v).apply() }
    /** The first-launch setup guide was finished. */
    var setupDone: Boolean
        get() = p.getBoolean("setupDone", false)
        set(v) { p.edit().putBoolean("setupDone", v).apply() }
    var recentSearches: List<String>
        get() = p.getString("recentSearches", "")!!.split("\n").filter { it.isNotBlank() }
        set(v) { p.edit().putString("recentSearches", v.take(8).joinToString("\n")).apply() }

    companion object {
        /** Folders hidden until the user changes the selection: voice notes, recordings, system sounds. */
        private val AUTO_HIDE = listOf("whatsapp", "recording", "recorder", "call", "notification", "ringtone", "alarm", "voice")
        fun hiddenByDefault(folderPath: String) = AUTO_HIDE.any { folderPath.lowercase().contains(it) }

        val PRESETS: Map<String, List<Float>> = linkedMapOf(
            "Flat" to List(10) { 0f },
            "Bass" to listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f),
            "Vocal" to listOf(-2f, -1f, 0f, 2f, 4f, 4f, 3f, 1f, 0f, -1f),
            "Bollywood" to listOf(3f, 2f, 1f, 0f, 1f, 2f, 3f, 3f, 2f, 1f),
            "Rock" to listOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 4f, 5f, 5f),
            "Classical" to listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f),
        )
        val BANDS_HZ = listOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
    }
}
