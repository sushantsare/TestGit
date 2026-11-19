package com.sushant.gymmanager.ui.members

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Cake
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.auth.ProtectedAction
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.AppDatePickerDialog
import com.sushant.gymmanager.ui.components.AppTimePickerDialog
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.ShiftPill
import com.sushant.gymmanager.ui.theme.LocalExtraColors
import com.sushant.gymmanager.util.DateUtils

private enum class Picker { DOB, DOJ, FROM, TO }

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddEditMemberScreen(memberId: Long, onDone: () -> Unit, vm: AddEditMemberViewModel = viewModel()) {
    LaunchedEffect(memberId) { vm.init(memberId) }
    val context = LocalContext.current
    val isNew = memberId == 0L
    val f = vm.form
    val errors = vm.errors
    var picker by remember { mutableStateOf<Picker?>(null) }
    var confirmDiscard by remember { mutableStateOf(false) }
    var touched by remember { mutableStateOf(setOf<String>()) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }

    val tryExit: () -> Unit = { if (vm.isDirty) { confirmDiscard = true } else { onDone() } }
    BackHandler(enabled = vm.isDirty) { confirmDiscard = true }

    fun showError(key: String) = (key in touched || f != vm.original) && errors.containsKey(key) &&
        (key != "roll" || f.rollNumber.isNotEmpty() || key in touched)

    Scaffold(
        topBar = {
            GymTopBar(
                title = if (isNew) "Add Member" else "Edit Member",
                subtitle = if (isNew) "Register a new gym member" else f.fullName,
                onBack = tryExit
            )
        },
        bottomBar = {
            Surface(tonalElevation = 0.dp, color = MaterialTheme.colorScheme.surface) {
                Column {
                    HorizontalDivider(color = LocalExtraColors.current.cardBorder)
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = tryExit, modifier = Modifier.weight(1f).height(50.dp)) { Text("Cancel") }
                        Button(
                            onClick = {
                                val doSave = {
                                    vm.save {
                                        Toast.makeText(context, if (isNew) "Member added" else "Changes saved", Toast.LENGTH_SHORT).show()
                                        onDone()
                                    }
                                }
                                if (isNew) BiometricHelper.guard(context, vm.settings, ProtectedAction.ADD_MEMBER, "Add new member", doSave)
                                else doSave()
                            },
                            enabled = vm.canSave,
                            modifier = Modifier.weight(1.4f).height(50.dp).testTag("save_member")
                        ) {
                            if (vm.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(if (isNew) "Save Member" else "Save Changes")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (vm.loading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ---------- Profile ----------
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Box(
                            Modifier.size(88.dp).clip(CircleShape)
                                .border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), CircleShape)
                                .clickable {
                                    photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            if (vm.photoImporting) CircularProgressIndicator(Modifier.size(28.dp))
                            else MemberAvatar(f.photoPath, f.fullName, size = 84.dp)
                        }
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(30.dp).clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AddAPhoto, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onPrimary)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Profile Photo", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Tap the circle to pick a photo. No storage permission needed.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (f.photoPath != null) {
                            TextButton(onClick = vm::removePhoto, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text("Remove photo")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                FormField(
                    value = f.rollNumber, label = "Roll Number *", icon = Icons.Default.Badge, tag = "field_roll",
                    error = if (showError("roll") || vm.rollTaken) errors["roll"] else null,
                    onChange = { v -> vm.update { it.copy(rollNumber = v.take(12)) }; touched = touched + "roll" }
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = f.fullName, label = "Full Name *", icon = Icons.Default.Person, tag = "field_name",
                    error = if (showError("name")) errors["name"] else null,
                    capitalization = KeyboardCapitalization.Words,
                    onChange = { v -> vm.update { it.copy(fullName = v) }; touched = touched + "name" }
                )
            }

            // ---------- Contact & personal ----------
            AppCard(Modifier.fillMaxWidth()) {
                CardHeader("Contact & Personal")
                FormField(
                    value = f.whatsapp, label = "WhatsApp Number *", icon = Icons.Default.Phone, tag = "field_whatsapp",
                    keyboardType = KeyboardType.Phone,
                    error = if (showError("whatsapp")) errors["whatsapp"] else null,
                    onChange = { v -> vm.update { it.copy(whatsapp = v.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16)) }; touched = touched + "whatsapp" }
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        "Date of Birth", DateUtils.formatShort(f.dateOfBirth).ifEmpty { "Optional" }, Icons.Default.Cake,
                        Modifier.weight(1f), placeholder = f.dateOfBirth.isBlank()
                    ) { picker = Picker.DOB }
                    PickerField(
                        "Date of Joining", DateUtils.formatShort(f.dateOfJoining), Icons.Default.Event, Modifier.weight(1f)
                    ) { picker = Picker.DOJ }
                }
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = f.fatherName, label = "Father's Name (optional)", icon = Icons.Default.Person,
                    capitalization = KeyboardCapitalization.Words,
                    onChange = { v -> vm.update { it.copy(fatherName = v) } }
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = f.motherName, label = "Mother's Name (optional)", icon = Icons.Default.Person,
                    capitalization = KeyboardCapitalization.Words,
                    onChange = { v -> vm.update { it.copy(motherName = v) } }
                )
            }

            // ---------- Workout slot ----------
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardHeader("Workout Time Slot", Modifier.weight(1f))
                    if (f.fromTime.isNotBlank()) ShiftPill(vm.shift)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        "From", if (f.fromTime.isBlank()) "Select" else DateUtils.formatTo12Hour(f.fromTime), Icons.Default.AccessTime,
                        Modifier.weight(1f).testTag("field_from"), placeholder = f.fromTime.isBlank()
                    ) { picker = Picker.FROM }
                    PickerField(
                        "To", if (f.toTime.isBlank()) "Select" else DateUtils.formatTo12Hour(f.toTime), Icons.Default.AccessTime,
                        Modifier.weight(1f).testTag("field_to"), placeholder = f.toTime.isBlank()
                    ) { picker = Picker.TO }
                }
                val slotError = if ("slot" in touched) errors["slot"] else null
                Text(
                    slotError ?: "Shift is detected automatically from the start time using your shift windows in Settings.",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slotError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ---------- Membership ----------
            AppCard(Modifier.fillMaxWidth()) {
                CardHeader("Membership Plan")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlanType.all.forEach { p ->
                        FilterChip(
                            selected = f.planType == p,
                            onClick = { vm.selectPlan(p) },
                            label = { Text(DateUtils.planLabel(p).takeIf { p != PlanType.CUSTOM } ?: "Custom") },
                            leadingIcon = if (f.planType == p) {
                                { Icon(Icons.Default.Check, null, Modifier.size(16.dp)) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                selectedLeadingIconColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier.testTag("plan_$p")
                        )
                    }
                }
                if (f.planType == PlanType.CUSTOM) {
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FormField(
                            value = f.customMonths, label = "Months", keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            onChange = { v -> vm.update { it.copy(customMonths = v.filter { c -> c.isDigit() }.take(3)) } }
                        )
                        FormField(
                            value = f.customDays, label = "Days", keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            onChange = { v -> vm.update { it.copy(customDays = v.filter { c -> c.isDigit() }.take(3)) } }
                        )
                    }
                    errors["custom"]?.let {
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                FormField(
                    value = f.fee, label = "Membership Fee (₹) *", icon = Icons.Default.CurrencyRupee, tag = "field_fee",
                    keyboardType = KeyboardType.Decimal,
                    error = errors["fee"],
                    onChange = { v -> vm.update { it.copy(fee = v.filter { c -> c.isDigit() || c == '.' }.take(9)) } }
                )
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Valid till", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(DateUtils.formatFull(vm.expiryPreview), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Auto-Renew", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Create the next unpaid billing cycle automatically when this one ends",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = f.autoRenew, onCheckedChange = { v -> vm.update { it.copy(autoRenew = v) } })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    when (picker) {
        Picker.DOB -> AppDatePickerDialog(f.dateOfBirth.ifBlank { "2000-01-01" }, { iso -> vm.update { it.copy(dateOfBirth = iso) } }) { picker = null }
        Picker.DOJ -> AppDatePickerDialog(f.dateOfJoining, { iso -> vm.update { it.copy(dateOfJoining = iso) } }) { picker = null }
        Picker.FROM -> AppTimePickerDialog("Workout starts at", f.fromTime.ifBlank { "06:00" }, { t ->
            vm.update { it.copy(fromTime = t, toTime = it.toTime.ifBlank { DateUtils.parseTime(t)?.plusHours(1)?.let { x -> DateUtils.toHHmm(x.hour, x.minute) } ?: "" }) }
            touched = touched + "slot"
        }) { picker = null }
        Picker.TO -> AppTimePickerDialog("Workout ends at", f.toTime.ifBlank { "07:00" }, { t ->
            vm.update { it.copy(toTime = t) }; touched = touched + "slot"
        }) { picker = null }
        null -> {}
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = "Discard changes?",
            message = "You have unsaved changes. If you leave now, they will be lost.",
            confirmText = "Discard",
            destructive = true,
            onConfirm = { confirmDiscard = false; onDone() },
            onDismiss = { confirmDiscard = false }
        )
    }
}

@Composable
private fun CardHeader(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier.padding(bottom = 12.dp))
}

@Composable
private fun FormField(
    value: String,
    label: String,
    onChange: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
    icon: ImageVector? = null,
    tag: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null) } },
        isError = error != null,
        supportingText = error?.let { { Text(it) } },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)
    )
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    placeholder: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = modifier.height(60.dp),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Column {
                Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (placeholder) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1
                )
            }
        }
    }
}
