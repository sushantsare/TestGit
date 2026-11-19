package com.sushant.gymmanager

import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.local.Shift
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.DuePeriod
import com.sushant.gymmanager.util.WhatsAppHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.time.LocalDate

class DateUtilsTest {
    @Test fun expiry_oneMonth() = assertEquals("2026-02-15", DateUtils.calculateExpiryDate("2026-01-15", PlanType.ONE_MONTH))

    @Test fun expiry_threeMonths() = assertEquals("2026-04-30", DateUtils.calculateExpiryDate("2026-01-30", PlanType.THREE_MONTHS))

    @Test fun expiry_monthEndClamps() = assertEquals("2026-02-28", DateUtils.calculateExpiryDate("2026-01-31", PlanType.ONE_MONTH))

    @Test fun expiry_twelveMonths() = assertEquals("2027-03-01", DateUtils.calculateExpiryDate("2026-03-01", PlanType.TWELVE_MONTHS))

    @Test fun expiry_customMonthsAndDays() =
        assertEquals("2026-03-25", DateUtils.calculateExpiryDate("2026-01-15", PlanType.CUSTOM, customMonths = 2, customDays = 10))

    @Test fun expiry_customDaysOnly() =
        assertEquals("2026-01-29", DateUtils.calculateExpiryDate("2026-01-15", PlanType.CUSTOM, customMonths = 0, customDays = 14))

    @Test fun shift_insideMorningWindow() = assertEquals(Shift.MORNING, DateUtils.determineShift("06:00", "05:30", "10:30"))

    @Test fun shift_morningBoundaries() {
        assertEquals(Shift.MORNING, DateUtils.determineShift("05:30", "05:30", "10:30"))
        assertEquals(Shift.MORNING, DateUtils.determineShift("10:30", "05:30", "10:30"))
    }

    @Test fun shift_outsideMorningWindow() {
        assertEquals(Shift.EVENING, DateUtils.determineShift("05:29", "05:30", "10:30"))
        assertEquals(Shift.EVENING, DateUtils.determineShift("10:31", "05:30", "10:30"))
        assertEquals(Shift.EVENING, DateUtils.determineShift("18:00", "05:30", "10:30"))
    }

    @Test fun format12Hour() {
        assertEquals("6:00 AM", DateUtils.formatTo12Hour("06:00"))
        assertEquals("7:30 PM", DateUtils.formatTo12Hour("19:30"))
        assertEquals("12:00 PM", DateUtils.formatTo12Hour("12:00"))
        assertEquals("12:15 AM", DateUtils.formatTo12Hour("00:15"))
    }

    @Test fun formatTimeSlot() = assertEquals("6:00 AM – 7:30 AM", DateUtils.formatTimeSlot("06:00", "07:30"))

    @Test fun expiringSoonAndExpired() {
        val today = LocalDate.of(2026, 11, 19)
        assertTrue(DateUtils.isExpiringSoon("2026-11-19", 7, today))
        assertTrue(DateUtils.isExpiringSoon("2026-11-26", 7, today))
        assertFalse(DateUtils.isExpiringSoon("2026-11-27", 7, today))
        assertFalse(DateUtils.isExpiringSoon("2026-11-18", 7, today))
        assertTrue(DateUtils.isExpired("2026-11-18", today))
        assertFalse(DateUtils.isExpired("2026-11-19", today))
    }

    @Test fun formatDates() {
        assertEquals("5 March 2026", DateUtils.formatFull("2026-03-05"))
        assertEquals("5 Mar 2026", DateUtils.formatShort("2026-03-05"))
        assertEquals("", DateUtils.formatShort(""))
    }
}

class WhatsAppHelperTest {
    @Test fun normalize_tenDigits() = assertEquals("919876543210", WhatsAppHelper.normalizePhone("98765 43210"))

    @Test fun normalize_leadingZero() = assertEquals("919876543210", WhatsAppHelper.normalizePhone("09876543210"))

    @Test fun normalize_alreadyInternational() = assertEquals("919876543210", WhatsAppHelper.normalizePhone("+91 98765-43210"))

    @Test fun url_containsNumberAndEncodedMessage() {
        val msg = WhatsAppHelper.buildReminderMessage(
            "Rahul Kumar", "PowerFit Gym",
            listOf(DuePeriod("2026-10-01", "2026-11-01", 1000.0), DuePeriod("2026-11-01", "2026-12-01", 1000.0))
        )
        val url = WhatsAppHelper.generateWhatsAppUrl("9876543210", msg)
        assertTrue(url.startsWith("https://wa.me/919876543210?text="))
        val encoded = url.substringAfter("?text=")
        assertFalse("spaces must be encoded", encoded.contains(" "))
        assertFalse("plus signs must not stand for spaces", encoded.contains("+"))
        val decoded = URLDecoder.decode(encoded, "UTF-8")
        assertTrue(decoded.contains("Rahul Kumar"))
        assertTrue(decoded.contains("PowerFit Gym"))
        assertTrue(decoded.contains("₹2,000"))
        assertTrue(decoded.contains("1 Oct 2026 – 1 Nov 2026"))
    }

    @Test fun url_withoutMessage() = assertEquals("https://wa.me/919876543210", WhatsAppHelper.generateWhatsAppUrl("9876543210"))
}

class NewFeatureUtilsTest {
    @Test fun qrRoundTrip() {
        val code = com.sushant.gymmanager.data.repository.GymRepository.qrContent("42")
        assertEquals("42", com.sushant.gymmanager.data.repository.GymRepository.parseQr(code))
        assertEquals("7", com.sushant.gymmanager.data.repository.GymRepository.parseQr("7"))
    }

    @Test fun excelColumnNames() {
        assertEquals("A", com.sushant.gymmanager.util.XlsxWriter.colName(0))
        assertEquals("Z", com.sushant.gymmanager.util.XlsxWriter.colName(25))
        assertEquals("AA", com.sushant.gymmanager.util.XlsxWriter.colName(26))
    }

    @Test fun excelFileIsValidZip() {
        val out = java.io.ByteArrayOutputStream()
        com.sushant.gymmanager.util.XlsxWriter().sheet("Payments", listOf("Name", "Amount"), listOf(listOf("A & B <x>", 1000.0))).write(out)
        val names = mutableListOf<String>()
        java.util.zip.ZipInputStream(java.io.ByteArrayInputStream(out.toByteArray())).use { z ->
            while (true) { val e = z.nextEntry ?: break; names += e.name }
        }
        assertTrue(names.contains("xl/workbook.xml"))
        assertTrue(names.contains("xl/worksheets/sheet1.xml"))
    }

    @Test fun streaks() {
        fun a(d: String, p: Boolean) = com.sushant.gymmanager.data.local.AttendanceEntity(memberId = 1, attendanceDate = d, shift = "MORNING", isPresent = p)
        val s = com.sushant.gymmanager.ui.attendance.computeStreaks(
            listOf(a("2026-11-01", true), a("2026-11-02", true), a("2026-11-03", true), a("2026-11-04", false), a("2026-11-05", true), a("2026-11-06", true))
        )
        assertEquals(2, s.current)
        assertEquals(3, s.best)
    }

    @Test fun cycleBalance() {
        val c = com.sushant.gymmanager.data.local.PaymentCycleEntity(memberId = 1, cycleStart = "2026-11-01", cycleEnd = "2026-12-01", amount = 1000.0, paidAmount = 400.0)
        assertEquals(600.0, c.balance, 0.001)
    }
}
