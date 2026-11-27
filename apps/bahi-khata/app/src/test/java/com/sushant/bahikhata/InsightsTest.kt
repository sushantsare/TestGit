package com.sushant.bahikhata

import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.sms.SmsKind
import com.sushant.bahikhata.sms.SmsParser
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class InsightsTest {
    private val today = LocalDate.now()
    private fun at(daysAgo: Long, h: Int = 12) = Dates.startOf(today.minusDays(daysAgo)) + h * 3_600_000L
    private val fmt = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    // ---------------- "What did you buy?"

    @Test fun itemsWithAndWithoutPrices() {
        fun p(t: String) = Insights.parseItems(t).map { it.name to it.pricePaise }
        assertEquals(listOf("Milk" to 6000L, "Bread" to 4000L, "Eggs" to -1L), p("milk 60, bread 40, eggs"))
        assertEquals(listOf("Atta" to 32050L, "Sugar" to 4500L), p("₹320.50 atta; sugar rs 45"))
        assertEquals(listOf("Tea" to 1000L, "Samosa" to 2000L), p("tea 10 and samosa 20/-"))
        assertTrue(Insights.parseItems(" , ;").isEmpty())
        assertEquals("Milk 60, Eggs", Insights.itemsText(listOf(Insights.Item("Milk", 6000), Insights.Item("Eggs"))))
    }

    @Test fun quantitiesAndPriceForOne() {
        val l = Insights.parseItems("milk 2L 60, 3 eggs 21, rice 5kg 320, curd 500g 40, biscuit x4 40, oil 500ml")
        assertEquals(listOf("Milk", "Eggs", "Rice", "Curd", "Biscuit", "Oil"), l.map { it.name })
        assertEquals(listOf(2.0, 3.0, 5.0, 500.0, 4.0, 500.0), l.map { it.qty })
        assertEquals(listOf("L", "", "kg", "g", "", "ml"), l.map { it.unit })
        assertEquals(listOf(6000L, 2100L, 32000L, 4000L, 4000L, -1L), l.map { it.pricePaise })
        assertEquals(3000L, l[0].unitPricePaise); assertEquals("/L", l[0].unitLabel)
        assertEquals(700L, l[1].unitPricePaise); assertEquals("each", l[1].unitLabel)
        assertEquals(8000L, l[3].unitPricePaise); assertEquals("/kg", l[3].unitLabel)   // ₹40 for 500 g = ₹80 per kg
        // saved text reads back the same
        assertEquals(l, Insights.parseItems(Insights.itemsText(l)))
    }

    @Test fun priceHistoryComparesPriceForOne() {
        val items = listOf(EntryItem(entryId = 1, name = "Milk", pricePaise = 56_00, shopKey = "s", time = at(16), qty = 2.0, unit = "L"),
            EntryItem(entryId = 2, name = "Milk", pricePaise = 30_00, shopKey = "s", time = at(0), qty = 1.0, unit = "L"))
        val l = Insights.priceHistory(items, emptyMap()).single()
        assertEquals(28_00L, l.first); assertEquals(30_00L, l.last); assertEquals("/L", l.per)
    }

    // ---------------- subscriptions

    @Test fun monthlyRepeatIsASubscription() {
        val pays = listOf(2L, 32L, 62L).map { Insights.Payment("Netflix", 649_00, at(it)) } + Insights.Payment("Swiggy", 300_00, at(3)) +
            listOf(5L, 35L).map { Insights.Payment("Gym Fitness", 1_000_00, at(it)) }
        val subs = Insights.subscriptions(pays, today)
        assertEquals(setOf("Netflix", "Gym Fitness"), subs.map { it.name }.toSet())
        val n = subs.first { it.name == "Netflix" }
        assertEquals("Monthly", n.cadence); assertEquals(649_00L, n.monthlyPaise); assertEquals(3, n.count)
    }

    @Test fun stoppedSubscriptionIsDropped() {
        val pays = listOf(120L, 150L, 180L).map { Insights.Payment("Hotstar", 299_00, at(it)) }
        assertTrue(Insights.subscriptions(pays, today).isEmpty())
    }

    @Test fun randomShopIsNotASubscription() {
        val pays = listOf(1L, 9L, 23L, 60L).map { Insights.Payment("Ravi Kirana", (100 + it * 13) * 100, at(it)) }
        assertTrue(Insights.subscriptions(pays, today).isEmpty())
    }

    // ---------------- unusual debits

    @Test fun unusualDebitReasons() {
        val past = (1..30).map { Entry(type = EType.EXPENSE, amountPaise = (100 + it * 10) * 100L, accountId = 1, time = at(it.toLong())) }
        val night = Dates.startOf(today) + 2 * 3_600_000L + 15 * 60_000L
        assertTrue(Insights.unusual(250_00, at(0, 14), 1, false, past).isEmpty())
        assertTrue(Insights.unusual(50_000_00, at(0, 14), 1, false, past).any { it.contains("bigger") })
        assertTrue(Insights.unusual(250_00, night, 1, false, past).any { it.contains("late at night") })
        assertTrue(Insights.unusual(250_00, at(0, 14), 1, true, past).any { it.contains("international") })
        val oldAcc = listOf(Entry(type = EType.EXPENSE, amountPaise = 100_00, accountId = 7, time = at(90)))
        assertTrue(Insights.unusual(250_00, at(0, 14), 7, false, past + oldAcc).any { it.contains("haven't used") })
    }

    // ---------------- ledger: cancelled, refunds, transfers, investments

    @Test fun ledgerHandlesNewKinds() {
        val a = Account(id = 1, type = AccType.BANK, name = "A", openingPaise = 10_000_00)
        val b = Account(id = 2, type = AccType.BANK, name = "B", openingPaise = 0)
        val buy = Entry(id = 1, type = EType.EXPENSE, amountPaise = 1_000_00, accountId = 1, categoryId = 5, time = at(3))
        val es = listOf(
            buy,
            Entry(id = 2, type = EType.INCOME, amountPaise = 400_00, accountId = 1, categoryId = 5, refundOf = 1, time = at(1)),
            Entry(id = 3, type = EType.EXPENSE, amountPaise = 999_00, accountId = 1, cancelled = true, time = at(1)),
            Entry(id = 4, type = EType.TRANSFER, amountPaise = 2_000_00, accountId = 1, toAccountId = 2, time = at(1)),
            Entry(id = 5, type = EType.INVEST, amountPaise = 500_00, accountId = 1, time = at(1)),
        )
        val n = Ledger.nets(listOf(a, b), es)
        assertEquals(10_000_00L - 1_000_00 + 400_00 - 2_000_00 - 500_00, n[1]); assertEquals(2_000_00L, n[2])
        val r = Dates.startOf(today.minusDays(10))..Dates.endOf(today)
        assertEquals(600_00L, Ledger.spent(es, r))
        assertEquals(0L, Ledger.received(es, r))
        assertEquals(600_00L, Ledger.byCategory(es, r)[5])
        assertEquals(10_000_00L - 1_000_00, Ledger.netAt(a, es, at(2)))
    }

    // ---------------- SMS: reminders and tags

    @Test fun rechargeExpiryReminder() {
        val d = today.plusDays(3)
        val p = SmsParser.parse("JM-JIOINF-S", "Your Jio plan of Rs 299 will expire on ${d.format(fmt)}. Recharge now to continue enjoying services.")
        assertEquals(SmsKind.REMINDER, p.kind); assertEquals("RECHARGE", p.remindKind); assertEquals(d, p.remindDate); assertEquals("Jio plan", p.remindTitle)
    }

    @Test fun electricityBillReminder() {
        val d = today.plusDays(9)
        val p = SmsParser.parse("BW-NBPDCL-S", "Dear consumer, your electricity bill of Rs 1,240.00 for consumer no 101234567 is due on ${d.format(fmt)}. Pay on time to avoid surcharge.")
        assertEquals(SmsKind.REMINDER, p.kind); assertEquals("UTILITY", p.remindKind); assertEquals(124_000L, p.amountPaise); assertEquals("Electricity bill", p.remindTitle)
    }

    @Test fun emiUpcomingReminder() {
        val d = today.plusDays(5)
        val p = SmsParser.parse("VM-BAJAJF-S", "Your EMI of Rs 2,150 for loan a/c 4XX9 will be debited on ${d.format(fmt)}. Please keep sufficient balance.")
        assertEquals(SmsKind.REMINDER, p.kind); assertEquals("EMI", p.remindKind); assertEquals(d, p.remindDate)
    }

    @Test fun debitedEmiIsNotAReminder() {
        val p = SmsParser.parse("VM-HDFCBK-T", "Rs 2150.00 debited from A/c XX4417 towards EMI on 05-11-26. Avl bal Rs 5,000.00")
        assertEquals(SmsKind.DEBIT, p.kind); assertTrue("EMI" in p.tags)
    }

    @Test fun tagsAndCategoryHints() {
        val sal = SmsParser.parse("VM-SBIINB-T", "Your A/c XX4417 is credited with Rs 28,000.00 on 01-11-26 by NEFT SALARY ACME PVT LTD. Avl bal Rs 31,000")
        assertEquals(SmsKind.CREDIT, sal.kind); assertTrue("SALARY" in sal.tags); assertEquals("Salary", sal.categoryHint)
        val chg = SmsParser.parse("VM-SBIINB-T", "Rs 17.70 debited from A/c XX4417 towards SMS alert charges for Qtr ending Sep. Avl bal Rs 4,982.30")
        assertTrue("CHARGE" in chg.tags); assertEquals("Bank charges", chg.categoryHint)
        val cb = SmsParser.parse("VM-ICICIT-T", "Cashback of Rs 50.00 credited to your ICICI Bank Credit Card XX3301 on 20-Nov-26.")
        assertTrue("CASHBACK" in cb.tags); assertEquals("Cashback", cb.categoryHint)
        val sip = SmsParser.parse("VM-HDFCBK-T", "Rs 1000.00 debited from A/c XX4417 on 05-11-26 to ICCL Groww SIP. Ref 123456")
        assertTrue("INVEST" in sip.tags)
        val intl = SmsParser.parse("VM-HDFCBK-T", "Rs 1,850.00 spent on HDFC Bank Card 8812 at NETFLIX.COM USD on 21-11-26 (international transaction)")
        assertTrue("INTL" in intl.tags)
        val shop = SmsParser.parse("VM-HDFCBK-T", "Rs 640.00 spent on HDFC Bank Card 8812 at LUCENT BOOKS on 21-11-26")
        assertFalse("CHARGE" in shop.tags); assertFalse("INTL" in shop.tags)
    }

    @Test fun reversalIsTagged() {
        val p = SmsParser.parse("VM-SBIUPI-S", "Rs 230.00 reversed to your A/c XX4417 on 21-11-26 for failed UPI txn Ref 532511234567")
        assertEquals(SmsKind.REFUND, p.kind); assertTrue("REVERSAL" in p.tags)
    }

    // ---------------- price history, people, year

    @Test fun priceHistoryPerShop() {
        val items = listOf(EntryItem(entryId = 1, name = "Milk", pricePaise = 56_00, shopKey = "ravi kirana", time = at(16)),
            EntryItem(entryId = 2, name = "milk", pricePaise = 60_00, shopKey = "ravi kirana", time = at(0)),
            EntryItem(entryId = 2, name = "Bread", pricePaise = 40_00, shopKey = "ravi kirana", time = at(0)))
        val l = Insights.priceHistory(items, mapOf("ravi kirana" to "Ravi Kirana"))
        assertEquals(1, l.size); assertEquals("Ravi Kirana", l[0].shop); assertEquals(4_00L, l[0].change)
    }

    @Test fun peopleSummary() {
        val p = Person(id = 3, name = "Rahul Kumar", openingPaise = 0)
        val names = listOf(UpiName(key = "vpa:rahulk@okaxis", display = "Rahul Kumar", isPerson = true, personId = 3))
        val es = listOf(Entry(type = EType.EXPENSE, amountPaise = 200_00, accountId = 1, merchant = "Rahul Kumar", time = at(1)),
            Entry(type = EType.INCOME, amountPaise = 500_00, accountId = 1, merchant = "rahul kumar", time = at(2)),
            Entry(type = EType.UDHAR_GAVE, amountPaise = 300_00, accountId = 1, personId = 3, time = at(3)),
            Entry(type = EType.EXPENSE, amountPaise = 100_00, accountId = 1, merchant = "Swiggy", time = at(1)))
        val s = Insights.people(es, names, listOf(p), listOf(Udhar(personId = 3, gave = true, amountPaise = 300_00)), 0L..Long.MAX_VALUE)
        assertEquals(1, s.size); assertEquals(500_00L, s[0].paidPaise); assertEquals(500_00L, s[0].gotPaise); assertEquals(300_00L, s[0].udharPaise)
    }

    @Test fun paydayFromSalaryCategory() {
        val es = listOf(Entry(type = EType.INCOME, amountPaise = 28_000_00, accountId = 1, categoryId = 9, merchant = "Salary", time = at(10)),
            Entry(type = EType.INCOME, amountPaise = 27_500_00, accountId = 1, categoryId = 9, merchant = "Salary", time = at(40)),
            Entry(type = EType.EXPENSE, amountPaise = 3_000_00, accountId = 1, time = at(5)))
        val p = Insights.payday(es, setOf(9L), today = today)!!
        assertEquals(10L, p.daysSince); assertEquals(3_000_00L, p.spentSince); assertEquals(0L, p.receivedSince); assertNotNull(p.nextGuess)
    }
}
