package com.sushant.bahikhata.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import java.time.LocalDate

private fun payments(entries: List<Entry>, history: List<SmsItem>): List<Insights.Payment> =
    entries.filter { Ledger.isSpending(it) && !it.merchant.isNullOrBlank() }.map { Insights.Payment(it.merchant!!, it.amountPaise, it.time) } +
        history.filter { it.debit && it.merchant.isNotBlank() }.map { Insights.Payment(it.merchant, it.amountPaise, it.receivedAt) }

/** Reports → "More from your SMS": one row per insight page. */
@Composable
fun InsightsScreen(vm: AppVm) {
    val nav = LocalNav.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val history by vm.historySms.collectAsStateWithLifecycle()
    val reminders by vm.reminders.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val subs = remember(entries, history) { Insights.subscriptions(payments(entries, history)) }
    val salaryIds = cats.filter { it.kind == "INCOME" && it.name.equals("Salary", true) }.map { it.id }.toSet()
    val pay = remember(entries, salaryIds) { Insights.payday(entries, salaryIds, history.filter { "SALARY" in it.flags }.map { Insights.Payment(it.merchant, it.amountPaise, it.receivedAt) }) }
    val upcoming = reminders.filter { !it.done && Insights.daysLeft(it) >= 0 }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("From your SMS") }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("insights_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { BkCard(Modifier.padding(16.dp, 8.dp).fillMaxWidth()) {
                SettingNav(Icons.Outlined.Subscriptions, "Subscriptions", if (subs.isEmpty()) "Found by themselves from repeat payments" else "${subs.size} · ${hm(subs.sumOf { it.monthlyPaise })} a month") { nav.go("insights/subs") }
                SettingNav(Icons.Outlined.EventRepeat, "Bills, renewals & EMIs", if (upcoming.isEmpty()) "Recharge expiry, utility bills, EMI and AutoPay" else "${upcoming.size} coming up") { nav.go("reminders") }
                SettingNav(Icons.Outlined.Payments, "Since payday", pay?.let { "${it.daysSince} days · ${hm(it.spentSince)} spent" } ?: "Shows once a salary SMS arrives") { nav.go("insights/payday") }
                SettingNav(Icons.Outlined.AccountBalance, "Bank charges", "SMS charges, fees, GST, penalties") { nav.go("insights/charges") }
                SettingNav(Icons.Outlined.TrendingUp, "Investments & insurance", "SIP, mutual funds, FD, premiums — kept apart from spending") { nav.go("insights/invest") }
                SettingNav(Icons.Outlined.People, "People", "Who you pay most, and who pays you") { nav.go("insights/people") }
                SettingNav(Icons.Outlined.ShowChart, "Price history", "How the price of what you buy changes, shop by shop") { nav.go("insights/prices") }
                SettingNav(Icons.Outlined.CalendarMonth, "Year summary", "Income, spending, interest, charges and biggest spends") { nav.go("insights/year") }
            } }
            item { Text("Worked out on this phone from your entries and bank SMS. Nothing leaves the phone.", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 8.dp)) }
        }
    }
}

@Composable
fun InsightPage(vm: AppVm, page: String) {
    val nav = LocalNav.current
    val bk = LocalBk.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val history by vm.historySms.collectAsStateWithLifecycle()
    val cats by vm.categories.collectAsStateWithLifecycle()
    val catMap by vm.catMap.collectAsStateWithLifecycle()
    val accs by vm.accMap.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val udhar by vm.udhar.collectAsStateWithLifecycle()
    val names by vm.upiNames.collectAsStateWithLifecycle()
    val items by vm.items.collectAsStateWithLifecycle()
    val today = Dates.today()
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    fun catIds(kind: String, name: String) = cats.filter { it.kind == kind && it.name.equals(name, true) }.map { it.id }.toSet()
    val title = when (page) {
        "subs" -> "Subscriptions"; "payday" -> "Since payday"; "charges" -> "Bank charges"; "invest" -> "Investments & insurance"
        "people" -> "People"; "prices" -> "Price history"; else -> "Year summary"
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar(title) }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("insight_$page"), contentPadding = PaddingValues(bottom = 32.dp)) {
            when (page) {
                "subs" -> {
                    val subs = Insights.subscriptions(payments(entries, history), today)
                    if (subs.isEmpty()) item { Empty(Icons.Outlined.Subscriptions, "No subscriptions found yet", "Payments that repeat every month (Netflix, Jio, gym…) show up here once they've been paid twice.") }
                    else {
                        item { Total("Every month", subs.sumOf { it.monthlyPaise }, "${subs.size} subscriptions · ${hm(subs.sumOf { it.monthlyPaise } * 12)} a year") }
                        item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            subs.forEach { s ->
                                ListRow(s.name, "${s.cadence} · last ${Dates.dayMonth(s.last)} · next ~${Dates.dayMonth(s.next)}", Modifier.testTag("sub_${s.name}"),
                                    leading = { Initials(s.name) }, trailing = { MoneyText(s.amountPaise) })
                            }
                        } }
                    }
                }
                "payday" -> {
                    val salaryIds = catIds("INCOME", "Salary")
                    val p = Insights.payday(entries, salaryIds, history.filter { "SALARY" in it.flags }.map { Insights.Payment(it.merchant, it.amountPaise, it.receivedAt) }, today)
                    if (p == null) item { Empty(Icons.Outlined.Payments, "No salary seen yet", "When a salary SMS is saved (category Salary), this shows what you've spent since payday and how many days are left.") }
                    else {
                        item { Total("Spent since payday", p.spentSince, "Salary ${hm(p.amountPaise)} on ${Dates.dayMonth(p.last)} · ${p.daysSince} days ago") }
                        item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            ListRow("Left from salary", if (p.amountPaise > 0) "${(p.spentSince * 100 / p.amountPaise).coerceAtMost(999)}% spent" else null, trailing = {
                                MoneyText(p.amountPaise + p.receivedSince - p.spentSince, color = if (p.amountPaise + p.receivedSince >= p.spentSince) bk.income else bk.expense) })
                            if (p.receivedSince > 0) ListRow("Other money in since then", null, trailing = { MoneyText(p.receivedSince, color = bk.income) })
                            p.nextGuess?.let { n ->
                                val d = java.time.temporal.ChronoUnit.DAYS.between(today, n)
                                ListRow("Next salary (likely)", "${Dates.dayMonth(n)} · ${if (d > 0) "in $d days" else "any day now"}",
                                    trailing = { if (d > 0) Text("${hm(((p.amountPaise + p.receivedSince - p.spentSince).coerceAtLeast(0)) / d)}/day", style = MaterialTheme.typography.labelLarge) })
                            }
                        } }
                    }
                }
                "charges" -> {
                    val ids = catIds("EXPENSE", "Bank charges")
                    val r = Dates.startOf(LocalDate.of(year, 1, 1))..Dates.endOf(LocalDate.of(year, 12, 31))
                    val list = entries.filter { Ledger.isSpending(it) && it.categoryId in ids && it.time in r }
                    item { YearPicker(year) { year = it } }
                    if (list.isEmpty()) item { Empty(Icons.Outlined.AccountBalance, "No bank charges in $year", "SMS charges, card fees, GST and penalties are collected here as they're saved.") }
                    else {
                        item { Total("Charges in $year", list.sumOf { it.amountPaise }, "${list.size} charges") }
                        item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            list.groupBy { it.accountId }.forEach { (a, l) -> ListRow(accs[a]?.name ?: "Account", "${l.size} charges", trailing = { MoneyText(l.sumOf { it.amountPaise }, color = bk.expense) }) }
                        } }
                        item { SectionLabel("Each charge") }
                        item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { list.forEach { e -> EntryRow(e, vm, onClick = { nav.go("entry/${e.id}") }) } } }
                    }
                }
                "invest" -> {
                    val ins = catIds("EXPENSE", "Insurance")
                    val r = Dates.startOf(LocalDate.of(year, 1, 1))..Dates.endOf(LocalDate.of(year, 12, 31))
                    val inv = entries.filter { it.type == EType.INVEST && !it.cancelled && it.time in r }
                    val prem = entries.filter { Ledger.isSpending(it) && it.categoryId in ins && it.time in r }
                    item { YearPicker(year) { year = it } }
                    if (inv.isEmpty() && prem.isEmpty()) item { Empty(Icons.Outlined.TrendingUp, "Nothing here for $year", "SIP and mutual fund debits are saved as investments (not spending); insurance premiums show here too.") }
                    else {
                        item { Total("Invested in $year", inv.sumOf { it.amountPaise }, "Insurance premiums ${hm(prem.sumOf { it.amountPaise })}") }
                        if (inv.isNotEmpty()) { item { SectionLabel("Investments (not counted as spending)") }
                            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                                inv.groupBy { it.merchant?.trim().orEmpty().ifBlank { "Investment" } }.forEach { (m, l) -> ListRow(m, "${l.size} payments", trailing = { MoneyText(l.sumOf { it.amountPaise }) }) }
                            } } }
                        if (prem.isNotEmpty()) { item { SectionLabel("Insurance") }
                            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { prem.forEach { e -> EntryRow(e, vm, onClick = { nav.go("entry/${e.id}") }) } } } }
                    }
                }
                "people" -> {
                    val r = Dates.startOf(LocalDate.of(year, 1, 1))..Dates.endOf(LocalDate.of(year, 12, 31))
                    val list = Insights.people(entries, names, people, udhar, r)
                    item { YearPicker(year) { year = it } }
                    if (list.isEmpty()) item { Empty(Icons.Outlined.People, "No people yet", "Name a UPI ID as a Person, or add udhar — money to and from them adds up here.") }
                    else item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        list.forEach { p ->
                            ListRow(p.name, listOfNotNull("paid ${hm(p.paidPaise)}", "got ${hm(p.gotPaise)}", p.udharPaise?.takeIf { it != 0L }?.let { if (it > 0) "owes you ${hm(it)}" else "you owe ${hm(-it)}" }).joinToString(" · "),
                                Modifier.testTag("person_sum_${p.name}"), leading = { Initials(p.name) }, trailing = { MoneyText(p.paidPaise + p.gotPaise) },
                                onClick = p.personId?.let { id -> { nav.go("person/$id") } })
                        }
                    } }
                }
                "prices" -> {
                    val shopNames = entries.mapNotNull { e -> e.merchant?.let { Insights.shopKey(it) to it.trim() } }.toMap()
                    val lines = Insights.priceHistory(items, shopNames)
                    if (lines.isEmpty()) item { Empty(Icons.Outlined.ShowChart, "No price history yet", "When you answer \"What did you buy?\" with prices (milk 60), buying the same thing again shows how its price moved.") }
                    else item { BkCard(Modifier.padding(16.dp, 8.dp).fillMaxWidth()) {
                        lines.forEach { l ->
                            val up = l.change > 0
                            ListRow("${l.item} · ${l.shop}", "${hm(l.first)} → ${hm(l.last)}${if (l.per == "each") "" else " " + l.per} since ${Dates.dayMonth(l.points.first().date)} · ${l.points.size} times", Modifier.testTag("price_${l.item}"),
                                leading = { Icon(if (up) Icons.Outlined.TrendingUp else if (l.change < 0) Icons.Outlined.TrendingDown else Icons.Outlined.TrendingFlat, null,
                                    tint = if (up) bk.expense else if (l.change < 0) bk.income else MaterialTheme.colorScheme.onSurfaceVariant) },
                                trailing = { Text(if (l.change == 0L) "same" else (if (up) "+" else "−") + hm(kotlin.math.abs(l.change)).removePrefix("−"), style = MaterialTheme.typography.labelLarge,
                                    color = if (up) bk.expense else if (l.change < 0) bk.income else MaterialTheme.colorScheme.onSurfaceVariant) })
                        }
                    } }
                }
                else -> {
                    val y = Insights.year(entries, year) { k, n -> catIds(k, n) }
                    item { YearPicker(year) { year = it } }
                    item { Total("Saved in $year", y.received - y.spent, "Received ${hm(y.received)} · spent ${hm(y.spent)}") }
                    item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        listOf("Invested" to y.invested, "Interest earned" to y.interest, "Cashback" to y.cashback, "Refunds" to y.refunds,
                            "Bank charges paid" to y.charges, "Insurance premiums" to y.insurance).filter { it.second > 0 }.forEach { (l, v) ->
                            ListRow(l, null, trailing = { MoneyText(v, color = if (l == "Bank charges paid") bk.expense else MaterialTheme.colorScheme.onSurface) })
                        }
                        if (listOf(y.invested, y.interest, y.cashback, y.refunds, y.charges, y.insurance).all { it == 0L }) ListRow("No interest, cashback or charges saved this year", null)
                    } }
                    if (y.topCategories.isNotEmpty()) { item { SectionLabel("Where it went") }
                        item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { y.topCategories.forEach { (c, v) -> ListRow(c?.let { catMap[it]?.name } ?: "No category", null, trailing = { MoneyText(v) }) } } } }
                    if (y.topSpends.isNotEmpty()) { item { SectionLabel("Biggest spends") }
                        item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { y.topSpends.forEach { e -> EntryRow(e, vm, onClick = { nav.go("entry/${e.id}") }) } } } }
                    item { SectionLabel("Month by month") }
                    item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        val max = y.byMonth.maxOf { it.second }.coerceAtLeast(1)
                        y.byMonth.filter { it.second > 0 }.forEach { (ym, v) ->
                            Row(Modifier.padding(14.dp, 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(Dates.mon(ym), Modifier.width(44.dp), style = MaterialTheme.typography.labelLarge)
                                Box(Modifier.weight(1f)) { Hideable { Bar(v.toFloat() / max, MaterialTheme.colorScheme.primary, Modifier.fillMaxWidth()) } }
                                Spacer(Modifier.width(8.dp)); MoneyText(v, style = MaterialTheme.typography.labelLarge, compact = true)
                            }
                        }
                    } }
                }
            }
        }
    }
}

/** Settings / Insights → Bills, renewals & EMIs read from SMS. */
@Composable
fun RemindersScreen(vm: AppVm) {
    val snack = LocalSnack.current
    val bk = LocalBk.current
    val list by vm.reminders.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val (notifOk, askNotif) = rememberNotifState()
    val open = list.filter { !it.done }.sortedBy { it.dueEpochDay }
    val done = list.filter { it.done }.sortedByDescending { it.dueEpochDay }.take(20)
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Bills, renewals & EMIs") }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("reminders_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { BkCard(Modifier.padding(16.dp, 8.dp).fillMaxWidth()) {
                SettingSwitch(Icons.Outlined.NotificationsActive, "Remind me", "A day before a plan ends or an EMI / AutoPay is debited; 2 days before a bill is due", s.remindersOn, { on ->
                    vm.prefs.update { it.copy(remindersOn = on) }; if (on && !notifOk) askNotif()
                }, "remind_on")
            } }
            if (open.isEmpty()) item { Empty(Icons.Outlined.EventRepeat, "Nothing coming up", "When an SMS says your plan expires, a bill is due or an EMI will be debited, it shows here and you get a reminder.") }
            else item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                open.forEach { r ->
                    val d = Insights.daysLeft(r)
                    ListRow(r.title, listOfNotNull(Dates.dayMonth(LocalDate.ofEpochDay(r.dueEpochDay)), r.amountPaise.takeIf { it > 0 }?.let { hm(it) },
                        when (r.kind) { RemindKind.RECHARGE -> "plan ends"; RemindKind.UTILITY -> "bill due"; RemindKind.EMI -> "EMI"; else -> "AutoPay" }).joinToString(" · "),
                        Modifier.testTag("rem_${r.id}"), leading = { Icon(when (r.kind) { RemindKind.RECHARGE -> Icons.Outlined.PhoneAndroid; RemindKind.UTILITY -> Icons.Outlined.Bolt; else -> Icons.Outlined.EventRepeat }, null) },
                        trailing = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(when { d < 0 -> "passed"; d == 0L -> "today"; d == 1L -> "tomorrow"; else -> "in $d days" }, style = MaterialTheme.typography.labelLarge,
                                color = if (d <= 2) bk.expense else MaterialTheme.colorScheme.onSurfaceVariant)
                            IconButton(onClick = { vm.launch { vm.repo.setReminderDone(r, true) }; snack.show("Marked done", "Undo") { vm.launch { vm.repo.setReminderDone(r, false) } } },
                                modifier = Modifier.testTag("rem_done_${r.id}")) { Icon(Icons.Outlined.Check, "Mark done") }
                        } })
                }
            } }
            if (done.isNotEmpty()) {
                item { SectionLabel("Done") }
                item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    done.forEach { r -> ListRow(r.title, Dates.dayMonth(LocalDate.ofEpochDay(r.dueEpochDay)), leading = { Icon(Icons.Outlined.CheckCircle, null, tint = bk.income) },
                        trailing = { TextButton(onClick = { vm.launch { vm.repo.deleteReminder(r) } }) { Text("Remove") } }) }
                } }
            }
        }
    }
}

@Composable
private fun Empty(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) =
    Box(Modifier.fillMaxWidth().padding(top = 48.dp), contentAlignment = Alignment.Center) { EmptyState(icon, title, body) }

@Composable
private fun Total(label: String, v: Long, sub: String) {
    BkCard(Modifier.padding(16.dp, 8.dp).fillMaxWidth(), padding = PaddingValues(18.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        MoneyText(v, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("insight_total"))
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun YearPicker(year: Int, onPick: (Int) -> Unit) {
    val now = Dates.today().year
    Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onPick(year - 1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous year") }
        Text("$year", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        IconButton(onClick = { onPick(year + 1) }, enabled = year < now) { Icon(Icons.Outlined.ChevronRight, "Next year") }
    }
}
