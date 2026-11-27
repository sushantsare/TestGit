package com.sushant.pageone.data

import android.content.Context
import androidx.room.*
import kotlinx.coroutines.flow.Flow

/** One PDF found on the phone (from the MediaStore scan). */
@Entity(tableName = "pdfs", indices = [Index("folder"), Index("fingerprint")])
data class PdfEntity(
    @PrimaryKey val path: String,
    val name: String,
    val folder: String,          // parent directory path
    val size: Long,
    val modified: Long,          // epoch millis
    val pages: Int = 0,          // 0 = not read yet
    val fingerprint: String = "",
    val mediaId: Long = 0,
)

/** Recently opened PDFs, keyed by content fingerprint so copies of the same file share one entry. */
@Entity(tableName = "recents")
data class RecentEntity(
    @PrimaryKey val fingerprint: String,
    val name: String,
    val uri: String,             // content:// or file path the file was last opened from
    val path: String?,           // real file path when known
    val pages: Int,
    val openedAt: Long,
)

@Entity(tableName = "stars")
data class StarEntity(
    @PrimaryKey val fingerprint: String,
    val name: String,
    val uri: String,
    val path: String?,
    val pages: Int,
    val sort: Int,
)

/** Where you stopped reading (shared by every copy of the same PDF). */
@Entity(tableName = "positions")
data class PositionEntity(
    @PrimaryKey val fingerprint: String,
    val page: Int,
    val zoom: Float,
    val offset: Int,
    val updatedAt: Long,
)

@Entity(tableName = "bookmarks", primaryKeys = ["fingerprint", "page"])
data class BookmarkEntity(val fingerprint: String, val page: Int, val label: String, val createdAt: Long)

@Entity(tableName = "search_history")
data class SearchHistoryEntity(@PrimaryKey val query: String, val at: Long)

@Dao
interface PdfDao {
    @Query("SELECT * FROM pdfs ORDER BY name COLLATE NOCASE")
    fun all(): Flow<List<PdfEntity>>

    @Query("SELECT * FROM pdfs")
    suspend fun allOnce(): List<PdfEntity>

    @Upsert suspend fun upsert(items: List<PdfEntity>)

    @Query("DELETE FROM pdfs WHERE path IN (:paths)")
    suspend fun delete(paths: List<String>)

    @Query("UPDATE pdfs SET pages = :pages, fingerprint = :fp WHERE path = :path")
    suspend fun setInfo(path: String, pages: Int, fp: String)

    @Query("SELECT * FROM pdfs WHERE fingerprint = :fp")
    suspend fun byFingerprint(fp: String): List<PdfEntity>

    @Query("SELECT * FROM pdfs WHERE path = :path")
    suspend fun byPath(path: String): PdfEntity?
}

@Dao
interface ReadingDao {
    @Query("SELECT * FROM recents ORDER BY openedAt DESC LIMIT 300")
    fun recents(): Flow<List<RecentEntity>>

    @Upsert suspend fun upsertRecent(r: RecentEntity)

    @Query("DELETE FROM recents WHERE fingerprint = :fp")
    suspend fun deleteRecent(fp: String)

    @Query("DELETE FROM recents")
    suspend fun clearRecents()

    @Query("SELECT * FROM recents ORDER BY openedAt DESC LIMIT 1")
    suspend fun lastRecent(): RecentEntity?

    @Query("SELECT * FROM stars ORDER BY sort")
    fun stars(): Flow<List<StarEntity>>

    @Query("SELECT * FROM stars ORDER BY sort")
    suspend fun starsOnce(): List<StarEntity>

    @Upsert suspend fun upsertStar(s: StarEntity)

    @Upsert suspend fun upsertStars(s: List<StarEntity>)

    @Query("DELETE FROM stars WHERE fingerprint = :fp")
    suspend fun deleteStar(fp: String)

    @Query("SELECT * FROM positions")
    fun positions(): Flow<List<PositionEntity>>

    @Query("SELECT * FROM positions WHERE fingerprint = :fp")
    suspend fun position(fp: String): PositionEntity?

    @Upsert suspend fun savePosition(p: PositionEntity)

    @Query("SELECT * FROM bookmarks WHERE fingerprint = :fp ORDER BY page")
    fun bookmarks(fp: String): Flow<List<BookmarkEntity>>

    @Upsert suspend fun addBookmark(b: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE fingerprint = :fp AND page = :page")
    suspend fun removeBookmark(fp: String, page: Int)

    @Query("SELECT * FROM search_history ORDER BY at DESC LIMIT 8")
    fun searchHistory(): Flow<List<SearchHistoryEntity>>

    @Upsert suspend fun addSearch(s: SearchHistoryEntity)

    @Query("DELETE FROM search_history")
    suspend fun clearSearchHistory()
}

@Database(
    entities = [PdfEntity::class, RecentEntity::class, StarEntity::class, PositionEntity::class, BookmarkEntity::class, SearchHistoryEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class PageOneDb : RoomDatabase() {
    abstract fun pdfs(): PdfDao
    abstract fun reading(): ReadingDao

    companion object {
        fun create(context: Context): PageOneDb =
            Room.databaseBuilder(context, PageOneDb::class.java, "pageone.db").build()
    }
}
