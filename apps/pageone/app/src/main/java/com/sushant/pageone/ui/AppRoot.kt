package com.sushant.pageone.ui

import android.net.Uri
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sushant.pageone.Incoming
import com.sushant.pageone.MainActivity
import com.sushant.pageone.data.DocRef
import com.sushant.pageone.ui.components.Tab
import com.sushant.pageone.ui.reader.ReaderScreen
import com.sushant.pageone.ui.screens.*
import kotlinx.coroutines.launch

object Routes {
    const val SETUP = "setup"
    const val HOME = "home"
    const val RECENT = "recent"
    const val STARRED = "starred"
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val DIAGNOSTICS = "diagnostics"
    const val DUPLICATES = "duplicates"
    fun folder(path: String) = "folder?path=${Uri.encode(path)}"
    fun merge(seed: String? = null) = "merge?seed=${Uri.encode(seed ?: "")}"
    fun split(uri: String? = null) = "split?uri=${Uri.encode(uri ?: "")}"
    fun compress(uri: String? = null) = "compress?uri=${Uri.encode(uri ?: "")}"
}

fun NavHostController.goTab(tab: Tab) {
    val route = when (tab) { Tab.HOME -> Routes.HOME; Tab.RECENT -> Routes.RECENT; Tab.STARRED -> Routes.STARRED }
    navigate(route) {
        popUpTo(Routes.HOME) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@Composable
fun AppRoot(vm: MainVm, activity: MainActivity) {
    val nav = rememberNavController()
    val s by vm.settings.collectAsState()
    val locked by vm.locked.collectAsState()
    val incoming by activity.incoming.collectAsState()
    val scope = rememberCoroutineScope()
    val start = remember { if (s.setupDone) Routes.HOME else Routes.SETUP }
    val entry by nav.currentBackStackEntryAsState()
    val inTabs = entry?.destination?.route in setOf(Routes.HOME, Routes.RECENT, Routes.STARRED)
    val inReader = entry?.destination?.route?.startsWith("reader") == true

    // PDFs from other apps and launcher shortcuts
    LaunchedEffect(incoming, s.setupDone, locked) {
        val inc = incoming ?: return@LaunchedEffect
        if (locked) return@LaunchedEffect
        activity.incoming.value = null
        when (inc) {
            is Incoming.Open -> nav.navigate(MainVm.readerRoute(inc.ref))
            Incoming.Search -> if (s.setupDone) nav.navigate(Routes.SEARCH)
            Incoming.Starred -> if (s.setupDone) nav.goTab(Tab.STARRED)
            Incoming.Continue -> scope.launch {
                val r = vm.lib.lastRecent()
                if (r != null) nav.navigate(MainVm.readerRoute(vm.lib.resolve(r.fingerprint, r.name, r.uri, r.path)))
                else vm.message("Nothing opened yet")
            }
        }
    }

    val fIn = fadeIn(tween(250, delayMillis = 50, easing = LinearEasing))
    val fOut = fadeOut(tween(250, easing = LinearEasing))
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).semantics { testTagsAsResourceId = true }) {
        NavHost(
            nav, startDestination = start,
            enterTransition = { fIn }, exitTransition = { fOut },
            popEnterTransition = { fIn }, popExitTransition = { fOut },
        ) {
            composable(Routes.SETUP) { SetupScreen(vm) { vm.update { it.copy(setupDone = true) }; nav.navigate(Routes.HOME) { popUpTo(Routes.SETUP) { inclusive = true } } } }
            composable(Routes.HOME) { HomeScreen(vm, nav) }
            composable(Routes.RECENT) { RecentScreen(vm, nav) }
            composable(Routes.STARRED) { StarredScreen(vm, nav) }
            composable("folder?path={path}", listOf(navArgument("path") { type = NavType.StringType; defaultValue = "" })) {
                FolderScreen(vm, nav, it.arguments?.getString("path").orEmpty())
            }
            composable(Routes.SEARCH) { SearchScreen(vm, nav) }
            composable(Routes.SETTINGS) { SettingsScreen(vm, nav) }
            composable(Routes.ABOUT) { AboutScreen(vm, nav) }
            composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(vm, nav) }
            composable(Routes.DUPLICATES) { DuplicatesScreen(vm, nav) }
            composable("merge?seed={seed}", listOf(navArgument("seed") { type = NavType.StringType; defaultValue = "" })) {
                MergeScreen(vm, nav, it.arguments?.getString("seed").orEmpty())
            }
            composable("split?uri={uri}", listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })) {
                SplitScreen(vm, nav, it.arguments?.getString("uri").orEmpty())
            }
            composable("compress?uri={uri}", listOf(navArgument("uri") { type = NavType.StringType; defaultValue = "" })) {
                CompressScreen(vm, nav, it.arguments?.getString("uri").orEmpty())
            }
            composable(
                "reader?uri={uri}&path={path}&name={name}",
                listOf(
                    navArgument("uri") { type = NavType.StringType; defaultValue = "" },
                    navArgument("path") { type = NavType.StringType; defaultValue = "" },
                    navArgument("name") { type = NavType.StringType; defaultValue = "" },
                ),
            ) {
                val a = it.arguments
                val ref = DocRef(a?.getString("uri").orEmpty(), a?.getString("path")?.takeIf { p -> p.isNotEmpty() }, a?.getString("name").orEmpty())
                ReaderScreen(vm, nav, ref)
            }
        }
        // list screens scroll under the status bar: keep the clock and icons on a solid strip
        if (!inReader) Box(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().windowInsetsTopHeight(WindowInsets.statusBars)
                .background(MaterialTheme.colorScheme.surface)
        )
        SnackbarHost(
            vm.snackbar,
            Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = when { inTabs && androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp < com.sushant.pageone.ui.screens.WIDE_DP -> 88.dp; inReader -> 100.dp; else -> 16.dp }, start = 12.dp, end = 12.dp).testTag("snackbar"),
        ) { Snackbar(it) }
        if (locked) LockScreen(activity, onUnlocked = { vm.locked.value = false }, onClose = { activity.finish() })
    }
}
