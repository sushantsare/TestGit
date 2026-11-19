package com.sushant.speedtile.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    theme: Int, onTheme: (Int) -> Unit,
    decimals: Boolean, onDecimals: (Boolean) -> Unit,
    gaugeMax: Int, onGaugeMax: (Int) -> Unit,
    autoStop: Int, onAutoStop: (Int) -> Unit,
    keepOn: Boolean, onKeepOn: (Boolean) -> Unit,
    offOnClose: Boolean, onOffOnClose: (Boolean) -> Unit,
    inPlace: Boolean, onEnableInPlace: () -> Unit,
    onBack: () -> Unit,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("nav_back")) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AppCard {
                SectionTitle("Appearance")
                Spacer(Modifier.size(12.dp))
                Label("Theme")
                Segments(listOf("System", "Light", "Dark"), theme, "theme") { onTheme(it) }
                Spacer(Modifier.size(14.dp))
                Label("Speedometer scale")
                val scales = listOf(120, 160, 200, 240)
                Segments(scales.map { "$it" }, scales.indexOf(gaugeMax).coerceAtLeast(0), "scale") { onGaugeMax(scales[it]) }
                Spacer(Modifier.size(6.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("Show one decimal", "For example 42.6 km/h instead of 43", decimals, "sw_decimals", onDecimals)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                SwitchRow("Keep screen on", "While the speed screen is open and speed is on", keepOn, "sw_keep_on", onKeepOn)
            }

            AppCard {
                SectionTitle("Quick Settings tile")
                SwitchRow(
                    "Turn off when the panel closes",
                    "Speed stops as soon as you close Quick Settings. After tapping the tile, pull down within 30 seconds to see your speed.",
                    offOnClose, "sw_off_on_close", onOffOnClose,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Start without closing the panel", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(
                            if (inPlace) "On · the tile switches on right where it is."
                            else "Set location to \"Allow all the time\" so the tile can start GPS by itself, without Quick Settings closing.",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (inPlace) LocalExtra.current.green else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!inPlace) {
                        Spacer(Modifier.width(12.dp))
                        androidx.compose.material3.FilledTonalButton(onClick = onEnableInPlace, modifier = Modifier.testTag("btn_in_place")) { Text("Turn on") }
                    }
                }
            }

            AppCard {
                SectionTitle("Battery")
                Spacer(Modifier.size(4.dp))
                Text(
                    "Switch GPS off by itself after you stop moving for:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.size(10.dp))
                val opts = listOf(0, 5, 15, 30)
                Segments(listOf("Never", "5 min", "15 min", "30 min"), opts.indexOf(autoStop).coerceAtLeast(0), "autostop") { onAutoStop(opts[it]) }
            }
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.size(8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Segments(labels: List<String>, selected: Int, tag: String, onSelect: (Int) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        labels.forEachIndexed { i, l ->
            SegmentedButton(
                selected = i == selected,
                onClick = { onSelect(i) },
                shape = SegmentedButtonDefaults.itemShape(i, labels.size),
                modifier = Modifier.testTag("${tag}_$i"),
                icon = {},
            ) { Text(l, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        }
    }
}

@Composable
private fun SwitchRow(title: String, sub: String, checked: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onChange, modifier = Modifier.testTag(tag))
    }
}

