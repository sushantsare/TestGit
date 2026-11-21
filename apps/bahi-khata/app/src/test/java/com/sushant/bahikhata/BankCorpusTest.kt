package com.sushant.bahikhata

import com.sushant.bahikhata.data.Money
import com.sushant.bahikhata.sms.SmsParser
import org.junit.Assert.assertTrue
import org.junit.Test

/** Spec §11 corpus: 675 SMS shapes from 45 Indian banks (public, private, small-finance, payments and gramin banks):
 *  4 currency styles × 4 account styles × 4 merchant styles × 4 date styles, debit and credit wordings. */
class BankCorpusTest {
    @Test fun fortyFiveBanks() {
        val all = BankCorpus.all()
        val bad = all.filter { (kind, sender, body, paise, last4) ->
            val r = SmsParser.parse(sender as String, body as String)
            r.kind.name != kind || r.amountPaise != paise || r.last4 != last4
        }.map { it[2] }
        assertTrue("${all.size} SMS, ${bad.size} wrong:\n" + bad.take(10).joinToString("\n"), all.size >= 600 && BankCorpus.banks.size >= 40 && bad.isEmpty())
    }
}

object BankCorpus {
    val banks = listOf("SBI" to "SBIUPI", "HDFC Bank" to "HDFCBK", "ICICI Bank" to "ICICIB", "Axis Bank" to "AXISBK", "Kotak Bank" to "KOTAKB", "PNB" to "PNBSMS",
        "Bank of Baroda" to "BOBTXN", "Canara Bank" to "CANBNK", "Union Bank" to "UNIONB", "Bank of India" to "BOIIND", "Indian Bank" to "INDBNK", "Central Bank of India" to "CENTBK",
        "Indian Overseas Bank" to "IOBCHN", "UCO Bank" to "UCOBNK", "Bank of Maharashtra" to "MAHABK", "Punjab & Sind Bank" to "PSBANK", "IDBI Bank" to "IDBIBK", "Yes Bank" to "YESBNK",
        "IndusInd Bank" to "INDUSB", "Federal Bank" to "FEDBNK", "IDFC FIRST Bank" to "IDFCFB", "RBL Bank" to "RBLBNK", "Bandhan Bank" to "BDNSMS", "South Indian Bank" to "SIBSMS",
        "Karur Vysya Bank" to "KVBANK", "Karnataka Bank" to "KBLBNK", "City Union Bank" to "CUBLTD", "DCB Bank" to "DCBBNK", "Dhanlaxmi Bank" to "DHANBK", "J&K Bank" to "JKBANK",
        "TMB" to "TMBANK", "AU Bank" to "AUBANK", "Equitas Bank" to "EQUTAS", "Ujjivan SFB" to "UJJIVN", "Jana Bank" to "JANABK", "Airtel Payments Bank" to "AIRBNK",
        "Paytm Payments Bank" to "PAYTMB", "IPPB" to "IPBMSG", "Fino Bank" to "FINOBK", "StanChart" to "SCBANK", "HSBC" to "HSBCIN", "Citibank" to "CITIBK",
        "DBS Bank" to "DBSBNK", "Saraswat Bank" to "SARBNK", "Dakshin Bihar Gramin Bank" to "DBGBNK")
    val cur = listOf("Rs.", "Rs ", "INR ", "₹")
    val acc = listOf("A/c XX%s", "a/c no. XXXXXX%s", "Acct *%s", "account ending %s")
    val debitVerb = listOf("debited", "has been debited", "was debited")
    val creditVerb = listOf("credited", "has been credited", "is credited")
    val mer = listOf("to VPA %m@okaxis (UPI Ref 532612345678)", "at %M", "towards %M", "Info: UPI/P2M/532612345678/%M")
    val dates = listOf("21-11-26", "21/11/2026", "21Nov26", "21-Nov-2026")
    /** Returns (expected kind, sender, body, paise, last4). */
    fun all(): List<Array<Any>> {
        val out = ArrayList<Array<Any>>()
        val shops = listOf("Big Bazaar", "Reliance Fresh", "Apollo Pharmacy", "Ola Cabs", "IRCTC", "DMart", "Vishal Mega Mart", "Patna Sweets")
        var k = 0
        banks.forEachIndexed { bi, (bank, hdr) ->
            for (v in 0 until 15) {
                k++
                val credit = v % 5 == 4
                val c = cur[(v + bi) % 4]; val a = acc[(v / 2 + bi) % 4]; val d = dates[(v + bi * 3) % 4]
                val paise = 100L * (37 + (k * 7919) % 90000) + (k % 100)
                val rupees = paise / 100
                val amt = (if (k % 2 == 0) Money.groupIndian(rupees) else rupees.toString()) + "." + (paise % 100).toString().padStart(2, '0')
                val last4 = (1000 + (k * 37) % 8999).toString()
                val shop = shops[k % shops.size]
                val m = mer[(v + bi) % 4].replace("%m", shop.lowercase().replace(" ", "")).replace("%M", shop.uppercase())
                val body = if (!credit) when (v % 3) {
                    0 -> "$c$amt ${debitVerb[v % 3]} from ${a.format(last4)} on $d $m. Avl Bal $c" + "12,345.67 -$bank"
                    1 -> "Dear Customer, your ${a.format(last4)} is ${debitVerb[1]} for $c$amt on $d $m. Not you? Call 1800 -$bank"
                    else -> "$bank: ${a.format(last4)} ${debitVerb[2]} $c$amt on $d; $m. Bal: $c" + "5,000.00"
                } else when (v % 2) {
                    0 -> "$c$amt ${creditVerb[v % 3]} to your ${a.format(last4)} on $d by NEFT from RAMESH KUMAR. Avl Bal $c" + "9,876.00 -$bank"
                    else -> "Dear Customer, ${a.format(last4)} ${creditVerb[1]} with $c$amt on $d (UPI Ref 532612345678) -$bank"
                }
                out += arrayOf(if (credit) "CREDIT" else "DEBIT", "VM-$hdr-T", body, paise, last4)
            }
        }
        return out
    }
}

