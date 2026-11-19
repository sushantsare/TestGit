package com.sushant.gymmanager.ui.dashboard

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CurrencyRupee
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.MembershipEntity
import com.sushant.gymmanager.data.local.Shift
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.RollBadge
import com.sushant.gymmanager.ui.components.SectionTitle
import com.sushant.gymmanager.ui.components.WhatsAppButton
import com.sushant.gymmanager.ui.members.MemberCardDialog
import com.sushant.gymmanager.ui.navigation.Routes
import com.sushant.gymmanager.ui.theme.StatusAmber
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.ui.theme.StatusRed
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.DuePeriod
import com.sushant.gymmanager.util.Money
import com.sushant.gymmanager.util.WhatsAppHelper
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ExpiringItem(val member: MemberEntity, val membership: MembershipEntity, val daysLeft: Long, val dues: List<DuePeriod>)

data class DashboardState(
    val gymName: String,
    val activeCount: Int,
    val inactiveCount: Int,
    val frozenCount: Int,
    val morningPresent: Int,
    val morningExpected: Int,
    val eveningPresent: Int,
    val eveningExpected: Int,
    val pendingTotal: Double,
    val overdueCycles: Int,
    val membersWithDues: Int,
    val expiringCount: Int,
    val expiringDays: Int,
    val collectedThisMonth: Double,
    val expiring: List<ExpiringItem>
)

class DashboardViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as GymApp).repository

    init {
        viewModelScope.launch { repo.checkAndGenerateAutoRenewalCycles() }
    }

    private val base = combine(
        repo.settings,
        repo.activeMembersWithPlans,
        repo.allMembers,
        repo.unpaidCycles,
        repo.attendanceForDate(DateUtils.today())
    ) { settings, active, all, unpaid, attendance -> Base(settings, active, all, unpaid, attendance) }

    private data class Base(
        val settings: com.sushant.gymmanager.data.local.GymSettingsEntity,
        val active: List<com.sushant.gymmanager.data.repository.MemberWithPlan>,
        val all: List<MemberEntity>,
        val unpaid: List<com.sushant.gymmanager.data.local.PaymentCycleEntity>,
        val attendance: List<com.sushant.gymmanager.data.local.AttendanceEntity>
    )

    val state: StateFlow<DashboardState?> = combine(base, repo.allPayments) { b, payments ->
        val settings = b.settings
        val today = LocalDate.now()
        val running = b.active.filter { it.member.frozenFrom == null }
        val known = b.all.map { it.id }.toSet()
        val unpaid = b.unpaid.filter { it.memberId in known }
        val presentIds = b.attendance.filter { it.isPresent }
        val morningIds = running.filter { it.member.shift == Shift.MORNING }.map { it.member.id }.toSet()
        val eveningIds = running.filter { it.member.shift == Shift.EVENING }.map { it.member.id }.toSet()
        val duesByMember = unpaid.groupBy { it.memberId }
        val expiring = running.mapNotNull { mp ->
            val plan = mp.membership ?: return@mapNotNull null
            if (!DateUtils.isExpiringSoon(plan.expiryDate, settings.expiringSoonDays, today)) return@mapNotNull null
            ExpiringItem(
                mp.member, plan, DateUtils.daysUntil(plan.expiryDate, today),
                duesByMember[mp.member.id].orEmpty().map { DuePeriod(it.cycleStart, it.cycleEnd, it.balance) }
            )
        }.sortedBy { it.daysLeft }
        val monthPrefix = today.toString().take(7)
        DashboardState(
            gymName = settings.gymName,
            activeCount = b.active.size,
            inactiveCount = b.all.size - b.active.size,
            frozenCount = b.active.size - running.size,
            morningPresent = presentIds.count { it.shift == Shift.MORNING && it.memberId in morningIds },
            morningExpected = morningIds.size,
            eveningPresent = presentIds.count { it.shift == Shift.EVENING && it.memberId in eveningIds },
            eveningExpected = eveningIds.size,
            pendingTotal = unpaid.sumOf { it.balance },
            overdueCycles = unpaid.count { (DateUtils.parseDate(it.cycleStart) ?: today) <= today },
            membersWithDues = duesByMember.size,
            expiringCount = expiring.size,
            expiringDays = settings.expiringSoonDays,
            collectedThisMonth = payments.filter { it.paidAt.startsWith(monthPrefix) }.sumOf { it.amount },
            expiring = expiring.take(5)
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}

@Composable
fun DashboardScreen(
    onOpenDrawer: () -> Unit,
    onNavigate: (String) -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    vm: DashboardViewModel = viewModel()
) {
    val state by vm.state.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Long?>(null) }
    val context = LocalContext.current
    val lang = currentLang()

    Scaffold(
        topBar = {
            GymTopBar(
                title = state?.gymName ?: "",
                subtitle = t("Dashboard"),
                onMenu = onOpenDrawer,
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, t("More options")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(t("Backup & Restore")) },
                            leadingIcon = { Icon(Icons.Default.Backup, null) },
                            onClick = { menu = false; onNavigate(Routes.BACKUP) }
                        )
                        DropdownMenuItem(
                            text = { Text(t("Settings")) },
                            leadingIcon = { Icon(Icons.Default.Settings, null) },
                            onClick = { menu = false; onNavigate(Routes.SETTINGS) }
                        )
                    }
                }
            )
        }
    ) { padding ->
        val s = state
        if (s == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp).padding(bottom = 24.dp)
                .testTag("dashboard"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            HeroBanner(s.collectedThisMonth)

            // 2 x 2 overview
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        Modifier.weight(1f), Icons.Default.Groups, MaterialTheme.colorScheme.primary,
                        s.activeCount.toString(), t("Active Members"),
                        if (s.frozenCount > 0) t("%d inactive • %d frozen", s.inactiveCount, s.frozenCount) else t("%d inactive", s.inactiveCount)
                    ) { onNavigate(Routes.MEMBERS) }
                    MetricCard(
                        Modifier.weight(1f), Icons.Default.EventAvailable, StatusGreen,
                        "${s.morningPresent + s.eveningPresent}/${s.morningExpected + s.eveningExpected}",
                        t("Today's Attendance"), t("M: %d/%d • E: %d/%d", s.morningPresent, s.morningExpected, s.eveningPresent, s.eveningExpected)
                    ) { onNavigate(Routes.ATTENDANCE) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricCard(
                        Modifier.weight(1f), Icons.Default.CurrencyRupee, StatusRed,
                        Money.format(s.pendingTotal), t("Pending Dues"),
                        if (s.overdueCycles == 0) t("All clear") else t(if (s.overdueCycles == 1) "%d overdue cycle" else "%d overdue cycles", s.overdueCycles)
                    ) { onNavigate(Routes.PAYMENTS) }
                    MetricCard(
                        Modifier.weight(1f), Icons.Default.HourglassBottom, StatusAmber,
                        s.expiringCount.toString(), t("Expiring Soon"), t("Within %d days", s.expiringDays)
                    ) { onNavigate(Routes.MEMBERS) }
                }
            }

            // Quick actions
            SectionTitle(t("Quick Actions"))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Modifier.weight(1f), Icons.Default.PersonAdd, t("Add Member"), onAddMember)
                QuickAction(Modifier.weight(1f), Icons.Default.EventAvailable, t("Attendance")) { onNavigate(Routes.ATTENDANCE) }
                QuickAction(Modifier.weight(1f), Icons.Default.Payments, t("Payments")) { onNavigate(Routes.PAYMENTS) }
                QuickAction(Modifier.weight(1f), Icons.Default.BarChart, t("Reports")) { onNavigate(Routes.REPORTS) }
            }

            // Expiring list
            SectionTitle(t("Memberships Expiring Soon")) {
                if (s.expiringCount > 5) Text(
                    t("+%d more", s.expiringCount - 5),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            if (s.expiring.isEmpty()) {
                AppCard(Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(StatusGreen.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.ThumbUp, null, tint = StatusGreen) }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(t("No memberships expiring"), style = MaterialTheme.typography.titleSmall)
                            Text(
                                t("Every active plan is valid for more than %d days.", s.expiringDays),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 4.dp)) {
                    s.expiring.forEachIndexed { i, item ->
                        ExpiringRow(item, onClick = { openId = item.member.id }) {
                            val msg = if (item.dues.isNotEmpty()) {
                                WhatsAppHelper.buildReminderMessage(item.member.fullName, s.gymName, item.dues, lang)
                            } else {
                                WhatsAppHelper.buildExpiryMessage(item.member.fullName, s.gymName, item.membership.expiryDate, lang)
                            }
                            WhatsAppHelper.open(context, item.member.whatsappNumber, msg)
                        }
                        if (i < s.expiring.lastIndex) {
                            androidx.compose.material3.HorizontalDivider(
                                Modifier.padding(start = 72.dp, end = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                }
            }
        }
    }
    openId?.let { MemberCardDialog(it, onDismiss = { openId = null }, onEdit = onEditMember) }
}

@Composable
private fun HeroBanner(collected: Double) {
    Box(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(Color(0xFFF97316), Color(0xFFC2410C))))
    ) {
        Icon(
            Icons.Default.FitnessCenter, null,
            tint = Color.White.copy(alpha = 0.14f),
            modifier = Modifier.size(150.dp).align(Alignment.CenterEnd).offset(x = 28.dp)
        )
        Column(Modifier.padding(20.dp)) {
            Surface(shape = RoundedCornerShape(50), color = Color.White.copy(alpha = 0.2f)) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudOff, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(t("Local Offline"), style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(t("Welcome back, Coach"), style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Spacer(Modifier.height(2.dp))
            Text(DateUtils.formatDayFull(), style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.9f))
            Spacer(Modifier.height(10.dp))
            Text(
                t("Collected this month: %s", Money.format(collected)),
                style = MaterialTheme.typography.labelLarge, color = Color.White
            )
        }
    }
}

@Composable
private fun MetricCard(
    modifier: Modifier,
    icon: ImageVector,
    tint: Color,
    value: String,
    label: String,
    sub: String,
    onClick: () -> Unit
) {
    AppCard(modifier = modifier.height(140.dp), onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.13f)),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp)) }
            Spacer(Modifier.weight(1f))
            Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
        }
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.headlineSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun QuickAction(modifier: Modifier, icon: ImageVector, label: String, onClick: () -> Unit) {
    AppCard(modifier = modifier, onClick = onClick, contentPadding = PaddingValues(vertical = 14.dp, horizontal = 4.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp)) }
            Spacer(Modifier.height(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun ExpiringRow(item: ExpiringItem, onClick: () -> Unit, onRemind: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(item.member.photoUri, item.member.fullName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RollBadge(item.member.rollNumber)
                    Spacer(Modifier.width(8.dp))
                    Text(item.member.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.EventBusy, null, Modifier.size(14.dp), tint = StatusAmber)
                    Spacer(Modifier.width(4.dp))
                    Text(I18n.expiryText(currentLang(), item.membership.expiryDate), style = MaterialTheme.typography.labelMedium, color = StatusAmber)
                }
            }
            WhatsAppButton(onClick = onRemind, contentDescription = t("Send reminder to %s", item.member.fullName))
        }
    }
}
