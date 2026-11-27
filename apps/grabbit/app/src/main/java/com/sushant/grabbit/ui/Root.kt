package com.sushant.grabbit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.sushant.grabbit.ui.theme.Motion

private data class Dest(val label: String, val tag: String, val on: ImageVector, val off: ImageVector)

private val DESTS = listOf(
    Dest("Grab", "nav_home", Icons.Rounded.Home, Icons.Outlined.Home),
    Dest("Downloads", "nav_downloads", Icons.Rounded.Download, Icons.Outlined.Download),
    Dest("Settings", "nav_settings", Icons.Rounded.Settings, Icons.Outlined.Settings),
)

@Composable
fun GrabbitRoot(vm: MainViewModel) {
    val snackbar = remember { SnackbarHostState() }
    // Messages always go away after a few seconds — with accessibility services on (password managers,
    // screen readers…) newer Compose can keep a snackbar up indefinitely, covering buttons underneath
    LaunchedEffect(Unit) {
        UiMessages.flow.collect { msg ->
            snackbar.currentSnackbarData?.dismiss()
            kotlinx.coroutines.withTimeoutOrNull(3500) { snackbar.showSnackbar(msg) }
        }
    }

    val site = vm.loginSite
    if (site != null) {
        Box(Modifier.fillMaxSize()) {
            LoginScreen(site, onClose = { vm.loginSite = null; vm.loginTick++ })
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
        }
        return
    }
    BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }

    val works by vm.works.collectAsState()
    val active = works.count { !it.state.isFinished }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer) {
                DESTS.forEachIndexed { i, d ->
                    val selected = vm.tab == i
                    NavigationBarItem(
                        selected = selected,
                        onClick = { vm.tab = i },
                        modifier = Modifier.testTag(d.tag),
                        icon = {
                            BadgedBox(badge = { if (i == 1 && active > 0) Badge { Text("$active") } }) {
                                Icon(if (selected) d.on else d.off, null)
                            }
                        },
                        label = { Text(d.label, style = MaterialTheme.typography.labelMedium) },
                    )
                }
            }
        },
    ) { pad ->
        // Fade-through between unrelated top-level destinations
        AnimatedContent(
            targetState = vm.tab,
            transitionSpec = {
                fadeIn(tween(Motion.MEDIUM2, delayMillis = Motion.SHORT2, easing = Motion.EmphasizedDecelerate)) togetherWith
                    fadeOut(tween(Motion.SHORT2, easing = Motion.EmphasizedAccelerate))
            },
            label = "tabs",
            modifier = Modifier.fillMaxSize().padding(pad),
        ) { tab ->
            Box(Modifier.fillMaxSize()) {
                when (tab) {
                    0 -> HomeScreen(vm, Modifier)
                    1 -> DownloadsScreen(vm, Modifier)
                    else -> SettingsScreen(vm, Modifier)
                }
            }
        }
    }
}
