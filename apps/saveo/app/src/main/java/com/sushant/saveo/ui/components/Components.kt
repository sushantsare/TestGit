package com.sushant.saveo.ui.components

import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.memory.MemoryCache
import coil.request.ImageRequest
import coil.request.videoFrameMillis
import com.sushant.saveo.R
import com.sushant.saveo.data.Formats
import com.sushant.saveo.data.MediaKind
import com.sushant.saveo.data.Source
import com.sushant.saveo.ui.Media
import com.sushant.saveo.ui.theme.LocalSaveo

enum class SaveState { IDLE, SAVING, SAVED }

/** Thumbnail / full image via Coil. Videos show a frame from 1 s in. */
@Composable
fun MediaImage(uri: Uri, kind: MediaKind, modifier: Modifier = Modifier, sizePx: Int? = null, contentScale: ContentScale = ContentScale.Crop, crossfade: Boolean = true, placeholderSizePx: Int? = null, blurred: Boolean = false) {
    val ctx = LocalContext.current
    val req = remember(uri, kind, sizePx, placeholderSizePx, blurred) {
        ImageRequest.Builder(ctx).data(uri).apply {
            if (sizePx != null) size(sizePx)
            if (kind == MediaKind.VIDEO) { decoderFactory(VideoFrameDecoder.Factory()); videoFrameMillis(1000) }
            crossfade(crossfade)
            memoryCacheKey(uri.toString() + "@" + (sizePx ?: 0) + if (blurred) "b" else "")
            if (blurred) transformations(BlurTransformation())
            // Show the already-decoded grid thumbnail instantly while the big image loads (no blank frame on swipe)
            if (placeholderSizePx != null) placeholderMemoryCacheKey(MemoryCache.Key(uri.toString() + "@" + placeholderSizePx))
        }.build()
    }
    AsyncImage(model = req, contentDescription = null, modifier = modifier, contentScale = contentScale)
}

/** Glossy round save button: download → spinner → green tick (with a small pop). */
@Composable
fun SaveButton(state: SaveState, onClick: () -> Unit, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val ex = LocalSaveo.current
    val pop by animateFloatAsState(if (state == SaveState.SAVED) 1f else 0.92f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "pop")
    val desc = stringResource(when (state) { SaveState.IDLE -> R.string.cd_save; SaveState.SAVING -> R.string.cd_saving; SaveState.SAVED -> R.string.cd_saved })
    val brush = if (state == SaveState.SAVED) Brush.verticalGradient(listOf(Color(0xFF3CCB89), Color(0xFF148654)))
    else Brush.verticalGradient(listOf(Color.White, Color(0xFFE3F2EA)))
    Box(
        modifier
            .size(48.dp) // touch target
            .semantics { contentDescription = desc; role = Role.Button }
            .clickable(enabled = state == SaveState.IDLE, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier.size(size).scale(if (state == SaveState.SAVED) pop else 1f)
                .shadow(5.dp, CircleShape).clip(CircleShape).background(brush),
            contentAlignment = Alignment.Center,
        ) {
            AnimatedContent(state, transitionSpec = { (fadeIn() + scaleIn(initialScale = 0.6f)) togetherWith fadeOut() }, label = "save") { s ->
                when (s) {
                    SaveState.IDLE -> Icon(Icons.Rounded.Download, null, tint = ex.saveBadgeGreen, modifier = Modifier.size(size * 0.55f))
                    SaveState.SAVING -> CircularProgressIndicator(Modifier.size(size * 0.5f), strokeWidth = 2.5.dp, color = ex.saveBadgeGreen)
                    SaveState.SAVED -> Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(size * 0.55f))
                }
            }
        }
    }
}

@Composable
fun DurationBadge(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(8.dp)).background(Color.Black.copy(alpha = 0.5f)).padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(2.dp))
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
fun SourceBadge(source: Source, modifier: Modifier = Modifier) {
    val label = source.shortBadge ?: return
    Box(modifier.size(22.dp).clip(RoundedCornerShape(6.dp)).background(Color.White), contentAlignment = Alignment.Center) {
        Text(label, color = Color(0xFF1B6E48), fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Grid tile used on both tabs. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MediaTile(
    media: Media,
    saveState: SaveState?,
    selecting: Boolean,
    selected: Boolean,
    durationOf: suspend (Uri) -> Long,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
    sizePx: Int = 360,
) {
    val known = media.saved?.durationMs?.takeIf { it > 0 }
    var duration by remember(media.uri) { mutableLongStateOf(known ?: -1L) }
    LaunchedEffect(media.uri) { if (media.kind == MediaKind.VIDEO && known == null) duration = durationOf(media.uri) }
    val durText = if (media.kind == MediaKind.VIDEO && duration > 0) Formats.duration(duration) else null
    val desc = if (media.kind == MediaKind.VIDEO) stringResource(R.string.cd_video_status, durText ?: "") else stringResource(R.string.cd_photo_status)
    val selDesc = if (selecting) stringResource(if (selected) R.string.cd_selected else R.string.cd_not_selected) else null
    val savedDesc = if (saveState == SaveState.SAVED) stringResource(R.string.cd_saved) else null
    val shape = MaterialTheme.shapes.large
    Box(
        modifier
            .aspectRatio(1f)
            .shadow(2.dp, shape)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics(mergeDescendants = false) {
                contentDescription = listOfNotNull(desc, savedDesc).joinToString(", ")
                if (selDesc != null) stateDescription = selDesc
            },
    ) {
        MediaImage(media.uri, media.kind, Modifier.fillMaxSize(), sizePx = sizePx)
        if (selected) Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)).border(3.dp, MaterialTheme.colorScheme.primary, shape))
        Row(Modifier.align(Alignment.TopStart).padding(9.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selecting) {
                Box(
                    Modifier.size(26.dp).clip(CircleShape)
                        .background(if (selected) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.2f))
                        .border(2.5.dp, Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) { if (selected) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp)) }
                Spacer(Modifier.width(6.dp))
            }
            if (durText != null) DurationBadge(durText)
        }
        if (media.saved == null) SourceBadge(media.source, Modifier.align(Alignment.TopEnd).padding(9.dp))
        if (saveState != null && !selecting) SaveButton(saveState, onSave, Modifier.align(Alignment.BottomEnd).padding(2.dp))
    }
}

@Composable
fun GlossyButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, icon: ImageVector? = null, enabled: Boolean = true) {
    val ex = LocalSaveo.current
    Row(
        modifier
            .heightIn(min = 52.dp)
            .shadow(if (enabled) 6.dp else 0.dp, CircleShape, ambientColor = Color(0xFF187C51), spotColor = Color(0xFF187C51))
            .clip(CircleShape)
            .background(if (enabled) ex.glossBrush else Brush.verticalGradient(listOf(MaterialTheme.colorScheme.surfaceContainerHigh, MaterialTheme.colorScheme.surfaceContainerHigh)))
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val c = if (enabled) ex.onGloss else MaterialTheme.colorScheme.onSurfaceVariant
        if (icon != null) { Icon(icon, null, tint = c, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)) }
        Text(text, color = c, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** The Saveo app icon (gradient square + glass card + save badge). */
@Composable
fun AppIcon(size: Dp, modifier: Modifier = Modifier) {
    val corner = size * 0.26f
    Box(
        modifier.size(size).shadow(10.dp, RoundedCornerShape(corner), ambientColor = Color(0xFF187C51), spotColor = Color(0xFF187C51))
            .clip(RoundedCornerShape(corner))
            .background(Brush.linearGradient(listOf(Color(0xFF2FBF7F), Color(0xFF0F6B47)))),
        contentAlignment = Alignment.Center,
    ) {
        Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.size(size * 1.25f))
    }
}

@Composable
fun EmptyState(title: String, body: String, modifier: Modifier = Modifier, illustration: Boolean = true, action: (@Composable () -> Unit)? = null) {
    Column(modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        if (illustration) { EmptyIllustration(); Spacer(Modifier.height(24.dp)) }
        Text(title, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.widthIn(max = 300.dp))
        if (action != null) { Spacer(Modifier.height(24.dp)); action() }
    }
}

@Composable
private fun EmptyIllustration() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.size(width = 180.dp, height = 140.dp)) {
        Box(Modifier.padding(start = 18.dp, top = 22.dp).size(76.dp, 96.dp).clip(RoundedCornerShape(18.dp)).background(cs.primaryContainer))
        Surface(
            Modifier.align(Alignment.TopEnd).padding(end = 10.dp, top = 8.dp).size(86.dp, 112.dp),
            shape = RoundedCornerShape(20.dp), color = cs.surfaceContainerLowest, border = androidx.compose.foundation.BorderStroke(2.dp, cs.outlineVariant),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Box(Modifier.size(46.dp).border(4.dp, cs.primary, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.PlayArrow, null, tint = cs.primary, modifier = Modifier.size(26.dp))
                }
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier = modifier.padding(start = 22.dp, end = 22.dp, top = 20.dp, bottom = 8.dp).semantics { heading() },
        style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
}

/** Settings-style list group with rounded card. */
@Composable
fun ListCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Surface(modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column { content() }
    }
}

@Composable
fun ColorIcon(icon: ImageVector, bg: Color, modifier: Modifier = Modifier, tint: Color = Color.White) {
    Box(modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(bg), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun SettingRow(
    icon: ImageVector, iconBg: Color, title: String, subtitle: String? = null,
    checked: Boolean? = null, enabled: Boolean = true, subtitleColor: Color? = null, onClick: () -> Unit,
) {
    val stateText = when (checked) { true -> "On"; false -> "Off"; null -> null }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(enabled = enabled, role = if (checked != null) Role.Switch else Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { if (stateText != null) stateDescription = stateText }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ColorIcon(icon, iconBg)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (enabled) 1f else 0.38f))
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = subtitleColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        if (checked != null) Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        else Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun ListDivider() {
    Box(Modifier.fillMaxWidth().padding(start = 66.dp).height(1.dp).background(MaterialTheme.colorScheme.outlineVariant))
}

/** Converts dp to px for image request sizes. */
@Composable
fun dpToPx(dp: Dp): Int = with(LocalDensity.current) { dp.roundToPx() }

/** Cheap 3-pass box blur on a tiny bitmap (used for the viewer background). */
class BlurTransformation(private val radius: Int = 3) : coil.transform.Transformation {
    override val cacheKey: String = "box-blur-$radius"
    override suspend fun transform(input: android.graphics.Bitmap, size: coil.size.Size): android.graphics.Bitmap {
        val w = input.width; val h = input.height
        val src = input.copy(android.graphics.Bitmap.Config.ARGB_8888, true)
        val px = IntArray(w * h); src.getPixels(px, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)
        repeat(3) {
            boxPass(px, tmp, w, h, radius, horizontal = true)
            boxPass(tmp, px, w, h, radius, horizontal = false)
        }
        src.setPixels(px, 0, w, 0, 0, w, h)
        return src
    }

    private fun boxPass(inp: IntArray, out: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
        val lines = if (horizontal) h else w
        val len = if (horizontal) w else h
        for (l in 0 until lines) {
            for (i in 0 until len) {
                var a = 0; var rr = 0; var g = 0; var b = 0; var n = 0
                for (k in -r..r) {
                    val j = (i + k).coerceIn(0, len - 1)
                    val p = if (horizontal) inp[l * w + j] else inp[j * w + l]
                    a += p ushr 24; rr += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF; n++
                }
                val v = ((a / n) shl 24) or ((rr / n) shl 16) or ((g / n) shl 8) or (b / n)
                if (horizontal) out[l * w + i] = v else out[i * w + l] = v
            }
        }
    }
}
