package com.sushant.gallery

import android.graphics.Bitmap
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.pinch
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.sushant.gallery.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Clicks through every screen like a user, checks the filmstrip flick-to-delete + Undo, and saves screenshots. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val timeout: org.junit.rules.Timeout = org.junit.rules.Timeout.seconds(1500)
    @get:Rule val compose = createEmptyComposeRule()

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val app get() = ctx.applicationContext as GalleryApp
    private val device = UiDevice.getInstance(inst)
    private val failures = ArrayList<String>()
    private var shotNo = 0
    private val shotsDir = File(Seed.root, "Pictures/AppShots").apply { mkdirs(); File(this, ".nomedia").createNewFile() }

    private fun shot(name: String) {
        inst.waitForIdleSync(); Thread.sleep(900)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        shotNo++
        File(shotsDir, "%02d_%s.png".format(shotNo, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun step(name: String, block: () -> Unit) {
        Log.i("GalleryWalk", "step $name start")
        if (name !in setOf("photos", "select", "onboarding")) runCatching { home() }
        try { block(); Log.i("GalleryWalk", "step $name ok") } catch (t: Throwable) {
            failures += "$name: ${t.message?.take(300)}"; Log.e("GalleryWalk", "step $name failed", t); runCatching { shot("FAIL_$name") }
        }
    }

    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 15000) = compose.waitUntil(ms) { has(tag) }
    private fun waitText(t: String, ms: Long = 15000) = compose.waitUntil(ms) { compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tag(t: String): SemanticsNodeInteraction = compose.onNodeWithTag(t, useUnmergedTree = true)
    private fun firstCell() = compose.onAllNodesWithTag("cell", useUnmergedTree = true).onFirst()
    private fun back() { device.pressBack(); compose.waitForIdle(); Thread.sleep(600) }
    private fun main(block: () -> Unit) = inst.runOnMainSync(block)
    private fun count(): Int = compose.onNodeWithTag("film_count", useUnmergedTree = true).fetchSemanticsNode()
        .config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text)?.firstOrNull()?.text?.substringAfter("/ ")?.trim()?.toIntOrNull() ?: -1

    /** Back to the Photos tab from wherever a failed step left the app. */
    private fun home() {
        repeat(6) { if (has("tab_photos") && !has("selbar")) return@repeat; device.pressBack(); Thread.sleep(500) }
        if (has("tab_photos")) { tag("tab_photos").performClick(); compose.waitForIdle() }
    }

    @Test fun walkthrough() {
        Seed.grantAllFiles(inst)
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) { app.db.clearAllTables() }
        Seed.library(inst)
        main { app.prefs.setTheme(ThemeMode.LIGHT) }
        var scenario = ActivityScenario.launch(MainActivity::class.java)

        step("photos") { waitTag("cell", 30000); Thread.sleep(1500); shot("photos") }
        step("select") {
            firstCell().performTouchInput { longClick() }
            waitTag("selbar"); compose.onAllNodesWithTag("cell", useUnmergedTree = true)[2].performClick(); shot("photos_select")
            tag("sel_more").performClick(); waitTag("sel_move"); shot("selection_more")
            back(); tag("selbar"); back()
        }
        step("albums") {
            tag("tab_albums").performClick(); waitTag("album_card"); shot("albums")
            compose.onAllNodesWithTag("album_card", useUnmergedTree = true).onFirst().performClick(); waitTag("cell"); shot("album_camera"); back(); waitTag("pin_fav")
            compose.waitForIdle(); Thread.sleep(800)
            tag("pin_fav").performClick(); compose.waitUntil(15000) { has("empty") || has("cell") }; shot("favourites"); back(); waitTag("pin_fav")
            tag("tab_photos").performClick(); waitTag("cell")
        }
        step("viewer") {
            firstCell().performClick(); waitTag("viewer"); Thread.sleep(1200); shot("viewer")
            tag("v_info").performClick(); waitTag("info_sheet"); shot("viewer_info"); back()
        }
        step("filmstrip_flick_undo") {
            tag("viewer").performTouchInput {
                pinch(center + Offset(-320f, -320f), center + Offset(-50f, -50f), center + Offset(320f, 320f), center + Offset(50f, 50f), 500L)
            }
            waitTag("filmstrip"); Thread.sleep(1200); shot("filmstrip")
            val before = count()
            tag("film_card_current").performTouchInput { swipe(center, Offset(centerX, -600f), 180L) }
            waitText("Moved to Recycle Bin"); Thread.sleep(600); shot("filmstrip_flicked_undo_bar")
            val after = count()
            assertTrue("count should drop ($before -> $after)", after == before - 1)
            assertTrue("bin has the item", app.repo.trash.value.isNotEmpty())
            compose.onNodeWithText("Undo", useUnmergedTree = true).performClick()
            compose.waitUntil(15000) { count() == before }
            tag("filmstrip").performTouchInput { swipeLeft() }; Thread.sleep(800)
            tag("film_card_current").performTouchInput { swipe(center, Offset(centerX, centerY + 300f), 300L) } // down = nothing
            Thread.sleep(800)
            assertTrue("flick down must not delete", count() == before)
            shot("filmstrip_after_undo")
            back(); waitTag("viewer"); back(); waitTag("cell")
        }
        step("delete_and_bin") {
            firstCell().performTouchInput { longClick() }; waitTag("selbar")
            tag("sel_delete").performClick(); waitText("Moved to Recycle Bin"); shot("deleted_snackbar")
            tag("tab_albums").performClick(); waitTag("pin_bin"); tag("pin_bin").performClick(); waitTag("cell"); shot("recycle_bin")
            firstCell().performClick(); waitTag("bin_restore"); shot("bin_item_sheet"); tag("bin_restore").performClick(); Thread.sleep(1500)
            back(); tag("tab_photos").performClick(); waitTag("cell")
        }
        step("hidden_album") {
            tag("tab_albums").performClick(); waitTag("pin_hidden"); tag("pin_hidden").performClick(); waitTag("lock_screen"); shot("hidden_create_pin")
            repeat(2) { "1234".forEach { d -> tag("pin_$d").performClick(); Thread.sleep(150) }; Thread.sleep(1200) }
            waitTag("empty"); shot("hidden_empty"); back()
            tag("tab_photos").performClick(); waitTag("cell")
            firstCell().performTouchInput { longClick() }; waitTag("selbar"); tag("sel_more").performClick(); waitTag("sel_hide")
            tag("sel_hide").performClick(); waitText("Hidden album", 20000); Thread.sleep(1500)
            tag("tab_albums").performClick(); tag("pin_hidden").performClick(); waitTag("cell", 20000); Thread.sleep(1500); shot("hidden_album")
            back(); tag("tab_photos").performClick(); waitTag("cell")
        }
        step("editor") {
            firstCell().performClick(); waitTag("viewer"); tag("v_edit").performClick(); waitTag("editor"); Thread.sleep(2000); shot("editor_adjust")
            tag("etab_2").performClick(); Thread.sleep(800); tag("filter_1").performClick(); Thread.sleep(500); shot("editor_filters")
            tag("etab_0").performClick(); Thread.sleep(600); shot("editor_crop")
            back(); waitTag("viewer"); back(); waitTag("cell")
        }
        step("search") {
            tag("search").performClick(); waitTag("search_field"); tag("search_field").performTextInput("Screenshot")
            waitTag("cell"); Thread.sleep(800); shot("search"); back(); back(); waitTag("cell")
        }
        step("settings") {
            tag("more").performClick(); waitTag("menu_settings"); tag("menu_settings").performClick(); waitTag("theme_seg"); shot("settings")
            tag("row_diag").performScrollTo().performClick(); waitTag("export_log"); shot("diagnostics"); back()
            tag("row_about").performScrollTo().performClick(); waitTag("developer"); shot("about"); back()
        }
        step("dark") {
            tag("theme_DARK").performScrollTo().performClick(); Thread.sleep(800); shot("settings_dark"); back(); waitTag("cell"); shot("photos_dark")
            tag("tab_albums").performClick(); waitTag("album_card"); shot("albums_dark")
            tag("pin_bin").performClick(); Thread.sleep(1000); shot("bin_dark"); back()
            tag("tab_photos").performClick(); waitTag("cell")
            firstCell().performClick(); waitTag("viewer"); Thread.sleep(800)
            tag("viewer").performTouchInput {
                pinch(center + Offset(-320f, -320f), center + Offset(-50f, -50f), center + Offset(320f, 320f), center + Offset(50f, 50f), 500L)
            }
            waitTag("filmstrip"); Thread.sleep(1000); shot("filmstrip_dark"); back(); back()
            main { app.prefs.setTheme(ThemeMode.LIGHT) }
        }
        step("landscape") {
            device.setOrientationLeft(); Thread.sleep(2000)
            firstCell().performClick(); waitTag("viewer"); Thread.sleep(1200); shot("viewer_landscape"); back()
            device.setOrientationNatural(); Thread.sleep(1500)
        }
        step("onboarding") {
            scenario.close()
            Seed.shell(inst, "appops set --uid ${ctx.packageName} MANAGE_EXTERNAL_STORAGE deny"); Thread.sleep(500)
            app.prefs.onboarded = false
            scenario = ActivityScenario.launch(MainActivity::class.java)
            waitTag("onboarding"); shot("onboarding")
            tag("not_now").performClick(); waitTag("denied"); shot("permission_denied")
            Seed.grantAllFiles(inst)
        }
        scenario.close()
        assertTrue("Walkthrough failures:\n" + failures.joinToString("\n"), failures.isEmpty())
    }
}
