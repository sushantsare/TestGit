package com.sushant.pageone.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

/** Use MaterialTheme.motionScheme (expressive springs) for component motion; these tokens for explicit tweens. */
object Durations {
    const val short2 = 100; const val short4 = 200
    const val medium1 = 250; const val medium2 = 300; const val medium4 = 400
    const val long2 = 500
}

object Easings {
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}

/*
 Transitions (spec):
 - Tabs (Home/Recent/Starred): fade through, medium2.
 - Row/Continue card → Reader: container transform (shared bounds on the thumbnail → page), medium4.
 - Reader chrome (back circle, title, split pill, floating toolbar, pen FAB, page pill): fade + slide 16dp, medium1; tap on page toggles.
 - FAB menu: FloatingActionButtonMenu expressive animation (items stagger 30ms).
 - Setup guide: pages slide + fade; next button morphs Circle → RoundedSquare(24dp) → Cookie → tick; final step scales Home in from 0.92.
 - Predictive back everywhere; sheets close before screens.
 - Respect reduced motion: crossfade only.
*/
