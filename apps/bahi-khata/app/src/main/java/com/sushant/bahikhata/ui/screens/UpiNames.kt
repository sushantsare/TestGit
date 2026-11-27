package com.sushant.bahikhata.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk

/** "Name this UPI ID?" — shown on an SMS suggestion or entry whose UPI ID (or shop text) has no saved name yet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NameUpiCard(vm: AppVm, key: String, shown: String, suggestion: String, debit: Boolean, modifier: Modifier = Modifier) {
    val snack = LocalSnack.current
    val cats by vm.categories.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    var name by rememberSaveable(key) { mutableStateOf(suggestion) }
    var person by rememberSaveable(key) { mutableStateOf(false) }
    var catId by rememberSaveable(key) { mutableStateOf<Long?>(null) }
    var open by rememberSaveable(key) { mutableStateOf(false) }
    Column(modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp).testTag("name_upi")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Badge, null, tint = MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Name this UPI ID?", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
                Text(shown, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (!open) TextButton(onClick = { open = true }, modifier = Modifier.testTag("name_upi_open")) { Text("Name it") }
        }
        if (open) {
            Spacer(Modifier.height(8.dp))
            TextIn(name, { name = it }, "Name (e.g. Sharma Kirana)", tag = "upi_name")
            Spacer(Modifier.height(6.dp))
            Segments(listOf("Shop", "Person"), if (person) 1 else 0, { person = it == 1 })
            Spacer(Modifier.height(6.dp))
            Text("Usual category", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                cats.filter { !it.archived && it.kind == if (debit) "EXPENSE" else "INCOME" }.forEach { c -> Pill(c.name, catId == c.id, { catId = if (catId == c.id) null else c.id }) }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = {
                vm.launch { vm.repo.nameUpi(UpiName(key = key, display = "")) }
                snack.show("Won't ask for this UPI ID again — you can name it from Settings → Saved UPI names")
            }, modifier = Modifier.testTag("name_upi_skip")) { Text("Skip") }
            if (open) Button(enabled = name.isNotBlank(), onClick = {
                val n = name.trim()
                val pid = if (person) people.firstOrNull { it.name.equals(n, true) }?.id else null
                vm.launch { vm.repo.nameUpi(UpiName(key = key, display = n, isPerson = person, categoryId = catId, personId = pid)) }
                snack.show("Saved — payments to this UPI ID now show as $n")
            }, modifier = Modifier.testTag("name_upi_save")) { Text("Save name") }
        }
    }
}

/** "What did you buy?": each item with an optional price; shows how much of the payment is still not split into items. */
@Composable
fun ItemsDialog(title: String, total: Long, shop: String, initial: List<Insights.Item>, history: List<EntryItem>, onSave: (List<Insights.Item>) -> Unit, onDismiss: () -> Unit) {
    // one row per item: name, quantity, unit, total paid for it
    data class R(val name: String = "", val qty: String = "", val unit: String = "", val total: String = "")
    fun qtyInput(q: Double) = if (q == 1.0) "" else Insights.qtyText(q, "")
    var rows by remember { mutableStateOf(initial.map { R(it.name, qtyInput(it.qty), it.unit, if (it.pricePaise > 0) Money.input(it.pricePaise) else "") }.ifEmpty { listOf(R()) }) }
    val shopKey = Insights.shopKey(shop)
    // things bought at this shop before, most often first, with the last price for one
    val usual = remember(history, shopKey) {
        history.filter { it.shopKey == shopKey && shopKey.isNotBlank() }.groupBy { it.name.lowercase() }
            .map { (_, l) -> l.maxBy { it.time } to l.size }.sortedByDescending { it.second }.take(10).map { it.first }
    }
    fun item(r: R) = Insights.Item(r.name.trim(), Money.parse(r.total) ?: -1L, r.qty.toDoubleOrNull()?.takeIf { it > 0 } ?: 1.0, r.unit)
    val parsed = rows.filter { it.name.isNotBlank() }.map(::item)
    val assigned = parsed.sumOf { it.pricePaise.coerceAtLeast(0) }
    fun update(i: Int, f: (R) -> R) { rows = rows.toMutableList().also { it[i] = f(it[i]) } }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (usual.isNotEmpty()) {
                Text("Bought here before", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    usual.forEach { u ->
                        val on = rows.any { it.name.equals(u.name, true) }
                        val per = Insights.unitPrice(u.pricePaise, u.qty, u.unit)
                        Pill(u.name + if (u.pricePaise > 0) " · ${Money.fmt(per)}${Insights.unitLabel(u.unit).let { if (it == "each") "" else it }}" else "", on, {
                            rows = if (on) rows.filterNot { it.name.equals(u.name, true) }.ifEmpty { listOf(R()) }
                            else (rows.filter { it.name.isNotBlank() || it.total.isNotBlank() } + R(u.name, "", u.unit, ""))
                        })
                    }
                }
            }
            rows.forEachIndexed { i, r ->
                Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(r.name, { v -> update(i) { it.copy(name = v) } }, label = { Text("Item") }, singleLine = true,
                            modifier = Modifier.weight(1f).testTag("item_name_$i"))
                        IconButton(onClick = { rows = rows.toMutableList().also { it.removeAt(i) }.ifEmpty { listOf(R()) } }) { Icon(Icons.Outlined.Close, "Remove item") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(r.qty, { v -> update(i) { it.copy(qty = v.filter { c -> c.isDigit() || c == '.' }.take(7)) } }, label = { Text("Qty") }, placeholder = { Text("1") },
                            singleLine = true, keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.weight(0.8f).testTag("item_qty_$i"))
                        var unitOpen by remember { mutableStateOf(false) }
                        Box {
                            OutlinedButton(onClick = { unitOpen = true }, contentPadding = PaddingValues(horizontal = 10.dp), modifier = Modifier.padding(top = 6.dp).testTag("item_unit_$i")) {
                                Text(r.unit.ifBlank { "pcs" }); Icon(Icons.Outlined.ArrowDropDown, null)
                            }
                            DropdownMenu(unitOpen, { unitOpen = false }) {
                                Insights.UNITS.forEach { u -> DropdownMenuItem(text = { Text(u.ifBlank { "pcs" }) }, onClick = { update(i) { it.copy(unit = u) }; unitOpen = false }) }
                            }
                        }
                        OutlinedTextField(r.total, { v -> update(i) { it.copy(total = v.filter { c -> c.isDigit() || c == '.' }) } }, label = { Text("Total ₹") }, singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                            modifier = Modifier.weight(1f).testTag("item_price_$i"))
                    }
                    val cur = item(r)
                    if (cur.pricePaise > 0 && cur.hasQty)
                        Text("${Money.fmt(cur.unitPricePaise)} ${cur.unitLabel.let { l -> if (l == "each") "each" else l.removePrefix("/").let { u -> "per $u" } }}",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 4.dp).testTag("item_each_$i"))
                }
            }
            TextButton(onClick = { rows = rows + R() }, modifier = Modifier.testTag("item_add")) { Icon(Icons.Outlined.Add, null); Spacer(Modifier.width(4.dp)); Text("Add item") }
            if (total > 0) {
                val left = total - assigned
                Text(when {
                    assigned == 0L -> "Payment ${Money.fmt(total)} · prices are optional"
                    left == 0L -> "Adds up to the payment ✓"
                    left > 0 -> "${Money.fmt(left)} not given to an item yet"
                    else -> "${Money.fmt(-left)} more than the payment"
                }, style = MaterialTheme.typography.labelMedium, color = if (left < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("items_left"))
            }
        }
    }, confirmButton = { TextButton(onClick = { onSave(parsed); onDismiss() }, modifier = Modifier.testTag("items_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

/** Settings → Saved UPI names: rename, change category, link a person, stop asking for items, merge or delete. */
@Composable
fun UpiNamesScreen(vm: AppVm) {
    val snack = LocalSnack.current
    val names by vm.upiNames.collectAsStateWithLifecycle()
    val cats by vm.catMap.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    var edit by rememberSaveable(stateSaver = recordSaver<UpiName>()) { mutableStateOf<UpiName?>(null) }
    var merge by rememberSaveable(stateSaver = recordSaver<UpiName>()) { mutableStateOf<UpiName?>(null) }
    val shown = names.filter { it.display.isNotBlank() }
    val skipped = names.filter { it.display.isBlank() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Saved UPI names") }, contentWindowInsets = WindowInsets(0)) { pad ->
        if (names.isEmpty()) Box(Modifier.padding(pad).fillMaxSize(), contentAlignment = Alignment.Center) {
            EmptyState(Icons.Outlined.Badge, "No saved names yet", "When a payment SMS shows a UPI ID, tap \"Name it\" once — every later payment to it shows your name and category.")
        } else LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("upi_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (shown.isNotEmpty()) item { BkCard(Modifier.padding(16.dp, 8.dp).fillMaxWidth()) {
                shown.forEach { n ->
                    ListRow(n.display, listOfNotNull(if (n.isPerson) "Person" else "Shop", n.categoryId?.let { cats[it]?.name }, n.key.substringAfter(':'),
                        if (!n.askItems) "no item question" else null).joinToString(" · "), Modifier.testTag("upi_${n.display}"),
                        leading = { Initials(n.display) }, trailing = { Chev() }, onClick = { edit = n })
                }
            } }
            if (skipped.isNotEmpty()) {
                item { SectionLabel("Not named (won't ask again)") }
                item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    skipped.forEach { n -> ListRow(n.key.substringAfter(':'), "Tap to name it", leading = { Icon(Icons.Outlined.QuestionMark, null) }, onClick = { edit = n }) }
                } }
            }
        }
    }
    edit?.let { n ->
        var name by rememberSaveable(n) { mutableStateOf(n.display) }
        var person by rememberSaveable(n) { mutableStateOf(n.isPerson) }
        var catId by rememberSaveable(n) { mutableStateOf(n.categoryId) }
        var ask by rememberSaveable(n) { mutableStateOf(n.askItems) }
        var pid by rememberSaveable(n) { mutableStateOf(n.personId) }
        AlertDialog(onDismissRequest = { edit = null }, title = { Text(if (n.display.isBlank()) "Name this UPI ID" else "Edit name") }, text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(n.key.substringAfter(':'), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextIn(name, { name = it }, "Name", tag = "upi_edit_name")
                Segments(listOf("Shop", "Person"), if (person) 1 else 0, { person = it == 1 })
                Text("Usual category", style = MaterialTheme.typography.labelMedium)
                Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    cats.values.filter { !it.archived }.sortedWith(compareBy({ it.kind }, { it.sort })).forEach { c -> Pill(c.name, catId == c.id, { catId = if (catId == c.id) null else c.id }) }
                }
                if (person && people.isNotEmpty()) {
                    Text("Udhar person", style = MaterialTheme.typography.labelMedium)
                    Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        people.forEach { p -> Pill(p.name, pid == p.id, { pid = if (pid == p.id) null else p.id }) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ask \"What did you buy?\"", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    Switch(ask, { ask = it }, Modifier.testTag("upi_ask"))
                }
                if (n.display.isNotBlank() && shown.size > 1) TextButton(onClick = { merge = n; edit = null }) { Text("Same place as another name? Merge…") }
            }
        }, confirmButton = { TextButton(enabled = name.isNotBlank(), onClick = {
            vm.launch { vm.repo.nameUpi(n.copy(display = name.trim(), isPerson = person, categoryId = catId, askItems = ask, personId = if (person) pid else null)) }
            edit = null
        }, modifier = Modifier.testTag("upi_edit_ok")) { Text("Save") } }, dismissButton = { Row {
            TextButton(onClick = { vm.launch { vm.repo.deleteUpiName(n) }; snack.show("Name removed", "Undo") { vm.launch { vm.repo.nameUpi(n) } }; edit = null }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            TextButton(onClick = { edit = null }) { Text("Cancel") }
        } })
    }
    merge?.let { n ->
        PickSheet("Merge ${n.display} into", shown.filter { it.key != n.key }, { it.display }, { it.key.substringAfter(':') }, onPick = { t ->
            vm.launch { vm.repo.mergeUpiName(n, t); snack.show("${n.display} now shows as ${t.display}") }
        }, onDismiss = { merge = null })
    }
    @Suppress("UNUSED_VARIABLE") val b = LocalBk
}
