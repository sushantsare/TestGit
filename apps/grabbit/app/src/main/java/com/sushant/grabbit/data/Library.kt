package com.sushant.grabbit.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class SavedItem(
    val id: String,
    val title: String,
    val site: String,
    val thumb: String,
    val uri: String,
    val fileName: String,
    val mime: String,
    val size: Long,
    val time: Long,
    val sourceUrl: String,
    val path: String?,
) {
    val isAudio get() = mime.startsWith("audio")
}

/** List of finished downloads, stored as JSON in app storage. */
object Library {
    private lateinit var file: File
    val items = MutableStateFlow<List<SavedItem>>(emptyList())

    fun init(ctx: Context) {
        file = File(ctx.filesDir, "library.json")
        items.value = load()
    }

    @Synchronized
    fun add(list: List<SavedItem>) {
        items.value = list + items.value
        persist()
    }

    @Synchronized
    fun update(item: SavedItem) {
        items.value = items.value.map { if (it.id == item.id) item else it }
        persist()
    }

    @Synchronized
    fun remove(id: String) {
        items.value = items.value.filterNot { it.id == id }
        persist()
    }

    private fun load(): List<SavedItem> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            SavedItem(
                id = o.getString("id"),
                title = o.optString("title"),
                site = o.optString("site"),
                thumb = o.optString("thumb"),
                uri = o.getString("uri"),
                fileName = o.optString("fileName"),
                mime = o.optString("mime"),
                size = o.optLong("size"),
                time = o.optLong("time"),
                sourceUrl = o.optString("sourceUrl"),
                path = if (o.has("path")) o.optString("path") else null,
            )
        }
    }.getOrDefault(emptyList())

    private fun persist() {
        val arr = JSONArray()
        items.value.forEach { s ->
            arr.put(JSONObject().apply {
                put("id", s.id); put("title", s.title); put("site", s.site); put("thumb", s.thumb)
                put("uri", s.uri); put("fileName", s.fileName); put("mime", s.mime); put("size", s.size)
                put("time", s.time); put("sourceUrl", s.sourceUrl)
                if (s.path != null) put("path", s.path)
            })
        }
        runCatching { file.writeText(arr.toString()) }
    }
}
