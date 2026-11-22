package com.sushant.tarang.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

// Each icon is two four-point shapes in a 1×1 box (top-left, top-right, bottom-right, bottom-left).
// Play = the triangle cut in two; pause = the two bars. Moving every point between them is the morph.
private val PLAY = arrayOf(
    floatArrayOf(0.32f, 0.16f, 0.585f, 0.33f, 0.585f, 0.67f, 0.32f, 0.84f),
    floatArrayOf(0.585f, 0.33f, 0.85f, 0.5f, 0.85f, 0.5f, 0.585f, 0.67f),
)
private val PAUSE = arrayOf(
    floatArrayOf(0.24f, 0.18f, 0.42f, 0.18f, 0.42f, 0.82f, 0.24f, 0.82f),
    floatArrayOf(0.58f, 0.18f, 0.76f, 0.18f, 0.76f, 0.82f, 0.58f, 0.82f),
)

/** Shape point for 0 = play, 1 = pause (pure, unit-tested). */
fun playPausePoint(shape: Int, index: Int, morph: Float): Float = PLAY[shape][index] + (PAUSE[shape][index] - PLAY[shape][index]) * morph

/** Play ↔ pause icon that reshapes smoothly instead of swapping. [morph]: 0 = play, 1 = pause. */
@Composable
fun PlayPauseGlyph(morph: Float, color: Color, size: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val ox = (this.size.width - s) / 2; val oy = (this.size.height - s) / 2
        val round = Stroke(width = s * 0.07f, join = StrokeJoin.Round)   // soft corners like the Material icons
        for (shape in 0..1) {
            val p = Path()
            for (i in 0 until 4) {
                val x = ox + playPausePoint(shape, i * 2, morph) * s
                val y = oy + playPausePoint(shape, i * 2 + 1, morph) * s
                if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
            }
            p.close()
            drawPath(p, color)
            drawPath(p, color, style = round)
        }
    }
}

/** Animated 0..1 values for a play button: icon morph and roundness (round when paused, rounded square when playing). */
@Composable
fun rememberPlayMorph(playing: Boolean): Pair<Float, Float> {
    val morph by animateFloatAsState(if (playing) 1f else 0f, spring(dampingRatio = 0.75f, stiffness = 500f), label = "ppMorph")
    val round by animateFloatAsState(if (playing) 0f else 1f, spring(dampingRatio = 0.6f, stiffness = 420f), label = "ppRound")
    return morph to round
}

/**
 * A complete play/pause button: the icon morphs and the shape changes with it.
 * Paused = fully round; playing = rounded square with [squareRadius] corners.
 */
@Composable
fun PlayPauseButton(
    playing: Boolean, onClick: () -> Unit, container: Color, content: Color,
    width: Dp, height: Dp = width, squareRadius: Dp = 16.dp, iconSize: Dp = 28.dp, tag: String = "play_pause", modifier: Modifier = Modifier,
) {
    val (morph, round) = rememberPlayMorph(playing)
    val radius = squareRadius + (minOf(width, height) / 2 - squareRadius) * round
    Box(
        modifier.size(width, height).clip(RoundedCornerShape(radius)).background(container)
            .clickable(onClickLabel = if (playing) "Pause" else "Play", onClick = onClick)
            .semantics { contentDescription = if (playing) "Pause" else "Play" }.testTag(tag),
        contentAlignment = Alignment.Center,
    ) { PlayPauseGlyph(morph, content, iconSize) }
}

