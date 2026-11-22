package com.sushant.tarang.data

import android.content.ContentUris
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** Loads album covers from the song files (embedded art) with a memory cache. Misses are cached too. */
class Covers(private val ctx: Context) {
    private object None
    private val cache = object : LruCache<String, Any>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Any) = if (value is Bitmap) value.byteCount else 64
    }
    private val seeds = HashMap<Long, Int?>()
    private val gate = Semaphore(4)

    fun cached(songId: Long, px: Int): Bitmap? = cache.get(key(songId, px)) as? Bitmap
    private fun key(id: Long, px: Int) = "$id@${bucket(px)}"
    private fun bucket(px: Int) = when { px <= 160 -> 160; px <= 400 -> 400; else -> 800 }

    suspend fun load(song: Song, px: Int): Bitmap? {
        val k = key(song.id, px)
        cache.get(k)?.let { return it as? Bitmap }
        val b = gate.withPermit { withContext(Dispatchers.IO) { decode(song, bucket(px)) } }
        cache.put(k, b ?: None)
        return b
    }

    private fun decode(song: Song, size: Int): Bitmap? {
        if (Build.VERSION.SDK_INT >= 29) {
            runCatching { return ctx.contentResolver.loadThumbnail(song.uri, Size(size, size), null) }
        }
        runCatching {
            val r = MediaMetadataRetriever()
            try {
                r.setDataSource(ctx, song.uri)
                val bytes = r.embeddedPicture
                if (bytes != null) return sampled(bytes, size)
            } finally { runCatching { r.release() } }
        }
        runCatching {
            val art = ContentUris.withAppendedId(Uri.parse("content://media/external/audio/albumart"), song.albumId)
            ctx.contentResolver.openInputStream(art)?.use { return BitmapFactory.decodeStream(it) }
        }
        return null
    }

    private fun sampled(bytes: ByteArray, size: Int): Bitmap? {
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, o)
        var s = 1
        while (o.outWidth / (s * 2) >= size && o.outHeight / (s * 2) >= size) s *= 2
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = s })
    }

    /** The colour the player builds its scheme from: vibrant colour of the cover, or null if there is no cover. */
    suspend fun seed(song: Song): Int? {
        synchronized(seeds) { if (seeds.containsKey(song.id)) return seeds[song.id] }
        val b = load(song, 160)
        val seed = b?.let { bmp ->
            withContext(Dispatchers.Default) {
                val p = Palette.from(bmp).maximumColorCount(16).generate()
                (p.vibrantSwatch ?: p.dominantSwatch ?: p.mutedSwatch)?.rgb
            }
        }
        synchronized(seeds) { seeds[song.id] = seed }
        return seed
    }

    fun forget(songId: Long) {
        listOf(160, 400, 800).forEach { cache.remove(key(songId, it)) }
        synchronized(seeds) { seeds.remove(songId) }
    }
}
