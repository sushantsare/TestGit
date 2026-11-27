package com.sushant.bahikhata.data

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs

/**
 * Pure maths behind the insight pages (subscriptions, payday, people, price history, yearly summary)
 * and the checks the SMS engine runs (unusual debits, items typed in a notification). No Android types,
 * so all of it is unit tested.
 */
object Insights {

    // ---------------- "What did you buy?"

    /** One thing bought: its name, what was paid for all of it, and how much of it. */
    data class Item(val name: String, val pricePaise: Long = -1, val qty: Double = 1.0, val unit: String = "") {
        /** Price for one piece, or per kg / per litre for weights and volumes (grams and ml are scaled up). */
        val unitPricePaise: Long get() = unitPrice(pricePaise, qty, unit)
        val unitLabel: String get() = unitLabel(unit)
        val hasQty: Boolean get() = qty != 1.0 || unit.isNotBlank()
    }

    val UNITS = listOf("", "kg", "g", "L", "ml", "pack", "dozen")
    private val unitWords = mapOf(
        "pc" to "", "pcs" to "", "piece" to "", "pieces" to "", "nos" to "", "no" to "",
        "kg" to "kg", "kgs" to "kg", "kilo" to "kg", "g" to "g", "gm" to "g", "gms" to "g", "gram" to "g", "grams" to "g",
        "l" to "L", "lt" to "L", "ltr" to "L", "litre" to "L", "liter" to "L", "ml" to "ml",
        "pack" to "pack", "packs" to "pack", "pkt" to "pack", "packet" to "pack", "packets" to "pack", "dozen" to "dozen", "dz" to "dozen",
    )

    fun unitPrice(pricePaise: Long, qty: Double, unit: String): Long {
        if (pricePaise <= 0 || qty <= 0) return pricePaise
        val q = when (unit) { "g", "ml" -> qty / 1000.0; else -> qty }
        return Math.round(pricePaise / q)
    }

    /** "each", "/kg", "/L", "/pack", "/dozen" — grams and ml are shown per kg and per litre. */
    fun unitLabel(unit: String): String = when (unit) { "", "pcs" -> "each"; "g", "kg" -> "/kg"; "ml", "L" -> "/L"; else -> "/$unit" }

    fun qtyText(qty: Double, unit: String): String {
        val n = if (qty == Math.floor(qty)) qty.toLong().toString() else qty.toString().trimEnd('0').trimEnd('.')
        return if (unit.isBlank()) n else if (unit == "pack" || unit == "dozen") "$n $unit" else "$n$unit"
    }

    /**
     * "milk 2L 60, bread 40, 3 eggs 21, rice 5kg 320" → items with total price and quantity.
     * Prices and quantities are optional; "₹60 milk" and "eggs x6 42" work too. No quantity means 1.
     */
    fun parseItems(text: String): List<Item> =
        text.split(',', ';', '\n', '+').flatMap { it.split(Regex("(?i)\\s+and\\s+")) }.mapNotNull { raw ->
            var t = raw.trim().trim('.', ':')
            if (t.isEmpty()) return@mapNotNull null
            var price = -1L
            val tail = Regex("""(?i)(?<=^|\s)(?:₹|rs\.?|inr)?\s*([0-9]+(?:\.[0-9]{1,2})?)\s*(?:₹|rs\.?|rupees?|/-?)?$""").find(t)
            val head = Regex("""(?i)^(?:₹|rs\.?|inr)\s*([0-9]+(?:\.[0-9]{1,2})?)\s+""").find(t)
            when {
                tail != null && tail.range.first > 0 -> { price = Money.parse(tail.groupValues[1]) ?: -1L; t = t.substring(0, tail.range.first).trim().trimEnd('-', ':', '=', '@') }
                head != null -> { price = Money.parse(head.groupValues[1]) ?: -1L; t = t.substring(head.range.last + 1).trim() }
            }
            var qty = 1.0; var unit = ""
            val unitRx = unitWords.keys.sortedByDescending { it.length }.joinToString("|")
            val withUnit = Regex("""(?i)(?:^|\s)([0-9]+(?:\.[0-9]+)?)\s*($unitRx)(?=\s|$)""").find(t)
            val times = Regex("""(?i)(?:^|\s)(?:x\s*([0-9]+(?:\.[0-9]+)?)|([0-9]+(?:\.[0-9]+)?)\s*x)(?=\s|$)""").find(t)
            val lead = Regex("""^([0-9]+(?:\.[0-9]+)?)\s+(?=\D)""").find(t)
            when {
                withUnit != null -> { qty = withUnit.groupValues[1].toDouble(); unit = unitWords[withUnit.groupValues[2].lowercase(Locale.ROOT)].orEmpty(); t = t.removeRange(withUnit.range).trim() }
                times != null -> { qty = (times.groupValues[1].ifEmpty { times.groupValues[2] }).toDouble(); t = t.removeRange(times.range).trim() }
                lead != null -> { qty = lead.groupValues[1].toDouble(); t = t.substring(lead.range.last + 1).trim() }
            }
            if (qty <= 0) qty = 1.0
            val name = t.trim().trim('-', ':').trim().replaceFirstChar { it.titlecase(Locale.ROOT) }
            if (name.isEmpty() || name.all { it.isDigit() || it == '.' }) null else Item(name.take(40), price, qty, unit)
        }

    fun itemsText(items: List<Item>): String =
        items.joinToString(", ") { i -> listOfNotNull(i.name, if (i.hasQty) (if (i.unit.isBlank()) "x" else "") + qtyText(i.qty, i.unit) else null, if (i.pricePaise > 0) Money.input(i.pricePaise) else null).joinToString(" ") }

    fun shopKey(name: String?): String = name.orEmpty().trim().lowercase(Locale.ROOT)

    // ---------------- subscriptions

    data class Sub(val name: String, val amountPaise: Long, val cadence: String, val last: LocalDate, val next: LocalDate, val count: Int, val monthlyPaise: Long)

    private val knownSubs = listOf("netflix", "spotify", "hotstar", "disney", "prime video", "amazon prime", "youtube", "jio", "airtel", "vodafone", "vi", "bsnl",
        "tata play", "dish tv", "apple", "google one", "google play", "zee5", "sonyliv", "jiocinema", "linkedin", "openai", "chatgpt", "microsoft", "adobe",
        "dropbox", "icloud", "gaana", "wynk", "audible", "kindle", "swiggy one", "zomato gold", "cred", "act fibernet", "hathway", "excitel")

    data class Payment(val name: String, val amountPaise: Long, val time: Long)

    /** Spends that repeat on a steady rhythm (monthly / weekly / yearly) with a similar amount, plus well-known
     *  subscription services seen at least twice. A subscription not paid for a long while is treated as stopped. */
    fun subscriptions(payments: List<Payment>, today: LocalDate = Dates.today()): List<Sub> {
        val out = ArrayList<Sub>()
        payments.filter { it.name.isNotBlank() && it.amountPaise > 0 }.groupBy { shopKey(it.name) }.forEach { (key, list0) ->
            val list = list0.sortedBy { it.time }
            if (list.size < 2) return@forEach
            val dates = list.map { Dates.date(it.time) }
            val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.filter { it > 0 }
            if (gaps.isEmpty()) return@forEach
            val gap = gaps.sorted()[gaps.size / 2]
            val cadence = when (gap) { in 6..8 -> "Weekly"; in 26..35 -> "Monthly"; in 84..95 -> "Every 3 months"; in 350..380 -> "Yearly"; else -> null }
            val known = knownSubs.any { k -> Regex("(^|[^a-z0-9])" + Regex.escape(k.trim()) + "([^a-z0-9]|$)").containsMatchIn(key) }
            if (cadence == null && !known) return@forEach
            val amounts = list.takeLast(4).map { it.amountPaise }.sorted()
            val typical = amounts[amounts.size / 2]
            val steady = list.takeLast(4).all { abs(it.amountPaise - typical) * 100 <= typical * 20 }
            if (!steady && !known) return@forEach
            val stepDays = when (cadence) { "Weekly" -> 7L; "Every 3 months" -> 91L; "Yearly" -> 365L; else -> 30L }
            val last = dates.last()
            if (ChronoUnit.DAYS.between(last, today) > stepDays * 1.6 + 5) return@forEach   // stopped paying
            val next = when (cadence) { "Weekly" -> last.plusWeeks(1); "Every 3 months" -> last.plusMonths(3); "Yearly" -> last.plusYears(1); else -> last.plusMonths(1) }
            val monthly = when (cadence) { "Weekly" -> typical * 433 / 100; "Every 3 months" -> typical / 3; "Yearly" -> typical / 12; else -> typical }
            out += Sub(list.last().name.trim(), typical, cadence ?: "Monthly", last, next, list.size, monthly)
        }
        return out.sortedByDescending { it.monthlyPaise }
    }

    // ---------------- payday

    data class Payday(val last: LocalDate, val amountPaise: Long, val payer: String, val daysSince: Long, val spentSince: Long, val receivedSince: Long, val nextGuess: LocalDate?)

    /** Last salary credit (category "Salary"), what's been spent since, and when the next one is likely. */
    fun payday(entries: List<Entry>, salaryCategoryIds: Set<Long>, history: List<Payment> = emptyList(), today: LocalDate = Dates.today()): Payday? {
        val salaries = entries.filter { Ledger.isIncome(it) && it.categoryId in salaryCategoryIds }.map { Payment(it.merchant.orEmpty(), it.amountPaise, it.time) } + history
        val last = salaries.maxByOrNull { it.time } ?: return null
        val since = Dates.startOf(Dates.date(last.time))..Dates.endOf(today)
        val dates = salaries.map { Dates.date(it.time) }.distinct().sorted()
        val gaps = dates.zipWithNext { a, b -> ChronoUnit.DAYS.between(a, b) }.filter { it in 20..40 }
        val next = if (gaps.isNotEmpty()) Dates.date(last.time).plusMonths(1) else null
        val fromEntries = history.none { it.time == last.time }   // the salary itself is an entry: not "received since"
        val receivedSince = (Ledger.received(entries, since) - if (fromEntries) last.amountPaise else 0L).coerceAtLeast(0L)
        return Payday(Dates.date(last.time), last.amountPaise, last.name, ChronoUnit.DAYS.between(Dates.date(last.time), today),
            Ledger.spent(entries, since), receivedSince, next)
    }

    // ---------------- people

    data class PersonSum(val name: String, val paidPaise: Long, val gotPaise: Long, val count: Int, val udharPaise: Long?, val personId: Long?)

    /** Who he pays most and who pays him: money to/from named people (UPI names marked "Person" and Udhar people). */
    fun people(entries: List<Entry>, names: List<UpiName>, people: List<Person>, udhar: List<Udhar>, r: LongRange): List<PersonSum> {
        val personNames = (names.filter { it.isPerson && it.display.isNotBlank() }.map { shopKey(it.display) to it.personId } +
            people.map { shopKey(it.name) to it.id }).groupBy({ it.first }, { it.second }).mapValues { v -> v.value.firstOrNull { it != null } }
        val m = LinkedHashMap<String, PersonSum>()
        fun add(key: String, display: String, paid: Long, got: Long, pid: Long?) {
            val o = m[key] ?: PersonSum(display, 0, 0, 0, null, pid)
            m[key] = o.copy(paidPaise = o.paidPaise + paid, gotPaise = o.gotPaise + got, count = o.count + 1, personId = o.personId ?: pid)
        }
        entries.filter { it.time in r && !it.cancelled }.forEach { e ->
            when (e.type) {
                EType.EXPENSE, EType.INCOME -> {
                    val k = shopKey(e.merchant); if (k.isEmpty() || k !in personNames) return@forEach
                    if (e.type == EType.EXPENSE) add(k, e.merchant!!.trim(), e.amountPaise, 0, personNames[k]) else add(k, e.merchant!!.trim(), 0, e.amountPaise, personNames[k])
                }
                EType.UDHAR_GAVE, EType.UDHAR_GOT -> {
                    val p = people.firstOrNull { it.id == e.personId } ?: return@forEach
                    val k = shopKey(p.name)
                    if (e.type == EType.UDHAR_GAVE) add(k, p.name, e.amountPaise, 0, p.id) else add(k, p.name, 0, e.amountPaise, p.id)
                }
            }
        }
        return m.values.map { s -> s.copy(udharPaise = s.personId?.let { id -> people.firstOrNull { it.id == id }?.let { Ledger.personBalance(it, udhar) } }) }
            .sortedByDescending { it.paidPaise + it.gotPaise }
    }

    // ---------------- price history

    data class PricePoint(val date: LocalDate, val pricePaise: Long)
    data class PriceLine(val item: String, val shop: String, val points: List<PricePoint>, val per: String = "each") {
        val first get() = points.first().pricePaise
        val last get() = points.last().pricePaise
        val change get() = last - first
    }

    /** Same item at the same shop bought more than once with a price: how its price moved. */
    fun priceHistory(items: List<EntryItem>, shopNames: Map<String, String>): List<PriceLine> =
        items.filter { it.pricePaise > 0 && it.shopKey.isNotBlank() }.groupBy { Triple(it.shopKey, it.name.trim().lowercase(Locale.ROOT), unitLabel(it.unit)) }
            .mapNotNull { (k, l) ->
                if (l.size < 2) return@mapNotNull null
                val pts = l.sortedBy { it.time }.map { PricePoint(Dates.date(it.time), unitPrice(it.pricePaise, it.qty, it.unit)) }
                PriceLine(l.last().name.trim(), shopNames[k.first] ?: k.first.replaceFirstChar { it.titlecase(Locale.ROOT) }, pts, k.third)
            }.sortedWith(compareByDescending<PriceLine> { abs(it.change) * 100 / it.first.coerceAtLeast(1) }.thenBy { it.item })

    // ---------------- year summary

    data class Year(
        val year: Int, val received: Long, val spent: Long, val invested: Long, val interest: Long, val cashback: Long, val charges: Long,
        val refunds: Long, val insurance: Long, val topSpends: List<Entry>, val topCategories: List<Pair<Long?, Long>>, val byMonth: List<Pair<YearMonth, Long>>,
    )

    fun year(entries: List<Entry>, year: Int, catByName: (String, String) -> Set<Long>): Year {
        val r = Dates.startOf(LocalDate.of(year, 1, 1))..Dates.endOf(LocalDate.of(year, 12, 31))
        val inYear = entries.filter { it.time in r && !it.cancelled }
        fun sumCat(kind: String, name: String) = catByName(kind, name).let { ids -> inYear.filter { it.categoryId in ids && (if (kind == "INCOME") Ledger.isIncome(it) else Ledger.isSpending(it)) }.sumOf { it.amountPaise } }
        val byMonth = (1..12).map { YearMonth.of(year, it) }.map { ym -> ym to Ledger.spent(entries, Dates.monthRange(ym)) }
        return Year(
            year, Ledger.received(entries, r), Ledger.spent(entries, r), inYear.filter { it.type == EType.INVEST }.sumOf { it.amountPaise },
            sumCat("INCOME", "Interest"), sumCat("INCOME", "Cashback"), sumCat("EXPENSE", "Bank charges"),
            inYear.filter { Ledger.isRefund(it) }.sumOf { it.amountPaise }, sumCat("EXPENSE", "Insurance"),
            inYear.filter { Ledger.isSpending(it) }.sortedByDescending { it.amountPaise }.take(5),
            Ledger.byCategory(entries, r).toList().filter { it.second > 0 }.sortedByDescending { it.second }.take(5), byMonth,
        )
    }

    // ---------------- unusual debit

    /** Reasons a new debit looks out of the ordinary (empty = looks normal). [past] = his recent spending entries. */
    fun unusual(amountPaise: Long, time: Long, accountId: Long?, international: Boolean, past: List<Entry>, now: Long = System.currentTimeMillis()): List<String> {
        val why = ArrayList<String>()
        val recent = past.filter { !it.cancelled && (it.type == EType.EXPENSE || it.type == EType.UDHAR_GAVE) && it.time in (now - 90L * 86_400_000L)..now }.map { it.amountPaise }.sorted()
        val big = if (recent.size >= 10) maxOf(500_000L, recent[(recent.size * 9) / 10] * 3) else 1_000_000L   // ₹5,000 / ₹10,000 floors
        if (amountPaise >= big) why += "a much bigger amount than you usually spend"
        val hour = Dates.dateTime(time).hour
        if (hour in 0..4) why += "at ${Dates.time(time)}, late at night"
        if (international) why += "an international payment"
        if (accountId != null) {
            val used = past.filter { it.accountId == accountId || it.toAccountId == accountId }
            val lastUse = used.filter { it.time < time - 60_000L }.maxOfOrNull { it.time }
            if (used.isNotEmpty() && lastUse != null && time - lastUse > 60L * 86_400_000L) why += "from an account you haven't used for over 2 months"
        }
        return why
    }

    // ---------------- reminders

    fun daysLeft(r: Reminder, today: LocalDate = Dates.today()) = ChronoUnit.DAYS.between(today, LocalDate.ofEpochDay(r.dueEpochDay))
}
