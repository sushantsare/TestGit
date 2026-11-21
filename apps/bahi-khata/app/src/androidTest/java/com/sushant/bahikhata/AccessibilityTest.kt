package com.sushant.bahikhata

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.accessibility.enableAccessibilityChecks
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sushant.bahikhata.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Audit T2: Google's Accessibility Test Framework on every main screen, light and dark
 * (touch targets, labels, contrast, duplicate descriptions). Any error fails the test.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    private val app get() = ApplicationProvider.getApplicationContext<App>()

    @Before fun data() {
        runBlocking { Seed.fill(app.repo) }
        app.prefs.update { Settings(setupDone = true, secureScreens = false) }
    }

    private val routes = listOf("home", "entries", "accounts", "udhar", "reports", "add?type=EXPENSE", "search", "budgets", "sms", "settings", "categories", "backup", "export", "about")

    @Test fun everyScreenPassesAtf() = runAndroidComposeUiTest<MainActivity> {
        enableAccessibilityChecks()
        listOf(com.sushant.bahikhata.data.ThemeMode.LIGHT, com.sushant.bahikhata.data.ThemeMode.DARK).forEach { theme ->
            app.prefs.update { it.copy(theme = theme) }
            routes.forEach { r ->
                activity!!.runOnUiThread { activity!!.startActivity(Intent(activity, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, r).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
                waitForIdle(); Thread.sleep(600); waitForIdle()
                onRoot().tryPerformAccessibilityChecks()
            }
        }
    }
}
