@file:Suppress("DEPRECATION")

package com.sushant.bahikhata.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateDp
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
/** The bottom tab bar, drawn by each tab screen itself (given that screen's own route) so it moves with the screen during back. */
val LocalTabBar = staticCompositionLocalOf<@Composable (String) -> Unit> { {} }

data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val selIcon: androidx.compose.ui.graphics.vector.ImageVector)

private val TAB_ROUTES = setOf("home", "entries", "accounts", "udhar", "reports")

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
    val rail = railShown()
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
                if (rail && route != null && route != "setup") {   // the rail stays put on every screen, so nothing jumps during back
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
                    snackbarHost = { SnackbarHost(host, if (top && !rail) Modifier.navigationBarsPadding().padding(bottom = 160.dp) else Modifier.padding(bottom = if (top) 0.dp else 80.dp)) }, // above the tab bar + FAB on tabs and above Next / Save buttons elsewhere
                    contentWindowInsets = WindowInsets(0),
                ) { pad ->
                    // screens without the bottom bar keep their Save/Next buttons above the gesture bar, and every
                    // screen shrinks above the keyboard (edge-to-edge turns off the old automatic resize)
                    // on tablets/desktop windows, screens other than the main tabs are centred at ≤ 840 dp (forms never stretch)
                    // each screen handles its own bottom space (tab bar or gesture bar) and width cap inside page(),
                    // so the layout never changes size in the middle of a back swipe
                    val bar: @Composable (String) -> Unit = { me ->
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainerLow, tonalElevation = 0.dp) {
                            tabs.forEach { t ->
                                NavigationBarItem(selected = me == t.route, onClick = { nav.tab(t.route) },
                                    icon = { BadgedBox(badge = { if (t.route == "home" && pending.isNotEmpty()) Badge { Text("${pending.size}") } }) { Icon(if (me == t.route) t.selIcon else t.icon, null) } },
                                    label = { BarLabel(t.label) }, modifier = Modifier.testTag("tab_${t.route}"))
                            }
                        }
                    }
                    CompositionLocalProvider(LocalTabBar provides bar) {
                        Box(Modifier.padding(pad).fillMaxSize().imePadding()) { Graph(nav, vm, start) }
                    }
                }
            }
            if (locked) LockScreen(onUnlock, onLockBack)
        }
    }
}

@Composable
private fun Graph(nav: NavHostController, vm: AppVm, start: String) {
    // Motion follows Material 3 / Android predictive back:
    // • between bottom-bar tabs: a quick fade-through (no sliding between peers)
    // • opening a screen: shared-axis X — the new screen comes in from the right, the old one drifts left
    // • going back (button or gesture), from any screen including tabs: the top screen becomes a card that shrinks
    //   with the finger over the previous screen, like Android's back-to-home. The gesture can be cancelled.
    fun isTab(r: String?) = r?.substringBefore('?')?.substringBefore('/') in TAB_ROUTES
    val ease = androidx.compose.animation.core.FastOutSlowInEasing
    NavHost(nav, startDestination = start,
        enterTransition = {
            if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) fadeIn(tween(210, delayMillis = 90))
            else slideInHorizontally(tween(300, easing = ease)) { it / 10 } + fadeIn(tween(210, delayMillis = 60))
        },
        exitTransition = {
            if (isTab(initialState.destination.route) && isTab(targetState.destination.route)) fadeOut(tween(90))
            else slideOutHorizontally(tween(300, easing = ease)) { -it / 10 } + fadeOut(tween(90))
        },
        // back looks the same everywhere (tabs, Settings and every screen inside it): the previous screen waits,
        // fully visible, behind the card
        popEnterTransition = { EnterTransition.None },
        popExitTransition = {
            // the card reaches its full shrink in the first 40 % of the swipe, so it clearly follows the finger
            // (like Android's back-to-home); it stays solid and fades out only at the very end
            scaleOut(tween(120, easing = androidx.compose.animation.core.LinearOutSlowInEasing), targetScale = 0.9f) +
                slideOutHorizontally(tween(120, easing = androidx.compose.animation.core.LinearOutSlowInEasing)) { it / 14 } +
                fadeOut(tween(80, delayMillis = 220))
        },
    ) {
        page("setup") { SetupScreen(vm) }
        page("home") { HomeScreen(vm) }
        page("entries") { EntriesScreen(vm) }
        page("accounts") { AccountsScreen(vm) }
        page("udhar") { UdharScreen(vm) }
        page("reports") { ReportsScreen(vm) }
        page("add?type={type}&id={id}&sms={sms}", arguments = listOf(
            navArgument("type") { type = NavType.StringType; defaultValue = "EXPENSE" },
            navArgument("id") { type = NavType.LongType; defaultValue = 0L },
            navArgument("sms") { type = NavType.LongType; defaultValue = 0L },
        )) { b -> AddEntryScreen(vm, b.arguments?.getString("type") ?: "EXPENSE", b.arguments?.getLong("id") ?: 0L, b.arguments?.getLong("sms") ?: 0L) }
        page("entry/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { b -> EntryDetailScreen(vm, b.arguments!!.getLong("id")) }
        page("search") { SearchScreen(vm) }
        page("account/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { b -> AccountDetailScreen(vm, b.arguments!!.getLong("id")) }
        page("account_edit/{id}?type={type}", arguments = listOf(navArgument("id") { type = NavType.LongType }, navArgument("type") { type = NavType.StringType; defaultValue = "BANK" })) { b ->
            AccountEditScreen(vm, b.arguments!!.getLong("id"), b.arguments?.getString("type") ?: "BANK")
        }
        page("transfer/{kind}?acc={acc}", arguments = listOf(navArgument("kind") { type = NavType.StringType }, navArgument("acc") { type = NavType.LongType; defaultValue = 0L })) { b ->
            TransferScreen(vm, b.arguments!!.getString("kind")!!, b.arguments?.getLong("acc") ?: 0L)
        }
        page("person/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { b -> PersonScreen(vm, b.arguments!!.getLong("id")) }
        page("budgets") { BudgetsScreen(vm) }
        page("sms") { SmsInboxScreen(vm) }
        page("sms_setup") { SmsSetupScreen(vm) }
        page("settings") { SettingsScreen(vm) }
        page("categories") { CategoriesScreen(vm) }
        page("recurring") { RecurringScreen(vm) }
        page("backup") { BackupScreen(vm) }
        page("export") { ExportScreen(vm) }
        page("diagnostics") { DiagnosticsScreen(vm) }
        page("about") { AboutScreen() }
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
            Text("Bahi Khata is locked", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground)
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
        Text("Can't open your khata right now", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onBackground, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("The phone's secure key store didn't respond, so Bahi Khata kept your data locked instead of opening it unprotected. Nothing was lost. Restart the phone and open the app again.",
            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(onClick = onClose) { Text("Close") }
    }
}

/** Bottom-bar label that grows with the phone's font size only up to what fits five tabs (never below a real 12 sp). */
@Composable
private fun BarLabel(text: String) {
    val scale = LocalDensity.current.fontScale
    val size = if (scale <= 1.3f) MaterialTheme.typography.labelMedium.fontSize else (12f * 1.3f / scale).coerceAtLeast(12f / scale).sp
    Text(text, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Clip, fontSize = size)
}

/** The navigation rail (which carries the + button) needs width and height for five items; phones in landscape
 *  and short split-screen windows keep the bottom bar, and then each screen shows its own + button. */
@Composable
fun railShown(): Boolean {
    val c = LocalConfiguration.current
    return c.screenWidthDp >= 600 && c.screenHeightDp >= 480
}

/** A screen in the graph. Tab screens carry their own bottom tab bar; other screens keep clear of the gesture bar and,
 *  on wide windows, are centred at ≤ 840 dp. While a screen leaves (back gesture or button) it becomes a card with
 *  round corners that shrinks away, the same as Android's own back-to-home motion. */
@OptIn(androidx.compose.animation.ExperimentalAnimationApi::class)
private fun androidx.navigation.NavGraphBuilder.page(
    route: String, arguments: List<androidx.navigation.NamedNavArgument> = emptyList(),
    content: @Composable (androidx.navigation.NavBackStackEntry) -> Unit,
) = composable(route, arguments) { entry ->
    val corner by transition.animateDp(transitionSpec = { tween(120) }, label = "corner") { st -> if (st == androidx.compose.animation.EnterExitState.PostExit) 28.dp else 0.dp }
    val me = route.substringBefore('?').substringBefore('/')
    val tab = me in TAB_ROUTES
    val rail = railShown()
    val wide = LocalConfiguration.current.screenWidthDp >= 840
    Box(Modifier.fillMaxSize().graphicsLayer { shape = RoundedCornerShape(corner); clip = corner > 0.dp }
        .background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.TopCenter) {
        if (tab && !rail) Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) { content(entry) }
            LocalTabBar.current(me)
        } else Box(Modifier.fillMaxHeight().navigationBarsPadding().then(if (!tab && wide) Modifier.widthIn(max = 840.dp) else Modifier.fillMaxWidth())) { content(entry) }
    }
}
