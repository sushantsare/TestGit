package com.sushant.bahikhata.data

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.res.ResourcesCompat
import com.sushant.bahikhata.R
import java.io.ByteArrayOutputStream
import java.time.YearMonth
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** CSV, Excel (.xlsx) and PDF statements for any period. */
object Export {
    data class Row(val date: String, val type: String, val amount: Long, val account: String, val category: String, val merchant: String, val upi: String, val note: String, val tags: String)

    fun rows(entries: List<Entry>, accounts: Map<Long, Account>, cats: Map<Long, Category>, r: LongRange): List<Row> =
        entries.filter { it.time in r && !it.cancelled }.sortedBy { it.time }.map { e ->
            val acc = accounts[e.accountId]?.name.orEmpty() + (e.toAccountId?.let { " → " + accounts[it]?.name.orEmpty() } ?: "")
            Row(Dates.dayMonthYear(Dates.date(e.time)) + " " + Dates.time(e.time), typeName(e.type),
                if (e.type == EType.EXPENSE || e.type == EType.UDHAR_GAVE || e.type == EType.INVEST) -e.amountPaise else e.amountPaise,
                acc, e.categoryId?.let { cats[it]?.name }.orEmpty(), e.merchant.orEmpty(), e.upiApp.orEmpty(), e.note.orEmpty(), Ledger.tags(e.tags).joinToString(" ") { "#$it" })
        }

    fun typeName(t: String) = when (t) {
        EType.EXPENSE -> "Expense"; EType.INCOME -> "Income"; EType.CARD_PAYMENT -> "Card bill paid"; EType.CASH_WITHDRAWAL -> "Cash withdrawn"
        EType.ADJUSTMENT -> "Balance adjustment"; EType.UDHAR_GAVE -> "Udhar given"; EType.UDHAR_GOT -> "Udhar received"
        EType.TRANSFER -> "Transfer"; EType.INVEST -> "Investment"; else -> t
    }

    private val header = listOf("Date", "Type", "Amount (₹)", "Account", "Category", "Merchant / person", "Paid via", "Note", "Tags")
    private fun rupees(p: Long) = java.math.BigDecimal(p).movePointLeft(2).toPlainString()

    fun csv(rows: List<Row>): ByteArray {
        // text from SMS/shops is never run as a spreadsheet formula (=, +, -, @ at the start get a leading quote)
        fun safe(s: String) = if (s.isNotEmpty() && s[0] in "=+-@\t\r") "'$s" else s
        fun q(raw: String): String { val s = safe(raw); return if (s.any { it == ',' || it == '"' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s }
        val sb = StringBuilder("\uFEFF").append(header.joinToString(",")).append("\n")
        rows.forEach { r -> sb.append(listOf(r.date, r.type, rupees(r.amount), r.account, r.category, r.merchant, r.upi, r.note, r.tags).joinToString(",") { q(it) }).append("\n") }
        return sb.toString().toByteArray()
    }

    /** Minimal valid SpreadsheetML workbook (one sheet, inline strings, numeric amounts). */
    fun xlsx(rows: List<Row>): ByteArray {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun col(i: Int) = ('A' + i).toString()
        val sheet = StringBuilder("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?><worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><cols><col min="1" max="1" width="20" customWidth="1"/><col min="2" max="2" width="16" customWidth="1"/><col min="3" max="3" width="12" customWidth="1"/><col min="4" max="9" width="22" customWidth="1"/></cols><sheetData>""")
        fun str(r: Int, c: Int, v: String, s: Int = 0) = """<c r="${col(c)}$r" t="inlineStr"${if (s > 0) " s=\"$s\"" else ""}><is><t>${esc(v)}</t></is></c>"""
        sheet.append("<row r=\"1\">"); header.forEachIndexed { i, h -> sheet.append(str(1, i, h, 1)) }; sheet.append("</row>")
        rows.forEachIndexed { idx, r ->
            val n = idx + 2
            sheet.append("<row r=\"$n\">").append(str(n, 0, r.date)).append(str(n, 1, r.type))
                .append("""<c r="C$n" s="2"><v>${rupees(r.amount)}</v></c>""")
                .append(str(n, 3, r.account)).append(str(n, 4, r.category)).append(str(n, 5, r.merchant)).append(str(n, 6, r.upi)).append(str(n, 7, r.note)).append(str(n, 8, r.tags))
                .append("</row>")
        }
        sheet.append("</sheetData></worksheet>")
        val files = mapOf(
            "[Content_Types].xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types"><Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/><Default Extension="xml" ContentType="application/xml"/><Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/><Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/><Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/></Types>""",
            "_rels/.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/></Relationships>""",
            "xl/workbook.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships"><sheets><sheet name="Bahi Khata" sheetId="1" r:id="rId1"/></sheets></workbook>""",
            "xl/_rels/workbook.xml.rels" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships"><Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/></Relationships>""",
            "xl/styles.xml" to """<?xml version="1.0" encoding="UTF-8" standalone="yes"?><styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><numFmts count="1"><numFmt numFmtId="164" formatCode="#,##0.00"/></numFmts><fonts count="2"><font><sz val="11"/><name val="Calibri"/></font><font><b/><sz val="11"/><name val="Calibri"/></font></fonts><fills count="2"><fill><patternFill patternType="none"/></fill><fill><patternFill patternType="gray125"/></fill></fills><borders count="1"><border/></borders><cellStyleXfs count="1"><xf/></cellStyleXfs><cellXfs count="3"><xf/><xf fontId="1" applyFont="1"/><xf numFmtId="164" applyNumberFormat="1"/></cellXfs></styleSheet>""",
            "xl/worksheets/sheet1.xml" to sheet.toString(),
        )
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { z -> files.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return bos.toByteArray()
    }

    /** A4 monthly / period report: totals, spending by category, by account, and every entry. */
    fun pdf(context: Context, title: String, rows: List<Row>, entries: List<Entry>, r: LongRange, accounts: Map<Long, Account>, cats: Map<Long, Category>): ByteArray {
        val doc = PdfDocument()
        val W = 595; val H = 842; val M = 40f
        val font = ResourcesCompat.getFont(context, R.font.manrope_regular) ?: Typeface.DEFAULT
        val bold = ResourcesCompat.getFont(context, R.font.manrope_bold) ?: Typeface.DEFAULT_BOLD
        val ink = Color.parseColor("#1E1B16"); val muted = Color.parseColor("#6A6155"); val brand = Color.parseColor("#2C3E8C")
        val red = Color.parseColor("#B3261E"); val green = Color.parseColor("#1F7A45"); val line = Color.parseColor("#E6DACA")
        fun p(size: Float, c: Int = ink, b: Boolean = false, align: Paint.Align = Paint.Align.LEFT) = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = size; color = c; typeface = if (b) bold else font; textAlign = align }
        var pageNo = 0
        var page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create())
        var cv = page.canvas
        var y = M
        fun newPage() {
            cv.drawText("Bahi Khata · page $pageNo", W / 2f, H - 20f, p(8f, muted, align = Paint.Align.CENTER))
            doc.finishPage(page); page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, ++pageNo).create()); cv = page.canvas; y = M
        }
        fun need(h: Float) { if (y + h > H - 40) newPage() }

        cv.drawRect(0f, 0f, W.toFloat(), 86f, Paint().apply { color = Color.parseColor("#FBF6EE") })
        cv.drawText("Bahi Khata", M, 38f, p(20f, brand, true)); cv.drawText(title, M, 62f, p(12f, muted))
        y = 112f
        val spent = Ledger.spent(entries, r); val got = Ledger.received(entries, r)
        listOf(Triple("Spent", spent, red), Triple("Received", got, green), Triple("Net", got - spent, ink)).forEachIndexed { i, (l, v, c) ->
            val x = M + i * 172
            cv.drawText(l, x, y, p(10f, muted)); cv.drawText(Money.fmt(v, sign = l == "Net"), x, y + 22, p(17f, c, true))
        }
        y += 52
        cv.drawText("Spending by category", M, y, p(12f, ink, true)); y += 16
        val byCat = Ledger.byCategory(entries, r).toList().sortedByDescending { it.second }
        byCat.forEach { (cid, v) ->
            need(18f)
            val name = cid?.let { cats[it]?.name } ?: "Uncategorised"
            cv.drawText(name, M, y, p(10f)); cv.drawText(Money.fmt(v), W - M, y, p(10f, align = Paint.Align.RIGHT))
            val pct = if (spent > 0) v.toFloat() / spent else 0f
            cv.drawRect(M + 150, y - 7, M + 150 + 230 * pct, y - 2, Paint().apply { color = Color.parseColor("#E68A1E") })
            y += 16
        }
        if (byCat.isEmpty()) { cv.drawText("No spending in this period", M, y, p(10f, muted)); y += 16 }
        y += 10; need(30f)
        cv.drawText("By card / account", M, y, p(12f, ink, true)); y += 16
        Ledger.byAccount(entries, r).toList().sortedByDescending { it.second }.forEach { (aid, v) ->
            need(16f); cv.drawText(accounts[aid]?.name ?: "—", M, y, p(10f)); cv.drawText(Money.fmt(v), W - M, y, p(10f, align = Paint.Align.RIGHT)); y += 16
        }
        y += 14; need(40f)
        cv.drawText("All entries (${rows.size})", M, y, p(12f, ink, true)); y += 16
        val cols = floatArrayOf(M, M + 92, M + 190, M + 300, W - M)
        listOf("Date", "Merchant / type", "Account", "Category").forEachIndexed { i, h -> cv.drawText(h, cols[i], y, p(8.5f, muted, true)) }
        cv.drawText("Amount", cols[4], y, p(8.5f, muted, true, Paint.Align.RIGHT)); y += 6
        cv.drawLine(M, y, W - M, y, Paint().apply { color = line }); y += 12
        rows.forEach { row ->
            need(14f)
            fun cut(s: String, n: Int) = if (s.length > n) s.take(n - 1) + "…" else s
            cv.drawText(row.date.substringBeforeLast(" ").substringBeforeLast(" "), cols[0], y, p(8.5f))
            cv.drawText(cut(row.merchant.ifBlank { row.type }, 20), cols[1], y, p(8.5f))
            cv.drawText(cut(row.account, 22), cols[2], y, p(8.5f, muted))
            cv.drawText(cut(row.category, 16), cols[3], y, p(8.5f, muted))
            cv.drawText(Money.fmt(row.amount), cols[4], y, p(8.5f, if (row.amount < 0) red else green, true, Paint.Align.RIGHT))
            y += 14
        }
        cv.drawText("Bahi Khata · page $pageNo", W / 2f, H - 20f, p(8f, muted, align = Paint.Align.CENTER))
        doc.finishPage(page)
        val bos = ByteArrayOutputStream(); doc.writeTo(bos); doc.close()
        return bos.toByteArray()
    }

    fun monthTitle(ym: YearMonth) = "Report for " + Dates.month(ym)
}
