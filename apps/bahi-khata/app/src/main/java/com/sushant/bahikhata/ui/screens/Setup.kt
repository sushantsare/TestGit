@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.bahikhata.App
import com.sushant.bahikhata.data.*
import com.sushant.bahikhata.ui.*
import com.sushant.bahikhata.ui.components.*
import com.sushant.bahikhata.ui.theme.LocalBk
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STEPS = 9

/** First opening only: a short animation, then a guide that sets up exactly what this app needs, one step per page. */
@Composable
fun SetupScreen(vm: AppVm) {
    var intro by rememberSaveable { mutableStateOf(true) }
    if (intro) WalletIntro { intro = false } else SetupGuide(vm)
}

/** The wallet draws itself on the cream page, the ₹ drops in, then the guide fades up. Tap to skip. */
@Composable
private fun WalletIntro(onDone: () -> Unit) {
    val bk = LocalBk.current
    val ink = MaterialTheme.colorScheme.primary
    val draw = remember { Animatable(0f) }
    val drop = remember { Animatable(-1f) }
    val out = remember { Animatable(1f) }
    val ctx = LocalContext.current
    val noMotion = remember { android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    LaunchedEffect(Unit) {
        if (noMotion) { onDone(); return@LaunchedEffect }
        draw.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
        drop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow))
        delay(350)
        out.animateTo(0f, tween(320))
        onDone()
    }
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { onDone() }
        .graphicsLayer { alpha = out.value; scaleX = 0.96f + 0.04f * out.value; scaleY = 0.96f + 0.04f * out.value }
        .semantics { contentDescription = "Bahi Khata" }.testTag("intro"), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(150.dp), contentAlignment = Alignment.Center) {
                Canvas(Modifier.fillMaxSize()) {
                    val w = size.width; val h = size.height
                    val body = Path().apply {
                        addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.12f, h * 0.30f, w * 0.88f, h * 0.82f, CornerRadius(w * 0.09f)))
                    }
                    val flap = Path().apply { moveTo(w * 0.20f, h * 0.30f); lineTo(w * 0.62f, h * 0.16f); lineTo(w * 0.70f, h * 0.30f) }
                    val clasp = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(w * 0.64f, h * 0.48f, w * 0.88f, h * 0.64f, CornerRadius(w * 0.05f))) }
                    val stroke = Stroke(width = w * 0.045f, cap = StrokeCap.Round)
                    listOf(body to 0f, flap to 0.45f, clasp to 0.7f).forEach { (p, start) ->
                        val t = ((draw.value - start) / (1f - start * 0.6f)).coerceIn(0f, 1f)
                        if (t <= 0f) return@forEach
                        val pm = PathMeasure().apply { setPath(p, false) }
                        val seg = Path(); pm.getSegment(0f, pm.length * t, seg, true)
                        drawPath(seg, ink, style = stroke)
                    }
                    if (draw.value >= 1f) drawRoundRect(bk.saffron.copy(alpha = 0.18f), Offset(w * 0.12f, h * 0.30f), Size(w * 0.76f, h * 0.52f), CornerRadius(w * 0.09f))
                }
                Text("₹", fontSize = 46.sp, fontWeight = FontWeight.ExtraBold, color = bk.saffron,
                    modifier = Modifier.offset(x = (-10).dp, y = (drop.value * 120).dp + 8.dp).graphicsLayer { alpha = if (drop.value <= -0.99f) 0f else 1f })
            }
            Spacer(Modifier.height(18.dp))
            Text("Bahi Khata", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.graphicsLayer { alpha = draw.value })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SetupGuide(vm: AppVm) {
    val nav = LocalNav.current
    val ctx = LocalContext.current
    val bk = LocalBk.current
    val app = ctx.applicationContext as App
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(1) }
    var forward by remember { mutableStateOf(true) }
    val accounts by vm.accounts.collectAsStateWithLifecycle()
    val people by vm.people.collectAsStateWithLifecycle()
    val udhar by vm.udhar.collectAsStateWithLifecycle()
    val nets by vm.nets.collectAsStateWithLifecycle()
    val s by vm.settings.collectAsStateWithLifecycle()
    var addPerson by rememberSaveable { mutableStateOf(false) }
    var smsAsked by rememberSaveable { mutableStateOf(false) }
    var notifAsked by rememberSaveable { mutableStateOf(false) }
    var reading by rememberSaveable { mutableStateOf(false) }
    val canAuth = remember { Auth.available(ctx) }
    fun smsGranted() = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
    fun notifGranted() = Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var smsOk by remember { mutableStateOf(smsGranted()) }
    var notifOk by remember { mutableStateOf(notifGranted()) }
    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_RESUME) { smsOk = smsGranted(); notifOk = notifGranted() }

    fun go(to: Int) { forward = to > step; step = to.coerceIn(1, STEPS) }
    fun finish() {
        vm.prefs.update { it.copy(setupDone = true) }
        nav.navigate("home") { popUpTo("setup") { inclusive = true } }
    }
    val smsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        app.prefs.askedSms = true; smsAsked = true
        smsOk = res[Manifest.permission.READ_SMS] == true
        if (smsOk) {
            vm.prefs.update { it.copy(smsOn = true) }
            app.sms.startObserver()
            reading = true
            // reading runs app-wide, so moving on through the guide doesn't stop it
            val job = app.appScope.launch { app.sms.scanSince(System.currentTimeMillis() - 90L * 86_400_000L, notify = false); app.sms.historyScan() }
            scope.launch { job.join(); reading = false }
            go(step + 1)
        }
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        app.prefs.askedNotif = true; notifAsked = true; notifOk = granted || notifGranted()
        if (notifOk) go(step + 1)
    }

    // a step that needs a permission moves on only once it's allowed (or he taps Skip)
    val next: () -> Unit = {
        when (step) {
            4 -> if (smsOk) { if (!s.smsOn) vm.prefs.update { it.copy(smsOn = true) }; go(5) } else smsLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS))
            5 -> if (notifOk) go(6) else notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            STEPS -> finish()
            else -> go(step + 1)
        }
    }
    val nextLabel = when (step) {
        1 -> "Get started"; 4 -> if (smsOk) "Next" else "Allow SMS access"; 5 -> if (notifOk) "Next" else "Allow notifications"; STEPS -> "Open Bahi Khata"; else -> "Next"
    }
    val skippable = step in setOf(3, 4, 5, 7)

    androidx.activity.compose.BackHandler(step > 1) { go(step - 1) }   // system Back goes to the previous step, not out of the app
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0)) { pad ->
        Column(Modifier.padding(pad).statusBarsPadding().navigationBarsPadding().imePadding().fillMaxSize()) {
            Row(Modifier.padding(start = 4.dp, end = 16.dp, top = 8.dp).height(48.dp), verticalAlignment = Alignment.CenterVertically) {
                if (step > 1) IconButton(onClick = { go(step - 1) }, modifier = Modifier.testTag("back")) { Icon(Icons.Outlined.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (step == 1) TextButton(onClick = { finish() }, modifier = Modifier.testTag("skip_setup")) { Text("Skip setup") }
            }
            AnimatedContent(step, modifier = Modifier.weight(1f), transitionSpec = {
                val dir = if (forward) 1 else -1
                (slideInHorizontally(tween(320, easing = FastOutSlowInEasing)) { it / 5 * dir } + fadeIn(tween(260, 60))) togetherWith
                    (slideOutHorizontally(tween(260)) { -it / 6 * dir } + fadeOut(tween(160)))
            }, label = "step") { st ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 22.dp).widthIn(max = 640.dp)) {
                    when (st) {
                        1 -> Page(listOf(Icons.Outlined.AccountBalanceWallet, Icons.Outlined.CurrencyRupee, Icons.Outlined.Sms), "Welcome to Bahi Khata",
                            "Every rupee in and out — from which card or account — with udhar, card bills and SMS that save themselves.") {
                            listOf(Icons.Outlined.Lock to "Your data stays on this phone, encrypted", Icons.Outlined.Sms to "Bank SMS become entries you OK with one tap",
                                Icons.Outlined.CreditCard to "Card bills, due dates and EMIs, never miss one", Icons.Outlined.Handshake to "Udhar with friends, person by person").forEach { (i, t) ->
                                Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) { IconBadge(i, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer); Spacer(Modifier.width(14.dp)); Text(t, style = MaterialTheme.typography.bodyMedium) }
                            }
                        }
                        2 -> Page(listOf(Icons.Outlined.AccountBalance, Icons.Outlined.CreditCard, Icons.Outlined.Payments), "Your accounts", "Add each bank, card, wallet and cash with today's balance. It's your starting point — it won't count as income.") {
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
                                        Row(Modifier.weight(1f).heightIn(min = 56.dp).border(1.dp, bk.cardBorder, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).clickable { nav.go("account_edit/0?type=$t") }.padding(14.dp).testTag("setup_add_$t"),
                                            verticalAlignment = Alignment.CenterVertically) { Icon(Ico.account(t), null); Spacer(Modifier.width(8.dp)); Text(l, style = MaterialTheme.typography.labelLarge) }
                                    }
                                }
                            }
                            Text("UPI apps (GPay, PhonePe…) use your bank account's balance — add them inside the bank account.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        3 -> Page(listOf(Icons.Outlined.Handshake, Icons.Outlined.People, Icons.Outlined.CurrencyRupee), "Any udhar right now?", "People who owe you, or whom you owe, with today's amount. You can skip this.") {
                            if (people.isNotEmpty()) BkCard(Modifier.fillMaxWidth()) {
                                people.forEach { p -> val b = Ledger.personBalance(p, udhar)
                                    ListRow(p.name, if (b > 0) "owes you" else if (b < 0) "you owe" else "settled", leading = { Initials(p.name) }, trailing = { MoneyText(kotlin.math.abs(b), color = if (b > 0) bk.income else bk.expense) }) }
                            }
                            Gap(12)
                            OutlinedPillButton("Add person", Icons.Outlined.PersonAdd, { addPerson = true }, tag = "setup_person")
                        }
                        4 -> Page(listOf(Icons.Outlined.Sms, Icons.Outlined.AutoAwesome, Icons.Outlined.CheckCircle), "Read bank SMS?",
                            "Bank, card and UPI messages turn into entries you OK with one tap — plus bills, EMIs, refunds and subscriptions found by themselves. SMS never leave your phone.") {
                            Bullets(listOf("Only bank, card and UPI messages", "Read and processed on this phone", "Nothing is saved without your OK", "Turn it off any time in Settings"))
                            if (smsOk) Done("SMS access is on" + if (reading) " · reading the last few months…" else "")
                            else if (smsAsked) Text("No problem — tap Skip to add entries by hand. You can allow it later in Settings.", color = bk.warn, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                        }
                        5 -> Page(listOf(Icons.Outlined.Notifications, Icons.Outlined.ShoppingBasket, Icons.Outlined.EventRepeat), "Stay in the loop",
                            "Notifications let you save an SMS entry with one tap, answer \"What did you buy?\", and get bill, EMI and unusual-payment alerts.") {
                            if (notifOk) Done(if (Build.VERSION.SDK_INT < 33) "Notifications are on" else "Notifications allowed")
                            else if (notifAsked) Text("Notifications are off. Tap Skip — you can turn them on later in Settings.", color = bk.warn, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 10.dp))
                        }
                        6 -> Page(listOf(Icons.Outlined.Fingerprint, Icons.Outlined.VisibilityOff, Icons.Outlined.Lock), "Keep it private", "Lock the app with your fingerprint, and hide amounts when people are around.") {
                            BkCard(Modifier.fillMaxWidth()) {
                                SettingSwitch(Icons.Outlined.Fingerprint, "App lock", if (canAuth) "Fingerprint or phone PIN when you open the app" else "Set a screen lock on your phone first", s.lockOn && canAuth,
                                    { on -> if (canAuth) vm.prefs.update { it.copy(lockOn = on) } }, "setup_lock")
                                SettingSwitch(Icons.Outlined.VisibilityOff, "Hide amounts", "Shows ₹ •••• until you tap the eye", s.hideAmounts, { on -> vm.prefs.update { it.copy(hideAmounts = on) } }, "setup_hide")
                                SettingSwitch(Icons.Outlined.Fingerprint, "Fingerprint to show amounts", "Hiding is one tap; showing needs your fingerprint", s.hideAskAuth && canAuth,
                                    { on -> if (canAuth) vm.prefs.update { it.copy(hideAskAuth = on) } }, "setup_hide_auth")
                            }
                        }
                        7 -> Page(listOf(Icons.Outlined.CloudUpload, Icons.Outlined.Save, Icons.Outlined.Lock), "Back it up", "Backups are locked with your password. Google Drive keeps a copy every day, so a lost phone doesn't lose your khata.") {
                            BkCard(Modifier.fillMaxWidth()) {
                                SettingNav(Icons.Outlined.CloudUpload, "Set up backup", if (s.driveOn) "Google Drive backup is on" else "Google Drive or a backup file") { nav.go("backup") }
                            }
                            if (s.driveOn) Done("Backup is on")
                        }
                        8 -> Page(listOf(Icons.Outlined.DarkMode, Icons.Outlined.LightMode, Icons.Outlined.Contrast), "Your look", "Light, dark, or the same as your phone. Dark can go pure black on OLED screens.") {
                            Segments(listOf("Follow phone", "Light", "Dark"), s.theme.ordinal, { i -> vm.setTheme(ThemeMode.entries[i]) })
                            Gap(12)
                            val dark = com.sushant.bahikhata.ui.theme.isDark(s.theme)
                            BkCard(Modifier.fillMaxWidth()) {
                                SettingSwitch(Icons.Outlined.Contrast, "Pure black", if (dark) "Darker dark mode for OLED screens" else "Available in dark mode", s.pureBlack && dark,
                                    { on -> if (dark) vm.prefs.update { it.copy(pureBlack = on) } }, "setup_black")
                            }
                        }
                        else -> {
                            val tick = remember { Animatable(0f) }
                            LaunchedEffect(Unit) { tick.animateTo(1f, spring(dampingRatio = 0.5f, stiffness = Spring.StiffnessLow)) }
                            Column(Modifier.fillMaxWidth().padding(top = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                Box(Modifier.size(120.dp).scale(tick.value).clip(CircleShape).background(bk.income.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
                                    Icon(Icons.Outlined.Check, null, tint = bk.income, modifier = Modifier.size(64.dp))
                                }
                                Gap(24)
                                Text("You're all set", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, textAlign = TextAlign.Center)
                                Gap(8)
                                Text("Add an entry with the + button, or OK the SMS suggestions as they come. Everything here can be changed in Settings.", textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            // bottom bar: step counter that slides, Skip on optional steps, and a next button that turns and changes shape
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedContent(step, transitionSpec = {
                    (slideInVertically { if (forward) it else -it } + fadeIn()) togetherWith (slideOutVertically { if (forward) -it else it } + fadeOut())
                }, label = "count") { st -> Text("Step $st of $STEPS", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.testTag("step_count")) }
                Spacer(Modifier.weight(1f))
                if (skippable) TextButton(onClick = { go(step + 1) }, modifier = Modifier.testTag("skip")) { Text("Skip") }
                Spacer(Modifier.width(8.dp))
                MorphButton(step, nextLabel, next)
            }
        }
    }
    if (addPerson) PersonDialog(null, { vm.launch { vm.repo.savePerson(it) } }) { addPerson = false }
}

/** Circle → rounded square → leaf, a quarter turn on every step, a tick on the last. The label stays readable beside it. */
@Composable
private fun MorphButton(step: Int, label: String, onClick: () -> Unit) {
    val bk = LocalBk.current
    val shape = step % 3
    val tl by animateDpAsState(if (shape == 2) 30.dp else if (shape == 1) 16.dp else 30.dp, tween(380), label = "tl")
    val tr by animateDpAsState(if (shape == 2) 6.dp else if (shape == 1) 16.dp else 30.dp, tween(380), label = "tr")
    val turn by animateFloatAsState(step * 90f, tween(420, easing = FastOutSlowInEasing), label = "turn")
    val last = step == STEPS
    Row(Modifier.heightIn(min = 60.dp).clip(RoundedCornerShape(30.dp)).background(MaterialTheme.colorScheme.primary).clickable(onClick = onClick)
        .padding(start = 20.dp, end = 6.dp).testTag("setup_next"), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge, maxLines = 1)
        Spacer(Modifier.width(10.dp))
        Box(Modifier.size(48.dp).rotate(if (last) 0f else turn)
            .clip(RoundedCornerShape(topStart = tl, topEnd = tr, bottomEnd = tl, bottomStart = tr)).background(bk.saffron), contentAlignment = Alignment.Center) {
            Icon(if (last) Icons.Outlined.Check else Icons.Outlined.ArrowForward, null, tint = bk.onSaffron, modifier = Modifier.rotate(if (last) 0f else -turn))
        }
    }
}

/** One guide page: a playful group of tilted shapes with icons, a big rounded title, one line, then the page's own controls. */
@Composable
private fun Page(icons: List<ImageVector>, title: String, body: String, content: @Composable ColumnScope.() -> Unit) {
    val bk = LocalBk.current
    Column(Modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().height(150.dp).padding(top = 8.dp), contentAlignment = Alignment.Center) {
            val tints = listOf(MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer, bk.saffron.copy(alpha = 0.25f) to bk.saffron, bk.info to bk.onInfo)
            icons.take(3).forEachIndexed { i, ic ->
                val (bg, fg) = tints[i % tints.size]
                val x = listOf(-70, 0, 70)[i]; val y = listOf(18, -14, 22)[i]; val r = listOf(-12f, 6f, 14f)[i]
                Box(Modifier.offset(x.dp, y.dp).size(if (i == 1) 88.dp else 70.dp).rotate(r).clip(if (i == 1) CircleShape else RoundedCornerShape(22.dp)).background(bg), contentAlignment = Alignment.Center) {
                    Icon(ic, null, tint = fg, modifier = Modifier.size(if (i == 1) 42.dp else 32.dp).rotate(-r))
                }
            }
        }
        Gap(12)
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold)
        Gap(8)
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Gap(18)
        content()
        Gap(24)
    }
}

@Composable
private fun Bullets(lines: List<String>) = lines.forEach {
    Row(Modifier.padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Outlined.CheckCircle, null, tint = LocalBk.current.income); Spacer(Modifier.width(12.dp)); Text(it, style = MaterialTheme.typography.bodyMedium) }
}

@Composable
private fun Done(text: String) {
    Row(Modifier.padding(top = 12.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(LocalBk.current.income.copy(alpha = 0.12f)).padding(12.dp).testTag("step_done"), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.CheckCircle, null, tint = LocalBk.current.income); Spacer(Modifier.width(10.dp))
        Text(text, style = MaterialTheme.typography.labelLarge)
    }
}
