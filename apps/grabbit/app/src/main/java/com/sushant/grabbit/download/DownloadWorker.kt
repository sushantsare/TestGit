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

        fun buildRequest(ctx: Context, url: String, quality: String, playlist: Boolean, dir: File, opts: DlOpts): YoutubeDLRequest {
            val r = YoutubeDLRequest(url)
            val g = GlobalOpts(Prefs.videoFormat, Prefs.fasterDownloads, Prefs.embedThumb, Prefs.sponsorCats)
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

    override suspend fun doWork(): Result {
        val ctx = applicationContext
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

        suspend fun grab(o: DlOpts) = withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(buildRequest(ctx, source, quality, playlist, tmp, o), processId = id.toString(), callback = { p, eta, line ->
                    when {
                        line.contains("[Merger]") -> status = "Merging video and audio…"
                        line.contains("[ExtractAudio]") -> status = "Converting audio…"
                        line.contains("[EmbedThumbnail]") || line.contains("[Metadata]") -> status = "Finishing…"
                        else -> ITEM.find(line)?.let { status = "Downloading ${it.groupValues[1]} of ${it.groupValues[2]}…" }
                    }
                    Progress.totalBytes(line)?.let { total = it }
                    val now = SystemClock.elapsedRealtime()
                    if (now - last >= 600) {
                        last = now
                        val pct = p.coerceIn(0f, 100f)
                        val speed = (SPEED.find(line)?.groupValues?.get(1) ?: "").replace("iB", "B")
                        val size = Progress.sizeText(pct, total)
                        setProgressAsync(workDataOf(P_PCT to pct, P_STATUS to status, P_SPEED to speed, P_ETA to eta, P_SIZE to size))
                        val text = buildString {
                            append(status)
                            if (pct > 0) append("  ${pct.toInt()}%")
                            if (size.isNotEmpty()) append("  ·  $size")
                            if (speed.isNotEmpty()) append("  ·  $speed")
                        }
                        nm.notify(nid, Notifs.progress(ctx, title, text, pct.toInt(), p <= 0f, id))
                    }
                })
        }

        try {
            try {
                grab(opts)
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

        setProgress(workDataOf(P_PCT to 100f, P_STATUS to "Saving to your phone…"))
        // Trim: keep only the chosen part of each downloaded file
        if (opts.trimmed && !playlist) {
            setProgress(workDataOf(P_PCT to 100f, P_STATUS to "Trimming…"))
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
        val saved = try {
            Saver.saveAll(ctx, tmp, title, site, thumb, url, keepSubs = opts.keepSubFiles, name = opts.name)
        } catch (e: Exception) {
            return fail("Couldn't save the file: ${e.message ?: e}", e.toString())
        } finally {
            tmp.deleteRecursively()
        }
        if (saved.isEmpty()) return fail("Download finished but no file was produced. Try another format.", "")

        Library.add(saved)
        val where = if (saved.first().isAudio) "Music/Grabbit" else "Movies/Grabbit"
        Notifs.done(ctx, nid + 1, title, true,
            (if (saved.size > 1) "${saved.size} files saved to $where" else "Saved to $where") +
                (if (subsDropped) " · without subtitles (the site refused them)" else ""), saved.singleOrNull())
        return Result.success()
    }
}
