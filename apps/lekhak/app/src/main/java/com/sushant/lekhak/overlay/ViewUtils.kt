package com.sushant.lekhak.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue

internal fun Context.dp(v: Float): Int =
    TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, resources.displayMetrics).toInt()

internal fun Context.dp(v: Int): Int = dp(v.toFloat())

internal fun rounded(fill: Int, radiusPx: Float, strokePx: Int = 0, stroke: Int = 0) = GradientDrawable().apply {
    setColor(fill)
    cornerRadius = radiusPx
    if (strokePx > 0) setStroke(strokePx, stroke)
}
