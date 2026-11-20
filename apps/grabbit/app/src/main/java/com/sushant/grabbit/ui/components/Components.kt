package com.sushant.grabbit.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sushant.grabbit.R
import com.sushant.grabbit.ui.theme.BrandGradient
import com.sushant.grabbit.ui.theme.ImageScrim
import com.sushant.grabbit.ui.theme.LocalSizes
import com.sushant.grabbit.ui.theme.LocalSpacing
import com.sushant.grabbit.ui.theme.OnImage
import com.sushant.grabbit.ui.theme.Overline
import com.sushant.grabbit.ui.theme.PillShape

/** Brand tile: tangerine gradient square with the white Grabbit glyph. */
@Composable
fun BrandMark(size: Dp = LocalSizes.current.logo) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(BrandGradient),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_brand_glyph), null, Modifier.size(size))
    }
}

/** Screen title row used by Downloads and Settings ("large title" style). */
@Composable
fun ScreenTitle(text: String, modifier: Modifier = Modifier, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().heightIn(min = 64.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text, Modifier.weight(1f).semantics { heading() },
            style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface,
        )
        trailing()
    }
}

/** Small uppercase overline above a group ("IN PROGRESS · 1"). */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Text(
        text.uppercase(), modifier.padding(start = sp.xs, top = sp.s, bottom = sp.xxs).semantics { heading() },
        style = Overline, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** Standard container: rounded 24dp, tonal surface, no shadow. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    padding: Dp = LocalSpacing.current.l,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = color, contentColor = contentColor) {
        Column(Modifier.padding(padding), content = content)
    }
}

/** Dark label sitting on a thumbnail (site, duration). */
@Composable
fun ImageTag(text: String? = null, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(ImageScrim).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, Modifier.size(14.dp), tint = OnImage)
        if (text != null) Text(text, style = MaterialTheme.typography.labelMedium, color = OnImage)
    }
}

/** Video thumbnail with a neutral placeholder (never a broken-image glyph). */
@Composable
fun VideoThumb(url: String, audio: Boolean, modifier: Modifier) {
    Box(modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHighest)) {
        Icon(
            if (audio) Icons.Rounded.MusicNote else Icons.Rounded.PlayArrow,
            null, Modifier.align(Alignment.Center).size(22.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (url.isNotBlank()) AsyncImage(url, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        if (audio) ImageTag(icon = Icons.Rounded.MusicNote, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp))
    }
}

/** Shimmering placeholder blocks (loading state). */
@Composable
fun SkeletonBlock(modifier: Modifier, shape: androidx.compose.ui.graphics.Shape = MaterialTheme.shapes.small) {
    val base = MaterialTheme.colorScheme.surfaceContainerHighest
    val hi = MaterialTheme.colorScheme.surfaceContainerLow
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(
        -1f, 2f, infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Restart), label = "x",
    )
    Box(modifier.clip(shape).background(Brush.linearGradient(
        listOf(base, hi, base), start = Offset(x * 600f - 300f, 0f), end = Offset(x * 600f + 300f, 0f),
    )))
}

/** Error card: what happened, what to do, Retry, optional technical details. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ErrorCard(
    title: String,
    message: String,
    raw: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
    extraAction: (@Composable () -> Unit)? = null,
) {
    val sp = LocalSpacing.current
    var showRaw by remember { mutableStateOf(false) }
    AppCard(modifier, color = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.ErrorOutline, null)
            Spacer(Modifier.width(sp.m))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(sp.s))
        Text(message, style = MaterialTheme.typography.bodyMedium)
        if (showRaw && raw.isNotBlank()) {
            Spacer(Modifier.height(sp.s))
            Text(raw.takeLast(1500), style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(sp.m))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(sp.s), verticalArrangement = Arrangement.spacedBy(sp.xs)) {
            Button(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, null, Modifier.size(LocalSizes.current.iconSmall))
                Spacer(Modifier.width(sp.s)); Text("Retry")
            }
            extraAction?.invoke()
            if (raw.isNotBlank()) TextButton(onClick = { showRaw = !showRaw }) {
                Text(if (showRaw) "Hide details" else "Details", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

enum class EmptyType { FIRST_TIME, NO_RESULTS }

@Composable
fun EmptyState(
    type: EmptyType,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val sp = LocalSpacing.current
    Column(modifier.fillMaxWidth().padding(horizontal = sp.xl, vertical = sp.xxl), horizontalAlignment = Alignment.CenterHorizontally) {
        if (type == EmptyType.FIRST_TIME) {
            EmptyDownloadsIllustration(Modifier.width(LocalSizes.current.illustration))
            Spacer(Modifier.height(sp.xl))
        }
        Text(title, style = if (type == EmptyType.FIRST_TIME) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(sp.s))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(sp.xl))
            if (type == EmptyType.FIRST_TIME) Button(onClick = onAction) {
                if (actionIcon != null) { Icon(actionIcon, null, Modifier.size(LocalSizes.current.iconSmall)); Spacer(Modifier.width(sp.s)) }
                Text(actionLabel)
            } else TextButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

/** Empty downloads illustration, drawn with theme colours so it adapts to dark mode. */
@Composable
fun EmptyDownloadsIllustration(modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    Canvas(modifier.aspectRatio(180f / 140f)) {
        val k = size.width / 180f
        fun r(x: Float, y: Float, w: Float, h: Float, rad: Float, col: Color) =
            drawRoundRect(col, Offset(x * k, y * k), Size(w * k, h * k), CornerRadius(rad * k))
        r(20f, 30f, 140f, 92f, 20f, c.surfaceContainerHigh)
        r(40f, 48f, 64f, 36f, 10f, c.primaryContainer)
        drawPath(Path().apply { moveTo(66 * k, 58 * k); lineTo(80 * k, 66 * k); lineTo(66 * k, 74 * k); close() }, c.primary)
        r(40f, 94f, 100f, 8f, 4f, c.surfaceContainerHighest)
        r(112f, 48f, 28f, 8f, 4f, c.surfaceContainerHighest)
        r(112f, 62f, 20f, 8f, 4f, c.surfaceContainerHighest)
        drawCircle(c.primary, 20 * k, Offset(146 * k, 30 * k))
        val st = Stroke(3.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round)
        drawLine(c.onPrimary, Offset(146 * k, 20 * k), Offset(146 * k, 36 * k), 3.5f * k, StrokeCap.Round)
        drawPath(Path().apply { moveTo(139 * k, 29 * k); lineTo(146 * k, 36 * k); lineTo(153 * k, 29 * k) }, c.onPrimary, style = st)
    }
}

/** Group of settings rows inside one rounded container, with inset dividers. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(content = content)
    }
}

@Composable
fun SettingsDivider() = HorizontalDivider(Modifier.padding(start = 56.dp, end = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

/** One settings row: leading icon, title, optional subtitle, trailing slot. Whole row is the tap target. */
@Composable
fun SettingsItem(
    title: String,
    subtitle: String? = null,
    icon: ImageVector? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val sp = LocalSpacing.current
    Row(
        modifier.fillMaxWidth().heightIn(min = 64.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = sp.l, vertical = sp.m),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            leading != null -> { leading(); Spacer(Modifier.width(sp.l)) }
            icon != null -> { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(sp.l)) }
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall.copy(fontSize = MaterialTheme.typography.bodyLarge.fontSize),
                color = MaterialTheme.colorScheme.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(sp.m)); trailing() }
    }
}

@Composable
fun SettingsSwitch(
    title: String, subtitle: String?, icon: ImageVector, checked: Boolean, onChange: (Boolean) -> Unit,
) = SettingsItem(title, subtitle, icon, onClick = { onChange(!checked) }) {
    Switch(checked = checked, onCheckedChange = onChange)
}

/** Initial-letter avatar for account rows. */
@Composable
fun LetterAvatar(letter: String) {
    Box(
        Modifier.size(LocalSizes.current.avatar).clip(PillShape).background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) { Text(letter, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer) }
}

/** Tonal small button used inside rows. */
@Composable
fun RowButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) =
    FilledTonalButton(onClick = onClick, modifier = modifier.testTag("row_$text")) { Text(text) }
