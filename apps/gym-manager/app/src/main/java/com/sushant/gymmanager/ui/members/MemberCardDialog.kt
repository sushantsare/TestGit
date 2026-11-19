package com.sushant.gymmanager.ui.members

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.auth.ProtectedAction
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.LabeledValue
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.Pill
import com.sushant.gymmanager.ui.components.RollBadge
import com.sushant.gymmanager.ui.components.ShiftPill
import com.sushant.gymmanager.ui.components.planStatus
import com.sushant.gymmanager.ui.components.rememberGymApp
import com.sushant.gymmanager.ui.theme.LocalExtraColors
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.ui.theme.StatusRed
import com.sushant.gymmanager.ui.theme.WhatsAppGreen
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.Money
import com.sushant.gymmanager.util.WhatsAppHelper
import kotlinx.coroutines.launch

@Composable
fun MemberCardDialog(memberId: Long, onDismiss: () -> Unit, onEdit: (Long) -> Unit) {
    val app = rememberGymApp()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val details by remember(memberId) { app.repository.memberDetails(memberId) }.collectAsStateWithLifecycle(initialValue = null)
    val settings by app.repository.settings.collectAsStateWithLifecycle(initialValue = GymSettingsEntity())
    var confirmDeactivate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(details) {
        if (details != null) loaded = true else if (loaded) onDismiss() // member was deleted
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 720.dp).testTag("member_card"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp
        ) {
            val d = details
            if (d == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }
            val m = d.member
            val plan = d.membership
            val status = planStatus(plan, settings.expiringSoonDays)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        if (m.isActive) "ACTIVE MEMBER" else "DEACTIVATED",
                        if (m.isActive) StatusGreen else StatusRed
                    )
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Close") }
                }
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    MemberAvatar(m.photoUri, m.fullName, size = 96.dp)
                    Spacer(Modifier.height(12.dp))
                    Text(m.fullName, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RollBadge(m.rollNumber)
                        ShiftPill(m.shift)
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            DateUtils.formatTimeSlot(m.fromTime, m.toTime),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                // WhatsApp
                InfoBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabeledValue("WHATSAPP", "+" + WhatsAppHelper.normalizePhone(m.whatsappNumber), Modifier.weight(1f))
                        Button(
                            onClick = { WhatsAppHelper.open(context, m.whatsappNumber) },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Chat")
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                InfoBlock {
                    Row {
                        LabeledValue("DATE OF BIRTH", DateUtils.formatShort(m.dateOfBirth).ifEmpty { "—" }, Modifier.weight(1f))
                        LabeledValue("JOINED ON", DateUtils.formatShort(m.dateOfJoining).ifEmpty { "—" }, Modifier.weight(1f))
                    }
                    if (!m.fatherName.isNullOrBlank() || !m.motherName.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row {
                            LabeledValue("FATHER'S NAME", m.fatherName.orEmpty().ifBlank { "—" }, Modifier.weight(1f))
                            LabeledValue("MOTHER'S NAME", m.motherName.orEmpty().ifBlank { "—" }, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Membership
                InfoBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Membership", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Pill(status.label.uppercase(), status.color)
                    }
                    if (plan != null) {
                        Spacer(Modifier.height(12.dp))
                        Row {
                            LabeledValue("PLAN", DateUtils.planLabel(plan.planType, plan.durationMonths, plan.durationDays), Modifier.weight(1f))
                            LabeledValue("FEE", Money.format(plan.fee), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            LabeledValue("VALID TILL", DateUtils.formatShort(plan.expiryDate), Modifier.weight(1f))
                            LabeledValue("AUTO-RENEW", if (plan.autoRenew) "On" else "Off", Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(DateUtils.expiryText(plan.expiryDate), style = MaterialTheme.typography.labelMedium, color = status.color)
                        if (!plan.autoRenew && m.isActive) {
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = {
                                scope.launch {
                                    app.repository.renewMembership(m.id)
                                    Toast.makeText(context, "Membership renewed, a new due was added", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Default.Autorenew, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Renew for another period")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                // Attendance
                InfoBlock {
                    val pct = if (d.recordCount > 0) d.presentCount * 100 / d.recordCount else 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Attendance Record", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(
                            "${d.presentCount} present ($pct%)",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (d.recordCount > 0) d.presentCount.toFloat() / d.recordCount else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Out of ${d.recordCount} recorded sessions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                // Actions
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDismiss(); onEdit(m.id) }, modifier = Modifier.weight(1f).testTag("card_edit")) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Edit")
                    }
                    OutlinedButton(
                        onClick = {
                            if (m.isActive) {
                                confirmDeactivate = true
                            } else {
                                scope.launch {
                                    app.repository.setActive(m.id, true)
                                    Toast.makeText(context, "${m.fullName} reactivated", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (m.isActive) Icons.Default.PersonOff else Icons.Default.Restore, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (m.isActive) "Deactivate" else "Reactivate", maxLines = 1)
                    }
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Delete Permanently")
                }
            }

            if (confirmDeactivate) {
                ConfirmDialog(
                    title = "Deactivate member?",
                    message = "${m.fullName} will be hidden from attendance and payments. All history is kept and you can reactivate any time.",
                    confirmText = "Deactivate",
                    onConfirm = {
                        confirmDeactivate = false
                        scope.launch {
                            app.repository.setActive(m.id, false)
                            Toast.makeText(context, "${m.fullName} deactivated", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onDismiss = { confirmDeactivate = false }
                )
            }
            if (confirmDelete) {
                ConfirmDialog(
                    title = "Delete permanently?",
                    message = "This removes ${m.fullName}, their photo, attendance, memberships and payment history. This cannot be undone.",
                    confirmText = "Delete",
                    destructive = true,
                    onConfirm = {
                        confirmDelete = false
                        BiometricHelper.guard(context, settings, ProtectedAction.DELETE_MEMBER, "Delete ${m.fullName}") {
                            scope.launch {
                                app.repository.deleteMember(m.id)
                                Toast.makeText(context, "Member deleted", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            }
                        }
                    },
                    onDismiss = { confirmDelete = false }
                )
            }
        }
    }
}

@Composable
private fun InfoBlock(content: @Composable () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (LocalExtraColors.current.isDark) 1f else 0.7f)
    ) {
        Column(Modifier.padding(14.dp)) { content() }
    }
}
