package com.sushant.bahikhata

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Environment
import android.provider.MediaStore
import androidx.activity.BackEventCompat
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.sushant.bahikhata.data.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Back motion: drives the predictive back gesture the way the system does (start → progress → release or cancel)
 * and saves frames, so the animation can be checked: the top screen must shrink toward the finger while the
 * previous screen shows behind it; cancelling must leave the user where they were.
 */
@RunWith(AndroidJUnit4::class)
class PredictiveBackTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val app get() = ApplicationProvider.getApplicationContext<App>()

    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(300)
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "back_$name.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppShots")
        }
        app.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { uri -> app.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    @Test fun backGestureShrinksTopScreenAndCanBeCancelled() {
        runBlocking { Seed.fill(app.repo) }
        app.prefs.update { Settings(setupDone = true, secureScreens = false) }
        val sc = ActivityScenario.launch<MainActivity>(Intent(app, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "accounts"))
        compose.waitUntil(8_000) { has("accounts_list") }
        sc.onActivity { it.startActivity(Intent(it, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, "settings").addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        compose.waitUntil(8_000) { has("settings_list") }
        Thread.sleep(600)
        val y = 1200f
        fun ev(p: Float) = BackEventCompat(40f + 600f * p, y, p, BackEventCompat.EDGE_LEFT)
        // 1) swipe half-way and let go back: nothing changes
        sc.onActivity { it.onBackPressedDispatcher.dispatchOnBackStarted(ev(0f)) }
        listOf(0.1f, 0.25f, 0.4f, 0.5f).forEach { p -> sc.onActivity { it.onBackPressedDispatcher.dispatchOnBackProgressed(ev(p)) } }
        shot("1_gesture_half_way")
        sc.onActivity { it.onBackPressedDispatcher.dispatchOnBackCancelled() }
        compose.waitForIdle(); Thread.sleep(600)
        shot("2_gesture_cancelled")
        assertTrue("cancelled gesture must stay on Settings", has("settings_list"))
        // 2) swipe and release: goes back to Accounts
        sc.onActivity { it.onBackPressedDispatcher.dispatchOnBackStarted(ev(0f)) }
        listOf(0.2f, 0.5f, 0.8f).forEach { p -> sc.onActivity { it.onBackPressedDispatcher.dispatchOnBackProgressed(ev(p)) } }
        shot("3_gesture_80_percent")
        sc.onActivity { it.onBackPressedDispatcher.onBackPressed() }
        compose.waitUntil(5_000) { has("accounts_list") && !has("settings_list") }
        shot("4_back_done")
    }
}
