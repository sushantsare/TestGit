package com.sushant.grabbit.download

import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.SystemClock
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sushant.grabbit.data.Cookies
import com.sushant.grabbit.data.Library
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.engine.Engine
import com.sushant.grabbit.engine.Errors
import com.sushant.grabbit.engine.InstaStory
import com.sushant.grabbit.engine.isAudioQuality
import com.sushant.grabbit.data.Sites
import com.sushant.grabbit.engine.Threads
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class DownloadWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    companion object {
        const val K_URL = "url"
        const val K_TITLE = "title"
        const val K_THUMB = "thumb"
        const val K_SITE = "site"
        const val K_QUALITY = "quality"
        const val K_PLAYLIST = "playlist"
        const val K_OPTS = "opts"
        const val K_ERROR = "error"
        const val K_RAW = "raw"
        const val P_PCT = "p"
        const val P_STATUS = "status"
        const val P_SPEED = "speed"
        const val P_ETA = "eta"
        const val P_SIZE = "size"
        private val SPEED = Regex("""at\s+~?\s*([\d.]+\s*[KMG]i?B/s)""")
        private val ITEM = Regex("""Downloading item (\d+) of (\d+)""")

        const val P_STAGE = "stage"
        private const val TAG = "GrabbitE2E"

        fun buildRequest(
            ctx: Context, url: String, quality: String, playlist: Boolean, dir: File, opts: DlOpts,
            multiConnAllowed: Boolean = true,
        ): YoutubeDLRequest {
            val r = YoutubeDLRequest(url)
            val g = GlobalOpts(Prefs.videoFormat, Prefs.fasterDownloads, Prefs.embedThumb, Prefs.sponsorCats,
                multiConn = Engine.aria2Ready && multiConnAllowed)
            val yt = Sites.siteName(url) == "YouTube"
            DownloadArgs.build(quality, playlist, dir.absolutePath, opts, g, yt).forEach { (k, v) ->
                if (v == null) r.addOption(k) else r.addOption(k, v)
            }
            Cookies.apply(ctx, r)
            return r
        }
    }

    private val nid = id.hashCode()
    private val title get() = inputData.getString(K_TITLE) ?: "Grabbit"

    private fun fg(text: String, pct: Int, indeterminate: Boolean): ForegroundInfo {
        val n = Notifs.progress(applicationContext, title, text, pct, indeterminate, id)
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(nid, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else ForegroundInfo(nid, n)
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = fg("Starting…", 0, true)

    private fun fail(msg: String, raw: String): Result {
        Notifs.done(applicationContext, nid + 1, title, false, msg)
        return Result.failure(workDataOf(K_ERROR to msg, K_RAW to raw.takeLast(3000)))
    }

    private fun timing(msg: String) {
        if (com.sushant.grabbit.BuildConfig.DEBUG) android.util.Log.i(TAG, "TIMING $msg")
    }

    override suspend fun doWork(): Result {
        // Keep the CPU and Wi-Fi awake for the whole download, even with the screen off
        val pm = applicationContext.getSystemService(android.os.PowerManager::class.java)
        val wake = pm.newWakeLock(android.os.PowerManager.PARTIAL_WAKE_LOCK, "Grabbit:download").apply { setReferenceCounted(false) }
        @Suppress("DEPRECATION")
        val wifi = (applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager)
            ?.createWifiLock(android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Grabbit:download")
        runCatching { wake.acquire(6 * 60 * 60 * 1000L) }
        runCatching { wifi?.acquire() }
        try {
            return work()
        } finally {
            runCatching { if (wake.isHeld) wake.release() }
            runCatching { if (wifi?.isHeld == true) wifi.release() }
        }
    }

    private suspend fun work(): Result {
        val ctx = applicationContext
        val t0 = SystemClock.elapsedRealtime()
        val url = inputData.getString(K_URL) ?: return Result.failure(workDataOf(K_ERROR to "No link"))
        val quality = inputData.getString(K_QUALITY) ?: "best"
        val playlist = inputData.getBoolean(K_PLAYLIST, false)
        val site = inputData.getString(K_SITE) ?: ""
        val thumb = inputData.getString(K_THUMB) ?: ""

        runCatching { setForeground(fg("Preparing…", 0, true)) }
        setProgress(workDataOf(P_PCT to 0f, P_STATUS to "Preparing…"))

        if (!Engine.awaitReady()) return fail(Engine.failMessage(), "")

        val tmp = File(ctx.cacheDir, "dl/$id").apply { deleteRecursively(); mkdirs() }
        // Threads: find the post's direct video file first (yt-dlp can't read Threads pages)
        val source = if (Threads.isThreads(url) || InstaStory.isStory(url)) {
            try {
                if (InstaStory.isStory(url)) InstaStory.resolve(url).video else Threads.resolve(url).videos.first()
            } catch (e: com.sushant.grabbit.engine.FriendlyError) {
                return fail(e.message ?: "Download failed", e.raw)
            } catch (e: Exception) {
                val raw = e.message ?: e.toString()
                return fail(Errors.friendly(raw), raw)
            }
        } else url
        var opts = DlOpts.decode(inputData.getString(K_OPTS))
        var subsDropped = false
        val nm = ctx.getSystemService(NotificationManager::class.java)
        var last = 0L
        var total = 0L
        var status = if (isAudioQuality(quality)) "Downloading audio…" else "Downloading…"
        var stage = Stages.DOWNLOAD
        var tDl = 0L
        var tWork = 0L
        var multi = true

        fun publish(pct: Float, speed: String, eta: Long, size: String, force: Boolean) {
            val now = SystemClock.elapsedRealtime()
            if (!force && now - last < 600) return
            last = now
            val busy = stage != Stages.DOWNLOAD || pct <= 0f
            setProgressAsync(workDataOf(P_PCT to pct, P_STATUS to status, P_SPEED to speed, P_ETA to eta, P_SIZE to size, P_STAGE to stage))
            val text = buildString {
                append(status)
                if (stage == Stages.DOWNLOAD) {
                    if (pct > 0) append("  ${pct.toInt()}%")
                    if (size.isNotEmpty()) append("  ·  $size")
                    if (speed.isNotEmpty()) append("  ·  $speed")
                }
            }
            nm.notify(nid, Notifs.progress(ctx, title, text, pct.toInt(), busy, id))
        }

        suspend fun grab(o: DlOpts) = withContext(Dispatchers.IO) {
            val tStart = SystemClock.elapsedRealtime()
            YoutubeDL.getInstance().execute(buildRequest(ctx, source, quality, playlist, tmp, o, multi), processId = id.toString(), callback = { p, eta, line ->
                val work = Stages.statusFor(line)
                val item = ITEM.find(line)
                var force = false
                if (work != null && work != status) {
                    if (stage == Stages.DOWNLOAD) tDl = SystemClock.elapsedRealtime() - tStart
                    stage = Stages.WORK; status = work; force = true
                } else if (item != null) {
                    stage = Stages.DOWNLOAD
                    status = "Downloading ${item.groupValues[1]} of ${item.groupValues[2]}…"; force = true
                }
                Progress.totalBytes(line)?.let { total = it }
                val aria = Stages.aria(line)
                val pct = (aria?.pct ?: p).coerceIn(0f, 100f)
                val speed = aria?.speed?.replace("iB", "B") ?: (SPEED.find(line)?.groupValues?.get(1) ?: "").replace("iB", "B")
                val size = if (aria != null) "${aria.done.replace("iB", "B")} of ${aria.total.replace("iB", "B")}" else Progress.sizeText(pct, total)
                publish(if (stage == Stages.WORK) 100f else pct, speed, eta, size, force)
            })
            val end = SystemClock.elapsedRealtime() - tStart
            if (tDl == 0L) tDl = end else tWork = end - tDl
        }

        try {
            try {
                try {
                    grab(opts)
                } catch (e: Exception) {
                    // aria2c couldn't handle this site: retry with yt-dlp's own downloader
                    val raw = (e.message ?: e.toString()).lowercase()
                    if (isStopped || !multi || !("aria2" in raw || "external downloader" in raw)) throw e
                    multi = false
                    timing("aria2c failed, retrying with native downloader")
                    tmp.listFiles()?.forEach { it.deleteRecursively() }
                    grab(opts)
                }
            } catch (e: Exception) {
                // Subtitles failed (e.g. YouTube refused them): don't lose the video — download it without subtitles
                if (isStopped || opts.subLang.isEmpty() || !Errors.isSubtitleError(e.message ?: e.toString())) throw e
                opts = opts.copy(subLang = "")
                subsDropped = true
                status = "Subtitles unavailable, downloading video…"
                tmp.listFiles()?.forEach { it.deleteRecursively() }
                grab(opts)
            }
        } catch (e: Exception) {
            if (isStopped) {
                tmp.deleteRecursively()
                return Result.failure(workDataOf(K_ERROR to "Cancelled"))
            }
            val raw = e.message ?: e.toString()
            // A playlist with a few broken items still counts if something was downloaded
            val partialPlaylist = playlist && tmp.walkTopDown().any {
                it.isFile && it.extension.lowercase() !in setOf("part", "ytdl")
            }
            if (!partialPlaylist) {
                tmp.deleteRecursively()
                return fail(Errors.friendly(raw), raw)
            }
        }

        stage = Stages.WORK
        val tPost0 = SystemClock.elapsedRealtime()
        // Trim: keep only the chosen part of each downloaded file
        if (opts.trimmed && !playlist) {
            status = "Trimming…"; publish(100f, "", 0, "", true)
            val media = tmp.walkTopDown().filter { it.isFile && Saver.mimeOf(it.extension).let { m -> m.startsWith("video") || m.startsWith("audio") } }.toList()
            for (f in media) {
                val err = withContext(Dispatchers.IO) { Trimmer.trim(ctx, f, opts.trimStart, opts.trimEnd) }
                if (err != null) {
                    tmp.deleteRecursively()
                    return fail("Couldn't trim this file. Try again without trimming.", err)
                }
            }
        }
        // Split by chapters: keep the chapter files, drop the full-length original
        if (opts.splitChapters) {
            val media = tmp.walkTopDown().filter { it.isFile && Saver.mimeOf(it.extension).let { m -> m.startsWith("video") || m.startsWith("audio") } }.toList()
            if (media.any { it.name.startsWith(DownloadArgs.CHAPTER_PREFIX) }) {
                media.filterNot { it.name.startsWith(DownloadArgs.CHAPTER_PREFIX) }.forEach { it.delete() }
            }
        }
        val tSave0 = SystemClock.elapsedRealtime()
        val audioOnly = tmp.walkTopDown().filter { it.isFile }.all { Saver.mimeOf(it.extension).let { m -> !m.startsWith("video") } }
        stage = Stages.SAVE
        status = "Saving to ${if (audioOnly) "Music/Grabbit" else "Movies/Grabbit"}…"
        publish(0f, "", 0, "", true)
        val saved = try {
            Saver.saveAll(ctx, tmp, title, site, thumb, url, keepSubs = opts.keepSubFiles, name = opts.name) { d, t ->
                publish(d * 100f / t, "", 0, "${Progress.bytes(d)} of ${Progress.bytes(t)}", false)
            }
        } catch (e: Exception) {
            return fail("Couldn't save the file: ${e.message ?: e}", e.toString())
        } finally {
            tmp.deleteRecursively()
        }
        if (saved.isEmpty()) return fail("Download finished but no file was produced. Try another format.", "")
        val now = SystemClock.elapsedRealtime()
        timing("site=$site multi=${multi && Engine.aria2Ready && Prefs.fasterDownloads} bytes=${saved.sumOf { it.size }} download=${tDl}ms processing=${tWork + (tSave0 - tPost0)}ms save=${now - tSave0}ms total=${now - t0}ms")

        Library.add(saved)
        val where = if (saved.first().isAudio) "Music/Grabbit" else "Movies/Grabbit"
        Notifs.done(ctx, nid + 1, title, true,
            (if (saved.size > 1) "${saved.size} files saved to $where" else "Saved to $where") +
                (if (subsDropped) " · without subtitles (the site refused them)" else ""), saved.singleOrNull())
        return Result.success()
    }
}
