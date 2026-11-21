package com.sushant.bahikhata

import com.sushant.bahikhata.sms.SmsKind
import com.sushant.bahikhata.sms.SmsParser
import com.sushant.bahikhata.sms.Senders
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SMS corpus in the formats Indian banks, card issuers and UPI apps send.
 * Every financial SMS must be either parsed or land in "needs a look" (UNKNOWN_MONEY) — never NOT_FINANCIAL.
 * OTPs and promos must never become transactions.
 */
class SmsParserTest {
    data class Case(val sender: String, val body: String, val kind: SmsKind, val amount: Long = -1, val last4: String? = null, val merchant: String? = null, val upi: String? = null, val balance: Long = -2)

    private val cases = listOf(
        // ---- card spends
        Case("VM-HDFCBK-T", "Rs.412.00 spent on HDFC Bank Card 8812 at ZOMATO on 21-11-26. Not you? Call 18002586161", SmsKind.DEBIT, 41200, "8812", "Zomato"),
        Case("VM-SBICRD-T", "Trxn. of Rs.3,456.00 done on SBI Card ending 9911 at FLIPKART PAYMENTS on 21/11/26. Avl lmt Rs.46,544.", SmsKind.DEBIT, 345600, "9911", "Flipkart"),
        Case("AD-ICICIT-T", "INR 1,299.00 spent using ICICI Bank Card XX3301 on 20-Nov-26 on AMAZON PAY IN. Avl Limit: INR 48,701.00. If not you, call 1800 2662/SMS BLOCK 3301 to 9215676766.", SmsKind.DEBIT, 129900, "3301"),
        Case("JD-AXISBK-S", "Spent INR 850 Axis Bank Card no. XX4455 21-11-26 14:22:10 IST DOMINOS PIZZA Avl Limit: INR 59150 Not you? SMS BLOCK 4455 to 919951860002", SmsKind.DEBIT, 85000, "4455"),
        Case("VK-KOTAKB-T", "Rs. 600.00 spent on your Kotak Credit Card x7788 at BOOKMYSHOW on 19-Nov-2026. Avl limit Rs.24,400.", SmsKind.DEBIT, 60000, "7788", "BookMyShow"),
        Case("BX-IDFCFB-S", "INR 2,150.00 spent on your IDFC FIRST Bank Credit Card ending XX6612 at RELIANCE RETAIL on 18-NOV-2026 at 07:41 PM.", SmsKind.DEBIT, 215000, "6612"),
        Case("VM-ONECRD-S", "You've spent Rs. 499 at NETFLIX on your OneCard ending 2201.", SmsKind.DEBIT, 49900, "2201", "Netflix"),
        // ---- UPI debits
        Case("JD-SBIUPI-S", "Dear UPI user A/C X4417 debited by 230.0 on date 21Nov26 trf to RAVI KIRANA Refno 532511234567. If not u? call 1800111109. -SBI", SmsKind.DEBIT, 23000, "4417", "Ravi Kirana"),
        Case("AD-PHONPE-S", "Sent Rs.250 from A/c XX4417 to Rahul Kumar (rahul.k@ybl) on 21-11-2026. UPI Ref 532599887766", SmsKind.DEBIT, 25000, "4417", "Rahul Kumar", "PhonePe"),
        Case("BP-KOTAKB-T", "Rs 1,200 debited from Kotak Bank A/c x2290 to VPA zepto.kiranakart@ybl on 20-11-26. UPI Ref 4123344. Not you, call 18602662666", SmsKind.DEBIT, 120000, "2290", "Zepto", "PhonePe"),
        Case("VM-HDFCBK-T", "Sent Rs.180.00\nFrom HDFC Bank A/C *1234\nTo SWIGGY\nOn 21/11/26\nRef 532611112222\nNot You?\nCall 18002586161/SMS BLOCK UPI to 7308080808", SmsKind.DEBIT, 18000, "1234", "Swiggy"),
        Case("AX-ICICIB-S", "ICICI Bank Acct XX101 debited for Rs 75.00 on 21-Nov-26; AMIT TEA STALL credited. UPI:532633334444. Call 18002662 for dispute. SMS BLOCK 101 to 9215676766.", SmsKind.DEBIT, 7500, "101"),
        Case("VD-BOBTXN-T", "Rs.340.00 Dr. from A/C XXXXXX6677 and Cr. to vpa jio@okaxis. Ref:532655556666. AvlBal:Rs11,200.50(2026:11:21 10:12:09). Not you? Call 18005700-BOB", SmsKind.DEBIT, 34000, "6677", upi = "GPay"),
        Case("JM-PNBSMS-T", "Ac XX9911 debited with Rs.1,000.00 on 20-11-26 18:30:12 for UPI/532677778888/Sonu. Aval Bal Rs.5,432.10 CR. Download PNB ONE-PNB", SmsKind.DEBIT, 100000, "9911"),
        Case("VK-CANBNK-S", "An amount of INR 560.00 has been DEBITED to your account XXX2345 on 21/11/2026 towards UPI/532699990000/MEDPLUS. Total Avail.bal INR 8,900.00. - Canara Bank", SmsKind.DEBIT, 56000, "2345"),
        Case("AX-AMAZON-S", "Rs 349 paid to Jio Recharge using Amazon Pay balance. Order #405-123", SmsKind.DEBIT, 34900, upi = "Amazon Pay"),
        Case("AD-PAYTMB-S", "Rs.120 paid to Uber India from Paytm Wallet. Txn ID 41234567890. Balance Rs.380", SmsKind.DEBIT, 12000),
        // ---- credits
        Case("BZ-SBIINB-T", "Your a/c no. XXXXXXXX4417 is credited by Rs.28,000.00 on 01-11-26 by a/c linked to mobile 9XXXXXX123 (IMPS Ref no 412345678901). Avl Bal Rs 38,450.00", SmsKind.CREDIT, 2800000, "4417", balance = 3845000),
        Case("VD-BOBTXN-T", "Rs.5000 Credited to A/c ...6677 thru UPI/532512345678 by amit.s@oksbi. Total Bal:Rs.12,345.00CR. Avlbl Amt:Rs.12,345.00(21-11-2026 10:15:00) - Bank of Baroda", SmsKind.CREDIT, 500000, "6677", "Amit S", "GPay"),
        Case("VM-HDFCBK-T", "Update! INR 45,000.00 deposited in HDFC Bank A/c XX1234 on 31-OCT-26 for NEFT Cr-ACME SOFTWARE PVT LTD-SALARY OCT. Avl bal INR 52,300.10.", SmsKind.CREDIT, 4500000, "1234", balance = 5230010),
        Case("AD-KOTAKB-S", "Received Rs.500.00 in your Kotak Bank A/c XX2290 from priya.sharma@okicici on 20-11-26.UPI Ref:532688889999.", SmsKind.CREDIT, 50000, "2290", upi = "GPay"),
        Case("JK-ICICIB-S", "Dear Customer, Acct XX101 is credited with Rs 1,250.00 on 19-Nov-26 from VIKASH RAJ. UPI:532612121212-ICICI Bank.", SmsKind.CREDIT, 125000, "101", "Vikash Raj"),
        Case("VM-PHONPE-S", "Received Rs. 300 from Sonu Singh on PhonePe. Money credited to your SBI A/C XX4417.", SmsKind.CREDIT, 30000, "4417"),
        // ---- refunds / reversals
        Case("AD-ICICIT-T", "Rs 599.00 credited to your ICICI Bank Credit Card XX3301 towards refund on 20-Nov-26.", SmsKind.REFUND, 59900, "3301"),
        Case("VM-SBIUPI-S", "Dear SBI UPI User, Rs.230 reversed to your A/c XX4417 for failed txn Ref 532511234567 on 21Nov26.", SmsKind.REFUND, 23000, "4417"),
        // ---- ATM
        Case("VM-AXISBK-T", "INR 2,000.00 debited from A/c no. XX2290 on 18-11-2026 at ATM NFS PATNA. Avl Bal INR 5,460.00", SmsKind.ATM, 200000, "2290", balance = 546000),
        Case("JD-SBIINB-T", "Dear Customer, Rs.3000 withdrawn at ATM S1NW000123 from A/c X4417 on 20Nov26 Transaction Number 2345. Available Balance Rs.35450.", SmsKind.ATM, 300000, "4417"),
        // ---- card bill / due / payment
        Case("VK-HDFCBK-T", "Statement for HDFC Bank Credit Card XX8812: Total Amt Due Rs.6,240.00, Min Amt Due Rs.320.00, Payment due by 25-11-2026.", SmsKind.BILL, 624000, "8812"),
        Case("VM-SBICRD-T", "E-statement of SBI Card ending 9911 dated 15/11/2026 has been mailed. Total Amt Due Rs.12,500; Min Amt Due Rs.625; Payable by 05/12/2026.", SmsKind.BILL, 1250000, "9911"),
        Case("AX-ICICIT-T", "Your ICICI Bank Credit Card XX3301 payment of Rs 4,300 is due on 28-Nov-26. Minimum amount due is Rs 215. Please ignore if paid.", SmsKind.BILL, 430000, "3301"),
        Case("JM-HDFCBK-T", "Payment of Rs. 6,240 has been received on your HDFC Bank Credit Card XX8812. Thank you.", SmsKind.CARD_PAYMENT, 624000, "8812"),
        Case("VK-SBICRD-T", "We have received payment of Rs.12,500.00 via UPI on 02-Dec-26 towards your SBI Card ending 9911. Thank you.", SmsKind.CARD_PAYMENT, 1250000, "9911"),
        // ---- failed
        Case("VM-ICICIB-T", "Transaction of INR 1,850.00 on ICICI Bank Card XX3301 at MYNTRA DESIGNS has been declined due to insufficient limit.", SmsKind.FAILED),
        Case("AD-SBIUPI-S", "Your UPI transaction of Rs.500 to rahul.k@ybl has failed. Amount if debited will be reversed in 3 working days.", SmsKind.FAILED),
        // ---- autopay / balance
        Case("VM-HDFCBK-S", "UPI AutoPay mandate of Rs 199 for SPOTIFY created successfully on your HDFC Bank A/c XX1234. Next debit 01-Dec-26.", SmsKind.AUTOPAY_SETUP),
        Case("JD-SBIINB-S", "Avl Bal in A/c XX4417 is Rs.38,450.00 as on 21-11-26 10:00. -SBI", SmsKind.BALANCE),
        // ---- must be ignored
        Case("VM-HDFCBK-T", "Your OTP for transaction of Rs 1,299 at AMAZON is 482910. Do not share it with anyone.", SmsKind.OTP),
        Case("AD-SBIOTP-T", "OTP is 456123 for login to YONO SBI. Valid for 5 mins. Do not share. -SBI", SmsKind.OTP),
        Case("VM-KOTAKB-P", "Get up to 10% cashback on Kotak Credit Card. Apply now: bit.ly/xyz", SmsKind.PROMO),
        Case("BZ-HDFCBK-P", "Congratulations! You are pre-approved for a Personal Loan of Rs 5,00,000. Click here to apply. T&C apply.", SmsKind.PROMO),
        Case("+919876543210", "Bhai 500 bhej de kal", SmsKind.NOT_FINANCIAL),
        Case("JK-JIOINF-S", "Your Jio number has 1.5 GB data left today. Recharge now with Rs 299 plan.", SmsKind.PROMO),
    )

    @Test fun corpus() {
        val failures = ArrayList<String>()
        for (c in cases) {
            val p = SmsParser.parse(c.sender, c.body)
            fun bad(what: String) = failures.add("${c.sender}: $what\n   ${c.body}\n   -> $p")
            if (p.kind != c.kind) { bad("kind ${p.kind} != ${c.kind}"); continue }
            if (c.amount >= 0 && p.amountPaise != c.amount) bad("amount ${p.amountPaise} != ${c.amount}")
            if (c.last4 != null && p.last4 != c.last4) bad("last4 '${p.last4}' != '${c.last4}'")
            if (c.merchant != null && !p.merchant.equals(c.merchant, true)) bad("merchant '${p.merchant}' != '${c.merchant}'")
            if (c.upi != null && p.upiApp != c.upi) bad("upi '${p.upiApp}' != '${c.upi}'")
            if (c.balance > -2 && p.balancePaise != c.balance) bad("balance ${p.balancePaise} != ${c.balance}")
        }
        assertTrue("SMS parser failures (${failures.size}/${cases.size}):\n" + failures.joinToString("\n"), failures.isEmpty())
    }

    @Test fun everyMoneySmsIsParsedOrNeedsALook() {
        val money = cases.filter { it.kind != SmsKind.OTP && it.kind != SmsKind.PROMO && it.kind != SmsKind.NOT_FINANCIAL }
        money.forEach { c -> val k = SmsParser.parse(c.sender, c.body).kind
            assertTrue("dropped: ${c.body}", k != SmsKind.NOT_FINANCIAL && k != SmsKind.OTP && k != SmsKind.PROMO) }
    }

    @Test fun unknownFormatGoesToNeedsALook() {
        val p = SmsParser.parse("AX-XYZBNK-T", "Your acct 99887766 has a txn Rs 450.00 on 21/11. Bal Rs 1,000.")
        assertTrue(p.kind == SmsKind.UNKNOWN_MONEY || p.isTransaction)
    }

    @Test fun billFields() {
        val p = SmsParser.parse("VK-HDFCBK-T", "Statement for HDFC Bank Credit Card XX8812: Total Amt Due Rs.6,240.00, Min Amt Due Rs.320.00, Payment due by 25-11-2026.")
        assertEquals(624000L, p.billPaise); assertEquals(32000L, p.minDuePaise); assertEquals(java.time.LocalDate.of(2026, 11, 25), p.dueDate)
    }

    @Test fun senders() {
        assertEquals("HDFCBK" to 'T', Senders.split("VM-HDFCBK-T"))
        assertEquals("SBIUPI", Senders.split("JD-SBIUPI").first)
        assertEquals("", Senders.split("+919876543210").first)
        assertEquals("HDFC Bank", Senders.bankFor("HDFCBK"))
        assertEquals("PhonePe", Senders.upiFromVpa("x@ybl")); assertEquals("GPay", Senders.upiFromVpa("x@okaxis")); assertEquals("Paytm", Senders.upiFromVpa("x@paytm"))
    }

    @Test fun shapeSimilarityForLearnedFormats() {
        val a = SmsParser.shape("A/c XX9021 Dr INR 1,150.00 on 19/11 Ref 4471 Avl 8,240")
        val b = SmsParser.shape("A/c XX9021 Dr INR 75.00 on 20/11 Ref 9912 Avl 8,165")
        assertTrue(SmsParser.similarity(a, b) >= 0.6)
    }
}
