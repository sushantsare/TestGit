package com.sushant.grabbit.engine

import android.content.Context
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
)

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
    }

    suspend fun fetchInfo(ctx: Context, url: String): MediaInfo = withContext(Dispatchers.IO) {
        if (InstaStory.isStory(url)) {
            val st = InstaStory.resolve(url)
            return@withContext MediaInfo(
                url, "Story by @${st.user}", "@${st.user}", st.thumb, st.duration,
                "Instagram", false, 1, emptyList(), emptyMap(), true,
            )
        }
        if (Threads.isThreads(url)) {
            val post = Threads.resolve(url)
            return@withContext MediaInfo(
                url, ThreadsParser.title(post), post.user.let { if (it.isEmpty()) "" else "@$it" }, post.thumb, 0,
                "Threads", false, 1, emptyList(), emptyMap(), true,
            )
        }
        if (!Engine.awaitReady()) throw IllegalStateException(Engine.failMessage())
        val req = YoutubeDLRequest(url).apply {
            addOption("-J")
            addOption("--flat-playlist")
            addOption("--no-warnings")
        }
        Cookies.apply(ctx, req)
        val pid = "info-${System.nanoTime()}"
        cancelFetch()
        currentPid = pid
        val resp = YoutubeDL.getInstance().execute(req, processId = pid, callback = null)
        val out = resp.out.trim()
        val json = out.lineSequence().lastOrNull { it.trimStart().startsWith("{") } ?: out
        parse(url, JSONObject(json))
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
            return MediaInfo(
                url, title, uploader, thumb, 0, site, true, entries?.length() ?: 0,
                listOf(1080, 720, 480, 360), emptyMap(), true,
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
