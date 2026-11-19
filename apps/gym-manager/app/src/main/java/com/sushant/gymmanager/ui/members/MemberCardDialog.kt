package com.sushant.gymmanager.ui.members

import com.sushant.gymmanager.util.toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AcUnit
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gymmanager.auth.BiometricHelper
import com.sushant.gymmanager.auth.ProtectedAction
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.PaymentEntity
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.FrozenBlue
import com.sushant.gymmanager.ui.components.LabeledValue
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.Pill
import com.sushant.gymmanager.ui.components.RollBadge
import com.sushant.gymmanager.ui.components.ShiftPill
import com.sushant.gymmanager.ui.components.planStatus
import com.sushant.gymmanager.ui.components.rememberGymApp
import com.sushant.gymmanager.ui.navigation.LocalOpenCalendar
import com.sushant.gymmanager.ui.payments.ShareActions
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
    val lang = currentLang()
    val openCalendar = LocalOpenCalendar.current
    val details by remember(memberId) { app.repository.memberDetails(memberId) }.collectAsStateWithLifecycle(initialValue = null)
    val history by remember(memberId) { app.repository.paymentsFor(memberId) }.collectAsStateWithLifecycle(initialValue = emptyList())
    val settings by app.repository.settings.collectAsStateWithLifecycle(initialValue = GymSettingsEntity())
    var confirmDeactivate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var confirmFreeze by remember { mutableStateOf(false) }
    var undoTarget by remember { mutableStateOf<PaymentEntity?>(null) }
    var showAllHistory by remember { mutableStateOf(false) }
    var loaded by remember { mutableStateOf(false) }
    LaunchedEffect(details) {
        if (details != null) loaded = true else if (loaded) onDismiss()
    }
    fun toast(msg: String) = context.toast(msg)

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.92f).heightIn(max = 760.dp).testTag("member_card"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            val d = details
            if (d == null) {
                Box(Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                return@Surface
            }
            val m = d.member
            val plan = d.membership
            val frozen = m.frozenFrom != null
            val status = planStatus(plan, settings.expiringSoonDays, frozen)
            Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(t(if (m.isActive) "ACTIVE MEMBER" else "DEACTIVATED"), if (m.isActive) StatusGreen else StatusRed)
                    if (frozen) {
                        Spacer(Modifier.width(6.dp))
                        Pill(t("FROZEN"), FrozenBlue)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, t("Close")) }
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
                        Text(DateUtils.formatTimeSlot(m.fromTime, m.toTime), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(14.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = { scope.launch { ShareActions.shareIdCard(context, m.id) } },
                        modifier = Modifier.weight(1f).testTag("card_idcard"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.Badge, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("ID Card"), maxLines = 1)
                    }
                    FilledTonalButton(
                        onClick = { onDismiss(); openCalendar(m.id) },
                        modifier = Modifier.weight(1f).testTag("card_calendar"),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Calendar"), maxLines = 1)
                    }
                }
                Spacer(Modifier.height(10.dp))

                InfoBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        LabeledValue(t("WHATSAPP"), "+" + WhatsAppHelper.normalizePhone(m.whatsappNumber), Modifier.weight(1f))
                        Button(
                            onClick = { WhatsAppHelper.open(context, m.whatsappNumber) },
                            colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Chat, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Chat"))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                InfoBlock {
                    Row {
                        LabeledValue(t("DATE OF BIRTH"), DateUtils.formatShort(m.dateOfBirth).ifEmpty { "—" }, Modifier.weight(1f))
                        LabeledValue(t("JOINED ON"), DateUtils.formatShort(m.dateOfJoining).ifEmpty { "—" }, Modifier.weight(1f))
                    }
                    if (!m.fatherName.isNullOrBlank() || !m.motherName.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        Row {
                            LabeledValue(t("FATHER'S NAME"), m.fatherName.orEmpty().ifBlank { "—" }, Modifier.weight(1f))
                            LabeledValue(t("MOTHER'S NAME"), m.motherName.orEmpty().ifBlank { "—" }, Modifier.weight(1f))
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                InfoBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Membership"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Pill(t(status.label).uppercase(), status.color)
                    }
                    if (plan != null) {
                        Spacer(Modifier.height(12.dp))
                        Row {
                            LabeledValue(t("PLAN"), I18n.planLabel(lang, plan.planType, plan.durationMonths, plan.durationDays), Modifier.weight(1f))
                            LabeledValue(t("FEE"), Money.format(plan.fee), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(12.dp))
                        Row {
                            LabeledValue(t("VALID TILL"), DateUtils.formatShort(plan.expiryDate), Modifier.weight(1f))
                            LabeledValue(t("AUTO-RENEW"), t(if (plan.autoRenew) "On" else "Off"), Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(8.dp))
                        if (frozen) {
                            Text(
                                t("Paused since %s. Days frozen are added back when resumed.", DateUtils.formatShort(m.frozenFrom)),
                                style = MaterialTheme.typography.labelMedium, color = FrozenBlue
                            )
                        } else {
                            Text(I18n.expiryText(lang, plan.expiryDate), style = MaterialTheme.typography.labelMedium, color = status.color)
                        }
                        if (m.isActive) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (frozen) {
                                    Button(
                                        onClick = { scope.launch { app.repository.unfreeze(m.id); toast(t_("Membership resumed", lang)) } },
                                        modifier = Modifier.weight(1f).testTag("card_resume")
                                    ) { Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Resume")) }
                                } else {
                                    OutlinedButton(onClick = { confirmFreeze = true }, modifier = Modifier.weight(1f).testTag("card_freeze")) {
                                        Icon(Icons.Default.AcUnit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Freeze"))
                                    }
                                    if (!plan.autoRenew) {
                                        OutlinedButton(onClick = {
                                            scope.launch { app.repository.renewMembership(m.id); toast(t_("Membership renewed, a new due was added", lang)) }
                                        }, modifier = Modifier.weight(1f)) {
                                            Icon(Icons.Default.Autorenew, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Renew"))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                InfoBlock {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Payment History"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(Money.format(history.sumOf { it.amount }), style = MaterialTheme.typography.labelLarge, color = StatusGreen)
                    }
                    if (history.isEmpty()) {
                        Spacer(Modifier.height(6.dp))
                        Text(t("No payments recorded yet"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        val shown = if (showAllHistory) history else history.take(4)
                        shown.forEach { p ->
                            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        "${Money.format(p.amount)} • ${I18n.payModeLabel(lang, p.mode)}",
                                        style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        "${DateUtils.formatShort(p.paidAt.take(10))} • ${p.receiptNo}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { scope.launch { ShareActions.shareReceipt(context, p.receiptNo) } }) {
                                    Icon(Icons.Default.Receipt, t("Send receipt"), tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { undoTarget = p }) {
                                    Icon(Icons.AutoMirrored.Filled.Undo, t("Undo payment"), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                        if (history.size > 4) {
                            TextButton(onClick = { showAllHistory = !showAllHistory }) {
                                Text(if (showAllHistory) t("Show less") else t("Show all %d payments", history.size))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))

                InfoBlock {
                    val pct = if (d.recordCount > 0) d.presentCount * 100 / d.recordCount else 0
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(t("Attendance Record"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                        Text(t("%d present (%d%%)", d.presentCount, pct), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { if (d.recordCount > 0) d.presentCount.toFloat() / d.recordCount else 0f },
                        modifier = Modifier.fillMaxWidth().height(6.dp)
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(t("Out of %d recorded sessions", d.recordCount), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(Modifier.height(12.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { onDismiss(); onEdit(m.id) }, modifier = Modifier.weight(1f).testTag("card_edit")) {
                        Icon(Icons.Default.Edit, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Edit"))
                    }
                    OutlinedButton(
                        onClick = {
                            if (m.isActive) {
                                confirmDeactivate = true
                            } else {
                                scope.launch { app.repository.setActive(m.id, true); toast(t_("%s reactivated", lang, m.fullName)) }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(if (m.isActive) Icons.Default.PersonOff else Icons.Default.Restore, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(t(if (m.isActive) "Deactivate" else "Reactivate"), maxLines = 1)
                    }
                }
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(Icons.Default.DeleteForever, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Delete Permanently"))
                }
            }

            if (confirmFreeze) {
                ConfirmDialog(
                    title = t("Freeze membership?"),
                    message = t("Use this when %s takes a break. No new dues are created while frozen, and the paused days are added back to the plan when you resume.", m.fullName),
                    confirmText = t("Freeze"),
                    onConfirm = {
                        confirmFreeze = false
                        scope.launch { app.repository.freeze(m.id); toast(t_("Membership frozen", lang)) }
                    },
                    onDismiss = { confirmFreeze = false }
                )
            }
            if (confirmDeactivate) {
                ConfirmDialog(
                    title = t("Deactivate member?"),
                    message = t("%s will be hidden from attendance. Their history and any pending dues are kept, and you can reactivate any time.", m.fullName),
                    confirmText = t("Deactivate"),
                    onConfirm = {
                        confirmDeactivate = false
                        scope.launch { app.repository.setActive(m.id, false); toast(t_("%s deactivated", lang, m.fullName)) }
                    },
                    onDismiss = { confirmDeactivate = false }
                )
            }
            if (confirmDelete) {
                ConfirmDialog(
                    title = t("Delete permanently?"),
                    message = t("This removes %s, their photo, attendance, memberships and payment history. This cannot be undone.", m.fullName),
                    confirmText = t("Delete"),
                    destructive = true,
                    onConfirm = {
                        confirmDelete = false
                        BiometricHelper.guard(context, settings, ProtectedAction.DELETE_MEMBER, t_("Delete %s", lang, m.fullName)) {
                            scope.launch {
                                app.repository.deleteMember(m.id)
                                toast(t_("Member deleted", lang))
                                onDismiss()
                            }
                        }
                    },
                    onDismiss = { confirmDelete = false }
                )
            }
            undoTarget?.let { p ->
                ConfirmDialog(
                    title = t("Undo this payment?"),
                    message = t("%s (%s) will be removed and the amount becomes due again.", Money.format(p.amount), p.receiptNo),
                    confirmText = t("Undo"),
                    destructive = true,
                    onConfirm = {
                        undoTarget = null
                        scope.launch { app.repository.undoPayment(p.id); toast(t_("Payment reversed", lang)) }
                    },
                    onDismiss = { undoTarget = null }
                )
            }
        }
    }
}

/** Non-composable translation helper for callbacks. */
fun t_(en: String, lang: String, vararg args: Any): String = I18n.tr(lang, en, *args)

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
