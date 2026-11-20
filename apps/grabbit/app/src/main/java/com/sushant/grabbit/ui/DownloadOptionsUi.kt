package com.sushant.grabbit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Subtitles
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.sushant.grabbit.data.Prefs
import com.sushant.grabbit.download.DlOpts
import com.sushant.grabbit.engine.MediaInfo
import com.sushant.grabbit.engine.SubLang
import com.sushant.grabbit.ui.theme.LocalSpacing
import com.sushant.grabbit.ui.theme.TabularNums

/** Per-video download choices, initialised from Settings defaults. */
@Stable
class OptsState(info: MediaInfo) {
    var sub by mutableStateOf(defaultSub(info))
    var subMode by mutableStateOf(Prefs.subMode)
    val duration = info.duration
    var trim by mutableStateOf(0f..info.duration.toFloat())
    var chapters by mutableStateOf(false)
    var audioFormat by mutableStateOf(Prefs.audioFormat)

    val trimmed get() = duration > 0 && (trim.start > 0.5f || trim.endInclusive < duration - 0.5f)
    /** Share of the video kept (for size estimates). */
    val keptShare get() = if (trimmed && duration > 0) (trim.endInclusive - trim.start) / duration else 1f

    fun toOpts(audio: Boolean) = DlOpts(
        subLang = if (audio) "" else sub?.code.orEmpty(),
        subAuto = sub?.auto == true,
        subMode = subMode,
        trimStart = if (trimmed) trim.start.toLong() else -1,
        trimEnd = if (trimmed) kotlin.math.ceil(trim.endInclusive.toDouble()).toLong() else -1,
        splitChapters = chapters,
        audioFormat = audioFormat,
    )

    companion object {
        fun defaultSub(info: MediaInfo): SubLang? {
            val want = Prefs.subLang
            if (want.isEmpty()) return null
            return info.subtitles.firstOrNull { !it.auto && it.code.substringBefore('-') == want }
                ?: info.subtitles.firstOrNull { it.auto && it.code.substringBefore('-') == want }
        }
    }
}

val AUDIO_FORMAT_LABELS = listOf("mp3" to "MP3", "m4a" to "M4A", "opus" to "Opus", "best" to "Original")

fun fmtClock(sec: Long): String {
    val h = sec / 3600; val m = (sec % 3600) / 60; val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DownloadOptionsRow(info: MediaInfo, audio: Boolean, st: OptsState) {
    val sp = LocalSpacing.current
    var subSheet by remember { mutableStateOf(false) }
    var trimSheet by remember { mutableStateOf(false) }

    if (audio) {
        Text("Format", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(sp.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(sp.s)) {
            AUDIO_FORMAT_LABELS.forEach { (k, l) ->
                val sel = st.audioFormat == k
                FilterChip(
                    selected = sel, onClick = { st.audioFormat = k }, label = { Text(l) },
                    leadingIcon = if (sel) ({ Icon(Icons.Rounded.Check, null, Modifier.size(FilterChipDefaults.IconSize)) }) else null,
                    shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("af_$k"),
                )
            }
        }
        Spacer(Modifier.height(sp.s))
    }
    val showSubs = !audio && info.subtitles.isNotEmpty() && !info.isPlaylist
    val showTrim = info.duration > 0 && !info.isPlaylist
    val showChapters = info.chapters > 1 && !info.isPlaylist
    if (showSubs || showTrim || showChapters) FlowRow(horizontalArrangement = Arrangement.spacedBy(sp.s)) {
        if (showSubs) AssistChip(
            onClick = { subSheet = true },
            label = { Text(st.sub?.let { "Subtitles: ${it.name}" + if (it.auto) " (auto)" else "" } ?: "Subtitles: Off") },
            leadingIcon = { Icon(Icons.Rounded.Subtitles, null, Modifier.size(AssistChipDefaults.IconSize)) },
            shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("subsChip"),
        )
        if (showTrim) AssistChip(
            onClick = { trimSheet = true },
            label = {
                Text(
                    if (st.trimmed) "Trim: ${fmtClock(st.trim.start.toLong())}–${fmtClock(st.trim.endInclusive.toLong())}" else "Trim",
                    style = MaterialTheme.typography.labelLarge.copy(fontFeatureSettings = TabularNums),
                )
            },
            leadingIcon = { Icon(Icons.Rounded.ContentCut, null, Modifier.size(AssistChipDefaults.IconSize)) },
            shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("trimChip"),
        )
        if (showChapters) FilterChip(
            selected = st.chapters, onClick = { st.chapters = !st.chapters },
            label = { Text("Split ${info.chapters} chapters") },
            leadingIcon = {
                Icon(if (st.chapters) Icons.Rounded.Check else Icons.Rounded.ViewAgenda, null, Modifier.size(FilterChipDefaults.IconSize))
            },
            shape = MaterialTheme.shapes.small, modifier = Modifier.testTag("chaptersChip"),
        )
    }
    if (subSheet) SubtitleSheet(info, st) { subSheet = false }
    if (trimSheet) TrimSheet(st) { trimSheet = false }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SubtitleSheet(info: MediaInfo, st: OptsState, onClose: () -> Unit) {
    val sp = LocalSpacing.current
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(bottom = sp.l)) {
            Text("Subtitles", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = sp.xl))
            Spacer(Modifier.height(sp.m))
            if (st.sub != null) {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = sp.xl)) {
                    val modes = listOf("embed" to "Inside video", "file" to "Separate file", "both" to "Both")
                    modes.forEachIndexed { i, (k, l) ->
                        SegmentedButton(selected = st.subMode == k, onClick = { st.subMode = k },
                            shape = SegmentedButtonDefaults.itemShape(i, modes.size), icon = {}) { FitText(l) }
                    }
                }
                Text("Separate files go to Download/Grabbit as .srt", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = sp.xl, vertical = sp.s))
            }
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                SubRow("Off", null, st.sub == null) { st.sub = null; onClose() }
                info.subtitles.forEach { l ->
                    SubRow(l.name, if (l.auto) "Auto-generated" else null, st.sub == l) { st.sub = l }
                }
            }
            if (st.sub != null) Button(onClick = onClose, modifier = Modifier.fillMaxWidth().padding(horizontal = sp.xl)) { Text("Done") }
        }
    }
}

@Composable
private fun SubRow(title: String, sub: String?, selected: Boolean, onClick: () -> Unit) {
    val sp = LocalSpacing.current
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.RadioButton, onClick = onClick).padding(horizontal = sp.xl),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(sp.l))
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrimSheet(st: OptsState, onClose: () -> Unit) {
    val sp = LocalSpacing.current
    val d = st.duration.toFloat()
    ModalBottomSheet(onDismissRequest = onClose, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.navigationBarsPadding().padding(horizontal = sp.xl).padding(bottom = sp.l)) {
            Text("Trim", style = MaterialTheme.typography.titleLarge)
            Text("Keep only the part between the two handles", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(sp.l))
            Row {
                Text("Start ${fmtClock(st.trim.start.toLong())}", Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNums))
                Text("End ${fmtClock(st.trim.endInclusive.toLong())}",
                    style = MaterialTheme.typography.titleMedium.copy(fontFeatureSettings = TabularNums))
            }
            RangeSlider(
                value = st.trim, onValueChange = { st.trim = it }, valueRange = 0f..d,
                modifier = Modifier.testTag("trimSlider"),
            )
            Text("Keeps ${fmtClock((st.trim.endInclusive - st.trim.start).toLong())} of ${fmtClock(st.duration)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(sp.l))
            Row(horizontalArrangement = Arrangement.spacedBy(sp.s)) {
                TextButton(onClick = { st.trim = 0f..d }) { Text("Reset") }
                Spacer(Modifier.weight(1f))
                Button(onClick = onClose) { Text("Done") }
            }
        }
    }
}
