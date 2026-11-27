package com.sushant.grabbit.engine

import android.net.Uri
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.data.Sites
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigInteger
import java.net.HttpURLConnection
import java.net.URL

/** One photo or video in a post (carousel, gallery, tweet with photos). [url] is the direct file. */
data class GalleryItem(val url: String, val video: Boolean, val thumb: String, val duration: Long = 0) {
    /** File extension to save with. */
    val ext: String get() = if (video) "mp4" else Images.extOf(url)
}

/**
 * Photo posts: direct image links, X/Twitter photos, Instagram posts and carousels, Reddit image posts
 * and galleries. Returns null when the link has no photos, so videos keep going through yt-dlp as before.
 */
object Images {
    private const val UA = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36"
    val IMG_EXT = setOf("jpg", "jpeg", "png", "webp", "gif", "heic", "avif")

    fun extOf(url: String): String {
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: url.substringBefore('?')
        val e = path.substringAfterLast('.', "").lowercase()
        val fmt = runCatching { Uri.parse(url).getQueryParameter("format") }.getOrNull()?.lowercase()
        return when {
            e in IMG_EXT -> if (e == "jpeg") "jpg" else e
            fmt in IMG_EXT -> if (fmt == "jpeg") "jpg" else fmt!!
            else -> "jpg"
        }
    }

    fun isDirectImage(url: String): Boolean {
        val path = runCatching { Uri.parse(url).path }.getOrNull() ?: return false
        return path.substringAfterLast('.', "").lowercase() in IMG_EXT
    }

    private val TWEET = Regex("""(?:twitter|x)\.com/[^/?#]+/status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)
    private val INSTA_POST = Regex("""instagram\.com/(?:[^/?#]+/)?p/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
    private val REDDIT = Regex("""reddit\.com/(?:r/[^/]+/)?comments/([a-z0-9]+)|redd\.it/([a-z0-9]+)""", RegexOption.IGNORE_CASE)

    fun tweetId(url: String) = TWEET.find(url)?.groupValues?.get(1)
    fun instaCode(url: String) = INSTA_POST.find(url)?.groupValues?.get(1)
    fun redditId(url: String) = REDDIT.find(url)?.let { m -> m.groupValues[1].ifBlank { m.groupValues[2] } }?.takeIf { it.isNotBlank() }

    /** Could this link be a photo post? (Cheap check before any network call.) */
    fun mightHavePhotos(url: String) =
        isDirectImage(url) || tweetId(url) != null || instaCode(url) != null || redditId(url) != null

    /** Instagram shortcode -> media id. */
    fun instaMediaId(code: String): String {
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_"
        var n = BigInteger.ZERO
        for (c in code.take(11)) n = n.multiply(BigInteger.valueOf(64)).add(BigInteger.valueOf(alphabet.indexOf(c).toLong()))
        return n.toString()
    }

    /** Token the tweet-embed service expects: (id / 1e15 * π) in base 36 without zeros and dots. */
    fun tweetToken(id: String): String {
        val x = id.toDouble() / 1e15 * Math.PI
        val digits = "0123456789abcdefghijklmnopqrstuvwxyz"
        var ip = Math.floor(x).toLong()
        var frac = x - Math.floor(x)
        val sb = StringBuilder()
        if (ip == 0L) sb.append('0')
        val intPart = StringBuilder()
        while (ip > 0) { intPart.append(digits[(ip % 36).toInt()]); ip /= 36 }
        sb.append(intPart.reverse())
        sb.append('.')
        repeat(11) { frac *= 36; val d = Math.floor(frac).toInt(); sb.append(digits[d]); frac -= d }
        return sb.toString().replace(Regex("(0+|\\.)"), "")
    }

    suspend fun resolve(url: String): MediaInfo? = withContext(Dispatchers.IO) {
        when {
            isDirectImage(url) -> {
                val name = Uri.parse(url).lastPathSegment?.substringBeforeLast('.')?.ifBlank { null } ?: "Image"
                info(url, name, "", Sites.siteName(url), listOf(GalleryItem(url, false, url)))
            }
            tweetId(url) != null -> tweet(url, tweetId(url)!!)
            instaCode(url) != null -> insta(url, instaCode(url)!!)
            redditId(url) != null -> reddit(url)
            else -> null
        }
    }

    fun info(url: String, title: String, uploader: String, site: String, items: List<GalleryItem>) = MediaInfo(
        url, title, uploader, items.firstOrNull()?.thumb.orEmpty(), 0, site, false, items.size,
        emptyList(), emptyMap(), hasVideo = false, gallery = items,
    )

    // ---- X / Twitter -------------------------------------------------------------------------

    private fun tweet(url: String, id: String): MediaInfo? {
        var body: String? = null
        for (token in listOf(tweetToken(id), "a")) {
            val (code, text) = get("https://cdn.syndication.twimg.com/tweet-result?id=$id&lang=en&token=$token", emptyMap())
            if (code == 200 && text.trimStart().startsWith("{")) { body = text; break }
        }
        val j = JSONObject(body ?: return null)
        val items = TweetParser.items(j)
        if (items.none { !it.video }) return null // video-only tweets keep using yt-dlp
        val user = j.optJSONObject("user")?.optString("screen_name").orEmpty()
        val title = j.optString("text").substringBefore("https://t.co").trim().take(80).ifBlank { "Post by @$user" }
        return info(url, title, if (user.isBlank()) "" else "@$user", "X", items)
    }

    // ---- Instagram ---------------------------------------------------------------------------

    private fun insta(url: String, code: String): MediaInfo? {
        val cookie = Cookies.headerFor("www.instagram.com")
        val csrf = Regex("""(?:^|;\s*)csrftoken=([^;]+)""").find(cookie)?.groupValues?.get(1).orEmpty()
        if (Regex("""(?:^|;\s*)sessionid=""").containsMatchIn(cookie)) {
            val headers = mapOf(
                "User-Agent" to Prefs.loginUserAgent.ifBlank { UA }, "X-IG-App-ID" to "936619743392459",
                "X-CSRFToken" to csrf, "Cookie" to cookie, "Referer" to "https://www.instagram.com/",
                "X-Requested-With" to "XMLHttpRequest",
            )
            for (host in listOf("https://i.instagram.com", "https://www.instagram.com")) {
                val (c, body) = get("$host/api/v1/media/${instaMediaId(code)}/info/", headers)
                if (c == 200 && body.trimStart().startsWith("{")) {
                    val item = JSONObject(body).optJSONArray("items")?.optJSONObject(0) ?: break
                    val items = InstaPostParser.items(item)
                    if (items.none { !it.video }) return null
                    val user = item.optJSONObject("user")?.optString("username").orEmpty()
                    return info(url, "Post by @$user", "@$user", "Instagram", items)
                }
            }
        }
        // Not logged in: the public embed page shows the post's photos
        val (c, html) = get("https://www.instagram.com/p/$code/embed/captioned/", mapOf("User-Agent" to UA))
        if (c != 200) return null
        val items = InstaPostParser.fromEmbed(html)
        if (items.none { !it.video }) return null
        val user = Regex(""""owner"\s*:\s*\{[^}]*?"username"\s*:\s*"([^"]+)"""").find(html.replace("\\\"", "\""))?.groupValues?.get(1)
            ?: Regex("""class="UsernameText">([^<]+)<""").find(html)?.groupValues?.get(1).orEmpty()
        return info(url, if (user.isBlank()) "Instagram post" else "Post by @$user", if (user.isBlank()) "" else "@$user", "Instagram", items)
    }

    // ---- Reddit ------------------------------------------------------------------------------

    private fun reddit(url: String): MediaInfo? {
        val base = url.substringBefore('?').trimEnd('/')
        val jsonUrl = if (base.contains("redd.it/")) "https://www.reddit.com/comments/${redditId(url)}.json" else "$base.json"
        val (c, body) = get(jsonUrl, mapOf("User-Agent" to "android:com.sushant.grabbit:3.0 (personal downloader)"))
        if (c != 200 || !body.trimStart().startsWith("[")) return null
        val post = JSONArray(body).optJSONObject(0)?.optJSONObject("data")?.optJSONArray("children")
            ?.optJSONObject(0)?.optJSONObject("data") ?: return null
        val items = RedditParser.items(post)
        if (items.none { !it.video }) return null
        return info(url, post.optString("title").take(80).ifBlank { "Reddit post" },
            post.optString("author").let { if (it.isBlank()) "" else "u/$it" }, "Reddit", items)
    }

    // ---- HTTP --------------------------------------------------------------------------------

    private fun get(url: String, headers: Map<String, String>): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            if ("User-Agent" !in headers) setRequestProperty("User-Agent", UA)
            headers.forEach { (k, v) -> if (v.isNotBlank()) setRequestProperty(k, v) }
        }
        return try {
            val code = c.responseCode
            code to ((if (code in 200..299) c.inputStream else c.errorStream)?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: java.io.IOException) {
            -1 to ""
        } finally {
            c.disconnect()
        }
    }
}

/** Tweet JSON from the embed service -> photos (original size) and videos (best mp4). */
object TweetParser {
    fun items(j: JSONObject): List<GalleryItem> {
        val out = mutableListOf<GalleryItem>()
        val details = j.optJSONArray("mediaDetails")
        if (details != null) for (i in 0 until details.length()) {
            val m = details.optJSONObject(i) ?: continue
            val img = m.optString("media_url_https")
            when (m.optString("type")) {
                "photo" -> if (img.isNotBlank()) out += GalleryItem(orig(img), false, img)
                "video", "animated_gif" -> {
                    val variants = m.optJSONObject("video_info")?.optJSONArray("variants")
                    val best = (0 until (variants?.length() ?: 0)).mapNotNull { variants!!.optJSONObject(it) }
                        .filter { it.optString("content_type") == "video/mp4" }.maxByOrNull { it.optInt("bitrate", 0) }
                    best?.optString("url")?.let {
                        out += GalleryItem(it, true, img, (m.optJSONObject("video_info")?.optLong("duration_millis", 0) ?: 0) / 1000)
                    }
                }
            }
        }
        if (out.isEmpty()) j.optJSONArray("photos")?.let { p ->
            for (i in 0 until p.length()) p.optJSONObject(i)?.optString("url")?.takeIf { it.isNotBlank() }?.let { out += GalleryItem(orig(it), false, it) }
        }
        return out
    }

    /** Largest version of a pbs.twimg.com photo. */
    fun orig(u: String): String {
        if (!u.contains("pbs.twimg.com/media/")) return u
        val base = u.substringBefore('?')
        val ext = base.substringAfterLast('.', "jpg")
        return base.substringBeforeLast('.') + "?format=$ext&name=orig"
    }
}

/** Instagram media JSON / embed page -> photos and videos of a post (carousels included). */
object InstaPostParser {
    private fun best(o: JSONObject, key: String, inner: String? = null): String? {
        val arr = (if (inner != null) o.optJSONObject(key)?.optJSONArray(inner) else o.optJSONArray(key)) ?: return null
        return (0 until arr.length()).mapNotNull { arr.optJSONObject(it) }
            .maxByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }?.optString("url")
    }

    private fun one(m: JSONObject): GalleryItem? {
        val img = best(m, "image_versions2", "candidates").orEmpty()
        val vid = best(m, "video_versions")
        return when {
            vid != null -> GalleryItem(vid, true, img, m.optDouble("video_duration", 0.0).toLong())
            img.isNotBlank() -> GalleryItem(img, false, img)
            else -> null
        }
    }

    fun items(item: JSONObject): List<GalleryItem> {
        val carousel = item.optJSONArray("carousel_media")
        if (carousel != null) return (0 until carousel.length()).mapNotNull { carousel.optJSONObject(it)?.let(::one) }
        return listOfNotNull(one(item))
    }

    /** Embed page: "display_url" (photos) and "video_url" in its JSON (often escaped). */
    fun fromEmbed(html: String): List<GalleryItem> {
        val text = html.replace("\\\\/", "/").replace("\\/", "/").replace("\\\"", "\"")
        val out = LinkedHashMap<String, GalleryItem>()
        Regex(""""display_url"\s*:\s*"([^"]+)"(?:[^{}]*?"is_video"\s*:\s*(true|false))?(?:[^{}]*?"video_url"\s*:\s*"([^"]+)")?""")
            .findAll(text).forEach { m ->
                val img = unescape(m.groupValues[1])
                val vid = m.groupValues[3].takeIf { it.isNotBlank() }?.let(::unescape)
                val key = img.substringBefore('?')
                if (key !in out) out[key] = if (vid != null) GalleryItem(vid, true, img) else GalleryItem(img, false, img)
            }
        // A plain single photo: the embed's main image
        if (out.isEmpty()) Regex("""class="EmbeddedMediaImage"[^>]*src="([^"]+)"""").find(html)?.let {
            val img = it.groupValues[1].replace("&amp;", "&")
            out[img] = GalleryItem(img, false, img)
        }
        // The first display_url is the post itself for single photos; carousels list each child once more
        val list = out.values.toList()
        return if (list.size > 1) list.drop(if (text.contains("edge_sidecar_to_children")) 1 else 0) else list
    }

    private fun unescape(s: String) = s.replace("\\u0026", "&").replace("&amp;", "&")
}

/** Reddit post JSON -> image, or every image of a gallery post. */
object RedditParser {
    fun items(post: JSONObject): List<GalleryItem> {
        if (post.optBoolean("is_gallery")) {
            val meta = post.optJSONObject("media_metadata") ?: return emptyList()
            val order = post.optJSONObject("gallery_data")?.optJSONArray("items")
                ?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("media_id") } }
                ?: meta.keys().asSequence().toList()
            return order.mapNotNull { id ->
                val m = meta.optJSONObject(id) ?: return@mapNotNull null
                val s = m.optJSONObject("s") ?: return@mapNotNull null
                val u = (s.optString("u").ifBlank { s.optString("gif") }).replace("&amp;", "&")
                if (u.isBlank()) null else GalleryItem(u, false, u)
            }
        }
        val u = post.optString("url_overridden_by_dest").ifBlank { post.optString("url") }.replace("&amp;", "&")
        return if (Images.isDirectImage(u)) listOf(GalleryItem(u, false, u)) else emptyList()
    }
}
