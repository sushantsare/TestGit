package com.sushant.bahikhata

import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.sms.SmsKind
import com.sushant.bahikhata.sms.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class LogicTest {
    @Test fun money() {
        assertEquals("₹1,00,000", Money.fmt(10_000_000))
        assertEquals("₹1,23,45,678.50", Money.fmt(1_234_567_850))
        assertEquals("−₹1,800", Money.fmt(-180_000))
        assertEquals("+₹500", Money.fmt(50_000, sign = true))
        assertEquals("₹0.05", Money.fmt(5))
        assertEquals("₹1.2K", Money.compact(120_000))
        assertEquals("₹3.4L", Money.compact(34_000_000))
        assertEquals("₹2.1Cr", Money.compact(2_100_000_000))
        assertEquals(125_050L, Money.parse("1,250.50")); assertEquals(null, Money.parse("abc")); assertEquals(null, Money.parse(""))
        assertEquals("386.5", Money.input(38_650)); assertEquals("386", Money.input(38_600))
    }

    @Test fun calculator() {
        assertEquals(50_000L, Calc.eval("120+380"))
        assertEquals(26_000L, Calc.eval("300−40"))
        assertEquals(1_250L, Calc.eval("12.5"))
        assertEquals(null, Calc.eval("5−10"))
        assertEquals(null, Calc.eval(""))
    }

    @Test fun cardCycle() {
        val today = LocalDate.of(2026, 11, 21)
        assertEquals(LocalDate.of(2026, 12, 5), CardMath.nextStatement(today, 5))
        assertEquals(LocalDate.of(2026, 11, 5), CardMath.lastStatement(today, 5))
        assertEquals(LocalDate.of(2026, 11, 25), CardMath.dueAfter(LocalDate.of(2026, 11, 5), 25))
        assertEquals(LocalDate.of(2026, 12, 5), CardMath.dueAfter(LocalDate.of(2026, 11, 15), 5))
        // spend on 21 Nov: statement 5 Dec, due 25 Dec -> 34 days
        assertEquals(34L, CardMath.interestFreeDays(today, 5, 25))
        // statement day 31 in a 30-day month clamps
        assertEquals(LocalDate.of(2026, 11, 30), CardMath.nextStatement(today, 31))
        assertEquals(150_000L, CardMath.minDue(30_000_00))   // 5% of ₹30,000
        assertEquals(10_000L, CardMath.minDue(10_000))       // small bills: whole amount
    }

    @Test fun ledgerBalancesAndTransfers() {
        val bank = Account(id = 1, type = AccType.BANK, name = "SBI", openingPaise = 10_000_00)
        val card = Account(id = 2, type = AccType.CARD, name = "HDFC", openingPaise = -2_000_00, limitPaise = 50_000_00)
        val cash = Account(id = 3, type = AccType.CASH, name = "Cash", openingPaise = 500_00)
        val e = listOf(
            Entry(type = EType.EXPENSE, amountPaise = 300_00, accountId = 2, time = 1),
            Entry(type = EType.EXPENSE, amountPaise = 100_00, accountId = 1, time = 2),
            Entry(type = EType.INCOME, amountPaise = 5_000_00, accountId = 1, time = 3),
            Entry(type = EType.CARD_PAYMENT, amountPaise = 2_300_00, accountId = 1, toAccountId = 2, time = 4),
            Entry(type = EType.CASH_WITHDRAWAL, amountPaise = 1_000_00, accountId = 1, toAccountId = 3, time = 5),
            Entry(type = EType.ADJUSTMENT, amountPaise = -50_00, accountId = 3, time = 6),
            Entry(type = EType.UDHAR_GAVE, amountPaise = 200_00, accountId = 3, time = 7),
        )
        val n = Ledger.nets(listOf(bank, card, cash), e)
        assertEquals(10_000_00L - 100_00 + 5_000_00 - 2_300_00 - 1_000_00, n[1])
        assertEquals(0L, n[2])                       // card fully paid
        assertEquals(500_00L + 1_000_00 - 50_00 - 200_00, n[3])
        // only real spending/income counts
        assertEquals(400_00L, Ledger.spent(e, 0L..100L))
        assertEquals(5_000_00L, Ledger.received(e, 0L..100L))
    }

    @Test fun personBalance() {
        val p = Person(id = 7, name = "Rahul", openingPaise = 1_000_00)
        val u = listOf(Udhar(personId = 7, gave = true, amountPaise = 500_00), Udhar(personId = 7, gave = false, amountPaise = 300_00), Udhar(personId = 8, gave = true, amountPaise = 9_00))
        assertEquals(1_200_00L, Ledger.personBalance(p, u))
    }

    @Test fun recurringDates() {
        assertEquals(LocalDate.of(2026, 12, 21), Ledger.nextDate(LocalDate.of(2026, 11, 21), "MONTHLY"))
        assertEquals(LocalDate.of(2026, 11, 28), Ledger.nextDate(LocalDate.of(2026, 11, 21), "WEEKLY"))
        assertEquals(LocalDate.of(2027, 2, 28), Ledger.nextDate(LocalDate.of(2027, 1, 31), "MONTHLY"))
    }

    @Test fun tags() = assertEquals(listOf("trip", "office"), Ledger.tags("#Trip #office, trip"))

    /** 600+ generated SMS in the common templates with random amounts, cards and merchants. */
    @Test fun generatedCorpus() {
        val rnd = java.util.Random(42)
        val shops = listOf("ZOMATO", "SWIGGY", "AMAZON", "FLIPKART", "RELIANCE FRESH", "HP PETROL PUMP", "APOLLO PHARMACY", "BIG BAZAAR", "IRCTC", "UBER INDIA")
        val templates: List<Pair<String, (String, String, String) -> String>> = listOf(
            SmsKind.DEBIT.name to { a, c, m -> "Rs.$a spent on HDFC Bank Card $c at $m on 21-11-26. Not you? Call 18002586161" },
            SmsKind.DEBIT.name to { a, c, m -> "INR $a spent using ICICI Bank Card XX$c on 20-Nov-26 on $m. Avl Limit: INR 48,701.00." },
            SmsKind.DEBIT.name to { a, c, m -> "Dear UPI user A/C X$c debited by $a on date 21Nov26 trf to $m Refno 532511234567. If not u? call 1800111109. -SBI" },
            SmsKind.DEBIT.name to { a, c, m -> "Rs $a debited from Kotak Bank A/c x$c to VPA shop${c}@ybl on 20-11-26. UPI Ref 4123344." },
            SmsKind.DEBIT.name to { a, c, m -> "Trxn. of Rs.$a done on SBI Card ending $c at $m on 21/11/26. Avl lmt Rs.46,544." },
            SmsKind.DEBIT.name to { a, c, m -> "An amount of INR $a has been DEBITED to your account XXX$c on 21/11/2026 towards UPI/532699990000/$m. Total Avail.bal INR 8,900.00." },
            SmsKind.CREDIT.name to { a, c, m -> "Your a/c no. XXXXXXXX$c is credited by Rs.$a on 01-11-26 by a/c linked to mobile 9XXXXXX123 (IMPS Ref no 412345678901)." },
            SmsKind.CREDIT.name to { a, c, m -> "Received Rs.$a in your Kotak Bank A/c XX$c from friend@okicici on 20-11-26.UPI Ref:532688889999." },
            SmsKind.CREDIT.name to { a, c, m -> "Update! INR $a deposited in HDFC Bank A/c XX$c on 31-OCT-26 for NEFT Cr-$m." },
            SmsKind.ATM.name to { a, c, m -> "INR $a debited from A/c no. XX$c on 18-11-2026 at ATM NFS PATNA. Avl Bal INR 5,460.00" },
            SmsKind.REFUND.name to { a, c, m -> "Rs $a credited to your ICICI Bank Credit Card XX$c towards refund from $m on 20-Nov-26." },
            SmsKind.CARD_PAYMENT.name to { a, c, m -> "Payment of Rs. $a has been received on your HDFC Bank Credit Card XX$c. Thank you." },
        )
        var n = 0; val bad = ArrayList<String>()
        repeat(52) { i ->
            templates.forEach { (kind, f) ->
                val paise = (rnd.nextInt(5_000_000) + 100).toLong()
                val rupees = paise / 100; val p = paise % 100
                val amtText = (if (rnd.nextBoolean()) Money.groupIndian(rupees) else rupees.toString()) + "." + p.toString().padStart(2, '0')
                val card = (1000 + rnd.nextInt(8999)).toString()
                val body = f(amtText, card, shops[i % shops.size])
                val r = SmsParser.parse("VM-HDFCBK-T", body); n++
                if (r.kind.name != kind || r.amountPaise != paise || r.last4 != card) bad += "$kind/${r.kind} ${r.amountPaise}!=$paise ${r.last4}!=$card :: $body"
            }
        }
        assertTrue("$n generated SMS, ${bad.size} wrong:\n" + bad.take(15).joinToString("\n"), bad.isEmpty() && n >= 600)
    }
}
