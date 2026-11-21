@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.theme.LocalBk

val LocalHide = staticCompositionLocalOf { false }

object Ico {
    fun cat(key: String): ImageVector = when (key) {
        "restaurant" -> Icons.Outlined.Restaurant; "cart" -> Icons.Outlined.ShoppingCart; "car" -> Icons.Outlined.DirectionsCar
        "fuel" -> Icons.Outlined.LocalGasStation; "home" -> Icons.Outlined.Home; "bill" -> Icons.Outlined.Receipt
        "bag" -> Icons.Outlined.ShoppingBag; "health" -> Icons.Outlined.MedicalServices; "school" -> Icons.Outlined.School
        "movie" -> Icons.Outlined.Movie; "bank" -> Icons.Outlined.AccountBalance; "gift" -> Icons.Outlined.CardGiftcard
        "work" -> Icons.Outlined.Work; "percent" -> Icons.Outlined.Percent; "undo" -> Icons.Outlined.Undo
        "pets" -> Icons.Outlined.Pets; "sport" -> Icons.Outlined.FitnessCenter; "phone" -> Icons.Outlined.PhoneAndroid
        "family" -> Icons.Outlined.FamilyRestroom; "travel" -> Icons.Outlined.Flight; "coffee" -> Icons.Outlined.LocalCafe
        "beauty" -> Icons.Outlined.Spa; "savings" -> Icons.Outlined.Savings; "charity" -> Icons.Outlined.VolunteerActivism
        else -> Icons.Outlined.MoreHoriz
    }
    val catKeys = listOf("restaurant", "cart", "car", "fuel", "home", "bill", "bag", "health", "school", "movie", "bank", "gift", "work", "percent", "undo", "pets", "sport", "phone", "family", "travel", "coffee", "beauty", "savings", "charity", "more")

    fun account(type: String): ImageVector = when (type) {
        AccType.CARD -> Icons.Outlined.CreditCard; AccType.CASH -> Icons.Outlined.Payments; AccType.WALLET -> Icons.Outlined.AccountBalanceWallet
        else -> Icons.Outlined.AccountBalance
    }

    fun entry(e: Entry, cat: Category?): ImageVector = when (e.type) {
        EType.CARD_PAYMENT -> Icons.Outlined.CreditCard; EType.CASH_WITHDRAWAL -> Icons.Outlined.LocalAtm; EType.ADJUSTMENT -> Icons.Outlined.Tune
        EType.UDHAR_GAVE, EType.UDHAR_GOT -> Icons.Outlined.Handshake
        else -> cat?.let { cat(it.icon) } ?: if (e.type == EType.INCOME) Icons.Outlined.AccountBalance else Icons.Outlined.MoreHoriz
    }
}

/** Money text that respects "Hide amounts". */
@Composable
fun MoneyText(paise: Long, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.titleSmall, color: Color = Color.Unspecified, sign: Boolean = false, compact: Boolean = false) {
    val hide = LocalHide.current
    val text = if (hide) "₹ ••••" else if (compact) Money.compact(paise) else Money.fmt(paise, sign = sign)
    Text(text, modifier = modifier.semantics { contentDescription = if (hide) "Amount hidden" else Money.fmt(paise, sign).replace("₹", "rupees ").replace("−", "minus ") },
        style = style, color = color, maxLines = 1, softWrap = false)
}

@Composable
fun BkCard(modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, color: Color = LocalBk.current.card, padding: PaddingValues = PaddingValues(0.dp), content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.large
    val m = modifier.clip(shape).background(color).border(1.dp, LocalBk.current.cardBorder, shape)
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(padding)
    Column(m, content = content)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) TextButton(onClick = onAction, modifier = Modifier.heightIn(min = 36.dp)) { Text(action, style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
fun SectionTitle(text: String, action: String? = null, onAction: (() -> Unit)? = null) {
    Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.ExtraBold, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null && onAction != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun IconBadge(icon: ImageVector, bg: Color, fg: Color, size: Dp = 40.dp, round: Boolean = false, iconSize: Dp = 21.dp) {
    Box(Modifier.size(size).clip(if (round) CircleShape else RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = fg, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun Initials(name: String, size: Dp = 40.dp) {
    val bk = LocalBk.current
    val (bg, fg) = bk.cat[(name.hashCode() and 0x7fffffff) % bk.cat.size]
    Box(Modifier.size(size).clip(CircleShape).background(bg), contentAlignment = Alignment.Center) {
        Text(name.split(' ').filter { it.isNotBlank() }.take(2).joinToString("") { it.take(1).uppercase() }.ifEmpty { "?" }, color = fg, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun ListRow(
    title: String, subtitle: String? = null, modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, onClick: (() -> Unit)? = null,
) {
    Row(
        modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .heightIn(min = 56.dp).padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(12.dp)) }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!subtitle.isNullOrBlank()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) { Spacer(Modifier.width(8.dp)); trailing() }
    }
}

@Composable
fun Chev() = Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)

@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, action: String? = null, onAction: (() -> Unit)? = null, secondary: String? = null, onSecondary: (() -> Unit)? = null, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(112.dp).clip(RoundedCornerShape(36.dp)).background(MaterialTheme.colorScheme.secondaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        if (action != null && onAction != null) { Spacer(Modifier.height(24.dp)); Button(onClick = onAction, modifier = Modifier.testTag("empty_action")) { Text(action) } }
        if (secondary != null && onSecondary != null) { Spacer(Modifier.height(4.dp)); TextButton(onClick = onSecondary) { Text(secondary) } }
    }
}

@Composable
fun Bar(fraction: Float, color: Color, modifier: Modifier = Modifier, height: Dp = 8.dp) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "bar")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(MaterialTheme.colorScheme.surfaceVariant)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(height)).background(color))
    }
}

@Composable
fun StepBar(step: Int, total: Int = 3) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(total) { i -> Box(Modifier.weight(1f).height(4.dp).clip(RoundedCornerShape(4.dp)).background(if (i < step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)) }
    }
}

@Composable
fun Pill(text: String, selected: Boolean, onClick: () -> Unit, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    FilterChip(selected = selected, onClick = onClick, label = { Text(text, maxLines = 1) }, modifier = modifier,
        leadingIcon = icon?.let { { Icon(it, null, Modifier.size(18.dp)) } },
        colors = FilterChipDefaults.filterChipColors(
            containerColor = LocalBk.current.card, selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer, selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(enabled = true, selected = selected, borderColor = LocalBk.current.cardBorder, selectedBorderColor = MaterialTheme.colorScheme.secondaryContainer),
    )
}

/** Two equal-width segments, no tick (design rule). */
@Composable
fun Segments(options: List<String>, selected: Int, onSelect: (Int) -> Unit, colors: List<Color>? = null, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(4.dp)) {
        options.forEachIndexed { i, o ->
            val sel = i == selected
            Box(
                Modifier.weight(1f).heightIn(min = 44.dp).clip(RoundedCornerShape(9.dp))
                    .background(if (sel) LocalBk.current.card else Color.Transparent).clickable { onSelect(i) }.testTag("seg_$o"),
                contentAlignment = Alignment.Center,
            ) {
                Text(o, style = MaterialTheme.typography.labelLarge, color = if (sel) colors?.getOrNull(i) ?: MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
fun SettingSwitch(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, tag: String = title) {
    ListRow(title, subtitle, Modifier.testTag("sw_$tag"), leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
        trailing = { Switch(checked = checked, onCheckedChange = onChange) }, onClick = { onChange(!checked) })
}

@Composable
fun SettingNav(icon: ImageVector, title: String, subtitle: String? = null, onClick: () -> Unit) {
    ListRow(title, subtitle, Modifier.testTag("nav_$title"), leading = { Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }, trailing = { Chev() }, onClick = onClick)
}

@Composable
fun ConfirmDialog(title: String, text: String, confirm: String, onConfirm: () -> Unit, onDismiss: () -> Unit, danger: Boolean = true) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = { Text(text) },
        confirmButton = { TextButton(onClick = { onConfirm(); onDismiss() }, modifier = Modifier.testTag("confirm")) { Text(confirm, color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Donut chart drawn with Canvas. */
@Composable
fun Donut(parts: List<Pair<Long, Color>>, modifier: Modifier = Modifier, stroke: Dp = 22.dp, center: @Composable () -> Unit = {}) {
    val total = parts.sumOf { it.first }.toFloat()
    val track = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val sw = stroke.toPx()
            val s = Size(size.minDimension - sw, size.minDimension - sw)
            val tl = Offset((size.width - s.width) / 2, (size.height - s.height) / 2)
            drawArc(track, 0f, 360f, false, tl, s, style = Stroke(sw))
            var start = -90f
            if (total > 0) parts.forEach { (v, c) ->
                val sweep = 360f * v / total
                drawArc(c, start, (sweep - 1.2f).coerceAtLeast(0.5f), false, tl, s, style = Stroke(sw, cap = StrokeCap.Butt))
                start += sweep
            }
        }
        center()
    }
}

@Composable
fun Bars(values: List<Pair<Long, Long>>, labels: List<String>, modifier: Modifier = Modifier) {
    val inc = LocalBk.current.income; val exp = LocalBk.current.expense
    val max = (values.maxOfOrNull { maxOf(it.first, it.second) } ?: 1L).coerceAtLeast(1L).toFloat()
    Row(modifier.fillMaxWidth().height(140.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.Bottom) {
        values.forEachIndexed { i, (a, b) ->
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.height(110.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Box(Modifier.width(11.dp).fillMaxHeight((a / max).coerceAtLeast(0.02f)).clip(RoundedCornerShape(4.dp)).background(inc))
                    Box(Modifier.width(11.dp).fillMaxHeight((b / max).coerceAtLeast(0.02f)).clip(RoundedCornerShape(4.dp)).background(exp))
                }
                Spacer(Modifier.height(6.dp))
                Text(labels[i], style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun Ring(fraction: Float, color: Color, size: Dp = 44.dp) {
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(Modifier.size(size)) {
        val sw = 4.dp.toPx()
        val s = Size(this.size.width - sw, this.size.height - sw)
        drawArc(track, 0f, 360f, false, Offset(sw / 2, sw / 2), s, style = Stroke(sw))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, Offset(sw / 2, sw / 2), s, style = Stroke(sw, cap = StrokeCap.Round))
    }
}

/** Credit card face (in the account's colour). */
@Composable
fun CardFace(a: Account, modifier: Modifier = Modifier, height: Dp = 170.dp) {
    val face = LocalBk.current.cardFaces[a.colorIdx.mod(LocalBk.current.cardFaces.size)]
    Column(
        modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(18.dp)).background(face).padding(18.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(a.name, color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Icon(Icons.Outlined.Contactless, null, tint = Color.White)
        }
        Text("••••  ••••  ••••  " + a.last4.split(',').firstOrNull { it.isNotBlank() }.orEmpty(), color = Color.White, style = MaterialTheme.typography.titleMedium, letterSpacing = 2.sp)
    }
}

@Composable
fun Hideable(content: @Composable () -> Unit) {
    if (LocalHide.current) Box(Modifier.blur(6.dp)) { content() } else content()
}

@Composable
fun TagText(text: String, bg: Color, fg: Color) {
    Text(text, color = fg, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clip(RoundedCornerShape(8.dp)).background(bg).padding(horizontal = 8.dp, vertical = 3.dp))
}

@Composable
fun OutlinedPillButton(text: String, icon: ImageVector?, onClick: () -> Unit, modifier: Modifier = Modifier, tag: String = text) {
    OutlinedButton(onClick = onClick, modifier = modifier.testTag("btn_$tag"), border = BorderStroke(1.dp, LocalBk.current.cardBorder), contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)) {
        if (icon != null) { Icon(icon, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)) }
        Text(text, maxLines = 1)
    }
}

@Suppress("unused") private val unusedTu: TextUnit = 0.sp
