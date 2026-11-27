package com.sushant.grabbit.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.download.Gallery
import com.sushant.grabbit.engine.GalleryItem
import com.sushant.grabbit.engine.MediaInfo
import com.sushant.grabbit.ui.components.AppCard
import com.sushant.grabbit.ui.components.SectionLabel
import com.sushant.grabbit.ui.theme.LocalSizes
import com.sushant.grabbit.ui.theme.LocalSpacing
import com.sushant.grabbit.ui.theme.PillShape

/** Original / Smaller (JPG). */
@Composable
fun PhotoQualityRow(value: String, onChange: (String) -> Unit) {
    SectionLabel("Photo quality")
    Spacer(Modifier.height(LocalSpacing.current.xs))
    val opts = listOf(Gallery.Q_ORIGINAL to "Original", Gallery.Q_JPG to "Smaller (JPG)")
    com.sushant.grabbit.ui.components.ConnectedGroup(
        opts.map { (k, l) -> com.sushant.grabbit.ui.components.SegOption(k, l, tag = "pq_$k") }, value, onChange, height = 48.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHigh, onColor = MaterialTheme.colorScheme.onSurface,
    )
}

/** Photo post: grid of every item (all ticked), select all, photo quality, Save to, download. */
@Composable
fun GalleryCard(info: MediaInfo, framed: Boolean, onDownload: (MediaInfo, String, DlOpts) -> Unit) {
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    val items = info.gallery
    val picked = remember(info) { mutableStateListOf<Int>().apply { addAll(items.indices) } }
    var quality by rememberSaveable(info.url) { mutableStateOf(Prefs.imageFormat) }
    val st = remember(info) { OptsState(info) }
    val photos = items.count { !it.video }
    val videos = items.size - photos

    val body: @Composable () -> Unit = {
        Column(Modifier.padding(horizontal = sp.xs, vertical = sp.xs)) {
            EditableName(st.name, { st.name = it }) { m ->
                Column(m.padding(top = sp.s)) {
                    Text(st.name, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        listOf(info.site, listOfNotNull(
                            if (photos > 0) "$photos photo${if (photos > 1) "s" else ""}" else null,
                            if (videos > 0) "$videos video${if (videos > 1) "s" else ""}" else null,
                        ).joinToString(", ")).filter { it.isNotBlank() }.joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(sp.m))
            if (items.size == 1) {
                Tile(items[0], true, Modifier.fillMaxWidth().aspectRatio(4f / 3f), single = true) {}
            } else {
                items.indices.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                        row.forEach { i ->
                            Tile(items[i], i in picked, Modifier.weight(1f).aspectRatio(1f).testTag("tile_$i")) {
                                if (i in picked) picked.remove(i) else picked.add(i)
                            }
                        }
                        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = sp.xs)) {
                    Text("${picked.size} of ${items.size} selected", Modifier.weight(1f),
                        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val all = picked.size == items.size
                    FilledTonalButton(onClick = {
                        picked.clear(); if (!all) picked.addAll(items.indices)
                    }, modifier = Modifier.testTag("selectAll")) { Text(if (all) "Clear" else "Select all") }
                }
            }
            if (photos > 0) {
                Spacer(Modifier.height(sp.l))
                PhotoQualityRow(quality) { quality = it }
            }
            Spacer(Modifier.height(sp.l))
            SaveToSection(st.folder, { st.folder = it }, audio = false, image = videos == 0)
            Spacer(Modifier.height(sp.l))
            val chosen = picked.sorted().map { items[it] }
            Button(
                onClick = { onDownload(info.copy(gallery = chosen), quality, st.toOpts(false)) },
                enabled = chosen.isNotEmpty(),
                modifier = Modifier.fillMaxWidth().height(sz.button).testTag("downloadBtn"),
                shape = PillShape,
            ) {
                Icon(Icons.Rounded.Download, null)
                Spacer(Modifier.width(sp.s))
                Text(
                    when {
                        chosen.isEmpty() -> "Pick at least one"
                        chosen.size == 1 -> if (chosen[0].video) "Download video" else "Download photo"
                        else -> "Download ${chosen.size} items"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        }
    }
    if (framed) AppCard(padding = sp.m) { body() } else Column { body() }
}

@Composable
private fun Tile(item: GalleryItem, on: Boolean, modifier: Modifier, single: Boolean = false, onClick: () -> Unit) {
    val shape = RoundedCornerShape(if (on || single) 20.dp else 28.dp)
    Box(
        modifier.clip(shape).background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable(enabled = !single, role = Role.Checkbox, onClick = onClick)
            .semantics {
                selected = on
                contentDescription = (if (item.video) "Video" else "Photo") + if (on) ", selected" else ", not selected"
            },
    ) {
        AsyncImage(item.thumb.ifBlank { null }, null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().alpha(if (on || single) 1f else 0.5f))
        if (!single) {
            Box(
                Modifier.align(Alignment.TopEnd).padding(8.dp).size(28.dp).clip(CircleShape)
                    .background(if (on) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.25f))
                    .border(BorderStroke(2.dp, Color.White), CircleShape),
                contentAlignment = Alignment.Center,
            ) { if (on) Icon(Icons.Rounded.Check, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary) }
        }
        if (item.video) Row(
            Modifier.align(Alignment.BottomStart).padding(8.dp).clip(RoundedCornerShape(8.dp))
                .background(Color.Black.copy(alpha = 0.6f)).padding(horizontal = 6.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(12.dp), tint = Color.White)
            if (item.duration > 0) Text(fmtDuration(item.duration), color = Color.White, style = MaterialTheme.typography.labelSmall)
        }
    }
}

/** Image tab of a normal video: save its cover image. */
@Composable
fun CoverImageOptions(info: MediaInfo, quality: String, onQuality: (String) -> Unit, st: OptsState, onDownload: (MediaInfo, String, DlOpts) -> Unit) {
    val sp = LocalSpacing.current
    val sz = LocalSizes.current
    Text("Saves the video's cover image.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(sp.m))
    PhotoQualityRow(quality, onQuality)
    Spacer(Modifier.height(sp.l))
    SaveToSection(st.folder, { st.folder = it }, audio = false, image = true)
    Spacer(Modifier.height(sp.l))
    Button(
        onClick = {
            onDownload(info.copy(gallery = listOf(GalleryItem(info.thumbnail, false, info.thumbnail)), isPlaylist = false), quality, st.toOpts(false))
        },
        modifier = Modifier.fillMaxWidth().height(sz.button).testTag("downloadBtn"),
        shape = PillShape,
    ) {
        Icon(Icons.Rounded.Download, null)
        Spacer(Modifier.width(sp.s))
        Text("Download cover image", style = MaterialTheme.typography.titleMedium)
    }
}
