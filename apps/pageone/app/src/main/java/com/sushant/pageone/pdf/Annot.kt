package com.sushant.pageone.pdf

import android.graphics.PointF
import android.graphics.RectF

/** Edits made in PageOne, kept in memory until "Save" writes them into the PDF. Coordinates are page points. */
sealed class Annot {
    abstract val page: Int
    abstract fun bounds(): RectF

    data class Mark(override val page: Int, val rects: List<RectF>, val color: Int, val underline: Boolean) : Annot() {
        override fun bounds() = RectF(rects.minOf { it.left }, rects.minOf { it.top }, rects.maxOf { it.right }, rects.maxOf { it.bottom })
    }

    data class Ink(override val page: Int, val points: List<PointF>, val color: Int, val width: Float, val marker: Boolean) : Annot() {
        override fun bounds() = RectF(points.minOf { it.x } - width, points.minOf { it.y } - width, points.maxOf { it.x } + width, points.maxOf { it.y } + width)
    }

    data class Note(override val page: Int, val at: PointF, val text: String) : Annot() {
        override fun bounds() = RectF(at.x, at.y, at.x + NOTE_SIZE, at.y + NOTE_SIZE)
    }

    companion object { const val NOTE_SIZE = 18f }
}

enum class Tool { HIGHLIGHT, UNDERLINE, PEN, ERASER, NOTE }

/** A form field on a page (rect in page points, top-left origin). */
data class FormField(
    val name: String,
    val page: Int,
    val rect: RectF,
    val kind: Kind,
    val value: String,
    val checked: Boolean,
    val readOnly: Boolean,
    val multiline: Boolean,
) {
    enum class Kind { TEXT, CHECKBOX, OTHER }
}

data class OutlineItem(val title: String, val page: Int, val level: Int)

/** Undo/redo history for annotations (unit-tested). */
class AnnotHistory {
    private val items = ArrayList<Annot>()
    private val undone = ArrayList<List<Annot>>()
    private val done = ArrayList<List<Annot>>()
    val all: List<Annot> get() = items

    fun add(a: Annot) { done += items.toList(); items += a; undone.clear() }
    fun remove(a: Annot) { done += items.toList(); items.remove(a); undone.clear() }
    fun undo(): Boolean { val prev = done.removeLastOrNull() ?: return false; undone += items.toList(); items.clear(); items += prev; return true }
    fun redo(): Boolean { val next = undone.removeLastOrNull() ?: return false; done += items.toList(); items.clear(); items += next; return true }
    fun clear() { items.clear(); done.clear(); undone.clear() }
    val canUndo get() = done.isNotEmpty()
    val canRedo get() = undone.isNotEmpty()

    /** "2 highlights and 1 drawing" */
    fun summary(): String {
        val marks = items.count { it is Annot.Mark && !it.underline } + items.count { it is Annot.Ink && it.marker }
        val lines = items.count { it is Annot.Mark && it.underline }
        val ink = items.count { it is Annot.Ink && !it.marker }
        val notes = items.count { it is Annot.Note }
        val parts = listOfNotNull(
            marks.takeIf { it > 0 }?.let { "$it highlight${if (it > 1) "s" else ""}" },
            lines.takeIf { it > 0 }?.let { "$it underline${if (it > 1) "s" else ""}" },
            ink.takeIf { it > 0 }?.let { "$it drawing${if (it > 1) "s" else ""}" },
            notes.takeIf { it > 0 }?.let { "$it note${if (it > 1) "s" else ""}" },
        )
        return when (parts.size) {
            0 -> "changes"
            1 -> parts[0]
            else -> parts.dropLast(1).joinToString(", ") + " and " + parts.last()
        }
    }
}
