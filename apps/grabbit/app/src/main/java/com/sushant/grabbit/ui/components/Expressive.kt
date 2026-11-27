@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package com.sushant.grabbit.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sushant.grabbit.ui.FitText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Expressive press: squishes a little while pressed and springs back. */
fun Modifier.pressSquish(source: MutableInteractionSource, to: Float = 0.94f): Modifier = composed {
    val pressed by source.collectIsPressedAsState()
    val s by animateFloatAsState(if (pressed) to else 1f, spring(dampingRatio = 0.45f, stiffness = 700f), label = "squish")
    graphicsLayer { scaleX = s; scaleY = s }
}

data class SegOption(
    val key: String,
    val label: String,
    val icon: ImageVector? = null,
    /** Second line, e.g. a file size. */
    val sub: String = "",
    val tag: String? = null,
)

/**
 * Connected button group (Material 3 Expressive): joined segments, pill outer corners, small inner corners.
 * The selected one fills, grows a little wider and morphs to a pill. With [scroll], items keep a fixed width
 * and the row scrolls sideways (for long lists like qualities).
 */
@Composable
fun ConnectedGroup(
    options: List<SegOption>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 52.dp,
    scroll: Boolean = false,
    itemWidth: Dp = 84.dp,
    selectedColor: Color = MaterialTheme.colorScheme.primary,
    onSelectedColor: Color = MaterialTheme.colorScheme.onPrimary,
    color: Color = MaterialTheme.colorScheme.secondaryContainer,
    onColor: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val row = if (scroll) modifier.horizontalScroll(rememberScrollState()) else modifier.fillMaxWidth()
    Row(row.height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = o.key == selected
            val spring = spring<Dp>(dampingRatio = 0.6f, stiffness = 500f)
            val outer = height / 2
            val inner by animateDpAsState(if (on) outer else 8.dp, spring, label = "corner")
            val first = i == 0
            val last = i == options.lastIndex
            val shape = RoundedCornerShape(
                topStart = if (first) outer else inner, bottomStart = if (first) outer else inner,
                topEnd = if (last) outer else inner, bottomEnd = if (last) outer else inner,
            )
            val source = remember { MutableInteractionSource() }
            val sizeMod = if (scroll) {
                val w by animateDpAsState(if (on) itemWidth * 1.2f else itemWidth, spring, label = "w")
                Modifier.width(w)
            } else {
                val w by animateFloatAsState(if (on) 1.3f else 1f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "weight")
                Modifier.weight(w)
            }
            Surface(
                onClick = { onSelect(o.key) },
                shape = shape,
                color = if (on) selectedColor else color,
                contentColor = if (on) onSelectedColor else onColor,
                interactionSource = source,
                modifier = sizeMod.fillMaxHeight().pressSquish(source)
                    .semantics { role = Role.RadioButton; this.selected = on }
                    .let { m -> o.tag?.let { m.testTag(it) } ?: m },
            ) {
                Row(Modifier.padding(horizontal = 10.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    if (o.icon != null) {
                        Icon(o.icon, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        FitText(o.label, style = MaterialTheme.typography.labelLarge)
                        if (o.sub.isNotEmpty()) Text(o.sub, style = MaterialTheme.typography.labelSmall, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, color = (if (on) onSelectedColor else onColor).copy(alpha = 0.8f))
                    }
                }
            }
        }
    }
}

/** Split button (Expressive): big action + a small part that opens a menu. The small part rounds into a circle while open. */
@Composable
fun SplitButton(
    label: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    height: Dp = 60.dp,
    mainTag: String = "downloadBtn",
    menuDescription: String = "More options",
    menu: (@Composable (close: () -> Unit) -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    val outer = height / 2
    val inner by animateDpAsState(if (open) outer else 10.dp, spring(dampingRatio = 0.6f, stiffness = 500f), label = "split")
    val turn by animateFloatAsState(if (open) 180f else 0f, spring(dampingRatio = 0.6f, stiffness = 500f), label = "chev")
    Row(modifier.fillMaxWidth().height(height), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        val src = remember { MutableInteractionSource() }
        Surface(
            onClick = onClick, enabled = enabled, interactionSource = src,
            shape = if (menu == null) RoundedCornerShape(outer) else RoundedCornerShape(outer, 10.dp, 10.dp, outer),
            color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
            contentColor = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            modifier = Modifier.weight(1f).fillMaxHeight().pressSquish(src, 0.97f).testTag(mainTag),
        ) {
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) { label() }
        }
        if (menu != null) Box {
            val src2 = remember { MutableInteractionSource() }
            Surface(
                onClick = { open = true }, enabled = enabled, interactionSource = src2,
                shape = RoundedCornerShape(inner, outer, outer, inner),
                color = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(height).pressSquish(src2).testTag("$mainTag-menu")
                    .semantics { role = Role.Button },
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.KeyboardArrowDown, menuDescription, Modifier.graphicsLayer { rotationZ = turn })
                }
            }
            DropdownMenu(expanded = open, onDismissRequest = { open = false }, shape = MaterialTheme.shapes.large) {
                menu { open = false }
            }
        }
    }
}

/** Wavy progress (Expressive). [progress] null = still working with no percentage. */
@Composable
fun WavyProgress(progress: Float?, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    if (progress == null) LinearWavyProgressIndicator(modifier.fillMaxWidth(), color = color)
    else LinearWavyProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = modifier.fillMaxWidth(), color = color)
}

/**
 * Grabbit mark: three ring parts and the down arrow (same shape as the notification icon), in one colour.
 * With [animate], the ring parts close in and the arrow drops in with a small bounce — once.
 */
@Composable
fun GrabbitMark(size: Dp = 32.dp, color: Color = MaterialTheme.colorScheme.onSurface, animate: Boolean = false, modifier: Modifier = Modifier) {
    val spread = remember { Animatable(if (animate) 1f else 0f) }
    val drop = remember { Animatable(if (animate) 1f else 0f) }
    LaunchedEffect(animate) {
        if (!animate) return@LaunchedEffect
        launch { spread.animateTo(0f, spring(dampingRatio = 0.55f, stiffness = 260f)) }
        delay(160)
        drop.animateTo(0f, spring(dampingRatio = 0.38f, stiffness = 420f))
    }
    Canvas(modifier.size(size).semantics { }) {
        val k = this.size.minDimension / 24f
        val c = Offset(12f * k, 12f * k)
        val r = 9.4f * k
        val ring = Stroke(width = 2.8f * k, cap = StrokeCap.Round)
        fun ang(x: Float, y: Float) = Math.toDegrees(atan2((y - 12f).toDouble(), (x - 12f).toDouble())).toFloat()
        val arcs = listOf(
            floatArrayOf(13.65f, 2.76f, 21.24f, 10.35f),
            floatArrayOf(20.7f, 15.37f, 8.63f, 20.77f),
            floatArrayOf(4.52f, 17.68f, 8.63f, 3.23f),
        )
        val s = spread.value
        val alpha = (1f - s * 0.8f).coerceIn(0f, 1f)
        arcs.forEach { a ->
            val start = ang(a[0], a[1])
            var sweep = ang(a[2], a[3]) - start
            if (sweep < 0) sweep += 360f
            val mid = Math.toRadians((start + sweep / 2).toDouble())
            val push = s * 4f * k
            translate(left = (cos(mid) * push).toFloat(), top = (sin(mid) * push).toFloat()) {
                rotate(degrees = -30f * s, pivot = c) {
                    drawArc(color.copy(alpha = alpha), start, sweep, false,
                        topLeft = Offset(c.x - r, c.y - r), size = androidx.compose.ui.geometry.Size(r * 2, r * 2), style = ring)
                }
            }
        }
        val d = drop.value
        translate(top = -d * 8f * k) {
            val a = (1f - d).coerceIn(0f, 1f)
            val w = 2.7f * k
            drawLine(color.copy(alpha = a), Offset(12f * k, 6.6f * k), Offset(12f * k, 16.4f * k), w, StrokeCap.Round)
            drawLine(color.copy(alpha = a), Offset(8.1f * k, 12.9f * k), Offset(12f * k, 16.8f * k), w, StrokeCap.Round)
            drawLine(color.copy(alpha = a), Offset(15.9f * k, 12.9f * k), Offset(12f * k, 16.8f * k), w, StrokeCap.Round)
        }
    }
}
