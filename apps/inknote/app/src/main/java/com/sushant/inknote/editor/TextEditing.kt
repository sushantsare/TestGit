package com.sushant.inknote.editor

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.Editable
import android.text.Layout
import android.text.Spanned
import android.text.TextWatcher
import android.text.style.AlignmentSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import android.util.TypedValue
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import com.sushant.inknote.ink.RichText

/** The live EditText used for typing on a page. Formatting buttons act on it through this holder. */
class TextEditorHolder {
    var edit: EditText? = null

    fun create(context: Context, initial: String, textPx: Float, dark: Boolean, onChange: (String, String) -> Unit): EditText {
        val e = EditText(context)
        e.background = null
        e.setPadding(0, 0, 0, 0)
        e.gravity = Gravity.TOP or Gravity.START
        e.setTextSize(TypedValue.COMPLEX_UNIT_PX, textPx)
        e.setLineSpacing(0f, 1.25f)
        e.setTextColor(if (dark) 0xFFECE8E1.toInt() else 0xFF1C1B1F.toInt())
        e.setHintTextColor(if (dark) 0xFF8C877E.toInt() else 0xFF8A8478.toInt())
        e.hint = "Start typing"
        e.imeOptions = EditorInfo.IME_FLAG_NO_FULLSCREEN
        e.setText(RichText.decode(initial))
        e.setSelection(e.text.length)
        e.contentDescription = "Text box"
        e.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) { if (s != null) onChange(RichText.encode(s), s.toString()) }
        })
        edit = e
        return e
    }

    fun focus() {
        val e = edit ?: return
        e.requestFocus()
        e.post { (e.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(e, InputMethodManager.SHOW_IMPLICIT) }
    }

    fun hideKeyboard() { val e = edit ?: return; (e.context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(e.windowToken, 0) }

    private fun range(): Pair<Int, Int>? {
        val e = edit ?: return null
        val a = e.selectionStart.coerceAtLeast(0); val b = e.selectionEnd.coerceAtLeast(0)
        return minOf(a, b) to maxOf(a, b)
    }

    private fun lineRange(): Pair<Int, Int>? {
        val e = edit ?: return null; val t = e.text
        val (a, b) = range() ?: return null
        var s = a; while (s > 0 && t[s - 1] != '\n') s--
        var en = b; while (en < t.length && t[en] != '\n') en++
        return s to en
    }

    private fun notifyChange() { edit?.let { it.text.replace(0, 0, "") } }

    fun toggleStyle(style: Int) {
        val e = edit ?: return; val (a, b) = range() ?: return; val t = e.text
        if (a == b) return
        val existing = t.getSpans(a, b, StyleSpan::class.java).filter { it.style == style }
        if (existing.isNotEmpty()) existing.forEach { t.removeSpan(it) } else t.setSpan(StyleSpan(style), a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        notifyChange()
    }

    fun toggleUnderline() {
        val e = edit ?: return; val (a, b) = range() ?: return; val t = e.text
        if (a == b) return
        val ex = t.getSpans(a, b, UnderlineSpan::class.java).filter { it.javaClass == UnderlineSpan::class.java }
        if (ex.isNotEmpty()) ex.forEach { t.removeSpan(it) } else t.setSpan(UnderlineSpan(), a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        notifyChange()
    }

    fun toggleHeading() {
        val e = edit ?: return; val (a, b) = lineRange() ?: return; val t = e.text
        if (a == b) return
        val ex = t.getSpans(a, b, RelativeSizeSpan::class.java)
        if (ex.isNotEmpty()) { ex.forEach { t.removeSpan(it) }; t.getSpans(a, b, StyleSpan::class.java).filter { it.style == Typeface.BOLD }.forEach { t.removeSpan(it) } }
        else { t.setSpan(RelativeSizeSpan(1.45f), a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE); t.setSpan(StyleSpan(Typeface.BOLD), a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE) }
        notifyChange()
    }

    fun toggleBullet() {
        val e = edit ?: return; val (a, b0) = lineRange() ?: return; val t = e.text
        var b = b0; if (b < t.length) b++ // paragraph spans include the newline
        val ex = t.getSpans(a, b, BulletSpan::class.java)
        if (ex.isNotEmpty()) ex.forEach { t.removeSpan(it) } else runCatching { t.setSpan(BulletSpan(16), a, b, Spanned.SPAN_PARAGRAPH) }
        notifyChange()
    }

    /** Adds a tick box at the start of the line, or ticks / unticks an existing one. */
    fun toggleCheck() {
        val e = edit ?: return; val (a, _) = lineRange() ?: return; val t = e.text
        val line = t.subSequence(a, t.length).toString()
        when {
            line.startsWith(RichText.CHECK_OFF) -> t.replace(a, a + 2, RichText.CHECK_ON)
            line.startsWith(RichText.CHECK_ON) -> t.replace(a, a + 2, RichText.CHECK_OFF)
            else -> t.insert(a, RichText.CHECK_OFF)
        }
    }

    fun setColor(color: Int) {
        val e = edit ?: return; val (a, b) = range() ?: return; val t = e.text
        if (a == b) return
        t.getSpans(a, b, ForegroundColorSpan::class.java).forEach { t.removeSpan(it) }
        if (color != Color.TRANSPARENT) t.setSpan(ForegroundColorSpan(color), a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE)
        notifyChange()
    }

    fun cycleAlign() {
        val e = edit ?: return; val (a, b0) = lineRange() ?: return; val t = e.text
        var b = b0; if (b < t.length) b++
        val ex = t.getSpans(a, b, AlignmentSpan::class.java)
        val cur = ex.firstOrNull()?.alignment ?: Layout.Alignment.ALIGN_NORMAL
        ex.forEach { t.removeSpan(it) }
        val next = when (cur) { Layout.Alignment.ALIGN_NORMAL -> Layout.Alignment.ALIGN_CENTER; Layout.Alignment.ALIGN_CENTER -> Layout.Alignment.ALIGN_OPPOSITE; else -> null }
        if (next != null) runCatching { t.setSpan(AlignmentSpan.Standard(next), a, b, Spanned.SPAN_PARAGRAPH) }
        notifyChange()
    }

}
