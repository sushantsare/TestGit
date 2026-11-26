package com.sushant.tarang.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/** Settings → Haptics (small taps on buttons). */
val LocalHapticsOn = compositionLocalOf { true }

/** A small vibration for a button press or a change, only when Haptics is on in Settings. */
@Composable
fun rememberHaptic(): (HapticFeedbackType) -> Unit {
    val h = LocalHapticFeedback.current
    val on = LocalHapticsOn.current
    return remember(h, on) { { type -> if (on) h.performHapticFeedback(type) } }
}
