package com.sushant.gymmanager.ui.reports

import com.sushant.gymmanager.util.toast
import android.app.Application
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingDown
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.HourglassBottom
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.data.local.ExpenseEntity
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.PayMode
import com.sushant.gymmanager.data.local.PaymentCycleEntity
import com.sushant.gymmanager.data.local.PaymentEntity
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.SectionTitle
import com.sushant.gymmanager.ui.theme.Slate400
import com.sushant.gymmanager.ui.theme.StatusAmber
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.ui.theme.StatusRed
import com.sushant.gymmanager.ui.theme.Teal600
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.Downloads
import com.sushant.gymmanager.util.Money
import com.sushant.gymmanager.util.ShareUtil
import com.sushant.gymmanager.util.ReportInput
import com.sushant.gymmanager.util.ReportPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.YearMonth

data class ReportData(
    val payments: List<PaymentEntity>,
    val expenses: List<ExpenseEntity>,
    val unpaid: List<PaymentCycleEntity>,
    val members: List<MemberEntity>
)

class ReportsViewModel(app: Application) : AndroidViewModel(app) {
    private val gymApp = app as GymApp
    private val repo = gymApp.repository

    val data: StateFlow<ReportData?> = combine(repo.allPayments, repo.allExpenses, repo.unpaidCycles, repo.allMembers) { p, e, u, m ->
        ReportData(p, e, u, m)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    var month by mutableStateOf(YearMonth.now())
    var exporting by mutableStateOf(false)
        private set

    /** Builds the PDF report for the selected month, saves a copy in Downloads/GymManager and returns the shareable file. */
    fun export(onDone: (File?, Boolean) -> Unit) {
        val d = data.value ?: return
        exporting = true
        val selected = month
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val settings = repo.getSettings()
                    val input = ReportInput(settings, selected, d.payments, d.expenses, repo.snapshot().paymentCycles, d.members)
                    val name = "GymReport_" + settings.gymName.filter { it.isLetterOrDigit() }.take(20) + "_" + selected + ".pdf"
                    val file = File(File(getApplication<Application>().cacheDir, "shared").apply { mkdirs() }, name)
                    file.outputStream().use { ReportPdf.write(input, it) }
                    val saved = Downloads.save(getApplication(), name, "application/pdf") { out -> file.inputStream().use { it.copyTo(out) } } != null
                    file to saved
                }.getOrNull()
            }
            exporting = false
            onDone(result?.first, result?.second ?: false)
        }
    }
}

@Composable
fun ReportsScreen(onOpenDrawer: () -> Unit, vm: ReportsViewModel = viewModel()) {
    val context = LocalContext.current
    val lang = currentLang()
    val data by vm.data.collectAsStateWithLifecycle()
    val exportedMsg = t("PDF report saved to Downloads/GymManager")
    val failedMsg = t("Could not create the report")
    val doExport = {
        vm.export { file, saved ->
            if (file == null) {
                context.toast(failedMsg)
            } else {
                if (saved) context.toast(exportedMsg)
                ShareUtil.shareFile(context, file, "application/pdf", file.name)
            }
        }
    }

    Scaffold(topBar = {
        GymTopBar(t("Reports"), t("Revenue, expenses and profit"), onMenu = onOpenDrawer, actions = {
            IconButton(onClick = doExport, enabled = data != null && !vm.exporting) { Icon(Icons.Default.PictureAsPdf, t("Export PDF report")) }
        })
    }) { padding ->
        val d = data
        if (d == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            return@Scaffold
        }
        val key = vm.month.toString()
        val monthPays = d.payments.filter { it.paidAt.startsWith(key) }
        val collected = monthPays.sumOf { it.amount }
        val spent = d.expenses.filter { it.date.startsWith(key) }.sumOf { it.amount }
        val profit = collected - spent
        val pending = d.unpaid.sumOf { it.balance }
        val joined = d.members.count { it.dateOfJoining.startsWith(key) }
        val series = (5 downTo 0).map { vm.month.minusMonths(it.toLong()) }.map { ym ->
            val k = ym.toString()
            Triple(ym, d.payments.filter { it.paidAt.startsWith(k) }.sumOf { it.amount }, d.expenses.filter { it.date.startsWith(k) }.sumOf { it.amount })
        }

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp).testTag("reports"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { vm.month = vm.month.minusMonths(1) }) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, t("Previous month")) }
                    Text(DateUtils.formatMonth(vm.month.atDay(1)), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    IconButton(onClick = { vm.month = vm.month.plusMonths(1) }, enabled = vm.month.isBefore(YearMonth.now())) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, t("Next month"))
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(Modifier.weight(1f), Icons.Default.AccountBalanceWallet, StatusGreen, Money.format(collected), t("Collected"), t("%d payments", monthPays.size))
                Tile(Modifier.weight(1f), Icons.AutoMirrored.Filled.TrendingDown, StatusRed, Money.format(spent), t("Expenses"), t("This month"))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Tile(
                    Modifier.weight(1f), if (profit >= 0) Icons.Default.Savings else Icons.AutoMirrored.Filled.TrendingDown,
                    if (profit >= 0) Teal600 else StatusRed, Money.format(profit), t(if (profit >= 0) "Profit" else "Loss"), t("Collected − expenses")
                )
                Tile(Modifier.weight(1f), Icons.Default.HourglassBottom, StatusAmber, Money.format(pending), t("Pending Dues"), t("All members, today"))
            }

            AppCard(Modifier.fillMaxWidth()) {
                SectionTitle(t("Last 6 months"))
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendDot(MaterialTheme.colorScheme.primary, t("Collected"))
                    LegendDot(Slate400, t("Expenses"))
                }
                Spacer(Modifier.height(12.dp))
                BarChart(series)
            }

            AppCard(Modifier.fillMaxWidth()) {
                SectionTitle(t("Payment modes"))
                Spacer(Modifier.height(8.dp))
                if (monthPays.isEmpty()) {
                    Text(t("No payments this month"), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                PayMode.all.forEach { mode ->
                    val sum = monthPays.filter { it.mode == mode }.sumOf { it.amount }
                    if (sum > 0) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                            Text(I18n.payModeLabel(lang, mode), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(110.dp))
                            LinearProgressIndicator(
                                progress = { (sum / collected).toFloat() },
                                modifier = Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(Money.format(sum), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }

            AppCard(Modifier.fillMaxWidth()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.width(10.dp))
                    Text(t("New members this month"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                    Text(joined.toString(), style = MaterialTheme.typography.titleLarge)
                }
            }

            Button(
                onClick = doExport,
                enabled = !vm.exporting,
                modifier = Modifier.fillMaxWidth().height(52.dp).testTag("export_pdf")
            ) {
                if (vm.exporting) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                else {
                    Icon(Icons.Default.PictureAsPdf, null); Spacer(Modifier.width(8.dp)); Text(t("Export PDF Report"))
                }
            }
            Text(
                t("A neatly formatted PDF of the selected month: summary, 6-month trend, payment modes, payments, expenses and pending dues."),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun Tile(modifier: Modifier, icon: ImageVector, tint: Color, value: String, label: String, sub: String) {
    AppCard(modifier.height(132.dp), contentPadding = PaddingValues(14.dp)) {
        Box(Modifier.size(34.dp).clip(RoundedCornerShape(10.dp)).background(tint.copy(alpha = 0.13f)), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Text(sub, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BarChart(series: List<Triple<YearMonth, Double, Double>>) {
    val primary = MaterialTheme.colorScheme.primary
    val grid = MaterialTheme.colorScheme.outlineVariant
    val max = series.maxOf { maxOf(it.second, it.third) }.coerceAtLeast(1.0)
    Column {
        Text(Money.format(max), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Canvas(Modifier.fillMaxWidth().height(160.dp)) {
            val n = series.size
            val groupW = size.width / n
            val barW = groupW * 0.28f
            listOf(0f, 0.5f, 1f).forEach { f ->
                val y = size.height * f
                drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            series.forEachIndexed { i, (_, col, exp) ->
                val x0 = groupW * i + groupW / 2 - barW - 2.dp.toPx()
                val h1 = (col / max).toFloat() * size.height
                val h2 = (exp / max).toFloat() * size.height
                drawRoundRect(primary, Offset(x0, size.height - h1), Size(barW, h1), CornerRadius(6.dp.toPx()))
                drawRoundRect(Slate400, Offset(x0 + barW + 4.dp.toPx(), size.height - h2), Size(barW, h2), CornerRadius(6.dp.toPx()))
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth()) {
            series.forEach { (ym, _, _) ->
                Text(
                    DateUtils.formatMonthShort(ym.atDay(1)), Modifier.weight(1f), textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
