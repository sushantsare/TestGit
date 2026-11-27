package com.sushant.bahikhata

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.sms.SmsEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepoTest {
    private val ctx get() = ApplicationProvider.getApplicationContext<App>()
    private lateinit var db: AppDb
    private lateinit var repo: Repo

    @Before fun setUp() = runBlocking { db = AppDb.inMemory(ctx); repo = Repo(ctx, db, ctx.prefs); repo.ensureCategories() }
    @After fun tearDown() = db.close()

    private suspend fun nets() = Ledger.nets(repo.dao.accountsNow(), repo.dao.entriesNow())

    @Test fun editingToNoSplitRemovesShares() = runBlocking {
        val acc = repo.saveAccount(Account(type = AccType.CASH, name = "Cash", openingPaise = 1_000_00))
        val p = repo.savePerson(Person(name = "Rahul"))
        val id = repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 400_00, accountId = acc, merchant = "Swiggy"), splits = listOf(p to 200_00)))
        assertEquals(1, repo.dao.udharOfEntry(id).size)
        val e = repo.dao.entry(id)!!
        repo.saveEntry(Repo.SaveRequest(e.copy(amountPaise = 500_00), splits = emptyList()))
        assertEquals(0, repo.dao.udharOfEntry(id).size)
        repo.saveEntry(Repo.SaveRequest(e.copy(amountPaise = 501_00), splits = Ledger.splitShares(501_00, listOf(p), true)))
        assertEquals(250_50L, repo.dao.udharOfEntry(id).single().amountPaise)
    }

    @Test fun openingBalanceIsNotIncome() = runBlocking {
        repo.saveAccount(Account(type = AccType.BANK, name = "SBI", openingPaise = 10_000_00))
        assertEquals(0L, Ledger.received(repo.dao.entriesNow(), 0L..Long.MAX_VALUE))
        assertEquals(10_000_00L, nets().values.first())
    }

    @Test fun cardBillPaymentIsNotSpending() = runBlocking {
        val bank = repo.saveAccount(Account(type = AccType.BANK, name = "SBI", openingPaise = 10_000_00))
        val card = repo.saveAccount(Account(type = AccType.CARD, name = "HDFC", openingPaise = 0, limitPaise = 50_000_00, statementDay = 5, dueDay = 25))
        repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 1_000_00, accountId = card)))
        repo.transfer(EType.CARD_PAYMENT, bank, card, 1_000_00)
        val n = nets()
        assertEquals(9_000_00L, n[bank]); assertEquals(0L, n[card])
        assertEquals(1_000_00L, Ledger.spent(repo.dao.entriesNow(), 0L..Long.MAX_VALUE))
    }

    @Test fun adjustBalanceMatchesBank() = runBlocking {
        val id = repo.saveAccount(Account(type = AccType.BANK, name = "SBI", openingPaise = 5_000_00))
        repo.adjustBalance(repo.dao.account(id)!!, 4_750_00)
        assertEquals(4_750_00L, nets()[id])
        assertEquals(0L, Ledger.spent(repo.dao.entriesNow(), 0L..Long.MAX_VALUE))
    }

    @Test fun deleteAndUndoRestoresEverything() = runBlocking {
        val acc = repo.saveAccount(Account(type = AccType.CASH, name = "Cash", openingPaise = 1_000_00))
        val p = repo.savePerson(Person(name = "Rahul"))
        val id = repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 400_00, accountId = acc, merchant = "Swiggy"), splits = listOf(p to 200_00)))
        assertEquals(200_00L, Ledger.personBalance(repo.dao.person(p)!!, repo.dao.udharNow()))
        val d = repo.deleteEntry(repo.dao.entry(id)!!)
        assertNull(repo.dao.entry(id)); assertEquals(0L, Ledger.personBalance(repo.dao.person(p)!!, repo.dao.udharNow()))
        repo.undoDelete(d)
        assertNotNull(repo.dao.entry(id)); assertEquals(200_00L, Ledger.personBalance(repo.dao.person(p)!!, repo.dao.udharNow()))
    }

    @Test fun merchantMemoryRemembersCategoryAndAccount() = runBlocking {
        val acc = repo.saveAccount(Account(type = AccType.CARD, name = "HDFC"))
        val food = repo.dao.categoriesNow().first { it.name == "Food" }.id
        repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 300_00, accountId = acc, categoryId = food, merchant = "Swiggy")))
        val m = repo.merchant("swiggy")!!
        assertEquals(food, m.categoryId); assertEquals(acc, m.accountId)
    }

    @Test fun udharWithAccountMovesMoneyButIsNotSpending() = runBlocking {
        val acc = repo.saveAccount(Account(type = AccType.BANK, name = "SBI", openingPaise = 5_000_00))
        val p = repo.savePerson(Person(name = "Amit"))
        repo.addUdhar(p, true, 1_000_00, acc, "Lent")
        repo.addUdhar(p, false, 400_00, acc, "Back")
        assertEquals(4_400_00L, nets()[acc])
        assertEquals(600_00L, Ledger.personBalance(repo.dao.person(p)!!, repo.dao.udharNow()))
        assertEquals(0L, Ledger.spent(repo.dao.entriesNow(), 0L..Long.MAX_VALUE))
    }

    @Test fun recurringAddsDueEntriesOnce() = runBlocking {
        val acc = repo.saveAccount(Account(type = AccType.BANK, name = "SBI"))
        val start = LocalDate.now().minusMonths(2)
        repo.saveRecurring(Recurring(type = EType.EXPENSE, amountPaise = 2_950_00, accountId = acc, categoryId = null, merchant = "Rent", note = null, rule = "MONTHLY", nextEpochDay = start.toEpochDay()))
        val n1 = repo.runRecurring(); val n2 = repo.runRecurring()
        assertEquals(3, n1); assertEquals(0, n2)
        assertEquals(3, repo.dao.entriesNow().size)
    }

    @Test fun smsAcceptCreatesEntryAndLearnsLast4() = runBlocking {
        val acc = repo.saveAccount(Account(type = AccType.CARD, name = "HDFC"))
        val id = repo.dao.insertSms(SmsItem(key = "k1", sender = "VM-HDFCBK-T", body = "x", receivedAt = System.currentTimeMillis(), status = SmsStatus.PENDING, kind = "DEBIT", amountPaise = 412_00, last4 = "8812", merchant = "Zomato"))
        val item = repo.dao.smsItem(id)!!
        val entryId = repo.acceptSms(item, acc, null, "Zomato")
        assertEquals(412_00L, repo.dao.entry(entryId)!!.amountPaise)
        assertEquals(SmsStatus.ACCEPTED, repo.dao.smsItem(id)!!.status)
        assertTrue(repo.dao.account(acc)!!.last4.contains("8812"))
    }

    @Test fun atmSmsBecomesCashWithdrawal() = runBlocking {
        val bank = repo.saveAccount(Account(type = AccType.BANK, name = "Axis", openingPaise = 10_000_00))
        val id = repo.dao.insertSms(SmsItem(key = "k2", sender = "VM-AXISBK-T", body = "x", receivedAt = System.currentTimeMillis(), status = SmsStatus.PENDING, kind = "ATM", amountPaise = 2_000_00, last4 = "2290"))
        repo.acceptSms(repo.dao.smsItem(id)!!, bank, null, "")
        val n = nets()
        assertEquals(8_000_00L, n[bank])
        assertEquals(2_000_00L, n.entries.first { it.key != bank }.value)
        assertEquals(0L, Ledger.spent(repo.dao.entriesNow(), 0L..Long.MAX_VALUE))
    }

    @Test fun smsEngineKeysToleratePduVsInboxTime() {
        val e = SmsEngine(ctx, repo, CoroutineScope(Dispatchers.IO))
        val t = System.currentTimeMillis()
        val k = e.key("VM-HDFCBK-T", "Rs 10 spent", t)
        assertTrue((-1..1).any { e.key("VM-HDFCBK-T", "Rs 10 spent", t + 40_000, it) == k })
    }

    @Test fun cardBillFromEntries() = runBlocking {
        val today = LocalDate.now()
        val st = 5; val due = 25
        val card = repo.saveAccount(Account(type = AccType.CARD, name = "HDFC", statementDay = st, dueDay = due, limitPaise = 50_000_00, createdAt = 0))
        val lastSt = CardMath.lastStatement(today, st)
        repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 1_500_00, accountId = card, time = Dates.startOf(lastSt.minusDays(3)))))
        repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 700_00, accountId = card, time = Dates.startOf(lastSt.plusDays(1)) + 3_600_000)))
        val b = Ledger.cardBill(repo.dao.account(card)!!, nets()[card]!!, repo.dao.entriesNow(), today)
        assertEquals(1_500_00L, b.bill); assertEquals(2_200_00L, b.outstanding)
    }

    // ---------------- 1.1

    private fun sms(key: String, amount: Long, debit: Boolean, acc: Long?, kind: String = if (debit) "DEBIT" else "CREDIT", flags: String = "", vpa: String = "", merchant: String = "") =
        SmsItem(key = key, sender = "VM-SBIUPI-S", body = "test $key", receivedAt = System.currentTimeMillis(), status = SmsStatus.PENDING, kind = kind,
            amountPaise = amount, debit = debit, accountId = acc, confidence = 100, flags = flags, vpa = vpa, merchant = merchant)

    @Test fun transferSuggestionMovesMoneyWithoutSpending() = runBlocking {
        val a = repo.saveAccount(Account(type = AccType.BANK, name = "A", openingPaise = 10_000_00))
        val b = repo.saveAccount(Account(type = AccType.BANK, name = "B", openingPaise = 0))
        val id = repo.dao.insertSms(sms("t1", 1_500_00, true, a, kind = "TRANSFER", flags = "FROM:$a,TO:$b"))
        repo.acceptSms(repo.dao.smsItem(id)!!, a, null, "")
        val n = nets()
        assertEquals(8_500_00L, n[a]); assertEquals(1_500_00L, n[b])
        val r = Dates.monthRange(java.time.YearMonth.now())
        assertEquals(0L, Ledger.spent(repo.dao.entriesNow(), r)); assertEquals(0L, Ledger.received(repo.dao.entriesNow(), r))
    }

    @Test fun udharSettleFromSms() = runBlocking {
        val a = repo.saveAccount(Account(type = AccType.BANK, name = "A", openingPaise = 0))
        val p = repo.savePerson(Person(name = "Rahul", openingPaise = 800_00))
        val id = repo.dao.insertSms(sms("u1", 500_00, false, a, flags = "PERSON:$p"))
        repo.acceptSms(repo.dao.smsItem(id)!!, a, null, "Rahul", kind = "UDHAR")
        assertEquals(300_00L, Ledger.personBalance(repo.dao.person(p)!!, repo.dao.udharNow()))
        assertEquals(500_00L, nets()[a])
        assertEquals(0L, Ledger.received(repo.dao.entriesNow(), Dates.monthRange(java.time.YearMonth.now())))
    }

    @Test fun refundLinksToPurchaseAndLowersSpending() = runBlocking {
        val a = repo.saveAccount(Account(type = AccType.CARD, name = "Card", openingPaise = 0))
        val shop = repo.dao.categoriesNow().first { it.name == "Shopping" }.id
        repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 1_299_00, accountId = a, categoryId = shop, merchant = "Amazon", time = System.currentTimeMillis() - 86_400_000L)))
        val id = repo.dao.insertSms(sms("r1", 599_00, false, a, kind = "REFUND", merchant = "Amazon"))
        val eid = repo.acceptSms(repo.dao.smsItem(id)!!, a, null, "Amazon")
        val refund = repo.dao.entry(eid)!!
        assertNotNull(refund.refundOf); assertEquals(shop, refund.categoryId)
        val r = Dates.monthRange(java.time.YearMonth.now()).let { (it.first - 40L * 86_400_000L)..it.last }
        assertEquals(700_00L, Ledger.spent(repo.dao.entriesNow(), r))
        assertEquals(0L, Ledger.received(repo.dao.entriesNow(), r))
    }

    @Test fun cancelledEntryCountsNowhere() = runBlocking {
        val a = repo.saveAccount(Account(type = AccType.BANK, name = "A", openingPaise = 1_000_00))
        val id = repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 400_00, accountId = a, merchant = "Myntra")))
        repo.setCancelled(repo.dao.entry(id)!!, true)
        assertEquals(1_000_00L, nets()[a])
        assertEquals(0L, Ledger.spent(repo.dao.entriesNow(), 0L..Long.MAX_VALUE))
        repo.setCancelled(repo.dao.entry(id)!!, false)
        assertEquals(600_00L, nets()[a])
    }

    @Test fun itemsFromSmsAreSavedWithTheEntry() = runBlocking {
        val a = repo.saveAccount(Account(type = AccType.BANK, name = "A", openingPaise = 1_000_00))
        val id = repo.dao.insertSms(sms("i1", 100_00, true, a, merchant = "Ravi Kirana").copy(itemsText = "milk 60, bread 40"))
        val eid = repo.acceptSms(repo.dao.smsItem(id)!!, a, null, "Ravi Kirana")
        val items = repo.dao.itemsOf(eid)
        assertEquals(listOf("Milk" to 60_00L, "Bread" to 40_00L), items.map { it.name to it.pricePaise })
        assertEquals("ravi kirana", items.first().shopKey)
    }

    @Test fun namingUpiUpdatesWaitingSuggestionsAndEntries() = runBlocking {
        val a = repo.saveAccount(Account(type = AccType.BANK, name = "A", openingPaise = 1_000_00))
        repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 50_00, accountId = a, merchant = "Paytmqr2810", vpa = "paytmqr2810@paytm")))
        val sid = repo.dao.insertSms(sms("n1", 80_00, true, a, vpa = "paytmqr2810@paytm", merchant = "Paytmqr2810"))
        val groc = repo.dao.categoriesNow().first { it.name == "Groceries" }.id
        repo.nameUpi(UpiName(key = "vpa:paytmqr2810@paytm", display = "Sharma Kirana", categoryId = groc))
        assertEquals("Sharma Kirana", repo.dao.smsItem(sid)!!.merchant)
        assertEquals(groc, repo.dao.smsItem(sid)!!.categoryId)
        assertTrue(repo.dao.entriesNow().all { it.merchant == "Sharma Kirana" })
    }

    @Test fun accountDeleteCanBeUndone() = runBlocking {
        val a = repo.saveAccount(Account(type = AccType.BANK, name = "A", openingPaise = 1_000_00))
        val id = repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 100_00, accountId = a, merchant = "Shop")))
        repo.setItems(id, listOf(Insights.Item("Pen", 100_00)))
        val b = repo.deleteAccount(repo.dao.account(a)!!)
        assertNull(repo.dao.account(a)); assertTrue(repo.dao.entriesNow().isEmpty())
        repo.undoDeleteAccount(b)
        assertNotNull(repo.dao.account(a)); assertEquals(1, repo.dao.entriesNow().size); assertEquals(1, repo.dao.itemsOf(id).size)
        assertEquals(900_00L, nets()[a])
    }

    @Test fun matchBankUsesTheSmsMoment() = runBlocking {
        val now = System.currentTimeMillis()
        val a = repo.saveAccount(Account(type = AccType.BANK, name = "A", openingPaise = 1_000_00))
        repo.saveAccount(repo.dao.account(a)!!.copy(bankBalPaise = 1_200_00, bankBalAt = now - 60_000))
        repo.saveEntry(Repo.SaveRequest(Entry(type = EType.EXPENSE, amountPaise = 100_00, accountId = a, merchant = "After", time = now)))
        repo.matchBank(repo.dao.account(a)!!)
        val acc = repo.dao.account(a)!!
        assertEquals(1_200_00L, Ledger.netAt(acc, repo.dao.entriesNow(), acc.bankBalAt))
        assertEquals(1_100_00L, nets()[a])
    }

    @Test fun newCategoriesAddedForOlderInstalls() = runBlocking {
        assertTrue(repo.dao.categoriesNow().any { it.name == "Bank charges" && it.kind == "EXPENSE" })
        assertTrue(repo.dao.categoriesNow().any { it.name == "Cashback" && it.kind == "INCOME" })
        assertEquals(1, repo.dao.categoriesNow().count { it.name == "Insurance" })
    }
}
