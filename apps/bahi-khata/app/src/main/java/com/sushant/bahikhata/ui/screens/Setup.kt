@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import androidx.biometric.BiometricManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.R
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk

@Composable
fun SetupScreen(vm: AppVm) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    var step by rememberSaveable { mutableIntStateOf(1) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val udhar by vm.udhar.collectAsStateWithLifecycle()
    val nets by vm.nets.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var addPerson by remember { mutableStateOf(false) }

    fun finish() {
        vm.prefs.update { it.copy(setupDone = true) }
        nav.navigate("home") { popUpTo("setup") { inclusive = true } }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().fillMaxSize()) {
            if (step > 1) {
                Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { step-- }, modifier = Modifier.testTag("back")) { Icon(Icons.Outlined.ArrowBack, "Back") }
                    Text("STEP $step OF 4", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                }
                StepBar(step, 4)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 18.dp).widthIn(max = 640.dp)) {
                when (step) {
                    1 -> {
                        Gap(60)
                        Image(painterResource(R.drawable.ic_app_round), null, Modifier.size(96.dp).align(Alignment.CenterHorizontally))
                        Gap(24)
                        Text("Welcome to Bahi Khata", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                        Gap(10)
                        Text("Every rupee in and out — from which card or account — with receipts, udhar and card bills in one place.", textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
                        Gap(28)
                        listOf(Icons.Outlined.Lock to "Your data stays on this phone, encrypted", Icons.Outlined.Sms to "Bank SMS become entries you OK with one tap",
                            Icons.Outlined.CreditCard to "Card bills and due dates, never miss one", Icons.Outlined.Handshake to "Udhar with friends, person by person").forEach { (i, t) ->
                            Row(Modifier.padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) { IconBadge(i, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(14.dp)); Text(t, style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    2 -> {
                        Gap(16)
                        Text("Add your accounts with today's balance", style = MaterialTheme.typography.headlineSmall)
                        Gap(6)
                        Text("This is your starting point — it won't count as income. Cards: enter what you owe today.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(14)
                        if (accounts.isNotEmpty()) BkCard(Modifier.fillMaxWidth()) {
                            accounts.forEach { a ->
                                ListRow(a.name, typeLabel(a.type) + if (a.type == AccType.CARD && a.limitPaise > 0) " · limit ${Money.fmt(a.limitPaise)}" else "", Modifier.testTag("setup_acc_${a.name}"),
                                    leading = { Icon(Ico.account(a.type), null) }, trailing = {
                                        val n = nets[a.id] ?: 0L
                                        MoneyText(if (a.type == AccType.CARD) -n else n, color = if (a.type == AccType.CARD) bk.expense else MaterialTheme.colorScheme.onSurface)
                                    }, onClick = { nav.go("account_edit/${a.id}?type=${a.type}") })
                            }
                        }
                        Gap(12)
                        listOf(AccType.BANK to "+ Bank", AccType.CARD to "+ Credit card", AccType.WALLET to "+ Wallet", AccType.CASH to "+ Cash").chunked(2).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(bottom = 10.dp)) {
                                row.forEach { (t, l) ->
                                    Row(Modifier.weight(1f).heightIn(min = 56.dp).border(1.dp, bk.cardBorder, RoundedCornerShape(14.dp)).clickable { nav.go("account_edit/0?type=$t") }.padding(14.dp).testTag("setup_add_$t"),
                                        verticalAlignment = Alignment.CenterVertically) { Icon(Ico.account(t), null); Spacer(Modifier.width(8.dp)); Text(l, style = MaterialTheme.typography.labelLarge) }
                                }
                            }
                        }
                        Text("UPI apps (GPay, PhonePe…) use your bank account's balance — add them inside the bank account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    3 -> {
                        Gap(16)
                        Text("Any udhar right now?", style = MaterialTheme.typography.headlineSmall)
                        Gap(6)
                        Text("Add people who owe you or whom you owe, with today's amount. You can skip this.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Gap(14)
                        if (people.isNotEmpty()) BkCard(Modifier.fillMaxWidth()) {
                            people.forEach { p -> val b = Ledger.personBalance(p, udhar)
                                ListRow(p.name, if (b > 0) "owes you" else if (b < 0) "you owe" else "settled", leading = { Initials(p.name) }, trailing = { MoneyText(kotlin.math.abs(b), color = if (b > 0) bk.income else bk.expense) }) }
                        }
                        Gap(12)
                        OutlinedPillButton("Add person", Icons.Outlined.PersonAdd, { addPerson = true }, tag = "setup_person")
                    }
                    else -> {
                        Gap(16)
                        Text("Keep it safe and automatic", style = MaterialTheme.typography.headlineSmall)
                        Gap(14)
                        val canLock = BiometricManager.from(ctx).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_WEAK) == BiometricManager.BIOMETRIC_SUCCESS ||
                            (ctx.getSystemService(android.content.Context.KEYGUARD_SERVICE) as android.app.KeyguardManager).isDeviceSecure
                        BkCard(Modifier.fillMaxWidth()) {
                            SettingSwitch(Icons.Outlined.Fingerprint, "App lock", if (canLock) "Fingerprint or phone PIN when you open the app" else "Set a screen lock on your phone first", s.lockOn && canLock, { on -> if (canLock) vm.prefs.update { it.copy(lockOn = on) } }, "setup_lock")
                            SettingNav(Icons.Outlined.Sms, "Read bank SMS", if (s.smsOn) "On" else "Turn bank SMS into one-tap entries") { nav.go("sms_setup") }
                        }
                        Gap(12)
                        Text("You can change all of this later in Settings.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step in 2..3) TextButton(onClick = { step++ }, modifier = Modifier.testTag("skip")) { Text("Skip") }
                if (step == 1) TextButton(onClick = { finish() }, modifier = Modifier.testTag("skip_setup")) { Text("Skip setup") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { if (step < 4) step++ else finish() }, modifier = Modifier.weight(1f).height(52.dp).testTag("setup_next")) {
                    Text(when (step) { 1 -> "Get started"; 4 -> "Done"; else -> "Next" })
                }
            }
        }
    }
    if (addPerson) PersonDialog(null, { vm.launch { vm.repo.savePerson(it) } }) { addPerson = false }
}
