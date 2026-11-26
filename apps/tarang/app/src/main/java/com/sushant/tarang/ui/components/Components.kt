@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sushant.tarang.data.Covers
import com.sushant.tarang.data.Song
import com.sushant.tarang.ui.theme.PageTitle

val LocalCovers = staticCompositionLocalOf<Covers> { error("no covers") }

/** Album cover from the song file; a soft tonal tile with the first letter when the song has no cover. */
@Composable
fun Cover(song: Song?, size: Dp, shape: Shape, modifier: Modifier = Modifier) {
    val covers = LocalCovers.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val initial = song?.let { covers.cached(it.id, px) }
    val bmp by produceState(initial, song?.id, px) { value = song?.let { covers.load(it, px) } }
    Box(modifier.size(size).clip(shape)) {
        val b = bmp
        if (b != null) Image(b.asImageBitmap(), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        else CoverPlaceholder(song?.title ?: "", Modifier.fillMaxSize())
    }
}

@Composable
fun CoverPlaceholder(title: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val pairs = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer)
    val (bg, fg) = pairs[(title.hashCode() and 0x7fffffff) % pairs.size]
    BoxWithConstraints(modifier.background(Brush.linearGradient(listOf(bg, bg.copy(alpha = 0.75f)))), contentAlignment = Alignment.Center) {
        if (maxWidth >= 40.dp) Text(title.trim().take(1).uppercase().ifEmpty { "♪" }, color = fg, fontWeight = FontWeight.Bold,
            fontSize = (maxWidth.value * 0.38f).sp)
        else Icon(Icons.Rounded.MusicNote, null, tint = fg, modifier = Modifier.size(maxWidth * 0.5f))
    }
}

@Composable
fun PageHeader(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, titleTrailing: (@Composable () -> Unit)? = null, actions: @Composable RowScope.() -> Unit = {}) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (onBack != null) Box(Modifier.padding(start = 8.dp).size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .clickable(onClickLabel = "Back", onClick = onBack).testTag("back"), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Spacer(Modifier.weight(1f)); actions()
        }
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = PageTitle, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f, fill = false).semantics { heading() })
            if (titleTrailing != null) { Spacer(Modifier.width(12.dp)); titleTrailing() }
        }
    }
}

/**
 * The big page title shrinks into a small bar at the top once it has scrolled away (collapsing title).
 * [collapsed] = 0 (big title visible) … 1 (scrolled past it).
 */
@Composable
fun CollapsedTitleBar(title: String, collapsed: Float, modifier: Modifier = Modifier) {
    if (collapsed <= 0.01f) return
    val cs = MaterialTheme.colorScheme
    Box(modifier.fillMaxWidth().graphicsLayer { alpha = collapsed }.background(cs.background).statusBarsPadding().height(56.dp).testTag("collapsed_title"),
        contentAlignment = Alignment.Center) {
        Text(title, style = com.sushant.tarang.ui.theme.RoundTitle, fontSize = 18.sp, color = cs.onSurface,
            modifier = Modifier.graphicsLayer { translationY = (1f - collapsed) * 12.dp.toPx() })
    }
}

/** How far the first item (the big title) of a list has scrolled away, 0..1. */
@Composable
fun rememberCollapse(state: androidx.compose.foundation.lazy.LazyListState): Float {
    val px = with(LocalDensity.current) { 90.dp.toPx() }
    val v by remember(state) { derivedStateOf { if (state.firstVisibleItemIndex > 0) 1f else (state.firstVisibleItemScrollOffset / px).coerceIn(0f, 1f) } }
    return v
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InnerTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack, modifier = Modifier.testTag("back")) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        windowInsets = WindowInsets.statusBars,   // sits below the clock, never under it
    )
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) =
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp).semantics { heading() })

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song, playing: Boolean, onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier,
    number: Int? = null, meta: String? = null, selected: Boolean = false, onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth()
            .background(if (selected) cs.secondaryContainer else Color.Transparent)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = "Play")
            .heightIn(min = if (number != null) 60.dp else 68.dp)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("song_${song.id}"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            selected -> Box(Modifier.size(if (number != null) 28.dp else 52.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Selected", tint = cs.onPrimary)
            }
            number != null -> Box(Modifier.width(28.dp), contentAlignment = Alignment.Center) {
                if (playing) DancingBars(cs.primary, true, height = 16.dp)
                else Text("$number", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
            else -> Cover(song, 52.dp, RoundedCornerShape(14.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (playing) FontWeight.SemiBold else FontWeight.Medium,
                color = if (playing) cs.primary else cs.onSurface, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta ?: song.artist, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
        if (playing && number == null) DancingBars(cs.primary, true, Modifier.padding(horizontal = 6.dp), height = 16.dp)
        IconButton(onClick = onMore, modifier = Modifier.testTag("more_${song.id}")) { Icon(Icons.Rounded.MoreVert, "More options for ${song.title}", tint = cs.onSurfaceVariant) }
    }
}

@Composable
fun ListItemRow(
    title: String, subtitle: String? = null, icon: ImageVector? = null, iconTone: Int = -1,
    modifier: Modifier = Modifier, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null, enabled: Boolean = true,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier.fillMaxWidth().let { if (onClick != null) it.clickable(enabled = enabled, onClick = onClick) else it }
            .heightIn(min = if (subtitle != null) 72.dp else 56.dp).padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            if (iconTone >= 0) {
                val (bg, fg) = listOf(cs.primaryContainer to cs.onPrimaryContainer, cs.secondaryContainer to cs.onSecondaryContainer, cs.tertiaryContainer to cs.onTertiaryContainer)[iconTone % 3]
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(bg), contentAlignment = Alignment.Center) { Icon(icon, null, tint = fg) }
            } else Icon(icon, null, tint = cs.onSurfaceVariant)
            Spacer(Modifier.width(16.dp))
        }
        Column(Modifier.weight(1f).alpha(if (enabled) 1f else 0.38f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = cs.onSurface)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        }
        if (trailing != null) { Spacer(Modifier.width(12.dp)); trailing() }
    }
}


@Composable
fun EmptyState(icon: ImageVector, title: String, body: String, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(120.dp).clip(RoundedCornerShape(40.dp)).background(cs.primaryContainer), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = cs.onPrimaryContainer, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = cs.onSurface)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant, modifier = Modifier.widthIn(max = 300.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), content = actions)
    }
}

@Composable
fun PlayButtons(onPlay: () -> Unit, onShuffle: () -> Unit, modifier: Modifier = Modifier, compactShuffle: Boolean = false, sidePadding: Dp = 16.dp) {
    Row(modifier.fillMaxWidth().padding(horizontal = sidePadding), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(onClick = onPlay, modifier = Modifier.weight(1f).height(52.dp).testTag("play_all")) {
            Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Play all")
        }
        if (compactShuffle) FilledTonalIconButton(onClick = onShuffle, modifier = Modifier.size(52.dp).testTag("shuffle_all")) { Icon(Icons.Rounded.Shuffle, "Shuffle all") }
        else FilledTonalButton(onClick = onShuffle, modifier = Modifier.weight(1f).height(52.dp).testTag("shuffle_all")) {
            Icon(Icons.Rounded.Shuffle, null); Spacer(Modifier.width(8.dp)); Text("Shuffle")
        }
    }
}

@Composable
fun TextInputDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit, label: String = "Name") {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done), modifier = Modifier.fillMaxWidth().testTag("dialog_input"))
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()); onDismiss() }, enabled = text.isNotBlank(), modifier = Modifier.testTag("dialog_ok")) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun ConfirmDialog(title: String, body: String, confirm: String, onDismiss: () -> Unit, onConfirm: () -> Unit, icon: ImageVector? = null, destructive: Boolean = true) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon?.let { { Icon(it, null) } },
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = {
            if (destructive) Button(onClick = { onConfirm(); onDismiss() }, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("confirm")) { Text(confirm) }
            else TextButton(onClick = { onConfirm(); onDismiss() }, modifier = Modifier.testTag("confirm")) { Text(confirm) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun <T> ChoiceDialog(title: String, options: List<T>, selected: T, label: (T) -> String, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss, title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { o ->
                    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).selectable(selected = o == selected, role = Role.RadioButton, onClick = { onPick(o); onDismiss() })
                        .padding(vertical = 12.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(o == selected, null); Spacer(Modifier.width(12.dp)); Text(label(o), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}


@Composable
fun FilterChipRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, o ->
            FilterChip(selected = i == selected, onClick = { onSelect(i) }, label = { Text(o) },
                leadingIcon = if (i == selected) { { Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) } } else null,
                modifier = Modifier.testTag("chip_$o"))
        }
    }
}

/** Connected segmented buttons (Material 3 Expressive): outer ends fully round, tight inner corners. */
@Composable
fun SegmentedRow(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        options.forEachIndexed { i, o ->
            val on = i == selected
            val l = if (i == 0) 22.dp else 8.dp; val r = if (i == options.lastIndex) 22.dp else 8.dp
            Row(Modifier.weight(1f).height(44.dp).clip(RoundedCornerShape(l, r, r, l)).background(if (on) cs.primary else cs.surfaceContainerHigh)
                .clickable { onSelect(i) }.testTag("chip_$o"), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (on) { Icon(Icons.Rounded.Check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)) }
                Text(o, style = MaterialTheme.typography.labelLarge, color = if (on) cs.onPrimary else cs.onSurface, maxLines = 1)
            }
        }
    }
}

fun Modifier.describe(text: String) = semantics { contentDescription = text }
