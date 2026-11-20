package com.sushant.saveo.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Collections
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.saveo.R
import com.sushant.saveo.ui.home.HomeScreen
import com.sushant.saveo.ui.settings.AboutScreen
import com.sushant.saveo.ui.settings.BinScreen
import com.sushant.saveo.ui.settings.DiagnosticsScreen
import com.sushant.saveo.ui.settings.LockScreen
import com.sushant.saveo.ui.settings.SettingsScreen
import com.sushant.saveo.ui.setup.OnboardingScreen
import com.sushant.saveo.ui.setup.SetupScreen
import com.sushant.saveo.ui.theme.LocalSaveo
import com.sushant.saveo.ui.viewer.ViewerScreen

/**
 * Root: screens, adaptive layout (bottom-free phone layout; navigation rail from 600dp; grid + viewer side by
 * side from 840dp), the viewer overlay, snackbars, lock screen and Back handling.
 */
@Composable
fun AppRoot(
    vm: MainViewModel,
    authenticate: (onOk: () -> Unit) -> Unit,
    lockAvailable: Boolean,
    onUnlockRequest: () -> Unit,
    onExit: () -> Unit,
) {
    val screen by vm.screen.collectAsStateWithLifecycle()
    val viewer by vm.viewer.collectAsStateWithLifecycle()
    val locked by vm.locked.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val tab by vm.tab.collectAsStateWithLifecycle()
    val snackHost = remember { SnackbarHostState() }
    val ctx = LocalContext.current

    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val ids = vm.pendingDeleteIds
        vm.pendingDeleteIds = emptyList()
        if (r.resultCode == android.app.Activity.RESULT_OK) vm.deleteConfirmed(ids)
    }

    LaunchedEffect(vm) {
        vm.events.collect { e ->
            when (e) {
                is UiEvent.Snack -> {
                    snackHost.currentSnackbarData?.dismiss()
                    val r = snackHost.showSnackbar(e.text, e.action, duration = if (e.action != null) SnackbarDuration.Long else SnackbarDuration.Short)
                    if (r == SnackbarResult.ActionPerformed) e.onAction?.invoke()
                }
                is UiEvent.Launch -> runCatching { ctx.startActivity(e.intent) }.onFailure { vm.showSnack(ctx.getString(R.string.snack_no_whatsapp)) }
                UiEvent.DismissSnack -> snackHost.currentSnackbarData?.dismiss()
                is UiEvent.ConfirmDelete -> { vm.pendingDeleteIds = e.ids; deleteLauncher.launch(IntentSenderRequest.Builder(e.sender).build()) }
            }
        }
    }

    LaunchedEffect(screen) { snackHost.currentSnackbarData?.dismiss() }

    BackHandler(enabled = !locked) { if (!vm.back()) onExit() }
    BackHandler(enabled = locked) { onExit() }

    androidx.compose.material3.Surface(color = MaterialTheme.colorScheme.background, contentColor = MaterialTheme.colorScheme.onBackground) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val rail = maxWidth >= 600.dp && screen != Screen.ONBOARD && screen != Screen.SETUP
        val twoPane = maxWidth >= 840.dp && screen == Screen.HOME
        val shortWindow = maxHeight < 520.dp
        vm.twoPane = twoPane
        val statuses by vm.statuses.collectAsStateWithLifecycle()
        val savedList by vm.saved.collectAsStateWithLifecycle()
        // Two panes: always show something on the right
        LaunchedEffect(twoPane, viewer == null, tab, statuses.isEmpty(), savedList.isEmpty()) {
            if (twoPane && viewer == null) (if (tab == 0) statuses else savedList).firstOrNull()?.let { vm.openViewer(tab == 1, it.key) }
        }
        androidx.compose.runtime.CompositionLocalProvider(com.sushant.saveo.ui.home.LocalShortWindow provides shortWindow) {
        Row(Modifier.fillMaxSize()) {
            if (rail) SideRail(
                current = if (screen == Screen.HOME) tab else 2,
                onTab = { t -> vm.closeViewer(); if (t == 2) vm.go(Screen.SETTINGS) else { vm.go(Screen.HOME); vm.setTab(t) } },
            )
            Box(Modifier.weight(1f).fillMaxHeight()) {
                AnimatedContent(screen, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(160)) }, label = "screen") { s ->
                    Box(Modifier.fillMaxSize().then(if (s == Screen.HOME) Modifier else Modifier.widthIn(max = 900.dp))) {
                        when (s) {
                            Screen.ONBOARD -> OnboardingScreen(onStart = { vm.openSetup(false) })
                            Screen.SETUP -> SetupScreen(vm, onContinue = {
                                if (!settings.onboarded) vm.finishOnboarding() else vm.back()
                            }, onBack = { vm.back() })
                            Screen.HOME -> HomeScreen(vm, showTabs = !rail)
                            Screen.SETTINGS -> SettingsScreen(vm, authenticate, lockAvailable, onBack = { vm.back() })
                            Screen.BIN -> BinScreen(vm, onBack = { vm.back() })
                            Screen.DIAG -> DiagnosticsScreen(vm, onBack = { vm.back() })
                            Screen.ABOUT -> AboutScreen(onBack = { vm.back() })
                        }
                    }
                }
                // Phone / medium: viewer covers the screen
                if (!twoPane) androidx.compose.animation.AnimatedVisibility(viewer != null && screen == Screen.HOME, enter = fadeIn(tween(180)), exit = fadeOut(tween(160))) {
                    viewer?.let { ViewerScreen(vm, it, inPane = false) }
                }
            }
            if (twoPane) Box(Modifier.weight(0.85f).fillMaxHeight()) {
                val v = viewer
                if (v != null) ViewerScreen(vm, v, inPane = true)
                else PanePlaceholder()
            }
        }
        }
        SnackbarHost(snackHost, Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = if (viewer != null && !twoPane) 96.dp else if (screen == Screen.HOME) 112.dp else 8.dp).widthIn(max = 560.dp)) { d ->
            val ex = LocalSaveo.current
            Snackbar(d, containerColor = ex.snackBg, contentColor = androidx.compose.ui.graphics.Color.White, actionColor = ex.snackAction, shape = MaterialTheme.shapes.medium, modifier = Modifier.padding(horizontal = 16.dp))
        }
        if (locked && settings.appLock) LockScreen(onUnlock = onUnlockRequest)
    }
    }
}

@Composable
private fun SideRail(current: Int, onTab: (Int) -> Unit) {
    NavigationRail(Modifier.fillMaxHeight().statusBarsPadding(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Spacer(Modifier.height(20.dp))
        NavigationRailItem(current == 0, { onTab(0) }, icon = { Icon(Icons.Rounded.GridView, null) }, label = { Text(stringResource(R.string.tab_statuses)) }, modifier = Modifier.testTag("rail_statuses"))
        NavigationRailItem(current == 1, { onTab(1) }, icon = { Icon(Icons.Rounded.Collections, null) }, label = { Text(stringResource(R.string.tab_saved)) }, modifier = Modifier.testTag("rail_saved"))
        NavigationRailItem(current == 2, { onTab(2) }, icon = { Icon(if (current == 2) Icons.Rounded.Settings else Icons.Outlined.Settings, null) }, label = { Text(stringResource(R.string.settings)) })
    }
}

@Composable
private fun PanePlaceholder() {
    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerLow), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
            Icon(Icons.Rounded.Collections, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.pane_hint), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(260.dp))
        }
    }
}
