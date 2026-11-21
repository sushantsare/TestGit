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

fun NavHostController.go(route: String) { android.util.Log.d("BahiNav", "go $route"); navigate(route) { launchSingleTop = true } }
fun NavHostController.tab(route: String) = navigate(route) {
    popUpTo(graph.findStartDestination().id) { saveState = true }
    launchSingleTop = true; restoreState = true
}
private fun androidx.navigation.NavGraph.findStartDestination(): androidx.navigation.NavDestination {
    var d: androidx.navigation.NavDestination = this
    while (d is androidx.navigation.NavGraph) d = d.findNode(d.startDestinationId) ?: break
    return d
}

@Composable
fun AppRoot(vm: AppVm, locked: Boolean, onUnlock: () -> Unit, onLockBack: () -> Unit, pendingRoute: String?, onRouteConsumed: () -> Unit) {
    val nav = rememberNavController()
    val host = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val snack = remember { Snack(host, scope) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val width = LocalConfiguration.current.screenWidthDp
    val wide = width >= 840
    val rail = width >= 600
    val back by nav.currentBackStackEntryAsState()
    val route = back?.destination?.route?.substringBefore('?')?.substringBefore('/')
    val top = route in tabs.map { it.route }
    val pending by vm.smsPending.collectAsStateWithLifecycle()
    val start = remember { if (s.setupDone) "home" else "setup" }

    LaunchedEffect(pendingRoute, back) {
        val r = pendingRoute ?: return@LaunchedEffect
        if (back == null) return@LaunchedEffect
        onRouteConsumed()
        if (!s.setupDone) return@LaunchedEffect
        when (r) { "home", "entries", "accounts", "udhar", "reports" -> nav.tab(r); "add" -> nav.go("add?type=EXPENSE"); else -> nav.go(r) }
    }

    CompositionLocalProvider(LocalSnack provides snack, LocalNav provides nav, LocalHide provides s.hideAmounts, LocalWide provides wide) {
        Box(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
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
                    snackbarHost = { SnackbarHost(host, Modifier.padding(bottom = if (top) 0.dp else 80.dp)) }, // keep clear of bottom action buttons (Next / Save)
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
                    Box(Modifier.padding(pad).fillMaxSize()) { Graph(nav, vm, start) }
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
