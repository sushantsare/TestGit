package com.sushant.pageone.ui.components

import android.graphics.BitmapFactory
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.Home as OutlinedHome
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.sushant.pageone.ui.theme.GroupShapes
import com.sushant.pageone.ui.theme.Sizes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** Material 3 Expressive polygon family drawn as smooth polar shapes (cookie, clover, sunny, flower, burst, circle). */
enum class ExpressiveKind(val bumps: Int, val depth: Float, val abs: Boolean = false) {
    COOKIE(9, 0.10f), CLOVER(4, 0.18f), SUNNY(8, 0.08f), FLOWER(3, 0.16f, true), BURST(12, 0.12f), CIRCLE(0, 0f)
}

class ExpressiveShape(private val kind: ExpressiveKind, private val rotationDeg: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = minOf(size.width, size.height) / 2f
        val cx = size.width / 2f; val cy = size.height / 2f
        val p = Path()
        val steps = 200
        val rot = rotationDeg / 180f * PI.toFloat()
        for (i in 0..steps) {
            val th = i / steps.toFloat() * 2f * PI.toFloat()
            val wave = if (kind.abs) abs(cos(kind.bumps * th + rot)) * 2f - 1f else cos(kind.bumps * th + rot)
            val rr = if (kind.bumps == 0) r else r * (1f - kind.depth) * (1f + kind.depth * wave)
            val x = cx + rr * cos(th - PI.toFloat() / 2); val y = cy + rr * sin(th - PI.toFloat() / 2)
            if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
        }
        p.close()
        return Outline.Generic(p)
    }
}

private val rotation = listOf(ExpressiveKind.COOKIE, ExpressiveKind.CLOVER, ExpressiveKind.SUNNY, ExpressiveKind.FLOWER, ExpressiveKind.BURST, ExpressiveKind.CIRCLE)
fun kindFor(index: Int) = rotation[((index % rotation.size) + rotation.size) % rotation.size]

@Composable
fun ShapedIcon(
    icon: ImageVector, kind: ExpressiveKind, size: Dp = Sizes.folderShape,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    iconSize: Dp = size * 0.46f, modifier: Modifier = Modifier,
) {
    Box(modifier.size(size).clip(ExpressiveShape(kind)).background(container), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = content, modifier = Modifier.size(iconSize))
    }
}

@Composable
fun LargeTitle(title: String, subtitle: String?, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineLarge, modifier = Modifier.semantics { heading() })
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), content = actions)
    }
}

@Composable
fun TonalIconButton(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = modifier.size(48.dp)) {
        Box(contentAlignment = Alignment.Center) { Icon(icon, description, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun SmallTopBar(
    title: String, subtitle: String? = null, onBack: (() -> Unit)?, modifier: Modifier = Modifier,
    container: Color = Color.Transparent, actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().background(container).statusBarsPadding().padding(start = 4.dp, end = 4.dp, top = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) IconButton(onClick = onBack, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        else Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.semantics { heading() })
            if (!subtitle.isNullOrEmpty()) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        actions()
    }
}

@Composable
fun SearchPill(hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Surface(onClick = onClick, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).height(Sizes.searchPill).testTag("search_pill")) {
        Row(Modifier.padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(14.dp))
            Text(hint, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Connected button group (M3 Expressive): selected item fully rounded, neighbours 8dp inner corners. */
@Composable
fun ConnectedButtons(
    labels: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier,
    height: Dp = Sizes.connectedButtonH, trailingIcon: ImageVector? = null, onTrailing: (() -> Unit)? = null, tagPrefix: String = "seg",
    fill: Boolean = false,
) {
    val total = labels.size + if (trailingIcon != null) 1 else 0
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        labels.forEachIndexed { i, l ->
            val on = i == selected
            val outer = height / 2
            val shape = when {
                on -> RoundedCornerShape(outer)
                i == 0 -> RoundedCornerShape(topStart = outer, bottomStart = outer, topEnd = 8.dp, bottomEnd = 8.dp)
                i == total - 1 -> RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = outer, bottomEnd = outer)
                else -> RoundedCornerShape(8.dp)
            }
            Surface(
                onClick = { onSelect(i) }, shape = shape,
                color = if (on) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = if (on) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = (if (fill) Modifier.weight(1f) else Modifier).heightIn(min = height).testTag("${tagPrefix}_$i"),
            ) {
                Box(Modifier.padding(horizontal = 12.dp), contentAlignment = Alignment.Center) {
                    Text(l, style = MaterialTheme.typography.labelMedium, maxLines = 1)
                }
            }
        }
        if (trailingIcon != null && onTrailing != null) {
            Surface(onClick = onTrailing, shape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp, topEnd = height / 2, bottomEnd = height / 2),
                color = MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(width = height + 2.dp, height = height).testTag("${tagPrefix}_dir")) {
                Box(contentAlignment = Alignment.Center) { Icon(trailingIcon, "Change order", Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant, trailing: @Composable (() -> Unit)? = null) {
    Row(modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 14.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = color, modifier = Modifier.weight(1f).semantics { heading() })
        trailing?.invoke()
    }
}

/** One row of a grouped list: surfaceContainerLow, 24/6dp segmented corners, 3dp gaps between rows. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GroupedRow(
    index: Int, count: Int, onClick: (() -> Unit)?, modifier: Modifier = Modifier, onLongClick: (() -> Unit)? = null,
    selected: Boolean = false, minHeight: Dp = Sizes.rowMin, container: Color? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val bg = container ?: if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerLow
    Row(
        modifier.fillMaxWidth().heightIn(min = minHeight).clip(GroupShapes.of(index, count)).background(bg)
            .then(if (onClick != null) Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, role = Role.Button) else Modifier)
            .padding(start = 10.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp), content = content,
    )
}

@Composable
fun RowTexts(title: String, meta: String?, modifier: Modifier = Modifier, progress: Float? = null) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (!meta.isNullOrEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progress != null && progress > 0f) ProgressLine(progress, Modifier.padding(top = 6.dp))
    }
}

@Composable
fun ProgressLine(fraction: Float, modifier: Modifier = Modifier, height: Dp = Sizes.progressBar) {
    val f by animateFloatAsState(fraction.coerceIn(0f, 1f), label = "p")
    Box(modifier.fillMaxWidth().height(height).clip(RoundedCornerShape(height)).background(MaterialTheme.colorScheme.secondaryContainer)) {
        Box(Modifier.fillMaxWidth(f).fillMaxHeight().clip(RoundedCornerShape(height)).background(MaterialTheme.colorScheme.primary))
    }
}

@Composable
fun FolderRow(index: Int, count: Int, name: String, meta: String, onClick: () -> Unit, modifier: Modifier = Modifier, selected: Boolean = false) {
    GroupedRow(index, count, onClick, modifier.testTag("folder_row"), selected = selected) {
        ShapedIcon(folderIcon(name), kindFor(index))
        RowTexts(name, meta, Modifier.weight(1f))
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun folderIcon(name: String): ImageVector {
    val n = name.lowercase()
    return when {
        "whatsapp" in n -> Icons.Rounded.Chat
        "telegram" in n -> Icons.AutoMirrored.Rounded.Send
        n.startsWith("download") -> Icons.Rounded.Download
        "pyq" in n || "paper" in n || "exam" in n -> Icons.Rounded.Quiz
        "book" in n || "lucent" in n -> Icons.AutoMirrored.Rounded.MenuBook
        "admit" in n || "card" in n -> Icons.Rounded.Badge
        "pageone" in n -> Icons.Rounded.AutoAwesome
        else -> Icons.Rounded.Folder
    }
}

/** Small first-page preview read from the thumbnail cache. */
@Composable
fun PdfThumb(file: File?, version: Int, modifier: Modifier = Modifier, width: Dp = Sizes.pdfThumbW, height: Dp = Sizes.pdfThumbH) {
    val bmp by produceState<android.graphics.Bitmap?>(null, file?.path, version) {
        value = if (file == null) null else withContext(Dispatchers.IO) { runCatching { if (file.exists()) BitmapFactory.decodeFile(file.path) else null }.getOrNull() }
    }
    Box(modifier.size(width, height).shadow(1.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp)).background(Color(0xFFFFFDF8))) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else Column(Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 7.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(5) { i -> Box(Modifier.fillMaxWidth(if (i % 2 == 1) 0.6f else 1f).height(2.5.dp).clip(RoundedCornerShape(2.dp)).background(Color(0xFFD6D6D6))) }
        }
    }
}

enum class EmptyType { FIRST_TIME, CLEARED, NO_RESULTS }

@Composable
fun EmptyState(
    type: EmptyType, title: String, body: String, modifier: Modifier = Modifier,
    icon: ImageVector? = null, kind: ExpressiveKind = ExpressiveKind.SUNNY,
    container: Color = MaterialTheme.colorScheme.primaryContainer, content: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    actionLabel: String? = null, onAction: (() -> Unit)? = null, secondLabel: String? = null, onSecond: (() -> Unit)? = null,
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (icon != null && type != EmptyType.NO_RESULTS) {
            val s = if (type == EmptyType.FIRST_TIME) Sizes.emptyIllustration else 96.dp
            ShapedIcon(icon, kind, s, container, content, iconSize = s * 0.4f)
            Spacer(Modifier.height(24.dp))
        }
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 280.dp))
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (secondLabel != null && onSecond != null) FilledTonalButton(onClick = onSecond) { Text(secondLabel) }
                Button(onClick = onAction, modifier = Modifier.testTag("empty_action")) { Text(actionLabel) }
            }
        }
    }
}

// ---------- settings rows

@Composable
fun SettingSwitch(icon: ImageVector, title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit, index: Int, count: Int, tag: String) {
    GroupedRow(index, count, onClick = { onChange(!checked) }, minHeight = 56.dp, modifier = Modifier.testTag(tag)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag("${tag}_switch"))
    }
}

@Composable
fun SettingNav(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?, index: Int, count: Int, tag: String, chevron: Boolean = true) {
    GroupedRow(index, count, onClick = onClick, minHeight = 56.dp, modifier = Modifier.testTag(tag)) {
        Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onClick != null && chevron) Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Grouped list column with 3dp gaps, 12dp from the edges. */
@Composable
fun Group(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier.fillMaxWidth().padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp), content = content)
}

// ---------- bottom navigation (drawn by each tab screen so nothing jumps during back)

enum class Tab { HOME, RECENT, STARRED }

@Composable
fun BottomNav(current: Tab, onSelect: (Tab) -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(80.dp), horizontalArrangement = Arrangement.SpaceAround, verticalAlignment = Alignment.CenterVertically) {
            NavItem(Tab.HOME, current, Icons.Rounded.Home, Icons.Outlined.OutlinedHome, "Home", onSelect)
            NavItem(Tab.RECENT, current, Icons.Rounded.History, Icons.Rounded.History, "Recent", onSelect)
            NavItem(Tab.STARRED, current, Icons.Rounded.Star, Icons.Rounded.StarBorder, "Starred", onSelect)
        }
    }
}

@Composable
private fun NavItem(tab: Tab, current: Tab, on: ImageVector, off: ImageVector, label: String, onSelect: (Tab) -> Unit) {
    val sel = tab == current
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(role = Role.Tab) { onSelect(tab) }.padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("tab_${tab.name.lowercase()}"),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(64.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (sel) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(if (sel) on else off, null, tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = if (sel) FontWeight.Bold else FontWeight.SemiBold,
            color = if (sel) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Navigation rail for medium/expanded windows. */
@Composable
fun SideRail(current: Tab, onSelect: (Tab) -> Unit, fab: @Composable () -> Unit) {
    Column(Modifier.fillMaxHeight().width(96.dp).statusBarsPadding().padding(top = 20.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        fab()
        Spacer(Modifier.height(12.dp))
        NavItem(Tab.HOME, current, Icons.Rounded.Home, Icons.Outlined.OutlinedHome, "Home", onSelect)
        NavItem(Tab.RECENT, current, Icons.Rounded.History, Icons.Rounded.History, "Recent", onSelect)
        NavItem(Tab.STARRED, current, Icons.Rounded.Star, Icons.Rounded.StarBorder, "Starred", onSelect)
    }
}

/** Skeleton row shown while the first scan runs. */
@Composable
fun SkeletonRow(index: Int, count: Int) {
    val c = MaterialTheme.colorScheme.surfaceContainerHigh
    GroupedRow(index, count, onClick = null) {
        Box(Modifier.size(48.dp).clip(CircleShape).background(c))
        Column(Modifier.weight(1f)) {
            Box(Modifier.fillMaxWidth(0.6f).height(14.dp).clip(RoundedCornerShape(7.dp)).background(c))
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth(0.4f).height(10.dp).clip(RoundedCornerShape(5.dp)).background(c))
        }
    }
}

@Composable
fun OutlinedChoiceBorder(on: Boolean): Modifier =
    if (on) Modifier else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
