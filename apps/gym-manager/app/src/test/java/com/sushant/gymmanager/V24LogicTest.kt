package com.sushant.gymmanager

import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.data.local.Shift
import com.sushant.gymmanager.data.repository.GymRepository
import com.sushant.gymmanager.util.CsvImport
import com.sushant.gymmanager.util.WhatsAppHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class V24LogicTest {
    private val settings = GymSettingsEntity()
    private val today = LocalDate.of(2026, 11, 19)

    private fun cycle(id: Long, start: String, amount: Double, paid: Double = 0.0, waived: Double = 0.0) =
        PaymentCycleEntity(id = id, memberId = 1, cycleStart = start, cycleEnd = start, amount = amount, paidAmount = paid, waivedAmount = waived)

    @Test fun allocate_oldestFirst() {
        val map = GymRepository.allocate(listOf(cycle(2, "2026-11-01", 1000.0), cycle(1, "2026-10-01", 1000.0)), 1300.0)
        assertEquals(1000.0, map[1]!!, 0.001)
        assertEquals(300.0, map[2]!!, 0.001)
    }

    @Test fun allocate_neverMoreThanBalance() {
        val map = GymRepository.allocate(listOf(cycle(1, "2026-10-01", 1000.0, paid = 400.0)), 5000.0)
        assertEquals(600.0, map[1]!!, 0.001)
    }

    @Test fun balance_countsWaived() {
        val c = cycle(1, "2026-10-01", 1000.0, paid = 300.0, waived = 200.0)
        assertEquals(500.0, c.balance, 0.001)
        assertTrue(c.settledWith(800.0))
    }

    @Test fun receiptNumberParsing() {
        assertEquals(12L, GymRepository.parseReceiptNo("GM-00012"))
        assertNull(GymRepository.parseReceiptNo("R5x"))
    }

    @Test fun csv_withHeaderAnyOrder() {
        val text = "Name,Roll,WhatsApp,From,To,Plan (months),Fee,Joining Date\n" +
            "Aarav Sharma,101,98765 43210,6:00 AM,7:00 AM,3,2500,01/11/2026\n" +
            "Neha,102,9123456789,18:30,,1,,\n"
        val r = CsvImport.parse(text, settings, emptySet(), today)
        assertEquals(2, r.rows.size)
        val (m, plan) = r.rows[0]
        assertEquals("101", m.rollNumber)
        assertEquals("9876543210", m.whatsappNumber)
        assertEquals("06:00", m.fromTime)
        assertEquals("2026-11-01", m.dateOfJoining)
        assertEquals(Shift.MORNING, m.shift)
        assertEquals(PlanType.THREE_MONTHS, plan.planType)
        assertEquals(2500.0, plan.fee, 0.001)
        val (n, plan2) = r.rows[1]
        assertEquals("19:30", n.toTime)
        assertEquals(Shift.EVENING, n.shift)
        assertEquals(settings.fee1Month, plan2.fee, 0.001)
        assertEquals(today.toString(), n.dateOfJoining)
    }

    @Test fun csv_reportsBadRowsAndDuplicates() {
        val text = "Roll,Name,WhatsApp\n1,Ravi,9876543210\n1,Ravi Again,9876543211\n2,,9876543212\n3,Short,12345\n7,Taken,9876543213\n"
        val r = CsvImport.parse(text, settings, setOf("7"), today)
        assertEquals(1, r.rows.size)
        assertEquals(4, r.problems.size)
        assertTrue(r.problems[0].startsWith("Row 3"))
    }

    @Test fun csv_quotedValuesAndSemicolons() {
        assertEquals(listOf("a", "b, c", "d\"e"), CsvImport.splitRow("a,\"b, c\",\"d\"\"e\""))
        val r = CsvImport.parse("5;Sunny Raj;9445566778", settings, emptySet(), today)
        assertEquals("Sunny Raj", r.rows.single().first.fullName)
    }

    @Test fun csv_timesAndDates() {
        assertEquals(LocalTime.of(18, 0), CsvImport.parseTime("6 PM"))
        assertEquals(LocalTime.of(0, 30), CsvImport.parseTime("12:30 am"))
        assertEquals(LocalTime.of(7, 15), CsvImport.parseTime("7.15"))
        assertNull(CsvImport.parseTime("25:00"))
        assertEquals(LocalDate.of(2026, 3, 5), CsvImport.parseDate("5/3/2026"))
        assertEquals(LocalDate.of(2026, 3, 5), CsvImport.parseDate("2026-03-05"))
        assertNull(CsvImport.parseDate("someday"))
    }

    @Test fun missYouMessage() {
        val msg = WhatsAppHelper.buildMissYouMessage("Rohit", "PowerFit Gym")
        assertTrue(msg.startsWith("Hello Rohit,"))
        assertTrue(msg.contains("PowerFit Gym"))
    }
}
