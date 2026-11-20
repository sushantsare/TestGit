package com.sushant.inknote

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.sushant.inknote.data.ThemeMode
import com.sushant.inknote.work.Indexer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Clicks through every screen like a user and saves screenshots (light, dark, landscape / two-pane). */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val perms: GrantPermissionRule = if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.RECORD_AUDIO)
        else GrantPermissionRule.grant(Manifest.permission.RECORD_AUDIO)

    private val inst = InstrumentationRegistry.getInstrumentation()
    private val ctx = inst.targetContext
    private val app get() = ctx.applicationContext as InkApp
    private val device = UiDevice.getInstance(inst)
    private val failures = ArrayList<String>()

    private fun shot(name: String) {
        inst.waitForIdleSync(); Thread.sleep(900)
        val bmp: Bitmap = inst.uiAutomation.takeScreenshot() ?: return
        val v = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png"); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AppShots")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, v) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun step(name: String, block: () -> Unit) {
        try { block() } catch (t: Throwable) { failures += "$name: ${t.message?.take(300)}"; Log.e("InkWalk", "step $name failed", t); runCatching { shot("FAIL_$name") } }
    }

    private fun waitTag(tag: String, ms: Long = 8000) = compose.waitUntil(ms) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }
    private fun waitText(t: String, ms: Long = 8000) = compose.waitUntil(ms) { compose.onAllNodesWithText(t, substring = true).fetchSemanticsNodes().isNotEmpty() }
    private fun tag(t: String): SemanticsNodeInteraction = compose.onNodeWithTag(t, useUnmergedTree = true)
    private fun back() { device.pressBack(); compose.waitForIdle(); Thread.sleep(400) }

    private fun drawOn(pageTag: String, seed: Int) {
        tag(pageTag).performTouchInput {
            val w = width.toFloat(); val h = height.toFloat()
            for (line in 0 until 4) {
                val y = h * (0.55f + line * 0.06f); var x = w * 0.08f
                while (x < w * 0.8f) {
                    down(Offset(x, y))
                    var px = x; val end = x + w * (0.08f + (seed + line) % 3 * 0.03f)
                    while (px < end) { px += 6f; moveTo(Offset(px, y - 18f * kotlin.math.abs(kotlin.math.sin(px / 9f)))) }
                    up(); x = end + w * 0.03f
                }
            }
        }
    }

    @Test fun walkThroughEveryScreen() {
        // ---------- first launch: empty ----------
        var sc = ActivityScenario.launch(MainActivity::class.java)
        step("empty") { waitTag("empty_home"); shot("02_home_empty") }
        sc.close()

        runBlocking { Seed.all(ctx, app.repo); Indexer.run(app) }
        sc = ActivityScenario.launch(MainActivity::class.java)

        step("home") { waitText("Hamlet"); shot("01_home") }
        step("list_view") { tag("view_toggle").performClick(); waitTag("note_list"); shot("01b_home_list"); tag("view_toggle").performClick(); waitTag("note_grid") }
        step("drawer") { tag("open_drawer").performClick(); waitTag("nav"); shot("03_side_menu"); back() }
        step("new_note") {
            tag("fab_new").performClick(); waitTag("new_sheet"); tag("dir_horizontal").performClick(); shot("04_new_note_horizontal")
            tag("create_note").performClick(); waitTag("editor"); waitTag("page_0")
            drawOn("page_0", 1); shot("05a_new_horizontal_note_written")
        }
        step("new_note_back") { tag("editor_back").performClick(); waitTag("home") }

        step("editor") { compose.onNodeWithText("Hamlet – Act III").performClick(); waitTag("page_0"); Thread.sleep(800); shot("05_editor_handwriting") }
        step("pen_sheet") { tag("tool_Pen").performClick(); waitTag("pen_sheet"); shot("06_pen_settings"); back() }
        step("draw") { drawOn("page_0", 2); assertTrue(compose.onNodeWithTag("undo").fetchSemanticsNode().config.contains(androidx.compose.ui.semantics.SemanticsProperties.Disabled).not()) }
        step("typing") {
            tag("tool_Type text").performClick()
            tag("page_0").performTouchInput { click(Offset(width * 0.1f, height * 0.93f)) }
            device.wait(Until.hasObject(By.clazz("android.widget.EditText")), 5000)
            device.findObject(By.clazz("android.widget.EditText"))?.text = "Exam tip: revise soliloquies"
            Thread.sleep(500); shot("07_editor_typing")
            tag("text_done").performClick()
        }
        step("lasso") {
            tag("tool_Select").performClick()
            tag("page_0").performTouchInput {
                val cx = width * 0.4f; val cy = height * 0.6f; val r = width * 0.3f
                down(Offset(cx + r, cy))
                for (i in 1..40) { val a = i / 40.0 * 2 * Math.PI; moveTo(Offset(cx + r * kotlin.math.cos(a).toFloat(), cy + r * 0.6f * kotlin.math.sin(a).toFloat())) }
                up()
            }
            waitTag("selection_bar"); shot("08_select_writing")
            tag("sel_Done").performClick()
        }
        step("insert") { tag("tool_Insert").performClick(); waitTag("insert_sheet"); shot("21_insert_menu"); back() }
        step("menu") { tag("tool_Pen").performClick(); tag("editor_more").performClick(); waitTag("m_pages"); shot("10_note_menu"); back() }
        step("export") { tag("editor_more").performClick(); tag("m_export").performClick(); waitTag("export_sheet"); shot("15_export"); back() }
        step("tags") { tag("editor_more").performClick(); tag("m_tags").performClick(); waitTag("tags_sheet"); shot("15b_folder_tags"); back() }
        step("pages") {
            tag("editor_more").performClick(); tag("m_pages").performClick(); waitTag("pages_screen"); Thread.sleep(800); shot("11_pages_direction")
            tag("pthumb_1").performClick(); tag("pg_turn").performClick(); Thread.sleep(1200); shot("11b_page_turned")
            back()
        }
        step("editor_back") { waitTag("editor"); tag("editor_back").performClick(); waitTag("home") }

        step("recording_note") { compose.onNodeWithText("Romantic Poets lecture").performClick(); waitTag("player"); tag("play").performClick(); Thread.sleep(2500); shot("22_playback_sync"); tag("play").performClick(); tag("editor_back").performClick(); waitTag("home") }
        step("pdf") { compose.onNodeWithText("STET Paper 2 – 2024").performClick(); waitTag("page_0"); Thread.sleep(1500); shot("12_pdf_markup"); tag("editor_back").performClick(); waitTag("home") }
        step("typed_note") { compose.onNodeWithText("Hostel shopping").performClick(); waitTag("editor"); Thread.sleep(800); shot("09_typed_note"); back(); runCatching { tag("editor_back").performClick() }; waitTag("home") }

        step("search") {
            tag("search_pill").performClick(); waitTag("search_field")
            tag("search_field").performTextInput("Keats"); compose.waitUntil(8000) { compose.onAllNodesWithTag("result").fetchSemanticsNodes().isNotEmpty() }
            shot("13_search"); back(); back()
        }
        step("locked") {
            waitTag("home"); tag("note_grid").performScrollToNode(hasText("Exam form details")); compose.onNodeWithText("Exam form details").performClick(); waitTag("lock_screen"); device.pressBack(); Thread.sleep(600)
            if (compose.onAllNodesWithTag("lock_screen").fetchSemanticsNodes().isEmpty()) compose.onNodeWithText("Exam form details").performClick()
            waitTag("lock_screen"); shot("14_locked_note")
            listOf("pin_1", "pin_2", "pin_3", "pin_4", "pin_ok").forEach { tag(it).performClick() }
            waitTag("editor"); shot("14b_unlocked"); tag("editor_back").performClick(); waitTag("home")
        }
        step("select_mode") {
            tag("note_grid").performScrollToNode(hasText("Class timetable")); compose.onNodeWithText("Class timetable").performTouchInput { longClick() }
            waitTag("sel_delete"); shot("16_select_notes"); tag("sel_delete").performClick(); Thread.sleep(600)
        }
        step("trash") {
            tag("open_drawer").performClick(); waitTag("nav"); tag("nav_trash").performClick(); waitTag("trash_screen"); shot("17_trash")
            compose.onNodeWithTag("restore_Class timetable").performClick(); Thread.sleep(500)
        }
        step("settings") { tag("open_drawer").performClick(); waitTag("nav"); tag("nav_settings").performClick(); waitTag("settings_screen"); shot("18_settings") }
        step("about") { tag("row_about").performScrollTo().performClick(); waitTag("about_screen"); shot("20_about"); back() }

        // ---------- dark theme ----------
        step("dark") {
            app.settings.update { it.copy(theme = ThemeMode.DARK) }; Thread.sleep(1500)
            device.pressBack(); compose.waitForIdle(); waitTag("home"); Thread.sleep(800); shot("dark_01_home")
            tag("open_drawer").performClick(); waitTag("nav"); shot("dark_03_side_menu"); back()
            tag("fab_new").performClick(); waitTag("new_sheet"); shot("dark_04_new_note"); back()
            compose.onNodeWithText("Hamlet – Act III").performClick(); waitTag("page_0"); Thread.sleep(1000); shot("dark_05_editor")
            tag("tool_Pen").performClick(); waitTag("pen_sheet"); shot("dark_06_pen"); back()
            tag("editor_back").performClick(); waitTag("home")
            compose.onNodeWithText("STET Paper 2 – 2024").performClick(); waitTag("page_0"); Thread.sleep(1500); shot("dark_12_pdf"); tag("editor_back").performClick(); waitTag("home")
            tag("search_pill").performClick(); waitTag("search_field"); tag("search_field").performTextInput("Maggi"); Thread.sleep(1500); shot("dark_13_search"); back(); back()
            tag("open_drawer").performClick(); waitTag("nav"); tag("nav_settings").performClick(); waitTag("settings_screen"); shot("dark_18_settings")
            device.pressBack(); waitTag("home")
        }
        // ---------- landscape: expanded two-pane layout ----------
        step("landscape") {
            app.settings.update { it.copy(theme = ThemeMode.LIGHT) }; Thread.sleep(800)
            device.setOrientationLeft(); Thread.sleep(2500); compose.waitForIdle()
            waitTag("home"); shot("land_01_home")
            compose.onNodeWithText("Hamlet – Act III").performClick(); waitTag("page_0"); Thread.sleep(1200); shot("land_02_editor_list")
            tag("editor_more").performClick(); tag("m_beside").performClick(); Thread.sleep(800)
            compose.onAllNodes(hasText("Romantic Poets lecture")).onFirst().performClick(); waitTag("editor2"); Thread.sleep(1200); shot("land_03_two_notes")
            device.setOrientationNatural(); Thread.sleep(2000); device.unfreezeRotation()
            compose.waitForIdle(); shot("land_04_back_to_portrait")
        }
        sc.close()
        Log.i("InkWalk", "failures=${failures.size}: $failures")
        assertTrue("Walkthrough steps failed: $failures", failures.isEmpty())
    }

}
