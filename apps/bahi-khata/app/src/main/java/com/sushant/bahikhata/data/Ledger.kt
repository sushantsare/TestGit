package com.sushant.bahikhata.data

import java.time.LocalDate
import java.time.YearMonth

/** Pure balance / report maths over plain lists (unit tested, used by the UI and workers). */
object Ledger {
    fun nets(accounts: List<Account>, entries: List<Entry>): Map<Long, Long> {
        val m = HashMap<Long, Long>()
        accounts.forEach { m[it.id] = it.openingPaise }
        for (e in entries) {
            if (e.cancelled) continue   // failed / reversed payments never touch a balance
            m.add(e.accountId, effect(e, e.accountId))
            e.toAccountId?.let { if (it != e.accountId) m.add(it, effect(e, it)) }
        }
        return m
    }

    /** What [e] does to account [acc] (0 when it doesn't touch it). */
    fun effect(e: Entry, acc: Long): Long {
        if (e.cancelled) return 0
        val a = e.amountPaise
        return when {
            e.accountId == acc -> when (e.type) {
                EType.EXPENSE, EType.UDHAR_GAVE, EType.INVEST -> -a
                EType.INCOME, EType.UDHAR_GOT, EType.ADJUSTMENT -> a
                EType.CARD_PAYMENT, EType.CASH_WITHDRAWAL, EType.TRANSFER -> -a
                else -> 0
            }
            e.toAccountId == acc && (e.type == EType.CARD_PAYMENT || e.type == EType.CASH_WITHDRAWAL || e.type == EType.TRANSFER) -> a
            else -> 0
        }
    }

    /** The app's balance of [acc] at [time] (opening + everything up to then). Used to check the bank's "Avl bal". */
    fun netAt(acc: Account, entries: List<Entry>, time: Long): Long =
        acc.openingPaise + entries.filter { it.time <= time && !it.cancelled }.sumOf { effect(it, acc.id) }

    private fun HashMap<Long, Long>.add(k: Long, v: Long) { this[k] = (this[k] ?: 0L) + v }

    fun isSpending(e: Entry) = e.type == EType.EXPENSE && !e.cancelled
    /** A refund linked to its purchase lowers that spending instead of counting as income. */
    fun isRefund(e: Entry) = e.type == EType.INCOME && e.refundOf != null && !e.cancelled
    fun isIncome(e: Entry) = e.type == EType.INCOME && e.refundOf == null && !e.cancelled

    fun inRange(e: Entry, r: LongRange) = e.time in r

    fun spent(entries: List<Entry>, r: LongRange) = maxOf(0L, entries.filter { isSpending(it) && it.time in r }.sumOf { it.amountPaise } -
        entries.filter { isRefund(it) && it.time in r }.sumOf { it.amountPaise })
    fun received(entries: List<Entry>, r: LongRange) = entries.filter { isIncome(it) && it.time in r }.sumOf { it.amountPaise }

    fun byCategory(entries: List<Entry>, r: LongRange): Map<Long?, Long> {
        val m = entries.filter { isSpending(it) && it.time in r }.groupBy { it.categoryId }.mapValues { e -> e.value.sumOf { it.amountPaise } }.toMutableMap()
        entries.filter { isRefund(it) && it.time in r }.forEach { rf -> m[rf.categoryId] = maxOf(0L, (m[rf.categoryId] ?: 0L) - rf.amountPaise) }
        return m
    }

    fun byAccount(entries: List<Entry>, r: LongRange): Map<Long, Long> =
        entries.filter { isSpending(it) && it.time in r }.groupBy { it.accountId }.mapValues { e -> e.value.sumOf { it.amountPaise } }

    fun byMerchant(entries: List<Entry>, r: LongRange): List<Pair<String, Long>> =
        entries.filter { isSpending(it) && it.time in r && !it.merchant.isNullOrBlank() }
            .groupBy { it.merchant!!.trim() }.mapValues { e -> e.value.sumOf { it.amountPaise } }
            .toList().sortedByDescending { it.second }

    fun byTag(entries: List<Entry>, r: LongRange): List<Pair<String, Long>> {
        val m = HashMap<String, Long>()
        entries.filter { isSpending(it) && it.time in r }.forEach { e -> tags(e.tags).forEach { t -> m[t] = (m[t] ?: 0L) + e.amountPaise } }
        return m.toList().sortedByDescending { it.second }
    }

    fun tags(s: String): List<String> = s.split(',', ' ', '#').map { it.trim().lowercase() }.filter { it.isNotEmpty() }.distinct()

    fun daily(entries: List<Entry>, ym: YearMonth): Map<Int, Long> =
        entries.filter { isSpending(it) && it.time in Dates.monthRange(ym) }.groupBy { Dates.date(it.time).dayOfMonth }
            .mapValues { e -> e.value.sumOf { it.amountPaise } }

    /** Person balance: positive = they owe me. */
    fun personBalance(p: Person, udhar: List<Udhar>): Long =
        p.openingPaise + udhar.filter { it.personId == p.id }.sumOf { if (it.gave) it.amountPaise else -it.amountPaise }

    data class CardBill(val bill: Long, val minDue: Long, val due: LocalDate?, val outstanding: Long, val used: Long, val available: Long, val cycleStart: LocalDate?)

    fun cardBill(card: Account, net: Long, entries: List<Entry>, today: LocalDate = Dates.today()): CardBill {
        val outstanding = maxOf(0L, -net)
        val available = if (card.limitPaise > 0) maxOf(0L, card.limitPaise - outstanding) else 0L
        if (card.statementDay <= 0) return CardBill(0, 0, null, outstanding, outstanding, available, null)
        val lastSt = CardMath.lastStatement(today, card.statementDay)
        val stEnd = Dates.endOf(lastSt)
        val mine = entries.filter { (it.accountId == card.id || it.toAccountId == card.id) && !it.cancelled }
        val paidAfter = mine.filter { it.time > stEnd && it.toAccountId == card.id && it.type == EType.CARD_PAYMENT }.sumOf { it.amountPaise } +
            mine.filter { it.time > stEnd && it.accountId == card.id && (it.type == EType.INCOME) }.sumOf { it.amountPaise }
        val due = CardMath.dueAfter(lastSt, card.dueDay.takeIf { it > 0 } ?: card.statementDay)
        val smsBillValid = card.billPaise >= 0 && card.billDueEpochDay >= lastSt.toEpochDay()
        val billAtStatement = if (smsBillValid) card.billPaise else {
            val upto = card.openingPaise.takeIf { card.createdAt <= stEnd } ?: 0L
            val netAt = upto + mine.filter { it.time <= stEnd }.sumOf { e -> effect(e, card.id) }
            maxOf(0L, -netAt)
        }
        val bill = maxOf(0L, billAtStatement - paidAfter)
        val dueDate = if (smsBillValid) LocalDate.ofEpochDay(card.billDueEpochDay) else due
        val min = if (smsBillValid && card.minDuePaise >= 0) maxOf(0L, minOf(card.minDuePaise, bill)) else CardMath.minDue(bill)
        return CardBill(bill, min, dueDate, outstanding, outstanding, available, lastSt.plusDays(1))
    }

    /** Next date after [from] for a recurring rule. */
    fun nextDate(from: LocalDate, rule: String): LocalDate = when (rule) {
        "DAILY" -> from.plusDays(1)
        "WEEKLY" -> from.plusWeeks(1)
        "YEARLY" -> from.plusYears(1)
        else -> from.plusMonths(1)
    }

    /** Equal split of [amount] between [people] (and me when [includeMe]). Leftover paise stay with me, or go to the
     *  first people when I'm not included, so the shares always add up exactly. */
    fun splitShares(amount: Long, people: List<Long>, includeMe: Boolean): List<Pair<Long, Long>> {
        if (people.isEmpty() || amount <= 0) return emptyList()
        val n = people.size + if (includeMe) 1 else 0
        val base = amount / n
        val rest = if (includeMe) 0L else amount - base * n
        return people.mapIndexed { i, p -> p to base + if (i < rest) 1 else 0 }
    }
}
