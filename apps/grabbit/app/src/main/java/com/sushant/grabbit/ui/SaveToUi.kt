package com.sushant.grabbit.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.grabbit.data.Folder
import com.sushant.grabbit.data.Folders
import com.sushant.grabbit.ui.components.SectionLabel
import com.sushant.grabbit.ui.theme.LocalSpacing

/** Text for the Default folders, by what is being downloaded. */
fun defaultFolderPath(audio: Boolean, image: Boolean = false): String = when {
    image -> "Internal storage › Pictures › Grabbit"
    audio -> "Internal storage › Music › Grabbit"
    else -> "Internal storage › Movies › Grabbit"
}

/** "Save to" row (opens Android's folder picker) + Default and up to 3 remembered folders as chips. */
@Composable
fun SaveToSection(folder: String, onFolder: (String) -> Unit, audio: Boolean, image: Boolean = false) {
    val sp = LocalSpacing.current
    val ctx = LocalContext.current
    val recent by Folders.recent.collectAsState()
    var forget by remember { mutableStateOf<Folder?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) onFolder(Folders.add(ctx, uri).uri)
    }
    val cur = recent.firstOrNull { it.uri == folder }
    Column {
        SectionLabel("Save to")
        Spacer(Modifier.size(sp.xs))
        Surface(
            onClick = { picker.launch(Folders.pickerStart ?: cur?.let { treeToDoc(it.uri) }) },
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).testTag("saveToRow"),
        ) {
            Row(Modifier.padding(horizontal = sp.m, vertical = sp.s), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, modifier = Modifier.size(44.dp)) {}
                    Icon(Icons.Rounded.Folder, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                }
                Spacer(Modifier.width(sp.m))
                Column(Modifier.weight(1f)) {
                    Text(cur?.name ?: "Grabbit (Default)", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(cur?.path ?: defaultFolderPath(audio, image), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("saveToPath"))
                }
                Spacer(Modifier.width(sp.s))
                Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            }
        }
        Spacer(Modifier.size(sp.s))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(sp.s), contentPadding = PaddingValues(vertical = sp.xxs)) {
            item(key = "default") { FolderChip("Default", folder.isBlank(), onClick = { onFolder("") }, onLongClick = null, tag = "folderChip_default") }
            items(recent, key = { it.uri }) { f ->
                FolderChip(f.name, f.uri == folder, onClick = { onFolder(f.uri) }, onLongClick = { forget = f }, tag = "folderChip_${f.name}")
            }
        }
    }
    forget?.let { f ->
        AlertDialog(
            onDismissRequest = { forget = null },
            title = { Text("Forget \"${f.name}\"?") },
            text = { Text("It won't show as a chip any more. Files already saved there stay where they are.") },
            confirmButton = {
                TextButton(onClick = {
                    Folders.forget(ctx, f.uri)
                    if (folder == f.uri) onFolder("")
                    forget = null
                }) { Text("Forget") }
            },
            dismissButton = { TextButton(onClick = { forget = null }) { Text("Cancel") } },
        )
    }
}

/** Picker opens inside the current folder. */
private fun treeToDoc(tree: String): Uri? = runCatching {
    val u = Uri.parse(tree)
    android.provider.DocumentsContract.buildDocumentUriUsingTree(u, android.provider.DocumentsContract.getTreeDocumentId(u))
}.getOrNull()

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderChip(label: String, selected: Boolean, onClick: () -> Unit, onLongClick: (() -> Unit)?, tag: String) {
    val sp = LocalSpacing.current
    val haptic = LocalHapticFeedback.current
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0f),
        border = if (selected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.heightIn(min = 40.dp).clip(shape).testTag(tag)
            .combinedClickable(
                role = Role.RadioButton,
                onClick = onClick,
                onLongClick = onLongClick?.let { { haptic.performHapticFeedback(HapticFeedbackType.LongPress); it() } },
                onLongClickLabel = if (onLongClick != null) "Forget folder" else null,
            ),
    ) {
        Row(Modifier.padding(horizontal = sp.m, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (selected) {
                Icon(Icons.Rounded.Check, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Spacer(Modifier.width(6.dp))
            }
            Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1,
                color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface)
        }
    }
}
