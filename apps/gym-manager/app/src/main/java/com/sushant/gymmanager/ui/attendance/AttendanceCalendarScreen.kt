package com.sushant.gymmanager.ui.attendance

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import com.sushant.gymmanager.ui.theme.OnStatus
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gymmanager.data.local.AttendanceEntity
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.RollBadge
import com.sushant.gymmanager.ui.components.rememberGymApp
import com.sushant.gymmanager.ui.theme.StatusAmber
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.ui.theme.StatusRed
import com.sushant.gymmanager.util.DateUtils
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

data class Streaks(val current: Int, val best: Int)

/** Streaks count consecutive recorded sessions where the member was present (days without records are skipped). */
fun computeStreaks(records: List<AttendanceEntity>): Streaks {
    val byDay = records.groupBy { it.attendanceDate }.toSortedMap()
        .mapValues { (_, v) -> v.any { it.isPresent } }
    var best = 0
    var run = 0
    byDay.values.forEach { present ->
        run = if (present) run + 1 else 0
        if (run > best) best = run
    }
    return Streaks(run, best)
}

@Composable
fun AttendanceCalendarScreen(memberId: Long, onBack: () -> Unit) {
    val app = rememberGymApp()
    val member by remember(memberId) { app.repository.observeMember(memberId) }.collectAsStateWithLifecycle(initialValue = null)
    val records by remember(memberId) { app.repository.attendanceForMember(memberId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    var monthText by rememberSaveable { mutableStateOf(YearMonth.now().toString()) }
    val month = YearMonth.parse(monthText)
    val byDay = remember(records) { records.groupBy { it.attendanceDate }.mapValues { (_, v) -> v.any { it.isPresent } } }
    val streaks = remember(records) { computeStreaks(records) }
    val monthDays = (1..month.lengthOfMonth()).map { month.atDay(it) }
    val presentInMonth = monthDays.count { byDay[it.toString()] == true }
    val recordedInMonth = monthDays.count { byDay.containsKey(it.toString()) }
    val pct = if (recordedInMonth > 0) presentInMonth * 100 / recordedInMonth else 0

    Scaffold(topBar = { GymTopBar(t("Attendance Calendar"), member?.fullName, onBack = onBack) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("calendar"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            member?.let { m ->
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MemberAvatar(m.photoUri, m.fullName, size = 48.dp)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(m.fullName, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(2.dp))
                            RollBadge(m.rollNumber)
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile(Modifier.weight(1f), Icons.Default.EventAvailable, StatusGreen, "$presentInMonth/$recordedInMonth", t("This month (%d%%)", pct))
                StatTile(Modifier.weight(1f), Icons.Default.LocalFireDepartment, StatusAmber, streaks.current.toString(), t("Current streak"))
                StatTile(Modifier.weight(1f), Icons.Default.EmojiEvents, MaterialTheme.colorScheme.primary, streaks.best.toString(), t("Best streak"))
            }
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { monthText = month.minusMonths(1).toString() }) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Previous month"))
                    }
                    Text(
                        DateUtils.formatMonth(month.atDay(1)),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { monthText = month.plusMonths(1).toString() },
                        enabled = month.isBefore(YearMonth.now())
                    ) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Next month")) }
                }
                Spacer(Modifier.height(8.dp))
                val weekdays = listOf("Mo", "Tu", "We", "Th", "Fr", "Sa", "Su")
                Row {
                    weekdays.forEach {
                        Text(
                            t(it), Modifier.weight(1f), textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                val lead = month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value
                val cells = List(lead) { null } + monthDays
                val today = LocalDate.now()
                cells.chunked(7).forEach { week ->
                    Row(Modifier.fillMaxWidth()) {
                        week.forEach { day -> DayCell(day, day?.let { byDay[it.toString()] }, day == today, Modifier.weight(1f)) }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Legend(StatusGreen, t("✓ Present"))
                    Legend(StatusRed, t("✗ Absent"))
                    Legend(MaterialTheme.colorScheme.outlineVariant, t("No record"))
                }
            }
        }
    }
}

@Composable
private fun DayCell(day: LocalDate?, present: Boolean?, isToday: Boolean, modifier: Modifier) {
    Box(modifier.aspectRatio(1f).padding(3.dp), contentAlignment = Alignment.Center) {
        if (day != null) {
            val bg = when (present) {
                true -> StatusGreen
                false -> StatusRed
                null -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
            val fg = if (present != null) OnStatus else MaterialTheme.colorScheme.onSurfaceVariant
            val label = when (present) {
                true -> t("%d, present", day.dayOfMonth)
                false -> t("%d, absent", day.dayOfMonth)
                null -> day.dayOfMonth.toString()
            }
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(10.dp)).background(bg)
                    .then(if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(10.dp)) else Modifier)
                    .clearAndSetSemantics { contentDescription = label },
                contentAlignment = Alignment.Center
            ) {
                // A tick or cross as well as the colour, so it reads without colour vision.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(day.dayOfMonth.toString(), color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = if (isToday) FontWeight.Bold else null)
                    if (present != null) {
                        Icon(if (present) Icons.Default.Check else Icons.Default.Close, null, tint = fg, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(12.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun StatTile(modifier: Modifier, icon: ImageVector, tint: Color, value: String, label: String) {
    AppCard(modifier, contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp)) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(6.dp))
        Text(value, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
    }
}
