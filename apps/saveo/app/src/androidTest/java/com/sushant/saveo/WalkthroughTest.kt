package com.sushant.saveo

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.swipeDown
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.UiDevice
import com.sushant.saveo.ui.theme.ThemeMode
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Clicks through every screen like a user and saves screenshots (light, dark, landscape, states). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(1200)
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val perms: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)
        else GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE)

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val app get() = ctx.applicationContext as SaveoApp
    private val device = UiDevice.getInstance(inst)
    private val failures = ArrayList<String>()

    private fun shot(name: String) {
        inst.waitForIdleSync(); Thread.sleep(1000)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AppShots")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun step(name: String, block: () -> Unit) {
        Log.i("SaveoWalk", "step $name start")
        try { block(); Log.i("SaveoWalk", "step $name ok") } catch (t: Throwable) {
            failures += "$name: ${t.message?.take(300)}"; Log.e("SaveoWalk", "step $name failed", t); runCatching { shot("FAIL_$name") }
        }
    }

    private fun waitTag(tag: String, ms: Long = 10000) = compose.waitUntil(ms) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(t: String, ms: Long = 10000) = compose.waitUntil(ms) { compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun gone(tag: String, ms: Long = 10000) = compose.waitUntil(ms) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }
    private fun tag(t: String): SemanticsNodeInteraction = compose.onNodeWithTag(t, useUnmergedTree = true)
    private fun back() { device.pressBack(); compose.waitForIdle(); Thread.sleep(500) }
    private fun shell(cmd: String): String {
        val pfd = inst.uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    private fun fresh(onboarded: Boolean, dark: Boolean = false, dir: File? = null) = runBlocking {
        app.prefs.resetAll()
        app.database.clearAllTables()
        val root = dir ?: Seed.statuses(ctx)
        app.prefs.testDir = root.absolutePath
        app.prefs.update { it.copy(onboarded = onboarded, theme = if (dark) ThemeMode.DARK else ThemeMode.LIGHT) }
        app.repository.refresh()
        app.repository.markSeenAndGetNew(app.repository.status.value.items)
    }

    @Test fun walkthrough() {
        fresh(onboarded = false)
        val sc = ActivityScenario.launch(MainActivity::class.java)

        step("onboarding") { waitTag("get_started"); shot("01_onboarding") }
        step("setup") {
            tag("get_started").performClick(); waitTag("continue"); shot("02_connect_whatsapp")
            tag("continue").performClick(); waitTag("tile", 15000)
        }
        step("home") { Thread.sleep(1500); shot("03_home_statuses") }
        step("home_scroll_baseline") {
            shell("dumpsys gfxinfo ${ctx.packageName} reset")
            repeat(4) { tag("status_grid").performTouchInput { swipeUp() }; Thread.sleep(300); tag("status_grid").performTouchInput { swipeDown() }; Thread.sleep(300) }
            val g = shell("dumpsys gfxinfo ${ctx.packageName}")
            Log.i("SaveoWalk", "T7 home scroll (debug build): ${g.lines().firstOrNull { it.contains("Janky frames") }?.trim()}")
        }
        step("save_one") {
            compose.onAllNodesWithContentDescription("Save to gallery", useUnmergedTree = true).onFirst().performClick()
            waitText("saved to gallery"); shot("04_saved_snackbar")
        }
        step("viewer_photo") {
            compose.onAllNodesWithTag("tile", useUnmergedTree = true).onFirst().performClick()
            waitTag("viewer"); Thread.sleep(800); shot("05_viewer_photo")
        }
        step("viewer_swipe_smoothness") {
            shell("dumpsys gfxinfo ${ctx.packageName} reset")
            repeat(6) { tag("viewer").performTouchInput { swipeLeft() }; Thread.sleep(450) }
            repeat(6) { tag("viewer").performTouchInput { swipeRight() }; Thread.sleep(450) }
            val g = shell("dumpsys gfxinfo ${ctx.packageName}")
            val line = g.lines().firstOrNull { it.contains("Janky frames") }?.trim()
            Log.i("SaveoWalk", "T7 viewer swipe: $line | ${g.lines().firstOrNull { it.contains("Total frames rendered") }?.trim()}")
        }
        step("viewer_video") {
            // second item (newest after the 1-hour photo) is the music video
            repeat(2) { tag("viewer").performTouchInput { swipeLeft() }; Thread.sleep(600) }
            Thread.sleep(1800); shot("06_viewer_video_with_music")
            tag("viewer_save").performClick(); Thread.sleep(1500); shot("07_viewer_saved")
        }
        step("back_home") { back(); gone("viewer"); Thread.sleep(500) }
        step("selection") {
            compose.onAllNodesWithTag("tile", useUnmergedTree = true)[2].performTouchInput { longClick() }
            Thread.sleep(400)
            compose.onAllNodesWithTag("tile", useUnmergedTree = true)[3].performClick()
            Thread.sleep(400); shot("08_select_many")
            compose.onNodeWithText("Save 2", useUnmergedTree = true).performClick(); waitText("saved to gallery")
        }
        step("save_all") { tag("save_all").performClick(); waitText("saved to gallery"); Thread.sleep(800); shot("09_all_saved") }
        step("saved_tab") { tag("tab_saved").performClick(); waitTag("saved_tile"); Thread.sleep(1200); shot("10_saved_tab") }
        step("saved_viewer_delete") {
            compose.onAllNodesWithTag("saved_tile", useUnmergedTree = true).onFirst().performClick(); waitTag("viewer"); Thread.sleep(800)
            shot("11_saved_viewer")
            tag("viewer_delete").performClick(); waitText("Recycle bin"); shot("12_deleted_undo")
            back()
        }
        step("settings") {
            tag("tab_statuses").performClick(); Thread.sleep(400)
            tag("overflow").performClick(); waitTag("menu_settings"); tag("menu_settings").performClick()
            waitText("Auto-save"); shot("13_settings")
        }
        step("bin") { compose.onNodeWithText("Recycle bin").performClick(); waitText("deleted forever"); Thread.sleep(800); shot("14_recycle_bin"); back() }
        step("diagnostics") { compose.onNodeWithText("Diagnostics").performClick(); waitText("Crash log"); shot("15_diagnostics"); back() }
        step("about") { compose.onNodeWithText("About Saveo").performClick(); waitText("sushant.sare@gmail.com"); shot("16_about"); back() }
        step("folders") { compose.onNodeWithText("WhatsApp folders").performClick(); waitTag("continue"); shot("17_folders"); back() }
        step("notif_rationale") {
            compose.onNodeWithText("New status alerts").performClick(); Thread.sleep(600)
            if (compose.onAllNodesWithText("Turn on alerts?").fetchSemanticsNodes().isNotEmpty()) { shot("18_alerts_reason"); compose.onNodeWithText("Not now").performClick() }
            else shot("18_alerts_on")
            back()
        }
        step("landscape") {
            device.setOrientationLeft(); Thread.sleep(2500); shot("19_landscape_home")
            device.setOrientationNatural(); Thread.sleep(2000)
        }
        sc.close()

        // ---------- dark mode ----------
        fresh(onboarded = true, dark = true)
        val dark = ActivityScenario.launch(MainActivity::class.java)
        step("dark_home") { waitTag("tile", 15000); Thread.sleep(1500); shot("20_dark_home") }
        step("dark_viewer") { compose.onAllNodesWithTag("tile", useUnmergedTree = true).onFirst().performClick(); waitTag("viewer"); Thread.sleep(1000); shot("21_dark_viewer"); back() }
        step("dark_saved") {
            tag("save_all").performClick(); waitText("saved to gallery")
            tag("tab_saved").performClick(); waitTag("saved_tile"); Thread.sleep(1000); shot("22_dark_saved")
            tag("tab_statuses").performClick()
        }
        step("dark_settings") { tag("overflow").performClick(); waitTag("menu_settings"); tag("menu_settings").performClick(); waitText("Auto-save"); shot("23_dark_settings"); back() }
        step("lock_screen") {
            app.prefs.update { it.copy(appLock = true) }; app.lockNow()
            waitTag("lock_screen"); Thread.sleep(1500)
            shot("24_lock_screen")
            app.unlock(); app.prefs.update { it.copy(appLock = false) }
        }
        dark.close()

        // ---------- states ----------
        val empty = File(ctx.filesDir, "test-empty").apply { deleteRecursively(); File(this, "WHATSAPP").mkdirs() }
        fresh(onboarded = true, dir = empty)
        val st = ActivityScenario.launch(MainActivity::class.java)
        step("empty_state") { waitText("No statuses yet"); shot("25_empty_statuses") }
        step("saved_empty") { tag("tab_saved").performClick(); waitText("Nothing saved yet"); shot("26_empty_saved") }
        st.close()

        val lostDir = Seed.statuses(ctx).also { File(it, "LOST_WHATSAPP").writeText("x") }
        fresh(onboarded = true, dir = lostDir)
        val lost = ActivityScenario.launch(MainActivity::class.java)
        step("lost_access") { waitText("lost access"); Thread.sleep(800); shot("27_access_lost") }
        lost.close()

        fresh(onboarded = true, dir = File(ctx.filesDir, "nothing-here").apply { deleteRecursively() })
        app.prefs.testDir = null
        runBlocking { app.repository.refresh() }
        val ns = ActivityScenario.launch(MainActivity::class.java)
        step("not_connected") { waitText("Connect WhatsApp to start"); shot("28_not_connected") }
        ns.close()

        Log.i("SaveoWalk", "failures=${failures.size} ${failures.joinToString(" || ")}")
        assertTrue("Walkthrough failures: $failures", failures.isEmpty())
    }
}
