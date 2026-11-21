package com.sushant.grabbit.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import com.sushant.grabbit.download.FileNames

/** State of a file-name field: what was typed, and the safe name it becomes. */
private class NameInput(initial: String) {
    var value by mutableStateOf(TextFieldValue(initial, TextRange(0, initial.length)))
    val clean get() = FileNames.clean(value.text)
    val hasBad get() = FileNames.hasBad(value.text)
    fun change(v: TextFieldValue) {
        // Hard stop at the length limit instead of cutting later
        value = if (v.text.codePointCount(0, v.text.length) > FileNames.MAX) value else v
    }
}

@Composable
private fun NameField(input: NameInput, modifier: Modifier, onSubmit: () -> Unit, trailing: @Composable (() -> Unit)? = null) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    OutlinedTextField(
        value = input.value, onValueChange = input::change,
        modifier = modifier.fillMaxWidth().focusRequester(focus).testTag("nameField"),
        label = { Text("File name") },
        singleLine = true,
        isError = input.clean.isBlank(),
        supportingText = {
            Text(
                when {
                    input.clean.isBlank() -> "Type a name"
                    input.hasBad -> "These can't be used and will be removed: ${FileNames.BAD_CHARS}"
                    else -> "${input.clean.codePointCount(0, input.clean.length)}/${FileNames.MAX}"
                }
            )
        },
        trailingIcon = trailing,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { if (input.clean.isNotBlank()) onSubmit() }),
    )
}

/** Title with a pen icon; tapping the pen turns it into a name field (used before downloading). */
@Composable
fun EditableName(name: String, onChange: (String) -> Unit, title: @Composable (Modifier) -> Unit) {
    var editing by remember { mutableStateOf(false) }
    if (!editing) {
        Row {
            title(Modifier.weight(1f))
            IconButton(onClick = { editing = true }, modifier = Modifier.testTag("renameBtn")) {
                Icon(Icons.Rounded.Edit, "Rename file", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    } else {
        val input = remember { NameInput(name) }
        val done = { if (input.clean.isNotBlank()) { onChange(input.clean); editing = false } }
        NameField(input, Modifier, onSubmit = done) {
            Row {
                IconButton(onClick = { editing = false }) { Icon(Icons.Rounded.Close, "Cancel rename") }
                IconButton(onClick = done, enabled = input.clean.isNotBlank(), modifier = Modifier.testTag("nameOk")) {
                    Icon(Icons.Rounded.Check, "Save name")
                }
            }
        }
    }
}

/** "Rename" dialog for a saved download. */
@Composable
fun RenameDialog(current: String, onDismiss: () -> Unit, onRename: (String) -> Unit) {
    val input = remember { NameInput(current) }
    val ok = input.clean.isNotBlank() && input.clean != current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Edit, null) },
        title = { Text("Rename") },
        text = { NameField(input, Modifier, onSubmit = { if (ok) onRename(input.clean) }) },
        confirmButton = {
            TextButton(onClick = { onRename(input.clean) }, enabled = ok, modifier = Modifier.testTag("renameConfirm")) { Text("Rename") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
