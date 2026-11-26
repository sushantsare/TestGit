@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang

import android.Manifest
import android.content.ContentValues
import android.content.Intent
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import com.sushant.tarang.data.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Walks through EVERY screen like a user (standing rule: every page is tested) with real songs on the emulator,
 * saving light and dark screenshots to Pictures/AppShots. Soft checks: problems are collected with a screenshot
 * and the test fails at the end if there were any.
 */
@RunWith(AndroidJUnit4::class)
class WalkthroughTest {
    @get:Rule val compose = createEmptyComposeRule()
    @get:Rule val perms: GrantPermissionRule =
        if (Build.VERSION.SDK_INT >= 33) GrantPermissionRule.grant(Manifest.permission.READ_MEDIA_AUDIO)
        else GrantPermissionRule.grant(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    private val app get() = ApplicationProvider.getApplicationContext<TarangApp>()
    private lateinit var scenario: ActivityScenario<MainActivity>
    private var shots = 0
    private var suffix = ""
    private val problems = mutableListOf<String>()

    private fun log(m: String) = Log.i("Walk", m)
    private fun problem(what: String) { problems += what; log("PROBLEM $what"); runCatching { shot("PROBLEM_" + what.replace(Regex("[^A-Za-z0-9_]"), "_").take(40)) } }
    private fun has(tag: String) = compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
    private fun waitTag(tag: String, ms: Long = 8_000, soft: Boolean = false): Boolean =
        runCatching { compose.waitUntil(ms) { has(tag) } }.isSuccess.also { if (!it && !soft) problem("missing $tag") }
    private fun click(tag: String) {
        dismissSystemDialogs(); log("click $tag"); if (!waitTag(tag)) return
        runCatching { compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().also { n -> runCatching { n.performScrollTo() } }.performClick() }
            .onFailure { problem("click $tag: ${it.message?.take(60)}") }
        compose.waitForIdle()
    }
    private fun clickTo(tag: String, expect: String) { click(tag); if (!waitTag(expect, 5_000, soft = true)) { log("retry $tag"); click(tag); waitTag(expect) } }
    private fun type(tag: String, text: String) { if (!waitTag(tag)) return; compose.onNodeWithTag(tag).performTextClearance(); compose.onNodeWithTag(tag).performTextInput(text); compose.waitForIdle() }
    private fun dismissSystemDialogs() {
        runCatching {
            val d = androidx.test.uiautomator.UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
            val wait = d.findObject(androidx.test.uiautomator.By.text("Wait"))
            if (wait != null && d.findObject(androidx.test.uiautomator.By.textContains("isn't responding")) != null) { wait.click(); Thread.sleep(500) }
        }
    }
    private fun back() {
        dismissSystemDialogs()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
        Thread.sleep(600); compose.waitForIdle()
    }
    /** Go to a bottom tab like a user: press back until the tab bar shows (it hides on inner screens), then tap. */
    private fun tab(r: String, expect: String) {
        repeat(4) { if (has("tab_$r")) return@repeat; back() }
        clickTo("tab_$r", expect)
    }
    private fun go(route: String, waitFor: String? = null) {
        scenario.onActivity { a -> a.startActivity(Intent(a, MainActivity::class.java).putExtra(MainActivity.EXTRA_ROUTE, route).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)) }
        compose.waitForIdle(); waitFor?.let { waitTag(it) }; log("opened $route")
    }
    private fun shot(name: String) {
        compose.waitForIdle(); Thread.sleep(900); dismissSystemDialogs()
        val bmp: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot() ?: return
        val file = "%02d_%s%s.png".format(++shots, name, suffix)
        val cv = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, file); put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            if (Build.VERSION.SDK_INT >= 29) put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/AppShots")
        }
        val cr = app.contentResolver
        cr.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)?.let { uri -> cr.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) } }
        log("shot $file")
    }
    private fun song(file: String): Song? = app.repo.library.value.songs.firstOrNull { it.fileName.equals(file, true) }.also { if (it == null) problem("song $file not in library") }

    @Test fun walkthroughEveryScreen() {
        try { walk() } catch (t: Throwable) {
            runCatching { shot("FAILED_here") }
            runCatching { compose.onRoot(useUnmergedTree = true).printToLog("WalkTree") }
            throw t
        }
        log("problems=${problems.size}")
        org.junit.Assert.assertTrue("Walkthrough problems: " + problems.joinToString("; "), problems.isEmpty())
    }

    private fun vm(): com.sushant.tarang.ui.MainVm { var v: com.sushant.tarang.ui.MainVm? = null; scenario.onActivity { v = it.vm }; return v!! }
    private fun queue(): List<Long> = vm().player.ui.value.queueIds
    private fun current(): Long? = vm().player.ui.value.currentId
    private fun title(): String = runCatching {
        compose.onAllNodesWithTag("player_title", useUnmergedTree = true).onFirst().fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
    }.getOrDefault("")
    private fun bounds(tag: String) = runCatching { compose.onAllNodesWithTag(tag, useUnmergedTree = true).onFirst().fetchSemanticsNode().boundsInRoot }.getOrNull()
    private fun isFull() = has("full_player") && !has("mini_player")
    private fun isMini() = has("mini_player") && !has("full_player")
    private fun settle() { Thread.sleep(1300); compose.waitForIdle() }
    private val playingNow = SemanticsMatcher.expectValue(androidx.compose.ui.semantics.SemanticsProperties.StateDescription, "Playing now")
    /** The song playing now must be marked as playing in this list, wherever it was started from. */
    private fun checkMarked(listTag: String, where: String) {
        val id = current() ?: return
        runCatching { compose.onNodeWithTag(listTag, useUnmergedTree = true).performScrollToNode(hasTestTag("song_$id")) }
        compose.waitForIdle(); Thread.sleep(300)
        val ok = compose.onAllNodes(hasTestTag("song_$id") and playingNow, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        if (!ok) problem("playing song not marked in $where") else shot("marked_in_${where.replace(' ', '_')}")
        val others = compose.onAllNodes(playingNow, useUnmergedTree = true).fetchSemanticsNodes().size
        if (others > 1) problem("more than one song marked as playing in $where")
    }
    /** Big title and the top-bar buttons share one row. */
    private fun checkInlineTitle(where: String, buttonTag: String) {
        val t = bounds("page_title"); val b = bounds(buttonTag)
        if (t == null || b == null) { problem("$where: title or $buttonTag missing"); return }
        val c = (t.top + t.bottom) / 2f
        if (c < b.top || c > b.bottom) problem("$where: big title is not on the same row as the buttons")
        if (t.right > b.left + 1f) problem("$where: title overlaps the buttons")
    }

    /** While the player moves between full and mini, the cover must stay inside the player (never cut off mid-screen). */
    private fun checkCoverInside(at: String) {
        val c = bounds("player_cover"); val sheet = bounds("full_player") ?: bounds("mini_player")
        if (c == null || sheet == null) return
        val slack = 2f
        if (c.left < sheet.left - slack || c.right > sheet.right + slack || c.top < sheet.top - slack || c.bottom > sheet.bottom + slack)
            problem("cover outside the player at $at ($c vs $sheet)")
    }

    /** Pull the full player down part of the way, screenshot at 25/50/75 %, and let go. */
    private fun pullDownInSteps(startTag: String, name: String) {
        val node = compose.onAllNodesWithTag(startTag, useUnmergedTree = true).onFirst()
        val h = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.heightPixels.toFloat()
        node.performTouchInput { down(center) }
        listOf(25, 50, 75).forEach { pct ->
            node.performTouchInput { repeat(8) { moveBy(Offset(0f, h * 0.19f / 8f), delayMillis = 16) } }
            compose.waitForIdle(); checkCoverInside("$pct%"); shot("${name}_$pct")
        }
        node.performTouchInput { up() }
        settle()
    }

    private fun setupGuide() {
        app.prefs.setupDone = false
        scenario = ActivityScenario.launch(MainActivity::class.java)
        waitTag("setup"); Thread.sleep(1200); shot("setup_welcome")
        var guard = 0
        while (has("setup") && guard++ < 8) {
            click("setup_next"); Thread.sleep(900)
            if (has("setup")) shot("setup_step_$guard")
        }
        if (has("setup")) problem("setup guide did not finish")
        if (!app.prefs.setupDone) problem("setup guide not remembered")
        runCatching { scenario.close() }
    }

    private fun walk() {
        MediaSeed.run(app)
        app.prefs.update { Settings(themeMode = ThemeMode.LIGHT) }
        // ---- first launch: animation + step-by-step setup guide
        setupGuide()
        app.prefs.setupDone = true
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // ---- Home (nothing played yet, so no mini player)
        waitTag("home")
        runCatching { compose.waitUntil(20_000) { app.repo.library.value.songs.size >= 10 } }.onFailure { problem("library did not load the test songs") }
        if (app.repo.library.value.songs.any { it.folderName == "WhatsApp Audio" }) problem("WhatsApp audio should be hidden by default")
        if (has("tab_stats")) problem("Stats tab should be gone from the dock")
        waitTag("greeting_icon")
        if (has("continue_card")) problem("the Continue listening banner should be gone")
        if (!has("open_search") || !has("open_settings")) problem("Home: Search / Settings buttons missing")
        shot("home_fresh")

        // ---- Folders → folder → play all
        tab("folders", "folders"); shot("folders")
        checkInlineTitle("Folders", "open_search")
        click("chip_Most songs"); shot("folders_most_songs"); click("chip_Name")
        clickTo("folder_Bollywood", "song_list"); Thread.sleep(700); shot("folder_detail")
        waitTag("grow_cover", 3000, soft = true)
        click("play_all")
        if (!waitTag("mini_player", 10_000)) return
        Thread.sleep(2500); shot("folder_playing_mini")
        checkMarked("song_rows", "folder")
        song("Kesariya.mp3")?.let { s -> click("more_${s.id}"); waitTag("sheet"); shot("song_menu"); back() }

        // ---- full player
        clickTo("mini_player", "full_player"); Thread.sleep(1200); shot("player_full")
        if (!has("now_playing")) problem("top bar should say Now playing")
        if (!has("cover_carousel")) problem("cover carousel missing in the full player")
        fun playing() = compose.onAllNodesWithContentDescription("Pause", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val wasPlaying = playing()
        click("player_menu"); if (!waitTag("menu_speed", 4000)) problem("⋮ did not open the menu with Speed"); shot("player_menu")
        if (!has("menu_sound") || !has("menu_info") || !has("menu_edit")) problem("⋮ menu is missing Sound / Info / Edit details")
        back(); Thread.sleep(500)
        if (wasPlaying && !playing()) problem("⋮ paused the music")
        if (has("panel_header")) problem("ghost bar visible while Up next / Lyrics is closed")
        if (waitTag("peek_line", 4000)) shot("player_strip_up_next")
        waitTag("player_toolbar"); if (!has("extra_lyrics")) problem("Lyrics button missing in the toolbar")

        // ---- ⏮ ⏯ ⏭: three equal parts; holding one widens it and squeezes its neighbours
        val w0 = listOf("btn_prev", "btn_play", "btn_next").map { bounds(it)?.width ?: 0f }
        if (w0.any { it <= 0f }) problem("player buttons missing")
        else if (w0.max() - w0.min() > 3f) problem("⏮ ⏯ ⏭ should be equal widths at rest ($w0)")
        if (!has("shuffle") || !has("repeat")) problem("shuffle / repeat should stay at the ends of the row")
        compose.onNodeWithTag("btn_next", useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        val w1 = listOf("btn_prev", "btn_play", "btn_next").map { bounds(it)?.width ?: 0f }
        shot("buttons_pressing_next")
        if (!(w1[2] > w0[2] * 1.04f && w1[0] < w0[0] * 0.95f && w1[1] < w0[1] * 0.95f)) problem("holding Next did not widen it and squeeze the others ($w0 → $w1)")
        val outer0 = (bounds("btn_next")?.right ?: 0f) - (bounds("btn_prev")?.left ?: 0f)
        compose.onNodeWithTag("btn_next", useUnmergedTree = true).performTouchInput { up() }
        settle()
        compose.onNodeWithTag("btn_play", useUnmergedTree = true).performTouchInput { down(center) }
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        val w2 = listOf("btn_prev", "btn_play", "btn_next").map { bounds(it)?.width ?: 0f }
        val outer1 = (bounds("btn_next")?.right ?: 0f) - (bounds("btn_prev")?.left ?: 0f)
        shot("buttons_pressing_play")
        compose.onNodeWithTag("btn_play", useUnmergedTree = true).performTouchInput { up() }
        if (!(w2[1] > w0[1] * 1.04f && w2[0] < w0[0] * 0.95f)) problem("holding Play did not widen it ($w0 → $w2)")
        if (kotlin.math.abs(outer1 - outer0) > 3f) problem("the button group's outer edges moved while pressing")
        settle()
        if (!playing()) { click("btn_play"); settle() }

        // ---- cover carousel: every swipe changes the song exactly once
        val t0 = title(); val c0 = current()
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput { swipeLeft(durationMillis = 220) }
        settle(); val t1 = title(); shot("carousel_after_swipe_left")
        if (t1 == t0) problem("swipe left did not change the song ($t0)")
        val i1 = queue().indexOf(current())
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput { swipeRight(durationMillis = 220) }
        settle()
        if (current() != c0) problem("swipe right did not go back to the previous song")
        // slow drag let go halfway: snaps back, song stays
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput {
            down(center); repeat(6) { moveBy(Offset(-12f, 0f), delayMillis = 30) }; Thread.sleep(200); up()
        }
        settle(); if (current() != c0) problem("a tiny sideways drag changed the song")
        // mid-swipe picture (old cover squeezed out, new one opening)
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput { down(center); repeat(10) { moveBy(Offset(-24f, 0f), delayMillis = 16) } }
        shot("carousel_mid_swipe")
        compose.onAllNodesWithTag("cover_carousel", useUnmergedTree = true).onFirst().performTouchInput { repeat(10) { moveBy(Offset(-24f, 0f), delayMillis = 16) }; up() }
        settle()
        val i2 = queue().indexOf(current())
        if (i2 != i1) problem("a long slow swipe should move exactly one song (from ${i1 - 1} to $i2)")
        // two quick swipes = two songs
        repeat(2) { compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput { swipeLeft(durationMillis = 140) }; Thread.sleep(700) }
        settle()
        val i3 = queue().indexOf(current())
        if (i3 != i2 + 2) problem("two quick swipes should move two songs ($i2 → $i3)")
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput { swipeRight(durationMillis = 160) }; settle()
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput { swipeRight(durationMillis = 160) }; settle()
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput { swipeRight(durationMillis = 160) }; settle()

        // ---- swipe down to the mini player: slow, in steps (screenshots at 25/50/75 %), fast, short, from different spots
        pullDownInSteps("player_cover", "pull_down_cover")
        if (!isMini()) { problem("slow pull down from the cover did not reach the mini player"); click("mini_player") } else shot("after_pull_down")
        clickTo("mini_player", "full_player"); settle()
        compose.onAllNodesWithTag("player_title", useUnmergedTree = true).onFirst().performTouchInput { swipeDown(startY = centerY, endY = centerY + 1400f, durationMillis = 120) }
        settle(); if (!isMini()) { problem("fast flick down from the title did not close the player"); click("collapse") }
        clickTo("mini_player", "full_player"); settle()
        compose.onAllNodesWithTag("now_playing", useUnmergedTree = true).onFirst().performTouchInput {
            down(center); repeat(10) { moveBy(Offset(0f, 14f), delayMillis = 40) }; up()
        }
        settle(); if (!isMini()) { problem("short slow pull down from the top bar stopped halfway"); click("collapse") }
        clickTo("mini_player", "full_player"); settle()
        // let go halfway: finishes in the drag direction (down)
        val sheetH = InstrumentationRegistry.getInstrumentation().targetContext.resources.displayMetrics.heightPixels.toFloat()
        compose.onAllNodesWithTag("player_cover", useUnmergedTree = true).onFirst().performTouchInput {
            down(center); repeat(20) { moveBy(Offset(0f, sheetH * 0.4f / 20f), delayMillis = 16) }; Thread.sleep(400); up()
        }
        settle(); if (!isMini()) problem("letting go halfway did not finish closing")
        // ---- mini player: sideways swipe stays inside the capsule; short pull up opens fully; fast flick up too
        val m0 = title()
        compose.onNodeWithTag("mini_player", useUnmergedTree = true).performTouchInput {
            down(Offset(width * 0.3f, centerY)); repeat(6) { moveBy(Offset(-30f, 0f), delayMillis = 30) }
        }
        Thread.sleep(150); shot("mini_swipe_in_progress")
        compose.onNodeWithTag("mini_player", useUnmergedTree = true).performTouchInput { repeat(6) { moveBy(Offset(-40f, 0f), delayMillis = 20) }; up() }
        settle()
        if (title() == m0) problem("swipe on the mini player did not change the song")
        compose.onNodeWithTag("mini_player", useUnmergedTree = true).performTouchInput {
            down(center); repeat(10) { moveBy(Offset(0f, -14f), delayMillis = 40) }; up()
        }
        settle(); if (!isFull()) problem("short pull up on the mini player stopped halfway")
        click("collapse"); settle()
        compose.onNodeWithTag("mini_player", useUnmergedTree = true).performTouchInput { swipeUp(startY = centerY, endY = centerY - 900f, durationMillis = 120) }
        settle(); if (!isFull()) problem("fast flick up on the mini player did not open it")
        // pulling the bottom strip down also closes it
        compose.onNodeWithTag("peek_line", useUnmergedTree = true).performTouchInput { swipeDown(startY = centerY, endY = centerY + 900f, durationMillis = 300) }
        if (!waitTag("mini_player", 5000, soft = true)) problem("swipe down on the bottom strip did not close the player")
        clickTo("mini_player", "full_player"); settle()

        // ---- shuffle and repeat
        val before = queue(); val cur = current()
        click("shuffle"); Thread.sleep(1500)
        val shuffled = queue()
        if (!vm().player.ui.value.shuffle) problem("shuffle did not turn on")
        if (current() != cur) problem("shuffle changed the song playing now")
        if (shuffled.firstOrNull() != cur) problem("after shuffle the current song should be first in Up next")
        if (shuffled.sorted() != before.sorted()) problem("shuffle lost or added songs")
        shot("player_shuffle_on")
        click("peek_line"); waitTag("queue_list"); Thread.sleep(600); shot("up_next_shuffled"); back(); settle()
        click("shuffle"); Thread.sleep(1500)
        if (vm().player.ui.value.shuffle) problem("shuffle did not turn off")
        if (queue() != before) problem("turning shuffle off did not bring back the original order")
        if (current() != cur) problem("shuffle off changed the song playing now")
        click("repeat"); Thread.sleep(600)
        if (vm().player.ui.value.repeat != androidx.media3.common.Player.REPEAT_MODE_ALL) problem("repeat did not turn to Repeat all")
        click("repeat"); Thread.sleep(600)
        if (vm().player.ui.value.repeat != androidx.media3.common.Player.REPEAT_MODE_ONE) problem("repeat did not turn to Repeat this song")
        shot("player_repeat_one")
        click("repeat"); Thread.sleep(600)
        if (vm().player.ui.value.repeat != androidx.media3.common.Player.REPEAT_MODE_OFF) problem("repeat did not turn off")

        // ---- Up next (one queue) and its tools
        click("btn_next"); Thread.sleep(1200); shot("player_next_song")
        val closedTop = bounds("panel")?.top
        click("peek_line"); waitTag("queue_list"); Thread.sleep(700); shot("player_up_next")
        if (compose.onAllNodesWithContentDescription("Remove", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty())
            problem("a remove (trash) icon is drawn behind Up next rows")
        if (!has("queue_locate") || !has("queue_menu")) problem("Up next header buttons missing")
        val openTop = bounds("panel")?.top
        back(); settle()
        if (closedTop != null && openTop != null) {
            // short slow pull up, let go: it must open fully
            compose.onNodeWithTag("peek_line", useUnmergedTree = true).performTouchInput {
                down(center); repeat(10) { moveBy(Offset(0f, -14f), delayMillis = 40) }; Thread.sleep(300); up()
            }
            settle(); shot("panel_after_short_pull_up")
            val t1 = bounds("panel")?.top ?: closedTop
            if (kotlin.math.abs(t1 - openTop) > 8f) problem("Up next stopped halfway after a short pull up ($t1 vs $openTop)")
            // drag about half way up slowly and hold before letting go: still opens fully
            back(); settle()
            val half = (closedTop - openTop) * 0.45f
            compose.onNodeWithTag("peek_line", useUnmergedTree = true).performTouchInput {
                down(center); repeat(20) { moveBy(Offset(0f, -half / 20f), delayMillis = 30) }
            }
            Thread.sleep(150); compose.waitForIdle(); shot("panel_half_way")
            // the strip fades out as the page rises, so the finger is lifted through the page itself (same gesture)
            compose.onNodeWithTag("panel", useUnmergedTree = true).performTouchInput { advanceEventTime(400); up() }
            settle()
            val t2 = bounds("panel")?.top ?: closedTop
            if (kotlin.math.abs(t2 - openTop) > 8f) problem("Up next stopped halfway after a slow half pull ($t2 vs $openTop)")
            // short pull down on the open page: closes fully
            compose.onNodeWithTag("panel", useUnmergedTree = true).performTouchInput {
                down(Offset(centerX, 30f)); repeat(10) { moveBy(Offset(0f, 14f), delayMillis = 40) }; Thread.sleep(300); up()
            }
            settle(); shot("panel_after_short_pull_down")
            val t3 = bounds("panel")?.top ?: openTop
            if (kotlin.math.abs(t3 - closedTop) > 8f) problem("Up next stopped halfway after a short pull down ($t3 vs $closedTop)")
            if (kotlin.math.abs(t3 - closedTop) > 8f) back()
            settle()
            click("peek_line"); waitTag("queue_list"); Thread.sleep(500)
        }
        if (has("queue_2")) problem("old Queue 2 still shown")
        click("queue_locate"); click("queue_menu"); waitTag("queue_save"); shot("queue_menu"); back()
        back(); settle()
        // ---- Lyrics (button) — a song without lyrics shows the add page
        click("extra_lyrics"); Thread.sleep(1500); shot("player_lyrics")
        if (!has("lyrics") && !has("lyrics_empty")) problem("Lyrics button did not open lyrics")
        back(); settle()
        click("extra_sleep"); waitTag("sleep_30"); shot("sheet_sleep"); back()
        click("player_menu"); click("menu_speed"); waitTag("speed_slider"); shot("sheet_speed"); back()
        click("player_menu"); click("menu_sound"); waitTag("sound"); shot("sheet_sound"); click("eq_switch"); click("preset_Bollywood"); shot("sheet_sound_eq_on"); back()
        click("player_menu"); click("menu_info"); waitTag("info"); shot("sheet_info"); back()
        click("extra_playlist"); waitTag("new_playlist"); shot("sheet_add_to_playlist"); back()
        click("like"); Thread.sleep(500); shot("player_liked")
        click("collapse"); waitTag("mini_player"); Thread.sleep(600)

        // ---- song with lyrics inside the file: live line + lyrics page
        song("Tum Se Hi.mp3")?.let { s ->
            go(com.sushant.tarang.ui.R0.folder(s.folderPath), "song_list")
            click("song_${s.id}"); Thread.sleep(1500)
            if (waitTag("mini_player", 4000, soft = true)) shot("queue_replaced_undo")
            clickTo("mini_player", "full_player"); Thread.sleep(2500); shot("player_live_lyric")
            click("extra_lyrics"); Thread.sleep(2000); shot("player_lyrics_embedded"); back(); click("collapse"); waitTag("mini_player")
        }

        // ---- song with a synced .lrc next to it: the live line follows the music
        song("Raataan Lambiyan.mp3")?.let { s ->
            // Android 13+ hides .lrc text files from apps unless a lyrics folder is picked, so add it the way a user does (Add .lrc file)
            val lrc = InstrumentationRegistry.getInstrumentation().context.assets.open("media/Bollywood/Raataan Lambiyan.lrc").bufferedReader().readText()
            vm().saveLyrics(s.id, lrc, ".lrc file")
            Thread.sleep(800)
            go(com.sushant.tarang.ui.R0.folder(s.folderPath), "song_list")
            click("song_${s.id}"); Thread.sleep(1200)
            clickTo("mini_player", "full_player")
            if (!waitTag("live_lyric", 8000, soft = true)) problem("live lyric line missing for a song with a synced .lrc file")
            Thread.sleep(2500); shot("player_live_lyric_synced")
            click("collapse"); waitTag("mini_player")
        }

        // ---- Library + floating selection toolbar
        tab("library", "library"); waitTag("songs_list"); shot("library_songs")
        checkInlineTitle("Library", "open_search")
        if (!has("sort")) problem("Library: Sort button missing")
        checkMarked("songs_list", "library")
        // the playing song is marked on its album and artist pages too
        val curSong = app.repo.library.value.byId[current()]
        curSong?.let { cs ->
            app.repo.library.value.albums.firstOrNull { a -> a.songs.any { it.id == cs.id } }?.let { a ->
                go(com.sushant.tarang.ui.R0.album(a.id), "song_list"); Thread.sleep(800); checkMarked("song_rows", "album"); back() }
            app.repo.library.value.artists.firstOrNull { a -> a.songs.any { it.id == cs.id } }?.let { a ->
                go(com.sushant.tarang.ui.R0.artist(a.name), "song_list"); Thread.sleep(800); checkMarked("song_rows", "artist"); back() }
            tab("library", "library")
        }
        click("sort"); waitTag("dialog_ok", 2000, soft = true); shot("library_sort_dialog"); back()
        click("select"); waitTag("selection_toolbar"); shot("library_select"); click("select_all"); Thread.sleep(400); shot("library_select_all")
        if (!has("selected_count_${app.repo.library.value.songs.size}") && compose.onAllNodesWithText("${app.repo.library.value.songs.size} selected", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isEmpty()) problem("Select all did not select every song")
        click("sel_delete")
        if (!waitTag("confirm", 3000, soft = true)) problem("Delete on the selection toolbar did not ask to confirm") else { shot("library_delete_confirm"); back(); Thread.sleep(400) }
        if (has("selection_toolbar")) back()
        Thread.sleep(500); if (!has("library")) { problem("leaving selection left the Library"); tab("library", "library") }
        click("libtab_albums"); waitTag("albums_grid"); shot("library_albums")
        app.repo.library.value.albums.firstOrNull()?.let { a -> click("album_${a.id}"); waitTag("list_header"); shot("album_detail"); back() }
        click("libtab_artists"); waitTag("artists_list"); shot("library_artists")
        app.repo.library.value.artists.firstOrNull()?.let { a -> click("artist_${a.name}"); waitTag("list_header"); shot("artist_detail"); back() }
        click("libtab_playlists"); waitTag("playlists"); shot("library_playlists_empty")
        click("create_playlist"); type("dialog_input", "Road trip"); shot("playlist_create"); click("dialog_ok")
        runCatching { compose.waitUntil(5000) { app.repo.playlists.value.isNotEmpty() } }
        app.repo.playlists.value.firstOrNull()?.let { p ->
            song("Iktara.mp3")?.let { s -> runCatching { kotlinx.coroutines.runBlocking { app.repo.addToPlaylist(p.id, listOf(s.id)) } } }
            song("Lag Jaa Gale.mp3")?.let { s -> runCatching { kotlinx.coroutines.runBlocking { app.repo.addToPlaylist(p.id, listOf(s.id)) } } }
            click("playlist_${p.id}"); waitTag("list_header"); Thread.sleep(800); shot("playlist_detail")
            click("list_menu"); shot("playlist_menu"); back(); back()
        }
        click("libtab_songs"); click("tile_liked"); waitTag("list_header"); shot("liked_songs"); back()

        // ---- Home (now playing), Search
        tab("home", "home"); shot("home_playing")
        compose.onNodeWithTag("home").performTouchInput { swipeUp(startY = bottom * 0.8f, endY = bottom * 0.2f, durationMillis = 400) }
        Thread.sleep(700); if (!has("collapsed_title")) problem("big title did not collapse into the top bar"); shot("home_scrolled")
        clickTo("open_search", "search"); shot("search_empty")
        type("search_field", "kes"); Thread.sleep(800); shot("search_results")
        app.repo.library.value.byId[current()]?.let { cs -> type("search_field", cs.title.take(6)); Thread.sleep(800); checkMarked("search_list", "search") }
        type("search_field", "zzzz"); Thread.sleep(500); shot("search_nothing"); back(); if (has("search")) back()

        // ---- Settings (Stats card on top), Stats, About (hidden Diagnostics)
        go(com.sushant.tarang.ui.R0.SETTINGS, "settings"); shot("settings")
        if (has("set_diagnostics")) problem("Diagnostics should not be listed in Settings")
        clickTo("set_stats", "stats"); Thread.sleep(1200); shot("stats")
        click("chip_All time"); Thread.sleep(1000); shot("stats_all_time"); back()
        click("set_theme"); shot("settings_theme_dialog"); back()
        click("set_buttons"); shot("settings_notif_buttons"); back()
        click("set_crossfade"); shot("settings_crossfade"); back()
        click("set_carousel"); shot("settings_carousel"); back()
        compose.onNodeWithTag("set_about").performScrollTo(); shot("settings_bottom")
        if (compose.onAllNodesWithText("no internet access", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) problem("the no-internet card should be gone from Settings")
        val settingsLeft = bounds("set_about")?.left; val settingsRight = bounds("set_about")?.right
        clickTo("set_folders", "scan_folders"); shot("scan_folders"); back()
        clickTo("set_about", "about"); shot("about")
        if (compose.onAllNodesWithText("offline music player", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) problem("About: the tagline should be gone")
        if (compose.onAllNodesWithText("Built with", substring = true, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()) problem("About: the 'Built with' line should be gone")
        bounds("about_version")?.let { v -> if (settingsLeft != null && settingsRight != null && (kotlin.math.abs(v.left - settingsLeft) > 2f || kotlin.math.abs(v.right - settingsRight) > 2f))
            problem("About cards are not the same width as Settings cards (${v.left}..${v.right} vs $settingsLeft..$settingsRight)") }
        repeat(5) { compose.onNodeWithTag("about_version", useUnmergedTree = true).performClick(); Thread.sleep(120) }
        if (!waitTag("diagnostics", 4000, soft = true)) problem("5 taps on Version did not open Diagnostics") else { shot("diagnostics"); back() }
        back()
        song("Kesariya.mp3")?.let { s ->
            go(com.sushant.tarang.ui.R0.edit(s.id), "edit_details"); Thread.sleep(800); shot("edit_details")
            type("edit_title", "Kesariya (edit)"); back()   // first back closes the keyboard
            if (!has("confirm")) back()
            if (waitTag("confirm", 3000)) { shot("edit_discard_prompt"); click("confirm") }
            go(com.sushant.tarang.ui.R0.lyrics(s.id), "lyrics_edit"); Thread.sleep(800); shot("lyrics_edit"); back(); if (has("lyrics_edit")) back()
        }

        // ---- Dark theme, then pure black
        suffix = "_dark"
        app.prefs.update { it.copy(themeMode = ThemeMode.DARK) }
        go(com.sushant.tarang.ui.R0.HOME, "home"); Thread.sleep(800); shot("home")
        tab("folders", "folders"); shot("folders")
        tab("library", "library"); shot("library")
        clickTo("mini_player", "full_player"); Thread.sleep(800); shot("player_full")
        click("peek_line"); waitTag("queue_list"); shot("player_up_next"); back(); click("collapse"); waitTag("mini_player")
        go(com.sushant.tarang.ui.R0.SETTINGS, "settings"); shot("settings")
        go(com.sushant.tarang.ui.R0.STATS, "stats"); Thread.sleep(1000); shot("stats")
        go(com.sushant.tarang.ui.R0.SETTINGS, "settings")
        if (!has("set_pure_black")) problem("pure black switch missing in dark mode")
        click("set_pure_black"); suffix = "_pureblack"; shot("settings"); go(com.sushant.tarang.ui.R0.HOME, "home"); shot("home")
        clickTo("mini_player", "full_player"); Thread.sleep(800); shot("player_full"); click("collapse")
        // pure black switch must disappear in light mode
        app.prefs.update { it.copy(themeMode = ThemeMode.LIGHT) }; suffix = ""
        go(com.sushant.tarang.ui.R0.SETTINGS, "settings"); Thread.sleep(600)
        if (has("set_pure_black")) problem("pure black switch shown in light mode")
        app.prefs.update { it.copy(pureBlack = false, eqEnabled = false) }
        runCatching { scenario.close() }   // closing can time out while the player service keeps the app busy; the walk itself is done
    }
}
