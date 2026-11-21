@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import kotlinx.coroutines.launch
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import java.io.File
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneOffset

private val UPI = listOf("GPay", "PhonePe", "Paytm", "BHIM", "CRED", "Amazon Pay")
private val RULES = listOf("Never" to null, "Daily" to "DAILY", "Weekly" to "WEEKLY", "Monthly" to "MONTHLY", "Yearly" to "YEARLY")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryScreen(vm: AppVm, initialType: String, editId: Long, smsId: Long) {
    val nav = LocalNav.current
    val snack = LocalSnack.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    val focus = LocalFocusManager.current
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val categories by vm.categories.collectAsStateWithLifecycle()
    val merchants by vm.merchants.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()

    var type by rememberSaveable { mutableStateOf(if (initialType == EType.INCOME) EType.INCOME else EType.EXPENSE) }
    var step by rememberSaveable { mutableIntStateOf(1) }
    var expr by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var catId by rememberSaveable { mutableLongStateOf(0L) }
    var accId by rememberSaveable { mutableLongStateOf(0L) }
    var upi by rememberSaveable { mutableStateOf("") }
    var time by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var note by rememberSaveable { mutableStateOf("") }
    var tags by rememberSaveable { mutableStateOf("") }
    var files by rememberSaveable { mutableStateOf(listOf<String>()) }
    var splits by rememberSaveable { mutableStateOf(listOf<String>()) }   // "personId:paise"
    var splitMode by rememberSaveable { mutableStateOf("eq1") }            // eq1 = equal incl. me, eq0 = equal without me, amt = by amounts
    var rule by rememberSaveable { mutableStateOf<String?>(null) }
    var loadedOnce by rememberSaveable { mutableStateOf(false) }
    var saving by rememberSaveable { mutableStateOf(false) }
    var existing by remember { mutableStateOf<Entry?>(null) }
    var smsItem by remember { mutableStateOf<SmsItem?>(null) }

    LaunchedEffect(editId, smsId) {
        if (editId > 0) vm.repo.dao.entry(editId)?.let { e ->
            existing = e
            if (!loadedOnce) {
                type = e.type; expr = Money.input(e.amountPaise); merchant = e.merchant.orEmpty(); catId = e.categoryId ?: 0; accId = e.accountId
                upi = e.upiApp.orEmpty(); time = e.time; note = e.note.orEmpty(); tags = Ledger.tags(e.tags).joinToString(" ") { "#$it" }
                val old = vm.repo.dao.udharOfEntry(e.id).map { it.personId to it.amountPaise }
                splits = old.map { "${it.first}:${it.second}" }
                splitMode = when (old) { Ledger.splitShares(e.amountPaise, old.map { it.first }, true) -> "eq1"; Ledger.splitShares(e.amountPaise, old.map { it.first }, false) -> "eq0"; else -> "amt" }
                step = 3; loadedOnce = true
            }
        }
        if (smsId > 0) vm.repo.dao.smsItem(smsId)?.let { m ->
            smsItem = m
            if (!loadedOnce) {
                type = if (m.debit) EType.EXPENSE else EType.INCOME; expr = if (m.amountPaise > 0) Money.input(m.amountPaise) else ""
                merchant = m.merchant; catId = m.categoryId ?: 0; accId = m.accountId ?: 0; upi = m.upiApp; time = m.receivedAt
                step = if (m.amountPaise > 0) (if (catId > 0) 3 else 2) else 1; loadedOnce = true
            }
        }
        if (editId == 0L && smsId == 0L && !loadedOnce) {
            accId = s.lastAccountId.takeIf { id -> accounts.isEmpty() || accounts.any { it.id == id && !it.archived } } ?: 0L
            loadedOnce = true
        }
    }

    val amount = Calc.eval(expr) ?: 0L
    val acc = accounts.firstOrNull { it.id == accId }
    val cats = categories.filter { it.kind == (if (type == EType.INCOME) "INCOME" else "EXPENSE") && !it.archived }
    val cat = categories.firstOrNull { it.id == catId }

    var askDiscard by rememberSaveable { mutableStateOf(false) }
    val dirty = existing?.let { e ->
        amount != e.amountPaise || merchant != e.merchant.orEmpty() || catId != (e.categoryId ?: 0L) || accId != e.accountId ||
            note != e.note.orEmpty() || time != e.time || files.isNotEmpty()
    } ?: if (smsId > 0) false else (expr.isNotBlank() || merchant.isNotBlank() || note.isNotBlank() || tags.isNotBlank() || files.isNotEmpty())
    fun close() { if (dirty && !saving) askDiscard = true else nav.popBackStack() }
    BackHandler(step > 1 && editId == 0L && smsId == 0L) { step-- }
    BackHandler(!(step > 1 && editId == 0L && smsId == 0L) && dirty) { askDiscard = true }
    if (askDiscard) ConfirmDialog(if (existing != null) "Discard your changes?" else "Discard this entry?", "What you typed will be lost.", "Discard",
        onConfirm = { nav.popBackStack() }, onDismiss = { askDiscard = false })

    fun save() {
        if (amount <= 0 || accId == 0L || saving) return
        saving = true
        val e = (existing ?: Entry(type = type, amountPaise = 0, accountId = 0)).copy(
            type = type, amountPaise = amount, accountId = accId, categoryId = catId.takeIf { it > 0 }, merchant = merchant.trim().ifBlank { null },
            upiApp = upi.ifBlank { null }, note = note.trim().ifBlank { null }, tags = Ledger.tags(tags).joinToString(","), time = time,
            smsKey = existing?.smsKey ?: smsItem?.key,
        )
        val chosen = splits.mapNotNull { t -> t.split(':').takeIf { it.size == 2 }?.let { it[0].toLong() to it[1].toLong() } }
        // equal splits follow the saved amount, so editing the amount re-shares it
        val sp = if (splitMode == "amt") chosen else Ledger.splitShares(amount, chosen.map { it.first }, splitMode == "eq1")
        vm.launch {
            val id = vm.repo.saveEntry(Repo.SaveRequest(e, files.map { Uri.parse(it) }, sp, rule))
            smsItem?.let { vm.repo.linkSms(it, id, accId) }
            Diagnostics.count(ctx, if (editId > 0) "entry_edited" else "entry_added")
            val failed = vm.repo.lastAttachFailures
            snack.show(if (failed > 0) "Saved, but $failed ${if (failed == 1) "file" else "files"} couldn't be attached. Try adding again."
                else if (editId > 0) "Entry updated" else "${if (type == EType.INCOME) "Income" else "Expense"} saved · ${Money.fmt(amount)}")
            nav.popBackStack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Text(when {
                        editId > 0 -> "Edit entry"; smsId > 0 -> "Save from SMS"
                        step == 1 -> "Add entry"
                        else -> "${if (type == EType.INCOME) "Income" else "Expense"} · ${Money.fmt(amount)}" + if (step == 3 && cat != null) " · ${cat.name}" else ""
                    }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = { if (step > 1 && editId == 0L && smsId == 0L) step-- else close() }, modifier = Modifier.testTag("back")) {
                        Icon(if (step == 1 || editId > 0 || smsId > 0) Icons.Outlined.Close else Icons.Outlined.ArrowBack, if (step == 1) "Close" else "Back")
                    }
                },
                actions = { if (editId == 0L && smsId == 0L) Text("Step $step of 3", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 16.dp)) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                windowInsets = WindowInsets.statusBars,
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize().widthIn(max = 720.dp)) {
            StepBar(step)
            when (step) {
                1 -> AmountStep(type, { type = it; catId = 0 }, expr, { expr = it }, merchant, { merchant = it }, merchants,
                    onPickMerchant = { m ->
                        merchant = m.display
                        m.categoryId?.let { id -> if (categories.any { it.id == id && it.kind == (if (type == EType.INCOME) "INCOME" else "EXPENSE") }) catId = id }
                        m.accountId?.let { id -> if (accounts.any { it.id == id && !it.archived }) accId = id }
                    },
                    onNext = {
                        focus.clearFocus()
                        if (amount > 0) step = if (catId > 0 && accId > 0) 3 else 2
                    }, amount = amount,
                    onTransfer = { kind -> nav.popBackStack(); nav.go("transfer/$kind") })
                2 -> CategoryStep(cats, catId) { catId = it; step = 3 }
                else -> DetailsStep(
                    vm, type, accounts.filter { !it.archived || it.id == accId }, accId, { accId = it; if (accounts.firstOrNull { a -> a.id == it }?.type != AccType.BANK) upi = "" },
                    upi, { upi = it }, time, { time = it }, note, { note = it }, tags, { tags = it }, files, { files = it },
                    existing?.id ?: 0L, splits, { l, m -> splits = l; splitMode = m }, splitMode, people, amount, rule, { rule = it }, editId == 0L,
                    amountText = expr, onAmount = { expr = it }, merchant = merchant, onMerchant = { merchant = it },
                    cat = cat, onChangeCat = { step = 2 }, smsBody = smsItem?.body,
                    onSave = { save() }, saving = saving,
                )
            }
        }
    }
}

@Composable
private fun AmountStep(
    type: String, onType: (String) -> Unit, expr: String, onExpr: (String) -> Unit, merchant: String, onMerchant: (String) -> Unit,
    merchants: List<Merchant>, onPickMerchant: (Merchant) -> Unit, onNext: () -> Unit, amount: Long, onTransfer: (String) -> Unit,
) {
    val bk = LocalBk.current
    // short screens (phone landscape) put the keypad beside the amount so Next is never cut off
    val short = androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp < 480
    val top: @Composable ColumnScope.() -> Unit = {
        Segments(listOf("Expense", "Income"), if (type == EType.INCOME) 1 else 0, { onType(if (it == 1) EType.INCOME else EType.EXPENSE) },
            colors = listOf(bk.expense, bk.income), modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            val shown = if (expr.isBlank()) "₹0" else if (expr.any { it in "+−×÷" }) expr else "₹" + expr
            Text(shown, style = if (shown.length > 11) MaterialTheme.typography.headlineLarge else MaterialTheme.typography.displayMedium,
                textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp, start = 16.dp, end = 16.dp).testTag("amount_display")
                    .semantics { liveRegion = LiveRegionMode.Polite })
            if (expr.any { it in "+−×÷" }) Text("= " + Money.fmt(amount), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            var showSug by rememberSaveable { mutableStateOf(false) }
            OutlinedTextField(merchant, { onMerchant(it); showSug = true }, label = { Text(if (type == EType.INCOME) "From (optional)" else "Shop or person (optional)") },
                leadingIcon = { Icon(Icons.Outlined.Storefront, null) }, singleLine = true,
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words, imeAction = androidx.compose.ui.text.input.ImeAction.Done),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).testTag("f_merchant"))
            val sug = merchants.filter { it.kind == type && merchant.isNotBlank() && it.display.contains(merchant.trim(), true) && !it.display.equals(merchant.trim(), true) }.take(4)
            val topM = if (merchant.isBlank()) merchants.filter { it.kind == type }.take(6) else sug
            if (topM.isNotEmpty() && (showSug || merchant.isBlank())) {
                Row(Modifier.fillMaxWidth().horizontalScrollSafe().padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    topM.forEach { m -> AssistChip(onClick = { onPickMerchant(m); showSug = false }, label = { Text(m.display, maxLines = 1) }, modifier = Modifier.testTag("sug_${m.display}")) }
                }
            }
            if (type == EType.EXPENSE) Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedPillButton("Pay card bill", Icons.Outlined.CreditCard, { onTransfer("paybill") })
                OutlinedPillButton("Withdraw cash", Icons.Outlined.LocalAtm, { onTransfer("withdraw") })
            }
        }
    }
    val pad: @Composable ColumnScope.() -> Unit = {
        Keypad(expr, onExpr, compact = short)
        Button(onClick = onNext, enabled = amount > 0, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).testTag("next")) { Text("Next") }
    }
    // a physical keyboard types the amount straight away (Enter goes to the next step)
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val keys = Modifier.onKeyEvent { e ->
        if (e.type != androidx.compose.ui.input.key.KeyEventType.KeyDown) return@onKeyEvent false
        val code = e.nativeKeyEvent.keyCode
        if ((code == android.view.KeyEvent.KEYCODE_ENTER || code == android.view.KeyEvent.KEYCODE_NUMPAD_ENTER) && amount > 0) { onNext(); return@onKeyEvent true }
        keyFromKeyboard(code)?.let { onExpr(keyApply(expr, it)); true } ?: false
    }
    // a tiny focus holder (hidden from TalkBack) so key presses reach this screen without a text field
    val holder: @Composable () -> Unit = { Box(Modifier.size(1.dp).focusRequester(focus).focusable().clearAndSetSemantics { }) }
    if (short) Row(Modifier.fillMaxSize().then(keys)) {
        holder()
        Column(Modifier.weight(1f).fillMaxHeight(), content = top)
        Column(Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()).padding(top = 8.dp), content = pad)
    } else Column(Modifier.fillMaxSize().then(keys)) { holder(); top(); pad() }
}

@Composable
fun Modifier.horizontalScrollSafe(): Modifier = this.horizontalScroll(rememberScrollState())

/** What one keypad key (or keyboard key) does to the amount expression. */
fun keyApply(expr: String, k: String): String = when (k) {
    "⌫" -> expr.dropLast(1)
    "=" -> Calc.eval(expr)?.let { Money.input(it) } ?: expr
    "+", "−" -> if (expr.isEmpty() || expr.last() in "+−") expr else expr + k
    "." -> { val last = expr.split('+', '−').last(); if (last.contains('.')) expr else expr + (if (last.isEmpty()) "0." else ".") }
    else -> { val last = expr.split('+', '−').last(); if (last.substringAfter('.', "").length >= 2 && last.contains('.') || expr.length > 14) expr else expr + k }
}

/** Physical keyboard (Chromebook, tablet keyboard): digits, . + - Backspace, Enter = Next. */
fun keyFromKeyboard(code: Int): String? = when (code) {
    in android.view.KeyEvent.KEYCODE_0..android.view.KeyEvent.KEYCODE_9 -> (code - android.view.KeyEvent.KEYCODE_0).toString()
    in android.view.KeyEvent.KEYCODE_NUMPAD_0..android.view.KeyEvent.KEYCODE_NUMPAD_9 -> (code - android.view.KeyEvent.KEYCODE_NUMPAD_0).toString()
    android.view.KeyEvent.KEYCODE_PERIOD, android.view.KeyEvent.KEYCODE_NUMPAD_DOT -> "."
    android.view.KeyEvent.KEYCODE_PLUS, android.view.KeyEvent.KEYCODE_NUMPAD_ADD -> "+"
    android.view.KeyEvent.KEYCODE_MINUS, android.view.KeyEvent.KEYCODE_NUMPAD_SUBTRACT -> "−"
    android.view.KeyEvent.KEYCODE_DEL -> "⌫"
    android.view.KeyEvent.KEYCODE_EQUALS, android.view.KeyEvent.KEYCODE_NUMPAD_EQUALS -> "="
    else -> null
}

@Composable
fun Keypad(expr: String, onExpr: (String) -> Unit, compact: Boolean = false) {
    val keys = listOf("1", "2", "3", "⌫", "4", "5", "6", "+", "7", "8", "9", "−", ".", "0", "00", "=")
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        keys.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { k ->
                    val op = k in listOf("⌫", "+", "−", "=")
                    Box(
                        Modifier.weight(1f).heightIn(min = if (compact) 48.dp else 56.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (op) MaterialTheme.colorScheme.surfaceVariant else LocalBk.current.card)
                            .border(1.dp, LocalBk.current.cardBorder, RoundedCornerShape(14.dp))
                            .clickable { onExpr(keyApply(expr, k)) }.testTag("key_$k")
                            .semantics { contentDescription = when (k) { "⌫" -> "Delete digit"; "−" -> "Minus"; "+" -> "Plus"; "=" -> "Work out total"; "." -> "Point"; "00" -> "Double zero"; else -> k } },
                        contentAlignment = Alignment.Center,
                    ) { Text(k, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(vertical = 8.dp)) }
                }
            }
        }
    }
}

@Composable
private fun CategoryStep(cats: List<Category>, selected: Long, onPick: (Long) -> Unit) {
    val bk = LocalBk.current
    val nav = LocalNav.current
    Column {
        Text("What was it for?", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 10.dp))
        LazyVerticalGrid(GridCells.Adaptive(104.dp), Modifier.weight(1f, fill = false).padding(horizontal = 12.dp), contentPadding = PaddingValues(4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(cats, key = { it.id }) { c ->
                val sel = c.id == selected
                val (bg, fg) = bk.cat[c.colorIdx.mod(bk.cat.size)]
                Column(
                    Modifier.heightIn(min = 92.dp).clip(RoundedCornerShape(16.dp))
                        .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else bk.card)
                        .border(if (sel) 2.dp else 1.dp, if (sel) bk.saffron else bk.cardBorder, RoundedCornerShape(16.dp))
                        .clickable { onPick(c.id) }.padding(8.dp).testTag("cat_${c.name}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center,
                ) {
                    IconBadge(Ico.cat(c.icon), bg, fg, size = 38.dp)
                    Spacer(Modifier.height(6.dp))
                    Text(c.name, style = MaterialTheme.typography.labelLarge, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
        Text("Tap a category to continue", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(18.dp, 12.dp))
        TextButton(onClick = { nav.go("categories") }, modifier = Modifier.padding(start = 8.dp)) { Icon(Icons.Outlined.Edit, null); Spacer(Modifier.width(6.dp)); Text("Edit categories") }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun DetailsStep(
    vm: AppVm, type: String, accounts: List<Account>, accId: Long, onAcc: (Long) -> Unit, upi: String, onUpi: (String) -> Unit,
    time: Long, onTime: (Long) -> Unit, note: String, onNote: (String) -> Unit, tags: String, onTags: (String) -> Unit,
    files: List<String>, onFiles: (List<String>) -> Unit, entryId: Long, splits: List<String>, onSplits: (List<String>, String) -> Unit, splitMode: String,
    people: List<Person>, amount: Long, rule: String?, onRule: (String?) -> Unit, canRepeat: Boolean,
    amountText: String, onAmount: (String) -> Unit, merchant: String, onMerchant: (String) -> Unit, cat: Category?, onChangeCat: () -> Unit,
    smsBody: String?, onSave: () -> Unit, saving: Boolean,
) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    var showDate by rememberSaveable { mutableStateOf(false) }
    var showTime by rememberSaveable { mutableStateOf(false) }
    var showNote by rememberSaveable { mutableStateOf(false) }
    var showTags by rememberSaveable { mutableStateOf(false) }
    var showSplit by rememberSaveable { mutableStateOf(false) }
    var showRule by rememberSaveable { mutableStateOf(false) }
    var cameraUri by rememberSaveable { mutableStateOf<String?>(null) }
    val attFlow = remember(entryId) { if (entryId > 0) vm.repo.attachments(entryId) else kotlinx.coroutines.flow.flowOf(emptyList()) }
    val existingAtts by attFlow.collectAsState(emptyList())
    val scope = rememberCoroutineScope()

    val pickImages = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(5)) { l -> if (l.isNotEmpty()) onFiles(files + l.map { it.toString() }) }
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u -> if (u != null) onFiles(files + u.toString()) }
    val takePhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok -> val u = cameraUri; if (ok && u != null) onFiles(files + u); cameraUri = null }

    Column(Modifier.fillMaxSize()) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            if (smsBody != null) {
                Text(smsBody, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp).fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp))
            }
            // amount / merchant / category quick edits (edit + SMS modes)
            Row(Modifier.padding(16.dp, 12.dp, 16.dp, 0.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(amountText, { t -> onAmount(t.filter { it.isDigit() || it == '.' }) }, label = { Text("Amount") }, prefix = { Text("₹") }, singleLine = true,
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.Decimal),
                    modifier = Modifier.weight(1f).testTag("f_amount"))
                OutlinedTextField(merchant, onMerchant, label = { Text(if (type == EType.INCOME) "From" else "Shop / person") }, singleLine = true, modifier = Modifier.weight(1.3f).testTag("f_merchant2"))
            }
            ListRow(cat?.name ?: "Choose category", "Category", Modifier.testTag("row_category"), leading = { cat?.let { IconBadge(Ico.cat(it.icon), bk.cat[it.colorIdx.mod(bk.cat.size)].first, bk.cat[it.colorIdx.mod(bk.cat.size)].second) } ?: Icon(Icons.Outlined.Category, null) },
                trailing = { Text("Change", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge) }, onClick = onChangeCat)

            Text(if (type == EType.INCOME) "Received in" else "Paid from", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 18.dp, top = 8.dp, bottom = 8.dp))
            if (accounts.isEmpty()) {
                BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth(), padding = PaddingValues(16.dp)) {
                    Text("Add the card, bank account or cash this money is from.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { nav.go("account_edit/0?type=BANK") }) { Text("Add account") }
                        OutlinedButton(onClick = { vm.launch { onAcc(vm.repo.saveAccount(Account(type = AccType.CASH, name = "Cash"))) } }) { Text("Use cash") }
                    }
                }
            }
            FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp), maxItemsInEachRow = 2) {
                accounts.forEach { a ->
                    val sel = a.id == accId
                    Row(
                        Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (sel) MaterialTheme.colorScheme.secondaryContainer else bk.card)
                            .border(if (sel) 2.dp else 1.dp, if (sel) bk.saffron else bk.cardBorder, RoundedCornerShape(14.dp))
                            .clickable { onAcc(a.id) }.padding(10.dp).testTag("acc_${a.name}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Ico.account(a.type), null, tint = if (sel) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Column { Text(a.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(when (a.type) { AccType.CARD -> "Credit card"; AccType.CASH -> "Cash"; AccType.WALLET -> "Wallet"; else -> "Bank" }, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
                if (accounts.size % 2 == 1) Spacer(Modifier.weight(1f))
            }
            val acc = accounts.firstOrNull { it.id == accId }
            if (acc?.type == AccType.BANK) {
                Text("PAID VIA (UPI)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 18.dp, top = 14.dp, bottom = 4.dp))
                Row(Modifier.horizontalScrollSafe().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Direct / card", upi.isBlank(), { onUpi("") })
                    val linked = acc.upiApps.split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    (linked + UPI.filter { it !in linked }).forEach { u -> Pill(u, upi == u, { onUpi(u) }, modifier = Modifier.testTag("upi_$u")) }
                }
            }
            SectionLabel("Details")
            BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                ListRow(Dates.full(time), "Date & time", Modifier.testTag("row_date"), leading = { Icon(Icons.Outlined.CalendarToday, null) }, trailing = { Chev() }, onClick = { showDate = true })
                ListRow(note.ifBlank { "Add a note" }, "Note", Modifier.testTag("row_note"), leading = { Icon(Icons.Outlined.EditNote, null) }, trailing = { Chev() }, onClick = { showNote = true })
                ListRow(tags.ifBlank { "Add tags like #trip" }, "Tags", Modifier.testTag("row_tags"), leading = { Icon(Icons.Outlined.Sell, null) }, trailing = { Chev() }, onClick = { showTags = true })
                if (type == EType.EXPENSE) ListRow(
                    if (splits.isEmpty()) "Split with friends" else "Split with ${splits.size} ${if (splits.size == 1) "person" else "people"}",
                    if (splits.isEmpty()) "Shares go to Udhar" else splits.mapNotNull { t -> people.firstOrNull { it.id == t.substringBefore(':').toLongOrNull() }?.name }.joinToString(", "),
                    Modifier.testTag("row_split"), leading = { Icon(Icons.Outlined.Group, null) }, trailing = { Chev() }, onClick = { showSplit = true })
                if (canRepeat) ListRow(RULES.first { it.second == rule }.first, "Repeat (rent, EMI, subscriptions)", Modifier.testTag("row_repeat"), leading = { Icon(Icons.Outlined.Repeat, null) }, trailing = { Chev() }, onClick = { showRule = true })
            }
            SectionLabel("Attachments · ${existingAtts.size + files.size}")
            Row(Modifier.horizontalScrollSafe().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedPillButton("Camera", Icons.Outlined.PhotoCamera, {
                    val f = File(ctx.cacheDir, "share").apply { mkdirs() }.let { File(it, "receipt_${System.currentTimeMillis()}.jpg") }
                    val u = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    cameraUri = u.toString(); runCatching { takePhoto.launch(u) }
                }, tag = "camera")
                OutlinedPillButton("Gallery", Icons.Outlined.Image, { pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, tag = "gallery")
                OutlinedPillButton("PDF / file", Icons.Outlined.AttachFile, { pickFile.launch(arrayOf("application/pdf", "image/*", "*/*")) }, tag = "file")
            }
            if (existingAtts.isNotEmpty() || files.isNotEmpty()) {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    existingAtts.forEach { a ->
                        ListRow(a.displayName, if (a.mime.contains("pdf")) "PDF" else "Photo", leading = { Icon(if (a.mime.contains("pdf")) Icons.Outlined.PictureAsPdf else Icons.Outlined.Image, null) },
                            trailing = { IconButton(onClick = { scope.launchSafe { vm.repo.removeAttachment(a) } }) { Icon(Icons.Outlined.Close, "Remove ${a.displayName}") } })
                    }
                    files.forEachIndexed { i, f ->
                        val name = Uri.parse(f).lastPathSegment?.substringAfterLast('/') ?: "file"
                        ListRow(name, "Will be saved encrypted", leading = { Icon(Icons.Outlined.AttachFile, null) },
                            trailing = { IconButton(onClick = { onFiles(files.filterIndexed { j, _ -> j != i }) }) { Icon(Icons.Outlined.Close, "Remove $name") } })
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
        }
        Button(onClick = onSave, enabled = amount > 0 && accId > 0 && !saving, modifier = Modifier.fillMaxWidth().padding(16.dp).height(52.dp).testTag("save")) {
            Text(if (type == EType.INCOME) "Save income" else "Save expense")
        }
    }

    if (showDate) {
        val st = rememberDatePickerState(initialSelectedDateMillis = Dates.date(time).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest = { showDate = false }, confirmButton = {
            TextButton(onClick = {
                st.selectedDateMillis?.let { ms ->
                    val d = Instant.ofEpochMilli(ms).atZone(ZoneOffset.UTC).toLocalDate()
                    val t = Dates.dateTime(time).toLocalTime()
                    onTime(d.atTime(t).atZone(Dates.zone).toInstant().toEpochMilli())
                }
                showDate = false; showTime = true
            }) { Text("Next") }
        }, dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }) { DatePicker(st) }
    }
    if (showTime) {
        val lt = Dates.dateTime(time).toLocalTime()
        val st = rememberTimePickerState(lt.hour, lt.minute, false)
        AlertDialog(onDismissRequest = { showTime = false }, title = { Text("Time") }, text = { TimePicker(st) },
            confirmButton = { TextButton(onClick = { onTime(Dates.date(time).atTime(LocalTime.of(st.hour, st.minute)).atZone(Dates.zone).toInstant().toEpochMilli()); showTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showTime = false }) { Text("Cancel") } })
    }
    if (showNote) TextDialog("Note", note, { onNote(it) }, { showNote = false }, single = false)
    if (showTags) TextDialog("Tags", tags, { onTags(it) }, { showTags = false }, hint = "Example: #trip #office")
    if (showRule) PickSheet("Repeat", RULES, { it.first }, { if (it.second == null) "Only this time" else "Added automatically" }, onPick = { onRule(it.second) }, onDismiss = { showRule = false })
    if (showSplit) SplitDialog(vm, people, amount, splits, splitMode, onSplits) { showSplit = false }
}

private fun kotlinx.coroutines.CoroutineScope.launchSafe(block: suspend () -> Unit) = launch { runCatching { block() } }

@Composable
fun TextDialog(title: String, value: String, onDone: (String) -> Unit, onDismiss: () -> Unit, single: Boolean = true, hint: String? = null, kb: androidx.compose.ui.text.input.KeyboardType = androidx.compose.ui.text.input.KeyboardType.Text) {
    var t by rememberSaveable { mutableStateOf(value) }
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) },
        text = { OutlinedTextField(t, { t = it }, singleLine = single, minLines = if (single) 1 else 3, supportingText = hint?.let { { Text(it) } },
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = kb), modifier = Modifier.fillMaxWidth().testTag("dlg_field")) },
        confirmButton = { TextButton(onClick = { onDone(t.trim()); onDismiss() }, modifier = Modifier.testTag("dlg_ok")) { Text("Done") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
private fun SplitDialog(vm: AppVm, people: List<Person>, amount: Long, splits: List<String>, mode: String, onSplits: (List<String>, String) -> Unit, onDismiss: () -> Unit) {
    val start = splits.mapNotNull { t -> t.split(':').takeIf { it.size == 2 }?.let { it[0].toLongOrNull()?.let { id -> id to (it[1].toLongOrNull() ?: 0L) } } }
    var chosen by rememberSaveable { mutableStateOf(start.map { it.first }) }
    var byAmount by rememberSaveable { mutableStateOf(mode == "amt") }
    var includeMe by rememberSaveable { mutableStateOf(mode != "eq0") }
    var typed by rememberSaveable { mutableStateOf(start.associate { it.first.toString() to Money.input(it.second) }) }
    var newName by rememberSaveable { mutableStateOf("") }
    val equal = Ledger.splitShares(amount, chosen, includeMe).toMap()
    val custom = chosen.associateWith { Money.parse(typed[it.toString()].orEmpty()) ?: 0L }
    val customTotal = custom.values.sum()
    val tooMuch = byAmount && customTotal > amount
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Split ${Money.fmt(amount)}") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Text("Each person's share goes to Udhar as money they owe you.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Segments(listOf("Equally", "By amounts"), if (byAmount) 1 else 0, { byAmount = it == 1 })
            if (!byAmount) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().toggleable(includeMe, role = Role.Checkbox) { includeMe = it }) {
                Checkbox(includeMe, null); Text("Include my share")
            }
            people.forEach { p ->
                val on = p.id in chosen
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                    .toggleable(on, role = Role.Checkbox) { chosen = if (it) chosen + p.id else chosen - p.id }.testTag("split_${p.name}")) {
                    Checkbox(on, null)
                    Text(p.name, Modifier.weight(1f))
                    if (on && !byAmount) Text(Money.fmt(equal[p.id] ?: 0L), style = MaterialTheme.typography.labelLarge)
                }
                if (on && byAmount) MoneyField(typed[p.id.toString()].orEmpty(), { typed = typed + (p.id.toString() to it) }, "${p.name}'s share", Modifier.padding(start = 48.dp, bottom = 6.dp), tag = "share_${p.name}")
            }
            if (byAmount && chosen.isNotEmpty()) Text(
                if (tooMuch) "Shares add up to ${Money.fmt(customTotal)}, more than ${Money.fmt(amount)}" else "Your share: ${Money.fmt(amount - customTotal)}",
                color = if (tooMuch) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(newName, { newName = it }, label = { Text("New person") }, singleLine = true, modifier = Modifier.weight(1f).testTag("f_newperson"),
                    keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(capitalization = androidx.compose.ui.text.input.KeyboardCapitalization.Words))
                IconButton(onClick = { val n = newName.trim(); if (n.isNotEmpty()) vm.launch { val id = vm.repo.savePerson(Person(name = n)); chosen = chosen + id; newName = "" } }) { Icon(Icons.Outlined.PersonAdd, "Add person") }
            }
        }
    }, confirmButton = {
        TextButton(enabled = !tooMuch, onClick = {
            if (byAmount) onSplits(custom.filter { it.value > 0 }.map { "${it.key}:${it.value}" }, "amt")
            else onSplits(chosen.map { "$it:${equal[it] ?: 0L}" }, if (includeMe) "eq1" else "eq0")
            onDismiss()
        }, modifier = Modifier.testTag("split_ok")) { Text("Done") }
    }, dismissButton = { TextButton(onClick = { onSplits(emptyList(), "eq1"); onDismiss() }) { Text("No split") } })
}
