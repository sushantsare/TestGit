@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth

private val ymSaver = androidx.compose.runtime.saveable.Saver<YearMonth, String>({ it.toString() }, { YearMonth.parse(it) })

@Composable
fun EntriesScreen(vm: AppVm) {
    val nav = LocalNav.current
    val bk = LocalBk.current
    val wide = LocalWide.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    var calendar by rememberSaveable { mutableStateOf(false) }
    var ym by rememberSaveable(stateSaver = ymSaver) { mutableStateOf(YearMonth.now()) }
    var typeF by rememberSaveable { mutableStateOf("ALL") }
    var accF by rememberSaveable { mutableLongStateOf(0L) }
    var catF by rememberSaveable { mutableLongStateOf(0L) }
    var day by rememberSaveable { mutableIntStateOf(0) }
    var selected by rememberSaveable { mutableLongStateOf(0L) }
    var pick by rememberSaveable { mutableStateOf<String?>(null) }
    var limit by rememberSaveable { mutableIntStateOf(200) }

    val r = Dates.monthRange(ym)
    val filtered = remember(entries, r, typeF, accF, catF, calendar, day) { entries.filter { e ->
        e.time in r && (typeF == "ALL" || (typeF == "EXP" && e.type == EType.EXPENSE) || (typeF == "INC" && e.type == EType.INCOME) || (typeF == "OTHER" && e.type != EType.EXPENSE && e.type != EType.INCOME)) &&
            (accF == 0L || e.accountId == accF || e.toAccountId == accF) && (catF == 0L || e.categoryId == catF) &&
            (!calendar || day == 0 || Dates.date(e.time).dayOfMonth == day)
    } }
    val open: (Long) -> Unit = { id -> if (wide) selected = id else nav.go("entry/$id") }

    val list: @Composable (Modifier) -> Unit = { mod ->
        Column(mod) {
            OutlinedCard(onClick = { nav.go("search") }, shape = RoundedCornerShape(50), modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().searchMorph("search_entries").testTag("search_box"),
                colors = CardDefaults.outlinedCardColors(containerColor = bk.card), border = androidx.compose.foundation.BorderStroke(1.dp, bk.cardBorder)) {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant); Spacer(Modifier.width(10.dp))
                    Text("Search shop, note, #tag", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Row(Modifier.padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { ym = ym.minusMonths(1); day = 0 }) { Icon(Icons.Outlined.ChevronLeft, "Previous month") }
                Text(Dates.month(ym), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
                IconButton(onClick = { ym = ym.plusMonths(1); day = 0 }, enabled = ym < YearMonth.now()) { Icon(Icons.Outlined.ChevronRight, "Next month") }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("ALL" to "All", "EXP" to "Expense", "INC" to "Income", "OTHER" to "Transfers").forEach { (k, l) -> Pill(l, typeF == k, { typeF = k }, modifier = Modifier.testTag("f_type_$k")) }
                Pill(accounts.firstOrNull { it.id == accF }?.name ?: "Any account", accF != 0L, { pick = "acc" }, Icons.Outlined.AccountBalanceWallet)
                Pill(categories.firstOrNull { it.id == catF }?.name ?: "Any category", catF != 0L, { pick = "cat" }, Icons.Outlined.Category)
                if (accF != 0L || catF != 0L || typeF != "ALL") TextButton(onClick = { accF = 0; catF = 0; typeF = "ALL" }) { Text("Clear") }
            }
            if (calendar) CalendarGrid(entries, ym, day) { day = if (day == it) 0 else it }
            if (filtered.isEmpty()) {
                EmptyState(Icons.Outlined.ReceiptLong, if (entries.isEmpty()) "No entries yet" else "Nothing here",
                    if (entries.isEmpty()) "Tap + to add your first expense or income." else "No entries match this month and filters.",
                    if (entries.isEmpty()) "Add entry" else null, { nav.go("add?type=EXPENSE") })
            } else {
                val groups = filtered.take(limit).groupBy { Dates.date(it.time) }
                LazyColumn(Modifier.fillMaxSize().testTag("entries_list"), contentPadding = PaddingValues(bottom = 96.dp)) {
                    groups.forEach { (d, dayList) ->
                        item(key = "h$d") {
                            val tot = dayList.sumOf { if (it.type == EType.EXPENSE) -it.amountPaise else if (it.type == EType.INCOME) it.amountPaise else 0L }
                            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 6.dp)) {
                                Text(Dates.dayHeader(d), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                MoneyText(tot, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, sign = tot > 0)
                            }
                        }
                        item(key = "g$d") {
                            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { dayList.forEach { e -> EntryRow(e, vm, onClick = { open(e.id) }) } }
                        }
                    }
                    if (filtered.size > limit) item { TextButton(onClick = { limit += 200 }, modifier = Modifier.fillMaxWidth().padding(8.dp)) { Text("Load more (${filtered.size - limit} left)") } }
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = { MainBar("Entries") {
            Act(if (calendar) Icons.Outlined.ViewList else Icons.Outlined.CalendarMonth, if (calendar) "List view" else "Calendar view", "calendar") { calendar = !calendar; day = 0 }
            Act(Icons.Outlined.FileDownload, "Export", "export") { nav.go("export") }
        } },
        floatingActionButton = { if (!com.sushant.bahikhata.ui.railShown()) FloatingActionButton(onClick = { nav.go("add?type=EXPENSE") }, containerColor = bk.saffron, contentColor = bk.onSaffron, shape = RoundedCornerShape(18.dp), modifier = Modifier.testTag("fab_add")) { Icon(Icons.Outlined.Add, "Add entry", Modifier.size(30.dp)) } },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        if (wide) Row(Modifier.padding(pad).fillMaxSize()) {
            list(Modifier.weight(0.45f))
            VerticalDivider(color = bk.cardBorder)
            Box(Modifier.weight(0.55f)) {
                if (selected > 0) EntryDetailContent(vm, selected, embedded = true) { selected = 0 }
                else EmptyState(Icons.Outlined.TouchApp, "Pick an entry", "Its details, receipts and split show here.")
            }
        } else list(Modifier.padding(pad).fillMaxSize())
    }

    when (pick) {
        "acc" -> PickSheet("Account", listOf<Account?>(null) + accounts, { it?.name ?: "Any account" }, onPick = { accF = it?.id ?: 0 }, onDismiss = { pick = null })
        "cat" -> PickSheet("Category", listOf<Category?>(null) + categories.filter { !it.archived }, { it?.let { c -> c.name + if (c.kind == "INCOME") " (income)" else "" } ?: "Any category" }, onPick = { catF = it?.id ?: 0 }, onDismiss = { pick = null })
    }
}

@Composable
private fun CalendarGrid(entries: List<Entry>, ym: YearMonth, selected: Int, onDay: (Int) -> Unit) {
    val bk = LocalBk.current
    val daily = Ledger.daily(entries, ym)
    val offset = ym.atDay(1).dayOfWeek.value - 1
    val days = ym.lengthOfMonth()
    BkCard(Modifier.padding(16.dp, 8.dp).fillMaxWidth().testTag("calendar"), padding = PaddingValues(8.dp)) {
        Row { listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) } }
        val cells = (0 until offset).map { 0 } + (1..days).toList()
        cells.chunked(7).forEach { week ->
            Row {
                week.forEach { d ->
                    Column(
                        Modifier.weight(1f).heightIn(min = 50.dp).clip(RoundedCornerShape(10.dp))
                            .background(if (d == selected && d > 0) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent)
                            .then(if (d > 0) Modifier.clickable { onDay(d) } else Modifier).padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                    ) {
                        if (d > 0) {
                            Text("$d", style = MaterialTheme.typography.labelLarge, color = if (ym.atDay(d) == Dates.today()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                            daily[d]?.let { v -> Text(if (LocalHide.current) "•" else Money.compact(v).removePrefix("₹"), style = MaterialTheme.typography.labelSmall, color = bk.expense, maxLines = 1) }
                        }
                    }
                }
                repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun SearchScreen(vm: AppVm) {
    val nav = LocalNav.current
    val entries by vm.entries.collectAsStateWithLifecycle()
    val cats by vm.catMap.collectAsStateWithLifecycle()
    val accs by vm.accMap.collectAsStateWithLifecycle()
    var q by rememberSaveable { mutableStateOf("") }
    var typeF by rememberSaveable { mutableStateOf("ALL") }
    var period by rememberSaveable { mutableStateOf("YEAR") }
    var minA by rememberSaveable { mutableStateOf("") }
    var maxA by rememberSaveable { mutableStateOf("") }
    val fr = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { fr.requestFocus() } }
    // the bar grows out of whichever search the user tapped (Home's icon or the Entries box) and shrinks back into it
    val morphKey = remember { if (nav.previousBackStackEntry?.destination?.route?.startsWith("entries") == true) "search_entries" else "search_home" }

    val now = System.currentTimeMillis()
    val from = when (period) { "MONTH" -> Dates.startOf(Dates.today().withDayOfMonth(1)); "YEAR" -> Dates.startOf(Dates.today().withDayOfYear(1)); else -> 0L }
    val lo = Money.parse(minA); val hi = Money.parse(maxA)
    val ql = q.trim().lowercase().removePrefix("#")
    val res = if (ql.isEmpty() && lo == null && hi == null) emptyList() else entries.filter { e ->
        e.time in from..now + 86_400_000L &&
            (typeF == "ALL" || (typeF == "EXP" && e.type == EType.EXPENSE) || (typeF == "INC" && e.type == EType.INCOME)) &&
            (lo == null || e.amountPaise >= lo) && (hi == null || e.amountPaise <= hi) &&
            (ql.isEmpty() || listOfNotNull(e.merchant, e.note, e.upiApp, e.tags, cats[e.categoryId ?: -1]?.name, accs[e.accountId]?.name).any { it.lowercase().contains(ql) })
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        Row(Modifier.statusBarsPadding().padding(start = 4.dp, end = 12.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("back")) { Icon(Icons.Outlined.ArrowBack, "Back") }
            OutlinedTextField(q, { q = it }, placeholder = { Text("Shop, note, #tag, account") }, singleLine = true, shape = RoundedCornerShape(50),
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Outlined.Close, "Clear") } },
                modifier = Modifier.weight(1f).searchMorph(morphKey).focusRequester(fr).testTag("f_search"))
        }
    }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad)) {
            SectionLabel("Filters")
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("MONTH" to "This month", "YEAR" to "This year", "ALL" to "All time").forEach { (k, l) -> Pill(l, period == k, { period = k }) }
                listOf("ALL" to "Any type", "EXP" to "Expense", "INC" to "Income").forEach { (k, l) -> Pill(l, typeF == k, { typeF = k }) }
            }
            Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MoneyField(minA, { minA = it }, "Min ₹", Modifier.weight(1f), "min")
                MoneyField(maxA, { maxA = it }, "Max ₹", Modifier.weight(1f), "max")
            }
            if (res.isNotEmpty()) Row(Modifier.padding(horizontal = 18.dp, vertical = 6.dp)) {
                Text("${res.size} RESULTS", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                MoneyText(res.filter { !it.cancelled }.sumOf { signed(it) }, style = MaterialTheme.typography.labelMedium)
            }
            when {
                ql.isEmpty() && lo == null && hi == null -> EmptyState(Icons.Outlined.Search, "Search your khata", "Type a shop name, a note, an account or a #tag.")
                res.isEmpty() -> EmptyState(Icons.Outlined.SearchOff, "No results for \"$q\"", "Check the spelling, try fewer words, or widen the dates.")
                else -> LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                    item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) { res.take(300).forEach { e -> EntryRow(e, vm, onClick = { nav.go("entry/${e.id}") }) } } }
                }
            }
        }
    }
}

@Composable
fun EntryDetailScreen(vm: AppVm, id: Long) {
    val nav = LocalNav.current
    EntryDetailContent(vm, id, embedded = false) { nav.popBackStack() }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EntryDetailContent(vm: AppVm, id: Long, embedded: Boolean, onGone: () -> Unit) {
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    val scope = rememberCoroutineScope()
    val flow = remember(id) { vm.repo.entryFlow(id) }
    val e by flow.collectAsStateWithLifecycle(null)
    val attFlow = remember(id) { vm.repo.attachments(id) }
    val atts by attFlow.collectAsStateWithLifecycle(emptyList())
    val cats by vm.catMap.collectAsStateWithLifecycle()
    val accs by vm.accMap.collectAsStateWithLifecycle()
    val people by vm.personMap.collectAsStateWithLifecycle()
    val udhar by vm.udhar.collectAsStateWithLifecycle()
    val names by vm.upiNames.collectAsStateWithLifecycle()
    val allItems by vm.items.collectAsStateWithLifecycle()
    val allEntries by vm.entries.collectAsStateWithLifecycle()
    val st by vm.settings.collectAsStateWithLifecycle()
    var confirm by rememberSaveable { mutableStateOf(false) }
    var itemsOpen by rememberSaveable { mutableStateOf(false) }
    var viewing by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
    var seen by rememberSaveable { mutableStateOf(false) }
    val entry = e
    LaunchedEffect(entry) { if (entry != null) seen = true else if (seen) onGone() }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        if (embedded) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End) {
            entry?.let { en ->
                Act(Icons.Outlined.ContentCopy, "Duplicate", "duplicate") { vm.launch { vm.repo.duplicate(en); snack.show("Copied as a new entry for today") } }
                if (en.type == EType.EXPENSE || en.type == EType.INCOME) Act(Icons.Outlined.Edit, "Edit", "edit") { nav.go("add?id=${en.id}") }
                Act(Icons.Outlined.Delete, "Delete", "delete") { confirm = true }
            }
        } else BackBar("") {
            entry?.let { en ->
                Act(Icons.Outlined.ContentCopy, "Duplicate", "duplicate") { vm.launch { vm.repo.duplicate(en); snack.show("Copied as a new entry for today") } }
                if (en.type == EType.EXPENSE || en.type == EType.INCOME) Act(Icons.Outlined.Edit, "Edit", "edit") { nav.go("add?id=${en.id}") }
                Act(Icons.Outlined.Delete, "Delete", "delete") { confirm = true }
            }
        }
    }, contentWindowInsets = WindowInsets(0)) { pad ->
        if (entry == null) {
            val loaded by vm.loaded.collectAsStateWithLifecycle()
            Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
                if (!loaded) CircularProgressIndicator()
                else EmptyState(Icons.Outlined.SearchOff, "This entry isn't here", "It may have been deleted.", "Go back", { nav.popBackStack() })
            }
            return@Scaffold
        }
        val cat = entry.categoryId?.let { cats[it] }
        val v = signed(entry)
        Column(Modifier.padding(pad).fillMaxSize().verticalScroll(rememberScrollState()).testTag("entry_detail")) {
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                val (bg, fg) = cat?.let { bk.cat[it.colorIdx.mod(bk.cat.size)] } ?: (MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface)
                IconBadge(Ico.entry(entry, cat), bg, fg, size = 64.dp, iconSize = 32.dp)
                Spacer(Modifier.height(10.dp))
                MoneyText(v, style = MaterialTheme.typography.displaySmall, sign = v > 0, color = if (entry.type == EType.EXPENSE) bk.expense else if (entry.type == EType.INCOME) bk.income else MaterialTheme.colorScheme.onSurface)
                Text(entryTitle(entry, cat, accs, people), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
                Text(Dates.full(entry.time), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.cancelled) Row(Modifier.padding(16.dp, 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bk.info).padding(12.dp).testTag("cancelled_note"),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Block, null, tint = bk.onInfo); Spacer(Modifier.width(10.dp))
                Text("Cancelled — the bank said this payment failed or was reversed. It isn't counted anywhere.", color = bk.onInfo, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.launch { vm.repo.setCancelled(entry, false); snack.show("Counted again") } }, modifier = Modifier.testTag("uncancel")) { Text("Count it") }
            }
            entry.refundOf?.let { oid -> allEntries.firstOrNull { it.id == oid } }?.let { o ->
                Row(Modifier.padding(16.dp, 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bk.info).clickable { nav.go("entry/${o.id}") }.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Undo, null, tint = bk.onInfo); Spacer(Modifier.width(10.dp))
                    Text("Refund for ${o.merchant ?: "a purchase"} on ${Dates.dayMonth(Dates.date(o.time))} — lowers that spending instead of counting as income", color = bk.onInfo, style = MaterialTheme.typography.bodySmall)
                }
            }
            allEntries.filter { it.refundOf == entry.id && !it.cancelled }.takeIf { it.isNotEmpty() }?.let { rf ->
                Row(Modifier.padding(16.dp, 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(bk.info).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Undo, null, tint = bk.onInfo); Spacer(Modifier.width(10.dp))
                    Text("Refunded ${hm(rf.sumOf { it.amountPaise })}" + if (rf.sumOf { it.amountPaise } >= entry.amountPaise) " — fully paid back" else "", color = bk.onInfo, style = MaterialTheme.typography.bodySmall)
                }
            }
            val upiKey = vm.repo.upiKey(entry.vpa, entry.merchant.takeIf { entry.smsKey != null })
            if (upiKey != null && (entry.type == EType.EXPENSE || entry.type == EType.INCOME) && names.none { it.key == upiKey })
                NameUpiCard(vm, upiKey, entry.vpa ?: entry.merchant.orEmpty(), entry.merchant.orEmpty(), entry.type == EType.EXPENSE, Modifier.padding(16.dp, 4.dp))
            val its = allItems.filter { it.entryId == entry.id }
            if (entry.type == EType.EXPENSE && !entry.cancelled) {
                if (its.isEmpty() && st.askItems) Row(Modifier.padding(16.dp, 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer)
                    .clickable { itemsOpen = true }.padding(12.dp).testTag("ask_items"), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.ShoppingBasket, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(10.dp))
                    Text("What did you buy?", color = MaterialTheme.colorScheme.onSecondaryContainer, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    Text("Add items", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                }
            }
            if (its.isNotEmpty()) {
                SectionLabel("Items · ${its.size}", action = "Edit", onAction = { itemsOpen = true })
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth().testTag("items_card")) {
                    its.forEach { i ->
                        val hasQty = i.qty != 1.0 || i.unit.isNotBlank()
                        val sub = if (!hasQty) null else Insights.qtyText(i.qty, i.unit) + if (i.pricePaise > 0) " · ${hm(Insights.unitPrice(i.pricePaise, i.qty, i.unit))} " +
                            Insights.unitLabel(i.unit).let { l -> if (l == "each") "each" else "per " + l.removePrefix("/") } else ""
                        ListRow(i.name, sub, leading = { Icon(Icons.Outlined.ShoppingBasket, null) }, trailing = { if (i.pricePaise > 0) MoneyText(i.pricePaise) })
                    }
                    val sum = its.sumOf { it.pricePaise.coerceAtLeast(0) }
                    if (sum in 1 until entry.amountPaise) ListRow("Not given to an item", null, trailing = { MoneyText(entry.amountPaise - sum, color = MaterialTheme.colorScheme.onSurfaceVariant) })
                }
            }
            Spacer(Modifier.height(12.dp))
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                ListRow(Export.typeName(entry.type), "Type", leading = { Icon(Icons.Outlined.SwapVert, null) })
                if (cat != null) ListRow(cat.name, "Category", leading = { Icon(Icons.Outlined.Category, null) })
                ListRow(accs[entry.accountId]?.name ?: "—", (if (entry.type == EType.INCOME) "Received in" else "Paid from") + (entry.upiApp?.let { " · via $it" } ?: ""),
                    leading = { Icon(Ico.account(accs[entry.accountId]?.type ?: AccType.BANK), null) }, onClick = { nav.go("account/${entry.accountId}") })
                entry.toAccountId?.let { t -> ListRow(accs[t]?.name ?: "—", "To", leading = { Icon(Icons.Outlined.ArrowForward, null) }) }
                if (!entry.note.isNullOrBlank()) ListRow(entry.note, "Note", leading = { Icon(Icons.Outlined.EditNote, null) })
                if (entry.tags.isNotBlank()) ListRow(Ledger.tags(entry.tags).joinToString("  ") { "#$it" }, "Tags", leading = { Icon(Icons.Outlined.Sell, null) })
                if (entry.smsKey != null) ListRow("Saved from bank SMS", null, leading = { Icon(Icons.Outlined.Sms, null) })
                if (entry.recurringId != null) ListRow("Repeating entry", "Manage in Settings → Recurring", leading = { Icon(Icons.Outlined.Repeat, null) }, onClick = { nav.go("recurring") })
            }
            if (atts.isNotEmpty()) {
                SectionLabel("Attachments · ${atts.size}")
                FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    atts.forEach { a -> AttachmentTile(vm, a) { bmp -> viewing = bmp } }
                }
            }
            val splits = udhar.filter { it.entryId == entry.id && entry.type == EType.EXPENSE }
            if (splits.isNotEmpty()) {
                SectionLabel("Split")
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    splits.forEach { u ->
                        val p = people[u.personId]
                        ListRow("${p?.name ?: "Someone"} owes you ${hm(u.amountPaise)}", "Added to Udhar", leading = { Initials(p?.name ?: "?") },
                            trailing = { MoneyText(u.amountPaise, color = bk.income, sign = true) }, onClick = { nav.go("person/${u.personId}") })
                    }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (itemsOpen) entry?.let { en ->
        ItemsDialog("What did you buy?", en.amountPaise, en.merchant.orEmpty(), allItems.filter { it.entryId == en.id }.map { Insights.Item(it.name, it.pricePaise, it.qty, it.unit) }, allItems,
            onSave = { list -> vm.launch { vm.repo.setItems(en.id, list) } }, onDismiss = { itemsOpen = false })
    }
    if (confirm) ConfirmDialog("Delete this entry?", "Balances and reports will update. You can undo right after.", "Delete", onConfirm = {
        val en = entry ?: return@ConfirmDialog
        vm.launch {
            val d = vm.repo.deleteEntry(en)
            snack.show("Entry deleted", "Undo") { vm.launch { vm.repo.undoDelete(d) } }
        }
        if (!embedded) nav.popBackStack() else onGone()
    }, onDismiss = { confirm = false })

    viewing?.let { bmp ->
        Dialog(onDismissRequest = { viewing = null }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black).clickable { viewing = null }, contentAlignment = Alignment.Center) {
                Image(bmp.asImageBitmap(), "Receipt photo", Modifier.fillMaxWidth(), contentScale = ContentScale.Fit)
                IconButton(onClick = { viewing = null }, modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(8.dp)) { Icon(Icons.Outlined.Close, "Close", tint = androidx.compose.ui.graphics.Color.White) }
            }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unused = scope
}

@Composable
private fun AttachmentTile(vm: AppVm, a: Attachment, onImage: (android.graphics.Bitmap) -> Unit) {
    val ctx = LocalContext.current
    val bk = LocalBk.current
    val scope = rememberCoroutineScope()
    val snack = LocalSnack.current
    val isPdf = a.mime.contains("pdf")
    val isImage = a.mime.startsWith("image")
    var thumb by remember(a.id) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(a.id) {
        if (isImage) thumb = withContext(Dispatchers.IO) {
            runCatching {
                val f = vm.repo.openAttachment(a)
                val o = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeFile(f.absolutePath, o)
            }.getOrNull()
        }
    }
    Column(
        Modifier.size(110.dp).clip(RoundedCornerShape(14.dp)).background(bk.card).border(1.dp, bk.cardBorder, RoundedCornerShape(14.dp))
            .clickable {
                scope.launch {
                    val ok = runCatching {
                        val f = withContext(Dispatchers.IO) { vm.repo.openAttachment(a) }
                        if (isImage) withContext(Dispatchers.IO) { decodeForScreen(f).also { f.delete() } }?.let(onImage) ?: error("unreadable")   // plain copy removed at once
                        else viewFile(ctx, f, a.mime)
                    }.isSuccess
                    if (!ok) snack.show("Couldn't open this file. It may be damaged.")
                }
            }.testTag("att_${a.id}"),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
    ) {
        val t = thumb
        if (t != null) Image(t.asImageBitmap(), a.displayName, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        else {
            Icon(if (isPdf) Icons.Outlined.PictureAsPdf else Icons.Outlined.InsertDriveFile, null, tint = if (isPdf) bk.expense else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(36.dp))
            Text(a.displayName, style = MaterialTheme.typography.labelSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center, modifier = Modifier.padding(6.dp))
        }
    }
}

/** Decode a photo no larger than ~2 screen widths, so big camera photos can't run the phone out of memory. */
private fun decodeForScreen(f: java.io.File): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(f.absolutePath, bounds)
    val target = 2048
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= target || bounds.outHeight / (sample * 2) >= target) sample *= 2
    return BitmapFactory.decodeFile(f.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
}
