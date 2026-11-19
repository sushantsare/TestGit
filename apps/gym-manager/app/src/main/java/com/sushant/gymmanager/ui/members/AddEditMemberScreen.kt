package com.sushant.gymmanager.ui.members

import com.sushant.gymmanager.util.toast
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import com.sushant.gymmanager.ui.navigation.RegisterLeaveGuard
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.auth.ProtectedAction
import com.sushant.gymmanager.data.local.PlanType
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
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
    var leaveAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val photoLabel = t("Choose profile photo")
    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.importPhoto(uri)
    }

    val addTitle = t("Add new member")
    val savedMsg = t(if (isNew) "Member added" else "Changes saved")
    val tryExit: () -> Unit = { if (vm.isDirty) { confirmDiscard = true } else { onDone() } }
    BackHandler(enabled = vm.isDirty) { confirmDiscard = true }
    RegisterLeaveGuard(vm.isDirty) { proceed -> leaveAction = proceed; confirmDiscard = true }
    val fixMsg = t("Please fix the highlighted fields")

    fun showError(key: String) = vm.showError(key)

    Scaffold(
        topBar = {
            GymTopBar(
                title = t(if (isNew) "Add Member" else "Edit Member"),
                subtitle = if (isNew) t("Register a new gym member") else f.fullName,
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
                        OutlinedButton(onClick = tryExit, modifier = Modifier.weight(1f).height(50.dp)) { Text(t("Cancel")) }
                        Button(
                            onClick = {
                                if (vm.errors.isNotEmpty()) {
                                    vm.markSaveAttempted()
                                    context.toast(fixMsg)
                                    return@Button
                                }
                                val doSave = {
                                    vm.save {
                                        context.toast(savedMsg)
                                        onDone()
                                    }
                                }
                                if (isNew) BiometricHelper.guard(context, vm.settings, ProtectedAction.ADD_MEMBER, addTitle, doSave)
                                else doSave()
                            },
                            enabled = !vm.loading && !vm.saving,
                            modifier = Modifier.weight(1.4f).height(50.dp).testTag("save_member")
                        ) {
                            if (vm.saving) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            else {
                                Icon(Icons.Default.Check, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(t(if (isNew) "Save Member" else "Save Changes"), maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                                }
                                .semantics { contentDescription = photoLabel; role = Role.Button },
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
                        Text(t("Profile Photo"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            t("Tap the circle to choose a photo from your gallery."),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (f.photoPath != null) {
                            TextButton(onClick = vm::removePhoto, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
                                Text(t("Remove photo"))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                FormField(
                    value = f.rollNumber, label = t("Roll Number *"), icon = Icons.Default.Badge, tag = "field_roll",
                    error = if (showError("roll") || vm.rollTaken) errors["roll"] else null,
                    onBlur = { vm.touch("roll") },
                    onChange = { v -> vm.update { it.copy(rollNumber = v.take(12)) } }
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = f.fullName, label = t("Full Name *"), icon = Icons.Default.Person, tag = "field_name",
                    error = if (showError("name")) errors["name"] else null,
                    capitalization = KeyboardCapitalization.Words,
                    onBlur = { vm.touch("name") },
                    onChange = { v -> vm.update { it.copy(fullName = v) } }
                )
            }

            // ---------- Contact & personal ----------
            AppCard(Modifier.fillMaxWidth()) {
                CardHeader(t("Contact & Personal"))
                FormField(
                    value = f.whatsapp, label = t("WhatsApp Number *"), icon = Icons.Default.Phone, tag = "field_whatsapp",
                    keyboardType = KeyboardType.Phone,
                    error = if (showError("whatsapp")) errors["whatsapp"] else null,
                    onBlur = { vm.touch("whatsapp") },
                    onChange = { v -> vm.update { it.copy(whatsapp = v.filter { c -> c.isDigit() || c == '+' || c == ' ' }.take(16)) } }
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        t("Date of Birth"), DateUtils.formatShort(f.dateOfBirth).ifEmpty { t("Optional") }, Icons.Default.Cake,
                        Modifier.weight(1f), placeholder = f.dateOfBirth.isBlank()
                    ) { picker = Picker.DOB }
                    PickerField(
                        t("Date of Joining"), DateUtils.formatShort(f.dateOfJoining), Icons.Default.Event, Modifier.weight(1f)
                    ) { picker = Picker.DOJ }
                }
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = f.fatherName, label = t("Father's Name (optional)"), icon = Icons.Default.Person,
                    capitalization = KeyboardCapitalization.Words,
                    onChange = { v -> vm.update { it.copy(fatherName = v) } }
                )
                Spacer(Modifier.height(10.dp))
                FormField(
                    value = f.motherName, label = t("Mother's Name (optional)"), icon = Icons.Default.Person,
                    capitalization = KeyboardCapitalization.Words,
                    onChange = { v -> vm.update { it.copy(motherName = v) } }
                )
            }

            // ---------- Workout slot ----------
            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CardHeader(t("Workout Time Slot"), Modifier.weight(1f))
                    if (f.fromTime.isNotBlank()) ShiftPill(vm.shift)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    PickerField(
                        t("From"), if (f.fromTime.isBlank()) t("Select") else DateUtils.formatTo12Hour(f.fromTime), Icons.Default.AccessTime,
                        Modifier.weight(1f).testTag("field_from"), placeholder = f.fromTime.isBlank()
                    ) { picker = Picker.FROM }
                    PickerField(
                        t("To"), if (f.toTime.isBlank()) t("Select") else DateUtils.formatTo12Hour(f.toTime), Icons.Default.AccessTime,
                        Modifier.weight(1f).testTag("field_to"), placeholder = f.toTime.isBlank()
                    ) { picker = Picker.TO }
                }
                val slotError = if (showError("slot")) errors["slot"] else null
                Text(
                    slotError?.let { t(it) } ?: t("Shift is detected automatically from the start time using your shift windows in Settings."),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (slotError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            // ---------- Membership ----------
            AppCard(Modifier.fillMaxWidth()) {
                CardHeader(t("Membership Plan"))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    PlanType.all.forEach { p ->
                        FilterChip(
                            selected = f.planType == p,
                            onClick = { vm.selectPlan(p) },
                            label = { Text(I18n.planLabel(currentLang(), p)) },
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
                            value = f.customMonths, label = t("Months"), keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            onChange = { v -> vm.update { it.copy(customMonths = v.filter { c -> c.isDigit() }.take(3)) } }
                        )
                        FormField(
                            value = f.customDays, label = t("Days"), keyboardType = KeyboardType.Number,
                            modifier = Modifier.weight(1f),
                            onChange = { v -> vm.update { it.copy(customDays = v.filter { c -> c.isDigit() }.take(3)) } }
                        )
                    }
                    errors["custom"]?.takeIf { showError("custom") || f.customMonths.isNotEmpty() }?.let {
                        Text(t(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                Spacer(Modifier.height(12.dp))
                FormField(
                    value = f.fee, label = t("Membership Fee (₹) *"), icon = Icons.Default.CurrencyRupee, tag = "field_fee",
                    keyboardType = KeyboardType.Decimal,
                    error = if (showError("fee") || f.fee.isNotEmpty()) errors["fee"] else null,
                    onBlur = { vm.touch("fee") },
                    onChange = { v -> vm.update { it.copy(fee = v.filter { c -> c.isDigit() || c == '.' }.take(9)) } }
                )
                Spacer(Modifier.height(12.dp))
                Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarMonth, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(t("Valid till"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            Text(DateUtils.formatFull(vm.expiryPreview), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(t("Auto-Renew"), style = MaterialTheme.typography.titleSmall)
                        Text(
                            t("Create the next unpaid billing cycle automatically when this one ends"),
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
        Picker.DOB -> AppDatePickerDialog(f.dateOfBirth.ifBlank { "2000-01-01" }, { iso -> vm.update { it.copy(dateOfBirth = iso) } }, { picker = null }, pastOnly = true)
        Picker.DOJ -> AppDatePickerDialog(f.dateOfJoining, { iso -> vm.update { it.copy(dateOfJoining = iso) } }, { picker = null })
        Picker.FROM -> AppTimePickerDialog(t("Workout starts at"), f.fromTime.ifBlank { "06:00" }, { tm ->
            vm.update { it.copy(fromTime = tm, toTime = it.toTime.ifBlank { DateUtils.parseTime(tm)?.plusHours(1)?.let { x -> DateUtils.toHHmm(x.hour, x.minute) } ?: "" }) }
            vm.touch("slot")
        }) { picker = null }
        Picker.TO -> AppTimePickerDialog(t("Workout ends at"), f.toTime.ifBlank { "07:00" }, { tm ->
            vm.update { it.copy(toTime = tm) }; vm.touch("slot")
        }) { picker = null }
        null -> {}
    }

    if (confirmDiscard) {
        ConfirmDialog(
            title = t("Discard changes?"),
            message = t("You have unsaved changes. If you leave now, they will be lost."),
            confirmText = t("Discard"),
            destructive = true,
            onConfirm = {
                confirmDiscard = false
                val leave = leaveAction
                leaveAction = null
                if (leave != null) leave() else onDone()
            },
            onDismiss = { confirmDiscard = false; leaveAction = null }
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
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    onBlur: (() -> Unit)? = null
) {
    var hadFocus by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        leadingIcon = icon?.let { { Icon(it, null) } },
        isError = error != null,
        supportingText = error?.let { { Text(t(it)) } },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
        modifier = modifier.then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .onFocusChanged { state ->
                // A field's error shows once the user has been in it and moved on.
                if (hadFocus && !state.isFocused) onBlur?.invoke()
                hadFocus = state.isFocused
            }
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
