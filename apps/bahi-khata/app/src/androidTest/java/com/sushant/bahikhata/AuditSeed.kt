package com.sushant.bahikhata

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.room.withTransaction
import com.sushant.bahikhata.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Fills the installed debug app with realistic data for the e2e audit (T7 jank, T9 process death, T13 large data,
 * T14 screenshots). Does nothing in the normal test run; e2e.sh calls it with -e auditSeed <rows>.
 */
@RunWith(AndroidJUnit4::class)
class AuditSeed {
    @Test fun seedForAudit() = runBlocking {
        val args = InstrumentationRegistry.getArguments()
        args.getString("lockOn")?.let { v ->   // e2e: show the lock screen for a screenshot
            ApplicationProvider.getApplicationContext<App>().prefs.update { it.copy(lockOn = v == "1") }; Thread.sleep(2_000); return@runBlocking
        }
        val rows = args.getString("auditSeed")?.toIntOrNull() ?: return@runBlocking
        val app = ApplicationProvider.getApplicationContext<App>()
        Seed.fill(app.repo)
        if (rows > 0) {   // T13: thousands of extra entries across two years
            val accs = app.repo.dao.accountsNow().filter { it.type != AccType.CARD || it.limitPaise > 0 }
            val cats = app.repo.dao.categoriesNow().filter { it.kind == "EXPENSE" }
            val shops = listOf("Swiggy", "Zomato", "Ravi Kirana", "HP Petrol pump", "Amazon", "Flipkart", "Jio recharge", "Chai point", "Metro card", "Medical store")
            val now = System.currentTimeMillis()
            app.db.withTransaction {
                repeat(rows) { i ->
                    app.repo.dao.upsertEntry(Entry(type = EType.EXPENSE, amountPaise = (50 + (i * 37) % 4000) * 100L, accountId = accs[i % accs.size].id,
                        categoryId = cats[i % cats.size].id, merchant = shops[i % shops.size], time = now - i * 3L * 3_600_000L, tags = if (i % 7 == 0) "trip" else ""))
                }
            }
        }
        app.prefs.update { it.copy(setupDone = true, secureScreens = false, smsOn = false) }
        Thread.sleep(2_000)   // settings are saved asynchronously; let them reach disk before the test process ends
    }
}
