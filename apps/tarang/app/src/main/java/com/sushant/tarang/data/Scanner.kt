package com.sushant.tarang.data

import android.content.Context
import android.os.Build
import android.provider.MediaStore

/** Reads every audio file Android knows about (ringtones, alarms and notification sounds left out). */
class Scanner(private val ctx: Context) {

    fun scan(): List<SongEntity> {
        val cols = mutableListOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE, MediaStore.Audio.Media.MIME_TYPE, MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.YEAR, MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        if (Build.VERSION.SDK_INT >= 29) cols += MediaStore.Audio.Media.RELATIVE_PATH
        if (Build.VERSION.SDK_INT >= 30) cols += MediaStore.Audio.Media.ALBUM_ARTIST
        val sel = "${MediaStore.Audio.Media.IS_RINGTONE} = 0 AND ${MediaStore.Audio.Media.IS_ALARM} = 0 AND " +
            "${MediaStore.Audio.Media.IS_NOTIFICATION} = 0"
        val out = ArrayList<SongEntity>()
        ctx.contentResolver.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, cols.toTypedArray(), sel, null, null)?.use { c ->
            fun idx(n: String) = c.getColumnIndex(n)
            val iId = idx(MediaStore.Audio.Media._ID); val iTitle = idx(MediaStore.Audio.Media.TITLE)
            val iArtist = idx(MediaStore.Audio.Media.ARTIST); val iAlbum = idx(MediaStore.Audio.Media.ALBUM)
            val iAlbumId = idx(MediaStore.Audio.Media.ALBUM_ID); val iDur = idx(MediaStore.Audio.Media.DURATION)
            val iSize = idx(MediaStore.Audio.Media.SIZE); val iMime = idx(MediaStore.Audio.Media.MIME_TYPE)
            val iData = idx(MediaStore.Audio.Media.DATA); val iAdded = idx(MediaStore.Audio.Media.DATE_ADDED)
            val iYear = idx(MediaStore.Audio.Media.YEAR); val iTrack = idx(MediaStore.Audio.Media.TRACK)
            val iName = idx(MediaStore.Audio.Media.DISPLAY_NAME)
            val iRel = if (Build.VERSION.SDK_INT >= 29) idx(MediaStore.Audio.Media.RELATIVE_PATH) else -1
            val iAlbArt = if (Build.VERSION.SDK_INT >= 30) idx(MediaStore.Audio.Media.ALBUM_ARTIST) else -1
            while (c.moveToNext()) {
                val data = c.getStringOrNull(iData) ?: ""
                val name = c.getStringOrNull(iName) ?: data.substringAfterLast('/')
                val rel = (if (iRel >= 0) c.getStringOrNull(iRel) else null) ?: relativeFromData(data)
                val folderPath = rel.trim('/').ifEmpty { "Internal storage" }
                val artistRaw = c.getStringOrNull(iArtist)
                val albumRaw = c.getStringOrNull(iAlbum)
                val artist = if (artistRaw.isNullOrBlank() || artistRaw == "<unknown>") UNKNOWN_ARTIST else artistRaw.trim()
                val album = if (albumRaw.isNullOrBlank() || albumRaw == "<unknown>") UNKNOWN_ALBUM else albumRaw.trim()
                val title = c.getStringOrNull(iTitle)?.takeIf { it.isNotBlank() } ?: name.substringBeforeLast('.')
                val track = c.getIntOrZero(iTrack).let { if (it >= 1000) it % 1000 else it }
                out += SongEntity(
                    id = c.getLong(iId), title = title.trim(), artist = artist, album = album,
                    albumId = c.getLongOrZero(iAlbumId),
                    albumArtist = (if (iAlbArt >= 0) c.getStringOrNull(iAlbArt) else null)?.takeIf { it.isNotBlank() } ?: artist,
                    durationMs = c.getLongOrZero(iDur), sizeBytes = c.getLongOrZero(iSize),
                    mime = c.getStringOrNull(iMime) ?: "audio/*", path = data,
                    folderPath = folderPath, folderName = folderPath.substringAfterLast('/'), fileName = name,
                    dateAdded = c.getLongOrZero(iAdded), year = c.getIntOrZero(iYear), track = track,
                )
            }
        }
        return out
    }

    private fun relativeFromData(data: String): String {
        val dir = data.substringBeforeLast('/', "")
        val m = Regex("^/storage/(emulated/\\d+|[^/]+)/?(.*)$").find(dir) ?: return dir
        return m.groupValues[2]
    }
}

private fun android.database.Cursor.getStringOrNull(i: Int) = if (i < 0 || isNull(i)) null else getString(i)
private fun android.database.Cursor.getLongOrZero(i: Int) = if (i < 0 || isNull(i)) 0L else getLong(i)
private fun android.database.Cursor.getIntOrZero(i: Int) = if (i < 0 || isNull(i)) 0 else getInt(i)
