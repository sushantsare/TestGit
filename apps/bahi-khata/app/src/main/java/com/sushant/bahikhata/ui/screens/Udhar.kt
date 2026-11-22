@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk

@Composable
fun UdharScreen(vm: AppVm) {
    val nav = LocalNav.current
    val bk = LocalBk.current
    val wide = LocalWide.current
    val people by vm.people.collectAsStateWithLifecycle()
    val udhar by vm.udhar.collectAsStateWithLifecycle()
    var adding by rememberSaveable { mutableStateOf(false) }
    var q by rememberSaveable { mutableStateOf("") }
    var selected by rememberSaveable { mutableLongStateOf(0L) }
    val bal = people.associate { it.id to Ledger.personBalance(it, udhar) }
    val get = bal.values.filter { it > 0 }.sum(); val give = -bal.values.filter { it < 0 }.sum()
    val shown = people.filter { q.isBlank() || it.name.contains(q, true) }.sortedWith(compareByDescending<Person> { kotlin.math.abs(bal[it.id] ?: 0L) }.thenBy { it.name })
    val open: (Long) -> Unit = { id -> if (wide) selected = id else nav.go("person/$id") }

    val list: @Composable (Modifier) -> Unit = { mod ->
        if (people.isEmpty()) Box(mod, contentAlignment = Alignment.Center) {
            EmptyState(Icons.Outlined.Handshake, "No udhar yet", "Keep track of money you lend or borrow, person by person.", "Add a person", { adding = true })
        } else LazyColumn(mod.testTag("udhar_list"), contentPadding = PaddingValues(bottom = 96.dp)) {
            item {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    BkCard(Modifier.weight(1f), padding = PaddingValues(14.dp)) { Text("You'll get", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(get, style = MaterialTheme.typography.titleLarge, color = bk.income) }
                    BkCard(Modifier.weight(1f), padding = PaddingValues(14.dp)) { Text("You'll give", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant); MoneyText(give, style = MaterialTheme.typography.titleLarge, color = bk.expense) }
                }
            }
            if (people.size > 6) item { OutlinedTextField(q, { q = it }, placeholder = { Text("Search people") }, leadingIcon = { Icon(Icons.Outlined.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth().padding(16.dp, 12.dp, 16.dp, 0.dp)) }
            item { SectionLabel("People") }
            item {
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                    shown.forEach { p ->
                        val b = bal[p.id] ?: 0L
                        val last = udhar.firstOrNull { it.personId == p.id }
                        ListRow(p.name, last?.let { (it.note.ifBlank { if (it.gave) "You gave" else "You got" }) + " · " + Dates.short(it.time) } ?: if (b == 0L) "Settled" else "Opening balance",
                            Modifier.testTag("person_${p.name}"), leading = { Initials(p.name) },
                            trailing = { Column(horizontalAlignment = Alignment.End) {
                                MoneyText(kotlin.math.abs(b), color = if (b > 0) bk.income else if (b < 0) bk.expense else MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(if (b > 0) "they owe" else if (b < 0) "you owe" else "settled", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            } }, onClick = { open(p.id) })
                    }
                }
            }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { MainBar("Udhar") },
        floatingActionButton = { if (people.isNotEmpty()) ExtendedFloatingActionButton(onClick = { adding = true }, containerColor = bk.saffron, contentColor = bk.onSaffron, icon = { Icon(Icons.Outlined.PersonAdd, null) }, text = { Text("Add person") }, modifier = Modifier.testTag("add_person")) },
        contentWindowInsets = WindowInsets(0)) { pad ->
        if (wide) Row(Modifier.padding(pad).fillMaxSize()) {
            list(Modifier.weight(0.45f).fillMaxHeight())
            VerticalDivider(color = bk.cardBorder)
            Box(Modifier.weight(0.55f)) { if (selected > 0) PersonContent(vm, selected, embedded = true) { selected = 0 } else EmptyState(Icons.Outlined.TouchApp, "Pick a person", "Their balance and history show here.") }
        } else list(Modifier.padding(pad).fillMaxSize())
    }
    if (adding) PersonDialog(null, { p -> vm.launch { val id = vm.repo.savePerson(p); open(id) } }) { adding = false }
}

@Composable
fun PersonDialog(existing: Person?, onSave: (Person) -> Unit, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    var name by rememberSaveable { mutableStateOf(existing?.name.orEmpty()) }
    var phone by rememberSaveable { mutableStateOf(existing?.phone.orEmpty()) }
    var opening by rememberSaveable { mutableStateOf(existing?.let { if (it.openingPaise != 0L) Money.input(kotlin.math.abs(it.openingPaise)) else "" } ?: "") }
    var theyOwe by rememberSaveable { mutableStateOf((existing?.openingPaise ?: 0L) >= 0) }
    // pick from the phone-number list: the picked row is shared with us, so no contacts permission is needed
    val pick = rememberLauncherForActivityResult(object : androidx.activity.result.contract.ActivityResultContract<Unit, android.net.Uri?>() {
        override fun createIntent(context: android.content.Context, input: Unit) =
            android.content.Intent(android.content.Intent.ACTION_PICK).setType(ContactsContract.CommonDataKinds.Phone.CONTENT_TYPE)
        override fun parseResult(resultCode: Int, intent: android.content.Intent?): android.net.Uri? =
            intent?.data.takeIf { resultCode == android.app.Activity.RESULT_OK }
    }) { uri ->
        if (uri != null) runCatching {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)?.use { c ->
                if (c.moveToFirst()) { name = c.getString(0) ?: name; phone = c.getString(1)?.filter { it.isDigit() || it == '+' } ?: phone }
            }
        }.onFailure { snack.show("Couldn't read that contact. Type the name and number instead.") }
    }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (existing == null) "Add person" else "Edit person") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            TextIn(name, { name = it }, "Name *", tag = "pname")
            TextIn(phone, { v -> phone = v.filter { it.isDigit() || it == '+' || it == ' ' } }, "Phone (optional, for WhatsApp)", tag = "pphone", kb = KeyboardType.Phone)
            TextButton(onClick = { runCatching { pick.launch(Unit) } }) { Icon(Icons.Outlined.Contacts, null); Spacer(Modifier.width(6.dp)); Text("Pick from contacts") }
            if (existing == null) {
                MoneyField(opening, { opening = it }, "Existing udhar (optional)", tag = "popening")
                Segments(listOf("They owe me", "I owe them"), if (theyOwe) 0 else 1, { theyOwe = it == 0 })
            }
        }
    }, confirmButton = {
        TextButton(onClick = {
            if (name.isBlank()) return@TextButton
            val op = Money.parse(opening) ?: 0L
            onSave((existing ?: Person(name = "")).copy(name = name.trim(), phone = phone.trim(), openingPaise = if (existing != null) existing.openingPaise else if (theyOwe) op else -op))
            onDismiss()
        }, enabled = name.isNotBlank(), modifier = Modifier.testTag("person_save")) { Text("Save") }
    }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun PersonScreen(vm: AppVm, id: Long) { val nav = LocalNav.current; PersonContent(vm, id, embedded = false) { nav.popBackStack() } }

@Composable
fun PersonContent(vm: AppVm, id: Long, embedded: Boolean, onGone: () -> Unit) {
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    val people by vm.people.collectAsStateWithLifecycle()
    val udhar by vm.udhar.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    val p = people.firstOrNull { it.id == id }
    var entryDlg by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var editing by rememberSaveable { mutableStateOf(false) }
    var confirmDel by rememberSaveable { mutableStateOf(false) }
    var removeLine by rememberSaveable(stateSaver = recordSaver<Udhar>()) { mutableStateOf<Udhar?>(null) }
    var confirmSettle by rememberSaveable { mutableStateOf(false) }
    var langPick by rememberSaveable { mutableStateOf(false) }
    if (p == null) { MissingRecord(vm.loaded.collectAsStateWithLifecycle().value, "person"); return }
    val bal = Ledger.personBalance(p, udhar)
    val mine = udhar.filter { it.personId == p.id }

    fun whatsapp(lang: String) {
        val amt = Money.fmt(kotlin.math.abs(bal))
        val msg = if (lang == "hi") "नमस्ते ${p.name} जी, एक छोटा सा याद दिलाना था — मेरे $amt बाकी हैं। जब सुविधा हो तब लौटा दीजिएगा। धन्यवाद 🙏"
        else "Hi ${p.name}, just a gentle reminder about the $amt pending with you. Please return it when convenient. Thanks!"
        val digits = p.phone.filter { it.isDigit() }.let { if (it.length == 10) "91$it" else it }
        val uri = if (digits.isNotEmpty()) Uri.parse("https://wa.me/$digits?text=" + Uri.encode(msg)) else Uri.parse("https://wa.me/?text=" + Uri.encode(msg))
        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, msg), "Send reminder").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        Diagnostics.count(ctx, "whatsapp_reminder")
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = {
        val actions: @Composable RowScope.() -> Unit = {
            if (p.phone.isNotBlank()) Act(Icons.Outlined.Call, "Call ${p.name}", "call") { runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + p.phone)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            Act(Icons.Outlined.Edit, "Edit person", "edit_person") { editing = true }
            Act(Icons.Outlined.Delete, "Delete person", "delete_person") { confirmDel = true }
        }
        if (embedded) Row(Modifier.fillMaxWidth().padding(8.dp), horizontalArrangement = Arrangement.End, content = actions) else BackBar(p.name, actions = actions)
    }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            LazyColumn(Modifier.weight(1f).testTag("person_detail"), contentPadding = PaddingValues(bottom = 16.dp)) {
                item {
                    BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(18.dp)) {
                        Text(if (bal > 0) "${p.name} owes you" else if (bal < 0) "You owe ${p.name}" else "All settled with ${p.name}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        MoneyText(kotlin.math.abs(bal), style = MaterialTheme.typography.displaySmall, color = if (bal > 0) bk.income else if (bal < 0) bk.expense else MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (bal > 0) OutlinedPillButton("WhatsApp reminder", Icons.Outlined.Chat, { langPick = true }, tag = "whatsapp")
                            if (bal != 0L) OutlinedPillButton("Settle up", Icons.Outlined.DoneAll, { confirmSettle = true }, tag = "settle")
                        }
                    }
                }
                item { SectionLabel("History") }
                if (mine.isEmpty() && p.openingPaise == 0L) item { Text("Nothing yet. Use You gave / You got below.", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 4.dp)) }
                else item {
                    BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                        var running = bal
                        mine.forEach { u ->
                            val after = running
                            running -= if (u.gave) u.amountPaise else -u.amountPaise
                            ListRow(u.note.ifBlank { if (u.gave) "You gave" else "You got" }, Dates.dayMonthYear(Dates.date(u.time)) + " · " + (if (u.gave) "You gave" else "You got") + (u.accountId?.let { a -> accounts.firstOrNull { it.id == a }?.name?.let { " · $it" } } ?: ""),
                                Modifier.testTag("udhar_${u.id}"),
                                trailing = { Column(horizontalAlignment = Alignment.End) {
                                    MoneyText(u.amountPaise, color = if (u.gave) bk.income else bk.expense)
                                    Hideable { Text("Bal ${Money.fmt(after)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                                } },
                                onClick = {
                                    if (u.entryId != null && u.accountId == null) nav.go("entry/${u.entryId}")
                                    else removeLine = u   // asks first; undo is offered after
                                })
                        }
                        if (p.openingPaise != 0L) ListRow("Opening balance", if (p.openingPaise > 0) "They owed you" else "You owed them", trailing = { MoneyText(kotlin.math.abs(p.openingPaise)) })
                    }
                    Text("Tap a line to remove it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 6.dp))
                }
            }
            Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = { entryDlg = true }, colors = ButtonDefaults.buttonColors(containerColor = bk.expense, contentColor = MaterialTheme.colorScheme.surface), modifier = Modifier.weight(1f).height(52.dp).testTag("you_gave")) { Text("You gave") }
                Button(onClick = { entryDlg = false }, colors = ButtonDefaults.buttonColors(containerColor = bk.income, contentColor = MaterialTheme.colorScheme.surface), modifier = Modifier.weight(1f).height(52.dp).testTag("you_got")) { Text("You got") }
            }
        }
    }

    entryDlg?.let { gave -> UdharDialog(gave, p.name, accounts.filter { !it.archived && it.type != AccType.CARD }, { amt, acc, note ->
        vm.launch { vm.repo.addUdhar(p.id, gave, amt, acc, note); snack.show((if (gave) "You gave " else "You got ") + Money.fmt(amt)) }
    }) { entryDlg = null } }
    if (editing) PersonDialog(p, { vm.launch { vm.repo.savePerson(it) } }) { editing = false }
    removeLine?.let { u ->
        ConfirmDialog("Remove this line?", "${if (u.gave) "You gave" else "You got"} ${Money.fmt(u.amountPaise)}" + (if (u.note.isNotBlank()) " · ${u.note}" else "") + ". The balance is worked out again.", "Remove",
            onConfirm = { vm.launch { vm.repo.deleteUdhar(u); snack.show("Removed", "Undo") { vm.launch { vm.repo.addUdhar(u.personId, u.gave, u.amountPaise, u.accountId, u.note, u.time) } } } },
            onDismiss = { removeLine = null })
    }
    if (confirmDel) ConfirmDialog("Delete ${p.name}?", "Their udhar history is deleted too. Split expenses stay in your entries.", "Delete", onConfirm = { vm.launch { val b = vm.repo.deletePerson(p); snack.show("${p.name} deleted", "Undo") { vm.launch { vm.repo.restorePerson(b) } } }; onGone() }, onDismiss = { confirmDel = false })
    if (confirmSettle) ConfirmDialog("Settle up with ${p.name}?", "Adds a ${if (bal > 0) "You got" else "You gave"} entry of ${Money.fmt(kotlin.math.abs(bal))} so the balance becomes zero.", "Settle",
        onConfirm = { vm.launch { vm.repo.addUdhar(p.id, bal < 0, kotlin.math.abs(bal), null, "Settled up"); snack.show("Settled with ${p.name}") } }, onDismiss = { confirmSettle = false }, danger = false)
    if (langPick) PickSheet("Reminder language", listOf("en" to "English", "hi" to "हिंदी (Hindi)"), { it.second }, onPick = { vm.prefs.update { s2 -> s2.copy(reminderLang = it.first) }; whatsapp(it.first) }, onDismiss = { langPick = false })
    @Suppress("UNUSED_VARIABLE") val u2 = s
}

@Composable
private fun UdharDialog(gave: Boolean, name: String, accounts: List<Account>, onSave: (Long, Long?, String) -> Unit, onDismiss: () -> Unit) {
    var amt by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var acc by rememberSaveable { mutableStateOf<Long?>(null) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (gave) "You gave $name" else "You got from $name") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            MoneyField(amt, { amt = it }, "Amount *", tag = "uamount")
            TextIn(note, { note = it }, "Note (optional)", tag = "unote")
            Text(if (gave) "Money left from (optional)" else "Money came into (optional)", style = MaterialTheme.typography.labelLarge)
            Text("Pick an account to also update its balance. It isn't counted as spending or income.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Don't change", acc == null, { acc = null })
                accounts.forEach { a -> Pill(a.name, acc == a.id, { acc = a.id }) }
            }
        }
    }, confirmButton = { TextButton(enabled = (Money.parse(amt) ?: 0L) > 0, onClick = { Money.parse(amt)?.takeIf { it > 0 }?.let { onSave(it, acc, note.trim()); onDismiss() } }, modifier = Modifier.testTag("udhar_save")) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
