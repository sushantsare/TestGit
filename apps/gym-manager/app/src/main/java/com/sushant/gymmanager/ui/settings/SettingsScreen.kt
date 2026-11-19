package com.sushant.gymmanager.ui.settings

import com.sushant.gymmanager.util.toast
import android.app.Application
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.auth.ProtectedAction
import com.sushant.gymmanager.auth.findFragmentActivity
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.ThemeMode
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.work.DailyWorker
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.AppTimePickerDialog
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.theme.LocalExtraColors
import com.sushant.gymmanager.util.DateUtils
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.sushant.gymmanager.ui.components.rememberDownloadsAccess
import com.sushant.gymmanager.ui.components.rememberPermissionRequester
import com.sushant.gymmanager.ui.navigation.NavRequests
import com.sushant.gymmanager.ui.navigation.RegisterLeaveGuard
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.work.Notifications

data class FeeFields(val f1: String, val f3: String, val f6: String, val f12: String)

class SettingsViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as GymApp).repository
    private val json = Json { ignoreUnknownKeys = true }

    var saved by mutableStateOf<GymSettingsEntity?>(null)
        private set
    var draft by mutableStateOf(GymSettingsEntity())
    var fees by mutableStateOf(FeeFields("", "", "", ""))
    var expiringDays by mutableStateOf("7")

    init { reload() }

    fun reload(restore: Boolean = true) {
        viewModelScope.launch {
            val s = repo.getSettings()
            saved = s; draft = s
            fees = FeeFields(fmt(s.fee1Month), fmt(s.fee3Months), fmt(s.fee6Months), fmt(s.fee12Months))
            expiringDays = s.expiringSoonDays.toString()
            // Unsaved edits come back if Android closed the app in the background.
            if (restore) {
                handle.get<String>("draft")?.let { d -> runCatching { json.decodeFromString(GymSettingsEntity.serializer(), d) }.getOrNull()?.let { draft = it } }
                handle.get<Array<String>>("fees")?.takeIf { it.size == 4 }?.let { fees = FeeFields(it[0], it[1], it[2], it[3]) }
                handle.get<String>("days")?.let { expiringDays = it }
            }
        }
    }

    /** Remembers unsaved edits across the app being closed in the background. */
    fun persistDraft() {
        handle["draft"] = json.encodeToString(GymSettingsEntity.serializer(), draft)
        handle["fees"] = arrayOf(fees.f1, fees.f3, fees.f6, fees.f12)
        handle["days"] = expiringDays
    }

    fun discardDraft() {
        val s = saved ?: return
        draft = s
        fees = FeeFields(fmt(s.fee1Month), fmt(s.fee3Months), fmt(s.fee6Months), fmt(s.fee12Months))
        expiringDays = s.expiringSoonDays.toString()
        persistDraft()
    }

    /** Simple switches (theme, reminder, auto-backup) take effect at once without touching other unsaved edits. */
    fun applyNow(transform: (GymSettingsEntity) -> GymSettingsEntity) {
        val s = saved ?: return
        val next = transform(s)
        saved = next
        draft = transform(draft)
        persistDraft()
        viewModelScope.launch {
            repo.saveSettings(next)
            if (next.reminderHour != s.reminderHour) DailyWorker.schedule(getApplication(), next.reminderHour, replace = true)
        }
    }

    fun shiftError(d: GymSettingsEntity = draft): String? {
        val ms = DateUtils.parseTime(d.morningShiftStart); val me = DateUtils.parseTime(d.morningShiftEnd)
        val es = DateUtils.parseTime(d.eveningShiftStart); val ee = DateUtils.parseTime(d.eveningShiftEnd)
        return when {
            ms == null || me == null || es == null || ee == null -> "Pick all four shift times"
            !ms.isBefore(me) -> "Morning shift must end after it starts"
            !es.isBefore(ee) -> "Evening shift must end after it starts"
            es.isBefore(me) -> "Evening shift can't start before the morning shift ends"
            else -> null
        }
    }

    fun bulkFee(planType: String, fee: Double, onDone: (Int) -> Unit) {
        viewModelScope.launch { onDone(repo.bulkChangeFee(planType, fee)) }
    }

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun buildResult(): GymSettingsEntity? {
        val f1 = fees.f1.toDoubleOrNull(); val f3 = fees.f3.toDoubleOrNull()
        val f6 = fees.f6.toDoubleOrNull(); val f12 = fees.f12.toDoubleOrNull()
        val days = expiringDays.toIntOrNull()
        if (listOf(f1, f3, f6, f12).any { it == null || it < 0 } || days == null || days !in 1..90) return null
        if (draft.gymName.isBlank() || shiftError() != null) return null
        return draft.copy(
            gymName = draft.gymName.trim(),
            fee1Month = f1!!, fee3Months = f3!!, fee6Months = f6!!, fee12Months = f12!!,
            expiringSoonDays = days
        )
    }

    val isDirty: Boolean
        get() {
            val s = saved ?: return false
            val r = buildResult() ?: return true
            return r != s
        }

    fun securityChanged(result: GymSettingsEntity): Boolean {
        val s = saved ?: return false
        return s.biometricMasterEnabled && (
            s.biometricMasterEnabled != result.biometricMasterEnabled ||
                s.biometricAppLaunch != result.biometricAppLaunch ||
                s.biometricAddMember != result.biometricAddMember ||
                s.biometricDeleteMember != result.biometricDeleteMember ||
                s.biometricRestoreBackup != result.biometricRestoreBackup ||
                s.biometricResetData != result.biometricResetData
            )
    }

    fun save(result: GymSettingsEntity, onDone: (moved: Int) -> Unit) {
        viewModelScope.launch {
            val before = saved
            val hourChanged = before?.reminderHour != result.reminderHour
            repo.saveSettings(result)
            if (hourChanged) DailyWorker.schedule(getApplication(), result.reminderHour, replace = true)
            // New shift windows: re-sort existing members into Morning/Evening.
            val windowsChanged = before?.morningShiftStart != result.morningShiftStart || before.morningShiftEnd != result.morningShiftEnd
            val moved = if (windowsChanged) repo.reassignShifts(result.morningShiftStart, result.morningShiftEnd) else 0
            saved = result; draft = result
            persistDraft()
            onDone(moved)
        }
    }

    fun resetAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.resetAll()
            reload(restore = false)
            onDone()
        }
    }
}

private enum class TimeField { MS, ME, ES, EE, REMINDER }

@Composable
fun SettingsScreen(onOpenDrawer: () -> Unit, onDataReset: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val d = vm.draft
    var timeField by remember { mutableStateOf<TimeField?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val bioAvailable = remember { BiometricHelper.isAvailable(context) }
    val bioMessage = remember { BiometricHelper.statusMessage(context) }
    val result = vm.buildResult()
    val lang = currentLang()
    val securityTitle = t("Change security settings")
    val resetTitle = t("Reset all data")
    val savedMsg = t("Settings saved")
    val movedMsg = t("Settings saved. %d members moved to their new shift.")
    val resetMsg = t("All data has been reset")
    var leaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    var hourPicker by remember { mutableStateOf(false) }
    var bulkFee by remember { mutableStateOf(false) }
    var canPost by remember { mutableStateOf(Notifications.canPost(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { canPost = Notifications.canPost(context) }
    LaunchedEffect(vm.draft, vm.fees, vm.expiringDays) { if (vm.saved != null) vm.persistDraft() }
    RegisterLeaveGuard(vm.isDirty) { proceed -> leaveAction = proceed }
    BackHandler(enabled = vm.isDirty) { leaveAction = { NavRequests.openRoute.value = "dashboard" } }
    val notifPermission = rememberPermissionRequester(
        "android.permission.POST_NOTIFICATIONS",
        title = t("Daily summary notification"),
        reason = t("Allow notifications so the app can tell you each day which memberships are expiring and how much is pending."),
        deniedMessage = t("Notifications are off, so the daily summary can't be shown.")
    )
    val enableReminder: () -> Unit = {
        if (Build.VERSION.SDK_INT < 33) {
            vm.applyNow { it.copy(dailyReminderEnabled = true) }
        } else {
            notifPermission.request(onDenied = { canPost = false }) {
                canPost = true
                vm.applyNow { it.copy(dailyReminderEnabled = true) }
            }
        }
    }
    val downloadsAccess = rememberDownloadsAccess()

    Scaffold(
        topBar = { GymTopBar(t("Settings"), t("Gym preferences & security"), onMenu = onOpenDrawer) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Column {
                    HorizontalDivider(color = LocalExtraColors.current.cardBorder)
                    Button(
                        onClick = {
                            val r = result ?: return@Button
                            val doSave = {
                                vm.save(r) { moved -> context.toast(if (moved > 0) movedMsg.format(moved) else savedMsg) }
                            }
                            if (vm.securityChanged(r)) {
                                BiometricHelper.guard(context, vm.saved ?: r, ProtectedAction.CHANGE_SECURITY, securityTitle, doSave)
                            } else doSave()
                        },
                        enabled = result != null && vm.isDirty,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp).testTag("save_settings")
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(t(if (vm.isDirty || result == null) "Save Settings" else "All changes saved"))
                    }
                }
            }
        }
    ) { padding ->
        if (vm.saved == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(16.dp).testTag("settings_scroll"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Gym info
            Section(Icons.Default.FitnessCenter, t("Gym Information")) {
                OutlinedTextField(
                    value = d.gymName,
                    onValueChange = { vm.draft = d.copy(gymName = it.take(40)) },
                    label = { Text(t("Gym Name")) },
                    singleLine = true,
                    isError = d.gymName.isBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("field_gym_name")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = d.gymPhone,
                    onValueChange = { vm.draft = d.copy(gymPhone = it.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16)) },
                    label = { Text(t("Gym phone (shown on receipts)")) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = d.gymAddress,
                    onValueChange = { vm.draft = d.copy(gymAddress = it.take(60)) },
                    label = { Text(t("Gym address (shown on receipts)")) },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.expiringDays,
                    onValueChange = { vm.expiringDays = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text(t("\"Expiring soon\" warning (days)")) },
                    singleLine = true,
                    isError = (vm.expiringDays.toIntOrNull() ?: 0) !in 1..90,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Shift windows
            Section(Icons.Default.AccessTime, t("Shift Timing Windows")) {
                Text(
                    t("Members whose workout starts inside the morning window are placed in the Morning shift; all others go to Evening."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(t("Morning"), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeBox(t("Start"), d.morningShiftStart, Modifier.weight(1f)) { timeField = TimeField.MS }
                    TimeBox(t("End"), d.morningShiftEnd, Modifier.weight(1f)) { timeField = TimeField.ME }
                }
                Spacer(Modifier.height(12.dp))
                Text(t("Evening"), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeBox(t("Start"), d.eveningShiftStart, Modifier.weight(1f)) { timeField = TimeField.ES }
                    TimeBox(t("End"), d.eveningShiftEnd, Modifier.weight(1f)) { timeField = TimeField.EE }
                }
                vm.shiftError()?.let { err ->
                    Text(t(err), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Fees
            Section(Icons.Default.Payments, t("Default Membership Fees (₹)")) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeeField(t("1 Month"), vm.fees.f1, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f1 = it) }
                    FeeField(t("3 Months"), vm.fees.f3, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f3 = it) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeeField(t("6 Months"), vm.fees.f6, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f6 = it) }
                    FeeField(t("12 Months"), vm.fees.f12, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f12 = it) }
                }
                Text(
                    t("These are used for new members. To change what existing members pay, use the button below."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
                OutlinedButton(onClick = { bulkFee = true }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).testTag("bulk_fee")) {
                    Text(t("Change fee for existing members"))
                }
            }

            // Biometrics
            Section(Icons.Default.Fingerprint, t("Biometric Security")) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (bioAvailable) StatusGreen.copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        t(bioMessage),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        color = if (bioAvailable) StatusGreen else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow(t("Enable Biometric Protection"), t("Master switch for all options below"), d.biometricMasterEnabled, enabled = bioAvailable || d.biometricMasterEnabled) { on ->
                    if (on) {
                        val activity = context.findFragmentActivity()
                        if (activity != null) {
                            BiometricHelper.authenticate(activity, I18n.tr(lang, "Enable biometric protection"), I18n.tr(lang, "Confirm your fingerprint or face"), onSuccess = {
                                vm.draft = vm.draft.copy(biometricMasterEnabled = true)
                            })
                        }
                    } else {
                        vm.draft = d.copy(biometricMasterEnabled = false)
                    }
                }
                val sub = d.biometricMasterEnabled
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow(t("App Launch Authentication"), t("Ask every time the app opens"), d.biometricAppLaunch, enabled = sub) { vm.draft = d.copy(biometricAppLaunch = it) }
                ToggleRow(t("Add New Member"), t("Verify before saving a new member"), d.biometricAddMember, enabled = sub) { vm.draft = d.copy(biometricAddMember = it) }
                ToggleRow(t("Permanently Delete Member"), t("Verify before deleting a member"), d.biometricDeleteMember, enabled = sub) { vm.draft = d.copy(biometricDeleteMember = it) }
                ToggleRow(t("Restore Backup"), t("Verify before replacing data"), d.biometricRestoreBackup, enabled = sub) { vm.draft = d.copy(biometricRestoreBackup = it) }
                ToggleRow(t("Reset App Data"), t("Verify before wiping everything"), d.biometricResetData, enabled = sub) { vm.draft = d.copy(biometricResetData = it) }
            }

            // Notifications
            Section(Icons.Default.NotificationsActive, t("Daily Reminder")) {
                val saved = vm.saved ?: d
                val blocked = saved.dailyReminderEnabled && !canPost
                ToggleRow(t("Morning summary notification"), t("Expiring memberships and pending dues, once a day"), saved.dailyReminderEnabled && !blocked, tag = "toggle_reminder") { on ->
                    if (on) enableReminder() else vm.applyNow { it.copy(dailyReminderEnabled = false) }
                }
                if (blocked) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                t("Notifications are blocked for this app, so the daily summary can't appear."),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = enableReminder) { Text(t("Allow")) }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                }
                Spacer(Modifier.height(6.dp))
                TimeBox(t("Notification time"), DateUtils.toHHmm(saved.reminderHour, 0), Modifier.fillMaxWidth()) { hourPicker = true }
                Spacer(Modifier.height(6.dp))
                ToggleRow(t("Automatic daily backup"), t("Saves a backup every day and keeps a copy in Downloads/GymManager"), saved.autoBackupEnabled) { on ->
                    vm.applyNow { it.copy(autoBackupEnabled = on) }
                    if (on) downloadsAccess({}) {}
                }
            }

            // Theme
            Section(Icons.Default.Palette, t("Application Theme")) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(ThemeMode.SYSTEM, t("System"), Icons.Default.BrightnessAuto),
                        Triple(ThemeMode.LIGHT, t("Light"), Icons.Default.LightMode),
                        Triple(ThemeMode.DARK, t("Dark"), Icons.Default.DarkMode)
                    ).forEach { (mode, label, icon) ->
                        FilterChip(
                            selected = d.themeMode == mode,
                            onClick = { vm.applyNow { it.copy(themeMode = mode) } },
                            label = { Text(label) },
                            leadingIcon = { Icon(if (d.themeMode == mode) Icons.Default.Check else icon, null, Modifier.size(18.dp)) },
                            modifier = Modifier.weight(1f).testTag("theme_$mode")
                        )
                    }
                }
            }

            // Danger zone
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.4f))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Warning, null, tint = MaterialTheme.colorScheme.error)
                        Spacer(Modifier.width(8.dp))
                        Text(t("Danger Zone"), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        t("Erase every member, payment, attendance record, photo and setting. Create a backup first if you may need this data."),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { confirmReset = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t("Reset All App Data to Defaults"))
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    timeField?.let { field ->
        val (title, value) = when (field) {
            TimeField.MS -> "Morning shift starts" to d.morningShiftStart
            TimeField.ME -> "Morning shift ends" to d.morningShiftEnd
            TimeField.ES -> "Evening shift starts" to d.eveningShiftStart
            TimeField.EE -> "Evening shift ends" to d.eveningShiftEnd
            TimeField.REMINDER -> "Notification time" to DateUtils.toHHmm(d.reminderHour, 0) // not used: hours use their own picker
        }
        AppTimePickerDialog(t(title), value, onPick = { tm ->
            vm.draft = when (field) {
                TimeField.MS -> vm.draft.copy(morningShiftStart = tm)
                TimeField.ME -> vm.draft.copy(morningShiftEnd = tm)
                TimeField.ES -> vm.draft.copy(eveningShiftStart = tm)
                TimeField.EE -> vm.draft.copy(eveningShiftEnd = tm)
                TimeField.REMINDER -> vm.draft.copy(reminderHour = DateUtils.parseTime(tm)?.hour ?: 9)
            }
        }, onDismiss = { timeField = null })
    }

    if (hourPicker) {
        HourPickerDialog(vm.saved?.reminderHour ?: 9, onPick = { h -> hourPicker = false; vm.applyNow { it.copy(reminderHour = h) } }, onDismiss = { hourPicker = false })
    }
    if (bulkFee) {
        BulkFeeDialog(onDismiss = { bulkFee = false }) { plan, fee ->
            bulkFee = false
            vm.bulkFee(plan, fee) { n -> context.toast(I18n.tr(lang, "New fee set for %d members. It applies from their next due.", n), long = true) }
        }
    }
    leaveAction?.let { proceed ->
        ConfirmDialog(
            title = t("Discard unsaved settings?"),
            message = t("You changed some settings but haven't saved them."),
            confirmText = t("Discard"),
            destructive = true,
            onConfirm = { leaveAction = null; vm.discardDraft(); proceed() },
            onDismiss = { leaveAction = null }
        )
    }
    if (confirmReset) {
        ConfirmDialog(
            title = t("Reset everything?"),
            message = t("All members, photos, payments, attendance and settings will be permanently erased. This cannot be undone."),
            confirmText = t("Erase All"),
            destructive = true,
            onConfirm = {
                confirmReset = false
                BiometricHelper.guard(context, vm.saved ?: d, ProtectedAction.RESET_DATA, resetTitle) {
                    vm.resetAll {
                        context.toast(resetMsg)
                        onDataReset()
                    }
                }
            },
            onDismiss = { confirmReset = false }
        )
    }
}

@Composable
private fun Section(icon: ImageVector, title: String, content: @Composable ColumnScope.() -> Unit) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Box(
                Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(10.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
        }
        content()
    }
}

@Composable
private fun TimeBox(label: String, value: String, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(DateUtils.formatTo12Hour(value), style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun FeeField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter { c -> c.isDigit() || c == '.' }.take(9)) },
        label = { Text(label) },
        prefix = { Text("₹") },
        singleLine = true,
        isError = value.toDoubleOrNull() == null,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        shape = RoundedCornerShape(14.dp),
        modifier = modifier
    )
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, tag: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (enabled) 1f else 0.5f)
            )
        }
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled, modifier = if (tag != null) Modifier.testTag(tag) else Modifier)
    }
}

/** The reminder runs on the hour, so the picker only offers whole hours. */
@Composable
private fun HourPickerDialog(current: Int, onPick: (Int) -> Unit, onDismiss: () -> Unit) {
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Notification time")) },
        text = {
            androidx.compose.foundation.lazy.LazyColumn(Modifier.height(320.dp)) {
                items(24) { h ->
                    val label = DateUtils.formatTo12Hour(DateUtils.toHHmm(h, 0))
                    Row(
                        Modifier.fillMaxWidth().clickable { onPick(h) }.padding(vertical = 12.dp, horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        if (h == current) Icon(Icons.Default.Check, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
}

/** Sets a new price for everyone on one plan; running and past dues keep their old price. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun BulkFeeDialog(onDismiss: () -> Unit, onConfirm: (planType: String, fee: Double) -> Unit) {
    val lang = currentLang()
    var plan by remember { mutableStateOf(com.sushant.gymmanager.data.local.PlanType.ONE_MONTH) }
    var feeText by remember { mutableStateOf("") }
    val fee = feeText.toDoubleOrNull()
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Change fee for existing members")) },
        text = {
            Column {
                Text(
                    t("Members on this plan pay the new fee from their next due. Dues already running or unpaid keep their old amount."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    com.sushant.gymmanager.data.local.PlanType.all.filter { it != com.sushant.gymmanager.data.local.PlanType.CUSTOM }.forEach { p ->
                        FilterChip(selected = plan == p, onClick = { plan = p }, label = { Text(I18n.planLabel(lang, p)) })
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = feeText,
                    onValueChange = { feeText = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                    label = { Text(t("New fee")) },
                    prefix = { Text("₹") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("bulk_fee_amount")
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(plan, fee ?: 0.0) }, enabled = fee != null && fee >= 0) { Text(t("Apply")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
}
