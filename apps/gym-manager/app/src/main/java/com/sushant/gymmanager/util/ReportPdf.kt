package com.sushant.gymmanager.util

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sushant.gymmanager.data.local.ExpenseEntity
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.PaymentEntity
import com.sushant.gymmanager.i18n.I18n
import java.io.OutputStream
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

data class ReportInput(
    val settings: GymSettingsEntity,
    val month: YearMonth,
    val payments: List<PaymentEntity>,
    val expenses: List<ExpenseEntity>,
    val cycles: List<PaymentCycleEntity>,
    val members: List<MemberEntity>
)

/** Builds a neatly formatted, multi-page A4 monthly report with the Android PDF API (no libraries). */
object ReportPdf {
    private const val W = 595f
    private const val H = 842f
    private const val M = 36f
    private const val CONTENT_W = W - 2 * M
    private const val FOOTER = 34f

    private val ORANGE = 0xFFEA580C.toInt()
    private val ORANGE_DARK = 0xFFC2410C.toInt()
    private val ORANGE_SOFT = 0xFFFFF1E6.toInt()
    private val INK = 0xFF0F172A.toInt()
    private val MUTED = 0xFF64748B.toInt()
    private val LINE = 0xFFE2E8F0.toInt()
    private val ZEBRA = 0xFFF8FAFC.toInt()
    private val GREEN = 0xFF16A34A.toInt()
    private val RED = 0xFFDC2626.toInt()
    private val TEAL = 0xFF0D9488.toInt()
    private val AMBER = 0xFFD97706.toInt()

    private fun p(size: Float, color: Int = INK, bold: Boolean = false, align: Paint.Align = Paint.Align.LEFT) =
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = size; this.color = color; textAlign = align
            typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
        }

    private fun fit(text: String, paint: Paint, max: Float): String {
        if (paint.measureText(text) <= max) return text
        var s = text
        while (s.isNotEmpty() && paint.measureText("$s…") > max) s = s.dropLast(1)
        return "$s…"
    }

    class Col(val title: String, val weight: Float, val right: Boolean = false)

    private class Writer(val doc: PdfDocument, val footerLeft: String) {
        var pageNo = 0
        lateinit var page: PdfDocument.Page
        lateinit var c: Canvas
        var y = 0f

        fun newPage() {
            if (pageNo > 0) finishPage()
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W.toInt(), H.toInt(), pageNo).create())
            c = page.canvas
            y = M
        }

        fun finishPage() {
            c.drawLine(M, H - FOOTER, W - M, H - FOOTER, Paint().apply { color = LINE; strokeWidth = 0.8f })
            c.drawText(footerLeft, M, H - FOOTER + 16f, p(8f, MUTED))
            c.drawText("Page $pageNo", W - M, H - FOOTER + 16f, p(8f, MUTED, align = Paint.Align.RIGHT))
            doc.finishPage(page)
        }

        /** Starts a new page when [needed] points don't fit on this one. Returns true if it did. */
        fun ensure(needed: Float): Boolean {
            if (y + needed > H - FOOTER - 10f) {
                newPage(); return true
            }
            return false
        }
    }

    fun write(input: ReportInput, out: OutputStream) {
        val lang = input.settings.language
        fun tr(s: String, vararg a: Any) = I18n.tr(lang, s, *a)
        val key = input.month.toString()
        val monthName = DateUtils.formatMonth(input.month.atDay(1))
        val generated = LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM yyyy, h:mm a", Locale.ENGLISH))
        val memberById = input.members.associateBy { it.id }
        val cycleById = input.cycles.associateBy { it.id }

        val monthPays = input.payments.filter { it.paidAt.startsWith(key) }.sortedBy { it.paidAt }
        val monthExp = input.expenses.filter { it.date.startsWith(key) }.sortedBy { it.date }
        val collected = monthPays.sumOf { it.amount }
        val spent = monthExp.sumOf { it.amount }
        val profit = collected - spent
        val unpaid = input.cycles.filter { !it.isPaid && it.balance > 0.005 }.sortedWith(compareBy({ memberById[it.memberId]?.fromTime }, { it.cycleStart }))
        val pending = unpaid.sumOf { it.balance }
        val joined = input.members.count { it.dateOfJoining.startsWith(key) }
        val active = input.members.count { it.isActive }

        val doc = PdfDocument()
        val w = Writer(doc, "${input.settings.gymName} • ${tr("Generated on %s", generated)}")
        w.newPage()
        val c0 = w.c

        // ---------- Header band ----------
        val band = RectF(0f, 0f, W, 104f)
        c0.drawRect(band, Paint().apply { shader = LinearGradient(0f, 0f, W, 104f, ORANGE, ORANGE_DARK, Shader.TileMode.CLAMP) })
        c0.drawText(fit(input.settings.gymName, p(22f, Color.WHITE, true), 330f), M, 44f, p(22f, Color.WHITE, true))
        val contact = listOf(input.settings.gymAddress, input.settings.gymPhone).filter { it.isNotBlank() }.joinToString("  •  ")
        if (contact.isNotBlank()) c0.drawText(fit(contact, p(9f, Color.WHITE), 330f), M, 62f, p(9f, 0xE6FFFFFF.toInt()))
        c0.drawText(tr("MONTHLY REPORT"), M, 86f, p(9f, 0xCCFFFFFF.toInt(), true))
        c0.drawText(monthName, W - M, 50f, p(18f, Color.WHITE, true, Paint.Align.RIGHT))
        c0.drawText(tr("Generated on %s", generated), W - M, 68f, p(8.5f, 0xDDFFFFFF.toInt(), align = Paint.Align.RIGHT))
        w.y = 104f + 22f

        // ---------- Summary tiles ----------
        fun tiles(items: List<Triple<String, String, Int>>) {
            val gap = 10f
            val tw = (CONTENT_W - gap * (items.size - 1)) / items.size
            val th = 58f
            w.ensure(th + 10f)
            items.forEachIndexed { i, (label, value, color) ->
                val x = M + i * (tw + gap)
                val r = RectF(x, w.y, x + tw, w.y + th)
                w.c.drawRoundRect(r, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.WHITE })
                w.c.drawRoundRect(r, 8f, 8f, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = LINE; style = Paint.Style.STROKE; strokeWidth = 0.8f })
                w.c.drawRect(RectF(x, w.y + 8f, x + 3f, w.y + th - 8f), Paint().apply { this.color = color })
                w.c.drawText(fit(label.uppercase(), p(7.5f, MUTED, true), tw - 20f), x + 12f, w.y + 20f, p(7.5f, MUTED, true))
                w.c.drawText(fit(value, p(15f, color, true), tw - 20f), x + 12f, w.y + 42f, p(15f, color, true))
            }
            w.y += th + 10f
        }
        tiles(
            listOf(
                Triple(tr("Collected"), Money.format(collected), GREEN),
                Triple(tr("Expenses"), Money.format(spent), RED),
                Triple(tr(if (profit >= 0) "Profit" else "Loss"), Money.format(profit), if (profit >= 0) TEAL else RED),
                Triple(tr("Pending Dues"), Money.format(pending), AMBER)
            )
        )
        tiles(
            listOf(
                Triple(tr("Active Members"), active.toString(), INK),
                Triple(tr("New members"), joined.toString(), INK),
                Triple(tr("Payments"), monthPays.size.toString(), INK),
                Triple(tr("Members with dues"), unpaid.map { it.memberId }.toSet().size.toString(), INK)
            )
        )

        // ---------- Tables ----------
        fun sectionTitle(title: String, sub: String? = null) {
            w.ensure(60f)
            w.y += 12f
            w.c.drawText(title, M, w.y + 12f, p(12.5f, INK, true))
            if (sub != null) w.c.drawText(sub, W - M, w.y + 12f, p(8.5f, MUTED, align = Paint.Align.RIGHT))
            w.y += 22f
        }

        fun table(cols: List<Col>, rows: List<List<String>>, totals: List<String>? = null, empty: String) {
            val rowH = 17f
            val totalW = cols.sumOf { it.weight.toDouble() }.toFloat()
            val widths = cols.map { it.weight / totalW * CONTENT_W }
            fun header() {
                w.c.drawRoundRect(RectF(M, w.y, W - M, w.y + rowH + 2f), 4f, 4f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = ORANGE_SOFT })
                var x = M
                cols.forEachIndexed { i, col ->
                    val paint = p(8f, ORANGE_DARK, true, if (col.right) Paint.Align.RIGHT else Paint.Align.LEFT)
                    val tx = if (col.right) x + widths[i] - 6f else x + 6f
                    w.c.drawText(fit(col.title, paint, widths[i] - 10f), tx, w.y + 12.5f, paint)
                    x += widths[i]
                }
                w.y += rowH + 2f
            }
            fun line(values: List<String>, bold: Boolean, fill: Int?) {
                if (fill != null) w.c.drawRect(RectF(M, w.y, W - M, w.y + rowH), Paint().apply { color = fill })
                var x = M
                cols.forEachIndexed { i, col ->
                    val paint = p(8.5f, INK, bold, if (col.right) Paint.Align.RIGHT else Paint.Align.LEFT)
                    val tx = if (col.right) x + widths[i] - 6f else x + 6f
                    w.c.drawText(fit(values.getOrElse(i) { "" }, paint, widths[i] - 10f), tx, w.y + 12f, paint)
                    x += widths[i]
                }
                w.y += rowH
            }
            w.ensure(rowH * 3)
            header()
            if (rows.isEmpty()) {
                w.c.drawText(empty, M + 6f, w.y + 13f, p(8.5f, MUTED))
                w.y += rowH + 4f
                return
            }
            rows.forEachIndexed { i, r ->
                if (w.ensure(rowH)) header()
                line(r, false, if (i % 2 == 1) ZEBRA else null)
            }
            if (totals != null) {
                if (w.ensure(rowH + 4f)) header()
                w.c.drawLine(M, w.y + 1f, W - M, w.y + 1f, Paint().apply { color = LINE; strokeWidth = 1f })
                w.y += 2f
                line(totals, true, null)
            }
            w.y += 4f
        }

        // Last 6 months
        sectionTitle(tr("Last 6 months"))
        val months = (5 downTo 0).map { input.month.minusMonths(it.toLong()) }
        table(
            listOf(Col(tr("Month"), 1.4f), Col(tr("Collected"), 1f, true), Col(tr("Expenses"), 1f, true), Col(tr("Profit"), 1f, true), Col(tr("Payments"), 0.8f, true)),
            months.map { ym ->
                val k = ym.toString()
                val col = input.payments.filter { it.paidAt.startsWith(k) }
                val exp = input.expenses.filter { it.date.startsWith(k) }.sumOf { it.amount }
                listOf(DateUtils.formatMonth(ym.atDay(1)), Money.format(col.sumOf { it.amount }), Money.format(exp), Money.format(col.sumOf { it.amount } - exp), col.size.toString())
            },
            empty = ""
        )

        // Payment modes
        sectionTitle(tr("Payment modes"), monthName)
        table(
            listOf(Col(tr("Mode"), 1.6f), Col(tr("Payments"), 1f, true), Col(tr("Amount"), 1f, true), Col(tr("Share"), 0.8f, true)),
            PayMode.all.mapNotNull { mode ->
                val list = monthPays.filter { it.mode == mode }
                if (list.isEmpty()) null else listOf(
                    I18n.payModeLabel(lang, mode), list.size.toString(), Money.format(list.sumOf { it.amount }),
                    if (collected > 0) "${(list.sumOf { it.amount } * 100 / collected).toInt()}%" else "—"
                )
            },
            totals = if (monthPays.isEmpty()) null else listOf(tr("Total"), monthPays.size.toString(), Money.format(collected), "100%"),
            empty = tr("No payments this month")
        )

        // Payments received
        sectionTitle(tr("Payments received"), monthName)
        table(
            listOf(
                Col(tr("Date"), 0.85f), Col(tr("Receipt"), 0.8f), Col(tr("Member"), 1.6f),
                Col(tr("Period"), 1.7f), Col(tr("Mode"), 0.7f), Col(tr("Amount"), 0.9f, true)
            ),
            monthPays.map { pay ->
                val m = memberById[pay.memberId]
                val cy = cycleById[pay.cycleId]
                listOf(
                    DateUtils.formatShort(pay.paidAt.take(10)), pay.receiptNo,
                    m?.let { "${it.fullName} (#${it.rollNumber})" } ?: tr("Deleted member"),
                    cy?.let { "${DateUtils.formatShort(it.cycleStart)} – ${DateUtils.formatShort(it.cycleEnd)}" } ?: "",
                    I18n.payModeLabel(lang, pay.mode).substringBefore(" "), Money.format(pay.amount)
                )
            },
            totals = if (monthPays.isEmpty()) null else listOf(tr("Total"), "", "", "", "", Money.format(collected)),
            empty = tr("No payments this month")
        )

        // Expenses
        sectionTitle(tr("Expenses"), monthName)
        table(
            listOf(Col(tr("Date"), 0.9f), Col(tr("Category"), 1.1f), Col(tr("Note"), 2.4f), Col(tr("Amount"), 0.9f, true)),
            monthExp.map { listOf(DateUtils.formatShort(it.date), I18n.categoryLabel(lang, it.category), it.note.orEmpty(), Money.format(it.amount)) },
            totals = if (monthExp.isEmpty()) null else listOf(tr("Total"), "", "", Money.format(spent)),
            empty = tr("No expenses this month")
        )

        // Pending dues
        sectionTitle(tr("Pending dues"), tr("All members, as of today"))
        table(
            listOf(
                Col(tr("Roll"), 0.55f), Col(tr("Member"), 1.6f), Col(tr("WhatsApp"), 1.1f),
                Col(tr("Period"), 1.7f), Col(tr("Balance"), 0.9f, true)
            ),
            unpaid.map { cy ->
                val m = memberById[cy.memberId]
                listOf(
                    m?.rollNumber ?: "", m?.fullName ?: tr("Deleted member"), m?.whatsappNumber ?: "",
                    "${DateUtils.formatShort(cy.cycleStart)} – ${DateUtils.formatShort(cy.cycleEnd)}", Money.format(cy.balance)
                )
            },
            totals = if (unpaid.isEmpty()) null else listOf("", tr("Total"), "", "", Money.format(pending)),
            empty = tr("No pending dues")
        )

        w.finishPage()
        doc.writeTo(out)
        doc.close()
    }
}
