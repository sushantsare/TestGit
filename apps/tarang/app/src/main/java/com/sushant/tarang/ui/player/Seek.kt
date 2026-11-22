@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.player

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.sushant.tarang.data.formatDuration
import kotlin.math.PI
import kotlin.math.sin

/**
 * Wavy seek bar: the played part is a moving wave while music plays and flattens when paused; drag or tap to seek.
 */
@Composable
fun WavySeekBar(positionMs: Long, durationMs: Long, playing: Boolean, active: Color, track: Color, onSeek: (Long) -> Unit, modifier: Modifier = Modifier) {
    var dragFrac by remember { mutableStateOf<Float?>(null) }
    val frac = dragFrac ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val amp by animateFloatAsState(if (playing && dragFrac == null) 1f else 0f, tween(400), label = "amp")
    val phase by rememberInfiniteTransition(label = "wave").animateFloat(0f, (2 * PI).toFloat(), infiniteRepeatable(tween(1600, easing = LinearEasing)), label = "phase")
    Canvas(
        modifier
            .pointerInput(durationMs) {
                detectTapGestures { o -> if (durationMs > 0) onSeek((o.x / size.width * durationMs).toLong().coerceIn(0, durationMs)) }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o -> dragFrac = (o.x / size.width).coerceIn(0f, 1f) },
                    onDragEnd = { dragFrac?.let { onSeek((it * durationMs).toLong()) }; dragFrac = null },
                    onDragCancel = { dragFrac = null },
                ) { c, d -> c.consume(); dragFrac = ((dragFrac ?: frac) + d / size.width).coerceIn(0f, 1f) }
            }
            .semantics {
                contentDescription = "Song position"
                stateDescription = "${formatDuration((frac * durationMs).toLong())} of ${formatDuration(durationMs)}"
                progressBarRangeInfo = ProgressBarRangeInfo(frac, 0f..1f)
                setProgress { v -> onSeek((v * durationMs).toLong()); true }
            }
    ) {
        val h = size.height; val mid = h / 2; val w = size.width
        val px = w * frac
        val stroke = 4.dp.toPx(); val a = 3.2.dp.toPx() * amp
        val waveLen = 22.dp.toPx()
        if (px > 0f) {
            val path = Path()
            var x = 0f
            path.moveTo(0f, mid + a * sin(phase))
            while (x < px) { x = (x + 2f).coerceAtMost(px); path.lineTo(x, mid + a * sin(x / waveLen * 2 * PI.toFloat() + phase)) }
            drawPath(path, active, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (px + 10.dp.toPx() < w) drawLine(track, Offset(px + 10.dp.toPx(), mid), Offset(w, mid), stroke, StrokeCap.Round)
        drawLine(active, Offset(px, mid - 10.dp.toPx()), Offset(px, mid + 10.dp.toPx()), 4.dp.toPx(), StrokeCap.Round)
    }
}
