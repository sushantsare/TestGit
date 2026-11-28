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
import android.os.Build
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
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
import com.sushant.grabbit.ui.FetchState
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
        if (name == "direct" && exists("renameBtn")) {
            compose.onNodeWithTag("renameBtn").performClick()
            compose.onNodeWithTag("nameField").performTextClearance()
            compose.onNodeWithTag("nameField").performTextInput("My test: clip")
            shot("${name}_1d_rename")
            compose.onNodeWithTag("nameOk").performClick()
        }
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

    /** Pause mid-download, "restart" (reload the paused list from disk), resume, and check the finished file is whole. */
    private fun pauseFlow(fast: Boolean): String {
        val info = runCatching { runBlocking { com.sushant.grabbit.engine.Media.fetchInfo(ctx, "https://x.com/SpaceX/status/1732824684683784516") } }.getOrNull()
            ?: return "pause=noInfo"
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val wm = WorkManager.getInstance(ctx)
        Prefs.fasterDownloads = fast
        val name = if (fast) "fast" else "single"
        return runCatching {
            val before = Library.items.value.size
            compose.activity.runOnUiThread { vm.download(info, "h720", com.sushant.grabbit.download.DlOpts()) }
            compose.onNodeWithTag("nav_downloads").performClick()
            // Wait until it is properly under way
            var running: androidx.work.WorkInfo? = null
            compose.waitUntil(120_000) {
                running = wm.getWorkInfosByTag(Downloads.TAG).get().firstOrNull {
                    it.state == androidx.work.WorkInfo.State.RUNNING && it.progress.getFloat(com.sushant.grabbit.download.DownloadWorker.P_PCT, 0f) >= (if (fast) 8f else 45f)
                }
                running != null
            }
            val pctAtPause = running!!.progress.getFloat(com.sushant.grabbit.download.DownloadWorker.P_PCT, 0f)
            compose.onAllNodesWithTag("pauseBtn")[0].performClick()
            compose.waitUntil(20_000) { com.sushant.grabbit.data.Paused.items.value.isNotEmpty() }
            Thread.sleep(3000)
            val p = com.sushant.grabbit.data.Paused.items.value.first()
            val partBytes = Downloads.partDir(ctx, p.job.key).walkTopDown().filter { it.isFile }.sumOf { it.length() }
            shot("pause_${name}_1_paused")
            // Simulate closing the app: reload the paused list from storage
            com.sushant.grabbit.data.Paused.init(ctx)
            val survived = com.sushant.grabbit.data.Paused.has(p.job.key)
            compose.waitForIdle()
            val tResume = System.currentTimeMillis()
            compose.onAllNodesWithTag("resumeBtn")[0].performClick()
            compose.waitUntil(300_000) { Library.items.value.size > before }
            val ms = System.currentTimeMillis() - tResume
            val done = Library.items.value.maxByOrNull { it.time }!!
            val d = durationMs(done.uri)
            shot("pause_${name}_2_done")
            log("PAUSE $name pctAtPause=$pctAtPause partBytes=$partBytes canContinue=${p.canContinue} survived=$survived resumeMs=$ms bytes=${done.size} durationMs=$d")
            "pause_$name=${partBytes > 0 && p.canContinue && survived && done.size > 10_000_000 && d > 100_000}"
        }.getOrElse { log("PAUSE $name failed: ${it.message}"); shot("pause_${name}_fail"); "pause_$name=false" }
            .also { Prefs.fasterDownloads = true }
    }

    /** Save to a folder picked with Android's folder picker, sticky choice, rename/delete there, then back to Default. */
    private fun folderFlow(): String {
        val ui = androidx.test.uiautomator.UiDevice.getInstance(instr)
        instr.uiAutomation.executeShellCommand("mkdir -p /sdcard/Movies/GrabbitTest").close()
        Thread.sleep(800)
        com.sushant.grabbit.data.Folders.pickerStart = android.provider.DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Movies/GrabbitTest")
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        return runCatching {
            compose.onNodeWithTag("nav_home").performClick()
            compose.onNodeWithTag("urlField").performTextClearance()
            compose.onNodeWithTag("urlField").performTextInput("https://www.w3schools.com/html/mov_bbb.mp4")
            compose.onNodeWithTag("grabBtn").performClick()
            compose.waitUntil(150_000) { exists("downloadBtn") }
            compose.onNodeWithTag("saveToRow").performScrollTo().performClick()
            // Android's folder picker: "Use this folder", then "Allow"
            val use = ui.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("(?i)use this folder"))), 20_000)
            use!!.click()
            val allow = ui.wait(androidx.test.uiautomator.Until.findObject(androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("(?i)allow"))), 10_000)
            allow!!.click()
            compose.waitUntil(20_000) { com.sushant.grabbit.data.Folders.recent.value.any { it.name == "GrabbitTest" } }
            compose.waitForIdle()
            compose.onNodeWithTag("saveToRow").performScrollTo()
            shot("folder_1_picked")
            val before = Library.items.value.size
            compose.onNodeWithTag("downloadBtn").performScrollTo().performClick()
            compose.waitUntil(180_000) { Library.items.value.size > before }
            val item = Library.items.value.maxByOrNull { it.time }!!
            val onDisk = java.io.File("/sdcard/Movies/GrabbitTest/${item.fileName}").exists() ||
                runCatching { instr.uiAutomation.executeShellCommand("ls /sdcard/Movies/GrabbitTest").let { pfd ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().readText() } }.getOrDefault("").contains(item.fileName)
            val sticky = com.sushant.grabbit.data.Folders.current.value.contains("GrabbitTest")
            // Next link starts in the same folder
            compose.onNodeWithTag("nav_home").performClick()
            compose.waitForIdle()
            compose.onNodeWithTag("urlField").performTextClearance()
            compose.onNodeWithTag("urlField").performTextInput("https://www.w3schools.com/html/mov_bbb.mp4")
            compose.onNodeWithTag("grabBtn").performClick()
            compose.waitUntil(150_000) { exists("downloadBtn") }
            val shownPath = compose.onNodeWithTag("saveToPath", useUnmergedTree = true).fetchSemanticsNode()
                .config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
            compose.onNodeWithTag("saveToRow").performScrollTo()
            shot("folder_2_sticky")
            val renamed = vm.renameNow(item, "Folder clip") is MainViewModel.RenameResult.Done
            val after = Library.items.value.first { it.id == item.id }
            val deleted = vm.deleteNow(after) is MainViewModel.DeleteResult.Done
            compose.onNodeWithTag("folderChip_default").performScrollTo().performClick()
            compose.waitForIdle()
            log("FOLDER saved=${item.fileName} onDisk=$onDisk sticky=$sticky path=$shownPath renamed=$renamed(${after.fileName}) deleted=$deleted")
            "folder=${onDisk && sticky && shownPath.contains("GrabbitTest") && renamed && deleted}"
        }.getOrElse { log("FOLDER failed: ${it.message}"); shot("folder_fail"); "folder=false" }
            .also { com.sushant.grabbit.data.Folders.pickerStart = null; com.sushant.grabbit.data.Folders.select("") }
    }

    /** Photos: a direct image link, a tweet with a photo, and the Image tab (cover image) of a video. */
    private fun imageFlow(): String {
        val out = mutableListOf<String>()
        fun waitSaved(before: Int) = try { compose.waitUntil(120_000) { Library.items.value.size > before }; true } catch (e: Throwable) { false }
        // 1) Direct image link, through the UI
        out += runCatching {
            compose.onNodeWithTag("nav_home").performClick()
            compose.onNodeWithTag("urlField").performTextClearance()
            compose.onNodeWithTag("urlField").performTextInput("https://www.w3schools.com/html/pic_trulli.jpg")
            compose.onNodeWithTag("grabBtn").performClick()
            compose.waitUntil(60_000) { exists("downloadBtn") || exists("fetchError") }
            shot("img_1_direct_card")
            val before = Library.items.value.size
            compose.onNodeWithTag("downloadBtn").performScrollTo().performClick()
            val ok = waitSaved(before)
            val it = Library.items.value.maxByOrNull { it.time }
            log("IMAGE direct ok=$ok name=${it?.fileName} mime=${it?.mime} size=${it?.size}")
            "imgDirect=${ok && it?.mime == "image/jpeg"}"
        }.getOrElse { log("IMAGE direct failed: ${it.message}"); shot("img_1_fail"); "imgDirect=false" }
        // 2) Tweet with a photo (no login)
        out += runCatching {
            val info = runBlocking { com.sushant.grabbit.engine.Media.fetchInfo(ctx, "https://x.com/TheEllenShow/status/440322224407314432") }
            log("IMAGE tweet gallery=${info.gallery.size} title=${info.title} first=${info.gallery.firstOrNull()?.url}")
            if (info.gallery.isEmpty()) return@runCatching "imgTweet=noPhotos"
            val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
            val before = Library.items.value.size
            compose.activity.runOnUiThread { vm.download(info, com.sushant.grabbit.download.Gallery.Q_ORIGINAL, com.sushant.grabbit.download.DlOpts()) }
            val ok = waitSaved(before)
            val it = Library.items.value.maxByOrNull { it.time }
            log("IMAGE tweet ok=$ok name=${it?.fileName} mime=${it?.mime} size=${it?.size}")
            "imgTweet=${ok && it?.mime?.startsWith("image") == true}"
        }.getOrElse { log("IMAGE tweet failed: ${it.message}"); "imgTweet=false" }
        // 3) Image tab of a video: its cover image, as JPG
        out += runCatching {
            compose.onNodeWithTag("nav_home").performClick()
            compose.onNodeWithTag("urlField").performTextClearance()
            compose.onNodeWithTag("urlField").performTextInput("https://x.com/SpaceX/status/1732824684683784516")
            compose.onNodeWithTag("grabBtn").performClick()
            compose.waitUntil(120_000) { exists("downloadBtn") || exists("fetchError") }
            compose.onNodeWithTag("imageTab").performClick()
            compose.onNodeWithTag("pq_imgjpg").performScrollTo().performClick()
            compose.onNodeWithTag("downloadBtn").performScrollTo()
            shot("img_2_cover_tab")
            // Let the previous "Download started" message go, so it doesn't cover the button
            Thread.sleep(6000)
            compose.onNodeWithTag("downloadBtn").performScrollTo()
            val before = Library.items.value.size
            // Click action directly: a "Download started" message from the step before may still cover the button
            compose.onNodeWithTag("downloadBtn").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
            val ok = waitSaved(before)
            val it = Library.items.value.maxByOrNull { it.time }
            log("IMAGE cover ok=$ok name=${it?.fileName} mime=${it?.mime} size=${it?.size}")
            "imgCover=${ok && it?.mime == "image/jpeg"}"
        }.getOrElse { log("IMAGE cover failed: ${it.message}"); shot("img_2_fail"); "imgCover=false" }
        compose.onNodeWithTag("nav_downloads").performClick()
        compose.waitForIdle()
        shot("img_3_downloads")
        return out.joinToString(" ")
    }

    /** Same X video with Faster downloads off (one connection) and on (aria2c); the worker logs TIMING lines. */
    private fun speedFlow(): String {
        val info = runCatching { runBlocking { com.sushant.grabbit.engine.Media.fetchInfo(ctx, "https://x.com/SpaceX/status/1732824684683784516") } }.getOrNull()
            ?: return "speed=noInfo"
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val out = mutableListOf<String>()
        for (fast in listOf(false, true)) {
            Prefs.fasterDownloads = fast
            val before = Library.items.value.size
            val t = System.currentTimeMillis()
            compose.activity.runOnUiThread { vm.download(info, "h720", com.sushant.grabbit.download.DlOpts()) }
            val ok = try { compose.waitUntil(300_000) { Library.items.value.size > before }; true } catch (e: Throwable) { false }
            val ms = System.currentTimeMillis() - t
            val bytes = Library.items.value.maxByOrNull { it.time }?.size ?: 0
            log("SPEED faster=$fast ok=$ok ms=$ms bytes=$bytes")
            out += "${if (fast) "fast" else "single"}=${if (ok) "${ms}ms" else "fail"}"
        }
        Prefs.fasterDownloads = true
        return out.joinToString(" ")
    }

    /** Pen-icon name before download, then Rename from the Downloads list. */
    private fun renameFlow(): String {
        val item = Library.items.value.firstOrNull { it.fileName.startsWith("My test clip") }
        log("RENAME before-download name=${Library.items.value.firstOrNull()?.fileName}")
        if (item == null) return "renameBefore=false"
        compose.onNodeWithTag("nav_downloads").performClick()
        compose.waitForIdle()
        return runCatching {
            compose.onAllNodesWithContentDescription("More options", useUnmergedTree = true)[0].performClick()
            compose.onNodeWithText("Rename").performClick()
            compose.onNodeWithTag("nameField").performTextClearance()
            compose.onNodeWithTag("nameField").performTextInput("Renamed clip")
            shot("direct_4_rename_dialog")
            compose.onNodeWithTag("renameConfirm").performClick()
            compose.waitUntil(20_000) { Library.items.value.any { it.id == item.id && it.fileName.startsWith("Renamed clip") } }
            val now = Library.items.value.first { it.id == item.id }
            val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
            val exists = vm.fileExists(now)
            log("RENAME after name=${now.fileName} exists=$exists")
            shot("direct_5_renamed")
            "renameBefore=true renameAfter=$exists"
        }.getOrElse { log("RENAME failed: ${it.message}"); shot("direct_rename_fail"); "renameBefore=true renameAfter=false" }
    }

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

    /** Playlist: picker (range, untick, order), 2 at a time, own folder, one card, failure + retry, "Downloaded ✓" / Only new. */
    private fun playlistFlow(): String = runCatching {
        val vm = ViewModelProvider(compose.activity)[MainViewModel::class.java]
        val wm = WorkManager.getInstance(ctx)
        val base = "https://www.w3schools.com/html/"
        fun e(i: Int, url: String, t: String) = com.sushant.grabbit.engine.PlEntry(i, "vid$i", url, t, 10, "", available = true)
        val entries = listOf(
            e(1, base + "mov_bbb.mp4", "Bunny clip"), e(2, base + "movie.mp4", "Bear clip"),
            e(3, base + "mov_bbb.mp4?copy=2", "Bunny again"), e(4, base + "no_such_video_404.mp4", "Broken link"),
            com.sushant.grabbit.engine.PlEntry(5, "vid5", "", "[Private video]", 0, "", available = false),
        )
        val info = com.sushant.grabbit.engine.MediaInfo(
            base + "playlist", "Test Playlist", "W3Schools", "", 0, "W3schools", true, 5,
            listOf(720, 480), emptyMap(), true, entries = entries, playlistId = "PLTEST",
        )
        compose.onNodeWithTag("nav_home").performClick()
        compose.activity.runOnUiThread { vm.show(info) }
        compose.waitUntil(10_000) { exists("plCount") }
        shot("pl_1_picker")
        // Range 1-4, untick 3 and tick it again, reversed order
        compose.onNodeWithTag("rangeField").performScrollTo().performTextInput("1-4")
        compose.onNodeWithTag("rangeApply").performClick()
        val c1 = compose.onNodeWithTag("plCount", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        compose.onNodeWithTag("plCheck_3").performScrollTo().performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        val c2 = compose.onNodeWithTag("plCount", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        compose.onNodeWithTag("plCheck_3").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithTag("order_1").performScrollTo().performClick()
        compose.onNodeWithTag("downloadBtn").performScrollTo()
        shot("pl_2_picker_bottom")
        log("PLAYLIST counts after range=$c1 afterUntick=$c2")
        val savedBefore = Library.items.value.size
        compose.onNodeWithTag("downloadBtn").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick)
        compose.onNodeWithTag("nav_downloads").performClick()
        var maxRunning = 0
        var shotTaken = false
        val t0 = System.currentTimeMillis()
        while (System.currentTimeMillis() - t0 < 300_000) {
            val infos = wm.getWorkInfosByTag(Downloads.TAG).get().filter { Downloads.jobOf(it).opts.gt == "Test Playlist" }
            val running = infos.count { it.state == androidx.work.WorkInfo.State.RUNNING || it.state == androidx.work.WorkInfo.State.ENQUEUED }
            maxRunning = maxOf(maxRunning, running)
            if (!shotTaken && running > 0) { Thread.sleep(1500); compose.waitForIdle(); shot("pl_3_downloading"); shotTaken = true }
            val done = Library.items.value.size - savedBefore
            val failed = infos.count { it.state == androidx.work.WorkInfo.State.FAILED }
            if (done >= 3 && failed >= 1 && running == 0) break
            Thread.sleep(700)
        }
        Thread.sleep(4000)
        compose.waitForIdle()
        shot("pl_4_finished")
        val mine = Library.items.value.filter { it.groupTitle == "Test Playlist" }
        val names = mine.sortedBy { it.groupIndex }.map { it.fileName }
        // Each file sits in Movies/Grabbit/Test Playlist (asked from Android's media store)
        val folders = mine.map { item ->
            runCatching {
                ctx.contentResolver.query(android.net.Uri.parse(item.uri), arrayOf(android.provider.MediaStore.MediaColumns.RELATIVE_PATH), null, null, null)
                    ?.use { c -> if (c.moveToFirst()) c.getString(0) else "" }.orEmpty()
            }.getOrDefault("")
        }
        log("PLAYLIST folders=$folders")
        val inFolder = folders.isNotEmpty() && folders.all { it.trimEnd('/') == "Movies/Grabbit/Test Playlist" }
        val failedNow = wm.getWorkInfosByTag(Downloads.TAG).get().filter { Downloads.jobOf(it).opts.gt == "Test Playlist" && it.state == androidx.work.WorkInfo.State.FAILED }
        val archived = com.sushant.grabbit.data.PlaylistQueue.Archive.has("PLTEST", "vid1") && com.sushant.grabbit.data.PlaylistQueue.Archive.has("PLTEST", "vid2")
        // Retry failed runs the broken one again (it fails again: the link really is broken)
        val before = wm.getWorkInfosByTag(Downloads.TAG).get().size
        if (exists("retryFailed")) compose.onNodeWithTag("retryFailed").performScrollTo().performClick()
        compose.waitUntil(60_000) { wm.getWorkInfosByTag(Downloads.TAG).get().size > before }
        val retried = wm.getWorkInfosByTag(Downloads.TAG).get().size > before
        Thread.sleep(8000)
        // Open the playlist again: downloaded videos show "Downloaded ✓", Only new picks just the rest
        compose.onNodeWithTag("nav_home").performClick()
        compose.activity.runOnUiThread { vm.show(null) }
        compose.waitForIdle()
        compose.activity.runOnUiThread { vm.show(info) }
        compose.waitUntil(10_000) { exists("plCount") }
        val again = compose.onNodeWithTag("plCount", useUnmergedTree = true).fetchSemanticsNode().config[androidx.compose.ui.semantics.SemanticsProperties.Text].joinToString { it.text }
        shot("pl_5_reopened")
        compose.activity.runOnUiThread { vm.show(null) }
        log("PLAYLIST maxRunning=$maxRunning saved=${mine.size} names=$names inFolder=$inFolder failed=${failedNow.size} archived=$archived retried=$retried reopenCount=$again")
        "playlist=${c1.startsWith("4 of") && c2.startsWith("3 of") && maxRunning <= 2 && mine.size == 3 && inFolder &&
            names.firstOrNull()?.startsWith("01 - ") == true && failedNow.size == 1 && archived && retried && again.startsWith("1 of")}"
    }.getOrElse { log("PLAYLIST failed: ${it.message}"); shot("pl_fail"); "playlist=false" }

    /** Data saved by v2.8 (library entries in the old format) must load in 3.0; the new stores start empty. */
    private fun upgradeCheck(): String = runCatching {
        val f = java.io.File(ctx.filesDir, "library.json")
        val backup = if (f.exists()) f.readText() else "[]"
        val v28 = """[{"id":"old1","title":"Old video","site":"YouTube","thumb":"","uri":"content://media/external/video/media/1",""" +
            """"fileName":"Old video.mp4","mime":"video/mp4","size":123,"time":1700000000000,"sourceUrl":"https://youtu.be/x"},""" +
            """{"id":"old2","title":"Old song","site":"YouTube","thumb":"","uri":"content://media/external/audio/media/2",""" +
            """"fileName":"Old song.mp3","mime":"audio/mpeg","size":45,"time":1700000000001,"sourceUrl":"","path":"/sdcard/Music/Grabbit/Old song.mp3"}]"""
        f.writeText(v28)
        Library.init(ctx)
        val items = Library.items.value
        val ok = items.size == 2 && items[0].title == "Old video" && items[0].path == null && items[1].isAudio && items[1].path != null
        f.writeText(backup)
        Library.init(ctx)
        log("UPGRADE v2.8 library loaded=$ok (${items.map { it.fileName }}) restored=${Library.items.value.size}")
        "upgrade=$ok"
    }.getOrElse { log("UPGRADE failed: ${it.message}"); "upgrade=false" }

    /** Fresh install: intro animation, then every setup page; allow notifications and battery through Android's dialogs. */
    private fun onboardingFlow(): String {
        val ui = androidx.test.uiautomator.UiDevice.getInstance(instr)
        val seen = try { compose.waitUntil(8_000) { exists("introAnim") || exists("onboarding") }; true } catch (e: Throwable) { false }
        if (!seen) { log("ONBOARDING not shown"); return "onboarding=notShown" }
        if (exists("introAnim")) shot("ob_00_intro")
        compose.waitUntil(8_000) { exists("onboarding") }
        val pages = mutableListOf<Int>()
        fun allow() {
            // Android's dialogs: find "Allow" by text or by the dialog's positive-button id; retry for slow emulators
            val byText = androidx.test.uiautomator.By.text(java.util.regex.Pattern.compile("(?i)^allow$"))
            val t0 = System.currentTimeMillis()
            while (System.currentTimeMillis() - t0 < 20_000) {
                // Only Android's dialog, never a button inside Grabbit itself
                val b = ui.findObjects(byText).firstOrNull { it.applicationPackage != ctx.packageName }
                    ?: ui.findObject(androidx.test.uiautomator.By.res("android", "button1"))
                    ?: ui.findObject(androidx.test.uiautomator.By.res("com.android.permissioncontroller", "permission_allow_button"))
                if (b != null) { b.click(); break }
                Thread.sleep(500)
            }
            Thread.sleep(1500)
        }
        for (p in 0 until 7) {
            compose.waitUntil(5_000) { exists("obPage$p") }
            compose.waitForIdle()
            val skip = exists("obSkip")
            shot("ob_${p + 1}_page")
            if (p == 2 && exists("obNotif")) { compose.onNodeWithTag("obNotif").performClick(); allow(); shot("ob_3b_notif_done") }
            if (p == 3 && exists("obBattery")) { compose.onNodeWithTag("obBattery").performClick(); allow(); shot("ob_4b_battery_done") }
            pages += p
            log("ONBOARDING page=$p skip=$skip")
            if (p < 6) compose.onNodeWithTag("obNext").performClick() else compose.onNodeWithTag("obStart").performClick()
        }
        compose.waitUntil(8_000) { exists("urlField") }
        val notif = Build.VERSION.SDK_INT < 33 || ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val battery = com.sushant.grabbit.ui.BatteryAccess.allowed(ctx)
        log("ONBOARDING pages=${pages.size} notif=$notif battery=$battery onboarded=${Prefs.onboarded}")
        return "onboarding=${pages.size == 7 && Prefs.onboarded} notif=$notif battery=$battery"
    }

    @Test
    fun endToEnd() {
        val onboarding = onboardingFlow()
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
        val renamed = renameFlow()
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
        compose.onNodeWithTag("searchBtn").performClick()
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
        val speed = speedFlow()
        val pause = pauseFlow(false) + " " + pauseFlow(true)
        val folder = folderFlow()
        val images = imageFlow()
        val upgrade = upgradeCheck()
        val playlist = playlistFlow()

        log("SUMMARY direct=$direct x=$x dailymotion=$dm facebook=$fb youtube=$youtube share=$share threads=$threads $options $renamed $speed $pause $folder $images $onboarding $upgrade $playlist")
        results.forEach { Log.i("GrabbitE2E", "RESULT $it") }
        assertTrue("Downloads failed", direct && x)
        assertTrue("Share sheet download failed", share)
    }
}
