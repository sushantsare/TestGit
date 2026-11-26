package com.sushant.tarang.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Three little bars that dance next to the song playing now (frozen in place when paused). */
@Composable
fun DancingBars(color: Color, playing: Boolean, modifier: Modifier = Modifier, height: Dp = 18.dp) {
    val tr = rememberInfiniteTransition(label = "bars")
    val a by tr.animateFloat(0.3f, 1f, infiniteRepeatable(tween(420, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "a")
    val b by tr.animateFloat(1f, 0.25f, infiniteRepeatable(tween(520, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "b")
    val c by tr.animateFloat(0.45f, 0.95f, infiniteRepeatable(tween(360, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "c")
    // when paused, the bars stop exactly where they were (frozen), and carry on from there when it plays again
    val last = remember { floatArrayOf(0.45f, 0.8f, 0.35f) }
    Canvas(modifier.size(height * 1.1f, height).semantics { contentDescription = if (playing) "Playing now" else "Paused" }.testTag(if (playing) "dancing_bars" else "dancing_bars_paused")) {
        if (playing) { last[0] = a; last[1] = b; last[2] = c }
        val w = size.width / 5f
        last.forEachIndexed { i, f ->
            val bh = size.height * f
            drawRoundRect(color, Offset(i * 2 * w, size.height - bh), Size(w, bh), CornerRadius(w / 2, w / 2))
        }
    }
}

/** The Pixel "cookie": a circle with soft scalloped edges (n bumps). */
class CookieShape(private val bumps: Int = 9, private val depth: Float = 0.08f, private val rotationDeg: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = minOf(size.width, size.height) / 2f
        val cx = size.width / 2f; val cy = size.height / 2f
        val path = Path()
        val steps = 180
        for (i in 0..steps) {
            val th = i / steps.toFloat() * 2f * PI.toFloat()
            val rr = r * (1f - depth) * (1f + depth * cos(bumps * th + rotationDeg / 180f * PI.toFloat()))
            val x = cx + rr * cos(th); val y = cy + rr * sin(th)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

/** Soft moving highlight used while rows load (instead of a spinner). */
@Composable
fun shimmerBrush(base: Color): Brush {
    val tr = rememberInfiniteTransition(label = "shimmer")
    val x by tr.animateFloat(-300f, 1300f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "x")
    return Brush.linearGradient(listOf(base, base.copy(alpha = base.alpha * 0.45f), base), start = Offset(x, 0f), end = Offset(x + 300f, 120f))
}

/** A placeholder song row with a shimmer. */
@Composable
fun ShimmerRow(base: Color, modifier: Modifier = Modifier) {
    val b = shimmerBrush(base)
    Row(modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp, vertical = 8.dp).testTag("shimmer_row")) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(14.dp)).background(b))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).padding(top = 6.dp)) {
            Box(Modifier.fillMaxWidth(0.62f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(b))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.38f).height(11.dp).clip(RoundedCornerShape(6.dp)).background(b))
        }
    }
}
