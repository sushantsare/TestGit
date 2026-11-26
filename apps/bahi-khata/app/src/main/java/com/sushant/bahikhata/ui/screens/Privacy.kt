package com.sushant.bahikhata.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.data.HideArea
import com.sushant.bahikhata.ui.AppVm
import com.sushant.bahikhata.ui.Auth
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk

/** Settings → Hide amounts: the main switch, the fingerprint rule, and which parts of the app it covers. */
@Composable
fun HideAmountsScreen(vm: AppVm) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val canAuth = remember { Auth.available(ctx) }
    val areas = s.hideAreas.split(',').filter { it.isNotBlank() }.toSet()
    fun setArea(a: String, on: Boolean) {
        val n = (if (on) areas + a else areas - a)
        val str = HideArea.all.map { it.first }.filter { it in n }.joinToString(",")
        // turning an area off shows more, so it needs the fingerprint while hiding is on
        if (on) vm.prefs.update { it.copy(hideAreas = str) } else vm.guardedChange(ctx, "Show amounts in this part") { vm.prefs.update { it.copy(hideAreas = str) } }
    }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackBar("Hide amounts") }, contentWindowInsets = WindowInsets(0)) { pad ->
        LazyColumn(Modifier.padding(pad).fillMaxSize().testTag("hide_list"), contentPadding = PaddingValues(bottom = 32.dp)) {
            item { BkCard(Modifier.padding(16.dp, 8.dp).fillMaxWidth()) {
                SettingSwitch(Icons.Outlined.VisibilityOff, "Hide amounts", "Shows ₹ •••• instead of money — same as the eye on Home", s.hideAmounts, { on ->
                    if (on) vm.prefs.update { it.copy(hideAmounts = true) } else vm.guardedChange(ctx, "Show amounts") { vm.prefs.update { it.copy(hideAmounts = false) } }
                }, "hide_main")
                SettingSwitch(Icons.Outlined.Fingerprint, "Ask for fingerprint to show",
                    if (canAuth) "Hiding is one tap; showing again needs your fingerprint or phone PIN" else "Set a screen lock on your phone first",
                    s.hideAskAuth && canAuth, { on ->
                        if (!canAuth) return@SettingSwitch
                        if (on) vm.prefs.update { it.copy(hideAskAuth = true) } else vm.guardedChange(ctx, "Turn off fingerprint for amounts") { vm.prefs.update { it.copy(hideAskAuth = false) } }
                    }, "hide_auth")
            } }
            item { SectionLabel("What to hide") }
            item { BkCard(Modifier.padding(horizontal = 16.dp).fillMaxWidth()) {
                HideArea.all.forEach { (k, label) ->
                    val icon = when (k) {
                        HideArea.HOME -> Icons.Outlined.Home; HideArea.ENTRIES -> Icons.Outlined.ReceiptLong; HideArea.ACCOUNTS -> Icons.Outlined.AccountBalanceWallet
                        HideArea.UDHAR -> Icons.Outlined.Handshake; HideArea.REPORTS -> Icons.Outlined.BarChart; else -> Icons.Outlined.Notifications
                    }
                    SettingSwitch(icon, label, when (k) {
                        HideArea.HOME -> "Net, received, spent, card bills and budgets on Home"
                        HideArea.ENTRIES -> "Every entry, search, the calendar and SMS suggestions"
                        HideArea.ACCOUNTS -> "Balances, the card picture, bills and limits"
                        HideArea.UDHAR -> "Who owes what, and each person's lines"
                        HideArea.REPORTS -> "Charts, categories, budgets, goals and insights"
                        else -> "Amounts in bill reminders, budget alerts and SMS notifications"
                    }, k in areas, { on -> setArea(k, on) }, "area_$k")
                }
            } }
            item {
                Row(Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalBk.current.info).padding(12.dp)) {
                    Icon(Icons.Outlined.Info, null, tint = LocalBk.current.onInfo); Spacer(Modifier.width(10.dp))
                    Text("Amounts stay as you left them when you reopen the app. While hiding is on, switching any of this off asks for your fingerprint, so nobody can just turn it off.",
                        style = MaterialTheme.typography.bodySmall, color = LocalBk.current.onInfo)
                }
            }
        }
    }
}
