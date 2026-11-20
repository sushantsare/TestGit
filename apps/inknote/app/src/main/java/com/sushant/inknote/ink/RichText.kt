package com.sushant.inknote.ink

import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AlignmentSpan
import android.text.style.BulletSpan
import android.text.style.ForegroundColorSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import android.text.style.UnderlineSpan
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Our own rich-text format (stable across Android versions, unlike Html). Stored in TextBox.html. */
@Serializable
data class RichDoc(val t: String, val s: List<RichSpan> = emptyList())

@Serializable
data class RichSpan(val a: Int, val b: Int, val k: String, val v: Int = 0)

object RichText {
    private val json = Json { ignoreUnknownKeys = true }
    const val CHECK_OFF = "☐ "
    const val CHECK_ON = "☑ "

    fun encode(sp: CharSequence): String {
        val spans = ArrayList<RichSpan>()
        if (sp is Spanned) {
            for (o in sp.getSpans(0, sp.length, Any::class.java)) {
                val a = sp.getSpanStart(o); val b = sp.getSpanEnd(o)
                if (a < 0 || b <= a && o !is BulletSpan) continue
                if ((sp.getSpanFlags(o) and Spanned.SPAN_COMPOSING) != 0) continue
                when (o) {
                    is StyleSpan -> when (o.style) {
                        Typeface.BOLD -> spans += RichSpan(a, b, "b")
                        Typeface.ITALIC -> spans += RichSpan(a, b, "i")
                        Typeface.BOLD_ITALIC -> { spans += RichSpan(a, b, "b"); spans += RichSpan(a, b, "i") }
                    }
                    is UnderlineSpan -> if (o.javaClass == UnderlineSpan::class.java) spans += RichSpan(a, b, "u")
                    is ForegroundColorSpan -> spans += RichSpan(a, b, "c", o.foregroundColor)
                    is RelativeSizeSpan -> spans += RichSpan(a, b, "z", (o.sizeChange * 100).toInt())
                    is BulletSpan -> spans += RichSpan(a, b, "l")
                    is AlignmentSpan -> spans += RichSpan(a, b, "al", when (o.alignment) { Layout.Alignment.ALIGN_CENTER -> 1; Layout.Alignment.ALIGN_OPPOSITE -> 2; else -> 0 })
                }
            }
        }
        return json.encodeToString(RichDoc.serializer(), RichDoc(sp.toString(), spans))
    }

    fun decode(stored: String): SpannableStringBuilder {
        val doc = runCatching { json.decodeFromString(RichDoc.serializer(), stored) }.getOrElse { RichDoc(stored) }
        val sb = SpannableStringBuilder(doc.t)
        for (s in doc.s) {
            val a = s.a.coerceIn(0, sb.length); val b = s.b.coerceIn(a, sb.length)
            val span: Any = when (s.k) {
                "b" -> StyleSpan(Typeface.BOLD); "i" -> StyleSpan(Typeface.ITALIC); "u" -> UnderlineSpan()
                "c" -> ForegroundColorSpan(s.v); "z" -> RelativeSizeSpan(s.v / 100f); "l" -> BulletSpan(16)
                "al" -> AlignmentSpan.Standard(when (s.v) { 1 -> Layout.Alignment.ALIGN_CENTER; 2 -> Layout.Alignment.ALIGN_OPPOSITE; else -> Layout.Alignment.ALIGN_NORMAL })
                else -> continue
            }
            val flags = if (s.k == "l" || s.k == "al") Spanned.SPAN_PARAGRAPH else Spanned.SPAN_EXCLUSIVE_INCLUSIVE
            runCatching { sb.setSpan(span, a, b, flags) }.onFailure { sb.setSpan(span, a, b, Spanned.SPAN_EXCLUSIVE_INCLUSIVE) }
        }
        return sb
    }

    fun plain(stored: String): String = runCatching { json.decodeFromString(RichDoc.serializer(), stored).t }.getOrDefault(stored)

    fun fromPlain(text: String): String = json.encodeToString(RichDoc.serializer(), RichDoc(text))
}
