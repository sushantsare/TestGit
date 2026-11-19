package com.sushant.gymmanager.ui.payments

import android.app.Application
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.Shift
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.EmptyState
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.MemberAvatar
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
    val total: Double get() = cycles.sumOf { it.amount }
}

data class DuesSlot(val from: String, val to: String, val members: List<MemberDues>)

class PaymentsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GymApp).repository

    init {
        viewModelScope.launch { repo.checkAndGenerateAutoRenewalCycles() }
    }

    val settings: StateFlow<GymSettingsEntity> =
        repo.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GymSettingsEntity())

    val dues: StateFlow<List<MemberDues>?> = combine(repo.activeMembers, repo.unpaidCycles) { members, unpaid ->
        val byMember = unpaid.groupBy { it.memberId }
        members.mapNotNull { m -> byMember[m.id]?.sortedBy { it.cycleStart }?.let { MemberDues(m, it) } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var filter by mutableStateOf("ALL")
    var selected by mutableStateOf<Set<Long>>(emptySet())
        private set

    fun toggle(id: Long) { selected = if (id in selected) selected - id else selected + id }
    fun toggleMember(d: MemberDues) {
        val ids = d.cycles.map { it.id }
        selected = if (selected.containsAll(ids)) selected - ids.toSet() else selected + ids
    }
    fun clear() { selected = emptySet() }

    fun markPaid(onDone: (Int) -> Unit) {
        val ids = selected.toList()
        viewModelScope.launch {
            repo.markPaid(ids)
            selected = emptySet()
            onDone(ids.size)
        }
    }
}

@Composable
fun PaymentsScreen(onOpenDrawer: () -> Unit, vm: PaymentsViewModel = viewModel()) {
    val context = LocalContext.current
    val all by vm.dues.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val filtered = all?.filter { vm.filter == "ALL" || it.member.shift == vm.filter }
    val slots = remember(filtered) {
        filtered?.groupBy { it.member.fromTime to it.member.toTime }
            ?.map { (k, v) -> DuesSlot(k.first, k.second, v) }
            ?.sortedWith(compareBy({ it.from }, { it.to }))
    }
    val cycleById = remember(all) { all.orEmpty().flatMap { it.cycles }.associateBy { it.id } }
    val selectedTotal = vm.selected.sumOf { cycleById[it]?.amount ?: 0.0 }

    Scaffold(
        topBar = { GymTopBar("Payments", "Outstanding dues", onMenu = onOpenDrawer) },
        bottomBar = {
            AnimatedVisibility(
                visible = vm.selected.isNotEmpty(),
                enter = slideInVertically { it },
                exit = slideOutVertically { it }
            ) {
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp) {
                    Column {
                        HorizontalDivider(color = LocalExtraColors.current.cardBorder)
                        Row(
                            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("${vm.selected.size} selected", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(Money.format(selectedTotal), style = MaterialTheme.typography.titleLarge)
                            }
                            TextButton(onClick = vm::clear) { Text("Cancel") }
                            Spacer(Modifier.width(4.dp))
                            Button(
                                onClick = {
                                    vm.markPaid { n ->
                                        Toast.makeText(context, "$n ${if (n == 1) "cycle" else "cycles"} marked as paid", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = StatusGreen, contentColor = Color.White),
                                modifier = Modifier.height(48.dp).testTag("mark_paid")
                            ) {
                                Icon(Icons.Default.DoneAll, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Mark as Paid")
                            }
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val tabs = listOf("MORNING" to "Morning", "EVENING" to "Evening", "ALL" to "All Shifts")
            TabRow(selectedTabIndex = tabs.indexOfFirst { it.first == vm.filter }, containerColor = MaterialTheme.colorScheme.background) {
                tabs.forEach { (key, label) ->
                    Tab(selected = vm.filter == key, onClick = { vm.filter = key }, text = { Text(label) })
                }
            }
            when {
                slots == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("payments_list")
                ) {
                    item {
                        val total = filtered.orEmpty().sumOf { it.total }
                        SummaryBanner(total, filtered.orEmpty().size)
                    }
                    if (slots.isEmpty()) {
                        item {
                            EmptyState(Icons.Default.Verified, "No outstanding dues", "Every member in this view is fully paid up. 🎉")
                        }
                    }
                    slots.forEach { slot ->
                        item(key = "slot_${slot.from}_${slot.to}") {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Icon(Icons.Default.Schedule, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    DateUtils.formatTimeSlot(slot.from, slot.to),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        items(slot.members, key = { it.member.id }) { d ->
                            DuesCard(
                                d, vm.selected,
                                onToggle = vm::toggle,
                                onToggleAll = { vm.toggleMember(d) },
                                onRemind = {
                                    val msg = WhatsAppHelper.buildReminderMessage(
                                        d.member.fullName, settings.gymName,
                                        d.cycles.map { DuePeriod(it.cycleStart, it.cycleEnd, it.amount) }
                                    )
                                    WhatsAppHelper.open(context, d.member.whatsappNumber, msg)
                                }
                            )
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
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
            Text("TOTAL OUTSTANDING", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.85f))
            Text(Money.format(total), style = MaterialTheme.typography.headlineLarge, color = Color.White, modifier = Modifier.testTag("dues_total"))
            Text(
                if (members == 0) "Nothing pending" else "$members ${if (members == 1) "member has" else "members have"} unpaid cycles",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.9f)
            )
        }
    }
}

@Composable
private fun DuesCard(
    d: MemberDues,
    selected: Set<Long>,
    onToggle: (Long) -> Unit,
    onToggleAll: () -> Unit,
    onRemind: () -> Unit
) {
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
                }
            }
            OutlinedButton(
                onClick = onRemind,
                contentPadding = PaddingValues(horizontal = 12.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF128C4A)),
                border = androidx.compose.foundation.BorderStroke(1.dp, WhatsAppGreen.copy(alpha = 0.6f))
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Remind")
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
                Text(
                    "${DateUtils.formatShort(c.cycleStart)} – ${DateUtils.formatShort(c.cycleEnd)}",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f)
                )
                if (checked) Icon(Icons.Default.CheckCircle, null, Modifier.size(16.dp).padding(end = 2.dp), tint = StatusGreen)
                Spacer(Modifier.width(4.dp))
                Text(Money.format(c.amount), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
