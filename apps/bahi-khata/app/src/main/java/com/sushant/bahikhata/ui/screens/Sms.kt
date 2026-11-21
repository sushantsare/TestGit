@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.App
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.sms.SmsParser
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import com.sushant.bahikhata.work.Notify
import kotlinx.coroutines.launch

@Composable
fun SmsInboxScreen(vm: AppVm) {
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    val pending by vm.smsPending.collectAsStateWithLifecycle()
    val look by vm.smsLook.collectAsStateWithLifecycle()
    val ignored by vm.smsIgnored.collectAsStateWithLifecycle()
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val cats by vm.catMap.collectAsStateWithLifecycle()
    val nets by vm.nets.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var chooseAcc by remember { mutableStateOf<SmsItem?>(null) }
    var teach by remember { mutableStateOf<SmsItem?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val app = ctx.applicationContext as App
    val accOk = accounts.filter { !it.archived }

    fun ok(item: SmsItem, accountId: Long) = vm.launch {
        vm.repo.acceptSms(item, accountId, item.categoryId, item.merchant)
        Notify.cancel(ctx, Notify.smsId(item))
        Diagnostics.count(ctx, "sms_ok")
        snack.show("Saved · ${Money.fmt(item.amountPaise)}", "Undo") {
            vm.launch { vm.repo.dao.smsItem(item.id)?.entryId?.let { id -> vm.repo.dao.entry(id)?.let { vm.repo.deleteEntry(it) } }; vm.repo.restoreSms(item) }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Check SMS entries") {
        Act(Icons.Outlined.Refresh, "Scan inbox now", "scan") {
            if (!app.sms.enabled()) { nav.go("sms_setup"); return@Act }
            scanning = true
            scope.launch { val st = app.sms.scanSince(null, notify = false); scanning = false; snack.show(if (st.parsed + st.look == 0) "No new money SMS" else "Found ${st.parsed + st.look} new") }
        }
    } }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (scanning) LinearProgressIndicator(Modifier.fillMaxWidth())
            Text("Read from your bank SMS on this phone. Nothing is saved until you OK it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 18.dp))
            if (!app.sms.enabled()) {
                Row(Modifier.padding(16.dp, 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("SMS reading is off", color = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelLarge)
                    TextButton(onClick = { nav.go("sms_setup") }) { Text("Turn on") }
                }
            }
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(16.dp, 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("Suggestions · ${pending.size}", tab == 0, { tab = 0 }, modifier = Modifier.testTag("tab_sugg"))
                Pill("Needs a look · ${look.size}", tab == 1, { tab = 1 }, modifier = Modifier.testTag("tab_look"))
                Pill("Missed something?", tab == 2, { tab = 2 }, modifier = Modifier.testTag("tab_missed"))
            }
            val list = when (tab) { 0 -> pending; 1 -> look; else -> ignored }
            if (tab == 0 && pending.size > 1 && pending.any { it.accountId != null && it.confidence >= 90 && it.duplicateOf == null }) {
                TextButton(onClick = {
                    val good = pending.filter { it.accountId != null && it.confidence >= 90 && it.duplicateOf == null }
                    vm.launch { good.forEach { vm.repo.acceptSms(it, it.accountId!!, it.categoryId, it.merchant) }; snack.show("Saved ${good.size} entries") }
                }, modifier = Modifier.padding(start = 8.dp).testTag("ok_all")) { Icon(Icons.Outlined.DoneAll, null); Spacer(Modifier.width(6.dp)); Text("OK all sure ones (${pending.count { it.accountId != null && it.confidence >= 90 && it.duplicateOf == null }})") }
            }
            if (list.isEmpty()) EmptyState(Icons.Outlined.Sms, when (tab) { 0 -> "All caught up"; 1 -> "Nothing needs a look"; else -> "Nothing ignored" },
                when (tab) { 0 -> "New bank SMS will show up here for one-tap saving."; 1 -> "Money SMS in a new format land here so nothing is missed."; else -> "SMS you ignored can be brought back from here." })
            else LazyColumn(contentPadding = PaddingValues(bottom = 32.dp), modifier = Modifier.testTag("sms_list")) {
                items(list, key = { it.id }) { item ->
                    SmsCard(item, accounts, cats, nets, tab,
                        onOk = { if (item.accountId != null) ok(item, item.accountId) else chooseAcc = item },
                        onEdit = { nav.go("add?sms=${item.id}") },
                        onIgnore = { vm.launch { vm.repo.ignoreSms(item); Notify.cancel(ctx, Notify.smsId(item)); snack.show("Ignored", "Undo") { vm.launch { vm.repo.restoreSms(item) } } } },
                        onTeach = { teach = item },
                        onRestore = { vm.launch { vm.repo.restoreSms(item); snack.show("Moved back to the list") } },
                        onAdjust = { acc, bal -> vm.launch { vm.repo.adjustBalance(acc, bal); vm.repo.dao.updateSms(item.copy(note = item.note.replace(Regex("BAL:\\d+"), "").trim(' ', '·'))); snack.show("${acc.name} balance now matches the bank") } })
                }
            }
        }
    }
    chooseAcc?.let { item ->
        PickSheet("Which account is this?", accOk, { it.name }, { typeLabel(it.type) + if (item.last4.isNotBlank()) " · will remember ••${item.last4}" else "" }, icon = { Icon(Ico.account(it.type), null) },
            onPick = { a -> ok(item, a.id) }, onDismiss = { chooseAcc = null },
            footer = { TextButton(onClick = { chooseAcc = null; nav.go("account_edit/0?type=BANK") }, modifier = Modifier.padding(16.dp)) { Text("+ Add a new account") } })
    }
    teach?.let { item -> TeachDialog(vm, item, accOk) { teach = null } }
    @Suppress("UNUSED_VARIABLE") val u = s
}

@Composable
private fun SmsCard(
    item: SmsItem, accounts: List<Account>, cats: Map<Long, Category>, nets: Map<Long, Long>, tab: Int,
    onOk: () -> Unit, onEdit: () -> Unit, onIgnore: () -> Unit, onTeach: () -> Unit, onRestore: () -> Unit, onAdjust: (Account, Long) -> Unit,
) {
    val bk = LocalBk.current
    var expanded by remember { mutableStateOf(false) }
    val acc = accounts.firstOrNull { it.id == item.accountId }
    BkCard(Modifier.padding(16.dp, 6.dp).fillMaxWidth().testTag("sms_${item.id}"), padding = PaddingValues(14.dp), onClick = { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(item.merchant.ifBlank { when (item.kind) { "ATM" -> "ATM cash withdrawal"; "CARD_PAYMENT" -> "Card bill payment"; "REFUND" -> "Refund"; else -> if (item.debit) "Payment" else "Money received" } },
                    style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(listOfNotNull(
                    item.categoryId?.let { cats[it]?.name },
                    acc?.name ?: item.last4.takeIf { it.isNotBlank() }?.let { "••$it · choose account" } ?: "choose account",
                    item.upiApp.takeIf { it.isNotBlank() },
                    Dates.short(item.receivedAt),
                ).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (item.amountPaise > 0) MoneyText(if (item.debit) -item.amountPaise else item.amountPaise, style = MaterialTheme.typography.titleMedium, sign = !item.debit,
                color = if (item.debit) bk.expense else bk.income)
        }
        val noteText = item.note.replace(Regex("BAL:\\d+"), "").trim(' ', '·')
        if (noteText.isNotBlank()) Text("⚠ $noteText", style = MaterialTheme.typography.labelMedium, color = bk.warn, modifier = Modifier.padding(top = 6.dp))
        Regex("BAL:(\\d+)").find(item.note)?.let { m ->
            val bal = m.groupValues[1].toLong()
            if (acc != null) Row(Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(bk.info).padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Bank shows ${Money.fmt(bal)}, app shows ${Money.fmt(nets[acc.id] ?: 0L)}", color = bk.onInfo, style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                TextButton(onClick = { onAdjust(acc, bal) }) { Text("Match") }
            }
        }
        Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = if (expanded) 20 else 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(8.dp))
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            when (tab) {
                0 -> {
                    TextButton(onClick = onIgnore, modifier = Modifier.testTag("ignore_${item.id}")) { Text("Ignore") }
                    TextButton(onClick = onEdit, modifier = Modifier.testTag("edit_${item.id}")) { Text("Edit") }
                    FilledTonalButton(onClick = onOk, modifier = Modifier.testTag("ok_${item.id}")) { Icon(Icons.Outlined.Check, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("OK, save") }
                }
                1 -> {
                    TextButton(onClick = onIgnore) { Text("Not money") }
                    FilledTonalButton(onClick = onTeach, modifier = Modifier.testTag("teach_${item.id}")) { Icon(Icons.Outlined.School, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Fix & teach") }
                }
                else -> FilledTonalButton(onClick = onRestore, modifier = Modifier.testTag("restore_${item.id}")) { Icon(Icons.Outlined.Restore, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Bring back") }
            }
        }
    }
}

/** He fixes the fields once; the format is remembered (sender + shape) and the entry is saved. */
@Composable
private fun TeachDialog(vm: AppVm, item: SmsItem, accounts: List<Account>, onDismiss: () -> Unit) {
    val snack = LocalSnack.current
    val categories by vm.categories.collectAsStateWithLifecycle()
    var amt by remember { mutableStateOf(if (item.amountPaise > 0) Money.input(item.amountPaise) else "") }
    var debit by remember { mutableStateOf(item.debit) }
    var accId by remember { mutableStateOf(item.accountId) }
    var merchant by remember { mutableStateOf(item.merchant) }
    var catId by remember { mutableStateOf(item.categoryId) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Fix & teach") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(item.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Segments(listOf("Money out", "Money in"), if (debit) 0 else 1, { debit = it == 0 })
            MoneyField(amt, { amt = it }, "Amount", tag = "tamount")
            TextIn(merchant, { merchant = it }, "Shop / person", tag = "tmerchant")
            Text("Account", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { accounts.forEach { a -> Pill(a.name, accId == a.id, { accId = a.id }) } }
            Text("Category", style = MaterialTheme.typography.labelLarge)
            Row(Modifier.horizontalScrollSafe(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categories.filter { it.kind == (if (debit) "EXPENSE" else "INCOME") && !it.archived }.forEach { c -> Pill(c.name, catId == c.id, { catId = c.id }) }
            }
        }
    }, confirmButton = { TextButton(onClick = {
        val a = Money.parse(amt) ?: 0L
        val acc = accId
        if (a <= 0 || acc == null) return@TextButton
        vm.launch {
            vm.repo.teach(Learned(senderKey = com.sushant.bahikhata.sms.Senders.split(item.sender).first, shape = item.body, debit = debit, kind = if (debit) "DEBIT" else "CREDIT",
                accountId = acc, merchant = merchant, categoryId = catId))
            vm.repo.acceptSms(item.copy(debit = debit), acc, catId, merchant, a, debit, if (debit) "DEBIT" else "CREDIT")
            snack.show("Saved — this SMS format will be read automatically next time")
        }
        onDismiss()
    }, modifier = Modifier.testTag("teach_ok")) { Text("Save & teach") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
    @Suppress("UNUSED_VARIABLE") val p = SmsParser
}

/** Explains SMS reading first, then asks for the permission (never on launch). */
@Composable
fun SmsSetupScreen(vm: AppVm) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val snack = LocalSnack.current
    val app = ctx.applicationContext as App
    var denied by remember { mutableStateOf(false) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        app.prefs.askedSms = true
        if (res[Manifest.permission.READ_SMS] == true) {
            vm.prefs.update { it.copy(smsOn = true) }
            app.sms.startObserver()
            working = true
            scope.launch {
                val st = app.sms.scanSince(System.currentTimeMillis() - 90L * 86_400_000L, notify = false)
                working = false
                snack.show("Found ${st.parsed} entries and ${st.look} to look at from the last 90 days")
                nav.popBackStack(); nav.go("sms")
            }
        } else denied = true
    }
    val permanentlyDenied = denied && (ctx as? Activity)?.shouldShowRequestPermissionRationale(Manifest.permission.READ_SMS) == false
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("SMS suggestions", close = true) }, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(22.dp).widthIn(max = 640.dp)) {
                IconBadge(Icons.Outlined.Sms, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer, size = 72.dp, iconSize = 38.dp)
                Gap(20)
                Text("Let Bahi Khata read bank SMS?", style = MaterialTheme.typography.headlineSmall)
                Gap(10)
                Text("It looks only at messages from banks, cards and UPI apps and turns them into suggestions. SMS never leave your phone, and nothing is saved until you OK it.",
                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Gap(16)
                listOf("Only bank, card and UPI messages", "Read and processed on this phone", "Card bills and due dates update by themselves", "You can turn it off any time in Settings").forEach {
                    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CheckCircle, null, tint = LocalBk.current.income); Spacer(Modifier.width(12.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
                }
                if (working) { Gap(16); LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Reading the last 90 days…", style = MaterialTheme.typography.bodySmall) }
                if (denied) {
                    Gap(16)
                    Text(if (permanentlyDenied) "SMS access is blocked for Bahi Khata. Turn on \"SMS\" in app settings to use this." else "No problem — you can add entries by hand. Ask again any time.",
                        color = LocalBk.current.warn, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Column(Modifier.padding(16.dp)) {
                if (permanentlyDenied) Button(onClick = { ctx.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) },
                    modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Open settings") }
                else Button(onClick = {
                    val perms = mutableListOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)
                    if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) perms += Manifest.permission.POST_NOTIFICATIONS
                    launcher.launch(perms.toTypedArray())
                }, enabled = !working, modifier = Modifier.fillMaxWidth().height(52.dp).testTag("allow_sms")) { Text("Allow SMS access") }
                TextButton(onClick = { nav.popBackStack() }, modifier = Modifier.fillMaxWidth()) { Text("Not now — I'll add manually") }
            }
        }
    }
}
