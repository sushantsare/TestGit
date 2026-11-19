package com.sushant.gymmanager.ui.settings

import android.app.Application
import android.widget.Toast
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
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.AppTimePickerDialog
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.theme.LocalExtraColors
import com.sushant.gymmanager.util.DateUtils
import kotlinx.coroutines.launch

data class FeeFields(val f1: String, val f3: String, val f6: String, val f12: String)

class SettingsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GymApp).repository

    var saved by mutableStateOf<GymSettingsEntity?>(null)
        private set
    var draft by mutableStateOf(GymSettingsEntity())
    var fees by mutableStateOf(FeeFields("", "", "", ""))
    var expiringDays by mutableStateOf("7")

    init { reload() }

    fun reload() {
        viewModelScope.launch {
            val s = repo.getSettings()
            saved = s; draft = s
            fees = FeeFields(fmt(s.fee1Month), fmt(s.fee3Months), fmt(s.fee6Months), fmt(s.fee12Months))
            expiringDays = s.expiringSoonDays.toString()
        }
    }

    private fun fmt(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else v.toString()

    fun buildResult(): GymSettingsEntity? {
        val f1 = fees.f1.toDoubleOrNull(); val f3 = fees.f3.toDoubleOrNull()
        val f6 = fees.f6.toDoubleOrNull(); val f12 = fees.f12.toDoubleOrNull()
        val days = expiringDays.toIntOrNull()
        if (listOf(f1, f3, f6, f12).any { it == null || it < 0 } || days == null || days !in 1..90) return null
        if (draft.gymName.isBlank()) return null
        return draft.copy(
            gymName = draft.gymName.trim(),
            fee1Month = f1!!, fee3Months = f3!!, fee6Months = f6!!, fee12Months = f12!!,
            expiringSoonDays = days
        )
    }

    val isDirty: Boolean get() = saved != null && buildResult() != saved

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

    fun save(result: GymSettingsEntity, onDone: () -> Unit) {
        viewModelScope.launch {
            repo.saveSettings(result)
            saved = result; draft = result
            onDone()
        }
    }

    fun resetAll(onDone: () -> Unit) {
        viewModelScope.launch {
            repo.resetAll()
            reload()
            onDone()
        }
    }
}

private enum class TimeField { MS, ME, ES, EE }

@Composable
fun SettingsScreen(onOpenDrawer: () -> Unit, onDataReset: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val context = LocalContext.current
    val d = vm.draft
    var timeField by remember { mutableStateOf<TimeField?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val bioAvailable = remember { BiometricHelper.isAvailable(context) }
    val bioMessage = remember { BiometricHelper.statusMessage(context) }
    val result = vm.buildResult()

    Scaffold(
        topBar = { GymTopBar("Settings", "Gym preferences & security", onMenu = onOpenDrawer) },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                Column {
                    HorizontalDivider(color = LocalExtraColors.current.cardBorder)
                    Button(
                        onClick = {
                            val r = result ?: return@Button
                            val doSave = {
                                vm.save(r) { Toast.makeText(context, "Settings saved", Toast.LENGTH_SHORT).show() }
                            }
                            if (vm.securityChanged(r)) {
                                BiometricHelper.guard(context, vm.saved ?: r, ProtectedAction.CHANGE_SECURITY, "Change security settings", doSave)
                            } else doSave()
                        },
                        enabled = result != null && vm.isDirty,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)
                            .height(52.dp).testTag("save_settings")
                    ) {
                        Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (vm.isDirty || result == null) "Save Settings" else "All changes saved")
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
            Section(Icons.Default.FitnessCenter, "Gym Information") {
                OutlinedTextField(
                    value = d.gymName,
                    onValueChange = { vm.draft = d.copy(gymName = it.take(40)) },
                    label = { Text("Gym Name") },
                    singleLine = true,
                    isError = d.gymName.isBlank(),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth().testTag("field_gym_name")
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = vm.expiringDays,
                    onValueChange = { vm.expiringDays = it.filter { c -> c.isDigit() }.take(2) },
                    label = { Text("\"Expiring soon\" warning (days)") },
                    singleLine = true,
                    isError = (vm.expiringDays.toIntOrNull() ?: 0) !in 1..90,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            // Shift windows
            Section(Icons.Default.AccessTime, "Shift Timing Windows") {
                Text(
                    "Members whose workout starts inside the morning window are placed in the Morning shift; all others go to Evening.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text("Morning", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeBox("Start", d.morningShiftStart, Modifier.weight(1f)) { timeField = TimeField.MS }
                    TimeBox("End", d.morningShiftEnd, Modifier.weight(1f)) { timeField = TimeField.ME }
                }
                Spacer(Modifier.height(12.dp))
                Text("Evening", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TimeBox("Start", d.eveningShiftStart, Modifier.weight(1f)) { timeField = TimeField.ES }
                    TimeBox("End", d.eveningShiftEnd, Modifier.weight(1f)) { timeField = TimeField.EE }
                }
            }

            // Fees
            Section(Icons.Default.Payments, "Default Membership Fees (₹)") {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeeField("1 Month", vm.fees.f1, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f1 = it) }
                    FeeField("3 Months", vm.fees.f3, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f3 = it) }
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    FeeField("6 Months", vm.fees.f6, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f6 = it) }
                    FeeField("12 Months", vm.fees.f12, Modifier.weight(1f)) { vm.fees = vm.fees.copy(f12 = it) }
                }
            }

            // Biometrics
            Section(Icons.Default.Fingerprint, "Biometric Security") {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (bioAvailable) Color(0xFF16A34A).copy(alpha = 0.1f) else MaterialTheme.colorScheme.errorContainer
                ) {
                    Text(
                        bioMessage,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        color = if (bioAvailable) Color(0xFF15803D) else MaterialTheme.colorScheme.onErrorContainer
                    )
                }
                Spacer(Modifier.height(6.dp))
                ToggleRow("Enable Biometric Protection", "Master switch for all options below", d.biometricMasterEnabled, enabled = bioAvailable || d.biometricMasterEnabled) { on ->
                    if (on) {
                        val activity = context.findFragmentActivity()
                        if (activity != null) {
                            BiometricHelper.authenticate(activity, "Enable biometric protection", "Confirm your fingerprint or face", onSuccess = {
                                vm.draft = vm.draft.copy(biometricMasterEnabled = true)
                            })
                        }
                    } else {
                        vm.draft = d.copy(biometricMasterEnabled = false)
                    }
                }
                val sub = d.biometricMasterEnabled
                HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                ToggleRow("App Launch Authentication", "Ask every time the app opens", d.biometricAppLaunch, enabled = sub) { vm.draft = d.copy(biometricAppLaunch = it) }
                ToggleRow("Add New Member", "Verify before saving a new member", d.biometricAddMember, enabled = sub) { vm.draft = d.copy(biometricAddMember = it) }
                ToggleRow("Permanently Delete Member", "Verify before deleting a member", d.biometricDeleteMember, enabled = sub) { vm.draft = d.copy(biometricDeleteMember = it) }
                ToggleRow("Restore Backup", "Verify before replacing data", d.biometricRestoreBackup, enabled = sub) { vm.draft = d.copy(biometricRestoreBackup = it) }
                ToggleRow("Reset App Data", "Verify before wiping everything", d.biometricResetData, enabled = sub) { vm.draft = d.copy(biometricResetData = it) }
            }

            // Theme
            Section(Icons.Default.Palette, "Application Theme") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(ThemeMode.SYSTEM, "System", Icons.Default.BrightnessAuto),
                        Triple(ThemeMode.LIGHT, "Light", Icons.Default.LightMode),
                        Triple(ThemeMode.DARK, "Dark", Icons.Default.DarkMode)
                    ).forEach { (mode, label, icon) ->
                        FilterChip(
                            selected = d.themeMode == mode,
                            onClick = { vm.draft = d.copy(themeMode = mode) },
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
                        Text("Danger Zone", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Erase every member, payment, attendance record, photo and setting. Create a backup first if you may need this data.",
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
                        Text("Reset All App Data to Defaults")
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
        }
        AppTimePickerDialog(title, value, onPick = { t ->
            vm.draft = when (field) {
                TimeField.MS -> vm.draft.copy(morningShiftStart = t)
                TimeField.ME -> vm.draft.copy(morningShiftEnd = t)
                TimeField.ES -> vm.draft.copy(eveningShiftStart = t)
                TimeField.EE -> vm.draft.copy(eveningShiftEnd = t)
            }
        }, onDismiss = { timeField = null })
    }

    if (confirmReset) {
        ConfirmDialog(
            title = "Reset everything?",
            message = "All members, photos, payments, attendance and settings will be permanently erased. This cannot be undone.",
            confirmText = "Erase All",
            destructive = true,
            onConfirm = {
                confirmReset = false
                BiometricHelper.guard(context, vm.saved ?: d, ProtectedAction.RESET_DATA, "Reset all data") {
                    vm.resetAll {
                        Toast.makeText(context, "All data has been reset", Toast.LENGTH_SHORT).show()
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
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
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
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}
