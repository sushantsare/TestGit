package com.sushant.grabbit

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.lifecycle.ViewModelProvider
import com.sushant.grabbit.data.Library
import com.sushant.grabbit.data.SavedItem
import com.sushant.grabbit.ui.MainViewModel
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.download.DownloadWorker
import com.sushant.grabbit.download.Downloads
import com.sushant.grabbit.engine.Engine
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Real end-to-end run on the emulator: starts the engine, updates yt-dlp,
 * then grabs real links through the UI and checks the files land in the library.
 */
@RunWith(AndroidJUnit4::class)
class GrabbitE2ETest {
    @get:Rule(order = 0)
    val perm: GrantPermissionRule = GrantPermissionRule.grant(Manifest.permission.POST_NOTIFICATIONS)

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val instr = InstrumentationRegistry.getInstrumentation()
    private val ctx = instr.targetContext
    private val results = mutableListOf<String>()

    private fun log(msg: String) {
        Log.i("GrabbitE2E", msg)
        results += msg
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        Thread.sleep(1200)
        val bmp: Bitmap = instr.uiAutomation.takeScreenshot() ?: return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AppShots")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private fun exists(tag: String) =
        compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty()

    private fun failedCount(): Int = WorkManager.getInstance(ctx).getWorkInfosByTag(Downloads.TAG).get()
        .count { it.state == WorkInfo.State.FAILED }

    /** Grabs a link through the UI with the given quality chip (null = default). Returns true if a file was saved. */
    private fun grab(name: String, url: String): Boolean {
        compose.onNodeWithTag("nav_home").performClick()
        compose.onNodeWithTag("urlField").performTextClearance()
        compose.onNodeWithTag("urlField").performTextInput(url)
        compose.onNodeWithTag("grabBtn").performClick()
        try {
            compose.waitUntil(150_000) { exists("downloadBtn") || exists("fetchError") }
        } catch (e: Throwable) {
            log("[$name] FETCH TIMEOUT"); shot("${name}_fetch_timeout"); return false
        }
        if (exists("fetchError")) {
            log("[$name] FETCH FAILED"); shot("${name}_fetch_error"); return false
        }
        shot("${name}_1_info")
        val before = Library.items.value.size
        val failedBefore = failedCount()
        compose.onNodeWithTag("downloadBtn").performScrollTo().performClick()
        val start = System.currentTimeMillis()
        var shotProgress = false
        while (System.currentTimeMillis() - start < 300_000) {
            if (Library.items.value.size > before) break
            if (failedCount() > failedBefore) break
            if (!shotProgress) {
                val sized = WorkManager.getInstance(ctx).getWorkInfosByTag(Downloads.TAG).get().any {
                    it.state == WorkInfo.State.RUNNING && !it.progress.getString(DownloadWorker.P_SIZE).isNullOrEmpty()
                }
                if (sized || System.currentTimeMillis() - start > 15_000) { shot("${name}_2_progress"); shotProgress = true }
            }
            Thread.sleep(1000)
        }
        val ok = Library.items.value.size > before
        if (ok) {
            val item = Library.items.value.first()
            log("[$name] OK  ${item.fileName}  ${item.size} bytes  ${item.mime}  in ${(System.currentTimeMillis() - start) / 1000}s")
        } else {
            val failed = WorkManager.getInstance(ctx).getWorkInfosByTag(Downloads.TAG).get()
                .filter { it.state == WorkInfo.State.FAILED }
            val err = failed.lastOrNull()?.outputData?.getString(DownloadWorker.K_RAW)
                ?: failed.lastOrNull()?.outputData?.getString(DownloadWorker.K_ERROR) ?: "timeout"
            log("[$name] DOWNLOAD FAILED: ${err.takeLast(600)}")
        }
        shot("${name}_3_done")
        return ok
    }

    /** Runs yt-dlp directly against several sites/options and logs exactly what happens. */
    private fun diagnose() {
        val yt = "https://www.youtube.com/watch?v=jNQXAC9IVRw"
        val cases = listOf(
            Triple("yt-default", yt, emptyList<String>()),
            Triple("yt-android_vr", yt, listOf("--extractor-args", "youtube:player_client=android_vr")),
            Triple("yt-tv_simply", yt, listOf("--extractor-args", "youtube:player_client=tv_simply")),
            Triple("yt-mweb", yt, listOf("--extractor-args", "youtube:player_client=mweb")),
            Triple("yt-ios", yt, listOf("--extractor-args", "youtube:player_client=ios")),
            Triple("vimeo", "https://vimeo.com/76979871", emptyList()),
            Triple("dailymotion", "https://www.dailymotion.com/video/x7tgad0", emptyList()),
            Triple("reddit", "https://www.reddit.com/r/aww/comments/90bu6w/heat_index_was_110_degrees_so_we_offered_him_a/", emptyList()),
            Triple("x", "https://x.com/SpaceX/status/1732824684683784516", emptyList()),
            Triple("facebook", "https://www.facebook.com/watch/?v=10153231379946729", emptyList()),
            Triple("instagram", "https://www.instagram.com/reel/C6vRMSTJ0fG/", emptyList()),
            Triple("archive", "https://archive.org/details/BigBuckBunny_124", emptyList()),
        )
        for ((name, url, extra) in cases) {
            val req = YoutubeDLRequest(url).apply {
                addOption("-J"); addOption("--no-playlist")
                var i = 0
                while (i < extra.size) { addOption(extra[i], extra[i + 1]); i += 2 }
            }
            val t = System.currentTimeMillis()
            try {
                val r = YoutubeDL.getInstance().execute(req, processId = "diag-$name", callback = null)
                val j = org.json.JSONObject(r.out.lineSequence().last { it.startsWith("{") })
                val f = j.optJSONArray("formats")
                var maxH = 0
                if (f != null) for (k in 0 until f.length()) maxH = maxOf(maxH, f.getJSONObject(k).optInt("height", 0))
                log("DIAG $name OK formats=${f?.length()} maxH=$maxH ${(System.currentTimeMillis() - t) / 1000}s warn=${r.err.takeLast(300).replace('\n', ' ')}")
            } catch (e: Throwable) {
                log("DIAG $name FAIL ${(e.message ?: e.toString()).takeLast(500).replace('\n', ' ')}")
            }
        }
    }

    @Test
    fun endToEnd() {
        shot("00_home_start")
        val ready = runBlocking { withTimeout(180_000) { Engine.awaitReady() } }
        log("Engine ready=$ready version=${Engine.version.value} state=${Engine.state.value}")
        assertTrue("Engine failed to start: ${Engine.state.value}", ready)

        // Wait for the automatic first-launch yt-dlp update to finish
        val t0 = System.currentTimeMillis()
        while (Prefs.lastUpdate == 0L && System.currentTimeMillis() - t0 < 240_000) Thread.sleep(1000)
        while (Engine.updating.value && System.currentTimeMillis() - t0 < 300_000) Thread.sleep(1000)
        log("After auto-update: version=${Engine.version.value} lastUpdate=${Prefs.lastUpdate}")
        shot("01_home_ready")

        val direct = grab("direct", "https://www.w3schools.com/html/mov_bbb.mp4")
        val x = grab("x", "https://x.com/SpaceX/status/1732824684683784516")
        val dm = grab("dailymotion", "https://www.dailymotion.com/video/x7tgad0")
        val fb = grab("facebook", "https://www.facebook.com/watch/?v=10153231379946729")
        val youtube = grab("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw")

        compose.onNodeWithTag("nav_downloads").performClick()
        shot("90_downloads")
        compose.onNodeWithTag("nav_settings").performClick()
        shot("91_settings")
        compose.onNodeWithTag("settingsList").performScrollToNode(hasText("Developed by Sushant Kumar"))
        shot("91b_about")

        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]

        // Search in Saved
        compose.onNodeWithTag("nav_downloads").performClick()
        compose.onNodeWithTag("searchField").performTextInput("mov")
        shot("92_search")
        compose.onNodeWithContentDescription("Clear search").performClick()
        compose.activity.runOnUiThread {
            val a = compose.activity
            a.currentFocus?.clearFocus()
            WindowCompat.getInsetsController(a.window, a.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }

        // A library entry whose file is gone must be detected as missing
        val ghost = SavedItem(
            id = "ghost", title = "Deleted from gallery", site = "Test", thumb = "",
            uri = "content://media/external/video/media/987654321", fileName = "ghost.mp4", mime = "video/mp4",
            size = 1, time = System.currentTimeMillis(), sourceUrl = "", path = null,
        )
        Library.add(listOf(ghost))
        val missingOk = "ghost" in vm.findMissing(Library.items.value)
        log("MISSING-CHECK ghost detected=$missingOk")
        compose.activity.runOnUiThread { vm.checkMissing() }
        Thread.sleep(1500)
        compose.onNodeWithTag("sortBtn").performClick()
        shot("93a_sort_menu")
        compose.onNodeWithText("Newest first").performClick()
        shot("93_missing_file")
        Library.remove("ghost")
        assertTrue("Missing file not detected", missingOk)

        // Delete must really remove the file, not just the list entry
        val target = Library.items.value.firstOrNull { it.fileName.startsWith("mov_bbb") }
        if (target != null) {
            val r = vm.deleteNow(target)
            val gone = !vm.fileExists(target)
            log("DELETE result=$r fileGone=$gone")
            assertTrue("Delete did not remove the file: $r", r == MainViewModel.DeleteResult.Done && gone)
        }

        // Dark mode screenshots of every main screen
        Prefs.theme = 2
        compose.onNodeWithTag("nav_home").performClick()
        shot("95_dark_home")
        compose.onNodeWithTag("nav_downloads").performClick()
        shot("96_dark_downloads")
        compose.onNodeWithTag("nav_settings").performClick()
        shot("97_dark_settings")
        Prefs.theme = 0

        log("SUMMARY direct=$direct x=$x dailymotion=$dm facebook=$fb youtube=$youtube")
        results.forEach { Log.i("GrabbitE2E", "RESULT $it") }
        assertTrue("Downloads failed", direct && x)
    }
}
