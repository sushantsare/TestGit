package com.sushant.bahikhata

import android.content.Intent
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sushant.bahikhata.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Audit T9/CQ5: a half-typed entry survives the activity being destroyed and rebuilt from saved state
 *  (the same path Android uses when it kills a background app and the user comes back from Recents). */
@RunWith(AndroidJUnit4::class)
class StateRestoreTest {
    @get:Rule val compose = createEmptyComposeRule()

    @Test fun typedAmountAndScreenSurviveRecreation() {
        val app = ApplicationProvider.getApplicationContext<App>()
        runBlocking { Seed.fill(app.repo) }
        app.prefs.update { Settings(setupDone = true, secureScreens = false) }
        val sc = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "add?type=EXPENSE"))
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("key_7").fetchSemanticsNodes().isNotEmpty() }
        listOf("key_7", "key_5", "key_0").forEach { compose.onNodeWithTag(it).performClick() }
        compose.onNodeWithTag("f_merchant").performTextInput("Chai point")
        sc.recreate()
        compose.waitUntil(8_000) { compose.onAllNodesWithTag("amount_display").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("amount_display").assertTextEquals("₹750")
        compose.onNodeWithTag("f_merchant").assertTextContains("Chai point")
    }
}
