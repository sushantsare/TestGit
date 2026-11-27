@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.semantics.stateDescription
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
fun PageHeader(title: String, modifier: Modifier = Modifier, onBack: (() -> Unit)? = null, titleTrailing: (@Composable () -> Unit)? = null,
               inlineTitle: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    if (inlineTitle) {
        // big title and the top-bar buttons share one row (no empty band above the title)
        Row(modifier.fillMaxWidth().heightIn(min = 72.dp).padding(start = 20.dp, top = 6.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.foundation.text.BasicText(title, Modifier.weight(1f).semantics { heading() }.testTag("page_title"),
                style = PageTitle.copy(color = MaterialTheme.colorScheme.onSurface), maxLines = 1,
                autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = 18.sp, maxFontSize = PageTitle.fontSize))
            Spacer(Modifier.width(12.dp))
            actions()
        }
        return
    }
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

/** When the tab of this page is tapped while you are already on it, run [toTop] (smoothly scroll back to the top). */
@Composable
fun ScrollTopOnReselect(route: String, toTop: suspend () -> Unit) {
    val nav = com.sushant.tarang.ui.LocalNav.current
    LaunchedEffect(nav) { nav.reselect.collect { if (it == route) toTop() } }
}

/** One icon button in a page's top bar. */
data class TopAction(val icon: ImageVector, val desc: String, val tag: String? = null, val onClick: () -> Unit)

/**
 * Top-bar icon buttons: one icon sits in a filled circle; two or more side by side become a connected button group
 * (one pill split into parts: round outer ends, small inner corners, a small gap). The pressed part squishes a little.
 */
@Composable
fun TopActions(vararg actions: TopAction?, modifier: Modifier = Modifier, container: Color = Color.Unspecified, content: Color = Color.Unspecified) {
    val list = actions.filterNotNull()
    if (list.isEmpty()) return
    val cs = MaterialTheme.colorScheme
    val bgc = if (container == Color.Unspecified) cs.surfaceContainerHigh else container
    val fgc = if (content == Color.Unspecified) cs.onSurface else content
    Row(modifier.padding(end = 8.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
        list.forEachIndexed { i, a ->
            val src = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val pressed by src.collectIsPressedAsState()
            val press by androidx.compose.animation.core.animateFloatAsState(if (pressed) 1f else 0f,
                androidx.compose.animation.core.spring(dampingRatio = 0.6f, stiffness = 600f), label = "press")
            val single = list.size == 1
            val outer = 22.dp
            val inner = 6.dp + (22.dp - 6.dp) * press          // a pressed part rounds out its inner corners
            val start = if (single || i == 0) outer else inner
            val end = if (single || i == list.lastIndex) outer else inner
            Box(
                Modifier.size(if (single) 44.dp else 52.dp, 44.dp)
                    .graphicsLayer { val k = 1f - 0.06f * press; scaleX = k; scaleY = 1f - 0.03f * press }
                    .clip(RoundedCornerShape(topStart = start, bottomStart = start, topEnd = end, bottomEnd = end))
                    .background(bgc)
                    .clickable(interactionSource = src, indication = androidx.compose.material3.ripple(), onClickLabel = a.desc, onClick = a.onClick)
                    .semantics { contentDescription = a.desc }
                    .let { m -> if (a.tag != null) m.testTag(a.tag) else m },
                contentAlignment = Alignment.Center,
            ) { Icon(a.icon, null, tint = fgc, modifier = Modifier.size(22.dp)) }
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
        navigationIcon = { TopActions(TopAction(Icons.AutoMirrored.Rounded.ArrowBack, "Back", "back", onBack), modifier = Modifier.padding(start = 8.dp)) },
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
/** Which song plays now and whether it is playing (given once at the app root, read by every song card). */
val LocalNowPlaying = compositionLocalOf { NowPlaying(null, false) }
data class NowPlaying(val songId: Long?, val isPlaying: Boolean)

/**
 * Corners for one card of a grouped (connected) list: the first card is round on top, the last round at the bottom,
 * the ones between have small corners, and a lone card is round all over.
 */
fun groupCorners(index: Int, count: Int, big: Dp = 24.dp, small: Dp = 4.dp): List<Dp> {
    val top = if (index <= 0) big else small
    val bottom = if (index >= count - 1) big else small
    return listOf(top, top, bottom, bottom)
}

/**
 * One song as a card in a grouped list. The song playing now (wherever it was started from) turns into an
 * accent-coloured pill with a round cover and dancing bars (bars freeze while paused).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: Song, playing: Boolean = false, onClick: () -> Unit, onMore: () -> Unit, modifier: Modifier = Modifier,
    number: Int? = null, meta: String? = null, selected: Boolean = false, onLongClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null, index: Int = 0, count: Int = 1,
) {
    val cs = MaterialTheme.colorScheme
    val now = LocalNowPlaying.current
    val isNow = playing || song.id == now.songId
    val spec = androidx.compose.animation.core.spring<Float>(dampingRatio = 1f, stiffness = 500f)
    val p by androidx.compose.animation.core.animateFloatAsState(if (isNow) 1f else 0f, spec, label = "now")
    val sel by androidx.compose.animation.core.animateFloatAsState(if (selected) 1f else 0f, spec, label = "sel")
    val corners = groupCorners(index, count)
    val pill = 40.dp
    fun mix(a: Dp, t: Float) = a + (pill - a) * t
    val shape = RoundedCornerShape(mix(corners[0], p), mix(corners[1], p), mix(corners[3], p), mix(corners[2], p))
    val base = cs.surfaceContainerLow
    val bg = androidx.compose.ui.graphics.lerp(androidx.compose.ui.graphics.lerp(base, cs.primaryContainer, p), cs.secondaryContainer, sel)
    val fg = androidx.compose.ui.graphics.lerp(cs.onSurface, cs.onPrimaryContainer, p)
    val fg2 = androidx.compose.ui.graphics.lerp(cs.onSurfaceVariant, cs.onPrimaryContainer.copy(alpha = 0.8f), p)
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp).padding(bottom = 3.dp)
            .graphicsLayer { val k = 1f - 0.03f * sel; scaleX = k; scaleY = k }
            .testTag("song_${song.id}")
            .clip(shape).background(bg)
            .then(if (sel > 0f) Modifier.border(1.5.dp, cs.primary.copy(alpha = sel), shape) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick, onClickLabel = "Play")
            .heightIn(min = if (number != null) 62.dp else 70.dp)
            .padding(start = 12.dp, end = 2.dp, top = 8.dp, bottom = 8.dp)
            .semantics { if (isNow) stateDescription = "Playing now" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            selected -> Box(Modifier.size(if (number != null) 30.dp else 52.dp).clip(CircleShape).background(cs.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Check, "Selected", tint = cs.onPrimary)
            }
            number != null -> Box(Modifier.width(30.dp), contentAlignment = Alignment.Center) {
                if (isNow) DancingBars(cs.primary, now.isPlaying, height = 16.dp)
                else Text("$number", style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
            else -> {
                val r = 14.dp + (26.dp - 14.dp) * p
                Cover(song, 52.dp, RoundedCornerShape(r))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 1,
                overflow = if (isNow) TextOverflow.Clip else TextOverflow.Ellipsis,
                modifier = if (isNow) Modifier.basicMarquee(iterations = Int.MAX_VALUE, initialDelayMillis = 1500) else Modifier)
            Text(meta ?: song.artist, style = MaterialTheme.typography.bodyMedium, color = fg2, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) trailing()
        if (isNow && number == null) DancingBars(cs.primary, now.isPlaying, Modifier.padding(horizontal = 6.dp), height = 16.dp)
        IconButton(onClick = onMore, modifier = Modifier.testTag("more_${song.id}")) { Icon(Icons.Rounded.MoreVert, "More options for ${song.title}", tint = fg2) }
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
