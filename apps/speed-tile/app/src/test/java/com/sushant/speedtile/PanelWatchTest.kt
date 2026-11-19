package com.sushant.speedtile

import android.os.Handler
import android.os.Looper
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class)
class PanelWatchTest {
    private var running = false
    private var enabled = true
    private var stops = 0
    private lateinit var w: PanelWatch

    @Before fun setUp() {
        w = PanelWatch(Handler(Looper.getMainLooper()), { enabled }, { running }, { stops++; running = false })
    }

    private fun idle(ms: Long) = shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(ms))

    @Test fun tap_collapse_is_ignored_then_closing_turns_off() {
        w.panelOpened()                 // user opens the panel
        w.startingFromTile()            // taps the tile
        w.panelClosed()                 // our own collapse
        running = true
        idle(5_000)
        assertEquals(0, stops)
        w.panelOpened()                 // pulls down to see speed
        w.panelClosed()                 // closes the panel
        idle(PanelWatch.CLOSE_DEBOUNCE_MS + 100)
        assertEquals(1, stops)
    }

    @Test fun quick_close_and_reopen_is_ignored() {
        running = true
        w.panelOpened()
        w.panelClosed()
        idle(500)
        w.panelOpened()
        idle(5_000)
        assertEquals(0, stops)
    }

    @Test fun not_reopened_within_30s_turns_off() {
        w.startingFromTile()
        running = true
        idle(PanelWatch.REOPEN_WAIT_MS + 100)
        assertEquals(1, stops)
    }

    @Test fun reopened_in_time_keeps_running() {
        w.startingFromTile()
        running = true
        idle(10_000)
        w.panelOpened()
        idle(PanelWatch.REOPEN_WAIT_MS)
        assertEquals(0, stops)
    }

    @Test fun switch_off_means_panel_close_does_nothing() {
        enabled = false
        running = true
        w.panelOpened()
        w.panelClosed()
        w.startingFromTile()
        idle(PanelWatch.REOPEN_WAIT_MS + 5_000)
        assertEquals(0, stops)
    }

    @Test fun started_from_app_keeps_running_until_panel_used() {
        w.startedFromApp()
        running = true
        idle(PanelWatch.REOPEN_WAIT_MS + 5_000)
        assertEquals(0, stops)
        w.panelOpened(); w.panelClosed()
        idle(PanelWatch.CLOSE_DEBOUNCE_MS + 100)
        assertEquals(1, stops)
    }
}
