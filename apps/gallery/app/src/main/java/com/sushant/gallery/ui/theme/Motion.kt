package com.sushant.gallery.ui.theme

import androidx.compose.animation.core.CubicBezierEasing

object Motion {
    const val short2 = 100
    const val short4 = 200
    const val medium2 = 300
    const val medium4 = 400
    const val long2 = 500
    val emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val emphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val emphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    val standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)
}
