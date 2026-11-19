package com.sushant.speedtile.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

private const val START = 150f
private const val SWEEP = 240f

/** Round speedometer with the digital speed in the middle. */
@Composable
fun SpeedGauge(speedText: String, speedKmh: Float, maxKmh: Int, active: Boolean, modifier: Modifier = Modifier) {
    val frac by animateFloatAsState(
        targetValue = (speedKmh / maxKmh).coerceIn(0f, 1f),
        animationSpec = tween(600),
        label = "gauge",
    )
    val extra = LocalExtra.current
    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(fontSize = 11.sp, color = muted, fontWeight = FontWeight.Medium)
    val step = when {
        maxKmh <= 120 -> 20
        maxKmh <= 200 -> 20
        else -> 40
    }

    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(1.12f)
            .semantics { contentDescription = "Speed $speedText kilometres per hour" },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().aspectRatio(1.12f)) {
            val stroke = 18.dp.toPx()
            val d = min(size.width, size.height * 1.12f) - stroke - 8.dp.toPx()
            val topLeft = Offset((size.width - d) / 2, stroke / 2 + 4.dp.toPx())
            val arcSize = Size(d, d)
            drawArc(extra.gaugeTrack, START, SWEEP, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            if (frac > 0.002f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        0f to extra.red, 0.083f to extra.red, 0.25f to primary, 0.4167f to primary, 0.75f to primary, 0.88f to extra.amber, 1f to extra.red,
                        center = Offset(topLeft.x + d / 2, topLeft.y + d / 2),
                    ),
                    startAngle = START, sweepAngle = SWEEP * frac, useCenter = false,
                    topLeft = topLeft, size = arcSize, style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            val c = Offset(topLeft.x + d / 2, topLeft.y + d / 2)
            val rTickOut = d / 2 - stroke
            var v = 0
            while (v <= maxKmh) {
                val a = Math.toRadians((START + SWEEP * v / maxKmh).toDouble())
                val len = 10.dp.toPx()
                val o = Offset(c.x + (rTickOut * cos(a)).toFloat(), c.y + (rTickOut * sin(a)).toFloat())
                val i = Offset(c.x + ((rTickOut - len) * cos(a)).toFloat(), c.y + ((rTickOut - len) * sin(a)).toFloat())
                drawLine(muted.copy(alpha = 0.6f), i, o, strokeWidth = 2.dp.toPx(), cap = StrokeCap.Round)
                val label = measurer.measure(v.toString(), labelStyle)
                val rl = rTickOut - len - 14.dp.toPx()
                val lp = Offset(
                    c.x + (rl * cos(a)).toFloat() - label.size.width / 2,
                    c.y + (rl * sin(a)).toFloat() - label.size.height / 2,
                )
                drawText(label, topLeft = lp)
                v += step
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.offset(y = (-6).dp)) {
            Text(
                speedText,
                fontSize = 76.sp,
                fontWeight = FontWeight.Black,
                color = if (active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("speed_value"),
                maxLines = 1,
            )
            Text("km/h", style = MaterialTheme.typography.titleMedium, color = muted, fontWeight = FontWeight.SemiBold)
        }
    }
}
