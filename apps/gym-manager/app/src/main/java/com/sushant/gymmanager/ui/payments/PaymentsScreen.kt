package com.sushant.gymmanager.ui.payments

import com.sushant.gymmanager.util.toast
import android.app.Application
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.PaymentEntity
import com.sushant.gymmanager.data.repository.ReceiptRef
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.EmptyState
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.Pill
import com.sushant.gymmanager.ui.components.RollBadge
import com.sushant.gymmanager.ui.theme.LocalExtraColors
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.ui.theme.StatusRed
import com.sushant.gymmanager.ui.theme.WhatsAppGreen
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.DuePeriod
import com.sushant.gymmanager.util.Money
import com.sushant.gymmanager.util.WhatsAppHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemberDues(val member: MemberEntity, val cycles: List<PaymentCycleEntity>) {
    val total: Double get() = cycles.sumOf { it.balance }
}

data class DuesSlot(val from: String, val to: String, val members: List<MemberDues>)

data class HistoryRow(val payment: PaymentEntity, val member: MemberEntity?, val cycle: PaymentCycleEntity?)

class PaymentsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GymApp).repository

    init {
        viewModelScope.launch { repo.checkAndGenerateAutoRenewalCycles() }
    }

    val settings: StateFlow<GymSettingsEntity> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GymSettingsEntity())

    /** Every member (active or deactivated) that still owes money. */
    val dues: StateFlow<List<MemberDues>?> = combine(repo.allMembers, repo.unpaidCycles) { members, unpaid ->
        val byMember = unpaid.filter { it.balance > 0.0 }.groupBy { it.memberId }
        members.mapNotNull { m -> byMember[m.id]?.sortedBy { it.cycleStart }?.let { MemberDues(m, it) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val history: StateFlow<List<HistoryRow>?> = combine(repo.allPayments, repo.allMembers, repo.allCycles) { pays, members, cycles ->
        val mm = members.associateBy { it.id }
        val cc = cycles.associateBy { it.id }
        pays.take(300).map { HistoryRow(it, mm[it.memberId], cc[it.cycleId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var filter by mutableStateOf("ALL")
    var tab by mutableStateOf(0)
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set

    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun toggleMember(d: MemberDues) {
        val ids = d.cycles.map { it.id }
        selected = if (selected.containsAll(ids)) selected - ids.toSet() else selected + ids
    }
    fun clear() { selected = emptySet() }

    fun record(amounts: Map<Long, Double>, mode: String, note: String, onDone: (List<ReceiptRef>) -> Unit) {
        viewModelScope.launch {
            val refs = repo.recordPayments(amounts, mode, note)
            selected = emptySet()
            onDone(refs)
        }
    }

    fun undo(paymentId: Long) {
        viewModelScope.launch { repo.undoPayment(paymentId) }
    }
}

@Composable
fun PaymentsScreen(onOpenDrawer: () -> Unit, vm: PaymentsViewModel = viewModel()) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val lang = currentLang()
    val all by vm.dues.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filtered = all?.filter { vm.filter == "ALL" || it.member.shift == vm.filter }
    val slots = remember(filtered) {
        filtered?.groupBy { it.member.fromTime to it.member.toTime }
            ?.map { (k, v) -> DuesSlot(k.first, k.second, v) }
            ?.sortedWith(compareBy({ it.from }, { it.to }))
    }
    val cycleById = remember(all) { all.orEmpty().flatMap { it.cycles }.associateBy { it.id } }
    val selectedTotal = vm.selected.sumOf { cycleById[it]?.balance ?: 0.0 }
    var collecting by remember { mutableStateOf(false) }
    var receipts by remember { mutableStateOf<List<ReceiptRef>>(emptyList()) }
    var receiptNames by remember { mutableStateOf<Map<Long, String>>(emptyMap()) }
    var bulk by remember { mutableStateOf(false) }
    var undoRow by remember { mutableStateOf<HistoryRow?>(null) }

    Scaffold(
        topBar = {
            GymTopBar(t("Payments"), t(if (vm.tab == 0) "Outstanding dues" else "Payment history"), onMenu = onOpenDrawer, actions = {
                if (vm.tab == 0 && !all.isNullOrEmpty()) {
                    IconButton(onClick = { bulk = true }, modifier = Modifier.testTag("remind_all")) {
                        Icon(Icons.Default.Campaign, t("Remind everyone"))
                    }
                }
            })
        },
        bottomBar = {
            AnimatedVisibility(visible = vm.tab == 0 && vm.selected.isNotEmpty(), enter = slideInVertically { it }, exit = slideOutVertically { it }) {
                Surface(color = MaterialTheme.colorScheme.surface) {
                    Column {
                        HorizontalDivider(color = LocalExtraColors.current.cardBorder)
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(t("%d selected", vm.selected.size), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.format(selectedTotal), style = MaterialTheme.typography.titleLarge)
                            }
                            TextButton(onClick = vm::clear) { Text(t("Cancel")) }
                            Spacer(Modifier.width(4.dp))
                            Button(
                                onClick = { collecting = true },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color.White),
                                modifier = Modifier.height(48.dp).testTag("mark_paid")
                            ) {
                                Icon(Icons.Default.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Collect Payment"))
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = vm.tab, containerColor = MaterialTheme.colorScheme.background) {
                Tab(selected = vm.tab == 0, onClick = { vm.tab = 0 }, text = { Text(t("Outstanding")) }, icon = { Icon(Icons.Default.CurrencyRupee, null) })
                Tab(
                    selected = vm.tab == 1, onClick = { vm.tab = 1; vm.clear() }, text = { Text(t("History")) },
                    icon = { Icon(Icons.Default.History, null) }, modifier = Modifier.testTag("tab_history")
                )
            }
            if (vm.tab == 0) {
                if (slots == null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.testTag("payments_list")
                    ) {
                        item { SummaryBanner(filtered.orEmpty().sumOf { it.total }, filtered.orEmpty().size) }
                        item {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("ALL" to "All Shifts", "MORNING" to "Morning", "EVENING" to "Evening").forEach { (key, label) ->
                                    FilterChip(
                                        selected = vm.filter == key, onClick = { vm.filter = key }, label = { Text(t(label)) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    )
                                }
                            }
                        }
                        if (slots.isEmpty()) {
                            item { EmptyState(Icons.Default.Verified, t("No outstanding dues"), t("Every member in this view is fully paid up.")) }
                        }
                        slots.forEach { slot ->
                            item(key = "slot_${slot.from}_${slot.to}") {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                    Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(6.dp))
                                    Text(DateUtils.formatTimeSlot(slot.from, slot.to), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            items(slot.members, key = { it.member.id }) { d ->
                                DuesCard(
                                    d, vm.selected,
                                    onToggle = vm::toggle,
                                    onToggleAll = { vm.toggleMember(d) },
                                    onRemind = { remind(context, d, settings.gymName, lang) }
                                )
                            }
                        }
                        item { Spacer(Modifier.height(72.dp)) }
                    }
                }
            } else {
                HistoryList(history, onReceipt = { scope.launch { ShareActions.shareReceipt(context, it) } }, onUndo = { undoRow = it })
            }
        }
    }

    if (collecting) {
        val chosen = vm.selected.mapNotNull { cycleById[it] }
        val names = all.orEmpty().associate { it.member.id to it.member.fullName }
        CollectPaymentDialog(
            cycles = chosen,
            onDismiss = { collecting = false },
            onConfirm = { amounts, mode, note ->
                collecting = false
                vm.record(amounts, mode, note) { refs ->
                    receiptNames = names
                    receipts = refs
                    context.toast(I18n.tr(lang, "Payment recorded"))
                }
            }
        )
    }
    if (receipts.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = { receipts = emptyList() },
            icon = { Icon(Icons.Default.CheckCircle, null, tint = StatusGreen) },
            title = { Text(t("Payment recorded")) },
            text = {
                Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(t("Send a receipt on WhatsApp:"), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    receipts.forEach { r ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(receiptNames[r.memberId].orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${r.receiptNo} • ${Money.format(r.total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            OutlinedButton(onClick = { scope.launch { ShareActions.shareReceipt(context, r.receiptNo) } }) {
                                Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Send"))
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { receipts = emptyList() }, modifier = Modifier.testTag("receipts_done")) { Text(t("Done")) } }
        )
    }
    if (bulk) {
        BulkReminderDialog(all.orEmpty(), settings.gymName, onDismiss = { bulk = false })
    }
    undoRow?.let { row ->
        ConfirmDialog(
            title = t("Undo this payment?"),
            message = t("%s (%s) will be removed and the amount becomes due again.", Money.format(row.payment.amount), row.payment.receiptNo),
            confirmText = t("Undo"),
            destructive = true,
            onConfirm = { vm.undo(row.payment.id); undoRow = null },
            onDismiss = { undoRow = null }
        )
    }
}

private fun remind(context: android.content.Context, d: MemberDues, gymName: String, lang: String) {
    val msg = WhatsAppHelper.buildReminderMessage(d.member.fullName, gymName, d.cycles.map { DuePeriod(it.cycleStart, it.cycleEnd, it.balance) }, lang)
    WhatsAppHelper.open(context, d.member.whatsappNumber, msg)
}

@Composable
private fun CollectPaymentDialog(cycles: List<PaymentCycleEntity>, onDismiss: () -> Unit, onConfirm: (Map<Long, Double>, String, String) -> Unit) {
    val single = cycles.size == 1
    val total = cycles.sumOf { it.balance }
    var mode by remember { mutableStateOf(PayMode.CASH) }
    var amountText by remember { mutableStateOf(formatAmount(total)) }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()
    val valid = if (single) amount != null && amount > 0 && amount <= total + 0.005 else true
    val lang = currentLang()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Collect Payment")) },
        text = {
            Column {
                Text(t("Payment mode"), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PayMode.all.forEach { m ->
                        FilterChip(
                            selected = mode == m, onClick = { mode = m },
                            label = { Text(I18n.payModeLabel(lang, m).substringBefore(" "), maxLines = 1) },
                            modifier = Modifier.testTag("mode_$m")
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                if (single) {
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                        label = { Text(t("Amount received")) },
                        prefix = { Text("₹") },
                        singleLine = true,
                        isError = !valid,
                        supportingText = {
                            val rest = (total - (amount ?: 0.0)).coerceAtLeast(0.0)
                            Text(if (rest > 0.005) t("Partial payment • %s will remain due", Money.format(rest)) else t("Full payment"))
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth().testTag("pay_amount")
                    )
                } else {
                    Text(t("%d cycles will be marked fully paid", cycles.size), style = MaterialTheme.typography.bodyMedium)
                    Text(Money.format(total), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, label = { Text(t("Note (optional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val map = if (single) mapOf(cycles.first().id to (amount ?: 0.0)) else cycles.associate { it.id to it.balance }
                    onConfirm(map, mode, note)
                },
                enabled = valid,
                modifier = Modifier.testTag("confirm_payment")
            ) { Text(t("Record Payment")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
}

private fun formatAmount(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.ENGLISH, "%.2f", v)

@Composable
private fun BulkReminderDialog(dues: List<MemberDues>, gymName: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val lang = currentLang()
    var sent by remember { mutableStateOf(setOf<Long>()) }
    val next = dues.firstOrNull { it.member.id !in sent }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Campaign, null, tint = MaterialTheme.colorScheme.primary) },
        title = { Text(t("Remind everyone")) },
        text = {
            Column {
                Text(
                    t("Tap Send next: WhatsApp opens with the reminder ready. Come back here and continue with the next member."),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                Text(t("%d of %d sent", sent.size, dues.size), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(8.dp))
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    dues.forEach { d ->
                        val done = d.member.id in sent
                        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                if (done) Icons.Default.CheckCircle else Icons.AutoMirrored.Filled.Send, null, Modifier.size(18.dp),
                                tint = if (done) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(d.member.fullName, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                            Text(Money.format(d.total), style = MaterialTheme.typography.labelLarge, color = StatusRed)
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (next != null) {
                Button(
                    onClick = { sent = sent + next.member.id; remind(context, next, gymName, lang) },
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppGreen, contentColor = Color.White),
                    modifier = Modifier.testTag("send_next")
                ) { Text(t("Send next: %s", next.member.fullName.substringBefore(" ")), maxLines = 1) }
            } else {
                Button(onClick = onDismiss) { Text(t("All sent")) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Close")) } }
    )
}

@Composable
private fun HistoryList(rows: List<HistoryRow>?, onReceipt: (String) -> Unit, onUndo: (HistoryRow) -> Unit) {
    val lang = currentLang()
    when {
        rows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        rows.isEmpty() -> EmptyState(Icons.Default.History, t("No payments yet"), t("Payments you collect will be listed here with their receipts."))
        else -> LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("history_list")
        ) {
            rows.groupBy { it.payment.paidAt.take(10) }.forEach { (day, list) ->
                item(key = "day_$day") {
                    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(DateUtils.formatFull(day), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                        Text(Money.format(list.sumOf { it.payment.amount }), style = MaterialTheme.typography.labelLarge, color = StatusGreen)
                    }
                }
                items(list, key = { it.payment.id }) { row ->
                    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 12.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            MemberAvatar(row.member?.photoUri, row.member?.fullName ?: "?", size = 40.dp)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text(row.member?.fullName ?: t("Deleted member"), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${row.payment.receiptNo} • ${I18n.payModeLabel(lang, row.payment.mode)}",
                                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                )
                                row.cycle?.let {
                                    Text(
                                        "${DateUtils.formatShort(it.cycleStart)} – ${DateUtils.formatShort(it.cycleEnd)}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1
                                    )
                                }
                            }
                            Text(Money.format(row.payment.amount), style = MaterialTheme.typography.titleSmall, color = StatusGreen)
                            IconButton(onClick = { onReceipt(row.payment.receiptNo) }) { Icon(Icons.Default.Receipt, t("Send receipt"), tint = MaterialTheme.colorScheme.primary) }
                            IconButton(onClick = { onUndo(row) }) { Icon(Icons.AutoMirrored.Filled.Undo, t("Undo payment")) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBanner(total: Double, members: Int) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(
                if (total > 0) Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFF9F1239)))
                else Brush.linearGradient(listOf(Color(0xFF16A34A), Color(0xFF0D9488)))
            )
            .padding(20.dp)
    ) {
        Column {
            Text(t("TOTAL OUTSTANDING"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(Money.format(total), style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.testTag("dues_total"))
            Text(
                if (members == 0) t("Nothing pending") else t(if (members == 1) "%d member has unpaid cycles" else "%d members have unpaid cycles", members),
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun DuesCard(d: MemberDues, selected: Set<Long>, onToggle: (Long) -> Unit, onToggleAll: () -> Unit, onRemind: () -> Unit) {
    AppCard(Modifier.fillMaxWidth().testTag("dues_${d.member.rollNumber}"), contentPadding = PaddingValues(vertical = 10.dp)) {
        Row(Modifier.padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(d.member.photoUri, d.member.fullName, size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f).clickable(onClick = onToggleAll)) {
                Text(d.member.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollBadge(d.member.rollNumber)
                    Spacer(Modifier.width(8.dp))
                    Text(Money.format(d.total), style = MaterialTheme.typography.labelLarge, color = StatusRed)
                    if (!d.member.isActive) {
                        Spacer(Modifier.width(6.dp))
                        Pill(t("DEACTIVATED"), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            OutlinedButton(
                onClick = onRemind,
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF128C4A)),
                border = BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.6f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Remind"))
            }
        }
        Spacer(Modifier.height(6.dp))
        d.cycles.forEach { c ->
            val checked = c.id in selected
            Row(
                Modifier.fillMaxWidth().clickable { onToggle(c.id) }.padding(start = 8.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = { onToggle(c.id) }, modifier = Modifier.testTag("cycle_${c.id}"))
                Column(Modifier.weight(1f)) {
                    Text("${DateUtils.formatShort(c.cycleStart)} – ${DateUtils.formatShort(c.cycleEnd)}", style = MaterialTheme.typography.bodyMedium)
                    if (c.paidAmount > 0) {
                        Text(
                            t("%s paid of %s", Money.format(c.paidAmount), Money.format(c.amount)),
                            style = MaterialTheme.typography.labelSmall, color = StatusGreen
                        )
                    }
                }
                Text(Money.format(c.balance), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
