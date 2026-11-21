package com.sushant.gallery.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.Size
import androidx.exifinterface.media.ExifInterface
import coil.ImageLoader
import coil.decode.DataSource
import coil.decode.ImageSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.fetch.SourceResult
import coil.key.Keyer
import coil.request.Options
import coil.size.pxOrElse
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.data.Vault
import okio.Buffer
import java.io.File

/** Small grid thumbnail: system thumbnail cache first (fast), then a down-sampled decode of the file. */
data class Thumb(val uri: Uri, val path: String, val isVideo: Boolean, val modified: Long, val size: Long)

/** An encrypted Hidden-album picture. */
data class VaultRef(val id: String, val full: Boolean)

/** What to give Coil for an item. */
fun thumbModel(m: MediaItem): Any = when {
    m.vaultId != null -> VaultRef(m.vaultId, full = false)
    m.folder.isEmpty() -> m.uri
    else -> Thumb(m.uri, m.path, m.isVideo, m.dateModified, m.size)
}

fun fullModel(m: MediaItem): Any = when {
    m.vaultId != null -> VaultRef(m.vaultId, full = true)
    m.folder.isEmpty() -> m.uri
    else -> File(m.path)
}

class ThumbKeyer : Keyer<Thumb> {
    override fun key(data: Thumb, options: Options): String = "thumb:${data.path}:${data.modified}:${data.size}"
}

class VaultKeyer : Keyer<VaultRef> {
    override fun key(data: VaultRef, options: Options): String = "vault:${data.id}:${data.full}"
}

class ThumbFetcher(private val ctx: Context, private val data: Thumb, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val w = options.size.width.pxOrElse { 384 }.coerceIn(96, 768)
        val h = options.size.height.pxOrElse { 384 }.coerceIn(96, 768)
        var bmp: Bitmap? = null
        if (Build.VERSION.SDK_INT >= 29 && data.uri.scheme == "content") {
            bmp = runCatching { ctx.contentResolver.loadThumbnail(data.uri, Size(w, h), null) }.getOrNull()
        }
        if (bmp == null) bmp = if (data.isVideo) videoFrame(data.path) else decodeSampled(data.path, maxOf(w, h))
        bmp ?: return null
        return DrawableResult(BitmapDrawable(ctx.resources, bmp), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val ctx: Context) : Fetcher.Factory<Thumb> {
        override fun create(data: Thumb, options: Options, imageLoader: ImageLoader): Fetcher = ThumbFetcher(ctx, data, options)
    }

    companion object {
        fun videoFrame(path: String): Bitmap? = runCatching {
            val r = MediaMetadataRetriever()
            try { r.setDataSource(path); r.getFrameAtTime(1_000_000) ?: r.getFrameAtTime(0) } finally { r.release() }
        }.getOrNull()

        fun decodeSampled(path: String, target: Int): Bitmap? = runCatching {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, o)
            var s = 1
            while (o.outWidth / (s * 2) >= target && o.outHeight / (s * 2) >= target) s *= 2
            val b = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = s }) ?: return@runCatching null
            val deg = runCatching { ExifInterface(path).rotationDegrees }.getOrDefault(0)
            if (deg == 0) b else Bitmap.createBitmap(b, 0, 0, b.width, b.height, Matrix().apply { postRotate(deg.toFloat()) }, true)
        }.getOrNull()
    }
}

class VaultFetcher(private val vault: Vault, private val data: VaultRef, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult? {
        val bytes = (if (data.full) vault.fullBytes(data.id) else vault.thumbBytes(data.id)) ?: return null
        return SourceResult(
            source = ImageSource(Buffer().write(bytes), options.context),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val vault: Vault) : Fetcher.Factory<VaultRef> {
        override fun create(data: VaultRef, options: Options, imageLoader: ImageLoader): Fetcher = VaultFetcher(vault, data, options)
    }
}
