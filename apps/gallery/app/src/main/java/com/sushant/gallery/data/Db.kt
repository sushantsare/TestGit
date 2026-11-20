package com.sushant.gallery.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

/** A photo/video moved to the Recycle Bin. The file lives in [trashPath] until restored or purged. */
@Entity(tableName = "trash")
data class TrashEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val originalPath: String,
    val trashPath: String,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val size: Long,
    val dateTaken: Long,
    val durationMs: Long,
    val wasFavourite: Boolean,
    val deletedAt: Long,
)

@Entity(tableName = "favourite")
data class FavouriteEntity(@PrimaryKey val path: String, val addedAt: Long)

/** An encrypted item in the Hidden album. Files: vault/<id>.bin and vault/<id>.thumb */
@Entity(tableName = "vault")
data class VaultEntity(
    @PrimaryKey val id: String,
    val originalPath: String,
    val name: String,
    val mime: String,
    val isVideo: Boolean,
    val size: Long,
    val dateTaken: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val addedAt: Long,
)

@Dao
interface TrashDao {
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC") fun all(): Flow<List<TrashEntity>>
    @Query("SELECT * FROM trash ORDER BY deletedAt DESC") suspend fun list(): List<TrashEntity>
    @Query("SELECT * FROM trash WHERE deletedAt < :before") suspend fun olderThan(before: Long): List<TrashEntity>
    @Insert suspend fun insert(e: TrashEntity): Long
    @Query("DELETE FROM trash WHERE id = :id") suspend fun delete(id: Long)
}

@Dao
interface FavouriteDao {
    @Query("SELECT path FROM favourite") fun paths(): Flow<List<String>>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun add(e: FavouriteEntity)
    @Query("DELETE FROM favourite WHERE path = :path") suspend fun remove(path: String)
    @Query("UPDATE favourite SET path = :to WHERE path = :from") suspend fun move(from: String, to: String)
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault ORDER BY addedAt DESC") fun all(): Flow<List<VaultEntity>>
    @Query("SELECT * FROM vault WHERE id = :id") suspend fun get(id: String): VaultEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insert(e: VaultEntity)
    @Query("DELETE FROM vault WHERE id = :id") suspend fun delete(id: String)
}

@Database(entities = [TrashEntity::class, FavouriteEntity::class, VaultEntity::class], version = 1, exportSchema = false)
abstract class GalleryDb : RoomDatabase() {
    abstract fun trash(): TrashDao
    abstract fun favourites(): FavouriteDao
    abstract fun vault(): VaultDao

    companion object {
        fun create(ctx: Context): GalleryDb = Room.databaseBuilder(ctx, GalleryDb::class.java, "gallery.db").build()
        fun inMemory(ctx: Context): GalleryDb = Room.inMemoryDatabaseBuilder(ctx, GalleryDb::class.java).build()
    }
}
