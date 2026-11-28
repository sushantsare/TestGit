package com.sushant.grabbit.engine

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Grabbit's own YouTube playlist reader. Used only when the download engine can't open a playlist
 * ("Unable to recognize tab page"). It asks YouTube's app servers for the playlist the same way the
 * YouTube website and YouTube Music do, and returns the list of videos; each video then downloads normally.
 */
object YtPlaylist {
    data class Result(val id: String, val title: String, val owner: String, val entries: List<PlEntry>, val via: String)

    /** Most videos read in one go (100 per request). */
    const val MAX = 5000

    private const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Safari/537.36"

    private data class Client(val name: String, val version: String, val host: String, val nameId: String)

    private val WEB = Client("WEB", "2.20251120.01.00", "www.youtube.com", "1")
    private val MUSIC = Client("WEB_REMIX", "1.20251119.01.00", "music.youtube.com", "67")

    /** The playlist id from any YouTube / YouTube Music link, or null. */
    fun idFrom(url: String): String? {
        val host = runCatching { URL(url).host.lowercase() }.getOrNull() ?: return null
        if (!(host.endsWith("youtube.com") || host == "youtu.be")) return null
        return Regex("[?&]list=([A-Za-z0-9_-]{2,})").find(url)?.groupValues?.get(1)
    }

    /** Debug trail of the last read, shown in test logs. */
    @Volatile var lastTrail: String = ""

    fun read(id: String): Result {
        val trail = StringBuilder()
        val tries = listOf(
            "web" to { browse(WEB, id) },
            "music" to { browse(MUSIC, id) },
            "watch" to { next(id) },
        )
        var lastErr: Throwable? = null
        for ((name, run) in tries) {
            try {
                val r = run()
                trail.append("$name:${r.entries.size} ")
                if (r.entries.isNotEmpty()) { lastTrail = trail.toString(); return r.copy(via = name) }
            } catch (t: Throwable) {
                trail.append("$name:ERR(${t.message?.take(80)}) ")
                lastErr = t
            }
        }
        lastTrail = trail.toString()
        Log.w("Grabbit", "YtPlaylist failed $id: $trail")
        throw lastErr ?: IllegalStateException("Playlist is empty")
    }

    private fun context(c: Client) = JSONObject().put("client", JSONObject()
        .put("clientName", c.name).put("clientVersion", c.version).put("hl", "en").put("gl", "US"))

    private fun browse(c: Client, id: String): Result {
        val first = post(c, "browse", JSONObject().put("context", context(c)).put("browseId", "VL$id"))
        val head = YtPlaylistParser.header(first)
        val all = YtPlaylistParser.videos(first).toMutableList()
        var token = YtPlaylistParser.continuation(first)
        val seen = HashSet<String>()
        while (token != null && all.size < MAX && seen.add(token)) {
            val more = post(c, "browse", JSONObject().put("context", context(c)).put("continuation", token))
            val got = YtPlaylistParser.videos(more)
            if (got.isEmpty()) break
            all += got
            token = YtPlaylistParser.continuation(more)
        }
        return Result(id, head.first, head.second, YtPlaylistParser.toEntries(all), "")
    }

    /** Watch-page queue (works for mixes and some music lists; up to about 200 videos). */
    private fun next(id: String): Result {
        val j = post(WEB, "next", JSONObject().put("context", context(WEB)).put("playlistId", id))
        val panel = YtPlaylistParser.find(j, "playlistPanelRenderer")
        val title = panel?.let { YtPlaylistParser.text(it.opt("title")) }.orEmpty()
        val owner = panel?.let { YtPlaylistParser.text(it.opt("ownerName")) }.orEmpty()
        return Result(id, title, owner, YtPlaylistParser.toEntries(panel?.let { YtPlaylistParser.videos(it) }.orEmpty()), "")
    }

    private fun post(c: Client, endpoint: String, body: JSONObject): JSONObject {
        val conn = (URL("https://${c.host}/youtubei/v1/$endpoint?prettyPrint=false").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("User-Agent", UA)
            setRequestProperty("Origin", "https://${c.host}")
            setRequestProperty("Referer", "https://${c.host}/")
            setRequestProperty("X-Youtube-Client-Name", c.nameId)
            setRequestProperty("X-Youtube-Client-Version", c.version)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        try {
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            val text = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            return JSONObject(text)
        } finally { conn.disconnect() }
    }
}

/** Pure JSON reading for [YtPlaylist] (unit-tested). Walks the whole response, so layout changes don't break it. */
object YtPlaylistParser {
    data class Vid(val id: String, val title: String, val duration: Long, val thumb: String, val playable: Boolean)

    /** Rows of the playlist itself. */
    private val MAIN_KEYS = setOf("playlistVideoRenderer", "playlistPanelVideoRenderer", "musicResponsiveListItemRenderer")
    /** Only used when the page has none of the above (newer layouts). */
    private val OTHER_KEYS = setOf("videoRenderer", "gridVideoRenderer", "lockupViewModel")

    fun text(o: Any?): String = when (o) {
        is String -> o
        is JSONObject -> when {
            o.has("simpleText") -> o.optString("simpleText")
            o.has("runs") -> o.optJSONArray("runs")?.let { r -> (0 until r.length()).joinToString("") { r.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()
            o.has("content") -> o.optString("content")
            else -> ""
        }
        else -> ""
    }

    /** First object stored under [key] anywhere in the tree. */
    fun find(o: Any?, key: String): JSONObject? {
        when (o) {
            is JSONObject -> {
                o.optJSONObject(key)?.let { return it }
                for (k in o.keys()) find(o.opt(k), key)?.let { return it }
            }
            is JSONArray -> for (i in 0 until o.length()) find(o.opt(i), key)?.let { return it }
        }
        return null
    }

    private fun walk(o: Any?, f: (String, JSONObject) -> Unit) {
        when (o) {
            is JSONObject -> for (k in o.keys()) {
                val v = o.opt(k)
                if (v is JSONObject) f(k, v)
                walk(v, f)
            }
            is JSONArray -> for (i in 0 until o.length()) walk(o.opt(i), f)
        }
    }

    private fun thumbOf(r: JSONObject): String {
        val arr = find(r, "thumbnail")?.optJSONArray("thumbnails") ?: find(r, "image")?.optJSONArray("sources") ?: return ""
        val list = (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
        return (list.firstOrNull { it.optInt("width", 0) >= 240 } ?: list.lastOrNull())?.optString("url").orEmpty()
    }

    private fun secs(s: String): Long {
        val p = s.trim().split(":").mapNotNull { it.toLongOrNull() }
        if (p.isEmpty() || p.size != s.trim().split(":").size) return 0
        return p.fold(0L) { acc, n -> acc * 60 + n }
    }

    private fun vid(key: String, r: JSONObject): Vid? {
        return when (key) {
            "musicResponsiveListItemRenderer" -> {
                val id = r.optJSONObject("playlistItemData")?.optString("videoId").orEmpty()
                    .ifBlank { find(r, "watchEndpoint")?.optString("videoId").orEmpty() }
                if (id.isBlank()) return null
                val cols = r.optJSONArray("flexColumns")
                val title = cols?.optJSONObject(0)?.let { text(find(it, "text")) }.orEmpty()
                val fixed = r.optJSONArray("fixedColumns")?.optJSONObject(0)?.let { text(find(it, "text")) }.orEmpty()
                Vid(id, title, secs(fixed), thumbOf(r), true)
            }
            "lockupViewModel" -> {
                if (r.optString("contentType") != "LOCKUP_CONTENT_TYPE_VIDEO") return null
                val id = r.optString("contentId"); if (id.isBlank()) return null
                val title = find(r, "lockupMetadataViewModel")?.let { text(it.opt("title")) }.orEmpty()
                Vid(id, title, 0, thumbOf(r), true)
            }
            else -> {
                val id = r.optString("videoId"); if (id.isBlank()) return null
                val len = r.optString("lengthSeconds").toLongOrNull() ?: secs(text(r.opt("lengthText")))
                Vid(id, text(r.opt("title")), len, thumbOf(r), r.optBoolean("isPlayable", true))
            }
        }
    }

    /** Every video in the response, in page order, without repeats. */
    fun videos(j: JSONObject): List<Vid> {
        for (keys in listOf(MAIN_KEYS, OTHER_KEYS)) {
            val out = LinkedHashMap<String, Vid>()
            walk(j) { k, v -> if (k in keys) vid(k, v)?.let { if (it.id.length == 11) out.putIfAbsent(it.id, it) } }
            if (out.isNotEmpty()) return out.values.toList()
        }
        return emptyList()
    }

    /** Token for the next 100 videos, or null at the end. */
    fun continuation(j: JSONObject): String? {
        var tok: String? = null
        walk(j) { k, v -> if (k == "continuationCommand") v.optString("token").takeIf { it.isNotBlank() }?.let { tok = it } }
        if (tok == null) walk(j) { k, v -> if (k == "nextContinuationData") v.optString("continuation").takeIf { it.isNotBlank() }?.let { tok = it } }
        return tok
    }

    /** Playlist title and owner from the page header. */
    fun header(j: JSONObject): Pair<String, String> {
        val meta = find(j, "playlistMetadataRenderer")
        val ph = find(j, "playlistHeaderRenderer")
        val page = find(j, "pageHeaderViewModel")
        val music = find(j, "musicResponsiveHeaderRenderer") ?: find(j, "musicDetailHeaderRenderer") ?: find(j, "musicEditablePlaylistDetailHeaderRenderer")
        val title = listOf(
            meta?.optString("title"), ph?.let { text(it.opt("title")) },
            page?.let { text(find(it, "dynamicTextViewModel")?.opt("text") ?: it.opt("title")) },
            music?.let { text(it.opt("title")) },
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        val owner = listOf(
            ph?.let { text(it.opt("ownerText")) },
            music?.let { text(it.opt("straplineTextOne")) }, music?.let { text(it.opt("subtitle")) },
        ).firstOrNull { !it.isNullOrBlank() }.orEmpty()
        return title to owner
    }

    fun toEntries(v: List<Vid>): List<PlEntry> = v.mapIndexed { i, x ->
        val gone = !x.playable || x.title in setOf("[Private video]", "[Deleted video]", "[Unavailable video]")
        PlEntry(i + 1, x.id, "https://www.youtube.com/watch?v=${x.id}", x.title.ifBlank { "Video ${i + 1}" },
            x.duration, x.thumb.ifBlank { "https://i.ytimg.com/vi/${x.id}/mqdefault.jpg" }, available = !gone)
    }
}
