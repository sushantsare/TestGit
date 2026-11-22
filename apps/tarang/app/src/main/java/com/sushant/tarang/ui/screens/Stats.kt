@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.screens

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.*
import com.sushant.tarang.ui.LocalDockPadding
import com.sushant.tarang.ui.LocalNav
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.*
import java.util.Calendar

private val PERIODS = listOf("Week", "Month", "Year", "All time")

data class StatsData(
    val totalMs: Long, val prevMs: Long, val plays: Int, val bars: List<Pair<String, Long>>,
    val topSongs: List<Pair<Song, Int>>, val topArtists: List<Pair<String, Long>>, val topHour: Int?, val streak: Int, val songsHeard: Int,
)

/** Pure: turns raw play rows into what the Stats screen shows (unit-tested). */
fun buildStats(rows: List<PlayEntity>, period: StatsPeriod, kind: Int, byId: Map<Long, Song>, now: Long): StatsData {
    val inP = rows.filter { it.startedAt >= period.from }
    val prev = if (kind == 3) emptyList() else rows.filter { it.startedAt >= period.prevFrom && it.startedAt < period.from }
    val starts = period.buckets.map { it.second }
    val bars = period.buckets.mapIndexed { i, (label, start) ->
        val end = starts.getOrNull(i + 1) ?: when (kind) {
            0 -> start + 86_400_000L
            1 -> start + 7 * 86_400_000L
            else -> Calendar.getInstance().apply { timeInMillis = start; add(Calendar.MONTH, 1) }.timeInMillis
        }
        label to inP.filter { it.startedAt in start until end }.sumOf { it.listenedMs }
    }
    val counted = inP.filter { it.counted }
    val topSongs = counted.groupingBy { it.songId }.eachCount().entries.sortedByDescending { it.value }.mapNotNull { e -> byId[e.key]?.let { it to e.value } }.take(5)
    val artistMs = HashMap<String, Long>()
    inP.forEach { p -> byId[p.songId]?.artists?.forEach { a -> artistMs[a] = (artistMs[a] ?: 0) + p.listenedMs } }
    val hours = LongArray(24)
    val cal = Calendar.getInstance()
    inP.forEach { cal.timeInMillis = it.startedAt; hours[cal.get(Calendar.HOUR_OF_DAY)] += it.listenedMs }
    val topHour = hours.indices.maxByOrNull { hours[it] }?.takeIf { hours[it] > 0 }
    val days = rows.filter { it.counted }.map { dayIndex(it.startedAt) }.toSet()
    return StatsData(inP.sumOf { it.listenedMs }, prev.sumOf { it.listenedMs }, counted.size, bars, topSongs,
        artistMs.entries.sortedByDescending { it.value }.take(3).map { it.key to it.value }, topHour, streakDays(days, dayIndex(now)), counted.map { it.songId }.distinct().size)
}

private fun hour12(x: Int): String = (if (x % 12 == 0) 12 else x % 12).toString() + (if (x < 12) " AM" else " PM")
fun hourLabel(h: Int): String = hour12(h) + " – " + hour12((h + 1) % 24)

@Composable
fun StatsScreen() {
    val vm = LocalVm.current
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val lib by vm.library.collectAsState()
    var kind by rememberSaveable { mutableIntStateOf(0) }
    val period = remember(kind) { vm.repo.statsPeriod(kind) }
    val rows by remember(kind) { vm.repo.dao.playsSince(if (kind == 3) 0L else minOf(period.prevFrom, System.currentTimeMillis() - 400L * 86_400_000L)) }.collectAsState(null)
    val data = remember(rows, lib, kind) { rows?.let { buildStats(it, period, kind, lib.byId, System.currentTimeMillis()) } }
    val cs = MaterialTheme.colorScheme

    LazyColumn(Modifier.fillMaxSize().testTag("stats"), contentPadding = PaddingValues(bottom = LocalDockPadding.current)) {
        item {
            PageHeader("Stats", Modifier.statusBarsPadding()) {
                if (data != null && data.totalMs > 0) IconButton(onClick = {
                    val text = buildString {
                        append("My music this ${PERIODS[kind].lowercase()} on Tarang\n")
                        append("Listened: ${formatListen(data.totalMs)} · ${plural(data.plays, "play")}\n")
                        data.topSongs.firstOrNull()?.let { append("Top song: ${it.first.title} — ${it.first.artist}\n") }
                        data.topArtists.firstOrNull()?.let { append("Top artist: ${it.first}\n") }
                        if (data.streak > 1) append("Streak: ${data.streak} days in a row\n")
                    }
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share my stats"))
                }, Modifier.testTag("share_stats")) { Icon(Icons.Rounded.Share, "Share my stats") }
            }
        }
        item { FilterChipRow(PERIODS, kind, { kind = it }) }
        if (data == null) { item { Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }; return@LazyColumn }
        if (data.totalMs == 0L) {
            item { EmptyState(Icons.Rounded.Insights, "No listening yet", "Play music and Tarang counts the real time you listen. A song counts as played after 30 seconds.", Modifier.testTag("stats_empty")) }
            return@LazyColumn
        }
        item {
            Column(Modifier.fillMaxWidth().padding(16.dp).clip(RoundedCornerShape(28.dp)).background(cs.primaryContainer).padding(20.dp)) {
                Text("You listened for", style = MaterialTheme.typography.titleSmall, color = cs.onPrimaryContainer)
                Text(formatListen(data.totalMs), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = cs.onPrimaryContainer, modifier = Modifier.testTag("stats_total"))
                if (kind != 3) {
                    val diff = data.totalMs - data.prevMs
                    val prevName = listOf("last week", "last month", "last year")[kind]
                    Text(when { data.prevMs == 0L -> "Nothing to compare with $prevName"; diff >= 0 -> "${formatListen(diff)} more than $prevName"; else -> "${formatListen(-diff)} less than $prevName" },
                        style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer)
                }
                if (data.bars.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Bars(data.bars, cs.primary, cs.onPrimaryContainer)
                }
            }
        }
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile(Icons.Rounded.LocalFireDepartment, if (data.streak > 0) plural(data.streak, "day") else "—", "Streak", Modifier.weight(1f))
                StatTile(Icons.Rounded.PlayCircle, "%,d".format(data.plays), "Plays", Modifier.weight(1f))
                StatTile(Icons.Rounded.MusicNote, "%,d".format(data.songsHeard), "Songs", Modifier.weight(1f))
            }
        }
        data.topHour?.let { h -> item { ListItemRow("Your favourite time", hourLabel(h), Icons.Rounded.Schedule, iconTone = 1, modifier = Modifier.padding(top = 8.dp)) } }
        if (data.topSongs.isNotEmpty()) {
            item { SectionLabel("Top songs") }
            items(data.topSongs, key = { "ts${it.first.id}" }) { (s, n) ->
                SongRow(s, false, onClick = { vm.player.tapSong(s, data.topSongs.map { it.first }, "Mix", "Top songs") }, onMore = { nav.songMenu(s) }, meta = "${s.artist} · ${plural(n, "play")}")
            }
        }
        if (data.topArtists.isNotEmpty()) {
            item { SectionLabel("Top artists") }
            items(data.topArtists, key = { "ta${it.first}" }) { (a, ms) ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    LetterAvatar(a, 48.dp); Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(a, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatListen(ms), style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                    }
                }
            }
        }
        item { Text("Only time you actually listened is counted. A song counts as a play after 30 seconds.", style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(24.dp)) }
    }
}

@Composable
private fun Bars(bars: List<Pair<String, Long>>, bar: androidx.compose.ui.graphics.Color, label: androidx.compose.ui.graphics.Color) {
    val max = (bars.maxOfOrNull { it.second } ?: 0L).coerceAtLeast(1L)
    Column {
        Canvas(Modifier.fillMaxWidth().height(96.dp).semantics { contentDescription = bars.joinToString { "${it.first}: ${formatListen(it.second)}" } }) {
            val n = bars.size; val slot = size.width / n; val w = (slot * 0.56f).coerceAtMost(28.dp.toPx())
            bars.forEachIndexed { i, (_, v) ->
                val h = if (v == 0L) 4.dp.toPx() else (size.height * v / max).coerceAtLeast(6.dp.toPx())
                drawRoundRect(if (v == 0L) bar.copy(alpha = 0.25f) else bar, Offset(i * slot + (slot - w) / 2, size.height - h), Size(w, h), CornerRadius(w / 2))
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            bars.forEach { (l, _) -> Text(l, Modifier.weight(1f), style = MaterialTheme.typography.labelSmall, color = label, textAlign = TextAlign.Center) }
        }
    }
}

@Composable
private fun StatTile(icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    Column(modifier.clip(RoundedCornerShape(24.dp)).background(cs.surfaceContainer).padding(16.dp)) {
        Icon(icon, null, tint = cs.primary)
        Spacer(Modifier.height(8.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
    }
}
