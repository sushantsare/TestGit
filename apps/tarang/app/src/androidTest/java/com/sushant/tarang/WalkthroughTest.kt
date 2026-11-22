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

    private fun walk() {
        MediaSeed.run(app)
        app.prefs.update { Settings(themeMode = ThemeMode.LIGHT) }
        scenario = ActivityScenario.launch(MainActivity::class.java)

        // ---- Home (fresh install: nothing played yet, so no mini player)
        waitTag("home")
        runCatching { compose.waitUntil(20_000) { app.repo.library.value.songs.size >= 10 } }.onFailure { problem("library did not load the test songs") }
        if (app.repo.library.value.songs.any { it.folderName == "WhatsApp Audio" }) problem("WhatsApp audio should be hidden by default")
        shot("home_fresh")

        // ---- Folders → folder → play all
        tab("folders", "folders"); shot("folders")
        click("chip_Most songs"); shot("folders_most_songs"); click("chip_Name")
        clickTo("folder_Bollywood", "song_list"); shot("folder_detail")
        click("play_all")
        if (!waitTag("mini_player", 10_000)) return
        Thread.sleep(2500); shot("folder_playing_mini")
        song("Kesariya.mp3")?.let { s -> click("more_${s.id}"); waitTag("sheet"); shot("song_menu"); back() }

        // ---- full player and its panels
        clickTo("mini_player", "full_player"); Thread.sleep(800); shot("player_full")
        // ⋮ must open the menu and never pause the music
        fun playing() = compose.onAllNodesWithContentDescription("Pause", useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()
        val wasPlaying = playing()
        click("player_menu"); if (!waitTag("menu_end_queue", 4000)) problem("⋮ did not open the menu"); back(); Thread.sleep(500)
        if (wasPlaying && !playing()) problem("⋮ paused the music")
        if (has("panel_header")) problem("ghost now-playing bar visible while the panel is closed")
        // bottom strip: just a handle and "Up next · …"
        if (waitTag("peek_line", 4000)) shot("player_peek_line")
        // swipe left on the cover: next song
        compose.onNodeWithTag("player_cover", useUnmergedTree = true).performTouchInput { swipeLeft(durationMillis = 250) }
        Thread.sleep(1200); shot("player_swiped_to_next")
        // swipe down on the cover: closes into the mini player
        compose.onNodeWithTag("player_cover", useUnmergedTree = true).performTouchInput { swipeDown(endY = bottom * 0.95f, durationMillis = 400) }
        if (!waitTag("mini_player", 5000, soft = true)) problem("swipe down on the cover did not close the player") else shot("after_swipe_down")
        // swipe down on the bottom strip also closes it
        clickTo("mini_player", "full_player"); Thread.sleep(800)
        compose.onNodeWithTag("peek_line", useUnmergedTree = true).performTouchInput { swipeDown(startY = centerY, endY = centerY + 900f, durationMillis = 300) }   // a real thumb-length pull
        if (!waitTag("mini_player", 5000, soft = true)) problem("swipe down on the bottom strip did not close the player")
        clickTo("mini_player", "full_player"); Thread.sleep(800)
        click("btn_next"); Thread.sleep(1200); shot("player_next_song")
        click("peek_line"); waitTag("queue_list"); shot("player_up_next")
        click("queue_2"); Thread.sleep(800); shot("player_queue_2_empty"); click("queue_1"); Thread.sleep(800)
        click("tab_info"); waitTag("info"); shot("player_info")
        click("tab_lyrics"); Thread.sleep(1500); shot("player_lyrics")
        back()                                                              // closes the panel
        click("extra_sleep"); waitTag("sleep_30"); shot("sheet_sleep"); back()
        click("extra_speed"); waitTag("speed_slider"); shot("sheet_speed"); back()
        click("extra_sound"); waitTag("sound"); shot("sheet_sound"); click("eq_switch"); click("preset_Bollywood"); shot("sheet_sound_eq_on"); back()
        click("extra_playlist"); waitTag("new_playlist"); shot("sheet_add_to_playlist"); back()
        click("player_menu"); waitTag("menu_end_queue"); shot("player_menu"); back()
        click("collapse"); waitTag("mini_player"); Thread.sleep(600)

        // ---- song with lyrics inside the file
        song("Tum Se Hi.mp3")?.let { s ->
            go(com.sushant.tarang.ui.R0.folder(s.folderPath), "song_list")
            click("song_${s.id}"); Thread.sleep(1500)
            clickTo("mini_player", "full_player"); click("peek_line"); Thread.sleep(600); click("tab_lyrics"); Thread.sleep(2000); shot("player_lyrics_embedded"); back(); click("collapse"); waitTag("mini_player")
        }

        // ---- Library
        tab("library", "library"); waitTag("songs_list"); shot("library_songs")
        click("sort"); waitTag("dialog_ok", 2000, soft = true); shot("library_sort_dialog"); back()
        click("select"); waitTag("selection_bar"); shot("library_select"); click("select_all"); shot("library_select_all"); back()
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

        // ---- Stats, Home (now playing), Search
        tab("stats", "stats"); Thread.sleep(800); shot("stats")
        click("chip_All time"); shot("stats_all_time")
        tab("home", "home"); shot("home_playing")
        clickTo("open_search", "search"); shot("search_empty")
        type("search_field", "kes"); Thread.sleep(800); shot("search_results")
        type("search_field", "zzzz"); Thread.sleep(500); shot("search_nothing"); back(); if (has("search")) back()

        // ---- Settings and every page under it
        go(com.sushant.tarang.ui.R0.SETTINGS, "settings"); shot("settings")
        click("set_theme"); shot("settings_theme_dialog"); back()
        click("set_buttons"); shot("settings_notif_buttons"); back()
        click("set_crossfade"); shot("settings_crossfade"); back()
        compose.onNodeWithTag("set_about").performScrollTo(); shot("settings_bottom")
        clickTo("set_folders", "scan_folders"); shot("scan_folders"); back()
        clickTo("set_diagnostics", "diagnostics"); shot("diagnostics"); back()
        clickTo("set_about", "about"); shot("about"); back()
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
        tab("stats", "stats"); shot("stats")
        clickTo("mini_player", "full_player"); Thread.sleep(800); shot("player_full")
        click("peek_line"); waitTag("queue_list"); shot("player_up_next"); back(); click("collapse"); waitTag("mini_player")
        go(com.sushant.tarang.ui.R0.SETTINGS, "settings"); shot("settings")
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
