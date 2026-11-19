package com.sushant.gymmanager.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Lets a screen with unsaved work (settings, attendance ticks, a member form) intercept leaving it
 * through the side menu, so it can ask "Discard changes?" first.
 */
class LeaveGuard {
    private var owner: Any? = null
    private var handler by mutableStateOf<((proceed: () -> Unit) -> Unit)?>(null)

    fun set(owner: Any, h: (proceed: () -> Unit) -> Unit) {
        this.owner = owner
        handler = h
    }

    fun clear(owner: Any) {
        if (this.owner === owner) {
            this.owner = null
            handler = null
        }
    }

    /** Runs [proceed] now, or hands it to the screen that has unsaved work. */
    fun attempt(proceed: () -> Unit) {
        val h = handler
        if (h == null) proceed() else h(proceed)
    }
}

val LocalLeaveGuard = staticCompositionLocalOf { LeaveGuard() }

/** While [active], leaving through the menu calls [onAttempt] instead of navigating straight away. */
@Composable
fun RegisterLeaveGuard(active: Boolean, onAttempt: (proceed: () -> Unit) -> Unit) {
    val guard = LocalLeaveGuard.current
    val latest by rememberUpdatedState(onAttempt)
    val token = remember { Any() }
    DisposableEffect(active) {
        if (active) guard.set(token) { p -> latest(p) } else guard.clear(token)
        onDispose { guard.clear(token) }
    }
}

/** One-shot requests from outside a screen (notification, widget, dashboard tiles). */
object NavRequests {
    /** A route to open, e.g. "payments" or "members:EXPIRING". */
    val openRoute = MutableStateFlow<String?>(null)

    /** A filter the Master List should apply when it opens. */
    val memberFilter = MutableStateFlow<String?>(null)

    const val EXTRA_OPEN = "open_route"
}
