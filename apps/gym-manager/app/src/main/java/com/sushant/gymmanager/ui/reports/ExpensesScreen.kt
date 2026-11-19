package com.sushant.gymmanager.ui.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.gymmanager.data.local.ExpenseCategory
import com.sushant.gymmanager.data.local.ExpenseEntity
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.AppDatePickerDialog
import com.sushant.gymmanager.ui.components.EmptyState
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.rememberGymApp
import com.sushant.gymmanager.ui.theme.StatusRed
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.Money
import kotlinx.coroutines.launch
import java.time.LocalDate

fun categoryIcon(category: String): ImageVector = when (category) {
    "Rent" -> Icons.Default.Home
    "Electricity" -> Icons.Default.Bolt
    "Salary" -> Icons.Default.Groups
    "Equipment" -> Icons.Default.FitnessCenter
    "Maintenance" -> Icons.Default.Build
    else -> Icons.Default.MoreHoriz
}

@Composable
fun ExpensesScreen(onOpenDrawer: () -> Unit) {
    val app = rememberGymApp()
    val lang = currentLang()
    val list by app.repository.allExpenses.collectAsStateWithLifecycle(initialValue = null)
    var editing by remember { mutableStateOf<ExpenseEntity?>(null) }
    val thisMonth = LocalDate.now().toString().take(7)

    Scaffold(
        topBar = { GymTopBar(t("Expenses"), t("Rent, bills, salaries and more"), onMenu = onOpenDrawer) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { editing = ExpenseEntity(date = DateUtils.today(), category = "Rent", amount = 0.0) },
                icon = { Icon(Icons.Default.Add, null) },
                text = { Text(t("Add Expense")) },
                modifier = Modifier.testTag("fab_expense")
            )
        }
    ) { padding ->
        val items = list
        when {
            items == null -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            items.isEmpty() -> Box(Modifier.fillMaxSize().padding(padding)) {
                EmptyState(Icons.AutoMirrored.Filled.ReceiptLong, t("No expenses yet"), t("Record rent, electricity, trainer salaries and other costs to see your real profit in Reports."))
            }
            else -> LazyColumn(
                Modifier.fillMaxSize().padding(padding).testTag("expense_list"),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(t("THIS MONTH"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(items.filter { it.date.startsWith(thisMonth) }.sumOf { it.amount }), style = MaterialTheme.typography.headlineSmall, color = StatusRed)
                    }
                }
                items.groupBy { it.date.take(7) }.forEach { (month, rows) ->
                    item(key = "m_$month") {
                        Row(Modifier.padding(top = 10.dp, bottom = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                DateUtils.formatMonth(LocalDate.parse("$month-01")),
                                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            Text(Money.format(rows.sumOf { it.amount }), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                    items(rows, key = { it.id }) { e ->
                        AppCard(Modifier.fillMaxWidth(), onClick = { editing = e }, contentPadding = PaddingValues(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
                                    contentAlignment = Alignment.Center
                                ) { Icon(categoryIcon(e.category), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp)) }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(I18n.categoryLabel(lang, e.category), style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        listOfNotNull(DateUtils.formatShort(e.date), e.note).joinToString(" • "),
                                        style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1, overflow = TextOverflow.Ellipsis
                                    )
                                }
                                Text(Money.format(e.amount), style = MaterialTheme.typography.titleSmall, color = StatusRed)
                            }
                        }
                    }
                }
            }
        }
    }

    editing?.let { e -> ExpenseDialog(e, onDismiss = { editing = null }) }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseDialog(initial: ExpenseEntity, onDismiss: () -> Unit) {
    val app = rememberGymApp()
    val lang = currentLang()
    var date by remember { mutableStateOf(initial.date) }
    var category by remember { mutableStateOf(initial.category) }
    var amount by remember { mutableStateOf(if (initial.amount > 0) Money.format(initial.amount).drop(1).replace(",", "") else "") }
    var note by remember { mutableStateOf(initial.note.orEmpty()) }
    var pickDate by remember { mutableStateOf(false) }
    val value = amount.toDoubleOrNull()
    val isNew = initial.id == 0L

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t(if (isNew) "Add Expense" else "Edit Expense")) },
        text = {
            Column {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ExpenseCategory.all.forEach { c ->
                        FilterChip(
                            selected = category == c, onClick = { category = c },
                            label = { Text(I18n.categoryLabel(lang, c)) },
                            leadingIcon = { Icon(categoryIcon(c), null, Modifier.size(16.dp)) }
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amount, onValueChange = { amount = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                    label = { Text(t("Amount")) }, prefix = { Text("₹") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("expense_amount")
                )
                Spacer(Modifier.height(10.dp))
                Surface(
                    onClick = { pickDate = true }, shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline), color = MaterialTheme.colorScheme.surface
                ) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.CalendarToday, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(DateUtils.formatFull(date))
                    }
                }
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(60) }, label = { Text(t("Note (optional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                if (!isNew) {
                    TextButton(
                        onClick = { app.appScope.launch { app.repository.deleteExpense(initial.id) }; onDismiss() },
                        colors = androidx.compose.material3.ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) { Text(t("Delete expense")) }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    app.appScope.launch {
                        app.repository.saveExpense(initial.copy(date = date, category = category, amount = value ?: 0.0, note = note.trim().ifBlank { null }))
                    }
                    onDismiss()
                },
                enabled = value != null && value > 0,
                modifier = Modifier.testTag("save_expense")
            ) { Text(t("Save")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
    if (pickDate) AppDatePickerDialog(date, onPick = { date = it }, onDismiss = { pickDate = false }, pastOnly = true)
}
