package com.sushant.gymmanager.ui.backup

import com.sushant.gymmanager.util.toast
import android.app.Application
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.RestorePage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.auth.ProtectedAction
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.repository.BackupFileInfo
import com.sushant.gymmanager.data.repository.GymBackup
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.EmptyState
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.LabeledValue
import com.sushant.gymmanager.ui.components.Pill
import com.sushant.gymmanager.ui.theme.StatusAmber
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.util.DateUtils
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.material.icons.filled.TableChart
import com.sushant.gymmanager.ui.components.rememberDownloadsAccess
import com.sushant.gymmanager.util.CsvImport
import com.sushant.gymmanager.util.CsvImportResult
import com.sushant.gymmanager.util.ShareUtil
import com.sushant.gymmanager.util.Downloads
import java.io.File

class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val gymApp = app as GymApp
    private val manager = gymApp.backupManager

    var backups by mutableStateOf<List<BackupFileInfo>?>(null)
        private set
    var busy by mutableStateOf(false)
        private set
    var preview by mutableStateOf<GymBackup?>(null)
        private set
    var previewName by mutableStateOf("")
        private set
    var importError by mutableStateOf<String?>(null)
        private set
    var settings by mutableStateOf(GymSettingsEntity())
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            settings = gymApp.repository.getSettings()
            backups = manager.listBackups()
        }
    }

    fun create(onResult: (String) -> Unit) {
        busy = true
        viewModelScope.launch {
            val msg = runCatching { manager.createBackup() }
                .fold({ "OK" }, { "FAIL" })
            busy = false
            refresh()
            onResult(msg)
        }
    }

    fun load(uri: Uri, displayName: String) {
        busy = true
        importError = null
        viewModelScope.launch {
            runCatching { manager.readFromUri(uri) }
                .onSuccess { preview = it; previewName = displayName }
                .onFailure { preview = null; importError = it.message ?: "Could not read the file" }
            busy = false
        }
    }

    fun loadLocal(file: File) {
        busy = true
        importError = null
        viewModelScope.launch {
            runCatching { manager.parse(file.readText()) }
                .onSuccess { preview = it; previewName = file.name }
                .onFailure { preview = null; importError = it.message }
            busy = false
        }
    }

    fun restore(onDone: (Boolean, String) -> Unit) {
        val backup = preview ?: return
        busy = true
        viewModelScope.launch {
            val result = runCatching { manager.restore(backup) }
            busy = false
            if (result.isSuccess) {
                preview = null
                refresh()
                onDone(true, backup.members.size.toString())
            } else onDone(false, result.exceptionOrNull()?.message ?: "Restore failed")
        }
    }

    fun clearPreview() { preview = null; importError = null }

    // ---------- Members from a spreadsheet (CSV) ----------
    var csv by mutableStateOf<CsvImportResult?>(null)
        private set

    fun loadCsv(uri: Uri) {
        busy = true
        viewModelScope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }.orEmpty()
                    val existing = gymApp.repository.snapshot().members.map { it.rollNumber }.toSet()
                    CsvImport.parse(text, gymApp.repository.getSettings(), existing)
                }
            }.getOrElse { CsvImportResult(emptyList(), listOf("Could not read this file. Save the sheet as CSV and try again.")) }
            csv = result
            busy = false
        }
    }

    fun importCsv(onDone: (Int) -> Unit) {
        val rows = csv?.rows ?: return
        busy = true
        gymApp.appScope.launch {
            val added = gymApp.repository.importMembers(rows)
            withContext(Dispatchers.Main) {
                csv = null
                busy = false
                onDone(added)
            }
        }
    }

    fun clearCsv() { csv = null }

    fun sampleCsv(): java.io.File {
        val dir = java.io.File(getApplication<Application>().cacheDir, "shared").apply { mkdirs() }
        return java.io.File(dir, "GymManager_members_sample.csv").apply { writeText(CsvImport.sample()) }
    }

    fun delete(info: BackupFileInfo) {
        manager.delete(info.file)
        refresh()
    }

    fun shareIntent(info: BackupFileInfo) = manager.shareIntent(info.file)

    fun saveToDownloads(info: BackupFileInfo, onDone: (Boolean) -> Unit) {
        viewModelScope.launch { onDone(manager.copyToDownloads(info.file)) }
    }

    val lastAuto: String? get() = manager.lastAutoBackup()
}

@Composable
fun BackupScreen(onOpenDrawer: () -> Unit, onRestored: () -> Unit, vm: BackupViewModel = viewModel()) {
    val context = LocalContext.current
    var tab by remember { mutableIntStateOf(0) }
    var confirmRestore by remember { mutableStateOf(false) }
    var toDelete by remember { mutableStateOf<BackupFileInfo?>(null) }
    val lang = currentLang()
    val restoreTitle = t("Restore backup")
    val createdMsg = t("Backup created")
    val failedMsg = t("Backup failed")
    val savedMsg = t("Saved to Downloads/GymManager")
    val needPermMsg = t("Could not save to Downloads")

    val downloadsAccess = rememberDownloadsAccess()
    val importedMsg = t("%d members added")
    val csvPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) vm.loadCsv(uri)
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = context.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val idx = c.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && c.moveToFirst()) c.getString(idx) else null
            } ?: "backup.gymbackup"
            vm.load(uri, name)
        }
    }

    Scaffold(topBar = { GymTopBar(t("Backup & Restore"), t("Your data stays on this device"), onMenu = onOpenDrawer) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(t("Export")) }, icon = { Icon(Icons.Default.CloudUpload, null) })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(t("Import")) }, icon = { Icon(Icons.Default.CloudDownload, null) }, modifier = Modifier.testTag("tab_import"))
            }
            if (tab == 0) {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            HeaderRow(Icons.Default.Inventory2, t("Create a full backup"), t("Members, photos, plans, payments, attendance and settings in one .gymbackup file."))
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = { vm.create { context.toast(if (it == "OK") createdMsg else failedMsg) } },
                                enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp).testTag("create_backup")
                            ) {
                                if (vm.busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else { Icon(Icons.Default.CloudUpload, null); Spacer(Modifier.width(8.dp)); Text(t("Create Backup (.gymbackup)")) }
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(
                                t("The latest 10 backups are kept here. Tap the download icon to also save a copy in Downloads, or share it to Drive or WhatsApp."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            HeaderRow(
                                Icons.Default.Schedule,
                                t("Automatic daily backup"),
                                if (!vm.settings.autoBackupEnabled) t("Turned off in Settings")
                                else vm.lastAuto?.let { t("Last automatic backup: %s. A copy is kept in Downloads/GymManager.", DateUtils.formatShort(it)) }
                                    ?: t("Runs once a day and keeps a copy in Downloads/GymManager.")
                            )
                            if (vm.settings.autoBackupEnabled && Downloads.needsPermission(context)) {
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    t("On this Android version, copying to Downloads needs storage access."),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                                )
                                androidx.compose.material3.TextButton(onClick = { downloadsAccess({}) { vm.refresh() } }) { Text(t("Allow storage access")) }
                            }
                        }
                    }
                    item { Text(t("Saved on this device"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 4.dp)) }
                    val list = vm.backups
                    when {
                        list == null -> item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                        list.isEmpty() -> item { EmptyState(Icons.Default.FolderOpen, t("No backups yet"), t("Create your first backup to protect your gym's data.")) }
                        else -> items(list, key = { it.file.name }) { info ->
                            BackupRow(
                                info,
                                onSave = { downloadsAccess({}) { vm.saveToDownloads(info) { ok -> context.toast(if (ok) savedMsg else needPermMsg) } } },
                                onShare = { context.startActivity(vm.shareIntent(info)) },
                                onDelete = { toDelete = info },
                                onRestore = { vm.loadLocal(info.file); tab = 1 }
                            )
                        }
                    }
                }
            } else {
                LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            HeaderRow(Icons.Default.RestorePage, t("Restore from a backup"), t("Pick a .gymbackup file. You'll see a preview before anything changes."))
                            Spacer(Modifier.height(14.dp))
                            OutlinedButton(
                                onClick = { picker.launch(arrayOf("*/*")) },
                                enabled = !vm.busy,
                                modifier = Modifier.fillMaxWidth().height(50.dp)
                            ) {
                                Icon(Icons.Default.FileOpen, null); Spacer(Modifier.width(8.dp)); Text(t("Select .gymbackup File"))
                            }
                        }
                    }
                    vm.importError?.let { err ->
                        item {
                            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = RoundedCornerShape(16.dp)) {
                                Text(t(err), Modifier.fillMaxWidth().padding(16.dp), color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                    if (vm.busy && vm.preview == null) item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                    vm.preview?.let { p ->
                        item { PreviewCard(p, vm.previewName, vm.busy, onCancel = vm::clearPreview, onRestore = { confirmRestore = true }) }
                    }
                    item {
                        AppCard(Modifier.fillMaxWidth().testTag("csv_card")) {
                            HeaderRow(
                                Icons.Default.TableChart, t("Add members from a spreadsheet"),
                                t("Moving from a register or Excel? Save the sheet as CSV with columns: Roll, Name, WhatsApp, Joining Date, From, To, Plan (months), Fee.")
                            )
                            Spacer(Modifier.height(14.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedButton(
                                    onClick = { ShareUtil.shareFile(context, vm.sampleCsv(), "text/csv", "GymManager_members_sample.csv") },
                                    modifier = Modifier.weight(1f).height(48.dp)
                                ) { Text(t("Sample file"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                Button(
                                    onClick = { csvPicker.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "application/vnd.ms-excel", "*/*")) },
                                    enabled = !vm.busy,
                                    modifier = Modifier.weight(1.3f).height(48.dp).testTag("pick_csv")
                                ) { Text(t("Pick CSV file"), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            }
                            vm.csv?.let { r ->
                                Spacer(Modifier.height(12.dp))
                                Text(
                                    t("%d members ready to add", r.rows.size),
                                    style = MaterialTheme.typography.titleSmall, modifier = Modifier.testTag("csv_ready")
                                )
                                if (r.problems.isNotEmpty()) {
                                    Text(
                                        t("%d rows will be skipped:", r.problems.size),
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error
                                    )
                                    r.problems.take(6).forEach { pr ->
                                        Text("• $pr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    if (r.problems.size > 6) Text(t("…and %d more", r.problems.size - 6), style = MaterialTheme.typography.bodySmall)
                                }
                                Spacer(Modifier.height(10.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedButton(onClick = vm::clearCsv, modifier = Modifier.weight(1f)) { Text(t("Cancel")) }
                                    Button(
                                        onClick = { vm.importCsv { n -> context.toast(importedMsg.format(n)) } },
                                        enabled = r.rows.isNotEmpty() && !vm.busy,
                                        modifier = Modifier.weight(1.3f).testTag("import_csv")
                                    ) { Text(t("Add %d members", r.rows.size), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                    }
                    item {
                        Row(Modifier.padding(4.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Default.Security, null, Modifier.size(18.dp), tint = StatusGreen)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                t("Safe restore: a safety snapshot of your current data is saved automatically before restoring, and the restore is all-or-nothing."),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    if (confirmRestore) {
        ConfirmDialog(
            title = t("Replace all current data?"),
            message = t("Everything in the app will be replaced with this backup. A safety snapshot of your current data is created first."),
            confirmText = t("Restore"),
            destructive = true,
            onConfirm = {
                confirmRestore = false
                BiometricHelper.guard(context, vm.settings, ProtectedAction.RESTORE_BACKUP, restoreTitle) {
                    vm.restore { ok, msg ->
                        context.toast(if (ok) I18n.tr(lang, "Restored %d members successfully", msg.toIntOrNull() ?: 0) else I18n.tr(lang, msg), long = true)
                        if (ok) onRestored()
                    }
                }
            },
            onDismiss = { confirmRestore = false }
        )
    }
    toDelete?.let { info ->
        ConfirmDialog(
            title = t("Delete backup?"),
            message = t("%s will be removed from this device.", info.file.name),
            confirmText = t("Delete"),
            destructive = true,
            onConfirm = { vm.delete(info); toDelete = null },
            onDismiss = { toDelete = null }
        )
    }
    LaunchedEffect(Unit) { vm.refresh() }
}

@Composable
private fun HeaderRow(icon: ImageVector, title: String, subtitle: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackupRow(info: BackupFileInfo, onSave: () -> Unit, onShare: () -> Unit, onDelete: () -> Unit, onRestore: () -> Unit) {
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 14.dp, top = 12.dp, bottom = 8.dp, end = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Description, null, tint = if (info.isSafety) StatusAmber else MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(info.file.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${DateUtils.formatTimestamp(info.createdAt)} • ${info.sizeKb} KB",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onSave) { Icon(Icons.Default.Download, t("Save to Downloads")) }
            IconButton(onClick = onShare) { Icon(Icons.Default.Share, t("Share backup")) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.DeleteOutline, t("Delete backup")) }
        }
        Row(Modifier.padding(top = 6.dp, end = 10.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            if (info.isSafety) Pill(t("SAFETY SNAPSHOT"), StatusAmber)
            info.counts?.let { c ->
                Text(
                    t("%d members • %d cycles • %d attendance", c.members, c.paymentCycles, c.attendance),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } ?: Spacer(Modifier.weight(1f))
            androidx.compose.material3.TextButton(onClick = onRestore) { Text(t("Restore")) }
        }
    }
}

@Composable
private fun PreviewCard(p: GymBackup, name: String, busy: Boolean, onCancel: () -> Unit, onRestore: () -> Unit) {
    val c = p.counts
    AppCard(Modifier.fillMaxWidth().testTag("backup_preview")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(t("Backup Preview"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Pill(t("VALID"), StatusGreen)
        }
        Text(name, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(14.dp))
        Row {
            LabeledValue("GYM NAME", p.gymName, Modifier.weight(1f))
            LabeledValue("CREATED", DateUtils.formatTimestamp(p.createdAt), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            LabeledValue("MEMBERS", c.members.toString(), Modifier.weight(1f))
            LabeledValue("MEMBERSHIPS", c.memberships.toString(), Modifier.weight(1f))
            LabeledValue("PHOTOS", c.photos.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        Row {
            LabeledValue("ATTENDANCE", c.attendance.toString(), Modifier.weight(1f))
            LabeledValue("PAYMENT CYCLES", c.paymentCycles.toString(), Modifier.weight(2f))
        }
        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f).height(48.dp)) { Text(t("Cancel")) }
            Button(
                onClick = onRestore,
                enabled = !busy,
                modifier = Modifier.weight(1.3f).height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)
            ) {
                if (busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onError)
                else Text(t("Restore Now"))
            }
        }
    }
}
