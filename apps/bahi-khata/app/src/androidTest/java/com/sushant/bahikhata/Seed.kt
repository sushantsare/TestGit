package com.sushant.bahikhata

import com.sushant.bahikhata.data.*
import java.time.LocalDate
import java.time.YearMonth

/** Realistic sample data for the walkthrough (a student's month in Bihar). */
object Seed {
    suspend fun fill(repo: Repo) {
        repo.wipeAll()
        repo.ensureCategories()   // a fresh install creates them in the background; do not race it
        val cats = repo.dao.categoriesNow()
        fun cat(n: String, k: String = "EXPENSE") = cats.first { it.name == n && it.kind == k }.id
        val sbi = repo.saveAccount(Account(type = AccType.BANK, name = "SBI Savings", bank = "SBI", last4 = "4417", upiApps = "GPay,PhonePe", openingPaise = 18_450_00))
        val kotak = repo.saveAccount(Account(type = AccType.BANK, name = "Kotak 811", bank = "Kotak", last4 = "2290", openingPaise = 5_460_00))
        val hdfc = repo.saveAccount(Account(type = AccType.CARD, name = "HDFC Millennia", bank = "HDFC", last4 = "8812", openingPaise = -4_200_00, limitPaise = 50_000_00, statementDay = 5, dueDay = 25, colorIdx = 0))
        val icici = repo.saveAccount(Account(type = AccType.CARD, name = "ICICI Amazon Pay", bank = "ICICI", last4 = "3301", openingPaise = 0, limitPaise = 40_000_00, statementDay = 28, dueDay = 16, colorIdx = 5))
        val cash = repo.saveAccount(Account(type = AccType.CASH, name = "Cash", openingPaise = 2_370_00))
        repo.saveAccount(Account(type = AccType.WALLET, name = "Paytm Wallet", openingPaise = 540_00))

        val today = LocalDate.now()
        fun t(daysAgo: Long, h: Int = 13) = Dates.startOf(today.minusDays(daysAgo)) + h * 3_600_000L
        val e = listOf(
            Entry(type = EType.EXPENSE, amountPaise = 386_00, accountId = hdfc, categoryId = cat("Food"), merchant = "Swiggy", time = t(0, 21), tags = "food,weekend", note = "Late dinner order"),
            Entry(type = EType.EXPENSE, amountPaise = 500_00, accountId = cash, categoryId = cat("Fuel"), merchant = "HP Petrol pump", time = t(0, 9)),
            Entry(type = EType.INCOME, amountPaise = 28_000_00, accountId = sbi, categoryId = cat("Salary", "INCOME"), merchant = "Salary — Nov", time = t(1, 10)),
            Entry(type = EType.EXPENSE, amountPaise = 349_00, accountId = sbi, categoryId = cat("Bills & Recharge"), merchant = "Jio recharge", upiApp = "GPay", time = t(1, 18)),
            Entry(type = EType.EXPENSE, amountPaise = 1_299_00, accountId = icici, categoryId = cat("Shopping"), merchant = "Amazon", time = t(3)),
            Entry(type = EType.EXPENSE, amountPaise = 1_850_00, accountId = hdfc, categoryId = cat("Shopping"), merchant = "Myntra", time = t(6)),
            Entry(type = EType.EXPENSE, amountPaise = 2_950_00, accountId = sbi, categoryId = cat("Rent"), merchant = "Hostel rent", upiApp = "PhonePe", time = t(8)),
            Entry(type = EType.EXPENSE, amountPaise = 230_00, accountId = sbi, categoryId = cat("Groceries"), merchant = "Ravi Kirana", upiApp = "PhonePe", time = t(2, 19)),
            Entry(type = EType.EXPENSE, amountPaise = 640_00, accountId = kotak, categoryId = cat("Education"), merchant = "Lucent book", time = t(4)),
            Entry(type = EType.EXPENSE, amountPaise = 120_00, accountId = cash, categoryId = cat("Travel"), merchant = "Auto", time = t(5)),
            Entry(type = EType.CASH_WITHDRAWAL, amountPaise = 2_000_00, accountId = kotak, toAccountId = cash, time = t(7), note = "Cash withdrawn"),
            Entry(type = EType.EXPENSE, amountPaise = 412_00, accountId = hdfc, categoryId = cat("Food"), merchant = "Zomato", time = Dates.startOf(YearMonth.now().minusMonths(1).atDay(12)) + 20 * 3_600_000L),
            Entry(type = EType.INCOME, amountPaise = 27_500_00, accountId = sbi, categoryId = cat("Salary", "INCOME"), merchant = "Salary — Oct", time = Dates.startOf(YearMonth.now().minusMonths(1).atDay(1)) + 10 * 3_600_000L),
            Entry(type = EType.EXPENSE, amountPaise = 9_800_00, accountId = sbi, categoryId = cat("Shopping"), merchant = "Laptop bag & mouse", time = Dates.startOf(YearMonth.now().minusMonths(2).atDay(15))),
        )
        val ids = e.map { repo.saveEntry(Repo.SaveRequest(it)) }
        val rahul = repo.savePerson(Person(name = "Rahul Kumar", phone = "9876543210"))
        repo.savePerson(Person(name = "Amit Singh", openingPaise = 2_500_00))
        val priya = repo.savePerson(Person(name = "Priya Sharma"))
        repo.dao.upsertUdhar(Udhar(personId = rahul, gave = true, amountPaise = 193_00, entryId = ids[0], note = "Split: Swiggy", time = t(0, 21)))
        repo.addUdhar(rahul, true, 3_000_00, sbi, "Train ticket", t(10))
        repo.addUdhar(rahul, false, 1_000_00, sbi, "Paid back part", t(4))
        repo.addUdhar(priya, false, 800_00, null, "Borrowed for books", t(12))
        repo.saveBudget(Budget(categoryId = null, limitPaise = 25_000_00))
        repo.saveBudget(Budget(categoryId = cat("Food"), limitPaise = 800_00))
        repo.saveBudget(Budget(categoryId = cat("Shopping"), limitPaise = 4_000_00))
        repo.saveGoal(Goal(name = "New laptop", targetPaise = 45_000_00, savedPaise = 13_000_00, targetEpochDay = today.plusMonths(4).toEpochDay()))
        repo.saveGoal(Goal(name = "Emergency fund", targetPaise = 50_000_00, savedPaise = 30_000_00))
        repo.saveRecurring(Recurring(type = EType.EXPENSE, amountPaise = 2_950_00, accountId = sbi, categoryId = cat("Rent"), merchant = "Hostel rent", note = null, rule = "MONTHLY", nextEpochDay = today.plusDays(20).toEpochDay()))
        val now = System.currentTimeMillis()
        repo.dao.insertSms(SmsItem(key = "s1", sender = "VM-HDFCBK-T", body = "Rs.412.00 spent on HDFC Bank Card 8812 at ZOMATO on 21-11-26. Not you? Call 18002586161", receivedAt = now - 3_600_000,
            status = SmsStatus.PENDING, kind = "DEBIT", amountPaise = 412_00, debit = true, last4 = "8812", accountId = hdfc, merchant = "Zomato", categoryId = cat("Food"), confidence = 100))
        repo.dao.insertSms(SmsItem(key = "s2", sender = "JD-SBIUPI-S", body = "Dear UPI user A/C X4417 debited by 230.0 on date 21Nov26 trf to RAVI KIRANA Refno 532511234567. If not u? call 1800111109. -SBI", receivedAt = now - 7_200_000,
            status = SmsStatus.PENDING, kind = "DEBIT", amountPaise = 230_00, debit = true, last4 = "4417", accountId = sbi, merchant = "Ravi Kirana", upiApp = "PhonePe", categoryId = cat("Groceries"), confidence = 100,
            note = "Already added? You saved ₹230 at 7:00 PM"))
        repo.dao.insertSms(SmsItem(key = "s3", sender = "AD-ICICIT-T", body = "Rs 599.00 credited to your ICICI Bank Credit Card XX3301 towards refund on 20-Nov-26.", receivedAt = now - 86_400_000,
            status = SmsStatus.PENDING, kind = "REFUND", amountPaise = 599_00, debit = false, last4 = "3301", accountId = icici, categoryId = cat("Refund", "INCOME"), confidence = 90))
        repo.dao.insertSms(SmsItem(key = "s4", sender = "AX-UCOBNK-T", body = "A/c XX9021 Dr INR 1,150.00 on 19/11 Ref 4471 Avl 8,240", receivedAt = now - 2 * 86_400_000,
            status = SmsStatus.LOOK, kind = "UNKNOWN_MONEY", amountPaise = 1_150_00, debit = true, last4 = "9021", confidence = 65))
        repo.dao.insertSms(SmsItem(key = "s5", sender = "VM-PAYTMB-S", body = "Rs.60 paid to Chai Point from Paytm Wallet. Txn ID 41234567890.", receivedAt = now - 3 * 86_400_000,
            status = SmsStatus.IGNORED, kind = "DEBIT", amountPaise = 60_00, debit = true, merchant = "Chai Point", confidence = 85))
    }
}
