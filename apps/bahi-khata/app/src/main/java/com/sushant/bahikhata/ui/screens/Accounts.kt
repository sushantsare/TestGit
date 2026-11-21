@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.selectable
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk

fun typeLabel(t: String) = when (t) { AccType.CARD -> "Credit card"; AccType.CASH -> "Cash"; AccType.WALLET -> "Wallet"; else -> "Bank account" }

@Composable
fun AccountsScreen(vm: AppVm) {
    val nav = LocalNav.current
    val bk = LocalBk.current
    val wide = LocalWide.current
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val nets by vm.nets.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableLongStateOf(0L) }
    var addPick by rememberSaveable { mutableStateOf(false) }
    val active = accounts.filter { !it.archived }
    val have = active.filter { it.type != AccType.CARD }.sumOf { nets[it.id] ?: 0L }
    val owe = active.filter { it.type == AccType.CARD }.sumOf { -(nets[it.id] ?: 0L) }.coerceAtLeast(0L)
    val today = Dates.today()
    val best = active.filter { it.type == AccType.CARD && it.statementDay > 0 }.map { it to CardMath.interestFreeDays(today, it.statementDay, it.dueDay) }.maxByOrNull { it.second }
    val open: (Long) -> Unit = { id -> if (wide) selected = id else nav.go("account/$id") }

    val list: @Composable (Modifier) -> Unit = { mod ->
        LazyColumn(mod.testTag("accounts_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item {
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(18.dp)) {
                    Text("Net worth", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    MoneyText(have - owe, style = MaterialTheme.typography.headlineMedium)
                    Hideable { Text("You have ${Money.fmt(have)} · cards owe ${Money.fmt(owe)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            if (best != null && best.second > 0) item {
                Row(Modifier.padding(16.dp, 10.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bk.info).padding(12.dp).testTag("best_card"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Lightbulb, null, tint = bk.onInfo); Spacer(Modifier.width(10.dp))
                    Text("Best card today: ${best.first.name} — up to ${best.second} interest-free days", color = bk.onInfo, style = MaterialTheme.typography.labelLarge)
                }
            }
            listOf(AccType.BANK to "Bank accounts", AccType.CARD to "Credit cards", AccType.CASH to "Cash", AccType.WALLET to "Wallets").forEach { (t, label) ->
                val group = active.filter { it.type == t }
                if (group.isNotEmpty()) {
                    item { SectionLabel(label) }
                    item {
                        BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                            group.forEach { a -> AccountRow(a, nets[a.id] ?: 0L, entries) { open(a.id) } }
                        }
                    }
                }
            }
            val archived = accounts.filter { it.archived }
            if (archived.isNotEmpty()) {
                item { SectionLabel("Archived") }
                item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { archived.forEach { a -> AccountRow(a, nets[a.id] ?: 0L, entries) { open(a.id) } } } }
            }
            if (accounts.isEmpty()) item {
                EmptyState(Icons.Outlined.AccountBalanceWallet, "No accounts yet", "Add your bank accounts, credit cards, cash and wallets with today's balance.", "Add account", { addPick = true })
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        MainBar("Accounts") { Act(Icons.Outlined.Add, "Add account", "add_account") { addPick = true } }
    }, contentWindowInsets = WindowInsets(0)) { pad ->
        if (wide) Row(Modifier.padding(pad).fillMaxSize()) {
            list(Modifier.weight(0.45f))
            VerticalDivider(color = bk.cardBorder)
            Box(Modifier.weight(0.55f)) { if (selected > 0) AccountDetailContent(vm, selected, embedded = true) else EmptyState(Icons.Outlined.TouchApp, "Pick an account", "Balance, card bill and its entries show here.") }
        } else list(Modifier.padding(pad).fillMaxSize())
    }
    if (addPick) PickSheet("Add", listOf(AccType.BANK, AccType.CARD, AccType.CASH, AccType.WALLET), { typeLabel(it) }, icon = { Icon(Ico.account(it), null) },
        onPick = { nav.go("account_edit/0?type=$it") }, onDismiss = { addPick = false })
}

@Composable
private fun AccountRow(a: Account, net: Long, entries: List<Entry>, onClick: () -> Unit) {
    val bk = LocalBk.current
    if (a.type == AccType.CARD) {
        val b = Ledger.cardBill(a, net, entries)
        val due = b.due
        val days = due?.let { java.time.temporal.ChronoUnit.DAYS.between(Dates.today(), it) }
        ListRow(a.name, if (b.bill > 0 && due != null) "Due ${Dates.dayMonth(due)} · in $days days" else if (a.statementDay > 0) "Bill on ${a.statementDay} of every month" else "Credit card",
            Modifier.testTag("acc_row_${a.name}"),
            leading = { IconBadge(Ico.account(a.type), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface) },
            trailing = {
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.widthIn(max = 140.dp)) {
                    MoneyText(b.outstanding, color = if (b.outstanding > 0) bk.expense else MaterialTheme.colorScheme.onSurface)
                    if (a.limitPaise > 0) { Spacer(Modifier.height(4.dp)); Bar(b.outstanding.toFloat() / a.limitPaise, bk.expense, Modifier.width(110.dp), 5.dp) }
                }
            }, onClick = onClick)
    } else {
        ListRow(a.name, listOfNotNull(typeLabel(a.type), a.last4.split(',').firstOrNull { it.isNotBlank() }?.let { "•• $it" }, a.upiApps.takeIf { it.isNotBlank() }?.replace(",", ", ")).joinToString(" · "),
            Modifier.testTag("acc_row_${a.name}"),
            leading = { IconBadge(Ico.account(a.type), MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurface) },
            trailing = { MoneyText(net, color = if (net < 0) bk.expense else MaterialTheme.colorScheme.onSurface) }, onClick = onClick)
    }
}

@Composable
fun AccountDetailScreen(vm: AppVm, id: Long) = AccountDetailContent(vm, id, embedded = false)

@Composable
fun AccountDetailContent(vm: AppVm, id: Long, embedded: Boolean) {
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val bk = LocalBk.current
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val nets by vm.nets.collectAsStateWithLifecycle()
    val a = accounts.firstOrNull { it.id == id }
    var adjust by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var editMin by rememberSaveable { mutableStateOf(false) }
    var limit by rememberSaveable { mutableIntStateOf(50) }
    if (a == null) { MissingRecord(vm.loaded.collectAsStateWithLifecycle().value, "account"); return }
    val net = nets[a.id] ?: 0L
    val mine = entries.filter { it.accountId == a.id || it.toAccountId == a.id }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        val actions: @Composable RowScope.() -> Unit = {
            Act(Icons.Outlined.Edit, "Edit account", "edit_account") { nav.go("account_edit/${a.id}?type=${a.type}") }
            Act(if (a.archived) Icons.Outlined.Unarchive else Icons.Outlined.Archive, if (a.archived) "Unarchive" else "Archive", "archive") {
                vm.launch { vm.repo.archiveAccount(a); snack.show(if (a.archived) "Account is active again" else "Account archived — it stays in reports") }
            }
            Act(Icons.Outlined.Delete, "Delete account", "delete_account") { confirmDelete = true }
        }
        if (embedded) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End, content = actions) else BackBar(a.name, actions = actions)
    }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("account_detail"), contentPadding = PaddingValues(bottom = 110.dp)) {
            if (a.type == AccType.CARD) {
                val b = Ledger.cardBill(a, net, entries)
                item { Hideable { CardFace(a, Modifier.padding(horizontal = 16.dp)) } }
                item {
                    Row(Modifier.padding(16.dp, 14.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        listOf("Limit" to a.limitPaise, "Used" to b.outstanding, "Available" to b.available).forEach { (l, v) ->
                            BkCard(Modifier.weight(1f), padding = PaddingValues(12.dp)) {
                                Text(l, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                MoneyText(v, style = MaterialTheme.typography.titleSmall)
                            }
                        }
                    }
                    if (a.limitPaise > 0) Bar(b.outstanding.toFloat() / a.limitPaise, bk.expense, Modifier.padding(16.dp, 12.dp))
                }
                item {
                    BkCard(Modifier.padding(16.dp, 10.dp).fillMaxWidth()) {
                        if (a.statementDay <= 0) ListRow("Add bill date and due date", "So the app can show the bill and remind you", leading = { Icon(Icons.Outlined.Event, null) }, trailing = { Chev() }, onClick = { nav.go("account_edit/${a.id}?type=CARD") })
                        else {
                            ListRow("Bill ${if (LocalHide.current) "₹ ••••" else Money.fmt(b.bill)}", "Bill date ${a.statementDay} · minimum due ${if (LocalHide.current) "₹ ••••" else Money.fmt(b.minDue)}",
                                Modifier.testTag("bill_row"), leading = { Icon(Icons.Outlined.ReceiptLong, null) }, trailing = { if (b.bill > 0) Icon(Icons.Outlined.Edit, "Edit minimum due", tint = MaterialTheme.colorScheme.onSurfaceVariant) },
                                onClick = if (b.bill > 0) ({ editMin = true }) else null)
                            b.due?.let { due ->
                                val d = java.time.temporal.ChronoUnit.DAYS.between(Dates.today(), due)
                                ListRow("Due ${Dates.dayMonth(due)}", if (b.bill <= 0) "Nothing to pay right now" else if (d < 0) "Overdue by ${-d} days" else "in $d days",
                                    leading = { Icon(Icons.Outlined.Event, null) }, trailing = { if (b.bill > 0) Text("${d}d", color = if (d <= 5) bk.expense else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelLarge) })
                            }
                            ListRow("${CardMath.interestFreeDays(Dates.today(), a.statementDay, a.dueDay)} interest-free days", "If you spend on this card today", leading = { Icon(Icons.Outlined.Lightbulb, null) })
                        }
                    }
                }
                b.cycleStart?.let { cs -> item { SectionLabel("This cycle · from ${Dates.dayMonth(cs)}") } }
                val cycle = b.cycleStart?.let { cs -> mine.filter { it.time >= Dates.startOf(cs) } } ?: mine
                if (cycle.isEmpty()) item { Text("No entries in this cycle yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 4.dp)) }
                else item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { cycle.take(limit).forEach { e -> EntryRow(e, vm, onClick = { nav.go("entry/${e.id}") }) } } }
            } else {
                item {
                    BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconBadge(Ico.account(a.type), MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
                            Spacer(Modifier.width(12.dp))
                            Column { Text(typeLabel(a.type) + (a.bank.takeIf { it.isNotBlank() }?.let { " · $it" } ?: ""), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                a.last4.takeIf { it.isNotBlank() }?.let { Text("Numbers ending " + it.replace(",", ", "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Balance", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(net, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.testTag("balance"))
                        if (a.upiApps.isNotBlank()) Text("UPI: " + a.upiApps.replace(",", ", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item {
                    Row(Modifier.padding(16.dp, 10.dp).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedPillButton("Adjust balance", Icons.Outlined.Tune, { adjust = true }, tag = "adjust")
                        if (a.type == AccType.BANK) OutlinedPillButton("Withdraw cash", Icons.Outlined.LocalAtm, { nav.go("transfer/withdraw?acc=${a.id}") }, tag = "withdraw")
                        if (a.type == AccType.BANK || a.type == AccType.CASH) OutlinedPillButton("Pay a card bill", Icons.Outlined.CreditCard, { nav.go("transfer/paybill?acc=${a.id}") }, tag = "paybill")
                    }
                }
                item { SectionLabel("Entries · ${mine.size}") }
                if (mine.isEmpty()) item { Text("No entries for this account yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 4.dp)) }
                else item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { mine.take(limit).forEach { e -> EntryRow(e, vm, onClick = { nav.go("entry/${e.id}") }) } } }
            }
            if (mine.size > limit) item { TextButton(onClick = { limit += 100 }, modifier = Modifier.fillMaxWidth()) { Text("Load more") } }
        }
        if (a.type == AccType.CARD) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            Button(onClick = { nav.go("transfer/paybill?acc=${a.id}") }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).testTag("pay_bill")) { Text("Pay card bill") }
        }
    }

    // minimum due is a 5% estimate until the statement (or an SMS) says otherwise; he can type the real one
    if (editMin && a.type == AccType.CARD) {
        val b = Ledger.cardBill(a, net, entries)
        TextDialog("Minimum due on your statement", Money.input(b.minDue), { t ->
            Money.parse(t)?.let { v -> vm.launch {
                vm.repo.saveAccount(a.copy(minDuePaise = minOf(v, b.bill), billPaise = b.bill, billDueEpochDay = (b.due ?: Dates.today()).toEpochDay()))
                snack.show("Minimum due set to ${Money.fmt(minOf(v, b.bill))}")
            } }
        }, { editMin = false }, hint = "The app estimated ${Money.fmt(b.minDue)} (5% of the bill, at least ₹200)", kb = KeyboardType.Decimal)
    }
    if (adjust) TextDialog("Real balance today", Money.input(maxOf(0L, if (a.type == AccType.CARD) -net else net)), { t ->
        Money.parse(t)?.let { p -> vm.launch { vm.repo.adjustBalance(a, p); snack.show("Balance updated to ${Money.fmt(p)}") } }
    }, { adjust = false }, hint = "Type what your bank app shows. The difference is saved as an adjustment.", kb = KeyboardType.Decimal)
    if (confirmDelete) ConfirmDialog("Delete ${a.name}?", "This also deletes its ${mine.size} entries. Archive it instead if you want to keep them in reports.", "Delete",
        onConfirm = { vm.launch { vm.repo.deleteAccount(a) }; nav.popBackStack() }, onDismiss = { confirmDelete = false })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AccountEditScreen(vm: AppVm, id: Long, type: String) {
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val bk = LocalBk.current
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val existing = accounts.firstOrNull { it.id == id }
    var t by rememberSaveable { mutableStateOf(type) }
    var name by rememberSaveable { mutableStateOf("") }
    var bank by rememberSaveable { mutableStateOf("") }
    var last4 by rememberSaveable { mutableStateOf("") }
    var opening by rememberSaveable { mutableStateOf("") }
    var limit by rememberSaveable { mutableStateOf("") }
    var st by rememberSaveable { mutableStateOf("") }
    var due by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableIntStateOf(0) }
    var upis by rememberSaveable { mutableStateOf("") }
    var loaded by rememberSaveable { mutableStateOf(false) }
    var tried by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(existing) {
        if (existing != null && !loaded) {
            t = existing.type; name = existing.name; bank = existing.bank; last4 = existing.last4
            opening = Money.input(kotlin.math.abs(existing.openingPaise)); limit = if (existing.limitPaise > 0) Money.input(existing.limitPaise) else ""
            st = existing.statementDay.takeIf { it > 0 }?.toString().orEmpty(); due = existing.dueDay.takeIf { it > 0 }?.toString().orEmpty()
            color = existing.colorIdx; upis = existing.upiApps; loaded = true
        }
    }
    fun snapNow() = listOf(t, name, bank, last4, opening, limit, st, due, color, upis).joinToString("|")
    val snap = snapNow()
    var initial by rememberSaveable { mutableStateOf<String?>(null) }
    // the starting point is read when the form is filled (not from an older composition), so an untouched form is never "dirty"
    LaunchedEffect(loaded) { if (initial == null && (id == 0L || loaded)) initial = snapNow() }
    val close = rememberDiscardGuard(initial != null && snap != initial, if (id > 0) "Discard your changes?" else "Discard this account?")
    val stOk = st.isBlank() || st.toIntOrNull() in 1..31
    val dueOk = due.isBlank() || due.toIntOrNull() in 1..31
    val nameOk = name.isNotBlank()

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar(if (id > 0) "Edit account" else "Add " + typeLabel(t).lowercase(), close = true, onBack = close) }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(16.dp).widthIn(max = 640.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (id == 0L) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(AccType.BANK, AccType.CARD, AccType.CASH, AccType.WALLET).forEach { k -> Pill(typeLabel(k), t == k, { t = k }, Ico.account(k), Modifier.testTag("type_$k")) }
                }
                TextIn(name, { name = it }, when (t) { AccType.CARD -> "Card name (e.g. HDFC Millennia)"; AccType.CASH -> "Name (e.g. Cash in hand)"; AccType.WALLET -> "Wallet (e.g. Paytm Wallet)"; else -> "Account name (e.g. SBI Savings)" } + " *",
                    tag = "name", error = tried && !nameOk, supporting = if (tried && !nameOk) "Give it a name" else null)
                if (t == AccType.BANK || t == AccType.CARD) TextIn(bank, { bank = it }, "Bank (optional)", tag = "bank")
                if (t != AccType.CASH) TextIn(last4, { v -> last4 = v.filter { it.isDigit() || it == ',' }.take(30) }, "Last 4 digits (optional)", tag = "last4", kb = KeyboardType.Number,
                    supporting = "Matches bank SMS to this ${if (t == AccType.CARD) "card" else "account"}. Add more with commas, e.g. 4417,2290")
                MoneyField(opening, { opening = it }, if (t == AccType.CARD) "Amount you owe today" else "Balance today", tag = "opening")
                Text(if (t == AccType.CARD) "What's already on the card. It isn't counted as spending." else "Your starting point. It isn't counted as income.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (t == AccType.CARD) {
                    MoneyField(limit, { limit = it }, "Credit limit", tag = "limit")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        TextIn(st, { v -> st = v.filter { it.isDigit() }.take(2) }, "Bill date (day)", Modifier.weight(1f), "st", KeyboardType.Number, error = !stOk, supporting = if (!stOk) "1 to 31" else "e.g. 5")
                        TextIn(due, { v -> due = v.filter { it.isDigit() }.take(2) }, "Due date (day)", Modifier.weight(1f), "due", KeyboardType.Number, error = !dueOk, supporting = if (!dueOk) "1 to 31" else "e.g. 25")
                    }
                    Text("Card colour", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.selectableGroup()) {
                        val names = listOf("Black", "Blue", "Red", "Green", "Brown", "Purple", "Teal", "Gold")
                        bk.cardFaces.forEachIndexed { i, c ->
                            Box(Modifier.size(48.dp).clip(CircleShape).background(c).border(if (color == i) 3.dp else 0.dp, bk.saffron, CircleShape)
                                .selectable(color == i, role = Role.RadioButton) { color = i }
                                .semantics { contentDescription = "Card colour " + names.getOrElse(i) { "${i + 1}" } }.testTag("Colour ${i + 1}"))
                        }
                    }
                }
                if (t == AccType.BANK) {
                    Text("UPI apps linked to this account", style = MaterialTheme.typography.labelLarge)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("GPay", "PhonePe", "Paytm", "BHIM", "CRED", "Amazon Pay").forEach { u ->
                            val list = upis.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                            Pill(u, u in list, { upis = (if (u in list) list - u else list + u).joinToString(",") }, modifier = Modifier.testTag("upi_$u"))
                        }
                    }
                }
            }
            Button(onClick = {
                tried = true
                if (!nameOk || !stOk || !dueOk) return@Button
                val op = Money.parse(opening) ?: 0L
                val a = (existing ?: Account(type = t, name = "")).copy(
                    type = t, name = name.trim(), bank = bank.trim(), last4 = last4.split(',').map { it.trim() }.filter { it.isNotEmpty() }.joinToString(","),
                    openingPaise = if (t == AccType.CARD) -op else op, limitPaise = Money.parse(limit) ?: 0L,
                    statementDay = st.toIntOrNull() ?: 0, dueDay = due.toIntOrNull() ?: 0, colorIdx = color, upiApps = if (t == AccType.BANK) upis else "",
                )
                vm.launch { vm.repo.saveAccount(a); snack.show(if (id > 0) "Account updated" else "${a.name} added"); nav.popBackStack() }
            }, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).testTag("save_account")) { Text(if (id > 0) "Save changes" else "Add account") }
        }
    }
}


/** Pay card bill (bank/cash -> card) or withdraw cash (bank -> cash). Neither is counted as spending. */
@Composable
fun TransferScreen(vm: AppVm, kind: String, accId: Long) {
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val entries by vm.entries.collectAsStateWithLifecycle()
    val nets by vm.nets.collectAsStateWithLifecycle()
    val pay = kind == "paybill"
    val active = accounts.filter { !it.archived }
    val cards = active.filter { it.type == AccType.CARD }
    val sources = active.filter { if (pay) it.type == AccType.BANK || it.type == AccType.CASH || it.type == AccType.WALLET else it.type == AccType.BANK }
    var cardId by rememberSaveable { mutableLongStateOf(if (pay) (cards.firstOrNull { it.id == accId }?.id ?: cards.firstOrNull()?.id ?: 0L) else 0L) }
    var fromId by rememberSaveable { mutableLongStateOf(sources.firstOrNull { it.id == accId }?.id ?: sources.firstOrNull()?.id ?: 0L) }
    var amt by rememberSaveable { mutableStateOf("") }
    var choice by rememberSaveable { mutableStateOf("FULL") }
    val card = cards.firstOrNull { it.id == cardId }
    val bill = card?.let { Ledger.cardBill(it, nets[it.id] ?: 0L, entries) }
    LaunchedEffect(cardId, bill?.bill, choice) {
        if (pay && bill != null) when (choice) { "FULL" -> amt = Money.input(if (bill.bill > 0) bill.bill else bill.outstanding); "MIN" -> amt = Money.input(bill.minDue) }
    }
    val amount = Money.parse(amt) ?: 0L
    val cash = active.firstOrNull { it.type == AccType.CASH }
    val close = rememberDiscardGuard(choice == "OTHER" && amt.isNotBlank(), "Discard this ${if (pay) "payment" else "withdrawal"}?")

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar(if (pay) "Pay card bill" else "Withdraw cash", close = true, onBack = close) }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).widthIn(max = 640.dp)) {
                if (pay && cards.isEmpty()) { EmptyState(Icons.Outlined.CreditCard, "No credit card yet", "Add a credit card first.", "Add card", { nav.go("account_edit/0?type=CARD") }); return@Column }
                if (sources.isEmpty()) { EmptyState(Icons.Outlined.AccountBalance, "No bank account yet", "Add the bank account the money comes from.", "Add bank account", { nav.go("account_edit/0?type=BANK") }); return@Column }
                if (pay) {
                    SectionLabel("Card")
                    Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        cards.forEach { c -> Pill(c.name, c.id == cardId, { cardId = c.id }, Icons.Outlined.CreditCard, Modifier.testTag("card_${c.name}")) }
                    }
                    bill?.let { b -> Text("Bill ${hm(b.bill)} · outstanding ${hm(b.outstanding)}" + (b.due?.let { " · due ${Dates.dayMonth(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 8.dp)) }
                }
                Text(if (amount > 0) Money.fmt(amount) else "₹0", style = MaterialTheme.typography.displayMedium, modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                if (pay) Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Full bill", choice == "FULL", { choice = "FULL" }); Pill("Minimum due", choice == "MIN", { choice = "MIN" }); Pill("Other amount", choice == "OTHER", { choice = "OTHER"; amt = "" })
                }
                MoneyField(amt, { amt = it; choice = "OTHER" }, "Amount", Modifier.padding(16.dp), "amount")
                SectionLabel(if (pay) "Pay from" else "From bank account")
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    sources.forEach { s ->
                        val bal = nets[s.id] ?: 0L
                        ListRow(s.name, if (s.id == fromId && amount > 0) "${hm(bal)} → ${hm(bal - amount)}" else hm(bal), Modifier.testTag("from_${s.name}"),
                            leading = { Icon(Ico.account(s.type), null) }, trailing = { RadioButton(s.id == fromId, { fromId = s.id }) }, onClick = { fromId = s.id })
                    }
                }
                Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalBk.current.info).padding(12.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = LocalBk.current.onInfo); Spacer(Modifier.width(10.dp))
                    Text(if (pay) "Not counted as spending — your card purchases were already counted." else "Not counted as spending — the money just moves to cash in hand.", color = LocalBk.current.onInfo, style = MaterialTheme.typography.bodySmall)
                }
            }
            Button(onClick = {
                vm.launch {
                    if (pay) vm.repo.transfer(EType.CARD_PAYMENT, fromId, cardId, amount, note = "Card bill paid")
                    else {
                        val to = cash?.id ?: vm.repo.saveAccount(Account(type = AccType.CASH, name = "Cash"))
                        vm.repo.transfer(EType.CASH_WITHDRAWAL, fromId, to, amount, note = "Cash withdrawn")
                    }
                    snack.show(if (pay) "Card bill paid · ${Money.fmt(amount)}" else "Cash withdrawn · ${Money.fmt(amount)}")
                    nav.popBackStack()
                }
            }, enabled = amount > 0 && fromId > 0 && (!pay || cardId > 0), modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).testTag("do_transfer")) {
                Text(if (pay) "Pay ${if (amount > 0) Money.fmt(amount) else ""}" else "Withdraw ${if (amount > 0) Money.fmt(amount) else ""}")
            }
        }
    }
}
