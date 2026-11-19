package com.sushant.gymmanager.ui.payments

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.repository.GymRepository
import com.sushant.gymmanager.data.repository.ReceiptRef
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.Money
import kotlinx.coroutines.launch

fun formatAmount(v: Double) = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(java.util.Locale.ENGLISH, "%.2f", v)

/**
 * Records money against one or more dues. The amount can be less than the total: it is applied to the
 * oldest due first, and the rest stays due.
 */
@Composable
fun CollectPaymentDialog(cycles: List<PaymentCycleEntity>, onDismiss: () -> Unit, onConfirm: (Map<Long, Double>, String, String) -> Unit) {
    val total = cycles.sumOf { it.balance }
    var mode by remember { mutableStateOf(PayMode.CASH) }
    var amountText by remember { mutableStateOf(formatAmount(total)) }
    var note by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()
    val valid = amount != null && amount > 0 && amount <= total + 0.005
    val lang = currentLang()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Collect Payment")) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    if (cycles.size == 1) t("1 due • %s", Money.format(total)) else t("%d dues • %s", cycles.size, Money.format(total)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Text(t("Payment mode"), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PayMode.all.forEach { m ->
                        FilterChip(
                            selected = mode == m, onClick = { mode = m },
                            label = { Text(I18n.payModeLabel(lang, m).substringBefore(" "), maxLines = 1) },
                            modifier = Modifier.testTag("mode_$m")
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                    label = { Text(t("Amount received")) },
                    prefix = { Text("₹") },
                    singleLine = true,
                    isError = !valid,
                    supportingText = {
                        val rest = (total - (amount ?: 0.0)).coerceAtLeast(0.0)
                        Text(
                            when {
                                amount == null || amount <= 0 -> t("Enter the amount received")
                                amount > total + 0.005 -> t("More than the %s due", Money.format(total))
                                rest > 0.005 -> t("Partial payment • %s will remain due", Money.format(rest))
                                else -> t("Full payment")
                            }
                        )
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("pay_amount")
                )
                OutlinedTextField(
                    value = note, onValueChange = { note = it.take(80) }, label = { Text(t("Note (optional)")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(GymRepository.allocate(cycles, amount ?: 0.0), mode, note) },
                enabled = valid,
                modifier = Modifier.testTag("confirm_payment")
            ) { Text(t("Record Payment")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
}

/** After a payment: one place to send each receipt on WhatsApp. */
@Composable
fun ReceiptsDialog(receipts: List<ReceiptRef>, names: Map<Long, String>, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.CheckCircle, null, tint = StatusGreen) },
        title = { Text(t("Payment recorded")) },
        text = {
            Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                Text(t("Send a receipt on WhatsApp:"), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(8.dp))
                receipts.forEach { r ->
                    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(names[r.memberId].orEmpty(), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${r.receiptNo} • ${Money.format(r.total)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        OutlinedButton(onClick = { scope.launch { ShareActions.shareReceipt(context, r.receiptNo) } }) {
                            Icon(Icons.Default.Receipt, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text(t("Send"))
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss, modifier = Modifier.testTag("receipts_done")) { Text(t("Done")) } }
    )
}

/** Lets go of part or all of one due, with a reason (discount, away all month, added by mistake). */
@Composable
fun WaiveDialog(cycle: PaymentCycleEntity, onDismiss: () -> Unit, onConfirm: (amount: Double, reason: String) -> Unit) {
    var amountText by remember { mutableStateOf(formatAmount(cycle.balance)) }
    var reason by remember { mutableStateOf("") }
    val amount = amountText.toDoubleOrNull()
    val valid = amount != null && amount > 0 && amount <= cycle.balance + 0.005 && reason.isNotBlank()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(t("Waive due")) },
        text = {
            Column {
                Text(
                    t("%s – %s • %s due", DateUtils.formatShort(cycle.cycleStart), DateUtils.formatShort(cycle.cycleEnd), Money.format(cycle.balance)),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it.filter { c -> c.isDigit() || c == '.' }.take(9) },
                    label = { Text(t("Amount to waive")) },
                    prefix = { Text("₹") },
                    singleLine = true,
                    isError = amount == null || amount <= 0 || amount > cycle.balance + 0.005,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().testTag("waive_amount")
                )
                OutlinedTextField(
                    value = reason, onValueChange = { reason = it.take(60) },
                    label = { Text(t("Reason *")) },
                    placeholder = { Text(t("e.g. Away all month, discount, added by mistake")) },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("waive_reason")
                )
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(amount ?: 0.0, reason) }, enabled = valid, modifier = Modifier.testTag("confirm_waive")) { Text(t("Waive")) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(t("Cancel")) } }
    )
}
