package com.sushant.saveo.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(
    tableName = "saved_items",
    indices = [Index(value = ["hash"], unique = true), Index(value = ["matchKey"])],
)
data class SavedItem(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** SHA-256 of the file content: the same status is never saved twice. */
    val hash: String,
    /** name|size of the original status file, to show the tick on the Statuses tab. */
    val matchKey: String,
    /** MediaStore content URI of the copy in Pictures/Saveo or Movies/Saveo. */
    val mediaUri: String,
    val kind: String,
    val mime: String,
    val source: String,
    val originalName: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val statusTime: Long,
    val savedAt: Long,
    /** Set while the item sits in the recycle bin. */
    val deletedAt: Long? = null,
    /** Private copy kept while in the bin (so it can be restored). */
    val binPath: String? = null,
    /** true = in the phone Gallery (mediaUri is a MediaStore URI); false = hidden in Saveo's private vault (file:// URI). */
    val visible: Boolean = true,
) {
    val mediaKind: MediaKind get() = if (kind == MediaKind.VIDEO.name) MediaKind.VIDEO else MediaKind.IMAGE
    val sourceEnum: Source get() = Source.entries.firstOrNull { it.name == source } ?: Source.WHATSAPP
}

@Entity(tableName = "seen")
data class Seen(@PrimaryKey val matchKey: String, val firstSeen: Long)

@Dao
interface SavedDao {
    @Query("SELECT * FROM saved_items WHERE deletedAt IS NULL ORDER BY savedAt DESC, id DESC")
    fun observeSaved(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_items WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun observeBin(): Flow<List<SavedItem>>

    @Query("SELECT * FROM saved_items WHERE deletedAt IS NULL")
    suspend fun allSaved(): List<SavedItem>

    @Query("SELECT * FROM saved_items WHERE hash = :hash LIMIT 1")
    suspend fun byHash(hash: String): SavedItem?

    @Query("SELECT * FROM saved_items WHERE id = :id")
    suspend fun byId(id: Long): SavedItem?

    @Query("SELECT * FROM saved_items WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun binOlderThan(before: Long): List<SavedItem>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(item: SavedItem): Long

    @Update
    suspend fun update(item: SavedItem)

    @Query("DELETE FROM saved_items WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT id FROM saved_items WHERE deletedAt IS NULL AND visible = 0")
    suspend fun hiddenIds(): List<Long>

    @Query("SELECT COUNT(*) FROM saved_items WHERE deletedAt IS NULL")
    suspend fun savedCount(): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun markSeen(items: List<Seen>)

    @Query("SELECT matchKey FROM seen")
    suspend fun seenKeys(): List<String>

    @Query("SELECT COUNT(*) FROM seen")
    suspend fun seenCount(): Int
}

@Database(entities = [SavedItem::class, Seen::class], version = 2, exportSchema = false)
abstract class SaveoDb : RoomDatabase() {
    abstract fun dao(): SavedDao

    companion object {
        fun create(ctx: Context, inMemory: Boolean = false): SaveoDb =
            if (inMemory) Room.inMemoryDatabaseBuilder(ctx, SaveoDb::class.java).build()
            else Room.databaseBuilder(ctx, SaveoDb::class.java, "saveo.db").addMigrations(MIGRATION_1_2).build()

        /** v1.1.0: hidden saves. Everything saved with 1.0.0 is already in the Gallery, so it stays visible. */
        val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE saved_items ADD COLUMN visible INTEGER NOT NULL DEFAULT 1")
            }
        }
    }
}
