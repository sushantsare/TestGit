@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import java.time.YearMonth

@Composable
fun HomeScreen(vm: AppVm) {
    val nav = LocalNav.current
    val bk = LocalBk.current
    var ym by rememberSaveable(stateSaver = androidx.compose.runtime.saveable.Saver<YearMonth, String>({ it.toString() }, { YearMonth.parse(it) })) { mutableStateOf(YearMonth.now()) }
    val entries by vm.entries.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val nets by vm.nets.collectAsStateWithLifecycle()
    val pending by vm.smsPending.collectAsStateWithLifecycle()
    val look by vm.smsLook.collectAsStateWithLifecycle()
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val cats by vm.catMap.collectAsStateWithLifecycle()
    val loaded by vm.loaded.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val wide = LocalWide.current

    val r = Dates.monthRange(ym)
    // totals are worked out once per data change, not on every redraw
    val spent = remember(entries, r) { Ledger.spent(entries, r) }; val got = remember(entries, r) { Ledger.received(entries, r) }
    val prev = Dates.monthRange(ym.minusMonths(1))
    val prevSpent = remember(entries, prev) { Ledger.spent(entries, prev) }
    val today = Dates.today()
    val dueCards = accounts.filter { it.type == AccType.CARD && !it.archived }.mapNotNull { c ->
        val b = Ledger.cardBill(c, nets[c.id] ?: 0, entries, today)
        if (b.bill > 0 && b.due != null) Triple(c, b, java.time.temporal.ChronoUnit.DAYS.between(today, b.due)) else null
    }.filter { it.third in -3..20 }.sortedBy { it.third }
    val recent = entries.filter { it.time in r || ym == YearMonth.now() }.take(8)
    val byCat = remember(entries, r) { Ledger.byCategory(entries, r) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            MainBar("Bahi Khata") {
                Act(if (s.hideAmounts) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility, if (s.hideAmounts) "Show amounts" else "Hide amounts", "hide") { vm.toggleHide() }
                Act(Icons.Outlined.Search, "Search", "search") { nav.go("search") }
                Act(Icons.Outlined.Settings, "Settings", "settings") { nav.go("settings") }
            }
        },
        floatingActionButton = {
            if (!LocalWide.current && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < 600)
                FloatingActionButton(onClick = { nav.go("add?type=EXPENSE") }, containerColor = bk.saffron, contentColor = bk.onSaffron, shape = RoundedCornerShape(18.dp), modifier = Modifier.testTag("fab_add")) {
                    Icon(Icons.Outlined.Add, "Add entry", Modifier.size(30.dp))
                }
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        if (loaded && entries.isEmpty() && accounts.isEmpty()) {
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                EmptyState(Icons.Outlined.AccountBalanceWallet, "Your khata is empty", "Add your accounts and first entry to see where your money goes.",
                    "Set up accounts", { nav.go("account_edit/0?type=BANK") }, "Add an entry", { nav.go("add?type=EXPENSE") })
            }
            return@Scaffold
        }
        val summary: @Composable () -> Unit = {
            Column {
                Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ym = ym.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
                    Text(Dates.month(ym), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("month"))
                    IconButton(onClick = { ym = ym.plusMonths(1) }, enabled = ym < YearMonth.now()) { Icon(Icons.Outlined.ChevronRight, "Next month") }
                }
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(18.dp), onClick = { nav.tab("reports") }) {
                    Text("Net this month", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(got - spent, style = MaterialTheme.typography.displaySmall, sign = true, modifier = Modifier.testTag("net"))
                    Spacer(Modifier.height(12.dp))
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text("↓ Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyText(got, style = MaterialTheme.typography.titleMedium, color = bk.income)
                        }
                        Column(Modifier.weight(1f)) {
                            Text("↑ Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            MoneyText(spent, style = MaterialTheme.typography.titleMedium, color = bk.expense)
                        }
                    }
                    if (prevSpent > 0 && ym == YearMonth.now()) {
                        Spacer(Modifier.height(8.dp))
                        val pct = ((spent - prevSpent) * 100 / prevSpent)
                        Text(if (pct <= 0) "${-pct}% less than last month so far" else if (pct > 200) "${hm(spent - prevSpent)} more than last month" else "$pct% more than last month",
                            style = MaterialTheme.typography.labelSmall, color = if (pct <= 0) bk.income else bk.expense)
                    }
                }
                if (pending.isNotEmpty() || look.isNotEmpty()) {
                    Row(
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer).clickable { nav.go("sms") }.padding(horizontal = 14.dp, vertical = 12.dp).testTag("sms_alert"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.Sms, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(10.dp))
                        val n = pending.size + look.size
                        Text("$n SMS ${if (n == 1) "entry needs" else "entries need"} your OK", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f))
                        Icon(Icons.Outlined.ChevronRight, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                if (dueCards.isNotEmpty()) {
                    SectionTitle("Card bills due", "All cards") { nav.tab("accounts") }
                    BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        dueCards.forEach { (c, b, d) ->
                            ListRow(c.name, "Bill ${if (LocalHide.current) "₹ ••••" else Money.fmt(b.bill)} · due ${Dates.dayMonth(b.due!!)}", Modifier.testTag("due_${c.id}"),
                                leading = { Box(Modifier.size(46.dp, 30.dp).clip(RoundedCornerShape(6.dp)).background(bk.cardFaces[c.colorIdx.mod(bk.cardFaces.size)]), contentAlignment = Alignment.BottomEnd) { Text(c.last4.split(',').firstOrNull { it.isNotBlank() }.orEmpty(), color = androidx.compose.ui.graphics.Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(end = 4.dp, bottom = 2.dp)) } },
                                trailing = { Text(when { d < 0 -> "overdue"; d == 0L -> "today"; d == 1L -> "tomorrow"; else -> "in $d days" }, style = MaterialTheme.typography.labelLarge, color = if (d <= 5) bk.expense else MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = { nav.go("account/${c.id}") })
                        }
                    }
                }
                // budgets follow the budget cycle (Settings → first day), which may differ from the calendar month shown above
                val cyc = Dates.cycleRange(Dates.today(), s.monthStartDay)
                val bSpent = Ledger.spent(entries, cyc); val bByCat = Ledger.byCategory(entries, cyc)
                val shownBudgets = budgets.sortedByDescending { b -> (if (b.categoryId == null) bSpent else bByCat[b.categoryId] ?: 0L).toDouble() / b.limitPaise.coerceAtLeast(1) }.take(3)
                if (shownBudgets.isNotEmpty() && ym == YearMonth.now()) {
                    SectionTitle("Budgets", "Manage") { nav.go("budgets") }
                    BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(14.dp)) {
                        shownBudgets.forEachIndexed { i, b ->
                            val used = if (b.categoryId == null) bSpent else bByCat[b.categoryId] ?: 0L
                            val f = used.toFloat() / b.limitPaise.coerceAtLeast(1)
                            if (i > 0) Spacer(Modifier.height(10.dp))
                            Row { Text(if (b.categoryId == null) "Overall" else cats[b.categoryId]?.name ?: "Budget", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                                Hideable { Text("${Money.fmt(used)} / ${Money.fmt(b.limitPaise)}", style = MaterialTheme.typography.labelLarge, color = if (f >= 1f) bk.expense else MaterialTheme.colorScheme.onSurface) } }
                            Spacer(Modifier.height(5.dp))
                            Bar(f, if (f >= 1f) bk.expense else if (f >= .8f) bk.saffron else MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
        val list: @Composable () -> Unit = {
            Column {
                SectionTitle("Recent", "See all") { nav.tab("entries") }
                if (recent.isEmpty()) {
                    BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(18.dp)) {
                        Text("No entries yet this month. Tap + to add one.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    recent.forEach { e -> EntryRow(e, vm, onClick = { nav.go("entry/${e.id}") }) }
                }
            }
        }
        if (wide) {
            Row(Modifier.padding(pad).fillMaxSize()) {
                Column(Modifier.weight(1f).verticalScrollSafe()) { summary(); Spacer(Modifier.height(24.dp)) }
                Column(Modifier.weight(1f).verticalScrollSafe()) { list(); Spacer(Modifier.height(24.dp)) }
            }
        } else {
            LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("home_list"), contentPadding = PaddingValues(bottom = 96.dp)) {
                item { summary() }
                item { list() }
            }
        }
    }
}

@Composable
fun Modifier.verticalScrollSafe(): Modifier = this.verticalScroll(rememberScrollState())
