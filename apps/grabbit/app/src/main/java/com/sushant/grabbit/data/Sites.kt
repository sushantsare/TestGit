package com.sushant.grabbit.data

import android.net.Uri

data class LoginSite(
    val key: String,
    val name: String,
    val loginUrl: String,
    val domains: List<String>,
)

object Sites {
    val logins = listOf(
        LoginSite("instagram", "Instagram", "https://www.instagram.com/accounts/login/", listOf("instagram.com")),
        LoginSite("facebook", "Facebook", "https://m.facebook.com/login/", listOf("facebook.com")),
        LoginSite("x", "X (Twitter)", "https://x.com/i/flow/login", listOf("x.com", "twitter.com")),
        LoginSite("youtube", "YouTube", "https://accounts.google.com/ServiceLogin?service=youtube&continue=https://m.youtube.com/", listOf("youtube.com", "google.com")),
        LoginSite("reddit", "Reddit", "https://www.reddit.com/login/", listOf("reddit.com")),
        LoginSite("tiktok", "TikTok", "https://www.tiktok.com/login", listOf("tiktok.com")),
        LoginSite("pinterest", "Pinterest", "https://www.pinterest.com/login/", listOf("pinterest.com")),
        LoginSite("vimeo", "Vimeo", "https://vimeo.com/log_in", listOf("vimeo.com")),
    )

    private val URL_RE = Regex("""https?://[^\s<>"']+""")

    /** Pulls the first link out of shared text like "Watch this! https://youtu.be/xyz" */
    fun extractUrl(text: String): String? =
        URL_RE.find(text)?.value?.trimEnd(')', ']', '.', ',', '!', '?', ';')

    fun siteName(url: String): String {
        val host = (runCatching { Uri.parse(url).host }.getOrNull() ?: return "Web")
            .lowercase().removePrefix("www.").removePrefix("m.").removePrefix("mobile.")
        fun h(vararg d: String) = d.any { host == it || host.endsWith(".$it") }
        return when {
            h("youtube.com", "youtu.be") -> "YouTube"
            h("instagram.com") -> "Instagram"
            h("facebook.com", "fb.watch", "fb.com") -> "Facebook"
            h("x.com", "twitter.com") -> "X"
            h("reddit.com", "redd.it") -> "Reddit"
            h("tiktok.com") -> "TikTok"
            h("pinterest.com", "pin.it") -> "Pinterest"
            h("vimeo.com") -> "Vimeo"
            h("dailymotion.com", "dai.ly") -> "Dailymotion"
            h("sharechat.com") -> "ShareChat"
            h("mojapp.in") -> "Moj"
            h("linkedin.com") -> "LinkedIn"
            h("twitch.tv") -> "Twitch"
            h("soundcloud.com") -> "SoundCloud"
            h("threads.net") -> "Threads"
            h("snapchat.com") -> "Snapchat"
            else -> host.substringBeforeLast('.').substringAfterLast('.').replaceFirstChar { it.uppercase() }
        }
    }
}
