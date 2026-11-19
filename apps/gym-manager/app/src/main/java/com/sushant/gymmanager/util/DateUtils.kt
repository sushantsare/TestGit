package com.sushant.gymmanager.util

import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.local.Shift
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object DateUtils {
    /** Month/day names follow the app language (English or Hindi). */
    @Volatile
    var locale: Locale = Locale.ENGLISH
        set(value) {
            field = value
            fullFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", value)
            shortFormat = DateTimeFormatter.ofPattern("d MMM yyyy", value)
            dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", value)
            monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", value)
            monthShortFormat = DateTimeFormatter.ofPattern("MMM", value)
        }
    private var fullFormat = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH)
    private var shortFormat = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private var dayFormat = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)
    private var monthFormat = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
    private var monthShortFormat = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
    private val timeFormat = DateTimeFormatter.ofPattern("HH:mm")
    private val time12Format = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)
    private val timestampFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

    fun today(): String = LocalDate.now().toString()

    fun nowTimestamp(): String = LocalDateTime.now().format(timestampFormat)

    fun parseDate(iso: String?): LocalDate? =
        if (iso.isNullOrBlank()) null else runCatching { LocalDate.parse(iso.trim()) }.getOrNull()

    fun parseTime(hhmm: String?): LocalTime? =
        if (hhmm.isNullOrBlank()) null else runCatching { LocalTime.parse(hhmm.trim(), timeFormat) }.getOrNull()

    fun formatFull(iso: String?): String = parseDate(iso)?.format(fullFormat) ?: ""
    fun formatShort(iso: String?): String = parseDate(iso)?.format(shortFormat) ?: ""
    fun formatDayFull(date: LocalDate = LocalDate.now()): String = date.format(dayFormat)
    fun formatMonth(date: LocalDate): String = date.format(monthFormat)
    fun formatMonthShort(date: LocalDate): String = date.format(monthShortFormat)

    fun formatTimestamp(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDateTime()
            .let { it.toLocalDate().format(shortFormat) + ", " + it.format(DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH)) }

    /** "06:00" -> "6:00 AM" */
    fun formatTo12Hour(hhmm: String?): String =
        parseTime(hhmm)?.format(time12Format)?.uppercase(Locale.ENGLISH) ?: (hhmm ?: "")

    /** "06:00","07:30" -> "6:00 AM – 7:30 AM" */
    fun formatTimeSlot(from: String, to: String): String =
        "${formatTo12Hour(from)} – ${formatTo12Hour(to)}"

    fun toHHmm(hour: Int, minute: Int): String = String.format(Locale.ENGLISH, "%02d:%02d", hour, minute)

    /** Morning if morningStart <= fromTime <= morningEnd, otherwise Evening. */
    fun determineShift(fromTime: String, morningStart: String, morningEnd: String): String {
        val from = parseTime(fromTime) ?: return Shift.MORNING
        val start = parseTime(morningStart) ?: LocalTime.of(5, 30)
        val end = parseTime(morningEnd) ?: LocalTime.of(10, 30)
        return if (!from.isBefore(start) && !from.isAfter(end)) Shift.MORNING else Shift.EVENING
    }

    fun monthsFor(planType: String): Int = when (planType) {
        PlanType.ONE_MONTH -> 1
        PlanType.THREE_MONTHS -> 3
        PlanType.SIX_MONTHS -> 6
        PlanType.TWELVE_MONTHS -> 12
        else -> 0
    }

    fun calculateExpiryDate(startDate: String, planType: String, customMonths: Int = 0, customDays: Int = 0): String {
        val start = parseDate(startDate) ?: LocalDate.now()
        val result = if (planType == PlanType.CUSTOM) {
            start.plusMonths(customMonths.coerceAtLeast(0).toLong()).plusDays(customDays.coerceAtLeast(0).toLong())
        } else {
            start.plusMonths(monthsFor(planType).toLong())
        }
        return result.toString()
    }

    fun daysUntil(iso: String, today: LocalDate = LocalDate.now()): Long {
        val d = parseDate(iso) ?: return 0
        return ChronoUnit.DAYS.between(today, d)
    }

    fun isExpired(expiryDate: String, today: LocalDate = LocalDate.now()): Boolean {
        val d = parseDate(expiryDate) ?: return false
        return d.isBefore(today)
    }

    fun isExpiringSoon(expiryDate: String, thresholdDays: Int, today: LocalDate = LocalDate.now()): Boolean {
        val d = parseDate(expiryDate) ?: return false
        return !d.isBefore(today) && !d.isAfter(today.plusDays(thresholdDays.toLong()))
    }

    fun planLabel(planType: String, months: Int = 0, days: Int = 0): String = when (planType) {
        PlanType.ONE_MONTH -> "1 Month"
        PlanType.THREE_MONTHS -> "3 Months"
        PlanType.SIX_MONTHS -> "6 Months"
        PlanType.TWELVE_MONTHS -> "12 Months"
        else -> buildList {
            if (months > 0) add("$months ${if (months == 1) "Month" else "Months"}")
            if (days > 0) add("$days ${if (days == 1) "Day" else "Days"}")
        }.joinToString(" ").ifEmpty { "Custom" }
    }

    fun expiryText(expiryDate: String, today: LocalDate = LocalDate.now()): String {
        val days = daysUntil(expiryDate, today)
        return when {
            days < -1 -> "Expired ${-days} days ago"
            days == -1L -> "Expired yesterday"
            days == 0L -> "Expires today"
            days == 1L -> "Expires tomorrow"
            else -> "Expires in $days days"
        }
    }

    /** Material DatePicker works in UTC milliseconds. */
    fun isoToUtcMillis(iso: String?): Long? =
        parseDate(iso)?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli()

    fun utcMillisToIso(millis: Long): String =
        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate().toString()
}

object Money {
    private val format = NumberFormat.getNumberInstance(Locale("en", "IN")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 0
    }

    fun format(amount: Double): String = (if (amount < 0) "-₹" else "₹") + format.format(kotlin.math.abs(amount))
}
