package com.sushant.grabbit.engine

import android.net.Uri
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Prefs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Instagram stories, read directly with the user's own Instagram login (yt-dlp's story support is often
 * refused by Instagram). Asks Instagram for the account's current stories, picks the shared one, and hands
 * its video file to the normal download engine.
 */
object InstaStory {
    data class Story(val video: String, val thumb: String, val user: String, val duration: Long)

    private const val APP_ID = "936619743392459"
    private const val CHROME =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /** instagram.com/stories/<user>/<mediaId> or /stories/highlights/<id> */
    fun isStory(url: String): Boolean {
        val u = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = u.host?.lowercase() ?: return false
        if (host != "instagram.com" && !host.endsWith(".instagram.com")) return false
        return u.pathSegments.firstOrNull() == "stories" && u.pathSegments.size >= 2
    }

    suspend fun resolve(url: String): Story = withContext(Dispatchers.IO) {
        val u = Uri.parse(url)
        val seg = u.pathSegments
        val highlight = seg.getOrNull(1) == "highlights"
        val user = if (highlight) "" else seg[1]
        val wanted = if (highlight) u.getQueryParameter("story_media_id")?.substringBefore('_') else seg.getOrNull(2)

        val cookie = Cookies.headerFor("www.instagram.com")
        val csrf = Regex("""(?:^|;\s*)csrftoken=([^;]+)""").find(cookie)?.groupValues?.get(1).orEmpty()
        val hasSession = Regex("""(?:^|;\s*)sessionid=""").containsMatchIn(cookie)
        val ua = Prefs.loginUserAgent.ifBlank { CHROME }
        val diag = mutableListOf(
            "Instagram story reader",
            "login saved: ${cookie.isNotBlank()}, session: $hasSession, csrf: ${csrf.isNotBlank()}, " +
                "browser identity: ${Prefs.loginUserAgent.isNotBlank()}",
        )
        if (!hasSession) throw FriendlyError(
            "Stories need your Instagram login. Go to Settings → Accounts → Instagram and log in, then try again.",
            diag.joinToString("\n"),
        )
        fun api(path: String): JSONObject {
            var last = ""
            for (host in listOf("https://i.instagram.com", "https://www.instagram.com")) {
                val (code, body) = get("$host$path", ua, cookie, csrf)
                diag += "$host$path → HTTP $code, ${body.length} chars"
                if (code == 200 && body.trimStart().startsWith("{")) return JSONObject(body)
                last = body.take(200)
            }
            throw FriendlyError(
                when {
                    "login_required" in last || "checkpoint" in last ->
                        "Instagram says your login has expired or needs confirming. Open Instagram, check for a security " +
                            "prompt, then log out and log in again in Settings → Accounts."
                    else -> "Instagram didn't return this story. Try again in a minute."
                },
                diag.joinToString("\n") + "\n" + last,
            )
        }

        val reelId = if (highlight) "highlight:${seg.getOrNull(2)}" else {
            val profile = api("/api/v1/users/web_profile_info/?username=${Uri.encode(user)}")
            profile.optJSONObject("data")?.optJSONObject("user")?.optString("id")?.takeIf { it.isNotBlank() }
                ?: throw FriendlyError("Couldn't find the Instagram account @$user.", diag.joinToString("\n"))
        }
        val feed = api("/api/v1/feed/reels_media/?reel_ids=${Uri.encode(reelId)}")
        val story = InstaParser.pick(feed, reelId, wanted)
        diag += "stories found: ${InstaParser.count(feed, reelId)}, match: ${story != null}"
        when {
            story == null -> throw FriendlyError(
                "This story isn't available any more, or your account can't see it (private accounts need you to follow them).",
                diag.joinToString("\n"),
            )
            story.video.isBlank() -> throw FriendlyError(
                "This story is a photo. Grabbit downloads video stories only for now.", diag.joinToString("\n"),
            )
        }
        story!!.copy(user = story.user.ifBlank { user })
    }

    private fun get(url: String, ua: String, cookie: String, csrf: String): Pair<Int, String> {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 20_000
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            setRequestProperty("X-IG-App-ID", APP_ID)
            setRequestProperty("X-ASBD-ID", "129477")
            setRequestProperty("X-IG-WWW-Claim", "0")
            setRequestProperty("X-Requested-With", "XMLHttpRequest")
            setRequestProperty("Referer", "https://www.instagram.com/")
            setRequestProperty("Origin", "https://www.instagram.com")
            if (csrf.isNotBlank()) setRequestProperty("X-CSRFToken", csrf)
            setRequestProperty("Cookie", cookie)
        }
        return try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            code to (stream?.bufferedReader()?.use { it.readText() } ?: "")
        } catch (e: java.io.IOException) {
            throw FriendlyError("Couldn't reach Instagram. Check your internet connection and try again.", e.toString())
        } finally {
            c.disconnect()
        }
    }
}

/** Pure parsing of Instagram's reels_media response (unit-tested). */
object InstaParser {
    private fun items(feed: JSONObject, reelId: String): org.json.JSONArray? {
        feed.optJSONObject("reels")?.optJSONObject(reelId)?.optJSONArray("items")?.let { return it }
        val list = feed.optJSONArray("reels_media") ?: return null
        for (i in 0 until list.length()) {
            val r = list.optJSONObject(i) ?: continue
            if (r.optString("id") == reelId || list.length() == 1) return r.optJSONArray("items")
        }
        return null
    }

    fun count(feed: JSONObject, reelId: String): Int = items(feed, reelId)?.length() ?: 0

    /** The story with media id [wanted] (or the first one), with its best video and thumbnail. */
    fun pick(feed: JSONObject, reelId: String, wanted: String?): InstaStory.Story? {
        val arr = items(feed, reelId) ?: return null
        var chosen: JSONObject? = null
        for (i in 0 until arr.length()) {
            val it = arr.optJSONObject(i) ?: continue
            val pk = it.optString("pk").ifBlank { it.optString("id").substringBefore('_') }
            if (wanted == null || pk == wanted) { chosen = it; break }
        }
        val s = chosen ?: return null
        val video = s.optJSONArray("video_versions")?.let { v ->
            (0 until v.length()).mapNotNull { v.optJSONObject(it) }
                .maxByOrNull { it.optInt("width", 0) * it.optInt("height", 0) }?.optString("url")
        }.orEmpty()
        val thumb = s.optJSONObject("image_versions2")?.optJSONArray("candidates")?.optJSONObject(0)?.optString("url").orEmpty()
        val user = s.optJSONObject("user")?.optString("username").orEmpty()
        return InstaStory.Story(video, thumb, user, s.optDouble("video_duration", 0.0).toLong())
    }
}
