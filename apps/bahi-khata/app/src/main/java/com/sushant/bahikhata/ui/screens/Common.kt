@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.LocalNav
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackBar(title: String, close: Boolean = false, actions: @Composable RowScope.() -> Unit = {}) {
    val nav = LocalNav.current
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = { nav.popBackStack() }, modifier = Modifier.testTag("back")) { Icon(if (close) Icons.Outlined.Close else Icons.Outlined.ArrowBack, if (close) "Close" else "Back") } },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background, scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        windowInsets = WindowInsets.statusBars,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainBar(title: String, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
        windowInsets = WindowInsets.statusBars,
    )
}

@Composable
fun Act(icon: ImageVector, desc: String, tag: String = desc, onClick: () -> Unit) =
    IconButton(onClick = onClick, modifier = Modifier.testTag("act_$tag")) { Icon(icon, desc, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

fun accLabel(a: Account?): String = when {
    a == null -> "Unknown account"
    a.type == AccType.CARD -> a.name
    a.type == AccType.BANK -> a.name
    else -> a.name
}

fun entryTitle(e: Entry, cat: Category?, accs: Map<Long, Account>, people: Map<Long, Person> = emptyMap()): String = when (e.type) {
    EType.CARD_PAYMENT -> "Card bill paid · " + (accs[e.toAccountId ?: -1]?.name ?: "card")
    EType.CASH_WITHDRAWAL -> "Cash withdrawn"
    EType.ADJUSTMENT -> "Balance adjusted"
    EType.UDHAR_GAVE -> "Gave to " + (people[e.personId ?: -1]?.name ?: "someone")
    EType.UDHAR_GOT -> "Got back from " + (people[e.personId ?: -1]?.name ?: "someone")
    else -> e.merchant?.takeIf { it.isNotBlank() } ?: cat?.name ?: if (e.type == EType.INCOME) "Income" else "Expense"
}

fun entrySub(e: Entry, cat: Category?, accs: Map<Long, Account>): String {
    val acc = accs[e.accountId]?.name ?: "—"
    val via = e.upiApp?.takeIf { it.isNotBlank() }?.let { " via $it" } ?: ""
    return when (e.type) {
        EType.EXPENSE, EType.INCOME -> listOfNotNull(cat?.name.takeIf { !e.merchant.isNullOrBlank() }, "$acc$via").joinToString(" · ")
        EType.CARD_PAYMENT, EType.CASH_WITHDRAWAL -> "From $acc"
        else -> acc
    }
}

/** Sign for display: spending and money leaving is negative. */
fun signed(e: Entry): Long = when (e.type) {
    EType.EXPENSE, EType.UDHAR_GAVE -> -e.amountPaise
    EType.INCOME, EType.UDHAR_GOT -> e.amountPaise
    EType.ADJUSTMENT -> e.amountPaise
    else -> -e.amountPaise
}

@Composable
fun EntryRow(e: Entry, vm: com.sushant.bahikhata.ui.AppVm, onClick: () -> Unit, highlight: String = "") {
    val cats by vm.catMap.collectAsState(); val accs by vm.accMap.collectAsState(); val people by vm.personMap.collectAsState()
    val att by vm.attCounts.collectAsState()
    val cat = e.categoryId?.let { cats[it] }
    val bk = LocalBk.current
    val (bg, fg) = when (e.type) {
        EType.EXPENSE, EType.INCOME -> cat?.let { bk.cat[it.colorIdx.mod(bk.cat.size)] } ?: (MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface)
        else -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurface
    }
    val v = signed(e)
    ListRow(
        title = entryTitle(e, cat, accs, people),
        subtitle = entrySub(e, cat, accs) + (if ((att[e.id] ?: 0) > 0) " · 📎" else ""),
        modifier = Modifier.testTag("entry_${e.id}"),
        leading = { IconBadge(Ico.entry(e, cat), bg, fg) },
        trailing = {
            Column(horizontalAlignment = Alignment.End) {
                MoneyText(v, style = MaterialTheme.typography.titleSmall, sign = v > 0,
                    color = when (e.type) { EType.EXPENSE -> bk.expense; EType.INCOME -> bk.income; else -> MaterialTheme.colorScheme.onSurfaceVariant })
                Text(Dates.short(e.time), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        onClick = onClick,
    )
}

@Composable
fun MoneyField(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, tag: String = label) {
    OutlinedTextField(value, { t -> onChange(t.filter { it.isDigit() || it == '.' || it == ',' }) }, label = { Text(label) }, prefix = { Text("₹ ") },
        singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = modifier.fillMaxWidth().testTag("f_$tag"))
}

@Composable
fun TextIn(value: String, onChange: (String) -> Unit, label: String, modifier: Modifier = Modifier, tag: String = label, kb: KeyboardType = KeyboardType.Text, single: Boolean = true, supporting: String? = null, error: Boolean = false) {
    OutlinedTextField(value, onChange, label = { Text(label) }, singleLine = single, isError = error,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = kb), modifier = modifier.fillMaxWidth().testTag("f_$tag"))
}

/** Picker list in a bottom sheet. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> PickSheet(title: String, list: List<T>, label: (T) -> String, sub: (T) -> String? = { null }, icon: @Composable (T) -> Unit = {}, onPick: (T) -> Unit, onDismiss: () -> Unit, footer: (@Composable () -> Unit)? = null) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.background) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp))
        androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth().heightIn(max = 520.dp)) {
            items(list.size) { i ->
                val it = list[i]
                ListRow(label(it), sub(it), Modifier.testTag("pick_${label(it)}"), leading = { icon(it) }, onClick = { onPick(it); onDismiss() })
            }
            if (footer != null) item { footer() }
        }
        Spacer(Modifier.height(24.dp))
    }
}

fun shareFile(context: Context, file: File, mime: String, title: String = "Share") {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val i = Intent(Intent.ACTION_SEND).setType(mime).putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    context.startActivity(Intent.createChooser(i, title).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun viewFile(context: Context, file: File, mime: String) {
    val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
    val i = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(i) }.onFailure { shareFile(context, file, mime, "Open with") }
}

@Composable
fun Gap(h: Int) = Spacer(Modifier.height(h.dp))
