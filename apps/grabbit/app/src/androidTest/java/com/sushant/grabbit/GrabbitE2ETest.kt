package com.sushant.grabbit

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Intent
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import com.sushant.grabbit.engine.Threads
import java.net.HttpURLConnection
import java.net.URL
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
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.semantics.SemanticsProperties
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

    private fun Espresso_back() {
        instr.uiAutomation.executeShellCommand("input keyevent KEYCODE_BACK").close()
        Thread.sleep(800)
        compose.waitForIdle()
    }

    private fun saveBitmap(name: String, bmp: Bitmap) {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AppShots")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    /** Draws the real launcher icon (and its themed/monochrome layer) from the APK for review. */
    private fun renderIcons() {
        val size = 432
        val out = Bitmap.createBitmap(size * 3 + 80, size, Bitmap.Config.ARGB_8888)
        val c = android.graphics.Canvas(out)
        c.drawColor(android.graphics.Color.WHITE)
        val icon = ctx.packageManager.getApplicationIcon(ctx.packageName)
        icon.setBounds(0, 0, size, size); icon.draw(c)
        val round = androidx.core.content.ContextCompat.getDrawable(ctx, R.mipmap.ic_launcher_round)!!
        round.setBounds(size + 40, 0, size * 2 + 40, size); round.draw(c)
        if (android.os.Build.VERSION.SDK_INT >= 33 && icon is android.graphics.drawable.AdaptiveIconDrawable) {
            val mono = icon.monochrome
            if (mono != null) {
                val p = android.graphics.Paint().apply { color = 0xFFD7E8DF.toInt() }
                c.drawCircle(size * 2.5f + 80, size / 2f, size / 2f, p)
                mono.setTint(0xFF1F3A2E.toInt())
                val inset = size / 6
                mono.setBounds(size * 2 + 80 - inset, -inset, size * 3 + 80 + inset, size + inset); mono.draw(c)
            }
        }
        saveBitmap("icon_launcher", out)
    }

    private fun hasTestTagPrefix(prefix: String) = SemanticsMatcher("tag starts with $prefix") {
        (if (SemanticsProperties.TestTag in it.config) it.config[SemanticsProperties.TestTag] else "").startsWith(prefix)
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
        if (name == "x" && compose.onAllNodesWithText("Audio").fetchSemanticsNodes().isNotEmpty()) {
            compose.onNodeWithText("Audio").performClick()
            compose.onNodeWithTag("downloadBtn").performScrollTo()
            shot("${name}_1b_audio")
            log("[$name] audio options: " + compose.onAllNodes(hasTestTagPrefix("q_a")).fetchSemanticsNodes().size)
            compose.onNodeWithText("Video").performClick()
            if (exists("trimChip")) {
                compose.onNodeWithTag("trimChip").performClick()
                Thread.sleep(800)
                shot("${name}_1c_trim")
                compose.onNodeWithText("Done").performClick()
            }
        }
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
        val vmCheck = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        log("[$name] grab tab cleared after download: ${vmCheck.url.isEmpty()}")
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


    // ---------- Share sheet ("Share → Grabbit" from another app) ----------

    private fun launchShare(text: String): ActivityScenario<ShareActivity> = ActivityScenario.launch(
        Intent(ctx, ShareActivity::class.java).setAction(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, text).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )

    private fun clearMainScreen() {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        compose.activity.runOnUiThread { vm.url = ""; vm.resetFetch() }
        compose.waitForIdle()
    }

    private fun waitSheet(): Boolean = try {
        compose.waitUntil(150_000) { exists("downloadBtn") || exists("fetchError") }
        true
    } catch (e: Throwable) { false }

    private fun shareFlow(): Boolean {
        clearMainScreen()
        val before = Library.items.value.size
        val sc = launchShare("Check this out https://www.w3schools.com/html/mov_bbb.mp4")
        if (!waitSheet() || !exists("downloadBtn")) {
            shot("s1_sheet_failed"); log("SHARE fetch failed"); sc.close(); return false
        }
        shot("s1_sheet_light")
        compose.onNodeWithTag("downloadBtn").performClick()
        val closed = try {
            compose.waitUntil(20_000) { sc.state == Lifecycle.State.DESTROYED }; true
        } catch (e: Throwable) { false }
        val saved = try {
            compose.waitUntil(180_000) { Library.items.value.size > before }; true
        } catch (e: Throwable) { false }
        Thread.sleep(1500)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val notif = nm.activeNotifications.any {
            it.notification.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty().startsWith("Saved")
        }
        log("SHARE sheetClosed=$closed saved=$saved savedNotification=$notif")

        // Dark theme + a real site
        Prefs.theme = 2
        val sc2 = launchShare("https://x.com/SpaceX/status/1732824684683784516")
        waitSheet()
        shot("s2_sheet_dark")
        sc2.close()
        Prefs.theme = 0

        // Error state: a page with no video
        val sc3 = launchShare("https://example.com/")
        waitSheet()
        shot("s3_sheet_error")
        sc3.close()
        return closed && saved
    }

    // ---------- Threads ----------

    private fun httpGet(url: String, ua: String): String {
        val c = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 20_000
            setRequestProperty("User-Agent", ua)
            setRequestProperty("Accept-Language", "en-US,en;q=0.9")
        }
        return try { "HTTP ${c.responseCode}\n" + (runCatching { c.inputStream.bufferedReader().use { it.readText() } }.getOrDefault("")) }
        finally { c.disconnect() }
    }

    /** Finds a public Threads video post (from public profiles) so the Threads reader is tested on a real page. */
    private fun findThreadsVideo(): String? = runBlocking {
        val chrome = "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"
        for (profile in listOf("nasa", "natgeo", "netflix", "instagram", "zuck")) {
            val html = runCatching { httpGet("https://www.threads.com/@$profile", chrome) }.getOrElse { "ERR $it" }
            val posts = Regex("""/@$profile/post/([A-Za-z0-9_-]+)""").findAll(html)
                .map { "https://www.threads.com/@$profile/post/${it.groupValues[1]}" }.distinct().take(8).toList()
            log("THREADS profile @$profile ${html.lineSequence().first()} len=${html.length} posts=${posts.size} " +
                "video_versions=${"video_versions" in html} ogvideo=${"og:video" in html}")
            for (u in posts) {
                val r = runCatching { Threads.resolve(u) }
                log("THREADS try $u -> ${r.getOrNull()?.videos?.size ?: ("error " + r.exceptionOrNull()?.message)}")
                if (r.getOrNull()?.videos?.isNotEmpty() == true) return@runBlocking u
            }
            if (posts.isNotEmpty()) {
                // Leave a sample of a post page in the log for diagnosis
                val page = runCatching { httpGet(posts.first(), chrome) }.getOrDefault("")
                val at = page.indexOf("video").coerceAtLeast(0)
                log("THREADS sample len=${page.length} ${page.substring(at, minOf(page.length, at + 1500)).replace("\n", " ")}")
            }
        }
        null
    }

    private fun threadsFlow(): String {
        val u = findThreadsVideo() ?: return "no-public-video-found"
        compose.activity.startActivity(
            Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
        Thread.sleep(1500)
        return if (grab("threads", u)) "ok" else "failed"
    }


    // ---------- Per-download options: trim + audio format ----------

    private fun durationMs(uri: String): Long = runCatching {
        val r = android.media.MediaMetadataRetriever()
        r.setDataSource(ctx, android.net.Uri.parse(uri))
        val d = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLong() ?: -1
        r.release(); d
    }.getOrDefault(-1)

    private fun optionsFlow(): String {
        val url = "https://www.w3schools.com/html/mov_bbb.mp4"
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val info = runBlocking { com.sushant.grabbit.engine.Media.fetchInfo(ctx, url) }
        // Trim 2s..6s
        var before = Library.items.value.size
        compose.activity.runOnUiThread { vm.download(info, "best", com.sushant.grabbit.download.DlOpts(trimStart = 2, trimEnd = 6)) }
        val trimOk = try { compose.waitUntil(180_000) { Library.items.value.size > before }; true } catch (e: Throwable) { false }
        val trimmed = Library.items.value.maxByOrNull { it.time }
        val d = trimmed?.let { durationMs(it.uri) } ?: -1
        log("OPTIONS trim saved=$trimOk durationMs=$d")
        // Audio as M4A
        before = Library.items.value.size
        compose.activity.runOnUiThread { vm.download(info, "audio", com.sushant.grabbit.download.DlOpts(audioFormat = "m4a")) }
        val m4aOk = try { compose.waitUntil(180_000) { Library.items.value.size > before }; true } catch (e: Throwable) { false }
        val m4a = Library.items.value.maxByOrNull { it.time }
        log("OPTIONS m4a saved=$m4aOk mime=${m4a?.mime} name=${m4a?.fileName}")
        // Trim on a real site (X video, 1:54): keep 10s..20s
        val xInfo = runCatching { runBlocking { com.sushant.grabbit.engine.Media.fetchInfo(ctx, "https://x.com/SpaceX/status/1732824684683784516") } }.getOrNull()
        var xd = -1L
        if (xInfo != null) {
            before = Library.items.value.size
            compose.activity.runOnUiThread { vm.download(xInfo, "h360", com.sushant.grabbit.download.DlOpts(trimStart = 10, trimEnd = 20)) }
            val ok = try { compose.waitUntil(240_000) { Library.items.value.size > before }; true } catch (e: Throwable) { false }
            xd = if (ok) Library.items.value.maxByOrNull { it.time }?.let { durationMs(it.uri) } ?: -1 else -1
            log("OPTIONS xtrim saved=$ok durationMs=$xd")
        }
        return "trimDirect=${trimOk}(${d}ms) xtrim=${xd in 8500..12500} m4a=${m4aOk && m4a?.mime == "audio/mp4"}"
    }

    @Test
    fun endToEnd() {
        renderIcons()
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
        compose.onNodeWithTag("nav_downloads").performClick()
        shot("02_downloads_empty")
        compose.onNodeWithTag("nav_home").performClick()

        val direct = grab("direct", "https://www.w3schools.com/html/mov_bbb.mp4")
        val x = grab("x", "https://x.com/SpaceX/status/1732824684683784516")
        val dm = grab("dailymotion", "https://www.dailymotion.com/video/x7tgad0")
        val fb = grab("facebook", "https://www.facebook.com/watch/?v=10153231379946729")
        val youtube = grab("youtube", "https://www.youtube.com/watch?v=jNQXAC9IVRw")

        compose.onNodeWithTag("nav_downloads").performClick()
        shot("90_downloads")
        compose.onNodeWithTag("nav_settings").performClick()
        compose.activity.runOnUiThread {
            val a = compose.activity
            a.currentFocus?.clearFocus()
            WindowCompat.getInsetsController(a.window, a.window.decorView).hide(WindowInsetsCompat.Type.ime())
        }
        Thread.sleep(800)
        shot("91_settings")
        compose.onNodeWithTag("settingsList").performScrollToNode(hasText("Developed by Sushant Kumar"))
        shot("91b_about")
        // Subtitle mode pill: no tick, labels fit; then again with a large system font
        runCatching {
            compose.onNodeWithTag("settingsList").performScrollToNode(hasText("Subtitles"))
            compose.onAllNodesWithText("Subtitles")[0].performClick()
            shot("91c_subs_sheet")
            Espresso_back()
        }.onFailure { log("SUBS SHEET shot failed: ${it.message}") }
        runCatching {
            instr.uiAutomation.executeShellCommand("settings put system font_scale 1.4").close()
            Thread.sleep(2500)
            compose.waitForIdle()
            compose.onNodeWithTag("nav_settings").performClick()
            compose.onNodeWithTag("settingsList").performScrollToNode(hasText("Subtitles"))
            compose.onAllNodesWithText("Subtitles")[0].performClick()
            shot("91d_subs_sheet_bigfont")
            Espresso_back()
        }.onFailure { log("BIGFONT shot failed: ${it.message}") }
        instr.uiAutomation.executeShellCommand("settings put system font_scale 1.0").close()
        Thread.sleep(2500)
        compose.waitForIdle()
        compose.onNodeWithTag("nav_settings").performClick()

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
        Prefs.amoled = true
        Thread.sleep(800)
        shot("98_black_settings")
        compose.onNodeWithTag("nav_downloads").performClick()
        shot("99_black_downloads")
        Prefs.amoled = false
        Prefs.theme = 0

        val share = shareFlow()
        val threads = threadsFlow()
        val options = optionsFlow()

        log("SUMMARY direct=$direct x=$x dailymotion=$dm facebook=$fb youtube=$youtube share=$share threads=$threads $options")
        results.forEach { Log.i("GrabbitE2E", "RESULT $it") }
        assertTrue("Downloads failed", direct && x)
        assertTrue("Share sheet download failed", share)
    }
}
