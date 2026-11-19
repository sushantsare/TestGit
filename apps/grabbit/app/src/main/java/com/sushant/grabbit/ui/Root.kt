package com.sushant.grabbit.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

@Composable
fun GrabbitRoot(vm: MainViewModel) {
    val site = vm.loginSite
    if (site != null) {
        LoginScreen(site, onClose = { vm.loginSite = null; vm.loginTick++ })
        return
    }
    BackHandler(enabled = vm.tab != 0) { vm.tab = 0 }

    val works by vm.works.collectAsState()
    val active = works.count { !it.state.isFinished }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = vm.tab == 0, onClick = { vm.tab = 0 }, modifier = Modifier.testTag("nav_home"),
                    icon = { Icon(Icons.Rounded.Home, null) }, label = { Text("Grab") },
                )
                NavigationBarItem(
                    selected = vm.tab == 1, onClick = { vm.tab = 1 }, modifier = Modifier.testTag("nav_downloads"),
                    icon = {
                        BadgedBox(badge = { if (active > 0) Badge { Text("$active") } }) {
                            Icon(Icons.Rounded.Download, null)
                        }
                    },
                    label = { Text("Downloads") },
                )
                NavigationBarItem(
                    selected = vm.tab == 2, onClick = { vm.tab = 2 }, modifier = Modifier.testTag("nav_settings"),
                    icon = { Icon(Icons.Rounded.Settings, null) }, label = { Text("Settings") },
                )
            }
        },
    ) { pad ->
        val m = Modifier.padding(pad)
        when (vm.tab) {
            0 -> HomeScreen(vm, m)
            1 -> DownloadsScreen(vm, m)
            else -> SettingsScreen(vm, m)
        }
    }
}
