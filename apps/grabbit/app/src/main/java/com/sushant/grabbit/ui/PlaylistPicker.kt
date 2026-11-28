package com.sushant.grabbit.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.grabbit.download.Archive
import com.sushant.grabbit.engine.PlaylistEntry

/** Lets the user choose which videos of a playlist or channel to download. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistPicker(vm: MainViewModel) {
    val info = vm.picker ?: return
    val ctx = LocalContext.current
    val entries = info.entries
    val known = remember(info.url, entries.size) { Archive.known(ctx.applicationContext) }
    val fresh = remember(info.url, entries.size) { entries.filter { Archive.keyOf(it) !in known } }
    var selected by remember(info.url, entries.size) { mutableStateOf(fresh.map { Archive.keyOf(it) }.toSet()) }

    ModalBottomSheet(
        onDismissRequest = vm::closePicker,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
            Text(info.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                "${entries.size} videos · ${fresh.size} new",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(10, 20, 50).forEach { n ->
                    AssistChip(
                        onClick = { selected = fresh.take(n).map { Archive.keyOf(it) }.toSet() },
                        label = { Text("First $n") },
                    )
                }
                AssistChip(onClick = { selected = fresh.map { Archive.keyOf(it) }.toSet() }, label = { Text("All new") })
                AssistChip(onClick = { selected = emptySet() }, label = { Text("None") })
            }
            Spacer(Modifier.height(8.dp))
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp)) {
                items(entries, key = { Archive.keyOf(it) }) { e ->
                    PickRow(e, done = Archive.keyOf(e) in known, checked = Archive.keyOf(e) in selected) { k ->
                        selected = if (k in selected) selected - k else selected + k
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = vm::refreshPicker,
                enabled = !vm.pickerChecking,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (vm.pickerChecking) "Checking…" else "Check for new videos") }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { vm.downloadPicked(entries.filter { Archive.keyOf(it) in selected }) },
                enabled = selected.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Download ${selected.size} selected") }
        }
    }
}

@Composable
private fun PickRow(e: PlaylistEntry, done: Boolean, checked: Boolean, onToggle: (String) -> Unit) {
    val k = Archive.keyOf(e)
    Row(
        Modifier.fillMaxWidth()
            .clickable(enabled = !done) { onToggle(k) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked && !done, onCheckedChange = null, enabled = !done)
        Spacer(Modifier.width(8.dp))
        Column(Modifier.fillMaxWidth()) {
            Text(e.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            val sub = when {
                done -> "Already downloaded · skipped"
                e.duration > 0 -> fmtClock(e.duration)
                else -> ""
            }
            if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
