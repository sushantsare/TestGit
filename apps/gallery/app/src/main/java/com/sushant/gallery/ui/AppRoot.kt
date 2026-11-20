package com.sushant.gallery.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PhotoAlbum
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.PhotoAlbum
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sushant.gallery.R
import com.sushant.gallery.data.Source
import com.sushant.gallery.ui.albums.AlbumScreen
import com.sushant.gallery.ui.albums.AlbumsScreen
import com.sushant.gallery.ui.bin.BinScreen
import com.sushant.gallery.ui.editor.EditorScreen
import com.sushant.gallery.ui.onboarding.OnboardingScreen
import com.sushant.gallery.ui.photos.PhotosScreen
import com.sushant.gallery.ui.search.SearchScreen
import com.sushant.gallery.ui.settings.AboutScreen
import com.sushant.gallery.ui.settings.DiagnosticsScreen
import com.sushant.gallery.ui.settings.SettingsScreen
import com.sushant.gallery.ui.theme.isDark
import com.sushant.gallery.ui.vault.VaultScreen
import com.sushant.gallery.ui.viewer.ViewerScreen

/** Lets screens navigate without knowing route strings. */
class Nav(val nav: NavHostController, val vm: MainViewModel) {
    fun back() { nav.popBackStack() }
    fun viewer(source: Source, index: Int) = nav.navigate(vm.viewerRoute(source, index))
    fun album(source: Source) = nav.navigate("album/${vm.reg(source.encode())}")
    fun editor(source: Source, index: Int) = nav.navigate("editor/${vm.reg(source.encode() + "|" + index)}")
    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }
}

@Composable
fun AppRoot(vm: MainViewModel, startExternal: Boolean) {
    val access by vm.hasAccess.collectAsStateWithLifecycle()
    val theme by vm.prefs.theme.collectAsStateWithLifecycle()
    if (!access && !startExternal) {
        OnboardingScreen(vm)
        StatusBarIcons(light = !isDark(theme))
        return
    }
    val navController = rememberNavController()
    val nav = remember(navController) { Nav(navController, vm) }
    val snackHost = remember { SnackbarHostState() }
    val entry by navController.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: "home"
    val media = route.startsWith("viewer") || route.startsWith("editor")
    val secure = route == "vault" || route == "pin" || (route.startsWith("viewer") && entry?.arguments?.getInt("id")?.let { vm.viewerArgs(it).first == Source.Vault } == true)

    if (com.sushant.gallery.BuildConfig.DEBUG) LaunchedEffect(route) { android.util.Log.i("GalleryNav", "route=$route") }
    StatusBarIcons(light = !media && !isDark(theme))
    SecureWindow(secure)

    LaunchedEffect(Unit) {
        vm.snacks.collect { s ->
            snackHost.currentSnackbarData?.dismiss()
            val r = snackHost.showSnackbar(s.text, actionLabel = s.action, duration = if (s.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
            if (r == SnackbarResult.ActionPerformed) s.onAction?.invoke()
        }
    }
    LaunchedEffect(startExternal) {
        if (startExternal && vm.external != null) navController.navigate(vm.viewerRoute(Source.External, 0)) { popUpTo("home") { inclusive = false } }
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        NavHost(
            navController, startDestination = "home",
            enterTransition = { fadeIn(tween(220)) }, exitTransition = { fadeOut(tween(180)) },
            popEnterTransition = { fadeIn(tween(220)) }, popExitTransition = { fadeOut(tween(180)) },
        ) {
            composable("home") { Home(vm, nav) }
            composable("album/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                AlbumScreen(vm, nav, Source.decode(vm.key(it.arguments?.getInt("id") ?: 0)))
            }
            composable(
                "viewer/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType }),
                enterTransition = { EnterTransition.None }, exitTransition = { ExitTransition.None },
            ) {
                val (src, idx) = vm.viewerArgs(it.arguments?.getInt("id") ?: 0)
                ViewerScreen(vm, nav, src, idx)
            }
            composable("editor/{id}", arguments = listOf(navArgument("id") { type = NavType.IntType })) {
                val (src, idx) = vm.viewerArgs(it.arguments?.getInt("id") ?: 0)
                EditorScreen(vm, nav, src, idx)
            }
            composable("bin") { BinScreen(vm, nav) }
            composable("search") { SearchScreen(vm, nav) }
            composable("vault") { VaultScreen(vm, nav) }
            composable("pin") { com.sushant.gallery.ui.vault.PinScreen(vm, nav) }
            composable("settings") { SettingsScreen(vm, nav) }
            composable("diag") { DiagnosticsScreen(vm, nav) }
            composable("about") { AboutScreen(nav) }
        }
        val bottom: Dp = when {
            route == "home" -> 88.dp
            route.startsWith("viewer") -> 116.dp
            else -> 16.dp
        }
        SnackbarHost(
            snackHost,
            Modifier.align(androidx.compose.ui.Alignment.BottomCenter).windowInsetsPadding(WindowInsets.navigationBars).padding(bottom = bottom).testTag("snackbar"),
        ) { d -> Snackbar(d, actionColor = MaterialTheme.colorScheme.inversePrimary) }
    }
}

@Composable
private fun Home(vm: MainViewModel, nav: Nav) {
    var tab by rememberSaveable { mutableStateOf(0) }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        val expanded = maxWidth >= 840.dp
        val content: @Composable (Modifier) -> Unit = { m ->
            Box(m) {
                if (tab == 0) PhotosScreen(vm, nav, wide = wide) else AlbumsScreen(vm, nav, expanded = expanded)
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                NavigationRail(containerColor = MaterialTheme.colorScheme.background) {
                    Spacer(Modifier.padding(top = 32.dp))
                    NavigationRailItem(
                        selected = tab == 0, onClick = { tab = 0 }, label = { Text(stringResource(R.string.tab_photos)) },
                        icon = { Icon(if (tab == 0) Icons.Rounded.PhotoLibrary else Icons.Outlined.PhotoLibrary, null) },
                        modifier = Modifier.testTag("tab_photos"),
                    )
                    NavigationRailItem(
                        selected = tab == 1, onClick = { tab = 1 }, label = { Text(stringResource(R.string.tab_albums)) },
                        icon = { Icon(if (tab == 1) Icons.Rounded.PhotoAlbum else Icons.Outlined.PhotoAlbum, null) },
                        modifier = Modifier.testTag("tab_albums"),
                    )
                    Spacer(Modifier.weight(1f))
                    NavigationRailItem(
                        selected = false, onClick = { nav.go("settings") }, label = { Text(stringResource(R.string.settings)) },
                        icon = { Icon(Icons.Outlined.Settings, null) },
                    )
                    Spacer(Modifier.padding(bottom = 16.dp))
                }
                content(Modifier.weight(1f))
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f))
                NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                    NavigationBarItem(
                        selected = tab == 0, onClick = { tab = 0 }, label = { Text(stringResource(R.string.tab_photos)) },
                        icon = { Icon(if (tab == 0) Icons.Rounded.PhotoLibrary else Icons.Outlined.PhotoLibrary, null) },
                        modifier = Modifier.testTag("tab_photos"),
                    )
                    NavigationBarItem(
                        selected = tab == 1, onClick = { tab = 1 }, label = { Text(stringResource(R.string.tab_albums)) },
                        icon = { Icon(if (tab == 1) Icons.Rounded.PhotoAlbum else Icons.Outlined.PhotoAlbum, null) },
                        modifier = Modifier.testTag("tab_albums"),
                    )
                }
            }
        }
    }
}

@Composable
fun StatusBarIcons(light: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }
}

@Composable
fun SecureWindow(on: Boolean) {
    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        if (on) w.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else w.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }
}
