package com.sushant.lekhak.overlay

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import com.sushant.lekhak.R

/** 56dp violet gradient circle with a white pen. */
class BubbleView(context: Context) : FrameLayout(context) {
    companion object { const val IDLE_ALPHA = 0.7f }

    init {
        val size = context.dp(56)
        layoutParams = LayoutParams(size, size)
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(0xFF6200EE.toInt(), 0xFF3700B3.toInt())
        ).apply {
            shape = GradientDrawable.OVAL
            setStroke(context.dp(2), 0xFFFFFFFF.toInt())
        }
        elevation = context.dp(8).toFloat()
        alpha = IDLE_ALPHA
        contentDescription = "Lekhak: tap for smart replies, long press for settings"
        addView(ImageView(context).apply {
            setImageResource(R.drawable.ic_pen)
        }, LayoutParams(context.dp(26), context.dp(26), Gravity.CENTER))
    }
}
