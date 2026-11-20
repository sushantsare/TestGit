package com.sushant.gallery.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sushant.gallery.R
import com.sushant.gallery.data.Dates
import com.sushant.gallery.data.Formats
import com.sushant.gallery.data.MediaItem
import com.sushant.gallery.ui.theme.LocalFixedColors
import com.sushant.gallery.ui.theme.LocalSpacing

enum class Illus { PHOTOS, BIN, LOCK }

@Composable
fun Illustration(kind: Illus, modifier: Modifier = Modifier) {
    val pc = MaterialTheme.colorScheme.primaryContainer
    val pri = MaterialTheme.colorScheme.primary
    val c3 = MaterialTheme.colorScheme.surfaceContainerHighest
    val sun = LocalFixedColors.current.sun
    Canvas(modifier.size(width = 180.dp, height = 150.dp)) {
        val u = size.width / 180f
        when (kind) {
            Illus.PHOTOS -> {
                rotate(-8f, pivot = Offset(78 * u, 72 * u)) {
                    drawRoundRect(c3, Offset(30 * u, 30 * u), Size(96 * u, 84 * u), CornerRadius(14 * u))
                }
                drawRoundRect(pc, Offset(54 * u, 26 * u), Size(96 * u, 84 * u), CornerRadius(14 * u))
                val p = Path().apply {
                    moveTo(66 * u, 96 * u); lineTo(88 * u, 70 * u); lineTo(102 * u, 86 * u); lineTo(112 * u, 76 * u); lineTo(136 * u, 96 * u); close()
                }
                drawPath(p, pri)
                drawCircle(sun, 9 * u, Offset(128 * u, 48 * u))
            }
            Illus.BIN -> {
                drawRoundRect(pc, Offset(52 * u, 44 * u), Size(76 * u, 90 * u), CornerRadius(14 * u))
                drawRoundRect(pri, Offset(42 * u, 30 * u), Size(96 * u, 14 * u), CornerRadius(7 * u))
                drawRoundRect(pri, Offset(76 * u, 20 * u), Size(28 * u, 12 * u), CornerRadius(6 * u))
                val p = Path().apply { moveTo(74 * u, 88 * u); lineTo(86 * u, 100 * u); lineTo(108 * u, 74 * u) }
                drawPath(p, pri, style = Stroke(8 * u, cap = StrokeCap.Round))
            }
            Illus.LOCK -> {
                drawArc(pri, 180f, 180f, false, Offset(64 * u, 20 * u), Size(52 * u, 52 * u), style = Stroke(10 * u))
                drawLine(pri, Offset(64 * u, 46 * u), Offset(64 * u, 62 * u), 10 * u)
                drawLine(pri, Offset(116 * u, 46 * u), Offset(116 * u, 62 * u), 10 * u)
                drawRoundRect(pc, Offset(44 * u, 60 * u), Size(92 * u, 74 * u), CornerRadius(16 * u))
                drawCircle(pri, 9 * u, Offset(90 * u, 92 * u))
                drawRoundRect(pri, Offset(86 * u, 96 * u), Size(8 * u, 18 * u), CornerRadius(4 * u))
            }
        }
    }
}

@Composable
fun EmptyState(
    illus: Illus?,
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    val sp = LocalSpacing.current
    Column(
        modifier.fillMaxSize().padding(horizontal = sp.xxl).testTag("empty"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(sp.xxxl + sp.xxl))
        if (illus != null) {
            Illustration(illus)
            Spacer(Modifier.height(sp.xl))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(sp.s))
        Text(
            body, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.widthIn(max = 280.dp),
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(sp.xl))
            Button(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val sp = LocalSpacing.current
    Row(
        modifier.fillMaxWidth().heightIn(min = sp.topBar).padding(horizontal = sp.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.cd_back), tint = contentColor)
            }
        }
        Column(Modifier.weight(1f).padding(start = if (onBack == null) sp.m else sp.xs)) {
            Text(
                title, color = contentColor, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = if (onBack == null) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.semantics { heading() },
            )
            if (subtitle != null) {
                Text(subtitle, color = contentColor.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        actions()
    }
}

@Composable
fun SelectionTopBar(count: String, onClose: () -> Unit, actions: @Composable RowScope.() -> Unit) {
    val sp = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer).heightIn(min = sp.topBar).padding(horizontal = sp.xs).testTag("selbar"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.cd_clear_selection), tint = MaterialTheme.colorScheme.onPrimaryContainer) }
        Text(count, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.weight(1f).padding(start = sp.xs))
        actions()
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, trailing: @Composable (() -> Unit)? = null) {
    val sp = LocalSpacing.current
    Row(
        modifier.fillMaxWidth().padding(start = sp.l, end = sp.s, top = sp.l, bottom = sp.s).heightIn(min = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** A square grid cell: thumbnail, video length, favourite heart, selection tick, optional days-left label. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaCell(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    favourite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    daysLeft: Int? = null,
    interactive: Boolean = true,
) {
    val fixed = LocalFixedColors.current
    val scale by animateFloatAsState(if (selected) 0.86f else 1f, label = "cellScale")
    val radius by animateDpAsState(if (selected) 10.dp else 0.dp, label = "cellRadius")
    val isSel = selected
    val desc = buildString {
        append(if (item.isVideo) "Video" else "Photo"); append(", "); append(Dates.fullDate(item.dateTaken))
        if (item.isVideo) { append(", "); append(Formats.duration(item.durationMs)) }
        if (favourite) append(", favourite")
        if (daysLeft != null) append(", deleted forever in $daysLeft days")
    }
    Box(
        modifier
            .aspectRatio(1f)
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .semantics {
                contentDescription = desc; role = Role.Image
                if (selectionMode) this.selected = isSel
            }
            .then(if (interactive) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick) else Modifier)
            .testTag("cell"),
    ) {
        AsyncImage(
            model = thumbModel(item), contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().scale(scale).clip(RoundedCornerShape(radius)),
        )
        if (item.isVideo) {
            Row(
                Modifier.align(Alignment.BottomEnd).padding(4.dp).background(fixed.mediaScrim, RoundedCornerShape(6.dp)).padding(end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.PlayArrow, null, tint = fixed.onMedia, modifier = Modifier.size(14.dp))
                Text(Formats.duration(item.durationMs), color = fixed.onMedia, style = MaterialTheme.typography.labelSmall)
            }
        }
        if (favourite && daysLeft == null) {
            Icon(Icons.Rounded.Favorite, null, tint = fixed.onMedia, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(16.dp))
        }
        if (daysLeft != null) {
            Text(
                pluralText(R.plurals.days_left, daysLeft),
                color = fixed.onMedia, style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, fixed.mediaScrim))).padding(start = 6.dp, top = 12.dp, bottom = 4.dp),
            )
        }
        if (selectionMode) {
            val pri = MaterialTheme.colorScheme.primary
            Box(
                Modifier.align(Alignment.TopStart).padding(6.dp).size(22.dp).clip(CircleShape)
                    .background(if (selected) pri else fixed.mediaScrim.copy(alpha = 0.25f))
                    .border(2.dp, if (selected) pri else fixed.onMedia, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun pluralText(id: Int, n: Int): String = androidx.compose.ui.res.pluralStringResource(id, n, n)

@Composable
fun SettingsGroup(text: String) {
    val sp = LocalSpacing.current
    Text(
        text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = sp.xl, top = sp.xl, bottom = sp.xs).semantics { heading() },
    )
}

@Composable
fun SettingsRow(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    destructive: Boolean = false,
    chevron: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null,
) {
    val sp = LocalSpacing.current
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = sp.xl, vertical = sp.m),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(sp.l),
    ) {
        Icon(icon, null, tint = if (destructive) color else MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = color)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        trailing?.invoke()
        if (chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun SwitchRow(icon: ImageVector, title: String, checked: Boolean, onChange: (Boolean) -> Unit, subtitle: String? = null, tag: String = "") {
    SettingsRow(
        icon, title, subtitle = subtitle, onClick = { onChange(!checked) },
        modifier = Modifier.semantics(mergeDescendants = true) { role = Role.Switch }.testTag(tag),
    ) { Switch(checked = checked, onCheckedChange = null) }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    destructive: Boolean = true,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = {
            Button(
                onClick = onConfirm, modifier = Modifier.testTag("confirm"),
                colors = if (destructive) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
                else ButtonDefaults.buttonColors(),
            ) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Round shortcut used on Albums (Favourites, Videos, Recycle Bin, Hidden). */
@Composable
fun PinButton(icon: ImageVector, label: String, onClick: () -> Unit, badge: Int = 0, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Column(
        modifier.width(84.dp).clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick).padding(vertical = sp.xs),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            Box(
                Modifier.size(sp.pinSize).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
            if (badge > 0) {
                Text(
                    if (badge > 99) "99+" else badge.toString(), style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onError,
                    modifier = Modifier.align(Alignment.TopEnd).background(MaterialTheme.colorScheme.error, RoundedCornerShape(9.dp)).padding(horizontal = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(sp.s))
        Text(label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
fun InfoBanner(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    val sp = LocalSpacing.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = sp.l, vertical = sp.xs)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(12.dp)).padding(horizontal = 14.dp, vertical = sp.m),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
