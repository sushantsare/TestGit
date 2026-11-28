package com.sushant.grabbit.data

import android.content.Context
import androidx.work.WorkManager
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.download.Downloads
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** A playlist download in progress: the videos still waiting their turn. */
data class PlaylistRun(
    val grp: String,
    val title: String,
    val thumb: String,
    val site: String,
    val total: Int,
    val pending: List<Downloads.Job>,
    val paused: Boolean = false,
    val time: Long = System.currentTimeMillis(),
)

/**
 * Playlist downloads: every video is its own download, but only [PARALLEL] run at once.
 * The rest wait here (saved to disk, so they survive closing the app) and start one by one
 * as earlier ones finish — a failed video never stops the rest.
 */
object PlaylistQueue {
    const val PARALLEL = 2
    private lateinit var dir: File
    val runs = MutableStateFlow<Map<String, PlaylistRun>>(emptyMap())

    fun init(ctx: Context) {
        dir = File(ctx.filesDir, "playlists").apply { mkdirs() }
        runs.value = dir.listFiles { f -> f.extension == "json" }.orEmpty()
            .mapNotNull { runCatching { read(JSONObject(it.readText())) }.getOrNull() }.associateBy { it.grp }
        Archive.init(ctx)
    }

    fun tagOf(grp: String) = "grp:$grp"

    /** Starts a playlist download; returns its run key. */
    fun start(ctx: Context, title: String, thumb: String, site: String, jobs: List<Downloads.Job>): String {
        val grp = UUID.randomUUID().toString().take(12)
        val withGroup = jobs.map { it.copy(opts = it.opts.copy(grp = grp)) }
        put(PlaylistRun(grp, title, thumb, site, jobs.size, withGroup))
        fill(ctx, grp)
        return grp
    }

    /** Starts waiting videos until [PARALLEL] are running. [exclude] = a job that is just ending. */
    @Synchronized
    fun fill(ctx: Context, grp: String, exclude: UUID? = null) {
        val run = runs.value[grp] ?: return
        if (run.paused) return
        val active = runCatching {
            WorkManager.getInstance(ctx).getWorkInfosByTag(tagOf(grp)).get()
                .count { !it.state.isFinished && it.id != exclude }
        }.getOrDefault(0)
        var free = PARALLEL - active
        var pending = run.pending
        while (free > 0 && pending.isNotEmpty()) {
            Downloads.enqueue(ctx, pending.first(), keepFolder = false)
            pending = pending.drop(1)
            free--
        }
        if (pending.isEmpty() && active == 0 && run.pending.isEmpty()) remove(grp) else put(run.copy(pending = pending))
    }

    /** Called by every playlist job when it ends (saved, failed or stopped). */
    fun afterJob(ctx: Context, grp: String, self: UUID) = fill(ctx, grp, exclude = self)

    fun pause(ctx: Context, grp: String) {
        runs.value[grp]?.let { put(it.copy(paused = true)) }
        runCatching {
            WorkManager.getInstance(ctx).getWorkInfosByTag(tagOf(grp)).get().filter { !it.state.isFinished }
                .forEach { Downloads.pause(ctx, it) }
        }
    }

    fun resume(ctx: Context, grp: String) {
        // Paused videos go back to the front of the line, then the line starts again
        val paused = Paused.items.value.filter { it.job.opts.grp == grp }
        paused.forEach { Paused.remove(it.job.key) }
        val run = runs.value[grp] ?: PlaylistRun(grp, paused.firstOrNull()?.job?.opts?.gt.orEmpty(), "", "", paused.size, emptyList())
        put(run.copy(paused = false, pending = paused.map { it.job } + run.pending))
        fill(ctx, grp)
    }

    /** Puts failed videos back in line. */
    fun retry(ctx: Context, grp: String, jobs: List<Downloads.Job>) {
        if (jobs.isEmpty()) return
        val run = runs.value[grp] ?: PlaylistRun(grp, jobs.first().opts.gt, jobs.first().thumb, jobs.first().site, jobs.size, emptyList())
        put(run.copy(pending = run.pending + jobs, paused = false))
        fill(ctx, grp)
    }

    fun cancel(ctx: Context, grp: String) {
        remove(grp)
        Paused.items.value.filter { it.job.opts.grp == grp }.forEach { Downloads.discard(ctx, it) }
        runCatching {
            WorkManager.getInstance(ctx).getWorkInfosByTag(tagOf(grp)).get().filter { !it.state.isFinished }
                .forEach { Downloads.cancel(ctx, it.id) }
        }
    }

    @Synchronized
    private fun put(r: PlaylistRun) {
        runs.value = runs.value + (r.grp to r)
        runCatching { File(dir, "${r.grp}.json").writeText(write(r).toString()) }
    }

    @Synchronized
    private fun remove(grp: String) {
        runs.value = runs.value - grp
        File(dir, "$grp.json").delete()
    }

    private fun write(r: PlaylistRun) = JSONObject().apply {
        put("grp", r.grp); put("title", r.title); put("thumb", r.thumb); put("site", r.site)
        put("total", r.total); put("paused", r.paused); put("time", r.time)
        put("pending", JSONArray().apply { r.pending.forEach { put(jobJson(it)) } })
    }

    private fun read(o: JSONObject): PlaylistRun {
        val arr = o.optJSONArray("pending") ?: JSONArray()
        return PlaylistRun(
            o.getString("grp"), o.optString("title"), o.optString("thumb"), o.optString("site"), o.optInt("total"),
            (0 until arr.length()).map { jobOf(arr.getJSONObject(it)) }, o.optBoolean("paused"), o.optLong("time"),
        )
    }

    fun jobJson(j: Downloads.Job) = JSONObject().apply {
        put("url", j.url); put("title", j.title); put("thumb", j.thumb); put("site", j.site)
        put("quality", j.quality); put("opts", j.opts.encode()); put("key", j.key)
    }

    fun jobOf(o: JSONObject) = Downloads.Job(
        url = o.getString("url"), title = o.optString("title"), thumb = o.optString("thumb"), site = o.optString("site"),
        quality = o.optString("quality", "best"), playlist = false, opts = DlOpts.decode(o.optString("opts")), key = o.optString("key"),
    )

    /** Which videos of each playlist were already downloaded ("Only new" and "Downloaded ✓"). */
    object Archive {
        private lateinit var file: File
        private val map = HashMap<String, MutableSet<String>>()

        fun init(ctx: Context) {
            file = File(ctx.filesDir, "playlist_archive.json")
            map.clear()
            runCatching {
                val o = JSONObject(file.readText())
                o.keys().forEach { k ->
                    val a = o.getJSONArray(k)
                    map[k] = (0 until a.length()).map { a.getString(it) }.toMutableSet()
                }
            }
        }

        @Synchronized
        fun has(playlist: String, video: String) = video.isNotBlank() && map[playlist]?.contains(video) == true

        @Synchronized
        fun add(playlist: String, video: String) {
            if (playlist.isBlank() || video.isBlank()) return
            if (map.getOrPut(playlist) { mutableSetOf() }.add(video)) {
                val o = JSONObject()
                map.forEach { (k, v) -> o.put(k, JSONArray(v.toList())) }
                runCatching { file.writeText(o.toString()) }
            }
        }
    }
}

/** Playlist picks: ranges like "1-10, 15, -3" and the order options (pure, unit-tested). */
object PlaylistPick {
    /** Numbers in [spec] (1-based, "-3" = third from the end of [total]). Null if the text can't be read. */
    fun range(spec: String, total: Int): Set<Int>? {
        val out = sortedSetOf<Int>()
        val parts = spec.split(',', ' ').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.isEmpty()) return null
        fun num(s: String): Int? = s.toIntOrNull()?.let { if (it < 0) total + 1 + it else it }
        for (p in parts) {
            val m = Regex("""^(-?\d+)?\s*[-:]\s*(-?\d+)?$""").find(p.replace("–", "-"))
            if (p.toIntOrNull() != null) { num(p)?.let { out += it }; continue }
            if (m == null) return null
            val a = m.groupValues[1].takeIf { it.isNotEmpty() }?.let(::num) ?: 1
            val b = m.groupValues[2].takeIf { it.isNotEmpty() }?.let(::num) ?: total
            if (a <= b) for (i in a..b) out += i else for (i in b..a) out += i
        }
        return out.filter { it in 1..total }.toSet()
    }

    const val IN_ORDER = 0
    const val REVERSED = 1
    const val NEWEST = 2
    const val RANDOM = 3

    fun <T> order(list: List<T>, mode: Int, seed: Long, index: (T) -> Int, time: (T) -> Long): List<T> = when (mode) {
        REVERSED -> list.sortedByDescending(index)
        NEWEST -> if (list.all { time(it) > 0 }) list.sortedByDescending(time) else list.sortedByDescending(index)
        RANDOM -> list.shuffled(java.util.Random(seed))
        else -> list.sortedBy(index)
    }

    /** "04 - Title": numbers padded to the playlist size. */
    fun fileName(index: Int, total: Int, title: String) =
        "${index.toString().padStart(if (total >= 100) 3 else 2, '0')} - $title"
}
