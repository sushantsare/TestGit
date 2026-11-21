@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth

private val ymS = androidx.compose.runtime.saveable.Saver<YearMonth, String>({ it.toString() }, { YearMonth.parse(it) })

@Composable
fun ReportsScreen(vm: AppVm) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    val snack = LocalSnack.current
    val scope = rememberCoroutineScope()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val cats by vm.catMap.collectAsStateWithLifecycle()
    val accs by vm.accMap.collectAsStateWithLifecycle()
    var period by rememberSaveable { mutableStateOf("MONTH") }
    var ym by rememberSaveable(stateSaver = ymS) { mutableStateOf(YearMonth.now()) }
    val r: LongRange = when (period) {
        "WEEK" -> { val t = Dates.today(); Dates.startOf(t.minusDays(6))..Dates.endOf(t) }
        "YEAR" -> Dates.startOf(LocalDate.of(ym.year, 1, 1))..Dates.endOf(LocalDate.of(ym.year, 12, 31))
        else -> Dates.monthRange(ym)
    }
    val spent = Ledger.spent(entries, r); val got = Ledger.received(entries, r)
    val byCat = Ledger.byCategory(entries, r).toList().sortedByDescending { it.second }
    val colors = listOf(bk.saffron, MaterialTheme.colorScheme.primary, bk.income, bk.expense, bk.cat[4].second, bk.cat[2].second, bk.cat[7].second, MaterialTheme.colorScheme.outline)
    val parts = byCat.take(7).mapIndexed { i, (_, v) -> v to colors[i] } + (byCat.drop(7).sumOf { it.second }.takeIf { it > 0 }?.let { listOf(it to colors[7]) } ?: emptyList())
    val months = (5 downTo 0).map { ym.minusMonths(it.toLong()) }
    val bars = months.map { m -> val mr = Dates.monthRange(m); Ledger.received(entries, mr) to Ledger.spent(entries, mr) }

    fun pdf() = scope.launch {
        val bytes = withContext(Dispatchers.IO) {
            Export.pdf(ctx, if (period == "MONTH") Export.monthTitle(ym) else if (period == "YEAR") "Report for ${ym.year}" else "Last 7 days",
                Export.rows(entries, accs, cats, r), entries, r, accs, cats)
        }
        val f = File(ctx.cacheDir, "share").apply { mkdirs() }.let { File(it, "BahiKhata-${if (period == "MONTH") ym.toString() else period.lowercase()}.pdf") }
        f.writeBytes(bytes); shareFile(ctx, f, "application/pdf", "Share report")
        Diagnostics.count(ctx, "pdf_report")
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { MainBar("Reports") {
        Act(Icons.Outlined.PictureAsPdf, "Monthly report PDF", "pdf") { pdf() }
        Act(Icons.Outlined.FileDownload, "Export", "export") { nav.go("export") }
    } }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("reports"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("WEEK" to "Week", "MONTH" to "Month", "YEAR" to "Year").forEach { (k, l) -> Pill(l, period == k, { period = k }, modifier = Modifier.testTag("period_$k")) }
                }
                if (period != "WEEK") Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { ym = if (period == "YEAR") ym.minusYears(1) else ym.minusMonths(1) }) { Icon(Icons.Outlined.ChevronLeft, "Previous") }
                    Text(if (period == "YEAR") ym.year.toString() else Dates.month(ym), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                    IconButton(onClick = { ym = if (period == "YEAR") ym.plusYears(1) else ym.plusMonths(1) }, enabled = (if (period == "YEAR") ym.year < YearMonth.now().year else ym < YearMonth.now())) { Icon(Icons.Outlined.ChevronRight, "Next") }
                }
            }
            item {
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(14.dp)) {
                    if (spent == 0L) Text("No spending in this period yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(8.dp))
                    else Row(verticalAlignment = Alignment.CenterVertically) {
                        Donut(parts, Modifier.size(150.dp).testTag("donut")) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) { MoneyText(spent, style = MaterialTheme.typography.titleSmall, compact = true); Text("spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            byCat.take(7).forEachIndexed { i, (cid, v) -> Legend(colors[i], cid?.let { cats[it]?.name } ?: "Uncategorised", v) }
                            if (byCat.size > 7) Legend(colors[7], "Others", byCat.drop(7).sumOf { it.second })
                        }
                    }
                }
            }
            item {
                Row(Modifier.padding(16.dp, 10.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BkCard(Modifier.weight(1f), padding = PaddingValues(12.dp)) { Text("Received", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(got, color = bk.income) }
                    BkCard(Modifier.weight(1f), padding = PaddingValues(12.dp)) { Text("Spent", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(spent, color = bk.expense) }
                    BkCard(Modifier.weight(1f), padding = PaddingValues(12.dp)) { Text("Saved", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(got - spent) }
                }
            }
            item { SectionLabel("Income vs spending · 6 months") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(14.dp)) {
                Hideable { Bars(bars, months.map { Dates.mon(it) }) }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { Legend(bk.income, "Received", null); Legend(bk.expense, "Spent", null) }
            } }
            val byAcc = Ledger.byAccount(entries, r).toList().sortedByDescending { it.second }
            if (byAcc.isNotEmpty()) {
                item { SectionLabel("By card / account") }
                item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    byAcc.forEach { (aid, v) -> ListRow(accs[aid]?.name ?: "—", "${if (spent > 0) v * 100 / spent else 0}% of spending", leading = { Icon(Ico.account(accs[aid]?.type ?: AccType.BANK), null) }, trailing = { MoneyText(v) }, onClick = { nav.go("account/$aid") }) }
                } }
            }
            val merchants = Ledger.byMerchant(entries, r).take(5)
            if (merchants.isNotEmpty()) {
                item { SectionLabel("Top shops") }
                item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { merchants.forEach { (m, v) -> ListRow(m, null, trailing = { MoneyText(v) }) } } }
            }
            val tags = Ledger.byTag(entries, r).take(6)
            if (tags.isNotEmpty()) {
                item { SectionLabel("By tag") }
                item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { tags.forEach { (t, v) -> ListRow("#$t", null, trailing = { MoneyText(v) }) } } }
            }
            item { SectionLabel("Plan") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                SettingNav(Icons.Outlined.Savings, "Budgets & savings goals", "Limits per category, alerts at 80% and 100%") { nav.go("budgets") }
                SettingNav(Icons.Outlined.PictureAsPdf, "Share this report as PDF") { pdf() }
                SettingNav(Icons.Outlined.TableView, "Export CSV / Excel / PDF", "Any dates") { nav.go("export") }
            } }
        }
    }
    @Suppress("UNUSED_VARIABLE") val u = snack
}

@Composable
private fun Legend(c: androidx.compose.ui.graphics.Color, name: String, v: Long?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(c)); Spacer(Modifier.width(8.dp))
        Text(name, style = MaterialTheme.typography.labelLarge, modifier = if (v != null) Modifier.weight(1f) else Modifier, maxLines = 1)
        if (v != null) MoneyText(v, style = MaterialTheme.typography.labelLarge)
    }
}

@Composable
fun BudgetsScreen(vm: AppVm) {
    val snack = LocalSnack.current
    val bk = LocalBk.current
    val budgets by vm.budgets.collectAsStateWithLifecycle()
    val goals by vm.goals.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var editBudget by remember { mutableStateOf<Budget?>(null) }
    var pickCat by remember { mutableStateOf(false) }
    var editGoal by remember { mutableStateOf<Goal?>(null) }
    var addMoney by remember { mutableStateOf<Goal?>(null) }
    val r = Dates.monthRange(YearMonth.now())
    val byCat = Ledger.byCategory(entries, r)
    val spent = Ledger.spent(entries, r)
    val daysLeft = YearMonth.now().lengthOfMonth() - Dates.today().dayOfMonth

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Budgets & goals") }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("budgets"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { SectionLabel("${Dates.month(YearMonth.now())} budgets", action = "Add", onAction = { pickCat = true }) }
            if (budgets.isEmpty()) item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Text("No budgets yet. Set a monthly limit for everything or for one category — you'll get a notification at 80% and 100%.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp)); Button(onClick = { pickCat = true }, modifier = Modifier.testTag("add_budget")) { Text("Add budget") }
            } }
            else item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                budgets.sortedBy { it.categoryId ?: -1 }.forEach { b ->
                    val used = if (b.categoryId == null) spent else byCat[b.categoryId] ?: 0L
                    val f = used.toFloat() / b.limitPaise.coerceAtLeast(1)
                    Column(Modifier.fillMaxWidth().clickable { editBudget = b }.padding(14.dp).testTag("budget_${b.id}")) {
                        Row { Text(if (b.categoryId == null) "Overall" else categories.firstOrNull { it.id == b.categoryId }?.name ?: "Category", style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                            Hideable { Text("${Money.fmt(used)} / ${Money.fmt(b.limitPaise)}", style = MaterialTheme.typography.labelLarge, color = if (f >= 1f) bk.expense else MaterialTheme.colorScheme.onSurface) } }
                        Spacer(Modifier.height(6.dp))
                        Bar(f, if (f >= 1f) bk.expense else if (f >= .8f) bk.saffron else MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(if (f >= 1f) "Over by ${Money.fmt(used - b.limitPaise)}" else "${Money.fmt(b.limitPaise - used)} left · $daysLeft days to go", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } }
            item { SectionLabel("Savings goals", action = "Add", onAction = { editGoal = Goal(name = "", targetPaise = 0) }) }
            if (goals.isEmpty()) item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(16.dp)) {
                Text("Save up for something — a laptop, a trip, an emergency fund.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp)); Button(onClick = { editGoal = Goal(name = "", targetPaise = 0) }, modifier = Modifier.testTag("add_goal")) { Text("Add goal") }
            } }
            else item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                goals.forEach { g ->
                    val f = g.savedPaise.toFloat() / g.targetPaise.coerceAtLeast(1)
                    ListRow(g.name, "${if (LocalHide.current) "₹ ••••" else Money.fmt(g.savedPaise)} of ${Money.fmt(g.targetPaise)}" + (if (g.targetEpochDay > 0) " · by " + Dates.dayMonthYear(LocalDate.ofEpochDay(g.targetEpochDay)) else ""),
                        Modifier.testTag("goal_${g.name}"), leading = { Ring(f, bk.income) },
                        trailing = { Row(verticalAlignment = Alignment.CenterVertically) { Text("${(f * 100).toInt().coerceAtMost(999)}%", style = MaterialTheme.typography.labelLarge)
                            IconButton(onClick = { addMoney = g }) { Icon(Icons.Outlined.AddCircleOutline, "Add money to ${g.name}") } } },
                        onClick = { editGoal = g })
                }
            } }
        }
    }
    if (pickCat) PickSheet("Budget for", listOf<Category?>(null) + categories.filter { it.kind == "EXPENSE" && !it.archived && budgets.none { b -> b.categoryId == it.id } },
        { it?.name ?: "Overall (all spending)" }, onPick = { c -> if (c == null && budgets.any { it.categoryId == null }) editBudget = budgets.first { it.categoryId == null } else editBudget = Budget(categoryId = c?.id, limitPaise = 0) },
        onDismiss = { pickCat = false })
    editBudget?.let { b ->
        var t by remember(b) { mutableStateOf(if (b.limitPaise > 0) Money.input(b.limitPaise) else "") }
        AlertDialog(onDismissRequest = { editBudget = null }, title = { Text("Monthly limit · " + (if (b.categoryId == null) "Overall" else categories.firstOrNull { it.id == b.categoryId }?.name ?: "")) },
            text = { MoneyField(t, { t = it }, "Limit", tag = "blimit") },
            confirmButton = { TextButton(onClick = { Money.parse(t)?.takeIf { it > 0 }?.let { v -> vm.launch { vm.repo.saveBudget(b.copy(limitPaise = v)) } }; editBudget = null }, modifier = Modifier.testTag("budget_ok")) { Text("Save") } },
            dismissButton = { Row { if (b.id > 0) TextButton(onClick = { vm.launch { vm.repo.deleteBudget(b) }; snack.show("Budget removed"); editBudget = null }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                TextButton(onClick = { editBudget = null }) { Text("Cancel") } } })
    }
    editGoal?.let { g ->
        var name by remember(g) { mutableStateOf(g.name) }
        var target by remember(g) { mutableStateOf(if (g.targetPaise > 0) Money.input(g.targetPaise) else "") }
        var months by remember(g) { mutableStateOf("") }
        AlertDialog(onDismissRequest = { editGoal = null }, title = { Text(if (g.id == 0L) "New goal" else "Edit goal") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                TextIn(name, { name = it }, "Goal (e.g. New laptop)", tag = "gname")
                MoneyField(target, { target = it }, "Target amount", tag = "gtarget")
                TextIn(months, { v -> months = v.filter { it.isDigit() }.take(3) }, if (g.targetEpochDay > 0) "Months to reach (now ${Dates.dayMonthYear(LocalDate.ofEpochDay(g.targetEpochDay))})" else "Months to reach (optional)", tag = "gmonths", kb = KeyboardType.Number)
            }
        }, confirmButton = { TextButton(onClick = {
            val tp = Money.parse(target) ?: 0L
            if (name.isNotBlank() && tp > 0) vm.launch { vm.repo.saveGoal(g.copy(name = name.trim(), targetPaise = tp,
                targetEpochDay = months.toLongOrNull()?.let { Dates.today().plusMonths(it).toEpochDay() } ?: g.targetEpochDay)) }
            editGoal = null
        }, modifier = Modifier.testTag("goal_ok")) { Text("Save") } }, dismissButton = { Row {
            if (g.id > 0) TextButton(onClick = { vm.launch { vm.repo.deleteGoal(g) }; snack.show("Goal deleted", "Undo") { vm.launch { vm.repo.saveGoal(g) } }; editGoal = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { editGoal = null }) { Text("Cancel") } } })
    }
    addMoney?.let { g ->
        TextDialog("Add to ${g.name}", "", { t -> Money.parse(t)?.takeIf { it > 0 }?.let { v -> vm.launch { vm.repo.saveGoal(g.copy(savedPaise = g.savedPaise + v)); snack.show("Added ${Money.fmt(v)} to ${g.name}") } } },
            { addMoney = null }, hint = "Money you set aside for this goal", kb = KeyboardType.Decimal)
    }
}
