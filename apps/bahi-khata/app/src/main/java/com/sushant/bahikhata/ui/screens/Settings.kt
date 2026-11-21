@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.App
import com.sushant.bahikhata.BuildConfig
import com.sushant.bahikhata.R
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import com.sushant.bahikhata.work.Notify
import com.sushant.bahikhata.work.Workers
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Composable
fun rememberNotifState(): Pair<Boolean, () -> Unit> {
    val ctx = LocalContext.current
    var allowed by remember { mutableStateOf(Notify.allowed(ctx)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { allowed = Notify.allowed(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { allowed = Notify.allowed(ctx) }
    return allowed to {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        else if (!allowed) ctx.startActivity(Intent(AndroidSettings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(AndroidSettings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppVm) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val app = ctx.applicationContext as App
    val s by vm.settings.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val rec by vm.recurring.collectAsStateWithLifecycle()
    val (notifOk, askNotif) = rememberNotifState()
    var themePick by rememberSaveable { mutableStateOf(false) }
    var timePick by rememberSaveable { mutableStateOf(false) }
    var lockPick by rememberSaveable { mutableStateOf(false) }
    var startPick by rememberSaveable { mutableStateOf(false) }
    var duePick by rememberSaveable { mutableStateOf(false) }
    var reset by rememberSaveable { mutableStateOf(false) }
    val canLock = androidx.biometric.BiometricManager.from(ctx).canAuthenticate(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS ||
        (ctx.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure

    fun notifSwitch(on: Boolean, apply: () -> Unit) { apply(); if (on && !notifOk) askNotif() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Settings") }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("settings_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionLabel("Appearance") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingNav(Icons.Outlined.DarkMode, "Theme", when (s.theme) { ThemeMode.SYSTEM -> "Follow phone"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" }) { themePick = true }
                SettingSwitch(Icons.Outlined.VisibilityOff, "Hide amounts", "Show ₹ •••• everywhere until you tap the eye", s.hideAmounts, { on -> vm.prefs.update { it.copy(hideAmounts = on) } }, "hide")
            } }
            item { SectionLabel("Security") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingSwitch(Icons.Outlined.Fingerprint, "App lock", if (canLock) "Fingerprint or phone PIN" else "Set a screen lock on your phone first", s.lockOn && canLock, { on -> if (canLock) vm.prefs.update { it.copy(lockOn = on) } else snack.show("Set a screen lock in phone settings first") }, "lock")
                if (s.lockOn) SettingNav(Icons.Outlined.Timer, "Lock after", when (s.lockAfterSec) { 0 -> "Immediately"; 60 -> "1 minute"; 300 -> "5 minutes"; else -> "${s.lockAfterSec / 60} minutes" }) { lockPick = true }
                SettingSwitch(Icons.Outlined.Screenshot, "Block screenshots", "Keeps amounts out of screenshots and recent apps", s.secureScreens, { on -> vm.prefs.update { it.copy(secureScreens = on) } }, "secure")
            } }
            item { SectionLabel("Entries") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingSwitch(Icons.Outlined.Sms, "SMS suggestions", if (app.sms.enabled()) "On · nothing is saved without your OK" else if (s.smsOn) "Needs SMS permission" else "Off", app.sms.enabled(), { on ->
                    if (on) nav.go("sms_setup") else { vm.prefs.update { it.copy(smsOn = false) }; app.sms.stopObserver() }
                }, "sms")
                SettingNav(Icons.Outlined.Category, "Categories", "${cats.count { !it.archived }} categories") { nav.go("categories") }
                SettingNav(Icons.Outlined.Repeat, "Repeating entries", if (rec.isEmpty()) "Rent, EMI, subscriptions" else "${rec.count { it.active }} active") { nav.go("recurring") }
                SettingNav(Icons.Outlined.Savings, "Budgets & savings goals") { nav.go("budgets") }
                SettingNav(Icons.Outlined.CalendarMonth, "Budget month starts on", if (s.monthStartDay == 1) "1st of every month" else "${ordinal(s.monthStartDay)} of every month (e.g. salary day)") { startPick = true }
            } }
            item { SectionLabel("Notifications") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingSwitch(Icons.Outlined.Notifications, "Daily reminder", "If nothing was added by ${String.format(java.util.Locale.ROOT, "%d:%02d %s", ((s.dailyReminderMin / 60 + 11) % 12) + 1, s.dailyReminderMin % 60, if (s.dailyReminderMin >= 720) "PM" else "AM")}", s.dailyReminderOn, { on ->
                    notifSwitch(on) { vm.prefs.update { it.copy(dailyReminderOn = on) }; Workers.scheduleReminder(ctx) }
                }, "daily")
                if (s.dailyReminderOn) SettingNav(Icons.Outlined.Schedule, "Reminder time") { timePick = true }
                SettingSwitch(Icons.Outlined.Event, "Card due reminders", dueText(s.dueReminderDays), s.dueRemindersOn, { on -> notifSwitch(on) { vm.prefs.update { it.copy(dueRemindersOn = on) }; Workers.runNow(ctx) } }, "due")
                if (s.dueRemindersOn) SettingNav(Icons.Outlined.Tune, "When to remind", dueText(s.dueReminderDays)) { duePick = true }
                SettingSwitch(Icons.Outlined.PieChart, "Budget alerts", "At 80% and 100% of a budget", s.budgetAlertsOn, { on -> notifSwitch(on) { vm.prefs.update { it.copy(budgetAlertsOn = on) } } }, "budget")
                if (!notifOk && (s.dailyReminderOn || s.dueRemindersOn || s.budgetAlertsOn || s.smsOn)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Notifications are blocked", color = LocalBk.current.warn, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                        TextButton(onClick = askNotif) { Text("Allow") }
                    }
                }
            } }
            item { SectionLabel("Data") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingNav(Icons.Outlined.CloudUpload, "Backup & restore", if (s.driveOn) "Drive backup on" + (if (s.lastDriveBackup > 0) " · " + Dates.short(s.lastDriveBackup) else "") else "Backup file · Google Drive off") { nav.go("backup") }
                SettingNav(Icons.Outlined.FileDownload, "Export", "CSV · Excel · PDF") { nav.go("export") }
                SettingNav(Icons.Outlined.BugReport, "Diagnostics", "Crash log and usage on this phone") { nav.go("diagnostics") }
            } }
            item { SectionLabel("About") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingNav(Icons.Outlined.Info, "About Bahi Khata", "Version ${BuildConfig.VERSION_NAME}") { nav.go("about") }
                ListRow("Reset app", "Delete all entries, accounts and settings", Modifier.testTag("reset"), leading = { Icon(Icons.Outlined.DeleteForever, null, tint = MaterialTheme.colorScheme.error) }, onClick = { reset = true })
            } }
        }
    }
    if (themePick) PickSheet("Theme", ThemeMode.entries.toList(), { when (it) { ThemeMode.SYSTEM -> "Follow phone"; ThemeMode.LIGHT -> "Light"; ThemeMode.DARK -> "Dark" } }, onPick = { vm.setTheme(it) }, onDismiss = { themePick = false })
    if (startPick) PickSheet("Budget month starts on", (1..28).toList(), { if (it == 1) "1st (calendar month)" else ordinal(it) }, onPick = { d -> vm.prefs.update { it.copy(monthStartDay = d) } }, onDismiss = { startPick = false })
    if (duePick) DueDaysDialog(s.dueReminderDays, { v -> vm.prefs.update { it.copy(dueReminderDays = v) } }) { duePick = false }
    if (lockPick) PickSheet("Lock after", listOf(0, 60, 300, 900), { when (it) { 0 -> "Immediately"; 60 -> "1 minute"; 300 -> "5 minutes"; else -> "15 minutes" } }, onPick = { v -> vm.prefs.update { it.copy(lockAfterSec = v) } }, onDismiss = { lockPick = false })
    if (timePick) {
        val st = rememberTimePickerState(s.dailyReminderMin / 60, s.dailyReminderMin % 60, false)
        AlertDialog(onDismissRequest = { timePick = false }, title = { Text("Reminder time") }, text = { TimePicker(st) },
            confirmButton = { TextButton(onClick = { vm.prefs.update { it.copy(dailyReminderMin = st.hour * 60 + st.minute) }; Workers.scheduleReminder(ctx); timePick = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { timePick = false }) { Text("Cancel") } })
    }
    if (reset) ConfirmDialog("Reset Bahi Khata?", "Everything on this phone is deleted: entries, accounts, udhar, receipts and settings. Make a backup first if you may need it.", "Delete everything",
        onConfirm = { vm.launch { vm.repo.wipeAll(); vm.prefs.update { Settings() }; snack.show("App reset") }; nav.navigate("setup") { popUpTo(0) } }, onDismiss = { reset = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategoriesScreen(vm: AppVm) {
    val snack = LocalSnack.current
    val bk = LocalBk.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    var kind by rememberSaveable { mutableStateOf("EXPENSE") }
    var edit by rememberSaveable(stateSaver = recordSaver<Category>()) { mutableStateOf<Category?>(null) }
    var merge by rememberSaveable(stateSaver = recordSaver<Category>()) { mutableStateOf<Category?>(null) }
    val list = categories.filter { it.kind == kind }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Categories") { Act(Icons.Outlined.Add, "Add category", "add_cat") { edit = Category(name = "", kind = kind, icon = "more", colorIdx = list.size % 10, sort = list.size) } } }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { Segments(listOf("Expense", "Income"), if (kind == "INCOME") 1 else 0, { kind = if (it == 1) "INCOME" else "EXPENSE" }, modifier = Modifier.padding(16.dp)) }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                list.forEach { c ->
                    val (bg, fg) = bk.cat[c.colorIdx.mod(bk.cat.size)]
                    val n = entries.count { it.categoryId == c.id }
                    ListRow(c.name + if (c.archived) " (hidden)" else "", "$n ${if (n == 1) "entry" else "entries"}", Modifier.testTag("catrow_${c.name}"), leading = { IconBadge(Ico.cat(c.icon), bg, fg) }, trailing = { Chev() }, onClick = { edit = c })
                }
            } }
            item { Text("Tap a category to rename it, change its icon or order, hide it or merge it into another.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 10.dp)) }
        }
    }
    edit?.let { c ->
        var name by remember(c) { mutableStateOf(c.name) }
        var icon by remember(c) { mutableStateOf(c.icon) }
        var color by remember(c) { mutableIntStateOf(c.colorIdx) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(if (c.id == 0L) "New category" else "Edit category") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextIn(name, { name = it }, "Name", tag = "cname")
                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Ico.catKeys.forEach { k -> Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(if (icon == k) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant)
                        .selectable(icon == k, role = Role.RadioButton) { icon = k }.semantics { contentDescription = "Icon " + k.replace('_', ' ') }, contentAlignment = Alignment.Center) { Icon(Ico.cat(k), k) } }
                }
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    bk.cat.forEachIndexed { i, (b, f) -> Box(Modifier.size(48.dp).clip(CircleShape).background(b).border(if (color == i) 3.dp else 1.dp, if (color == i) bk.saffron else f, CircleShape)
                        .selectable(color == i, role = Role.RadioButton) { color = i }.semantics { contentDescription = "Colour ${i + 1}" }) }
                }
                if (c.id > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Order", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { vm.launch { vm.repo.moveCategory(c, -1) } }, modifier = Modifier.testTag("cat_up")) { Icon(Icons.Outlined.ArrowUpward, null); Spacer(Modifier.width(4.dp)); Text("Up") }
                    OutlinedButton(onClick = { vm.launch { vm.repo.moveCategory(c, 1) } }, modifier = Modifier.testTag("cat_down")) { Icon(Icons.Outlined.ArrowDownward, null); Spacer(Modifier.width(4.dp)); Text("Down") }
                }
                if (c.id > 0) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.launch { vm.repo.saveCategory(c.copy(archived = !c.archived)) }; edit = null }) { Text(if (c.archived) "Show again" else "Hide") }
                    OutlinedButton(onClick = { merge = c; edit = null }) { Text("Merge into…") }
                }
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = { if (name.isNotBlank()) vm.launch { vm.repo.saveCategory(c.copy(name = name.trim(), icon = icon, colorIdx = color)) }; edit = null }, modifier = Modifier.testTag("cat_ok")) { Text("Save") } },
            dismissButton = { TextButton(onClick = { edit = null }) { Text("Cancel") } })
    }
    merge?.let { c ->
        PickSheet("Merge ${c.name} into", categories.filter { it.kind == c.kind && it.id != c.id && !it.archived }, { it.name }, onPick = { t -> vm.launch { vm.repo.mergeCategory(c, t.id); snack.show("${c.name} merged into ${t.name}") } }, onDismiss = { merge = null })
    }
}

@Composable
fun RecurringScreen(vm: AppVm) {
    val snack = LocalSnack.current
    val rec by vm.recurring.collectAsStateWithLifecycle()
    val accs by vm.accMap.collectAsStateWithLifecycle()
    val cats by vm.catMap.collectAsStateWithLifecycle()
    var edit by rememberSaveable(stateSaver = recordSaver<Recurring>()) { mutableStateOf<Recurring?>(null) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Repeating entries") }, contentWindowInsets = WindowInsets(0)) { pad ->
        if (rec.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Outlined.Repeat, "No repeating entries", "When adding an entry, set Repeat to monthly for rent, EMI or subscriptions — it's added on its own.")
        } else LazyColumn(Modifier.padding(pad).fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                rec.forEach { r ->
                    ListRow((r.merchant ?: r.categoryId?.let { cats[it]?.name } ?: Export.typeName(r.type)) + " · " + Money.fmt(r.amountPaise),
                        "${r.rule.lowercase().replaceFirstChar { it.uppercase() }} · ${accs[r.accountId]?.name ?: "—"} · next ${Dates.dayMonth(LocalDate.ofEpochDay(r.nextEpochDay))}",
                        Modifier.testTag("rec_${r.id}"), leading = { Icon(Icons.Outlined.Repeat, null) },
                        trailing = { Switch(r.active, { on -> vm.launch { vm.repo.saveRecurring(r.copy(active = on)) } }) }, onClick = { edit = r })
                }
            } }
        }
    }
    edit?.let { r ->
        var amt by remember(r) { mutableStateOf(Money.input(r.amountPaise)) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text("Repeating entry") }, text = { MoneyField(amt, { amt = it }, "Amount", tag = "ramount") },
            confirmButton = { TextButton(onClick = { Money.parse(amt)?.takeIf { it > 0 }?.let { v -> vm.launch { vm.repo.saveRecurring(r.copy(amountPaise = v)) } }; edit = null }) { Text("Save") } },
            dismissButton = { Row { TextButton(onClick = { vm.launch { vm.repo.deleteRecurring(r) }; snack.show("Stopped repeating", "Undo") { vm.launch { vm.repo.saveRecurring(r) } }; edit = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { edit = null }) { Text("Cancel") } } })
    }
}

@Composable
private fun PasswordDialog(title: String, confirm: Boolean, onDone: (CharArray) -> Unit, onDismiss: () -> Unit) {
    var p1 by rememberSaveable { mutableStateOf("") }
    var p2 by rememberSaveable { mutableStateOf("") }
    val ok = p1.length >= 6 && (!confirm || p1 == p2)
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(if (confirm) "Backups are locked with this password. You'll need it to restore on a new phone — Bahi Khata can't recover it." else "Type the backup password.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedTextField(p1, { p1 = it }, label = { Text("Password (6+ characters)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("pw1"))
            if (confirm) OutlinedTextField(p2, { p2 = it }, label = { Text("Same password again") }, singleLine = true, visualTransformation = PasswordVisualTransformation(),
                isError = p2.isNotEmpty() && p1 != p2, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth().testTag("pw2"))
        }
    }, confirmButton = { TextButton(onClick = { onDone(p1.toCharArray()); onDismiss() }, enabled = ok, modifier = Modifier.testTag("pw_ok")) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun BackupScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val s by vm.settings.collectAsStateWithLifecycle()
    var pwFor by rememberSaveable { mutableStateOf<String?>(null) }   // save / restore / drive / drive_restore
    var pendingUri by rememberSaveable { mutableStateOf<Uri?>(null) }
    var busy by rememberSaveable { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf<(() -> Unit)?>(null) }
    var freqPick by rememberSaveable { mutableStateOf(false) }
    val hasPw = remember(s.driveOn, pwFor) { Backup.loadPassword(ctx) != null }

    val create = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { u -> if (u != null) { pendingUri = u; pwFor = "save" } }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) { pendingUri = u; pwFor = "restore" } }
    val consent = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) { vm.prefs.update { it.copy(driveOn = true) }; Workers.scheduleDrive(ctx); Workers.driveNow(ctx); snack.show("Google Drive backup is on") }
        else snack.show("Drive backup stays off")
    }

    fun turnOnDrive() {
        busy = "Connecting to Google Drive…"
        scope.launch {
            val res = withContext(Dispatchers.IO) { runCatching { Drive.token(ctx) } }
            busy = null
            res.onSuccess { (tok, pi) ->
                if (tok != null) { vm.prefs.update { it.copy(driveOn = true) }; Workers.scheduleDrive(ctx); Workers.driveNow(ctx); snack.show("Google Drive backup is on") }
                else if (pi != null) consent.launch(IntentSenderRequest.Builder(pi.intentSender).build())
            }.onFailure { snack.show("Google sign-in isn't available on this phone. Drive backup needs Google Play services.") }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Backup & restore") }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            busy?.let { LinearProgressIndicator(Modifier.fillMaxWidth()); Text(it, Modifier.padding(16.dp, 8.dp), style = MaterialTheme.typography.bodySmall) }
            SectionLabel("Google Drive")
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingSwitch(Icons.Outlined.Cloud, "Auto-backup to Google Drive", if (s.driveOn) "On · saved in Bahi Khata's private Drive folder" else "Off", s.driveOn, { on ->
                    if (!on) { vm.prefs.update { it.copy(driveOn = false) }; Workers.scheduleDrive(ctx) }
                    else if (!hasPw) pwFor = "drive" else turnOnDrive()
                }, "drive")
                if (s.driveOn) {
                    SettingNav(Icons.Outlined.Schedule, "How often", if (s.driveFreq == "WEEKLY") "Weekly" else "Daily") { freqPick = true }
                    SettingSwitch(Icons.Outlined.Wifi, "Wi-Fi only", "Saves mobile data", s.driveWifiOnly, { on -> vm.prefs.update { it.copy(driveWifiOnly = on) }; Workers.scheduleDrive(ctx) }, "wifi")
                    ListRow(if (s.lastDriveBackup > 0) Dates.full(s.lastDriveBackup) else "Not yet", "Last backup", leading = { Icon(Icons.Outlined.History, null) })
                }
            }
            if (s.driveOn) Row(Modifier.padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedPillButton("Back up now", Icons.Outlined.Backup, { busy = "Backing up…"; vm.background { val r = Drive.backup(ctx); busy = null
                    snack.show(when (r) { is Drive.Res.Ok -> "Backed up to Google Drive"; is Drive.Res.NeedsSignIn -> "Sign in to Google Drive again"; is Drive.Res.Fail -> "Backup didn't finish: ${r.msg}" }) } }, tag = "drive_now")
                OutlinedPillButton("Restore from Drive", Icons.Outlined.CloudDownload, { pwFor = "drive_restore" }, tag = "drive_restore")
            }
            SectionLabel("Backup file")
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingNav(Icons.Outlined.Save, "Save backup file", "Encrypted with your password" + if (s.lastFileBackup > 0) " · last " + Dates.short(s.lastFileBackup) else "") {
                    create.launch("BahiKhata-${LocalDate.now()}.bkb")
                }
                SettingNav(Icons.Outlined.Restore, "Restore from file", "Choose a .bkb backup") { open.launch(arrayOf("*/*")) }
            }
            Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalBk.current.info).padding(12.dp)) {
                Icon(Icons.Outlined.Lock, null, tint = LocalBk.current.onInfo); Spacer(Modifier.width(10.dp))
                Text("Your data stays on this phone. Backups (file or Drive) are encrypted with your password before they leave it. Drive backups go to a hidden folder only Bahi Khata can read.",
                    style = MaterialTheme.typography.bodySmall, color = LocalBk.current.onInfo)
            }
        }
    }
    if (freqPick) PickSheet("How often", listOf("DAILY" to "Daily", "WEEKLY" to "Weekly"), { it.second }, onPick = { p -> vm.prefs.update { it.copy(driveFreq = p.first) }; Workers.scheduleDrive(ctx) }, onDismiss = { freqPick = false })
    pwFor?.let { mode ->
        PasswordDialog(if (mode == "save" || mode == "drive") "Set backup password" else "Backup password", confirm = mode == "save" || mode == "drive", onDone = { pw ->
            when (mode) {
                "save" -> { val u = pendingUri ?: return@PasswordDialog; busy = "Saving backup…"; vm.background {
                    runCatching { val b = Backup.build(ctx, pw); withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(u)!!.use { it.write(b) } } }
                        .onSuccess { vm.prefs.update { it.copy(lastFileBackup = System.currentTimeMillis()) }; Backup.savePassword(ctx, pw); snack.show("Backup saved") }
                        .onFailure { snack.show("Couldn't save the backup. Check the phone has free space and try again.") }
                    busy = null } }
                "restore" -> { val u = pendingUri ?: return@PasswordDialog; confirmRestore = { busy = "Restoring…"; vm.background {
                    runCatching { val b = withContext(Dispatchers.IO) { ctx.contentResolver.openInputStream(u)!!.use { it.readBytes() } }; Backup.restore(ctx, b, pw) }
                        .onSuccess { snack.show("Restored ${it.entries} entries and ${it.accounts} accounts") }.onFailure { snack.show(plainError(it, "Restore didn't work. Nothing on this phone was changed.")) }
                    busy = null } } }
                "drive" -> { Backup.savePassword(ctx, pw); turnOnDrive() }
                "drive_restore" -> confirmRestore = { busy = "Downloading from Drive…"; vm.background {
                    runCatching {
                        val (remote, res) = Drive.latest(ctx)
                        if (res !is Drive.Res.Ok) throw IllegalStateException(if (res is Drive.Res.Fail) res.msg else "Sign in to Google Drive again")
                        val r = remote ?: throw IllegalStateException("No backup found on Google Drive")
                        Backup.restore(ctx, Drive.download(ctx, r.id), pw)
                    }.onSuccess { snack.show("Restored ${it.entries} entries from Drive") }.onFailure { snack.show(plainError(it, "Restore didn't work. Nothing on this phone was changed.")) }
                    busy = null } }
            }
        }, onDismiss = { pwFor = null })
    }
    confirmRestore?.let { go -> ConfirmDialog("Replace everything on this phone?", "Restoring replaces all current entries, accounts and settings with the backup.", "Restore", onConfirm = go, onDismiss = { confirmRestore = null }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val accs by vm.accMap.collectAsStateWithLifecycle()
    val cats by vm.catMap.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf("THIS") }
    var from by rememberSaveable { mutableLongStateOf(Dates.startOf(Dates.today().withDayOfMonth(1))) }
    var to by rememberSaveable { mutableLongStateOf(Dates.endOf(Dates.today())) }
    var showRange by rememberSaveable { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<ByteArray, String>?>(null) }
    val r: LongRange = when (period) {
        "THIS" -> Dates.monthRange(YearMonth.now())
        "LAST" -> Dates.monthRange(YearMonth.now().minusMonths(1))
        "YEAR" -> Dates.startOf(Dates.today().withDayOfYear(1))..Dates.endOf(Dates.today())
        "ALL" -> 0L..Long.MAX_VALUE
        else -> from..to
    }
    val rows = Export.rows(entries, accs, cats, r)
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { u ->
        val p = pending; if (u != null && p != null) scope.launch {
            runCatching { withContext(Dispatchers.IO) { ctx.contentResolver.openOutputStream(u)!!.use { it.write(p.first) } } }.onSuccess { snack.show("Saved ${p.second}") }.onFailure { snack.show("Couldn't save") }
        }
    }
    fun build(kind: String): Pair<ByteArray, String> {
        val label = when (period) { "THIS" -> YearMonth.now().toString(); "LAST" -> YearMonth.now().minusMonths(1).toString(); "YEAR" -> Dates.today().year.toString(); "ALL" -> "all"; else -> "${Dates.date(from)}_${Dates.date(to)}" }
        return when (kind) {
            "csv" -> Export.csv(rows) to "BahiKhata-$label.csv"
            "xlsx" -> Export.xlsx(rows) to "BahiKhata-$label.xlsx"
            else -> Export.pdf(ctx, "Statement · $label", rows, entries, r, accs, cats) to "BahiKhata-$label.pdf"
        }
    }
    fun mime(n: String) = when { n.endsWith(".csv") -> "text/csv"; n.endsWith(".xlsx") -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; else -> "application/pdf" }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Export") }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            SectionLabel("Period")
            FlowRowSafe {
                listOf("THIS" to "This month", "LAST" to "Last month", "YEAR" to "This year", "ALL" to "All time", "CUSTOM" to "Custom dates").forEach { (k, l) -> Pill(l, period == k, { period = k; if (k == "CUSTOM") showRange = true }, modifier = Modifier.testTag("exp_$k")) }
            }
            if (period == "CUSTOM") Text("${Dates.dayMonthYear(Dates.date(from))} – ${Dates.dayMonthYear(Dates.date(to))}", Modifier.padding(18.dp, 4.dp).clickable { showRange = true }, color = MaterialTheme.colorScheme.primary)
            Text("${rows.size} entries", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 8.dp))
            SectionLabel("Format")
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                listOf(Triple("csv", "CSV", "Opens in any spreadsheet app"), Triple("xlsx", "Excel (.xlsx)", "Formatted sheet with amounts as numbers"), Triple("pdf", "PDF statement", "Totals, categories and every entry")).forEach { (k, t, d) ->
                    ListRow(t, d, Modifier.testTag("fmt_$k"), leading = { Icon(if (k == "pdf") Icons.Outlined.PictureAsPdf else Icons.Outlined.TableView, null) }, trailing = {
                        Row {
                            IconButton(onClick = { scope.launch { val b = withContext(Dispatchers.IO) { build(k) }; pending = b; save.launch(b.second) } }) { Icon(Icons.Outlined.Save, "Save $t") }
                            IconButton(onClick = { scope.launch { val f = withContext(Dispatchers.IO) { val b = build(k)
                                File(ctx.cacheDir, "share").apply { mkdirs() }.let { File(it, b.second) }.also { it.writeBytes(b.first) } }; shareFile(ctx, f, mime(f.name), "Share $t") } }) { Icon(Icons.Outlined.Share, "Share $t") }
                        }
                    })
                }
            }
            Text("Files are saved where you choose. Nothing is uploaded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 10.dp))
        }
    }
    if (showRange) {
        val st = rememberDateRangePickerState(initialSelectedStartDateMillis = Dates.date(from).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), initialSelectedEndDateMillis = Dates.date(to).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showRange = false }, confirmButton = { TextButton(onClick = {
            st.selectedStartDateMillis?.let { from = Dates.startOf(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            st.selectedEndDateMillis?.let { to = Dates.endOf(java.time.Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            showRange = false
        }) { Text("OK") } }, dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } }) { DateRangePicker(st, Modifier.weight(1f)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FlowRowSafe(content: @Composable () -> Unit) {
    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { content() }
}

@Composable
fun DiagnosticsScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    var log by remember { mutableStateOf(Diagnostics.crashLog(ctx)) }
    val counters = remember { Diagnostics.counters(ctx) }
    val crashes = log.lines().count { it.startsWith("=== ") }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Diagnostics") }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState())) {
            BkCard(Modifier.padding(16.dp).fillMaxWidth()) {
                ListRow(if (crashes == 0) "No crashes" else "$crashes ${if (crashes == 1) "crash" else "crashes"} logged", "Kept on this phone only", leading = { Icon(Icons.Outlined.BugReport, null) })
            }
            SectionLabel("Usage on this phone")
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                listOf("entry_added" to "Entries added", "entry_edited" to "Entries edited", "sms_ok" to "SMS saved from the list", "sms_ok_from_notification" to "SMS saved from notification",
                    "sms_scanned" to "SMS checked", "pdf_report" to "PDF reports", "whatsapp_reminder" to "WhatsApp reminders").forEach { (k, l) ->
                    ListRow(l, null, trailing = { Text("${counters[k] ?: 0}", style = MaterialTheme.typography.labelLarge) })
                }
                (counters["cold_start_ms_avg"] as? Long)?.let { ListRow("Average start-up", null, trailing = { Text("$it ms", style = MaterialTheme.typography.labelLarge) }) }
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedPillButton("Export crash log", Icons.Outlined.Share, {
                    vm.background {
                        val f = File(ctx.cacheDir, "share").apply { mkdirs() }.let { File(it, "bahikhata-crash-log.txt") }
                        f.writeText(log.ifBlank { "No crashes logged." })
                        withContext(Dispatchers.Main) { shareFile(ctx, f, "text/plain", "Share crash log") }
                    }
                }, tag = "export_log")
                if (crashes > 0) OutlinedPillButton("Clear", Icons.Outlined.Delete, { Diagnostics.clear(ctx); log = ""; snack.show("Crash log cleared") }, tag = "clear_log")
            }
            if (log.isNotBlank()) Text(log.takeLast(4000), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(16.dp))
            Text("No messages, notes or amounts are logged. Nothing leaves the phone unless you share it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 8.dp))
        }
    }
    @Suppress("UNUSED_VARIABLE") val u = vm
}

@Composable
fun AboutScreen() {
    val ctx = LocalContext.current
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("About") }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Gap(16)
            Image(painterResource(R.drawable.ic_app_round), null, Modifier.size(88.dp))
            Gap(10)
            Text("Bahi Khata", style = MaterialTheme.typography.headlineSmall)
            Text("Version ${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Gap(10)
            SectionLabel("Developer")
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                ListRow("Developed by Sushant Kumar", null, leading = { Icon(Icons.Outlined.Person, null) })
                ListRow("sushant.sare@gmail.com", "Tap to email", Modifier.testTag("email"), leading = { Icon(Icons.Outlined.Mail, null) }, onClick = {
                    runCatching { ctx.startActivity(Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com")).putExtra(Intent.EXTRA_SUBJECT, "Bahi Khata").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                })
            }
            SectionLabel("Privacy")
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                ListRow("Offline · no ads · no tracking", "Your data is encrypted on this phone. Only Google Drive backup (if you turn it on) uses the internet, and it's encrypted with your password first.",
                    leading = { Icon(Icons.Outlined.Shield, null) })
                ListRow("Open-source licences", "Jetpack Compose, Room, SQLCipher, WorkManager, Coil, kotlinx — Apache 2.0 / BSD", leading = { Icon(Icons.Outlined.Description, null) })
            }
            Gap(24)
        }
    }
}

/** Our own messages (wrong password, no backup found) are shown as they are; anything technical gets a plain sentence. */
fun plainError(t: Throwable, fallback: String): String =
    if ((t is IllegalStateException || t is IllegalArgumentException) && !t.message.isNullOrBlank() && t.message!!.length < 90) t.message!! else fallback

fun ordinal(n: Int): String = n.toString() + when { n % 100 in 11..13 -> "th"; n % 10 == 1 -> "st"; n % 10 == 2 -> "nd"; n % 10 == 3 -> "rd"; else -> "th" }

private fun dueText(days: String): String {
    val d = days.split(',').mapNotNull { it.trim().toIntOrNull() }.sortedDescending()
    if (d.isEmpty()) return "No reminders chosen"
    return d.joinToString(", ") { if (it == 0) "on the due day" else if (it == 1) "1 day before" else "$it days before" }.replaceFirstChar { it.uppercase() }
}

/** Which days before a card's due date to remind (any of 7, 5, 3, 2, 1, due day). */
@Composable
private fun DueDaysDialog(current: String, onDone: (String) -> Unit, onDismiss: () -> Unit) {
    var sel by rememberSaveable { mutableStateOf(current.split(',').mapNotNull { it.trim().toIntOrNull() }) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Remind me") }, text = {
        Column {
            listOf(7, 5, 3, 2, 1, 0).forEach { d ->
                val on = d in sel
                Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).toggleable(on, role = Role.Checkbox) { sel = if (it) sel + d else sel - d }.testTag("dueday_$d"),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(on, null); Spacer(Modifier.width(8.dp)); Text(if (d == 0) "On the due day" else if (d == 1) "1 day before" else "$d days before")
                }
            }
        }
    }, confirmButton = { TextButton(enabled = sel.isNotEmpty(), onClick = { onDone(sel.sortedDescending().joinToString(",")); onDismiss() }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
