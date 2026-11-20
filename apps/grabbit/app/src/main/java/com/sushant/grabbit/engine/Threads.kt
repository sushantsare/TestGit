package com.sushant.grabbit.engine

import android.net.Uri
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * Threads isn't supported by yt-dlp, so Grabbit reads the post page itself: the page embeds the post's
 * media as JSON ("video_versions"), or as og:video tags for link-preview crawlers. The direct video file
 * is then handed to the normal download engine.
 */
/** An error whose message is already user-friendly; [raw] carries technical details for "Details". */
class FriendlyError(message: String, val raw: String) : Exception(message)

object Threads {
    data class Post(val videos: List<String>, val thumb: String, val caption: String, val user: String)

    fun isThreads(url: String): Boolean {
        val host = runCatching { Uri.parse(url).host }.getOrNull()?.lowercase() ?: return false
        return host == "threads.net" || host == "threads.com" || host.endsWith(".threads.net") || host.endsWith(".threads.com")
    }

    private const val CHROME =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
    private const val PREVIEW_BOT = "facebookexternalhit/1.1 (+http://www.facebook.com/externalhit_uatext.php)"

    /** Reads the post. Tries as a normal browser (with your Threads login, if saved), then as a link-preview bot. */
    suspend fun resolve(url: String): Post = withContext(Dispatchers.IO) {
        val clean = url.substringBefore('?')
        val host = Uri.parse(clean).host ?: "www.threads.com"
        val ua = Prefs.loginUserAgent.ifBlank { CHROME }
        val diag = mutableListOf<String>()
        var blocked: String? = null
        for ((name, agent, cookie) in listOf(
            Triple("browser", ua, Cookies.headerFor(host)),
            Triple("preview", PREVIEW_BOT, ""),
        )) {
            try {
                val html = get(clean, agent, cookie)
                val post = ThreadsParser.parse(html, userFromUrl(clean))
                diag += "$name: page ${html.length / 1024} KB, login=${cookie.isNotBlank()}, " +
                    "video_versions=${"video_versions" in html}, og_video=${"og:video" in html}, videos=${post.videos.size}"
                if (post.videos.isNotEmpty()) return@withContext post
            } catch (e: Exception) {
                diag += "$name: ${e.message}"
                if (blocked == null && e is IllegalStateException) blocked = e.message
                if (blocked == null && e is java.io.IOException) blocked = "Couldn't reach Threads. Check your internet connection and try again."
            }
        }
        throw FriendlyError(
            blocked ?: ("No video found in this Threads post. It may be a photo or text post, or from a private " +
                "account (log in to Threads in Settings → Accounts)."),
            "Threads reader\n" + diag.joinToString("\n"),
        )
    }

    private fun userFromUrl(url: String): String =
        Regex("""/@([A-Za-z0-9._]+)""").find(url)?.groupValues?.get(1).orEmpty()

    private fun get(url: String, agent: String, cookie: String): String {
        var target = url
        repeat(5) {
            val c = (URL(target).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 15_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", agent)
                setRequestProperty("Accept", "text/html,application/xhtml+xml")
                setRequestProperty("Accept-Language", "en-US,en;q=0.9")
                if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
            }
            try {
                val code = c.responseCode
                if (code in 300..399) {
                    val loc = c.getHeaderField("Location") ?: throw IllegalStateException("HTTP $code")
                    target = URL(URL(target), loc).toString()
                    return@repeat
                }
                if (code == 404 || code == 410) throw IllegalStateException("This Threads post does not exist. It may have been deleted.")
                if (code == 401 || code == 403) throw IllegalStateException(
                    "Threads didn't allow access to this post. If the account is private, log in to Threads in Settings → Accounts."
                )
                if (code >= 400) throw IllegalStateException("Threads couldn't load this post right now. Try again in a minute.")
                return c.inputStream.bufferedReader().use { it.readText() }
            } finally {
                c.disconnect()
            }
        }
        throw IllegalStateException("Too many redirects")
    }
}

/** Pure HTML/JSON parsing for Threads pages (unit-tested). */
object ThreadsParser {
    private val VIDEO_VERSIONS = Regex(""""video_versions"\s*:\s*(\[[^\]]*\])""")
    private val IMAGE_CANDIDATES = Regex(""""image_versions2"\s*:\s*\{\s*"candidates"\s*:\s*(\[[^\]]*\])""")
    private val CAPTION = Regex(""""caption"\s*:\s*\{[^{}]*?"text"\s*:\s*("(?:[^"\\]|\\.)*")""")
    private val USERNAME = Regex(""""username"\s*:\s*("(?:[^"\\]|\\.)*")""")
    private fun meta(prop: String) =
        Regex("""<meta[^>]+(?:property|name)="$prop"[^>]+content="([^"]*)"""", RegexOption.IGNORE_CASE)

    fun parse(html: String, userHint: String = ""): Threads.Post {
        val videos = LinkedHashSet<String>()
        VIDEO_VERSIONS.findAll(html).forEach { m ->
            firstUrl(m.groupValues[1])?.let { videos += it }
        }
        if (videos.isEmpty()) {
            listOf("og:video:secure_url", "og:video:url", "og:video").forEach { p ->
                meta(p).find(html)?.groupValues?.get(1)?.let { videos += unescapeHtml(it) }
            }
        }
        val thumb = IMAGE_CANDIDATES.find(html)?.groupValues?.get(1)?.let { firstUrl(it) }
            ?: meta("og:image").find(html)?.groupValues?.get(1)?.let { unescapeHtml(it) }
            ?: ""
        val caption = CAPTION.find(html)?.groupValues?.get(1)?.let { jsonString(it) }
            ?: meta("og:description").find(html)?.groupValues?.get(1)?.let { unescapeHtml(it) }
            ?: ""
        val user = userHint.ifBlank { USERNAME.find(html)?.groupValues?.get(1)?.let { jsonString(it) }.orEmpty() }
        // The main post comes first on the page; replies with their own videos come later
        return Threads.Post(videos.take(1), thumb, caption, user)
    }

    fun title(post: Threads.Post): String {
        val line = post.caption.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }.orEmpty()
        return when {
            line.isNotEmpty() -> if (line.length > 90) line.take(87).trimEnd() + "…" else line
            post.user.isNotEmpty() -> "Threads video by @${post.user}"
            else -> "Threads video"
        }
    }

    private fun firstUrl(jsonArray: String): String? = runCatching {
        val arr = org.json.JSONArray(jsonArray)
        (0 until arr.length()).asSequence().mapNotNull { arr.optJSONObject(it)?.optString("url") }
            .firstOrNull { it.startsWith("http") }
    }.getOrNull() ?: Regex(""""url"\s*:\s*("(?:[^"\\]|\\.)*")""").find(jsonArray)?.groupValues?.get(1)?.let { jsonString(it) }

    private fun jsonString(quoted: String): String? =
        runCatching { org.json.JSONArray("[$quoted]").getString(0) }.getOrNull()

    fun unescapeHtml(s: String): String = s.replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'")
        .replace("&#x27;", "'").replace("&lt;", "<").replace("&gt;", ">")
}
