package com.sushant.bahikhata.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

/** Indian money formatting: ₹1,00,000.50 · -₹1,800 · compact 1.2K / 3.4L / 2.1Cr. Pure Kotlin (unit tested). */
object Money {
    fun groupIndian(n: Long): String {
        val s = n.toString()
        if (s.length <= 3) return s
        val last3 = s.takeLast(3)
        var rest = s.dropLast(3)
        val parts = ArrayList<String>()
        while (rest.length > 2) { parts.add(0, rest.takeLast(2)); rest = rest.dropLast(2) }
        if (rest.isNotEmpty()) parts.add(0, rest)
        return parts.joinToString(",") + "," + last3
    }

    /** paise -> "₹1,23,456" (paise shown only when not whole rupees). */
    fun fmt(paise: Long, sign: Boolean = false, alwaysPaise: Boolean = false): String {
        val neg = paise < 0
        val a = abs(paise)
        val r = a / 100
        val p = a % 100
        val body = "₹" + groupIndian(r) + if (p != 0L || alwaysPaise) "." + p.toString().padStart(2, '0') else ""
        return when {
            neg -> "−$body"
            sign && paise > 0 -> "+$body"
            else -> body
        }
    }

    fun compact(paise: Long): String {
        val r = abs(paise) / 100.0
        val s = when {
            r >= 1_00_00_000 -> trim(r / 1_00_00_000) + "Cr"
            r >= 1_00_000 -> trim(r / 1_00_000) + "L"
            r >= 1_000 -> trim(r / 1_000) + "K"
            else -> r.toLong().toString()
        }
        return (if (paise < 0) "−₹" else "₹") + s
    }

    private fun trim(d: Double): String {
        val s = String.format(Locale.US, "%.1f", d)
        return if (s.endsWith(".0")) s.dropLast(2) else s
    }

    /** "1,250.50" / "1250" / "₹ 1 250" -> paise; null if not a number. */
    fun parse(text: String): Long? {
        val t = text.replace("₹", "").replace(",", "").replace(" ", "").trim()
        if (t.isEmpty()) return null
        val d = t.toBigDecimalOrNull() ?: return null
        if (d.signum() < 0) return null
        return d.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** Text for an input field from paise: 38650 -> "386.5" ; 38600 -> "386". */
    fun input(paise: Long): String {
        val r = paise / 100
        val p = paise % 100
        return if (p == 0L) r.toString() else "$r." + p.toString().padStart(2, '0').trimEnd('0')
    }
}

/** Tiny calculator for the amount keypad: supports + − × ÷ left to right with paise precision. */
object Calc {
    fun eval(expr: String): Long? {
        val t = expr.replace("×", "*").replace("÷", "/").replace("−", "-").replace(" ", "")
        if (t.isEmpty()) return null
        var total: java.math.BigDecimal? = null
        var op = '+'
        var num = StringBuilder()
        fun apply(): Boolean {
            if (num.isEmpty()) return true
            val v = num.toString().toBigDecimalOrNull() ?: return false
            total = when (op) {
                '+' -> (total ?: java.math.BigDecimal.ZERO) + v
                '-' -> (total ?: java.math.BigDecimal.ZERO) - v
                '*' -> (total ?: java.math.BigDecimal.ZERO) * v
                '/' -> if (v.signum() == 0) return false else (total ?: java.math.BigDecimal.ZERO).divide(v, 2, java.math.RoundingMode.HALF_UP)
                else -> total
            }
            num = StringBuilder(); return true
        }
        for (c in t) {
            if (c.isDigit() || c == '.') num.append(c)
            else if (c in "+-*/") { if (!apply()) return null; op = c }
            else return null
        }
        if (!apply()) return null
        val r = total ?: return null
        if (r.signum() < 0) return null
        return r.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }
}

object Dates {
    val zone: ZoneId get() = ZoneId.systemDefault()
    fun date(ms: Long): LocalDate = Instant.ofEpochMilli(ms).atZone(zone).toLocalDate()
    fun dateTime(ms: Long): LocalDateTime = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
    fun startOf(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
    fun endOf(d: LocalDate): Long = startOf(d.plusDays(1)) - 1
    fun monthRange(ym: YearMonth): LongRange = startOf(ym.atDay(1))..endOf(ym.atEndOfMonth())
    fun today(): LocalDate = LocalDate.now(zone)

    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val dayYearFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val fullFmt = DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a", Locale.ENGLISH)
    private val timeFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private val monShort = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)

    fun short(ms: Long): String {
        val d = date(ms); val t = today()
        return when {
            d == t -> timeFmt.format(dateTime(ms))
            d.year == t.year -> dayFmt.format(d)
            else -> dayYearFmt.format(d)
        }
    }
    fun dayMonth(d: LocalDate): String = dayFmt.format(d)
    fun dayMonthYear(d: LocalDate): String = dayYearFmt.format(d)
    fun full(ms: Long): String = fullFmt.format(dateTime(ms))
    fun time(ms: Long): String = timeFmt.format(dateTime(ms))
    fun month(ym: YearMonth): String = monthFmt.format(ym)
    fun mon(ym: YearMonth): String = monShort.format(ym)

    fun dayHeader(d: LocalDate): String {
        val t = today()
        val base = DateTimeFormatter.ofPattern(if (d.year == t.year) "d MMM" else "d MMM yyyy", Locale.ENGLISH).format(d).uppercase()
        return when (d) {
            t -> "TODAY · $base"
            t.minusDays(1) -> "YESTERDAY · $base"
            else -> DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH).format(d).uppercase() + " · " + base
        }
    }
}

/** Credit-card cycle maths (pure, unit tested). */
object CardMath {
    private fun dayIn(ym: YearMonth, day: Int): LocalDate = ym.atDay(day.coerceIn(1, ym.lengthOfMonth()))

    /** Next statement date on or after [from]. */
    fun nextStatement(from: LocalDate, statementDay: Int): LocalDate {
        val ym = YearMonth.from(from)
        val thisMonth = dayIn(ym, statementDay)
        return if (!from.isAfter(thisMonth)) thisMonth else dayIn(ym.plusMonths(1), statementDay)
    }

    /** Last statement date strictly before or on [on]. */
    fun lastStatement(on: LocalDate, statementDay: Int): LocalDate {
        val ym = YearMonth.from(on)
        val thisMonth = dayIn(ym, statementDay)
        return if (!on.isBefore(thisMonth)) thisMonth else dayIn(ym.minusMonths(1), statementDay)
    }

    /** Due date that belongs to the statement generated on [statement]. */
    fun dueAfter(statement: LocalDate, dueDay: Int): LocalDate {
        val ym = YearMonth.from(statement)
        val same = dayIn(ym, dueDay)
        return if (same.isAfter(statement)) same else dayIn(ym.plusMonths(1), dueDay)
    }

    /** Interest-free days if you spend on [today]: until the due date of the next statement. */
    fun interestFreeDays(today: LocalDate, statementDay: Int, dueDay: Int): Long {
        if (statementDay <= 0 || dueDay <= 0) return 0
        val st = nextStatement(today, statementDay)
        val due = dueAfter(st, dueDay)
        return java.time.temporal.ChronoUnit.DAYS.between(today, due)
    }

    /** Due date of the current (already generated) bill. */
    fun currentDue(today: LocalDate, statementDay: Int, dueDay: Int): LocalDate? {
        if (statementDay <= 0 || dueDay <= 0) return null
        return dueAfter(lastStatement(today, statementDay), dueDay)
    }

    fun minDue(bill: Long): Long = if (bill <= 0) 0 else maxOf(minOf(bill, 20000L), bill * 5 / 100)
}
