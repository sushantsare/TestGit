package com.sushant.inknote.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Draw
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Interests
import androidx.compose.material.icons.outlined.Password
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.RestorePage
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.inknote.BuildConfig
import com.sushant.inknote.R
import com.sushant.inknote.data.AppSettings
import com.sushant.inknote.data.NoteEntity
import com.sushant.inknote.data.SortBy
import com.sushant.inknote.data.ThemeMode
import com.sushant.inknote.io.Backup
import com.sushant.inknote.ml.Handwriting
import com.sushant.inknote.ui.Dates
import com.sushant.inknote.ui.NoteThumb
import com.sushant.inknote.ui.app
import com.sushant.inknote.ui.home.NewNoteSheet
import com.sushant.inknote.ui.home.paperName
import com.sushant.inknote.ui.lock.AskPinDialog
import com.sushant.inknote.ui.lock.SetPinDialog
import com.sushant.inknote.ui.theme.LocalInk
import com.sushant.inknote.ui.toast
import com.sushant.inknote.work.IndexWorker
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScreenTop(title: String, expanded: Boolean, onBack: (() -> Unit)?, action: @Composable () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().then(if (expanded) Modifier else Modifier.statusBarsPadding()).height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null && !expanded) IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) } else Spacer(Modifier.size(12.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).padding(start = 4.dp))
        action()
    }
}

// ================= Trash =================
@Composable
fun TrashScreen(items: List<NoteEntity>, expanded: Boolean, onBack: () -> Unit, onRestore: (Long) -> Unit, onDelete: (Long) -> Unit, onEmpty: () -> Unit) {
    val ink = LocalInk.current
    var confirmEmpty by rememberSaveable { mutableStateOf(false) }
    var confirmOne by rememberSaveable { mutableStateOf<Long?>(null) }
    Column(Modifier.fillMaxSize().background(ink.bg).testTag("trash_screen")) {
        ScreenTop(stringResource(R.string.trash), expanded, onBack) { if (items.isNotEmpty()) TextButton(onClick = { confirmEmpty = true }, Modifier.testTag("empty_trash")) { Text(stringResource(R.string.empty)) } }
        Row(Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(ink.surf2).padding(14.dp)) {
            Icon(Icons.Outlined.Info, null, tint = ink.muted); Text(stringResource(R.string.trash_info), color = ink.muted, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(start = 10.dp))
        }
        if (items.isEmpty()) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Outlined.DeleteSweep, null, tint = ink.muted, modifier = Modifier.size(56.dp))
                Text(stringResource(R.string.trash_empty), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
            }
            return@Column
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val cols = when { maxWidth < 600.dp -> 2; maxWidth < 840.dp -> 3; else -> 4 }
            LazyVerticalGrid(GridCells.Fixed(cols), contentPadding = PaddingValues(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(items, key = { it.id }) { n ->
                    val left = (30 - ((System.currentTimeMillis() - (n.deletedAt ?: 0)) / 86_400_000L)).coerceAtLeast(0)
                    Surface(shape = RoundedCornerShape(16.dp), color = ink.card, shadowElevation = 1.dp) {
                        Column {
                            NoteThumb(n, Modifier.padding(6.dp).fillMaxWidth().height(130.dp))
                            Text(n.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 11.dp))
                            Text(androidx.compose.ui.res.pluralStringResource(R.plurals.days_left, left.toInt(), left.toInt()), style = MaterialTheme.typography.bodySmall, color = ink.muted, modifier = Modifier.padding(horizontal = 11.dp))
                            Row {
                                TextButton(onClick = { onRestore(n.id) }, Modifier.weight(1f).testTag("restore_${n.title}")) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Text(stringResource(R.string.restore), Modifier.padding(start = 4.dp)) }
                                IconButton(onClick = { confirmOne = n.id }) { Icon(Icons.Outlined.DeleteForever, stringResource(R.string.delete_forever)) }
                            }
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Spacer(Modifier.height(24.dp)) }
            }
        }
    }
    if (confirmEmpty) AlertDialog(onDismissRequest = { confirmEmpty = false }, title = { Text(stringResource(R.string.empty_trash_q)) }, text = { Text(androidx.compose.ui.res.pluralStringResource(R.plurals.empty_trash_body, items.size, items.size)) },
        confirmButton = { TextButton(onClick = { confirmEmpty = false; onEmpty() }, modifier = Modifier.testTag("confirm_empty")) { Text(stringResource(R.string.delete_all), color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmEmpty = false }) { Text(stringResource(R.string.cancel)) } })
    confirmOne?.let { id ->
        AlertDialog(onDismissRequest = { confirmOne = null }, title = { Text(stringResource(R.string.delete_forever_q)) }, text = { Text(stringResource(R.string.cant_undo)) },
            confirmButton = { TextButton(onClick = { confirmOne = null; onDelete(id) }) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { confirmOne = null }) { Text(stringResource(R.string.cancel)) } })
    }
}

// ================= Settings =================
private enum class SDialog { NONE, THEME, SORT, DEFAULT_PAGE, SET_PIN, BACKUP_PIN, RESTORE_PIN, RESTORE_CONFIRM }

@Composable
fun SettingsScreen(st: AppSettings, update: ((AppSettings) -> AppSettings) -> Unit, expanded: Boolean, onBack: () -> Unit, onAbout: () -> Unit) {
    val ctx = LocalContext.current
    val app = ctx.app()
    val ink = LocalInk.current
    val scope = rememberCoroutineScope()
    val hasPin by app.securePrefs.hasPin.collectAsState()
    var dialog by rememberSaveable { mutableStateOf(SDialog.NONE) }
    var busy by remember { mutableStateOf<String?>(null) }
    var packReady by remember { mutableStateOf<Boolean?>(null) }
    var restoreUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var backupPin by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { packReady = Handwriting.isDownloaded() }
    val backupDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pin = backupPin; backupPin = null
        if (uri == null) return@rememberLauncherForActivityResult
        busy = ctx.getString(R.string.backing_up)
        scope.launch {
            val tmp = File(ctx.cacheDir, "backup.tmp")
            val res = runCatching { val n = Backup(ctx, app.repo).writeAll(tmp, pin); app.exporter.saveTo(tmp, uri).also { if (!it) error("write") }; n }
            tmp.delete(); busy = null
            res.onSuccess { n -> app.settings.update { it.copy(lastBackupAt = System.currentTimeMillis(), lastBackupSize = 0) }; ctx.toast(ctx.resources.getQuantityString(R.plurals.backup_done, n, n)) }
                .onFailure { ctx.toast(ctx.getString(R.string.backup_failed)) }
        }
    }
    fun doRestore(uri: Uri, pin: String?) {
        busy = ctx.getString(R.string.restoring)
        scope.launch {
            val r = runCatching { Backup(ctx, app.repo).restore(uri, pin) }
            busy = null
            r.onSuccess { ctx.toast(if (it.skippedLocked > 0) ctx.resources.getQuantityString(R.plurals.restore_done, it.notes, it.notes) + " · " + ctx.resources.getQuantityString(R.plurals.locked_skipped, it.skippedLocked, it.skippedLocked) else ctx.resources.getQuantityString(R.plurals.restore_done, it.notes, it.notes)); IndexWorker.schedule(ctx, 1) }
                .onFailure { ctx.toast(it.message ?: ctx.getString(R.string.restore_failed)) }
        }
    }
    val restoreDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        restoreUri = uri
        scope.launch {
            val m = Backup(ctx, app.repo).inspect(uri)
            if (m == null || m.optString("app") != "InkNote") { ctx.toast(ctx.getString(R.string.not_backup)); return@launch }
            dialog = if (Backup(ctx, app.repo).hasLocked(m)) SDialog.RESTORE_PIN else SDialog.RESTORE_CONFIRM
        }
    }
    fun startBackup() {
        scope.launch {
            val locked = Backup(ctx, app.repo).lockedCount()
            if (locked > 0) dialog = SDialog.BACKUP_PIN
            else backupDoc.launch("InkNote-backup-" + SimpleDateFormat("d-MMM-yyyy", Locale.US).format(Date()) + ".inkbackup")
        }
    }

    Column(Modifier.fillMaxSize().background(ink.bg).testTag("settings_screen")) {
        ScreenTop(stringResource(R.string.settings), expanded, onBack)
        Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 720.dp)) {
                busy?.let { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(22.dp)); Text(it, Modifier.padding(start = 12.dp)) } }
                Section(stringResource(R.string.s_writing))
                SwitchRow(Icons.Outlined.Draw, stringResource(R.string.stylus_only), stringResource(R.string.stylus_only_sub), st.stylusOnly, "sw_stylus") { v -> update { it.copy(stylusOnly = v) } }
                SwitchRow(Icons.Outlined.Interests, stringResource(R.string.shape_correct), stringResource(R.string.shape_correct_sub), st.shapeHold, "sw_shape") { v -> update { it.copy(shapeHold = v) } }
                NavRow(Icons.Outlined.Description, stringResource(R.string.default_page), "${paperName(st.defaultPaper)} · ${stringResource(if (st.defaultOrientation == "LANDSCAPE") R.string.horizontal else R.string.vertical)} · ${stringResource(if (st.defaultPageType == "ENDLESS") R.string.one_long_page else R.string.pages)}", "row_default_page") { dialog = SDialog.DEFAULT_PAGE }
                NavRow(Icons.Outlined.Translate, stringResource(R.string.hw_language), when (packReady) { true -> stringResource(R.string.pack_ready); false -> stringResource(R.string.pack_missing); null -> "…" }, "row_pack") {
                    if (packReady == false) { busy = ctx.getString(R.string.downloading_pack); scope.launch { val e = Handwriting.download(); busy = null; packReady = Handwriting.isDownloaded(); ctx.toast(e ?: ctx.getString(R.string.pack_ready)); IndexWorker.schedule(ctx, 1) } }
                }
                Section(stringResource(R.string.s_notes))
                NavRow(Icons.Outlined.Sort, stringResource(R.string.sort_by), stringResource(when (st.sortBy) { SortBy.CREATED -> R.string.sort_created; SortBy.TITLE -> R.string.sort_title; else -> R.string.sort_modified }), "row_sort") { dialog = SDialog.SORT }
                SwitchRow(Icons.Outlined.AutoFixHigh, stringResource(R.string.auto_empty), stringResource(R.string.auto_empty_sub), st.autoEmptyTrash, "sw_autoempty") { v -> update { it.copy(autoEmptyTrash = v) } }
                Section(stringResource(R.string.s_privacy))
                SwitchRow(Icons.Outlined.Fingerprint, stringResource(R.string.fingerprint_unlock), stringResource(R.string.fingerprint_unlock_sub), st.biometric, "sw_bio") { v -> update { it.copy(biometric = v) } }
                NavRow(Icons.Outlined.Password, stringResource(if (hasPin) R.string.change_pin else R.string.set_pin), stringResource(if (hasPin) R.string.pin_set else R.string.pin_not_set), "row_pin") { dialog = SDialog.SET_PIN }
                Section(stringResource(R.string.s_backup))
                NavRow(Icons.Outlined.Backup, stringResource(R.string.backup_all), if (st.lastBackupAt > 0) stringResource(R.string.last_backup, Dates.full(st.lastBackupAt)) else stringResource(R.string.never_backed_up), "row_backup") { startBackup() }
                NavRow(Icons.Outlined.RestorePage, stringResource(R.string.restore_backup), stringResource(R.string.restore_sub), "row_restore") { restoreDoc.launch(arrayOf("*/*")) }
                Section(stringResource(R.string.s_appearance))
                NavRow(Icons.Outlined.DarkMode, stringResource(R.string.theme), stringResource(when (st.theme) { ThemeMode.LIGHT -> R.string.theme_light; ThemeMode.DARK -> R.string.theme_dark; else -> R.string.theme_system }), "row_theme") { dialog = SDialog.THEME }
                Section(stringResource(R.string.s_about))
                NavRow(Icons.Outlined.Info, stringResource(R.string.about_app), stringResource(R.string.version_x, BuildConfig.VERSION_NAME), "row_about", onAbout)
            }
        }
    }
    when (dialog) {
        SDialog.THEME -> ChoiceDialog(stringResource(R.string.theme), listOf(ThemeMode.SYSTEM to R.string.theme_system, ThemeMode.LIGHT to R.string.theme_light, ThemeMode.DARK to R.string.theme_dark), st.theme,
            { dialog = SDialog.NONE }) { v -> update { it.copy(theme = v) }; dialog = SDialog.NONE }
        SDialog.SORT -> ChoiceDialog(stringResource(R.string.sort_by), listOf(SortBy.MODIFIED to R.string.sort_modified, SortBy.CREATED to R.string.sort_created, SortBy.TITLE to R.string.sort_title), st.sortBy,
            { dialog = SDialog.NONE }) { v -> update { it.copy(sortBy = v) }; dialog = SDialog.NONE }
        SDialog.DEFAULT_PAGE -> NewNoteSheet(st, false, null, { dialog = SDialog.NONE }, { spec -> update { it.copy(defaultPaper = spec.paper, defaultOrientation = spec.orientation, defaultPageType = spec.pageType) }; dialog = SDialog.NONE; ctx.toast(ctx.getString(R.string.saved)) }, {}, {})
        SDialog.SET_PIN -> SetPinDialog({ dialog = SDialog.NONE }) { dialog = SDialog.NONE; ctx.toast(ctx.getString(R.string.pin_saved)) }
        SDialog.BACKUP_PIN -> AskPinDialog(stringResource(R.string.backup_all), stringResource(R.string.backup_pin_body), { dialog = SDialog.NONE }) { pin ->
            scope.launch {
                if (app.securePrefs.checkPin(pin)) { dialog = SDialog.NONE; backupPin = pin; backupDoc.launch("InkNote-backup-" + SimpleDateFormat("d-MMM-yyyy", Locale.US).format(Date()) + ".inkbackup") }
                else ctx.toast(ctx.getString(R.string.wrong_pin))
            }
        }
        SDialog.RESTORE_PIN -> AskPinDialog(stringResource(R.string.restore_backup), stringResource(R.string.restore_pin_body), { dialog = SDialog.NONE; restoreUri?.let { doRestore(it, null) } }) { pin ->
            dialog = SDialog.NONE; restoreUri?.let { doRestore(it, pin) }
        }
        SDialog.RESTORE_CONFIRM -> AlertDialog(onDismissRequest = { dialog = SDialog.NONE }, title = { Text(stringResource(R.string.restore_backup)) }, text = { Text(stringResource(R.string.restore_confirm)) },
            confirmButton = { TextButton(onClick = { dialog = SDialog.NONE; restoreUri?.let { doRestore(it, null) } }, modifier = Modifier.testTag("restore_ok")) { Text(stringResource(R.string.restore)) } },
            dismissButton = { TextButton(onClick = { dialog = SDialog.NONE }) { Text(stringResource(R.string.cancel)) } })
        SDialog.NONE -> Unit
    }
}

@Composable
private fun Section(t: String) = Text(t, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 4.dp))

@Composable
private fun SwitchRow(icon: ImageVector, title: String, sub: String, v: Boolean, tag: String, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().toggleable(v, role = Role.Switch, onValueChange = onChange).heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalInk.current.muted)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, style = MaterialTheme.typography.bodyLarge); Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalInk.current.muted) }
        Switch(v, null)
    }
}

@Composable
private fun NavRow(icon: ImageVector, title: String, sub: String, tag: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).heightIn(min = 64.dp).padding(horizontal = 20.dp, vertical = 8.dp).testTag(tag), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = LocalInk.current.muted)
        Column(Modifier.weight(1f).padding(horizontal = 16.dp)) { Text(title, style = MaterialTheme.typography.bodyLarge); if (sub.isNotEmpty()) Text(sub, style = MaterialTheme.typography.bodySmall, color = LocalInk.current.muted) }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = LocalInk.current.muted)
    }
}

@Composable
private fun ChoiceDialog(title: String, options: List<Pair<Int, Int>>, current: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
        text = {
            Column {
                options.forEach { (v, l) ->
                    Row(Modifier.fillMaxWidth().selectable(v == current, role = Role.RadioButton) { onPick(v) }.heightIn(min = 52.dp).testTag("opt_$v"), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(v == current, null); Text(stringResource(l), Modifier.padding(start = 12.dp))
                    }
                }
            }
        })
}

// ================= About =================
@Composable
fun AboutScreen(expanded: Boolean, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val ink = LocalInk.current
    var licences by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(ink.bg).testTag("about_screen")) {
        Row(Modifier.fillMaxWidth().then(if (expanded) Modifier else Modifier.statusBarsPadding()).height(64.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, stringResource(R.string.back)) }
            Text(stringResource(R.string.about), style = MaterialTheme.typography.headlineSmall)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(88.dp).clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.Draw, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(46.dp))
            }
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 16.dp))
            Text(stringResource(R.string.version_x, BuildConfig.VERSION_NAME), color = ink.muted)
            Text(stringResource(R.string.about_body), color = ink.muted, textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 18.dp).widthIn(max = 380.dp))
            Surface(shape = RoundedCornerShape(16.dp), color = ink.card, shadowElevation = 1.dp, modifier = Modifier.widthIn(max = 420.dp).fillMaxWidth()) {
                Column(Modifier.padding(vertical = 6.dp)) {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 10.dp)) {
                        Text(stringResource(R.string.developer), style = MaterialTheme.typography.bodySmall, color = ink.muted)
                        Text(stringResource(R.string.developed_by), style = MaterialTheme.typography.bodyLarge)
                    }
                    Column(Modifier.fillMaxWidth().clickable {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com")).putExtra(Intent.EXTRA_SUBJECT, "InkNote")) }
                            .onFailure { ctx.toast(ctx.getString(R.string.no_email_app)) }
                    }.padding(horizontal = 20.dp, vertical = 10.dp).testTag("about_email")) {
                        Text(stringResource(R.string.email), style = MaterialTheme.typography.bodySmall, color = ink.muted)
                        Text("sushant.sare@gmail.com", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
            TextButton(onClick = { licences = true }, modifier = Modifier.padding(top = 14.dp)) { Text(stringResource(R.string.licences)) }
        }
    }
    if (licences) AlertDialog(onDismissRequest = { licences = false }, title = { Text(stringResource(R.string.licences)) },
        text = { Text(stringResource(R.string.licences_body), modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { licences = false }) { Text(stringResource(R.string.ok)) } })
}
