package com.sushant.grabbit.data

import android.content.Context
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.download.Downloads
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A paused download: the job (to start it again), how far it got, and whether its part file can be continued. */
data class PausedItem(
    val job: Downloads.Job,
    val pct: Float,
    val size: String,
    val canContinue: Boolean,
    val time: Long,
)

/** Paused downloads, kept in app storage so they survive closing the app and restarting the phone. */
object Paused {
    private lateinit var file: File
    val items = MutableStateFlow<List<PausedItem>>(emptyList())

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "paused.json")
        items.value = load()
    }

    fun has(key: String) = items.value.any { it.job.key == key }

    @Synchronized
    fun add(p: PausedItem) {
        items.value = listOf(p) + items.value.filterNot { it.job.key == p.job.key }
        persist()
    }

    @Synchronized
    fun remove(key: String) {
        items.value = items.value.filterNot { it.job.key == key }
        persist()
    }

    private fun load(): List<PausedItem> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            PausedItem(
                job = Downloads.Job(
                    url = o.getString("url"), title = o.optString("title"), thumb = o.optString("thumb"),
                    site = o.optString("site"), quality = o.optString("quality", "best"), playlist = o.optBoolean("playlist"),
                    opts = DlOpts.decode(o.optString("opts")), key = o.getString("key"), gallery = o.optBoolean("gal"),
                ),
                pct = o.optDouble("pct", 0.0).toFloat(), size = o.optString("size"),
                canContinue = o.optBoolean("cont", true), time = o.optLong("time"),
            )
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val arr = JSONArray()
        items.value.forEach { p ->
            arr.put(JSONObject().apply {
                put("url", p.job.url); put("title", p.job.title); put("thumb", p.job.thumb); put("site", p.job.site)
                put("quality", p.job.quality); put("playlist", p.job.playlist); put("opts", p.job.opts.encode())
                put("key", p.job.key); put("pct", p.pct.toDouble()); put("size", p.size); put("cont", p.canContinue)
                put("time", p.time); put("gal", p.job.gallery)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }
}
