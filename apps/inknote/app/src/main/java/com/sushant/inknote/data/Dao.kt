package com.sushant.inknote.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class SearchHit(val noteId: Long, val pageId: Long, val pageIdx: Int, val source: String, val text: String)
data class FolderCount(val folderId: Long?, val n: Int)
data class TagWithNote(val noteId: Long, val name: String)

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL")
    fun liveNotes(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC")
    fun trash(): Flow<List<NoteEntity>>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun get(id: Long): NoteEntity?

    @Query("SELECT * FROM notes WHERE id = :id")
    fun observe(id: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes")
    suspend fun all(): List<NoteEntity>

    @Insert suspend fun insert(n: NoteEntity): Long
    @Update suspend fun update(n: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id") suspend fun delete(id: Long)

    @Query("UPDATE notes SET updatedAt = :t, thumbVersion = thumbVersion + 1 WHERE id = :id")
    suspend fun touch(id: Long, t: Long)

    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL AND deletedAt < :before")
    suspend fun expiredTrash(before: Long): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE deletedAt IS NULL AND indexedAt < updatedAt")
    suspend fun needIndex(): List<NoteEntity>

    @Query("UPDATE notes SET indexedAt = :t WHERE id = :id") suspend fun markIndexed(id: Long, t: Long)
}

@Dao
interface PageDao {
    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY idx")
    fun observe(noteId: Long): Flow<List<PageEntity>>

    @Query("SELECT * FROM pages WHERE noteId = :noteId ORDER BY idx")
    suspend fun list(noteId: Long): List<PageEntity>

    @Query("SELECT * FROM pages WHERE id = :id") suspend fun get(id: Long): PageEntity?
    @Query("SELECT * FROM pages") suspend fun all(): List<PageEntity>

    @Insert suspend fun insert(p: PageEntity): Long
    @Insert suspend fun insertAll(p: List<PageEntity>): List<Long>
    @Update suspend fun update(p: PageEntity)
    @Update suspend fun updateAll(p: List<PageEntity>)
    @Query("DELETE FROM pages WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM pages WHERE noteId = :noteId") suspend fun deleteForNote(noteId: Long)
}

@Dao
interface FolderDao {
    @Query("SELECT * FROM folders ORDER BY sort, name") fun observe(): Flow<List<FolderEntity>>
    @Query("SELECT * FROM folders") suspend fun all(): List<FolderEntity>
    @Insert suspend fun insert(f: FolderEntity): Long
    @Update suspend fun update(f: FolderEntity)
    @Query("DELETE FROM folders WHERE id = :id") suspend fun delete(id: Long)
    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :id") suspend fun unlinkNotes(id: Long)
}

@Dao
interface TagDao {
    @Query("SELECT * FROM tags ORDER BY name") fun observe(): Flow<List<TagEntity>>
    @Query("SELECT * FROM tags") suspend fun all(): List<TagEntity>
    @Query("SELECT * FROM note_tags") suspend fun allLinks(): List<NoteTagEntity>
    @Query("SELECT nt.noteId AS noteId, t.name AS name FROM note_tags nt JOIN tags t ON t.id = nt.tagId")
    fun observeLinks(): Flow<List<TagWithNote>>
    @Query("SELECT * FROM tags WHERE name = :name") suspend fun byName(name: String): TagEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(t: TagEntity): Long
    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(l: NoteTagEntity)
    @Query("DELETE FROM note_tags WHERE noteId = :noteId") suspend fun clearNote(noteId: Long)
    @Query("SELECT t.* FROM tags t JOIN note_tags nt ON nt.tagId = t.id WHERE nt.noteId = :noteId ORDER BY t.name")
    suspend fun forNote(noteId: Long): List<TagEntity>
    @Query("DELETE FROM tags WHERE id NOT IN (SELECT tagId FROM note_tags)") suspend fun dropUnused()
}

@Dao
interface RecordingDao {
    @Query("SELECT * FROM recordings WHERE noteId = :noteId ORDER BY createdAt")
    fun observe(noteId: Long): Flow<List<RecordingEntity>>
    @Query("SELECT * FROM recordings WHERE noteId = :noteId ORDER BY createdAt")
    suspend fun list(noteId: Long): List<RecordingEntity>
    @Query("SELECT * FROM recordings") suspend fun all(): List<RecordingEntity>
    @Insert suspend fun insert(r: RecordingEntity): Long
    @Update suspend fun update(r: RecordingEntity)
    @Query("DELETE FROM recordings WHERE id = :id") suspend fun delete(id: Long)
    @Query("DELETE FROM recordings WHERE noteId = :noteId") suspend fun deleteForNote(noteId: Long)
}

@Dao
interface SearchDao {
    @Query("DELETE FROM search_fts WHERE noteId = :noteId") suspend fun clear(noteId: String)
    @Insert suspend fun insert(rows: List<SearchFts>)
    @Query("SELECT CAST(noteId AS INTEGER) AS noteId, CAST(pageId AS INTEGER) AS pageId, CAST(pageIdx AS INTEGER) AS pageIdx, source, text FROM search_fts WHERE search_fts MATCH :q LIMIT 300")
    suspend fun match(q: String): List<SearchHit>
}
