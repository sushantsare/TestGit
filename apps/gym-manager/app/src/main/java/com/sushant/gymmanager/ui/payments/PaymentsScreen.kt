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
import com.sushant.gymmanager.ui.theme.WhatsAppInk
import com.sushant.gymmanager.ui.theme.OnStatus
import com.sushant.gymmanager.ui.components.SearchField
import com.sushant.gymmanager.ui.components.matches
import com.sushant.gymmanager.util.ShareUtil
import androidx.compose.material.icons.filled.Share
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
        pays.map { HistoryRow(it, mm[it.memberId], cc[it.cycleId]) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var duesQuery by mutableStateOf("")
    var historyQuery by mutableStateOf("")
    /** History shows this many rows; "Load more" adds another page. */
    var historyLimit by mutableStateOf(50)

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
    val filtered = all?.filter { (vm.filter == "ALL" || it.member.shift == vm.filter) && it.member.matches(vm.duesQuery) }
    val slots = remember(filtered) {
        filtered?.groupBy { it.member.fromTime to it.member.toTime }
            ?.map { (k, v) -> DuesSlot(k.first, k.second, v) }
            ?.sortedWith(compareBy({ it.from }, { it.to }))
    }
    val cycleById = remember(all) { all.orEmpty().flatMap { it.cycles }.associateBy { it.id } }
    val selectedTotal = vm.selected.sumOf { cycleById[it]?.balance ?: 0.0 }
    var collecting by remember { mutableStateOf(false) }
    var receipts by remember { mutableStateOf<List<ReceiptRef>>(emptyList()) }
    val shareTitle = t("Today's collection")
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
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = OnStatus),
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
                        item { SummaryBanner(filtered.orEmpty().sumOf { it.total }, filtered.orEmpty().size, filtered.orEmpty().flatMap { it.cycles }) }
                        item { SearchField(vm.duesQuery, { vm.duesQuery = it }, t("Search name, roll no. or WhatsApp"), tag = "dues_search") }
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
                HistoryList(
                    rows = history,
                    query = vm.historyQuery,
                    onQuery = { vm.historyQuery = it; vm.historyLimit = 50 },
                    limit = vm.historyLimit,
                    onMore = { vm.historyLimit += 50 },
                    onShareToday = { text -> ShareUtil.shareText(context, text, shareTitle) },
                    gymName = settings.gymName,
                    onReceipt = { scope.launch { ShareActions.shareReceipt(context, it) } },
                    onUndo = { undoRow = it }
                )
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
                }
            }
        )
    }
    if (receipts.isNotEmpty()) {
        ReceiptsDialog(receipts, receiptNames, onDismiss = { receipts = emptyList() })
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
                    colors = ButtonDefaults.buttonColors(containerColor = WhatsAppInk, contentColor = OnStatus),
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
private fun HistoryList(
    rows: List<HistoryRow>?,
    query: String,
    onQuery: (String) -> Unit,
    limit: Int,
    onMore: () -> Unit,
    onShareToday: (String) -> Unit,
    gymName: String,
    onReceipt: (String) -> Unit,
    onUndo: (HistoryRow) -> Unit
) {
    val lang = currentLang()
    when {
        rows == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        rows.isEmpty() -> EmptyState(Icons.Default.History, t("No payments yet"), t("Payments you collect will be listed here with their receipts."))
        else -> {
            val term = query.trim()
            val matching = if (term.isEmpty()) rows else rows.filter { r ->
                r.payment.receiptNo.contains(term, ignoreCase = true) || (r.member?.matches(term) ?: false)
            }
            val shown = matching.take(limit)
            val today = DateUtils.today()
            val todayRows = rows.filter { it.payment.paidAt.startsWith(today) }
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.testTag("history_list")
            ) {
                item(key = "closing") { TodayClosingCard(todayRows, gymName, lang, onShareToday) }
                item(key = "hsearch") { SearchField(query, onQuery, t("Search name, roll no. or receipt no."), tag = "history_search") }
                if (shown.isEmpty()) {
                    item(key = "nomatch") { EmptyState(Icons.Default.History, t("No matches"), t("No payment matches \"%s\".", query)) }
                }
                shown.groupBy { it.payment.paidAt.take(10) }.forEach { (day, list) ->
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
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            row.member?.fullName ?: t("Deleted member"), style = MaterialTheme.typography.titleSmall,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(Money.format(row.payment.amount), style = MaterialTheme.typography.titleSmall, color = StatusGreen, maxLines = 1)
                                    }
                                    Text(
                                        "${row.payment.receiptNo} • ${I18n.payModeLabel(lang, row.payment.mode)}",
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                    row.cycle?.let {
                                        Text(
                                            "${DateUtils.formatShort(it.cycleStart)} – ${DateUtils.formatShort(it.cycleEnd)}",
                                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1, overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                IconButton(onClick = { onReceipt(row.payment.receiptNo) }) { Icon(Icons.Default.Receipt, t("Send receipt"), tint = MaterialTheme.colorScheme.primary) }
                                IconButton(onClick = { onUndo(row) }) { Icon(Icons.AutoMirrored.Filled.Undo, t("Undo payment")) }
                            }
                        }
                    }
                }
                if (matching.size > shown.size) {
                    item(key = "more") {
                        OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth().testTag("history_more")) {
                            Text(t("Load more (%d left)", matching.size - shown.size))
                        }
                    }
                }
            }
        }
    }
}

/** Today's cash closing: what came in today, split by payment mode, ready to share. */
@Composable
private fun TodayClosingCard(rows: List<HistoryRow>, gymName: String, lang: String, onShare: (String) -> Unit) {
    val total = rows.sumOf { it.payment.amount }
    val byMode = PayMode.all.associateWith { m -> rows.filter { it.payment.mode == m }.sumOf { it.payment.amount } }.filterValues { it > 0 }
    AppCard(Modifier.fillMaxWidth().testTag("today_closing")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(t("TODAY'S COLLECTION"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(Money.format(total), style = MaterialTheme.typography.headlineSmall)
                Text(t(if (rows.size == 1) "%d payment" else "%d payments", rows.size), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = {
                    val lines = buildString {
                        append(I18n.tr(lang, "%s • Collection for %s", gymName, DateUtils.formatFull(DateUtils.today()))).append("\n\n")
                        byMode.forEach { (m, v) -> append(I18n.payModeLabel(lang, m)).append(": ").append(Money.format(v)).append("\n") }
                        append(I18n.tr(lang, "Total: %s (%d payments)", Money.format(total), rows.size))
                    }
                    onShare(lines)
                },
                enabled = rows.isNotEmpty(),
                modifier = Modifier.testTag("share_closing")
            ) {
                Icon(Icons.Default.Share, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Share"))
            }
        }
        if (byMode.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                byMode.forEach { (m, v) ->
                    Column {
                        Text(I18n.payModeLabel(lang, m).substringBefore(" "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(v), style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryBanner(total: Double, members: Int, cycles: List<PaymentCycleEntity>) {
    val today = java.time.LocalDate.now()
    fun age(c: PaymentCycleEntity) = java.time.temporal.ChronoUnit.DAYS.between(DateUtils.parseDate(c.cycleStart) ?: today, today)
    val fresh = cycles.filter { age(it) <= 30 }.sumOf { it.balance }
    val mid = cycles.filter { age(it) in 31..60 }.sumOf { it.balance }
    val old = cycles.filter { age(it) > 60 }.sumOf { it.balance }
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp))
            .background(
                if (total > 0) Brush.linearGradient(listOf(Color(0xFFDC2626), Color(0xFF9F1239)))
                else Brush.linearGradient(listOf(Color(0xFF15803D), Color(0xFF0F766E)))
            )
            .padding(20.dp)
    ) {
        Column {
            Text(t("TOTAL OUTSTANDING"), style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(Money.format(total), style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.testTag("dues_total"))
            Text(
                if (members == 0) t("Nothing pending") else t(if (members == 1) "%d member has unpaid cycles" else "%d members have unpaid cycles", members),
                style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.95f)
            )
            if (total > 0) {
                // How old the dues are, so the oldest get chased first.
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.testTag("dues_age")) {
                    AgeBox(Modifier.weight(1f), t("0–30 days"), fresh)
                    AgeBox(Modifier.weight(1f), t("31–60 days"), mid)
                    AgeBox(Modifier.weight(1f), t("60+ days"), old)
                }
            }
        }
    }
}

@Composable
private fun AgeBox(modifier: Modifier, label: String, amount: Double) {
    Surface(modifier, shape = RoundedCornerShape(12.dp), color = Color.Black.copy(alpha = 0.22f)) {
        Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(Money.format(amount), style = MaterialTheme.typography.titleSmall, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
                colors = ButtonDefaults.outlinedButtonColors(contentColor = WhatsAppInk),
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
                    if (c.paidAmount > 0 || c.waivedAmount > 0) {
                        Text(
                            if (c.waivedAmount > 0) t("%s paid, %s waived of %s", Money.format(c.paidAmount), Money.format(c.waivedAmount), Money.format(c.amount))
                            else t("%s paid of %s", Money.format(c.paidAmount), Money.format(c.amount)),
                            style = MaterialTheme.typography.labelSmall, color = StatusGreen
                        )
                    }
                }
                Text(Money.format(c.balance), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
