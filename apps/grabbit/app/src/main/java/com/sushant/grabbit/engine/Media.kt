package com.sushant.grabbit.engine

import android.content.Context
import android.util.Log
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Sites
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

data class MediaInfo(
    val url: String,
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val duration: Long,
    val site: String,
    val isPlaylist: Boolean,
    val count: Int,
    /** Available resolutions (shorter side, e.g. 1080, 720) */
    val heights: List<Int>,
    /** Approx. bytes per resolution incl. audio */
    val sizes: Map<Int, Long>,
    val hasVideo: Boolean,
    /** Audio streams the site offers (best first): bitrate in kbps and source size in bytes */
    val audios: List<AudioOpt> = emptyList(),
    /** Subtitle languages: uploaded ones first, then auto-generated / auto-translated */
    val subtitles: List<SubLang> = emptyList(),
    /** Number of chapters in the video (0 = none) */
    val chapters: Int = 0,
    /** Photo post (carousel, gallery, tweet photos): every item, photos and videos. Empty for normal videos. */
    val gallery: List<GalleryItem> = emptyList(),
    /** Playlist: the videos loaded so far (100 at a time). */
    val entries: List<PlEntry> = emptyList(),
    /** Playlist id from the site, used to remember which videos were already downloaded. */
    val playlistId: String = "",
    /** True when the playlist has more videos than loaded so far. */
    val hasMore: Boolean = false,
)

/** One video in a playlist. [index] is its number in the playlist (1-based). */
data class PlEntry(
    val index: Int, val id: String, val url: String, val title: String,
    val duration: Long, val thumb: String, val timestamp: Long = 0, val available: Boolean = true,
)

/** Reads playlist entries from yt-dlp's flat-playlist JSON (pure, unit-tested). */
object PlaylistParser {
    const val PAGE = 100

    private fun JSONObject.s(k: String): String = if (isNull(k)) "" else optString(k, "")

    fun entries(j: JSONObject, offset: Int, site: String): List<PlEntry> {
        val arr = j.optJSONArray("entries") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val e = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = e.s("id")
            var url = e.s("url").ifBlank { e.s("webpage_url") }
            if (url.isNotBlank() && !url.startsWith("http")) url = ""
            if (url.isBlank() && id.isNotBlank() && site == "YouTube") url = "https://www.youtube.com/watch?v=$id"
            var thumb = e.s("thumbnail")
            if (thumb.isBlank()) e.optJSONArray("thumbnails")?.let { th ->
                // Smallest thumbnail that's still clear on a list row
                val list = (0 until th.length()).mapNotNull { th.optJSONObject(it) }
                thumb = (list.firstOrNull { it.optInt("width", 0) >= 240 } ?: list.lastOrNull())?.s("url").orEmpty()
            }
            val title = e.s("title")
            val avail = e.s("availability")
            val gone = url.isBlank() || title in setOf("[Private video]", "[Deleted video]", "[Unavailable video]") ||
                avail in setOf("private", "needs_auth", "subscriber_only", "premium_only")
            PlEntry(
                index = e.optInt("playlist_index", 0).takeIf { it > 0 } ?: (offset + i + 1),
                id = id, url = url, title = title.ifBlank { id.ifBlank { "Video ${offset + i + 1}" } },
                duration = e.optDouble("duration", 0.0).let { if (it.isNaN()) 0L else it.toLong() },
                thumb = thumb,
                timestamp = e.optLong("timestamp", 0).takeIf { it > 0 } ?: e.s("upload_date").toLongOrNull() ?: 0,
                available = !gone,
            )
        }
    }

    /** Total videos in the playlist, or -1 if the site didn't say. */
    fun total(j: JSONObject): Int = j.optInt("playlist_count", -1)
}

data class SubLang(val code: String, val name: String, val auto: Boolean)

object SubtitleOptions {
    /** Auto-translated captions come in ~100 languages; offer the useful few. */
    private val AUTO_KEEP = setOf("en", "hi", "bn", "ta", "te", "mr", "ur", "es", "fr", "de", "ar", "ja", "ko", "zh-Hans")

    fun name(code: String): String {
        val base = code.substringBefore('-')
        val n = java.util.Locale.forLanguageTag(code).getDisplayName(java.util.Locale.ENGLISH)
            .ifBlank { java.util.Locale(base).getDisplayLanguage(java.util.Locale.ENGLISH) }
        return n.ifBlank { code }
    }

    /** [manual] and [auto] are the language codes yt-dlp reports. [original] = the video's spoken language. */
    fun build(manual: List<String>, auto: List<String>, original: String?, deviceLang: String): List<SubLang> {
        val out = LinkedHashMap<String, SubLang>()
        manual.filter { it != "live_chat" }.sortedBy { name(it) }.forEach { out[it] = SubLang(it, name(it), false) }
        val keep = AUTO_KEEP + deviceLang
        val autoOrig = original?.let { o -> auto.firstOrNull { it == "$o-orig" } ?: auto.firstOrNull { it == o } }
        val autos = buildList {
            if (autoOrig != null) add(autoOrig)
            addAll(auto.filter { it in keep && it != autoOrig })
        }
        autos.forEach { code ->
            if (out.keys.none { it.substringBefore('-') == code.substringBefore('-') && !out[it]!!.auto }) {
                out.putIfAbsent("auto:$code", SubLang(code, name(code.removeSuffix("-orig")), true))
            }
        }
        return out.values.toList()
    }
}

data class AudioOpt(val kbps: Int, val size: Long)

/** "audio" = best audio; "a128" = MP3 at about 128 kbps. */
fun isAudioQuality(q: String) = q == "audio" || (q.length > 1 && q[0] == 'a' && q.drop(1).all { it.isDigit() })
fun audioKbps(q: String): Int? = if (q.startsWith("a")) q.drop(1).toIntOrNull() else null

/** Groups audio-only formats into distinct bitrates (rounded to common steps), largest size per step. */
object AudioOptions {
    private val STEPS = listOf(32, 48, 64, 96, 128, 160, 192, 256, 320)
    fun build(raw: List<Pair<Double, Long>>): List<AudioOpt> {
        val byStep = sortedMapOf<Int, Long>(reverseOrder())
        for ((abr, size) in raw) {
            if (abr <= 0) continue
            val step = STEPS.minByOrNull { kotlin.math.abs(it - abr) } ?: continue
            byStep[step] = maxOf(byStep[step] ?: 0L, size)
        }
        return byStep.entries.take(5).map { AudioOpt(it.key, it.value) }
    }
}

object Media {
    @Volatile private var currentPid: String? = null

    fun cancelFetch() {
        currentPid?.let { runCatching { YoutubeDL.getInstance().destroyProcessById(it) } }
        currentPid = null
    }

    /** YouTube playlists the engine could only read through YouTube's API (skip the web page). */
    private val apiOnly = java.util.Collections.synchronizedSet(HashSet<String>())

    /** How the last playlist was read: "engine", "engine-api" or "grabbit-web" / "grabbit-music" / "grabbit-watch". */
    @Volatile var lastPlaylistVia: String = ""

    /** Test hook: pretend the engine failed, to check the fallbacks (0 = off, 1 = engine API, 2 = own reader only). */
    @Volatile var testFallback: Int = 0

    private fun runJson(ctx: Context, url: String, pid: String, extra: List<Pair<String, String?>>, login: Boolean = false): JSONObject {
        val req = YoutubeDLRequest(url).apply {
            addOption("-J"); addOption("--flat-playlist"); addOption("--no-warnings")
            extra.forEach { (k, v) -> if (v == null) addOption(k) else addOption(k, v) }
        }
        Cookies.apply(ctx, req, url, login)
        val out = YoutubeDL.getInstance().execute(req, processId = pid, callback = null).out.trim()
        return JSONObject(out.lineSequence().lastOrNull { it.trimStart().startsWith("{") } ?: out)
    }

    suspend fun fetchInfo(ctx: Context, url: String): MediaInfo = withContext(Dispatchers.IO) {
        if (InstaStory.isStory(url)) {
            val st = InstaStory.resolve(url)
            return@withContext MediaInfo(
                url, "Story by @${st.user}", "@${st.user}", st.thumb, st.duration,
                "Instagram", false, 1, emptyList(), emptyMap(), true,
            )
        }
        // Photo posts (and direct image links); null means "no photos here", so videos carry on as before
        if (Images.mightHavePhotos(url)) runCatching { Images.resolve(url) }.getOrNull()?.let { return@withContext it }
        if (Threads.isThreads(url)) {
            val post = Threads.resolve(url)
            if (post.videos.isEmpty() && post.images.isNotEmpty()) return@withContext Images.info(
                url, ThreadsParser.title(post), post.user.let { if (it.isEmpty()) "" else "@$it" }, "Threads",
                post.images.map { GalleryItem(it, false, it) },
            )
            return@withContext MediaInfo(
                url, ThreadsParser.title(post), post.user.let { if (it.isEmpty()) "" else "@$it" }, post.thumb, 0,
                "Threads", false, 1, emptyList(), emptyMap(), true,
            )
        }
        if (!Engine.awaitReady()) throw IllegalStateException(Engine.failMessage())
        val pid = "info-${System.nanoTime()}"
        cancelFetch()
        currentPid = pid
        // Playlists: first 100 videos; more load as the user scrolls
        val first = listOf("--playlist-items" to "1:${PlaylistParser.PAGE}")
        try {
            if (testFallback > 0) throw IllegalStateException("ERROR: [youtube:tab] test: Unable to recognize tab page")
            val info = try {
                parse(url, runJson(ctx, url, pid, first))
            } catch (e: Throwable) {
                // YouTube said "log in": try again with the saved YouTube login
                if (currentPid != pid || e is kotlinx.coroutines.CancellationException || !Cookies.youtubeLoggedIn() ||
                    Sites.siteName(url) != "YouTube" || !Errors.needsLogin(e.message)) throw e
                Log.w("Grabbit", "YouTube wants a login, retrying with it: ${e.message?.take(200)}")
                parse(url, runJson(ctx, url, pid, first, login = true))
            }
            if (info.isPlaylist) lastPlaylistVia = "engine"
            info
        } catch (e: Throwable) {
            val id = YtPlaylist.idFrom(url)
            // Only YouTube playlists get the fallbacks, and never after the user tapped cancel
            val listErr = "/playlist" in url || e.message.orEmpty().let { "tab" in it.lowercase() || "playlist" in it.lowercase() }
            if (id == null || !listErr || currentPid != pid || e is kotlinx.coroutines.CancellationException) throw e
            Log.w("Grabbit", "Engine couldn't open playlist $id: ${e.message?.take(200)}")
            youtubeFallback(ctx, url, id, pid, first) ?: throw e
        }
    }

    /** 1) the engine again, reading YouTube's API instead of the web page; 2) Grabbit's own playlist reader. */
    private fun youtubeFallback(ctx: Context, url: String, id: String, pid: String, first: List<Pair<String, String?>>): MediaInfo? {
        val plUrl = "https://www.youtube.com/playlist?list=$id"
        if (testFallback != 2) runCatching {
            val info = parse(url, runJson(ctx, plUrl, pid, first + ("--extractor-args" to "youtubetab:skip=webpage")))
            if (info.isPlaylist && info.entries.isNotEmpty()) {
                apiOnly += id; lastPlaylistVia = "engine-api"
                return info
            }
        }.onFailure { Log.w("Grabbit", "Engine API read failed $id: ${it.message?.take(200)}") }
        if (currentPid != pid) return null
        val r = runCatching { YtPlaylist.read(id) }.getOrElse { Log.w("Grabbit", "Own reader failed $id: ${it.message}"); return null }
        lastPlaylistVia = "grabbit-${r.via}"
        return MediaInfo(
            url, r.title.ifBlank { "YouTube playlist" }, r.owner, r.entries.firstOrNull()?.thumb.orEmpty(), 0, "YouTube",
            true, r.entries.size, listOf(1080, 720, 480, 360), emptyMap(), true,
            entries = r.entries, playlistId = id, hasMore = false,
        )
    }

    /** Next 100 videos of a playlist, starting at number [from]. */
    suspend fun playlistPage(ctx: Context, url: String, from: Int, site: String): List<PlEntry> = withContext(Dispatchers.IO) {
        if (!Engine.awaitReady()) throw IllegalStateException(Engine.failMessage())
        val id = YtPlaylist.idFrom(url)
        val api = id != null && id in apiOnly
        val target = if (api) "https://www.youtube.com/playlist?list=$id" else url
        val extra = buildList<Pair<String, String?>> {
            add("--playlist-items" to "$from:${from + PlaylistParser.PAGE - 1}")
            if (api) add("--extractor-args" to "youtubetab:skip=webpage")
        }
        val j = try { runJson(ctx, target, "page-${System.nanoTime()}", extra) } catch (e: Throwable) {
            if (!Cookies.youtubeLoggedIn() || !Errors.needsLogin(e.message)) throw e
            runJson(ctx, target, "page-${System.nanoTime()}", extra, login = true)
        }
        PlaylistParser.entries(j, from - 1, site)
    }

    private fun JSONObject.str(k: String): String = if (isNull(k)) "" else optString(k, "")

    private fun parse(url: String, j: JSONObject): MediaInfo {
        val site = Sites.siteName(j.str("webpage_url").ifBlank { url })
        val title = j.str("title").ifBlank { j.str("id").ifBlank { "Untitled" } }
        val uploader = j.str("uploader").ifBlank { j.str("channel").ifBlank { j.str("uploader_id") } }

        if (j.str("_type") == "playlist") {
            val entries = j.optJSONArray("entries")
            var thumb = j.str("thumbnail")
            if (thumb.isBlank()) {
                val e0 = entries?.optJSONObject(0)
                thumb = e0?.str("thumbnail").orEmpty()
                if (thumb.isBlank()) {
                    val th = e0?.optJSONArray("thumbnails")
                    if (th != null && th.length() > 0) thumb = th.optJSONObject(th.length() - 1)?.str("url").orEmpty()
                }
            }
            val list = PlaylistParser.entries(j, 0, site)
            val total = PlaylistParser.total(j)
            return MediaInfo(
                url, title, uploader, thumb, 0, site, true, if (total > 0) total else list.size,
                listOf(1080, 720, 480, 360), emptyMap(), true,
                entries = list, playlistId = j.str("id").ifBlank { url },
                hasMore = if (total > 0) list.size < total else list.size >= PlaylistParser.PAGE,
            )
        }

        val heights = sortedSetOf<Int>(reverseOrder())
        val sizes = HashMap<Int, Long>()
        var bestAudio = 0L
        var anyVideo = false
        val audioRaw = mutableListOf<Pair<Double, Long>>()
        val formats = j.optJSONArray("formats")
        if (formats != null) {
            for (i in 0 until formats.length()) {
                val f = formats.optJSONObject(i) ?: continue
                val v = f.str("vcodec")
                val a = f.str("acodec")
                val w = f.optInt("width", 0)
                val h = f.optInt("height", 0)
                val res = if (w > 0 && h > 0) minOf(w, h) else h
                val size = f.optLong("filesize", 0).takeIf { it > 0 } ?: f.optLong("filesize_approx", 0)
                val audioOnly = v == "none" || (v.isBlank() && res == 0 && a.isNotBlank() && a != "none") ||
                    f.str("ext") in setOf("m4a", "mp3", "opus", "ogg", "wav", "flac", "aac")
                if (!audioOnly) {
                    anyVideo = true
                    if (res > 0) {
                        heights += res
                        sizes[res] = maxOf(sizes[res] ?: 0L, size)
                    }
                } else if (a != "none") {
                    bestAudio = maxOf(bestAudio, size)
                    val abr = f.optDouble("abr", 0.0).takeIf { !it.isNaN() && it > 0 } ?: f.optDouble("tbr", 0.0)
                    if (!abr.isNaN()) audioRaw += abr to size
                }
            }
        }
        val top = j.optInt("height", 0)
        val hasVideo = anyVideo || heights.isNotEmpty() || top > 0 || formats == null || j.str("vcodec").let { it.isNotBlank() && it != "none" }
        val finalSizes = sizes.mapValues { (_, s) -> if (s > 0) s + bestAudio else 0L }
        return MediaInfo(
            url = url,
            title = title,
            uploader = uploader,
            thumbnail = j.str("thumbnail"),
            duration = j.optDouble("duration", 0.0).let { if (it.isNaN()) 0L else it.toLong() },
            site = site,
            isPlaylist = false,
            count = 1,
            heights = heights.filter { it >= 140 }.toList(),
            sizes = finalSizes,
            hasVideo = hasVideo,
            audios = AudioOptions.build(audioRaw),
            subtitles = SubtitleOptions.build(
                j.optJSONObject("subtitles")?.keys()?.asSequence()?.toList().orEmpty(),
                j.optJSONObject("automatic_captions")?.keys()?.asSequence()?.toList().orEmpty(),
                j.str("language").ifBlank { null },
                java.util.Locale.getDefault().language,
            ),
            chapters = j.optJSONArray("chapters")?.length() ?: 0,
        )
    }
}
