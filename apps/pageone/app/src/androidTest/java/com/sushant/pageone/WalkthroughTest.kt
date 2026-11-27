package com.sushant.pageone

import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import com.sushant.pageone.ui.theme.ThemeMode
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through EVERY screen like a user (standing rule: every page is tested) with real PDFs on the emulator:
 * setup guide, Home, folder, search, reader (find, selection, pages/contents/bookmarks, reading options, night,
 * read aloud, annotate + save), menu, tools, Recent, Starred, Settings, About, hidden Diagnostics, duplicates,
 * password PDF, form PDF, opening from another app, dark and pure black. Screenshots go to Pictures/AppShots.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    private val inst get() = InstrumentationRegistry.getInstrumentation()
    private val app get() = ApplicationProvider.getApplicationContext<PageOneApp>()
    private var shots = 0
    private var suffix = ""
    private val problems = mutableListOf<String>()

    private fun log(m: String) = Log.i("Walk", m)
    private fun problem(what: String) { problems += what; log("PROBLEM $what"); runCatching { shot("PROBLEM_" + what.replace(Regex("[^A-Za-z0-9_]"), "_").take(40)) } }
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun hasText(t: String) = compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 10_000, soft: Boolean = false): Boolean =
        runCatching { compose.waitUntil(ms) { has(tag) } }.isSuccess.also { if (!it && !soft) problem("missing $tag") }
    private fun waitText(t: String, ms: Long = 10_000, soft: Boolean = false): Boolean =
        runCatching { compose.waitUntil(ms) { hasText(t) } }.isSuccess.also { if (!it && !soft) problem("missing text $t") }
    private fun click(tag: String, soft: Boolean = false) {
        dismissSystemDialogs(); log("click $tag"); if (!waitTag(tag, soft = soft)) return
        runCatching { compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().also { n -> runCatching { n.performScrollTo() } }.performClick() }
            .onFailure { if (!soft) problem("click $tag: ${it.message?.take(60)}") }
        compose.waitForIdle()
    }
    private fun clickText(t: String) {
        log("clickText $t"); if (!waitText(t)) return
        runCatching { compose.onAllNodesWithText(t, substring = true, useUnmergedTree = true).onFirst().also { n -> runCatching { n.performScrollTo() } }.performClick() }
            .onFailure { problem("clickText $t: ${it.message?.take(60)}") }
        compose.waitForIdle()
    }
    private fun type(tag: String, text: String) {
        if (!waitTag(tag)) return
        runCatching { compose.onNodeWithTag(tag).performTextClearance(); compose.onNodeWithTag(tag).performTextInput(text) }.onFailure { problem("type $tag") }
        compose.waitForIdle()
    }
    private fun back() { dismissSystemDialogs(); UiDevice.getInstance(inst).pressBack(); compose.waitForIdle(); Thread.sleep(600) }
    private fun dismissSystemDialogs() {
        runCatching {
            val d = UiDevice.getInstance(inst)
            val wait = d.findObject(By.text("Wait"))
            if (wait != null && d.findObject(By.textContains("isn't responding")) != null) { wait.click(); Thread.sleep(500) }
        }
    }
    private fun shell(cmd: String) = runCatching { inst.uiAutomation.executeShellCommand(cmd).close() }

    private fun shot(name: String) {
        Thread.sleep(900)
        compose.waitForIdle()
        val bmp = inst.uiAutomation.takeScreenshot() ?: return
        shots++
        val file = "%02d_%s%s.png".format(shots, name, suffix)
        val cv = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file)
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppShots")
        }
        val r = app.contentResolver
        r.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { uri -> r.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        log("shot $file")
    }

    /** Put the test PDFs on the phone the way they arrive from WhatsApp, Telegram and downloads. */
    private fun seed(): Map<String, Uri> {
        val assets = inst.context.assets
        val plan = listOf(
            "Lucent General English.pdf" to "Lucent Books",
            "STET 2025 Paper 2 English.pdf" to "Telegram",
            "STET 2025 Paper 2 English.pdf" to "WhatsApp Documents",
            "Admit Card - Sushant Kumar.pdf" to "WhatsApp Documents",
            "SBI Statement Oct 2026.pdf" to "",
        )
        val out = HashMap<String, Uri>()
        val r = app.contentResolver
        for ((name, folder) in plan) {
            val rel = Environment.DIRECTORY_DOWNLOADS + (if (folder.isEmpty()) "/" else "/$folder/")
            val existing = r.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.MediaColumns._ID),
                "${MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${MediaStore.MediaColumns.RELATIVE_PATH}=?", arrayOf(name, rel), null)?.use { c ->
                if (c.moveToFirst()) Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, c.getLong(0).toString()) else null
            }
            if (existing != null) { out["$folder/$name"] = existing; continue }
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name); put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
                put(MediaStore.MediaColumns.RELATIVE_PATH, rel); put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val uri = r.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: continue
            r.openOutputStream(uri)?.use { o -> assets.open("pdfs/$name").use { it.copyTo(o) } }
            r.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
            out["$folder/$name"] = uri
        }
        Thread.sleep(1500)
        log("seeded ${out.size} PDFs")
        return out
    }

    private fun openReaderFor(name: String) {
        // from Search: the quickest way to any PDF
        clickTab("home"); click("search_pill"); type("search_field", name.take(12))
        waitTag("pdf_row"); compose.onAllNodesWithTag("pdf_row", useUnmergedTree = true).onFirst().performClick(); compose.waitForIdle()
    }

    /** Show (true) or hide (false) the reader's toolbars by tapping the page. */
    private fun chrome(visible: Boolean) {
        repeat(2) {
            if (has("reader_find") == visible) return
            runCatching { compose.onNodeWithTag(if (has("pages")) "pages" else "pager").performTouchInput { click(Offset(width * 0.5f, height * 0.6f)) } }
            if (runCatching { compose.waitUntil(3_000) { has("reader_find") == visible } }.isSuccess) return
        }
        problem("reader toolbars did not ${if (visible) "show" else "hide"}")
    }

    private val pageMatcher = SemanticsMatcher("a page") { n ->
        (n.config.getOrElseNullable(androidx.compose.ui.semantics.SemanticsProperties.TestTag) { null })?.matches(Regex("page_\\d+")) == true
    }
    private fun page() = compose.onAllNodes(pageMatcher, useUnmergedTree = true).onFirst()
    private fun waitPage(ms: Long = 10_000, soft: Boolean = false): Boolean =
        runCatching { compose.waitUntil(ms) { compose.onAllNodes(pageMatcher, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() } }.isSuccess
            .also { if (!it && !soft) problem("no page shown") }

    private fun clickTab(t: String) { if (has("tab_$t")) click("tab_$t") else { repeat(4) { if (!has("tab_$t")) back() }; click("tab_$t", soft = true) } }

    @Test
    fun walkEveryScreen() {
        shell("appops set ${app.packageName} MANAGE_EXTERNAL_STORAGE allow")
        val seeded = seed()
        app.prefs.update { it.copy(setupDone = false, themeMode = ThemeMode.LIGHT, pureBlack = false, wallpaperColors = false) }
        val scenario = ActivityScenario.launch(MainActivity::class.java)

        // ---------- first launch: animation + setup guide
        waitTag("setup")
        Thread.sleep(1500); shot("setup_1_welcome")
        click("setup_next"); shot("setup_2_files")
        click("setup_next"); shot("setup_3_look")
        click("setup_next"); shot("setup_4_done")
        click("setup_next")

        // ---------- Home
        if (!waitTag("folder_row", 30_000)) problem("library scan found no folders")
        shot("home")
        compose.onNodeWithTag("home_list").performTouchInput { swipeUp() }; shot("home_scrolled")
        compose.onNodeWithTag("home_list").performTouchInput { swipeDown() }
        click("folder_sort_1"); shot("home_sort_date"); click("folder_sort_0")

        // ---------- + tools menu
        click("tools_fab"); shot("home_tools_menu")
        click("tool_0"); waitTag("merge_go"); shot("tool_merge"); back()
        click("tools_fab"); click("tool_1"); waitTag("split_pick"); shot("tool_split"); back()
        click("tools_fab"); click("tool_2"); waitTag("compress_pick"); shot("tool_compress"); back()

        // ---------- folder
        clickText("Lucent Books"); waitTag("folder_list"); shot("folder")
        compose.onAllNodesWithTag("pdf_row", useUnmergedTree = true).onFirst().performTouchInput { longClick() }
        waitTag("selection_bar"); shot("folder_selected"); back()
        click("pdf_sort_0"); shot("folder_sort_name")

        // ---------- reader
        compose.onAllNodesWithTag("pdf_row", useUnmergedTree = true).onFirst().performClick()
        if (!waitPage(15_000)) problem("reader did not show page 1")
        Thread.sleep(1500); shot("reader")
        chrome(false); shot("reader_fullscreen"); chrome(true)
        // pinch zoom
        compose.onNodeWithTag("pages").performTouchInput { pinch(center - Offset(80f, 0f), center - Offset(240f, 0f), center + Offset(80f, 0f), center + Offset(240f, 0f)) }
        Thread.sleep(800); shot("reader_zoomed")
        compose.onNodeWithTag("pages").performTouchInput { doubleClick(center) }; Thread.sleep(800)
        chrome(true)
        // find
        click("reader_find"); type("find_field", "tense")
        compose.onNodeWithTag("find_field").performImeAction()
        if (!waitTag("find_count", 15_000)) problem("find showed no count")
        shot("reader_find")
        click("find_next"); shot("reader_find_next")
        click("find_all"); waitTag("results_list"); shot("reader_find_results"); back()
        click("find_close")
        // selection (long press on text)
        page().performTouchInput { longClick(Offset(width * 0.3f, height * 0.12f)) }
        if (waitTag("selection_popup", 5_000, soft = true)) { shot("reader_selection"); click("sel_highlight") } else log("no text selection on this API")
        // pages / contents / bookmarks
        click("tb_bookmark"); Thread.sleep(400); click("tb_pages"); waitTag("thumb_grid"); shot("sheet_pages")
        click("sheet_tab_1"); waitTag("toc", soft = true); shot("sheet_contents")
        click("sheet_tab_2"); shot("sheet_bookmarks"); back()
        // reading options
        click("tb_options"); waitTag("opt_reflow"); shot("sheet_options")
        click("opt_reflow"); back(); waitTag("reflow"); shot("reader_reflow")
        click("tb_options"); click("opt_reflow"); click("opt_crop"); back(); shot("reader_crop")
        click("tb_options"); click("opt_crop"); click("scroll_mode_1"); back(); waitTag("pager"); shot("reader_paged")
        click("tb_options"); click("scroll_mode_0"); back()
        // night
        click("tb_night"); shot("reader_night"); click("tb_night")
        // read aloud
        click("tb_speak")
        if (waitTag("read_aloud", 8_000, soft = true)) { shot("reader_read_aloud"); click("speak_close") } else log("no TTS engine on this emulator")
        // annotate + save as copy
        click("pen_fab"); waitTag("annotate_bar"); shot("annotate")
        click("tool_pen")
        page().performTouchInput { swipe(Offset(width * 0.2f, height * 0.5f), Offset(width * 0.8f, height * 0.55f), 400) }
        click("tool_highlight")
        page().performTouchInput { swipe(Offset(width * 0.12f, height * 0.2f), Offset(width * 0.7f, height * 0.2f), 400) }
        shot("annotate_drawn")
        click("annotate_done"); waitTag("save_copy"); shot("save_dialog")
        click("save_copy"); waitText("Saved as", 15_000, soft = true); Thread.sleep(1500); shot("after_save")
        // menu
        click("reader_menu"); waitTag("reader_menu_list"); shot("reader_menu")
        click("m_details"); shot("details"); back()
        click("reader_menu"); click("m_star")
        back()

        // ---------- Recent / Starred
        clickTab("recent"); waitTag("recent_list"); shot("recent")
        clickTab("starred"); waitTag("starred_list"); shot("starred")
        clickTab("home")

        // ---------- search
        click("search_pill"); type("search_field", "stet"); waitTag("search_results"); shot("search")
        click("search_filter_2"); shot("search_this_week"); back()

        // ---------- password PDF
        openReaderFor("SBI Statement")
        if (waitTag("password_field", 15_000)) {
            shot("password"); type("password_field", "0000"); click("password_open"); Thread.sleep(1500)
            shot("password_wrong")
            type("password_field", "1234"); click("password_open")
            if (!waitPage(15_000)) problem("password PDF did not open") else shot("password_opened")
        }
        back()

        // ---------- form PDF
        openReaderFor("Admit Card")
        if (waitPage(15_000)) {
            if (waitTag("field_name", 10_000, soft = true)) {
                compose.onAllNodesWithTag("field_name", useUnmergedTree = true).onFirst().performClick()
                compose.onAllNodesWithTag("field_name", useUnmergedTree = true).onFirst().performTextInput("Sushant Kumar")
                shot("form_filled")
            } else problem("form fields not shown")
        }
        back()                                   // closes the keyboard
        if (!has("save_discard")) back()         // asks to save the form entries
        if (waitTag("save_discard", 5_000, soft = true)) { shot("form_unsaved_prompt"); click("save_discard") } else problem("no unsaved-changes prompt")
        if (!has("tab_home")) back()

        // ---------- settings, about, diagnostics, duplicates
        clickTab("home"); click("open_settings"); waitTag("settings_list"); shot("settings")
        compose.onNodeWithTag("settings_list").performTouchInput { swipeUp() }; shot("settings_scrolled")
        click("set_duplicates"); shot("duplicates"); back()
        click("set_about"); waitTag("about_version"); shot("about")
        repeat(5) { compose.onNodeWithTag("about_version").performClick() }
        waitTag("diagnostics"); shot("diagnostics"); back(); back()
        click("set_theme"); shot("theme_dialog"); back()
        back()

        // ---------- open a PDF from another app (VIEW intent)
        seeded["Telegram/STET 2025 Paper 2 English.pdf"]?.let { uri ->
            app.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                .setClass(app, MainActivity::class.java))
            if (!waitPage(15_000)) problem("PDF from another app did not open") else shot("opened_from_other_app")
            back()
        }

        // ---------- dark, then pure black
        app.prefs.update { it.copy(themeMode = ThemeMode.DARK) }; suffix = "_dark"
        clickTab("home"); shot("home"); clickTab("recent"); shot("recent"); clickTab("home")
        click("open_settings"); shot("settings"); back()
        openReaderFor("Lucent"); waitPage(); shot("reader"); back()
        app.prefs.update { it.copy(pureBlack = true) }; suffix = "_black"
        clickTab("home"); shot("home"); click("open_settings"); shot("settings"); back()
        app.prefs.update { it.copy(themeMode = ThemeMode.LIGHT, pureBlack = false) }; suffix = ""

        scenario.close()
        log("walkthrough done: $shots screenshots, ${problems.size} problems")
        assertTrue("Problems: $problems", problems.isEmpty())
    }
}
