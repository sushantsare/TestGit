package com.sushant.bahikhata.sms

import java.time.LocalDate
import java.time.Month
import java.util.Locale

/**
 * Indian bank / card / UPI SMS parser. Pure Kotlin (no Android types) so it is fully unit tested.
 * Steps: sender filter -> classification -> field extraction -> confidence.
 * Anything that looks like money but can't be fully read is returned as [SmsKind.UNKNOWN_MONEY]
 * so the engine puts it in "Needs a look" instead of dropping it.
 */
enum class SmsKind { DEBIT, CREDIT, REFUND, ATM, CARD_PAYMENT, BILL, DUE, FAILED, AUTOPAY_SETUP, BALANCE, REMINDER, OTP, PROMO, UNKNOWN_MONEY, NOT_FINANCIAL }

data class Parsed(
    val kind: SmsKind,
    val amountPaise: Long = 0,
    val debit: Boolean = true,
    val last4: String = "",
    val merchant: String = "",
    val vpa: String = "",
    val upiApp: String = "",
    val ref: String = "",
    val balancePaise: Long = -1,
    val availLimitPaise: Long = -1,
    val billPaise: Long = -1,
    val minDuePaise: Long = -1,
    val dueDate: LocalDate? = null,
    val txnDate: LocalDate? = null,
    val bank: String = "",
    val senderKey: String = "",
    val categoryHint: String = "",
    val confidence: Int = 0,
    /** What else the SMS says about the money: SALARY, CHARGE, CASHBACK, INTEREST, INVEST, INSURANCE, FASTAG, WALLET, INTL, SELF, REVERSAL, EMI. */
    val tags: Set<String> = emptySet(),
    /** REMINDER only: RECHARGE / UTILITY / EMI / AUTOPAY, the date it's due and a short title ("Jio plan", "Electricity bill"). */
    val remindKind: String = "",
    val remindDate: LocalDate? = null,
    val remindTitle: String = "",
) {
    /** A real money movement that should become a suggested entry. */
    val isTransaction get() = kind in setOf(SmsKind.DEBIT, SmsKind.CREDIT, SmsKind.REFUND, SmsKind.ATM, SmsKind.CARD_PAYMENT)
}

object Senders {
    /** DLT header key (middle part of e.g. VM-HDFCBK-T) -> bank / app name. */
    val banks: Map<String, String> = linkedMapOf(
        "HDFCBK" to "HDFC Bank", "HDFCBN" to "HDFC Bank", "HDFCCC" to "HDFC Bank", "HDFC" to "HDFC Bank",
        "SBIUPI" to "SBI", "SBIINB" to "SBI", "SBYONO" to "SBI", "CBSSBI" to "SBI", "ATMSBI" to "SBI", "SBIPSG" to "SBI", "SBIBNK" to "SBI",
        "SBICRD" to "SBI Card", "SBMSBI" to "SBI Card", "SBICARD" to "SBI Card",
        "ICICIB" to "ICICI Bank", "ICICIT" to "ICICI Bank", "ICICI" to "ICICI Bank", "ICIBNK" to "ICICI Bank",
        "AXISBK" to "Axis Bank", "AXISMR" to "Axis Bank", "AXISB" to "Axis Bank", "AXISCC" to "Axis Bank",
        "KOTAKB" to "Kotak", "KOTAK" to "Kotak", "KOTAKM" to "Kotak", "KOTKBK" to "Kotak",
        "PNBSMS" to "PNB", "PNBBNK" to "PNB", "PUNBNK" to "PNB",
        "BOBTXN" to "Bank of Baroda", "BOBSMS" to "Bank of Baroda", "BOBCRD" to "BOB Card", "BARODA" to "Bank of Baroda",
        "CANBNK" to "Canara Bank", "CANARA" to "Canara Bank", "UNIONB" to "Union Bank", "UBIBNK" to "Union Bank", "UBOI" to "Union Bank",
        "IDFCFB" to "IDFC First", "IDFCBK" to "IDFC First", "YESBNK" to "Yes Bank", "YESBK" to "Yes Bank", "INDUSB" to "IndusInd", "INDUSL" to "IndusInd",
        "IOBCHN" to "Indian Overseas Bank", "IOBBNK" to "Indian Overseas Bank", "UCOBNK" to "UCO Bank", "CENTBK" to "Central Bank", "CBOI" to "Central Bank",
        "BOIIND" to "Bank of India", "BOI" to "Bank of India", "IDBIBK" to "IDBI Bank", "IDBI" to "IDBI Bank", "FEDBNK" to "Federal Bank", "FEDFIB" to "Federal Bank",
        "SIBSMS" to "South Indian Bank", "KVBANK" to "Karur Vysya", "RBLBNK" to "RBL Bank", "RBLCRD" to "RBL Bank", "AUBANK" to "AU Bank", "AUBNK" to "AU Bank",
        "PAYTMB" to "Paytm Payments Bank", "AIRBNK" to "Airtel Payments Bank", "AIRTLB" to "Airtel Payments Bank", "JIOPBK" to "Jio Payments Bank",
        "FINOBK" to "Fino", "EQUTAS" to "Equitas", "UJJIVN" to "Ujjivan", "DBSBNK" to "DBS", "SCBANK" to "Standard Chartered", "CITIBK" to "Citi",
        "HSBCIN" to "HSBC", "AMEXIN" to "Amex", "ONECRD" to "OneCard", "SLICEIT" to "slice", "SLCEIT" to "slice", "IPPBNK" to "India Post Payments Bank",
        "BANDHN" to "Bandhan Bank", "DCBBNK" to "DCB Bank", "KARBNK" to "Karnataka Bank", "CSBBNK" to "CSB Bank", "TMBANK" to "TMB", "JKBANK" to "J&K Bank",
        "PSBANK" to "Punjab & Sind Bank", "INDBNK" to "Indian Bank", "INDIAN" to "Indian Bank", "MAHABK" to "Bank of Maharashtra", "MAHBNK" to "Bank of Maharashtra",
        "APGVBK" to "APGVB", "BGGBNK" to "Bihar Gramin Bank", "DBGBNK" to "Dakshin Bihar Gramin Bank", "UBGBNK" to "Uttar Bihar Gramin Bank",
        "SURYOD" to "Suryoday", "ESAFBK" to "ESAF", "NSDLPB" to "NSDL Payments Bank",
        "PHONPE" to "PhonePe", "PHONEPE" to "PhonePe", "GPAY" to "GPay", "GOOGLEPAY" to "GPay", "PAYTM" to "Paytm", "IPAYTM" to "Paytm",
        "AMAZON" to "Amazon Pay", "AMZPAY" to "Amazon Pay", "AMAZPY" to "Amazon Pay", "MOBIKW" to "MobiKwik", "FREECH" to "Freecharge", "BHIMUP" to "BHIM",
        "CREDIN" to "CRED", "CREDCL" to "CRED", "NAVIIN" to "Navi", "SUPRMY" to "super.money", "POPCLB" to "POP",
    )

    val upiApps = mapOf("PHONPE" to "PhonePe", "PHONEPE" to "PhonePe", "GPAY" to "GPay", "GOOGLEPAY" to "GPay", "PAYTM" to "Paytm", "IPAYTM" to "Paytm",
        "AMAZON" to "Amazon Pay", "AMZPAY" to "Amazon Pay", "AMAZPY" to "Amazon Pay", "BHIMUP" to "BHIM", "CREDIN" to "CRED", "NAVIIN" to "Navi", "SUPRMY" to "super.money", "MOBIKW" to "MobiKwik")

    /** "VM-HDFCBK-T" -> ("HDFCBK", 'T'); "AX-SBIUPI" -> ("SBIUPI", null); "+919876543210" -> ("", null) */
    fun split(sender: String): Pair<String, Char?> {
        val s = sender.uppercase(Locale.ROOT).trim()
        if (s.isEmpty() || s.replace("+", "").all { it.isDigit() }) return "" to null
        val parts = s.split('-').filter { it.isNotBlank() }
        return when {
            parts.size >= 3 && parts.last().length == 1 -> parts[parts.size - 2] to parts.last()[0]
            parts.size >= 2 && parts.last().length == 1 -> parts[0] to parts.last()[0]
            parts.size >= 2 -> parts[1] to null
            else -> parts[0] to null
        }
    }

    fun bankFor(key: String): String {
        if (key.isEmpty()) return ""
        banks[key]?.let { return it }
        banks.entries.firstOrNull { key.startsWith(it.key) || it.key.startsWith(key) && key.length >= 4 }?.let { return it.value }
        return if (key.contains("BNK") || key.contains("BANK") || key.endsWith("BK")) key else ""
    }

    fun upiFromVpa(vpa: String): String {
        val h = vpa.substringAfter('@', "").lowercase(Locale.ROOT)
        return when {
            h.startsWith("ok") -> "GPay"
            h in setOf("ybl", "ibl", "axl") -> "PhonePe"
            h == "paytm" || h.startsWith("pt") -> "Paytm"
            h in setOf("apl", "yapl", "rapl", "amazonpay") -> "Amazon Pay"
            h == "upi" -> "BHIM"
            h == "axisb" -> "CRED"
            h.startsWith("navi") -> "Navi"
            h == "ikwik" -> "MobiKwik"
            else -> ""
        }
    }
}

object SmsParser {
    private const val CUR = """(?:rs\.?|inr|₹|rupees)\s*"""
    private const val NUM = """([0-9][0-9,]*(?:\.[0-9]{1,2})?)"""
    private val amountRx = Regex("""(?i)$CUR$NUM""")
    private val amountAfterRx = Regex("""(?i)\b$NUM\s*(?:rs\.?|inr|rupees)\b""")
    private val bareAmountRx = Regex("""(?i)(?:debited|credited|spent|sent|paid|received|withdrawn|deposited)\s+(?:by|of|for|with)?\s*$NUM""")
    private val balRx = Regex("""(?i)(?:avl\.?\s*bal(?:ance)?|available\s+bal(?:ance)?|a/?c\s+bal(?:ance)?|clear\s+bal(?:ance)?|\bbal(?:ance)?\b)\s*(?:is|:|-|of)?\s*(?:rs\.?|inr|₹)?\s*:?\s*(-?[0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val limitRx = Regex("""(?i)(?:avl\.?\s*(?:cr\.?\s*)?lmt|avl\.?\s*limit|available\s+(?:credit\s+)?limit|avail(?:able)?\s+lmt)\s*(?:is|:|-|of)?\s*(?:rs\.?|inr|₹)?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val totalDueRx = Regex("""(?i)(?:total\s+(?:amt|amount)\s+due|total\s+due|tot\.?\s*amt\s*due|statement\s+(?:amount|balance)|amount\s+due|amt\s+due)\s*(?:is|:|-|of)?\s*(?:rs\.?|inr|₹)?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val minDueRx = Regex("""(?i)(?:min(?:imum)?\.?\s*(?:amt|amount)?\.?\s*due|mad)\s*(?:is|:|-|of)?\s*(?:rs\.?|inr|₹)?\s*:?\s*([0-9][0-9,]*(?:\.[0-9]{1,2})?)""")
    private val last4Rx = listOf(
        Regex("""(?i)\b(?:a/?c|acct|account|card|ac)\s*(?:no\.?|number|num)?\s*(?:ending|ending\s+with|ends\s+with)?\s*[:\-]?\s*[x*.#]+\s*(\d{3,6})\b"""),
        Regex("""(?i)\b(?:a/?c|acct|account|card|ac)\s*(?:no\.?|number)?\s*(?:ending|ending\s+with|ends\s+with)\s*[:\-]?\s*(\d{3,6})\b"""),
        Regex("""(?i)\b[x*]{2,}\s*(\d{3,6})\b"""),
        Regex("""(?i)(?:card|a/?c)\s+(\d{4})\b"""),
        Regex("""(?i)\.{2,}(\d{4})\b"""),
        Regex("""(?i)\bending\s+(?:with\s+)?[x*]*(\d{4})\b"""),
    )
    private val vpaRx = Regex("""(?i)\b([a-z0-9][a-z0-9.\-_]{1,}@[a-z][a-z0-9]{1,})\b""")
    private val refRx = Regex("""(?i)(?:upi\s*ref(?:\s*no)?|ref\s*no|refno|ref\.?|rrn|utr|txn\s*(?:id|no)|transaction\s*id|reference(?:\s*no)?)\s*[:.#\-]?\s*([a-z0-9]{6,})""")
    private val merchantRxs = listOf(
        Regex("""(?i)\bat\s+([a-z0-9&'._\-*/ ]{2,40}?)(?=\s+on\s|\s+ref|\s+avl|\s+via|\s+txn|\s+using|\s+for\s|[.,;]|\s*\(|$)"""),
        Regex("""(?i)\b(?:trf|transfer(?:red)?|paid|sent|payment)\s+to\s+([a-z0-9&'._\-* ]{2,40}?)(?=\s+on\s|\s+ref|\s+upi|\s+avl|\s+via|\s+using|[.,;]|\s*\(|$)"""),
        Regex("""(?i)\bto\s+(?:vpa\s+)?([a-z0-9&'._\-* ]{2,40}?)(?=\s+on\s|\s+ref|\s+upi|\s+avl|\s+via|\s+using|[.,;]|\s*\(|$)"""),
        Regex("""(?i)\binfo[:\-\s]+([a-z0-9&'._\-*/ ]{2,40}?)(?=\s+avl|\s+ref|[.,;]|$)"""),
        Regex("""(?i)\b(?:from|by)\s+(?:vpa\s+)?([a-z0-9&'._\-* ]{2,40}?)(?=\s+on\s|\s+ref|\s+upi|\s+avl|\s+via|[.,;]|\s*\(|$)"""),
        Regex("""(?i)\btowards\s+([a-z0-9&'._\-* ]{2,40}?)(?=\s+on\s|\s+ref|[.,;]|$)"""),
    )

    private val otpWords = Regex("""(?i)\b(otp|one[\s-]?time\s+password|verification\s+code|security\s+code|login\s+code|passcode)\b""")
    private val failedWords = Regex("""(?i)\b(failed|declined|unsuccessful|could\s+not\s+be\s+(?:processed|completed)|not\s+processed|rejected|insufficient\s+(?:funds|balance)|transaction\s+cancelled)\b""")
    private val refundWords = Regex("""(?i)\b(refund(?:ed)?|reversed|reversal|reverted|chargeback)\b""")
    private val debitWords = Regex("""(?i)\b(debited|debit(?:ed)?\s+(?:by|with|for)|spent|sent|paid|withdrawn|withdrawal|purchase|deducted|charged|used\s+(?:at|for)|txn\s+of|trxn\.?\s+of|transaction\s+of|done\s+(?:on|at|using)|dr\.?\b|is\s+debited|has\s+been\s+debited|auto[\s-]?debit(?:ed)?)""")
    private val creditWords = Regex("""(?i)\b(credited|received|deposited|added\s+to|cr\.?\b|has\s+been\s+credited|is\s+credited|cashback)""")
    private val promoWords = Regex("""(?i)(\boffer\b|get\s+up\s*to|upto\s+\d+%|\d+%\s*off|apply\s+now|pre[-\s]?approved|instant\s+loan|personal\s+loan|click\s+here|limited\s+period|\bwin\b|congratulations|\bt&c\b|bit\.ly|hurry|exclusive|reward\s+points\s+expir|eligible\s+for|recharge\s+now|buy\s+now|shop\s+now|data\s+left|plans?\s+starting|valid\s+till)""")
    private val atmWords = Regex("""(?i)\b(atm|cash\s+withdrawal|cash\s+wdl|wdl|withdrawn\s+from\s+atm|nfs)\b""")
    private val billWords = Regex("""(?i)(statement\s+(?:for|of|is|has|generated|dated)|e-?statement|bill\s+(?:is\s+)?generated|total\s+(?:amt|amount)\s+due|total\s+due|min(?:imum)?\.?\s*(?:amt|amount)?\.?\s*due)""")
    private val dueWords = Regex("""(?i)(payment\s+(?:is\s+)?due|is\s+due\s+(?:on|by)|due\s+date|due\s+on|due\s+by|pay\s+by|overdue|last\s+date)""")
    private val cardPayWords = Regex("""(?i)(payment\s+of\s+.{0,25}(?:has\s+been\s+|is\s+)?received|received\s+(?:your\s+)?payment|thank\s+you\s+for\s+(?:your\s+)?payment|payment\s+.{0,30}credited\s+to\s+your\s+(?:credit\s+)?card|we\s+have\s+received\s+.{0,30}payment)""")
    private val mandateWords = Regex("""(?i)(mandate|autopay|auto[\s-]?pay|standing\s+instruction|e-?nach|\bsi\b).{0,60}(created|registered|set\s*up|activated|successfully\s+setup|approved)""")
    private val cardWords = Regex("""(?i)(credit\s+card|\bcard\b|\bcc\b)""")
    private val doneWords = Regex("""(?i)\b(has\s+been|was|is|were)\s+(successfully\s+)?(debited|deducted|paid|credited|received|processed|completed)\b|\b(debited|credited)\s+(from|to|with|by|for)\b""")
    private val rechargeRx = Regex("""(?i)\byour\b.{0,60}\b(plan|pack|validity|recharge|subscription|services?|connection)\b.{0,60}(expir|will\s+end|ends?\s+on|ending\s+on|valid\s+(?:till|upto|until)|will\s+be\s+(?:discontinued|deactivated|barred))""")
    private val telecomRx = Regex("""(?i)\b(jio|airtel|vodafone|\bvi\b|idea|bsnl|mtnl|tata\s*play|dish\s*tv|d2h|sun\s*direct|airtel\s*dth|act\s*fibernet|hathway|excitel|prepaid|dth)\b""")
    private val utilityRx = Regex("""(?i)\b(electricity|bijli|power\s+bill|discom|energy\s+bill|consumer\s*(?:no|number|id|a/c)|k\.?\s*no|ca\s+no|broadband|fibre|fiber|gas\s+bill|png|piped\s+gas|lpg|water\s+bill|postpaid\s+bill|landline|nbpdcl|sbpdcl|bses|tata\s+power|msedcl|uppcl|torrent\s+power)\b""")
    private val billDueRx = Regex("""(?i)(\bdue\b|pay\s+by|last\s+date|bill\s+(?:amount|amt|of|for|is)|amount\s+payable|payable\s+by|before)""")
    private val emiRx = Regex("""(?i)\b(emi|instal+ment|auto[\s-]?debit|autopay|auto[\s-]?pay|e-?nach|nach|mandate|standing\s+instruction|\bsi\b|sip)\b""")
    private val upcomingRx = Regex("""(?i)(is\s+due|due\s+on|due\s+date|will\s+be\s+(?:debited|deducted|presented|processed|auto[\s-]?debited)|scheduled\s+(?:for|on)|to\s+be\s+debited|upcoming|is\s+scheduled|ensure\s+(?:sufficient|adequate)\s+(?:balance|funds))""")
    private val tagRx = listOf(
        "SALARY" to Regex("""(?i)\b(salary|sal\b|payroll|sal\s+for|wages)"""),
        "CASHBACK" to Regex("""(?i)\b(cash\s*back|cashback|reward\s+credited|scratch\s+card)"""),
        "INTEREST" to Regex("""(?i)\b(interest\s+(?:credited|paid|cr)|int\.?\s*(?:pd|paid|cr|credit)|savings\s+int|sb\s+int|interest\s+of|int\s+credited)"""),
        "CHARGE" to Regex("""(?i)\b(charges?\b|fees?\b|gst\b|penalty|annual\s+fee|amc\b|sms\s+(?:alert\s+)?charges|min(?:imum)?\.?\s+bal|non[-\s]?maintenance|service\s+charge|late\s+(?:payment\s+)?fee|cheque\s+book|card\s+(?:annual|issuance|replacement)|finance\s+charge|processing\s+fee|ecs\s+return|bounce)"""),
        "INVEST" to Regex("""(?i)\b(sip\b|mutual\s+fund|\bmf\b|groww|zerodha|upstox|kuvera|paytm\s+money|angel\s+one|iccl|indian\s+clearing|bse\s*star|nse\s*clearing|cams\b|kfin|nps\b|ppf\b|sukanya|digital\s+gold|safegold|augmont|fixed\s+deposit|\bfd\b|recurring\s+deposit|\brd\b|smallcase|demat)"""),
        "INSURANCE" to Regex("""(?i)\b(insurance|premium|lic\b|policy\s*(?:no|number)|hdfc\s*life|sbi\s*life|icici\s*pru|star\s*health|max\s*life|tata\s*aia|bajaj\s*allianz|acko|go\s*digit|care\s+health|niva\s+bupa|pmjjby|pmsby)"""),
        "FASTAG" to Regex("""(?i)(fastag|toll\s+plaza|\bnhai\b|\btoll\b|netc)"""),
        "WALLET" to Regex("""(?i)\b(wallet|paytm\s+balance|amazon\s+pay\s+balance|mobikwik\s+balance|gift\s+card\s+balance)"""),
        "INTL" to Regex("""(?i)(\binternational\b|\busd\b|\$\s*\d|\beur\b|\bgbp\b|\baed\b|\bsgd\b|forex|cross[-\s]border|outside\s+india|\bfx\b)"""),
        "SELF" to Regex("""(?i)(self\s+transfer|to\s+self\b|own\s+account|your\s+own\s+a/?c|between\s+your\s+accounts)"""),
        "REVERSAL" to Regex("""(?i)\b(reversed|reversal|reverted|credited\s+back|refunded\s+to\s+your\s+a/?c\s+for\s+failed)"""),
        "EMI" to Regex("""(?i)\b(emi|loan\s+(?:a/?c|account|repayment)|instal+ment)\b"""),
    )

    private val moneyWords = Regex("""(?i)(rs\.?\s*\d|inr\s*\d|₹\s*\d|\bdebit|\bcredit|\ba/?c\b|\bupi\b|\bbal(?:ance)?\b|\bpaid\b|\btxn\b)""")

    fun parse(sender: String, body: String): Parsed {
        val (key, suffix) = Senders.split(sender)
        val bank = Senders.bankFor(key)
        val text = body.replace('\n', ' ').replace(Regex("\\s+"), " ").trim()
        val lower = text.lowercase(Locale.ROOT)

        val hasDebit = debitWords.containsMatchIn(text)
        val hasCredit = creditWords.containsMatchIn(text)
        val amount = firstAmount(text)
        val looksMoney = moneyWords.containsMatchIn(text) && (amount > 0 || hasDebit || hasCredit)

        // ---- classification
        val kind: SmsKind = when {
            otpWords.containsMatchIn(text) && !Regex("(?i)\\b(debited|credited)\\b").containsMatchIn(text) -> SmsKind.OTP
            failedWords.containsMatchIn(text) && (!refundWords.containsMatchIn(text) || Regex("(?i)(will\\s+be\\s+(reversed|refunded|credited)|if\\s+debited)").containsMatchIn(text)) -> SmsKind.FAILED
            mandateWords.containsMatchIn(text) && !Regex("(?i)\\b(debited|deducted|paid)\\b").containsMatchIn(text) -> SmsKind.AUTOPAY_SETUP
            reminderOf(text, key) != null -> SmsKind.REMINDER
            cardPayWords.containsMatchIn(text) -> SmsKind.CARD_PAYMENT
            refundWords.containsMatchIn(text) && amount > 0 -> SmsKind.REFUND
            billWords.containsMatchIn(text) && cardWords.containsMatchIn(text) && !Regex("(?i)\\b(debited|spent)\\b").containsMatchIn(text) -> SmsKind.BILL
            dueWords.containsMatchIn(text) && !hasDebit && !Regex("(?i)\\b(credited|received)\\b").containsMatchIn(text) && amount > 0 -> SmsKind.DUE
            atmWords.containsMatchIn(text) && hasDebit && amount > 0 -> SmsKind.ATM
            (suffix == 'P' || promoWords.containsMatchIn(text)) && !Regex("(?i)\\b(debited|credited|spent)\\b").containsMatchIn(text) -> SmsKind.PROMO
            hasDebit && hasCredit && amount > 0 -> if (firstIndex(debitWords, text) <= firstIndex(creditWords, text)) SmsKind.DEBIT else SmsKind.CREDIT
            hasDebit && amount > 0 -> SmsKind.DEBIT
            hasCredit && amount > 0 -> SmsKind.CREDIT
            Regex("(?i)(avl\\.?\\s*bal|available\\s+bal|balance\\s+(?:in|of|is))").containsMatchIn(text) && !hasDebit && !hasCredit -> SmsKind.BALANCE
            looksMoney && (bank.isNotEmpty() || hasDebit || hasCredit || Regex("(?i)\\b(a/?c|acct|account|upi|card)\\b").containsMatchIn(text)) -> SmsKind.UNKNOWN_MONEY
            else -> SmsKind.NOT_FINANCIAL
        }
        if (kind == SmsKind.NOT_FINANCIAL || kind == SmsKind.OTP || kind == SmsKind.PROMO) {
            return Parsed(kind = kind, bank = bank, senderKey = key)
        }
        if (kind == SmsKind.REMINDER) {
            val (rk, rd, rt) = reminderOf(text, key)!!
            return Parsed(kind = kind, amountPaise = amount, bank = bank, senderKey = key, remindKind = rk, remindDate = rd, remindTitle = rt,
                last4 = findLast4(text), confidence = 80)
        }

        // ---- fields
        val vpa = vpaRx.find(text)?.groupValues?.get(1)?.takeIf { !it.contains("@gmail") && !it.contains("@yahoo") && !it.endsWith(".com") } ?: ""
        val last4 = findLast4(text)
        val balance = balRx.findAll(text).map { toPaise(it.groupValues[1]) }.lastOrNull { it != null } ?: -1L
        val limit = limitRx.find(text)?.let { toPaise(it.groupValues[1]) } ?: -1L
        val ref = refRx.find(text)?.groupValues?.get(1)?.takeIf { it.any(Char::isDigit) } ?: ""
        val merchantRaw = findMerchant(text, kind)
        val vpaName = vpa.substringBefore('@').takeIf { vpa.isNotEmpty() && !it.all(Char::isDigit) }?.replace('.', ' ') ?: ""
        val merchant = when {
            kind == SmsKind.BILL || kind == SmsKind.DUE || kind == SmsKind.FAILED || kind == SmsKind.CARD_PAYMENT -> ""
            merchantRaw.isBlank() -> cleanMerchant(vpaName)
            vpa.isNotEmpty() && vpa.lowercase(Locale.ROOT).startsWith(merchantRaw.lowercase(Locale.ROOT)) -> cleanMerchant(vpaName)
            else -> cleanMerchant(merchantRaw)
        }
        val upiApp = when {
            vpa.isNotEmpty() -> Senders.upiFromVpa(vpa)
            else -> Senders.upiApps[key] ?: ""
        }.ifEmpty { Senders.upiApps[key] ?: "" }
        val txnDate = findDate(text)

        var billP = -1L; var minP = -1L; var due: LocalDate? = null
        if (kind == SmsKind.BILL || kind == SmsKind.DUE) {
            billP = totalDueRx.findAll(text).firstOrNull { m -> !text.substring(maxOf(0, m.range.first - 9), m.range.first).contains("min", true) }
                ?.let { toPaise(it.groupValues[1]) } ?: amount.takeIf { it > 0 } ?: -1L
            minP = minDueRx.find(text)?.let { toPaise(it.groupValues[1]) } ?: -1L
            due = findDueDate(text) ?: txnDate
        }

        val debit = when (kind) {
            SmsKind.DEBIT, SmsKind.ATM -> true
            SmsKind.CREDIT, SmsKind.REFUND -> false
            SmsKind.CARD_PAYMENT -> false // money came into the card
            else -> hasDebit || !hasCredit
        }

        var conf = 0
        if (amount > 0) conf += 40
        if (kind != SmsKind.UNKNOWN_MONEY) conf += 25
        if (last4.isNotEmpty()) conf += 15
        if (merchant.isNotEmpty() || vpa.isNotEmpty() || kind == SmsKind.ATM || kind == SmsKind.CARD_PAYMENT) conf += 10
        if (bank.isNotEmpty()) conf += 10

        val tags = tagRx.filter { (_, rx) -> rx.containsMatchIn(text) }.map { it.first }.toMutableSet()
        // tags only make sense in the right direction (a salary is never a debit, a charge never a credit)
        if (debit) tags.removeAll(setOf("SALARY", "CASHBACK", "INTEREST")) else tags.removeAll(setOf("CHARGE", "INVEST", "INSURANCE"))
        if (tags.contains("CHARGE") && (merchant.isNotBlank() && !Regex("(?i)charge|fee|gst|bank").containsMatchIn(merchant))) tags.remove("CHARGE")
        if (kind == SmsKind.REFUND && !refundWords.containsMatchIn(text)) tags.remove("REVERSAL")
        val hint = when {
            "SALARY" in tags -> "Salary"; "CASHBACK" in tags -> "Cashback"; "INTEREST" in tags -> "Interest"
            "CHARGE" in tags -> "Bank charges"; "INSURANCE" in tags -> "Insurance"; "FASTAG" in tags && debit -> "Travel"
            else -> categoryFor(merchant + " " + vpa + " " + lower)
        }
        return Parsed(
            tags = tags, kind = kind, amountPaise = if (kind == SmsKind.BILL || kind == SmsKind.DUE) maxOf(billP, 0) else amount,
            debit = debit, last4 = last4, merchant = merchant, vpa = vpa, upiApp = upiApp, ref = ref,
            balancePaise = balance, availLimitPaise = limit, billPaise = billP, minDuePaise = minP, dueDate = due,
            txnDate = txnDate, bank = bank, senderKey = key, categoryHint = hint,
            confidence = conf.coerceAtMost(100),
        )
    }

    private fun firstIndex(rx: Regex, s: String) = rx.find(s)?.range?.first ?: Int.MAX_VALUE

    /** An SMS about something coming up (no money moved yet): plan expiry, a utility bill, an EMI / AutoPay debit. */
    fun reminderOf(text: String, senderKey: String = ""): Triple<String, LocalDate, String>? {
        if (doneWords.containsMatchIn(text) || failedWords.containsMatchIn(text)) return null
        val date = (findDueDate(text) ?: return null).takeIf { !it.isBefore(LocalDate.now().minusDays(1)) } ?: return null
        val tl = telecomRx.find(text)?.value?.trim()?.lowercase(Locale.ROOT)
        val telecom = tl != null || Regex("JIO|AIRTEL|VICARE|VFCARE|BSNL|TATASKY|TPLAY|DISHTV").containsMatchIn(senderKey)
        if (rechargeRx.containsMatchIn(text) && telecom) {
            val op = when {
                tl == null -> Senders.bankFor(senderKey).ifBlank { senderKey.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) } }
                tl.contains("jio") -> "Jio"; tl.contains("airtel") -> "Airtel"; tl.contains("vodafone") || tl == "vi" || tl == "idea" -> "Vi"
                tl.contains("bsnl") -> "BSNL"; tl.contains("tata") -> "Tata Play"; tl.contains("dish") || tl.contains("d2h") -> "Dish TV"
                else -> tl.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
            return Triple("RECHARGE", date, "$op plan")
        }
        val u = utilityRx.find(text)
        if (u != null && billDueRx.containsMatchIn(text) && firstAmount(text) > 0 && !cardWords.containsMatchIn(text)) {
            val w = u.value.lowercase(Locale.ROOT)
            val title = when {
                Regex("electric|bijli|power|discom|energy|consumer|k\\.?\\s*no|ca\\s+no|nbpdcl|sbpdcl|bses|msedcl|uppcl|torrent").containsMatchIn(w) -> "Electricity bill"
                Regex("broadband|fib").containsMatchIn(w) -> "Broadband bill"
                Regex("gas|png|lpg").containsMatchIn(w) -> "Gas bill"
                w.contains("water") -> "Water bill"
                else -> "Phone bill"
            }
            return Triple("UTILITY", date, title)
        }
        if (emiRx.containsMatchIn(text) && upcomingRx.containsMatchIn(text)) {
            val emi = Regex("(?i)\\b(emi|instal+ment|loan)\\b").containsMatchIn(text)
            val who = Senders.bankFor(senderKey)
            return Triple(if (emi) "EMI" else "AUTOPAY", date, (if (emi) "EMI" else "AutoPay") + (if (who.isNotBlank()) " · $who" else ""))
        }
        return null
    }

    fun toPaise(s: String): Long? {
        val t = s.replace(",", "").trim().trimEnd('.')
        val d = t.toBigDecimalOrNull() ?: return null
        return d.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
    }

    /** First money amount that is not a balance / limit / due amount. */
    fun firstAmount(text: String): Long {
        val candidates = (amountRx.findAll(text) + amountAfterRx.findAll(text) + bareAmountRx.findAll(text))
            .sortedBy { it.range.first }
        for (m in candidates) {
            val before = text.substring(maxOf(0, m.range.first - 22), m.range.first).lowercase(Locale.ROOT)
            if (Regex("(bal(ance)?|lmt|limit|min(imum)?\\.?\\s*(amt|amount)?\\.?\\s*due|outstanding)\\W*(is|:|-|of)?\\W*$").containsMatchIn(before.trimEnd())) continue
            val v = toPaise(m.groupValues[1]) ?: continue
            if (v <= 0) continue
            return v
        }
        return 0
    }

    private fun findLast4(text: String): String {
        for (rx in last4Rx) {
            val m = rx.find(text) ?: continue
            val d = m.groupValues[1]
            if (d.length in 3..6) return d.takeLast(4)
        }
        return ""
    }

    private fun findMerchant(text: String, kind: SmsKind): String {
        val order = if (kind == SmsKind.CREDIT || kind == SmsKind.REFUND) listOf(4, 3, 5, 0, 1, 2) else listOf(0, 1, 2, 3, 5, 4)
        for (i in order) for (m in merchantRxs[i].findAll(text)) {
            val v = m.groupValues[1].trim()
            if (v.isBlank()) continue
            val l = v.lowercase(Locale.ROOT)
            if (Regex("^(your|you|the|a/?c|ac|acct|account|card|rs\\.?|inr|xx|x+\\d|\\*|beneficiary|bank|upi|ref|imps|neft|rtgs|self|mobile|net\\s?banking|refund|payment|bill|credit|debit|wallet)\\b").containsMatchIn(l)) continue
            if (l.all { it.isDigit() || it == ' ' || it == '.' || it == ',' }) continue
            return v
        }
        return ""
    }

    private val aliases = linkedMapOf(
        "bundl" to "Swiggy", "swiggy" to "Swiggy", "zomato" to "Zomato", "amzn" to "Amazon", "amazon" to "Amazon", "flipkart" to "Flipkart",
        "irctc" to "IRCTC", "uber" to "Uber", "olacabs" to "Ola", "ani technologies" to "Ola", "rapido" to "Rapido", "blinkit" to "Blinkit", "grofers" to "Blinkit",
        "zepto" to "Zepto", "kiranakart" to "Zepto", "bigbasket" to "BigBasket", "supermarket grocery" to "BigBasket", "myntra" to "Myntra", "netflix" to "Netflix",
        "spotify" to "Spotify", "jio" to "Jio", "airtel" to "Airtel", "bsnl" to "BSNL", "dominos" to "Domino's", "mcdonald" to "McDonald's", "dmart" to "DMart",
        "avenue supermarts" to "DMart", "reliance retail" to "Reliance", "meesho" to "Meesho", "ajio" to "AJIO", "nykaa" to "Nykaa", "bookmyshow" to "BookMyShow",
        "makemytrip" to "MakeMyTrip", "redbus" to "redBus", "hotstar" to "Hotstar", "apollo" to "Apollo Pharmacy", "pharmeasy" to "PharmEasy", "1mg" to "Tata 1mg",
    )

    fun cleanMerchant(raw: String): String {
        var s = raw.replace(Regex("[*_]+"), " ").replace(Regex("\\s+"), " ").trim().trim('.', ',', '-', '/')
        s = s.replace(Regex("(?i)\\b(pvt|private|ltd|limited|india|technologies|tech|payments?|services|retail|online|com|www)\\b\\.?"), " ")
            .replace(Regex("\\s+"), " ").trim()
        if (s.isEmpty()) return ""
        val l = s.lowercase(Locale.ROOT)
        aliases.entries.filter { l.contains(it.key) }.minByOrNull { l.indexOf(it.key) }?.let { return it.value }
        if (s.length > 28) s = s.take(28).trim()
        return if (s == s.uppercase(Locale.ROOT) || s == s.lowercase(Locale.ROOT)) s.lowercase(Locale.ROOT).split(' ').joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } } else s
    }

    private val catWords = listOf(
        "Food" to listOf("swiggy", "zomato", "restaurant", "cafe", "domino", "mcdonald", "kfc", "pizza", "burger", "biryani", "dhaba", "hotel", "food", "eat", "bakery", "sweets", "chai"),
        "Groceries" to listOf("blinkit", "zepto", "bigbasket", "dmart", "kirana", "grocery", "mart", "jiomart", "supermarket", "store", "general"),
        "Travel" to listOf("uber", "ola", "rapido", "irctc", "redbus", "makemytrip", "indigo", "airline", "metro", "railway", "bus", "fastag", "toll", "parking"),
        "Fuel" to listOf("petrol", "fuel", "hpcl", "bpcl", "iocl", "indian oil", "shell", "pump", "filling", "nayara"),
        "Bills & Recharge" to listOf("jio", "airtel", "bsnl", "vodafone", "recharge", "electricity", "bijli", "broadband", "dth", "tata play", "gas", "water", "postpaid", "prepaid"),
        "Shopping" to listOf("amazon", "flipkart", "myntra", "ajio", "meesho", "nykaa", "mall", "fashion", "reliance", "trends", "decathlon"),
        "Entertainment" to listOf("netflix", "spotify", "hotstar", "prime video", "bookmyshow", "pvr", "inox", "youtube", "gaana"),
        "Health" to listOf("pharmacy", "apollo", "medplus", "hospital", "clinic", "1mg", "pharmeasy", "medical", "chemist", "diagnostic", "lab"),
        "Education" to listOf("school", "college", "udemy", "byju", "coursera", "fees", "university", "book", "stationery", "coaching", "academy"),
        "EMI" to listOf("emi", "loan", "bajaj fin", "home credit"),
        "Rent" to listOf("rent", "nobroker", "hostel"),
    )

    fun categoryFor(text: String): String {
        val l = text.lowercase(Locale.ROOT)
        for ((cat, words) in catWords) if (words.any { Regex("\\b" + Regex.escape(it)).containsMatchIn(l) }) return cat
        return ""
    }

    private val months = mapOf("jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6, "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12)
    private val dateRxs = listOf(
        Regex("""\b(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})\b"""),
        Regex("""(?i)\b(\d{1,2})[-\s]?(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[-\s,]?(\d{2,4})?\b"""),
        Regex("""\b(20\d{2})-(\d{2})-(\d{2})\b"""),
    )

    fun findDate(text: String): LocalDate? = dateCandidates(text).firstOrNull()

    private fun findDueDate(text: String): LocalDate? {
        val idx = Regex("(?i)(due\\s+(?:date|on|by)|pay\\s+by|on\\s+or\\s+before|before|by)").findAll(text).map { it.range.last }.toList()
        for (i in idx) dateCandidates(text.substring(i)).firstOrNull()?.let { return it }
        return dateCandidates(text).lastOrNull()
    }

    private fun dateCandidates(text: String): List<LocalDate> {
        val out = ArrayList<Pair<Int, LocalDate>>()
        val now = LocalDate.now()
        dateRxs[0].findAll(text).forEach { m ->
            val d = m.groupValues[1].toInt(); val mo = m.groupValues[2].toInt(); var y = m.groupValues[3].toInt()
            if (y < 100) y += 2000
            runCatching { LocalDate.of(y, mo, d) }.getOrNull()?.let { out += m.range.first to it }
        }
        dateRxs[1].findAll(text).forEach { m ->
            val d = m.groupValues[1].toInt(); val mo = months[m.groupValues[2].lowercase(Locale.ROOT)] ?: return@forEach
            var y = m.groupValues[3].toIntOrNull() ?: now.year
            if (y < 100) y += 2000
            runCatching { LocalDate.of(y, Month.of(mo), d) }.getOrNull()?.let { out += m.range.first to it }
        }
        dateRxs[2].findAll(text).forEach { m ->
            runCatching { LocalDate.of(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt()) }.getOrNull()?.let { out += m.range.first to it }
        }
        return out.sortedBy { it.first }.map { it.second }.filter { it.year in 2015..2100 }
    }

    /** Token "shape" used to recognise formats he taught (digits and amounts become #). */
    fun shape(body: String): Set<String> = body.lowercase(Locale.ROOT)
        .replace(Regex("[0-9][0-9,.]*"), "#").split(Regex("[^a-z#/]+"))
        .filter { it.length >= 2 || it == "#" }.toSet()

    fun similarity(a: Set<String>, b: Set<String>): Double =
        if (a.isEmpty() || b.isEmpty()) 0.0 else a.intersect(b).size.toDouble() / a.union(b).size
}
