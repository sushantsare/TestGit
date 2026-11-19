package com.sushant.speedtile

import android.os.Handler

/**
 * Switches speed off when the Quick Settings panel closes.
 *
 * Starting GPS needs a visible screen, so a tap on the tile briefly collapses the panel itself.
 * That first close is ignored; speed then waits for the user to pull the panel down again
 * (up to [REOPEN_WAIT_MS]). From then on, closing the panel turns speed off. A close that is
 * followed by a reopen within [CLOSE_DEBOUNCE_MS] (page swipe, shade expanding) is ignored.
 */
class PanelWatch(
    private val handler: Handler,
    private val enabled: () -> Boolean,
    private val isRunning: () -> Boolean,
    private val stop: () -> Unit,
) {
    var armed = false
        private set
    var awaitingReopen = false
        private set

    private val closeStop = Runnable {
        armed = false
        if (enabled() && isRunning()) stop()
    }

    private val reopenTimeout = Runnable {
        val wasWaiting = awaitingReopen
        awaitingReopen = false
        if (wasWaiting && enabled() && isRunning()) stop()
    }

    /** The user tapped the tile to start (call before the panel collapses). */
    fun startingFromTile() {
        armed = false
        awaitingReopen = true
        handler.removeCallbacks(closeStop)
        handler.removeCallbacks(reopenTimeout)
        handler.postDelayed(reopenTimeout, REOPEN_WAIT_MS)
    }

    /** Started from the app screen: nothing to wait for. */
    fun startedFromApp() {
        awaitingReopen = false
        armed = false
        handler.removeCallbacks(reopenTimeout)
        handler.removeCallbacks(closeStop)
    }

    /** The tile became visible (panel opened). */
    fun panelOpened() {
        handler.removeCallbacks(closeStop)
        if (awaitingReopen) {
            awaitingReopen = false
            handler.removeCallbacks(reopenTimeout)
        }
        armed = true
    }

    /** The tile stopped being visible (panel closed, page changed, screen off). */
    fun panelClosed() {
        if (!enabled() || awaitingReopen || !armed) return
        handler.removeCallbacks(closeStop)
        handler.postDelayed(closeStop, CLOSE_DEBOUNCE_MS)
    }

    /** Speed went off for any reason. */
    fun reset() {
        armed = false
        awaitingReopen = false
        handler.removeCallbacks(closeStop)
        handler.removeCallbacks(reopenTimeout)
    }

    companion object {
        const val CLOSE_DEBOUNCE_MS = 1_500L
        const val REOPEN_WAIT_MS = 30_000L
    }
}
