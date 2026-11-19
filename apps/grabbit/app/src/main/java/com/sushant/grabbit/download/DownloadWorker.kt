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
        const val K_ERROR = "error"
        const val K_RAW = "raw"
        const val P_PCT = "p"
        const val P_STATUS = "status"
        const val P_SPEED = "speed"
        const val P_ETA = "eta"
        private val SPEED = Regex("""at\s+~?\s*([\d.]+\s*[KMG]i?B/s)""")
        private val ITEM = Regex("""Downloading item (\d+) of (\d+)""")

        fun buildRequest(ctx: Context, url: String, quality: String, playlist: Boolean, dir: File): YoutubeDLRequest {
            val r = YoutubeDLRequest(url)
            r.addOption("--no-mtime")
            r.addOption("--no-warnings")
            r.addOption(if (playlist) "--yes-playlist" else "--no-playlist")
            if (playlist) r.addOption("--ignore-errors")
            val template = if (playlist) "%(playlist_index)03d - %(title).90B [%(id)s].%(ext)s"
            else "%(title).120B [%(id)s].%(ext)s"
            r.addOption("-o", File(dir, template).absolutePath)
            if (quality == "audio") {
                r.addOption("-f", "ba/b")
                r.addOption("-x")
                r.addOption("--audio-format", "mp3")
                r.addOption("--audio-quality", "0")
                r.addOption("--embed-metadata")
                if (Prefs.embedThumb) {
                    r.addOption("--embed-thumbnail")
                    r.addOption("--convert-thumbnails", "jpg")
                }
            } else {
                val h = quality.removePrefix("h").toIntOrNull()
                r.addOption("-f", "bv*+ba/b")
                // Prefer H.264 + AAC so the file plays in every gallery/player
                r.addOption("-S", if (h != null) "res:$h,vcodec:h264,acodec:aac" else "res,vcodec:h264,acodec:aac")
                r.addOption("--merge-output-format", "mp4")
                r.addOption("--embed-metadata")
                if (Prefs.embedSubs) {
                    r.addOption("--embed-subs")
                    r.addOption("--sub-langs", "en.*,hi.*")
                }
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
        val req = buildRequest(ctx, url, quality, playlist, tmp)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        var last = 0L
        var status = if (quality == "audio") "Downloading audio…" else "Downloading…"

        try {
            withContext(Dispatchers.IO) {
                YoutubeDL.getInstance().execute(req, processId = id.toString(), callback = { p, eta, line ->
                    when {
                        line.contains("[Merger]") -> status = "Merging video and audio…"
                        line.contains("[ExtractAudio]") -> status = "Converting to MP3…"
                        line.contains("[EmbedThumbnail]") || line.contains("[Metadata]") -> status = "Finishing…"
                        else -> ITEM.find(line)?.let { status = "Downloading ${it.groupValues[1]} of ${it.groupValues[2]}…" }
                    }
                    val now = SystemClock.elapsedRealtime()
                    if (now - last >= 600) {
                        last = now
                        val pct = p.coerceIn(0f, 100f)
                        val speed = SPEED.find(line)?.groupValues?.get(1) ?: ""
                        setProgressAsync(workDataOf(P_PCT to pct, P_STATUS to status, P_SPEED to speed, P_ETA to eta))
                        val text = buildString {
                            append(status)
                            if (pct > 0) append("  ${pct.toInt()}%")
                            if (speed.isNotEmpty()) append("  ·  $speed")
                        }
                        nm.notify(nid, Notifs.progress(ctx, title, text, pct.toInt(), p <= 0f, id))
                    }
                })
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
        val saved = try {
            Saver.saveAll(ctx, tmp, title, site, thumb, url)
        } catch (e: Exception) {
            return fail("Couldn't save the file: ${e.message ?: e}", e.toString())
        } finally {
            tmp.deleteRecursively()
        }
        if (saved.isEmpty()) return fail("Download finished but no file was produced. Try another format.", "")

        Library.add(saved)
        val where = if (saved.first().isAudio) "Music/Grabbit" else "Movies/Grabbit"
        Notifs.done(ctx, nid + 1, title, true,
            if (saved.size > 1) "${saved.size} files saved to $where" else "Saved to $where")
        return Result.success()
    }
}
