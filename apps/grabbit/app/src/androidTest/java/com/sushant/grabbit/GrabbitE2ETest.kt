package com.sushant.grabbit

import android.Manifest
import android.content.ContentValues
import android.graphics.Bitmap
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.sushant.grabbit.data.Library
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.download.DownloadWorker
import com.sushant.grabbit.download.Downloads
import com.sushant.grabbit.engine.Engine
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
            if (!shotProgress && System.currentTimeMillis() - start > 4000) { shot("${name}_2_progress"); shotProgress = true }
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
        val youtube = grab("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw")
        val vimeo = grab("vimeo", "https://vimeo.com/76979871")

        compose.onNodeWithTag("nav_downloads").performClick()
        shot("90_downloads")
        compose.onNodeWithTag("nav_settings").performClick()
        shot("91_settings")

        log("SUMMARY direct=$direct youtube=$youtube vimeo=$vimeo")
        results.forEach { Log.i("GrabbitE2E", "RESULT $it") }
        assertTrue("No download succeeded", direct || youtube || vimeo)
    }
}
