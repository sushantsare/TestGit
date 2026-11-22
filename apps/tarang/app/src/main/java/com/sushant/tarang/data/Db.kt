package com.sushant.tarang.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One audio file found by the MediaStore scan. id = MediaStore _ID. */
@Entity(tableName = "songs", indices = [Index("folderPath"), Index("albumId")])
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val durationMs: Long,
    val sizeBytes: Long,
    val mime: String,
    val path: String,          // full file path when known (Android gives it for media files), else ""
    val folderPath: String,    // e.g. "Music/Bollywood"
    val folderName: String,    // e.g. "Bollywood"
    val fileName: String,
    val dateAdded: Long,       // seconds
    val year: Int,
    val track: Int,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"], indices = [Index("songId")])
data class PlaylistSongEntity(val playlistId: Long, val songId: Long, val position: Int)

@Entity(tableName = "likes")
data class LikeEntity(@PrimaryKey val songId: Long, val likedAt: Long = System.currentTimeMillis())

/** A listening session of one song. counted = listened for at least 30 seconds (the stats rule). */
@Entity(tableName = "plays", indices = [Index("songId"), Index("startedAt")])
data class PlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: Long,
    val startedAt: Long,
    val listenedMs: Long,
    val counted: Boolean,
)

/** Lyrics the user typed, pasted or imported from a .lrc file (kept in the app). */
@Entity(tableName = "lyrics")
data class LyricsEntity(@PrimaryKey val songId: Long, val text: String, val source: String, val updatedAt: Long = System.currentTimeMillis())

/** One of the three saved queues. ids = comma-separated song ids. */
@Entity(tableName = "queues")
data class QueueEntity(
    @PrimaryKey val slot: Int,
    val ids: String,
    val index: Int,
    val positionMs: Long,
    val contextLabel: String,
    val updatedAt: Long = System.currentTimeMillis(),
)

data class PlayCount(val songId: Long, val plays: Int, val lastPlayed: Long)

@Dao
interface LibraryDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE")
    fun songs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun allSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: Long): SongEntity?

    @Upsert suspend fun upsertSongs(list: List<SongEntity>)
    @Query("DELETE FROM songs WHERE id IN (:ids)") suspend fun deleteSongs(ids: List<Long>)
    @Query("SELECT COUNT(*) FROM songs") suspend fun songCount(): Int

    @Transaction
    suspend fun replaceLibrary(found: List<SongEntity>) {
        val keep = found.map { it.id }.toHashSet()
        val gone = allSongs().map { it.id }.filter { it !in keep }
        if (gone.isNotEmpty()) gone.chunked(500).forEach { deleteSongs(it) }
        found.chunked(500).forEach { upsertSongs(it) }
    }

    // ---- likes
    @Query("SELECT songId FROM likes ORDER BY likedAt DESC") fun likedIds(): Flow<List<Long>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun like(l: LikeEntity)
    @Query("DELETE FROM likes WHERE songId = :id") suspend fun unlike(id: Long)
    @Query("SELECT COUNT(*) FROM likes WHERE songId = :id") suspend fun isLiked(id: Long): Int

    // ---- playlists
    @Query("SELECT * FROM playlists ORDER BY name COLLATE NOCASE") fun playlists(): Flow<List<PlaylistEntity>>
    @Insert suspend fun insertPlaylist(p: PlaylistEntity): Long
    @Query("UPDATE playlists SET name = :name WHERE id = :id") suspend fun renamePlaylist(id: Long, name: String)
    @Query("DELETE FROM playlists WHERE id = :id") suspend fun deletePlaylistRow(id: Long)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :id") suspend fun clearPlaylist(id: Long)
    @Query("SELECT * FROM playlist_songs ORDER BY position") fun playlistSongs(): Flow<List<PlaylistSongEntity>>
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :id ORDER BY position") suspend fun playlistRows(id: Long): List<PlaylistSongEntity>
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun addToPlaylist(r: PlaylistSongEntity)
    @Query("DELETE FROM playlist_songs WHERE playlistId = :pid AND songId = :sid") suspend fun removeFromPlaylist(pid: Long, sid: Long)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putPlaylistRows(rows: List<PlaylistSongEntity>)
    @Query("DELETE FROM playlist_songs WHERE songId = :sid") suspend fun removeSongEverywhere(sid: Long)

    // ---- plays (stats)
    @Insert suspend fun insertPlay(p: PlayEntity): Long
    @Query("UPDATE plays SET listenedMs = :ms, counted = :counted WHERE id = :id") suspend fun updatePlay(id: Long, ms: Long, counted: Boolean)
    @Query("SELECT songId, COUNT(*) AS plays, MAX(startedAt) AS lastPlayed FROM plays WHERE counted = 1 GROUP BY songId")
    fun playCounts(): Flow<List<PlayCount>>
    @Query("SELECT * FROM plays WHERE startedAt >= :from ORDER BY startedAt")
    fun playsSince(from: Long): Flow<List<PlayEntity>>
    @Query("DELETE FROM plays") suspend fun clearPlays()
    @Query("SELECT songId, COUNT(*) AS plays, MAX(startedAt) AS lastPlayed FROM plays WHERE counted = 1 AND startedAt >= :from GROUP BY songId")
    fun playCountsSince(from: Long): Flow<List<PlayCount>>

    // ---- lyrics
    @Query("SELECT * FROM lyrics WHERE songId = :id") suspend fun lyrics(id: Long): LyricsEntity?
    @Query("SELECT * FROM lyrics WHERE songId = :id") fun lyricsFlow(id: Long): Flow<LyricsEntity?>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putLyrics(l: LyricsEntity)
    @Query("DELETE FROM lyrics WHERE songId = :id") suspend fun deleteLyrics(id: Long)

    // ---- queues
    @Query("SELECT * FROM queues ORDER BY slot") fun queues(): Flow<List<QueueEntity>>
    @Query("SELECT * FROM queues WHERE slot = :slot") suspend fun queue(slot: Int): QueueEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun putQueue(q: QueueEntity)
}

@Database(
    entities = [SongEntity::class, PlaylistEntity::class, PlaylistSongEntity::class, LikeEntity::class,
        PlayEntity::class, LyricsEntity::class, QueueEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class TarangDb : RoomDatabase() {
    abstract fun dao(): LibraryDao

    companion object {
        // Future schema changes get real Migration objects here; destructive migration is never used,
        // so an update can never wipe playlists, likes or stats.
        val MIGRATIONS: Array<androidx.room.migration.Migration> = arrayOf()

        fun create(ctx: Context): TarangDb =
            Room.databaseBuilder(ctx, TarangDb::class.java, "tarang.db").addMigrations(*MIGRATIONS).build()
    }
}
