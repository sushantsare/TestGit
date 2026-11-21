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
}
