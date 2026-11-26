package com.sushant.bahikhata

import android.content.Intent
import android.os.Build
import androidx.compose.ui.test.junit4.accessibility.enableAccessibilityChecks
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.tryPerformAccessibilityChecks
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sushant.bahikhata.data.Settings
import com.sushant.bahikhata.data.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit T2: Google's Accessibility Test Framework on every main screen, light and dark
 * (touch targets, labels, contrast, duplicate descriptions). Any error fails the test. Needs Android 14+.
 */
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<App>()
    private val routes = listOf("home", "entries", "accounts", "udhar", "reports", "add?type=EXPENSE", "search", "budgets", "sms", "settings", "categories", "backup", "export", "about",
        "hide_amounts", "upi_names", "reminders", "insights", "insights/subs", "insights/people", "insights/year")

    @Test fun everyScreenPassesAtf() {
        assumeTrue("ATF in Compose needs API 34+", Build.VERSION.SDK_INT >= 34)
        runBlocking { Seed.fill(app.repo) }
        app.prefs.update { Settings(setupDone = true, secureScreens = false) }
        val scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
        compose.enableAccessibilityChecks()
        listOf(ThemeMode.LIGHT, ThemeMode.DARK).forEach { theme ->
            app.prefs.update { it.copy(theme = theme) }
            routes.forEach { r ->
                scenario.onActivity { a -> a.startActivity(Intent(a, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, r).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                compose.waitForIdle(); Thread.sleep(600); compose.waitForIdle()
                compose.onRoot().tryPerformAccessibilityChecks()
            }
        }
    }
}
