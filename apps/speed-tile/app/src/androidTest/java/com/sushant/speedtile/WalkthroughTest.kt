package com.sushant.speedtile

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.location.Criteria
import android.location.Location
import android.location.LocationManager
import android.location.provider.ProviderProperties
import android.os.Build
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Log
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.FixMethodOrder
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.MethodSorters

@RunWith(AndroidJUnit4::class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class WalkthroughTest {

    @get:Rule val compose = createEmptyComposeRule()

    @get:Rule val perms: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)

    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val ctx: Context get() = instrumentation.targetContext
    private val app get() = ctx.applicationContext as SpeedTileApp

    @Before fun setUp() {
        shell("settings put secure location_mode 3")
        shell("settings put global hide_error_dialogs 1")
        instrumentation.runOnMainSync {
            app.prefs.setTheme(1)
            app.prefs.setDecimals(false)
            app.prefs.setOffOnPanelClose(true)
        }
    }

    @After fun tearDown() {
        shell("cmd statusbar collapse")
    }

    /** Real end-to-end: fake GPS receiver → service → engine → screen + Quick Settings tile. */
    @Test fun a_realGpsEndToEnd() {
        shell("appops set ${ctx.packageName} android:mock_location allow")
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        runCatching { lm.removeTestProvider(LocationManager.GPS_PROVIDER) }
        if (Build.VERSION.SDK_INT >= 31) {
            lm.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
                ProviderProperties.POWER_USAGE_HIGH, ProviderProperties.ACCURACY_FINE)
        } else {
            @Suppress("DEPRECATION")
            lm.addTestProvider(LocationManager.GPS_PROVIDER, false, true, false, false, true, true, true,
                Criteria.POWER_HIGH, Criteria.ACCURACY_FINE)
        }
        lm.setTestProviderEnabled(LocationManager.GPS_PROVIDER, true)
        instrumentation.runOnMainSync { app.engine.resetTrip() }

        try { ActivityScenario.launch(MainActivity::class.java).use {
            waitTag("btn_start")
            compose.onNodeWithTag("btn_start").performClick()
            compose.waitUntil(10_000) { app.engine.state.value.running }

            // Drive north at 60 km/h (16.67 m/s), one fix per second.
            var lat = 25.6
            val pusher = Thread {
                repeat(12) {
                    val l = Location(LocationManager.GPS_PROVIDER).apply {
                        latitude = lat; longitude = 85.1; accuracy = 4f; speed = 16.67f
                        time = System.currentTimeMillis(); elapsedRealtimeNanos = SystemClock.elapsedRealtimeNanos()
                    }
                    runCatching { lm.setTestProviderLocation(LocationManager.GPS_PROVIDER, l) }
                    lat += 0.00015
                    Thread.sleep(700)
                }
            }
            pusher.start()
            compose.waitUntil(15_000) { app.engine.state.value.status == Status.LIVE }
            Thread.sleep(2500)
            shot("01_real_gps_live_app")

            val s = app.engine.state.value
            assertEquals(60f, s.speedKmh!!, 0.6f)
            assertTrue(s.trip.distanceM > 10)

            shell("settings put secure sysui_qs_tiles custom(${ctx.packageName}/.SpeedTileService),wifi,bt,flashlight,dnd,airplane,rotation,location")
            shell("cmd statusbar expand-settings")
            Thread.sleep(3000)
            shot("02_real_gps_live_quick_settings")
            pusher.join()
            // Closing Quick Settings switches speed off (after the short debounce).
            shell("cmd statusbar collapse")
            compose.waitUntil(8_000) { !app.engine.state.value.running }
            assertEquals(StopReason.PANEL_CLOSED, app.engine.state.value.stopReason)
        } } finally {
            runCatching { lm.removeTestProvider(LocationManager.GPS_PROVIDER) }
        }
    }

    @Test fun c_noLauncherIcon() {
        val i = android.content.Intent(android.content.Intent.ACTION_MAIN).addCategory(android.content.Intent.CATEGORY_LAUNCHER)
            .setPackage(ctx.packageName)
        assertTrue(ctx.packageManager.queryIntentActivities(i, 0).isEmpty())
    }

    /** Screens in light and dark, using shown states (no GPS needed). */
    @Test fun b_walkthrough() {
        ActivityScenario.launch(MainActivity::class.java).use {
            show(SpeedState(trip = TripStats()))
            instrumentation.runOnMainSync { app.engine.resetTrip() }
            waitTag("btn_start")
            shot("03_home_off_light")

            show(liveState())
            waitText("64")
            Thread.sleep(800)
            shot("04_home_live_light")

            compose.onNodeWithTag("btn_reset_trip").performClick()
            waitTag("dlg_reset_confirm")
            shot("05_reset_confirm_light")
            compose.onNodeWithText("Cancel").performClick()

            show(SpeedState(running = true, status = Status.WAITING, satsSeen = 7))
            Thread.sleep(500)
            shot("06_home_finding_gps_light")
            show(liveState())

            compose.onNodeWithTag("nav_settings").performClick()
            waitTag("sw_decimals")
            Thread.sleep(1200)
            shot("07_settings_light")
            compose.onNodeWithTag("nav_back").performClick()

            compose.onNodeWithTag("nav_about").performClick()
            waitTag("about_email")
            Thread.sleep(1200)
            shot("08_about_light")
            compose.onNodeWithTag("nav_back").performClick()

            // Dark theme
            instrumentation.runOnMainSync { app.prefs.setTheme(2) }
            waitTag("btn_stop")
            Thread.sleep(800)
            shot("09_home_live_dark")
            show(SpeedState(trip = TripStats(maxKmh = 82.4f, distanceM = 12_840.0, movingMs = 1_040_000)))
            Thread.sleep(700)
            shot("10_home_off_dark")
            show(liveState())
            compose.onNodeWithTag("nav_settings").performClick()
            waitTag("sw_decimals")
            Thread.sleep(1200)
            shot("11_settings_dark")
            compose.onNodeWithTag("nav_back").performClick()
            compose.onNodeWithTag("nav_about").performClick()
            waitTag("about_email")
            Thread.sleep(1200)
            shot("12_about_dark")
            compose.onNodeWithTag("nav_back").performClick()

            // Tile states in Quick Settings
            shell("settings put secure sysui_qs_tiles custom(${ctx.packageName}/.SpeedTileService),wifi,bt,flashlight,dnd,airplane,rotation,location")
            show(SpeedState(running = true, status = Status.LIVE, speedKmh = 118f, accuracyM = 3f))
            shell("cmd statusbar expand-settings")
            Thread.sleep(3000)
            shot("13_tile_118_kmh")
            show(SpeedState(running = true, status = Status.WAITING))
            Thread.sleep(1500)
            shot("14_tile_finding_gps")
            show(SpeedState())
            Thread.sleep(1500)
            shot("15_tile_off")
            shell("cmd statusbar collapse")
            instrumentation.runOnMainSync { app.prefs.setTheme(1) }
        }
    }

    private fun liveState() = SpeedState(
        running = true, status = Status.LIVE, speedKmh = 64.2f, accuracyM = 4f, satsUsed = 11, satsSeen = 23,
        trip = TripStats(maxKmh = 82.4f, distanceM = 12_840.0, movingMs = 1_040_000),
    )

    private fun show(s: SpeedState) = instrumentation.runOnMainSync { app.engine.showForTest(s) }

    private fun shell(cmd: String) {
        val pfd = instrumentation.uiAutomation.executeShellCommand(cmd)
        android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).use { it.readBytes() }
    }

    private fun shot(name: String) {
        compose.waitForIdle()
        val bmp: Bitmap = instrumentation.uiAutomation.takeScreenshot() ?: return
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/AppShots")
        }
        val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
        ctx.contentResolver.openOutputStream(uri)?.use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("Walkthrough", "Saved screenshot $name")
    }

    private fun waitTag(tag: String, timeout: Long = 15_000) =
        compose.waitUntil(timeout) { compose.onAllNodesWithTag(tag, useUnmergedTree = true).fetchSemanticsNodes().isNotEmpty() }

    private fun waitText(text: String, timeout: Long = 15_000) =
        compose.waitUntil(timeout) { compose.onAllNodes(hasText(text, substring = true)).fetchSemanticsNodes().isNotEmpty() }
}
