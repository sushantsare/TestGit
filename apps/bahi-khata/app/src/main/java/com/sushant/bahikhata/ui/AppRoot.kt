@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.sushant.bahikhata.ui.components.LocalHide
import com.sushant.bahikhata.ui.screens.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

class Snack(private val host: SnackbarHostState, private val scope: CoroutineScope) {
    fun show(msg: String, action: String? = null, onAction: (() -> Unit)? = null) {
        scope.launch {
            host.currentSnackbarData?.dismiss()
            val r = host.showSnackbar(msg, actionLabel = action, withDismissAction = action == null, duration = if (action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) onAction?.invoke()
        }
    }
}

val LocalSnack = staticCompositionLocalOf<Snack> { error("no snack") }
val LocalNav = staticCompositionLocalOf<NavHostController> { error("no nav") }
val LocalWide = staticCompositionLocalOf { false }

data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val selIcon: androidx.compose.ui.graphics.vector.ImageVector)

val tabs = listOf(
    Tab("home", "Home", Icons.Outlined.Home, Icons.Filled.Home),
    Tab("entries", "Entries", Icons.Outlined.ReceiptLong, Icons.Filled.ReceiptLong),
    Tab("accounts", "Accounts", Icons.Outlined.AccountBalanceWallet, Icons.Filled.AccountBalanceWallet),
    Tab("udhar", "Udhar", Icons.Outlined.Handshake, Icons.Filled.Handshake),
    Tab("reports", "Reports", Icons.Outlined.BarChart, Icons.Filled.BarChart),
)

// single-top only for plain screens: "account_edit/5" must never reuse an open "account_edit/0" (its typed state would leak in)
fun NavHostController.go(route: String) { android.util.Log.d("BahiNav", "go $route"); navigate(route) { launchSingleTop = !route.contains('/') && !route.contains('?') } }
fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true; restoreState = true
}
val SAFE_ROUTE = Regex("^(home|entries|accounts|udhar|reports|search|budgets|sms|sms_setup|settings|categories|recurring|backup|export|diagnostics|about|" +
    "add(\\?(type=(EXPENSE|INCOME)|id=\\d+|sms=\\d+)(&(type=(EXPENSE|INCOME)|id=\\d+|sms=\\d+))*)?|entry/\\d+|account/\\d+|account_edit/\\d+(\\?type=[A-Z]+)?|" +
    "transfer/(paybill|withdraw|transfer)(\\?acc=\\d+)?|person/\\d+)$")

/** From a notification/shortcut: show the tab's top screen, not whatever was stacked on it before. */
fun NavHostController.root(route: String) {
    val start = graph.findStartDestination()
    popBackStack(start.id, false)
    if (start.route != route) navigate(route) { launchSingleTop = true }
}
private fun androidx.navigation.NavGraph.findStartDestination(): androidx.navigation.NavDestination {
    var d: androidx.navigation.NavDestination = this
    while (d is androidx.navigation.NavGraph) d = d.findNode(d.startDestinationId) ?: break
    return d
}

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun AppRoot(vm: AppVm, locked: Boolean, onUnlock: () -> Unit, onLockBack: () -> Unit, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val host = remember { SnackbarHostState() }
    DisposableEffect(nav) {
        val l = androidx.navigation.NavController.OnDestinationChangedListener { _, d, _ ->
            android.util.Log.d("BahiNav", "now ${d.route}")
            host.currentSnackbarData?.dismiss() // a message belongs to the screen it was shown on; never let it cover the next screen
        }
        nav.addOnDestinationChangedListener(l); onDispose { nav.removeOnDestinationChangedListener(l) }
    }
    val scope = rememberCoroutineScope()
    val snack = remember { Snack(host, scope) }
    LaunchedEffect(Unit) { vm.errors.collect { snack.show(it) } }
    val s by vm.settings.collectAsStateWithLifecycle()
    val width = LocalConfiguration.current.screenWidthDp
    val wide = width >= 840
    val rail = width >= 600
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route?.substringBefore('?')?.substringBefore('/')
    val top = route in tabs.map { it.route }
    val pendingOk by vm.smsPending.collectAsStateWithLifecycle()
    val pendingLook by vm.smsLook.collectAsStateWithLifecycle()
    val pending = pendingOk + pendingLook   // same count as the Home card
    val start = remember { if (s.setupDone) "home" else "setup" }

    LaunchedEffect(pendingRoute, back, locked) {
        val r = pendingRoute ?: return@LaunchedEffect
        if (locked) return@LaunchedEffect   // open the screen only after unlocking
        if (back == null) return@LaunchedEffect
        onRouteConsumed()
        if (!s.setupDone) return@LaunchedEffect
        // routes can come from other apps (the activity is exported for the launcher/shortcuts): only known screens are opened
        if (!SAFE_ROUTE.matches(r)) { android.util.Log.w("BahiNav", "ignored route"); return@LaunchedEffect }
        runCatching { when (r) { "home", "entries", "accounts", "udhar", "reports" -> nav.root(r); "add" -> nav.go("add?type=EXPENSE"); else -> nav.go(r) } }
    }

    CompositionLocalProvider(LocalSnack provides snack, LocalNav provides nav, LocalHide provides s.hideAmounts, LocalWide provides wide) {
        // debug builds expose test tags to UI Automator so the e2e audit can tap real elements
        Box(Modifier.fillMaxSize().semantics { testTagsAsResourceId = com.sushant.bahikhata.BuildConfig.DEBUG }) {
            // while locked the app is neither drawn nor readable by accessibility services (state is kept underneath)
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
                .then(if (locked) Modifier.alpha(0f).clearAndSetSemantics { } else Modifier)) {
                if (rail && top) {
                    NavigationRail(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, header = {
                        FloatingActionButton(onClick = { nav.go("add?type=EXPENSE") }, containerColor = com.sushant.bahikhata.ui.theme.LocalBk.current.saffron,
                            contentColor = com.sushant.bahikhata.ui.theme.LocalBk.current.onSaffron, modifier = Modifier.padding(top = 12.dp).testTag("rail_add")) { Icon(Icons.Outlined.Add, "Add entry") }
                    }) {
                        Spacer(Modifier.height(12.dp))
                        tabs.forEach { t ->
                            NavigationRailItem(selected = route == t.route, onClick = { nav.tab(t.route) },
                                icon = { BadgedBox(badge = { if (t.route == "home" && pending.isNotEmpty()) Badge { Text("${pending.size}") } }) { Icon(if (route == t.route) t.selIcon else t.icon, null) } },
                                label = { Text(t.label) }, modifier = Modifier.testTag("tab_${t.route}"))
                        }
                    }
                }
                Scaffold(
                    modifier = Modifier.weight(1f),
                    containerColor = MaterialTheme.colorScheme.background,
                    snackbarHost = { SnackbarHost(host, Modifier.padding(bottom = if (top && rail) 0.dp else 80.dp)) }, // stay above the FAB on tabs and above Next / Save buttons elsewhere
                    bottomBar = {
                        if (!rail && top) NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                            tabs.forEach { t ->
                                NavigationBarItem(selected = route == t.route, onClick = { nav.tab(t.route) },
                                    icon = { BadgedBox(badge = { if (t.route == "home" && pending.isNotEmpty()) Badge { Text("${pending.size}") } }) { Icon(if (route == t.route) t.selIcon else t.icon, null) } },
                                    label = { Text(t.label, maxLines = 1) }, modifier = Modifier.testTag("tab_${t.route}"))
                            }
                        }
                    },
                    contentWindowInsets = WindowInsets(0),
                ) { pad ->
                    // screens without the bottom bar keep their Save/Next buttons above the gesture bar, and every
                    // screen shrinks above the keyboard (edge-to-edge turns off the old automatic resize)
                    // on tablets/desktop windows, screens other than the main tabs are centred at ≤ 840 dp (forms never stretch)
                    Box(Modifier.padding(pad).fillMaxSize().then(if (top && !rail) Modifier else Modifier.navigationBarsPadding()).imePadding(), contentAlignment = Alignment.TopCenter) {
                        Box(Modifier.fillMaxHeight().then(if (!top && width >= 840) Modifier.widthIn(max = 840.dp) else Modifier.fillMaxWidth())) { Graph(nav, vm, start) }
                    }
                }
            }
            if (locked) LockScreen(onUnlock, onLockBack)
        }
    }
}

@Composable
private fun Graph(nav: NavHostController, vm: AppVm, start: String) {
    NavHost(nav, startDestination = start, enterTransition = { fadeIn() }, exitTransition = { fadeOut() }) {
        composable("setup") { SetupScreen(vm) }
        composable("home") { HomeScreen(vm) }
        composable("entries") { EntriesScreen(vm) }
        composable("accounts") { AccountsScreen(vm) }
        composable("udhar") { UdharScreen(vm) }
        composable("reports") { ReportsScreen(vm) }
        composable("add?type={type}&id={id}&sms={sms}", arguments = listOf(
            navArgument("type") { type = NavType.StringType; defaultValue = "EXPENSE" },
            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
            navArgument("sms") { type = NavType.LongType; defaultValue = 0L },
        )) { b -> AddEntryScreen(vm, b.arguments?.getString("type") ?: "EXPENSE", b.arguments?.getLong("id") ?: 0L, b.arguments?.getLong("sms") ?: 0L) }
        composable("entry/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { b -> EntryDetailScreen(vm, b.arguments!!.getLong("id")) }
        composable("search") { SearchScreen(vm) }
        composable("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { b -> AccountDetailScreen(vm, b.arguments!!.getLong("id")) }
        composable("account_edit/{id}?type={type}", arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("type") { type = NavType.StringType; defaultValue = "BANK" })) { b ->
            AccountEditScreen(vm, b.arguments!!.getLong("id"), b.arguments?.getString("type") ?: "BANK")
        }
        composable("transfer/{kind}?acc={acc}", arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("acc") { type = NavType.LongType; defaultValue = 0L })) { b ->
            TransferScreen(vm, b.arguments!!.getString("kind")!!, b.arguments?.getLong("acc") ?: 0L)
        }
        composable("person/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { b -> PersonScreen(vm, b.arguments!!.getLong("id")) }
        composable("budgets") { BudgetsScreen(vm) }
        composable("sms") { SmsInboxScreen(vm) }
        composable("sms_setup") { SmsSetupScreen(vm) }
        composable("settings") { SettingsScreen(vm) }
        composable("categories") { CategoriesScreen(vm) }
        composable("recurring") { RecurringScreen(vm) }
        composable("backup") { BackupScreen(vm) }
        composable("export") { ExportScreen(vm) }
        composable("diagnostics") { DiagnosticsScreen(vm) }
        composable("about") { AboutScreen() }
    }
}

@Composable
private fun LockScreen(onUnlock: () -> Unit, onBack: () -> Unit) {
    BackHandler { onBack() }
    Box(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}.testTag("lock"),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(14.dp)) {
            androidx.compose.foundation.Image(androidx.compose.ui.res.painterResource(com.sushant.bahikhata.R.drawable.ic_app_round), null, Modifier.size(96.dp))
            Text("Bahi Khata is locked", style = MaterialTheme.typography.headlineSmall)
            Text("Use fingerprint or phone PIN", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(16.dp))
            FilledTonalIconButton(onClick = onUnlock, modifier = Modifier.size(72.dp).testTag("unlock")) { Icon(Icons.Outlined.Fingerprint, "Unlock", Modifier.size(40.dp)) }
            TextButton(onClick = onUnlock) { Text("Unlock") }
        }
    }
}

/** Shown instead of the app when the phone's secure key store can't unlock the encrypted data. */
@Composable
fun StorageLocked(onClose: () -> Unit) {
    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).systemBarsPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(Icons.Outlined.Lock, null, Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text("Can't open your khata right now", style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("The phone's secure key store didn't respond, so Bahi Khata kept your data locked instead of opening it unprotected. Nothing was lost. Restart the phone and open the app again.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) { Text("Close") }
    }
}
