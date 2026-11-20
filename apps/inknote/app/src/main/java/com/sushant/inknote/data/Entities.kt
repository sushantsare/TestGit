package com.sushant.inknote.data

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.Index
import androidx.room.PrimaryKey

object Kind { const val INK = "INK"; const val TEXT = "TEXT"; const val PDF = "PDF" }
object Orientation { const val PORTRAIT = "PORTRAIT"; const val LANDSCAPE = "LANDSCAPE" }
object PageType { const val PAGES = "PAGES"; const val ENDLESS = "ENDLESS" }
object Paper {
    const val PLAIN = "PLAIN"; const val LINED = "LINED"; const val GRID = "GRID"; const val DOTTED = "DOTTED"
    const val CORNELL = "CORNELL"; const val TODO = "TODO"; const val PLANNER = "PLANNER"; const val TIMETABLE = "TIMETABLE"
    val ALL = listOf(PLAIN, LINED, GRID, DOTTED, CORNELL, TODO, PLANNER, TIMETABLE)
}

@Entity(tableName = "notes", indices = [Index("folderId"), Index("deletedAt")])
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val folderId: Long? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val pinned: Boolean = false,
    val favourite: Boolean = false,
    val locked: Boolean = false,
    val deletedAt: Long? = null,
    val kind: String = Kind.INK,
    val pageType: String = PageType.PAGES,
    val defaultOrientation: String = Orientation.PORTRAIT,
    val defaultPaper: String = Paper.LINED,
    val pageCount: Int = 1,
    val hasRecording: Boolean = false,
    val firstLine: String = "",
    val thumbVersion: Long = 0,
    val indexedAt: Long = 0,
)

@Entity(tableName = "pages", indices = [Index("noteId")])
data class PageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val idx: Int,
    val orientation: String = Orientation.PORTRAIT,
    val paper: String = Paper.LINED,
    val width: Float = PageSize.SHORT,
    val height: Float = PageSize.LONG,
    val pdfFile: String? = null,
    val pdfPage: Int = -1,
)

object PageSize {
    const val SHORT = 1000f
    const val LONG = 1414.2f
    fun of(orientation: String): Pair<Float, Float> =
        if (orientation == Orientation.LANDSCAPE) LONG to SHORT else SHORT to LONG
}

@Entity(tableName = "folders")
data class FolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val color: Int,
    val sort: Int = 0,
)

@Entity(tableName = "tags", indices = [Index(value = ["name"], unique = true)])
data class TagEntity(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String)

@Entity(tableName = "note_tags", primaryKeys = ["noteId", "tagId"], indices = [Index("tagId")])
data class NoteTagEntity(val noteId: Long, val tagId: Long)

object AudioKind { const val RECORDING = "RECORDING"; const val FILE = "FILE" }

@Entity(tableName = "recordings", indices = [Index("noteId")])
data class RecordingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val file: String,
    val name: String,
    val durationMs: Long,
    val createdAt: Long,
    val kind: String = AudioKind.RECORDING,
)

object Source { const val TITLE = "TITLE"; const val TYPED = "TYPED"; const val INK = "INK"; const val PDF = "PDF"; const val PHOTO = "PHOTO" }

@Fts4
@Entity(tableName = "search_fts")
data class SearchFts(
    val noteId: String,
    val pageId: String,
    val pageIdx: String,
    val source: String,
    val text: String,
) {
    @androidx.room.Ignore constructor(noteId: Long, pageId: Long, pageIdx: Int, source: String, text: String) : this(noteId.toString(), pageId.toString(), pageIdx.toString(), source, text)
}
