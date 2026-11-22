@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.sushant.tarang.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.media3.common.util.UnstableApi
import com.sushant.tarang.data.Prefs
import com.sushant.tarang.ui.LocalVm
import com.sushant.tarang.ui.components.*
import kotlin.math.roundToInt

private fun hz(f: Int) = if (f >= 1000) "${f / 1000}k" else "$f"

/**
 * Equalizer, bass and surround — applied inside Tarang's own audio pipeline, so it works on every phone.
 * Shown in a bottom sheet that is only as tall as its controls (the sheet scrolls on short screens).
 */
@Composable
fun SoundSheetContent() {
    val vm = LocalVm.current
    val s by vm.settings.collectAsState()
    var confirmReset by remember { mutableStateOf(false) }
    val presets = Prefs.PRESETS.keys.toList() + listOfNotNull(if (s.myPreset != null) "My preset" else null)
    Column(Modifier.fillMaxWidth().testTag("sound")) {
        Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Sound", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { confirmReset = true }, Modifier.testTag("sound_reset")) { Icon(Icons.Rounded.RestartAlt, "Reset sound") }
        }
        Column(Modifier.fillMaxWidth()) {
            ListItemRow("Equalizer", if (s.eqEnabled) "On · ${s.eqPreset}" else "Off", Icons.Rounded.Equalizer, iconTone = 0,
                onClick = { vm.setSettings { it.copy(eqEnabled = !it.eqEnabled) } }, modifier = Modifier.testTag("eq_switch"),
                trailing = { Switch(s.eqEnabled, { v -> vm.setSettings { it.copy(eqEnabled = v) } }) })
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.forEach { p ->
                    FilterChip(selected = s.eqPreset == p, enabled = s.eqEnabled, onClick = {
                        val bands = if (p == "My preset") s.myPreset!! else Prefs.PRESETS.getValue(p)
                        vm.setSettings { it.copy(eqPreset = p, eqBands = bands) }
                    }, label = { Text(p) }, modifier = Modifier.testTag("preset_$p"))
                }
            }
            Spacer(Modifier.height(8.dp))
            Prefs.BANDS_HZ.forEachIndexed { i, f ->
                val v = s.eqBands.getOrElse(i) { 0f }
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${hz(f)} Hz", Modifier.width(64.dp), style = MaterialTheme.typography.labelLarge)
                    Slider(v, { nv -> vm.setSettings { st -> st.copy(eqPreset = "Custom", eqBands = st.eqBands.toMutableList().also { b -> b[i] = (nv * 2).roundToInt() / 2f }) } },
                        valueRange = -12f..12f, enabled = s.eqEnabled, modifier = Modifier.weight(1f).testTag("band_$i"))
                    Text("%+.1f dB".format(v), Modifier.width(72.dp), style = MaterialTheme.typography.labelMedium)
                }
            }
            TextButton(onClick = { vm.setSettings { it.copy(myPreset = it.eqBands, eqPreset = "My preset") }; vm.snack.tryEmit("Saved as My preset") },
                enabled = s.eqEnabled, modifier = Modifier.padding(horizontal = 12.dp).testTag("save_preset")) {
                Icon(Icons.Rounded.Save, null); Spacer(Modifier.width(8.dp)); Text("Save as My preset")
            }
            SectionLabel("Bass and surround")
            Level("Bass boost", "Fuller low notes", s.bassBoost, "bass") { v -> vm.setSettings { it.copy(bassBoost = v) } }
            Level("Surround", "Wider sound in headphones", s.virtualizer, "virtualizer") { v -> vm.setSettings { it.copy(virtualizer = v) } }
            Text("These change only how Tarang sounds. Very high levels can distort on small speakers.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(20.dp))
        }
    }
    if (confirmReset) ConfirmDialog("Reset sound?", "Equalizer, bass boost and surround go back to normal. Your saved preset stays.", "Reset", { confirmReset = false }, {
        confirmReset = false; vm.setSettings { it.copy(eqEnabled = false, eqPreset = "Flat", eqBands = List(10) { 0f }, bassBoost = 0, virtualizer = 0) }
    }, destructive = false)
}

@Composable
private fun Level(title: String, sub: String, value: Int, tag: String, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                Text(sub, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(if (value == 0) "Off" else "$value%", style = MaterialTheme.typography.labelLarge)
        }
        Slider(value.toFloat(), { onChange((it / 5).roundToInt() * 5) }, valueRange = 0f..100f, modifier = Modifier.testTag(tag))
    }
}
