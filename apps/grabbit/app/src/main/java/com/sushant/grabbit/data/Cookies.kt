package com.sushant.grabbit.data

import android.content.Context
import android.webkit.CookieManager
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

/**
 * Logins are captured from the in-app WebView and written as Netscape cookie files that yt-dlp reads
 * with --cookies. They live in noBackupFilesDir, so they never go into Android backups or device transfer.
 * The engine also sends the same browser identity (user agent) the login used, so sites like Instagram
 * accept the session instead of treating it as a different device.
 */
object Cookies {
    private lateinit var app: Context

    private fun dir() = File(app.noBackupFilesDir, "logins").apply { mkdirs() }
    private fun siteFile(site: LoginSite) = File(dir(), "${site.key}.txt")
    private fun combined() = File(app.noBackupFilesDir, "cookies.txt")

    fun init(ctx: Context) {
        app = ctx.applicationContext
        // Move logins saved by older versions (settings file + filesDir) out of anything that is backed up
        val edit = Prefs.p.edit()
        var moved = false
        Sites.logins.forEach { s ->
            val old = Prefs.p.getString("ck_${s.key}", null)
            if (old != null) {
                siteFile(s).writeText(old)
                edit.remove("ck_${s.key}")
                moved = true
            }
        }
        edit.apply()
        File(app.filesDir, "cookies.txt").delete()
        if (moved) rebuild()
    }

    fun isLoggedIn(site: LoginSite): Boolean = siteFile(site).let { it.exists() && it.length() > 0 }

    /** True when the saved login includes the site's session cookie (a complete login). */
    fun sessionSaved(site: LoginSite): Boolean =
        siteFile(site).let { f -> f.exists() && f.readLines().any { it.split('\t').getOrNull(5) == site.session } }

    /** True once the WebView holds the site's session cookie, i.e. the login really finished. */
    fun hasSession(site: LoginSite): Boolean {
        val cm = CookieManager.getInstance()
        return site.domains.any { d ->
            listOf("https://www.$d", "https://$d", "https://m.$d").any { u ->
                cm.getCookie(u)?.split(";")?.any { it.trim().startsWith("${site.session}=") } == true
            }
        }
    }

    /** Saves the WebView's cookies for this site. Returns how many cookies were found. */
    fun save(site: LoginSite, userAgent: String): Int {
        val cm = CookieManager.getInstance()
        cm.flush()
        val expiry = System.currentTimeMillis() / 1000 + 60L * 60 * 24 * 365
        val sb = StringBuilder()
        var count = 0
        for (d in site.domains) {
            val seen = HashSet<String>()
            for (u in listOf("https://www.$d", "https://$d", "https://m.$d")) {
                val raw = cm.getCookie(u) ?: continue
                raw.split(";").forEach { part ->
                    val kv = part.trim()
                    val i = kv.indexOf('=')
                    if (i > 0) {
                        val name = kv.substring(0, i)
                        if (seen.add(name)) {
                            sb.append(".$d\tTRUE\t/\tTRUE\t$expiry\t$name\t${kv.substring(i + 1)}\n")
                            count++
                        }
                    }
                }
            }
        }
        if (count > 0) {
            siteFile(site).writeText(sb.toString())
            if (userAgent.isNotBlank()) Prefs.loginUserAgent = userAgent
            rebuild()
        }
        return count
    }

    fun logout(site: LoginSite) {
        siteFile(site).delete()
        rebuild()
        val cm = CookieManager.getInstance()
        for (d in site.domains) {
            for (u in listOf("https://www.$d", "https://$d", "https://m.$d")) {
                cm.getCookie(u)?.split(";")?.forEach { part ->
                    val name = part.trim().substringBefore('=')
                    if (name.isNotEmpty()) {
                        cm.setCookie(u, "$name=; Max-Age=0; Path=/")
                        cm.setCookie(u, "$name=; Max-Age=0; Path=/; Domain=.$d")
                    }
                }
            }
        }
        cm.flush()
    }

    private fun rebuild() {
        val all = Sites.logins.mapNotNull { s -> siteFile(s).takeIf { it.exists() }?.readText() }.joinToString("")
        val f = combined()
        if (all.isBlank()) f.delete() else f.writeText("# Netscape HTTP Cookie File\n$all")
    }

    fun hasAny(): Boolean = combined().let { it.exists() && it.length() > 0 }

    /** Cookie header for a plain HTTP request to [host] (used by the Threads reader). */
    fun headerFor(host: String): String =
        if (!hasAny()) "" else CookieText.header(combined().readText(), host)

    private val youtube get() = Sites.logins.first { it.key == "youtube" }

    /** Test hook: saves login cookies for [key] as if the user had logged in ("" = log out). */
    fun testLogin(key: String, text: String, ua: String) {
        val s = Sites.logins.first { it.key == key }
        if (text.isEmpty()) siteFile(s).delete() else siteFile(s).writeText(text)
        Prefs.loginUserAgent = ua
        rebuild()
    }

    fun youtubeLoggedIn(): Boolean = ::app.isInitialized && isLoggedIn(youtube)

    /**
     * Adds the saved logins to an engine request for [url].
     * YouTube is special: public videos and playlists work best logged out (a saved login goes stale as
     * YouTube rotates it, and the phone browser's identity makes YouTube send pages the engine can't read).
     * So YouTube gets its login only when [youtubeLogin] is true — used as a retry when a video needs it —
     * and never the phone browser's user agent.
     */
    fun apply(@Suppress("UNUSED_PARAMETER") ctx: Context, req: YoutubeDLRequest, url: String, youtubeLogin: Boolean = false) {
        if (Sites.siteName(url) == "YouTube") {
            if (youtubeLogin && youtubeLoggedIn()) {
                val f = File(app.noBackupFilesDir, "youtube_cookies.txt")
                f.writeText("# Netscape HTTP Cookie File\n" + siteFile(youtube).readText())
                req.addOption("--cookies", f.absolutePath)
            }
            return
        }
        val f = combined()
        if (f.exists() && f.length() > 0) {
            req.addOption("--cookies", f.absolutePath)
            val ua = Prefs.loginUserAgent
            if (ua.isNotBlank()) req.addOption("--user-agent", ua)
        }
    }
}

/** Pure helpers for Netscape cookie text (unit-tested). */
object CookieText {
    /** Builds "a=1; b=2" from cookie lines whose domain matches [host]. */
    fun header(text: String, host: String): String {
        val h = host.lowercase().removePrefix("www.")
        return text.lineSequence()
            .filter { it.isNotBlank() && !it.startsWith("#") }
            .mapNotNull { line ->
                val p = line.split('\t')
                if (p.size < 7) return@mapNotNull null
                val d = p[0].trimStart('.').lowercase()
                if (h == d || h.endsWith(".$d")) "${p[5]}=${p[6]}" else null
            }
            .distinct()
            .joinToString("; ")
    }
}
