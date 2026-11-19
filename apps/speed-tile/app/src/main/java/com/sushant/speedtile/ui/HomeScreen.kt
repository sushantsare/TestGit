package com.sushant.speedtile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GpsOff
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocationOff
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.speedtile.SpeedMath
import com.sushant.speedtile.SpeedState
import com.sushant.speedtile.Status
import com.sushant.speedtile.StopReason

/** Problems that stop speed from working, shown as one card under the Start button. */
enum class Notice { NONE, PERMISSION_DENIED, PERMISSION_BLOCKED, APPROXIMATE_ONLY, GPS_OFF }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    state: SpeedState,
    decimals: Boolean,
    gaugeMax: Int,
    notice: Notice,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onFixNotice: () -> Unit,
    onResetTrip: () -> Unit,
    onSettings: () -> Unit,
    onAbout: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Speed Tile", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onSettings, modifier = Modifier.testTag("nav_settings")) {
                        Icon(Icons.Outlined.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onAbout, modifier = Modifier.testTag("nav_about")) {
                        Icon(Icons.Outlined.Info, contentDescription = "About")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(pad)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard {
                val live = state.running && state.status == Status.LIVE
                SpeedGauge(
                    speedText = if (live) SpeedMath.formatSpeed(state.speedKmh, decimals) else if (state.running) "--" else "0",
                    speedKmh = if (live) state.speedKmh ?: 0f else 0f,
                    maxKmh = gaugeMax,
                    active = live,
                )
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { StatusPill(state) }
                Spacer(Modifier.size(16.dp))
                if (state.running) {
                    Button(
                        onClick = onStop,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("btn_stop"),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Stop, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Stop", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    Button(
                        onClick = onStart,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp).testTag("btn_start"),
                        shape = RoundedCornerShape(16.dp),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Start", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }

            NoticeCard(notice, onFixNotice)

            if (!state.running && state.stopReason == StopReason.IDLE) {
                InfoCard(
                    Icons.Outlined.Info,
                    "Turned off to save battery",
                    "You were not moving, so GPS switched itself off. Change this in Settings.",
                )
            }

            TripCard(state, decimals, onResetTrip)
        }
    }
}

@Composable
private fun StatusPill(state: SpeedState) {
    val extra = LocalExtra.current
    val (color, text) = when {
        !state.running -> MaterialTheme.colorScheme.onSurfaceVariant to "Off"
        state.status == Status.LIVE -> extra.green to buildString {
            append("Live")
            state.accuracyM?.let { append(" · ±${it.toInt()} m") }
            if (state.satsUsed > 0) append(" · ${state.satsUsed} satellites")
        }
        state.status == Status.WEAK -> extra.amber to "Weak GPS signal"
        state.status == Status.GPS_OFF -> extra.red to "Location is off"
        else -> extra.blue to if (state.satsSeen > 0) "Finding GPS · ${state.satsSeen} satellites seen" else "Finding GPS signal…"
    }
    Row(
        Modifier
            .background(color.copy(alpha = 0.13f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 7.dp)
            .testTag("status_pill"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(8.dp))
        Text(text, color = color, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun NoticeCard(notice: Notice, onFix: () -> Unit) {
    val (icon, title, body, action) = when (notice) {
        Notice.NONE -> return
        Notice.PERMISSION_DENIED -> Quad(Icons.Outlined.LocationOff, "Location needed",
            "Speed Tile measures speed with GPS, so it needs your location while it is on.", "Allow")
        Notice.PERMISSION_BLOCKED -> Quad(Icons.Outlined.LocationOff, "Location is blocked",
            "Allow location for Speed Tile in Settings to measure your speed.", "Open Settings")
        Notice.APPROXIMATE_ONLY -> Quad(Icons.Outlined.LocationOff, "Precise location needed",
            "GPS speed needs precise location. Approximate location is not accurate enough.", "Open Settings")
        Notice.GPS_OFF -> Quad(Icons.Outlined.GpsOff, "Location is off",
            "Turn on Location on your phone to see your speed.", "Turn on")
    }
    AppCard(Modifier.testTag("notice_card")) {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = LocalExtra.current.red, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(10.dp))
                FilledTonalButton(onClick = onFix, modifier = Modifier.testTag("notice_action")) { Text(action) }
            }
        }
    }
}

private data class Quad(val icon: ImageVector, val title: String, val body: String, val action: String)

@Composable
private fun InfoCard(icon: ImageVector, title: String, body: String) {
    AppCard {
        Row(verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 2.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(Modifier.size(2.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun TripCard(state: SpeedState, decimals: Boolean, onReset: () -> Unit) {
    val t = state.trip
    val empty = t.maxKmh == 0f && t.distanceM == 0.0 && t.movingMs == 0L
    AppCard(Modifier.testTag("trip_card")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionTitle("Trip")
                Text(
                    "Counts only while you are moving",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReset, enabled = !empty, modifier = Modifier.testTag("btn_reset_trip")) {
                Icon(Icons.Outlined.RestartAlt, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Reset")
            }
        }
        Spacer(Modifier.size(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Top speed", "${SpeedMath.formatSpeed(t.maxKmh, decimals)} km/h", Modifier.weight(1f))
            Stat("Average", "${SpeedMath.formatSpeed(t.avgKmh, decimals)} km/h", Modifier.weight(1f))
        }
        Spacer(Modifier.size(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Stat("Distance", SpeedMath.formatDistance(t.distanceM), Modifier.weight(1f))
            Stat("Moving time", SpeedMath.formatDuration(t.movingMs), Modifier.weight(1f))
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (LocalExtra.current.cardBorder == androidx.compose.ui.graphics.Color(0xFF2A2A2A)) 1f else 0.6f), RoundedCornerShape(14.dp))
            .heightIn(min = 64.dp)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.size(2.dp))
        Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
