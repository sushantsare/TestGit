package com.sushant.gymmanager.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EventAvailable
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sushant.gymmanager.BuildConfig
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.ui.attendance.AttendanceScreen
import com.sushant.gymmanager.ui.backup.BackupScreen
import com.sushant.gymmanager.ui.dashboard.DashboardScreen
import com.sushant.gymmanager.ui.members.AddEditMemberScreen
import com.sushant.gymmanager.ui.members.DeactivatedMembersScreen
import com.sushant.gymmanager.ui.members.MasterListScreen
import com.sushant.gymmanager.ui.payments.PaymentsScreen
import com.sushant.gymmanager.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val DASHBOARD = "dashboard"
    const val MEMBERS = "members"
    const val DEACTIVATED = "members/deactivated"
    const val EDIT_MEMBER = "member/edit/{memberId}"
    const val ATTENDANCE = "attendance"
    const val PAYMENTS = "payments"
    const val BACKUP = "backup"
    const val SETTINGS = "settings"
    fun editMember(id: Long) = "member/edit/$id"
}

private data class DrawerDestination(val route: String, val label: String, val icon: ImageVector)

private val destinations = listOf(
    DrawerDestination(Routes.DASHBOARD, "Dashboard", Icons.Default.Dashboard),
    DrawerDestination(Routes.MEMBERS, "Master List", Icons.Default.Groups),
    DrawerDestination(Routes.ATTENDANCE, "Attendance", Icons.Default.EventAvailable),
    DrawerDestination(Routes.PAYMENTS, "Payments", Icons.Default.Payments),
    DrawerDestination(Routes.BACKUP, "Backup", Icons.Default.Backup),
    DrawerDestination(Routes.SETTINGS, "Settings", Icons.Default.Settings)
)

fun NavHostController.navigateTopLevel(route: String) {
    navigate(route) {
        popUpTo(Routes.DASHBOARD) { inclusive = route == Routes.DASHBOARD }
        launchSingleTop = true
    }
}

@Composable
fun MainNavigation(settings: GymSettingsEntity) {
    val nav = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route
    val isTopLevel = destinations.any { it.route == route }

    val openDrawer: () -> Unit = { scope.launch { drawerState.open() } }
    BackHandler(enabled = drawerState.isOpen) { scope.launch { drawerState.close() } }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = isTopLevel || drawerState.isOpen,
        drawerContent = {
            AppDrawer(gymName = settings.gymName, currentRoute = route) { target ->
                scope.launch { drawerState.close() }
                if (target != route) nav.navigateTopLevel(target)
            }
        }
    ) {
        NavHost(navController = nav, startDestination = Routes.DASHBOARD) {
            composable(Routes.DASHBOARD) {
                DashboardScreen(
                    onOpenDrawer = openDrawer,
                    onNavigate = { nav.navigateTopLevel(it) },
                    onAddMember = { nav.navigate(Routes.editMember(0)) },
                    onEditMember = { nav.navigate(Routes.editMember(it)) }
                )
            }
            composable(Routes.MEMBERS) {
                MasterListScreen(
                    onOpenDrawer = openDrawer,
                    onAddMember = { nav.navigate(Routes.editMember(0)) },
                    onEditMember = { nav.navigate(Routes.editMember(it)) },
                    onOpenDeactivated = { nav.navigate(Routes.DEACTIVATED) }
                )
            }
            composable(Routes.DEACTIVATED) {
                DeactivatedMembersScreen(
                    onBack = { nav.popBackStack() },
                    onEditMember = { nav.navigate(Routes.editMember(it)) }
                )
            }
            composable(
                Routes.EDIT_MEMBER,
                arguments = listOf(navArgument("memberId") { type = NavType.LongType })
            ) { entry ->
                AddEditMemberScreen(
                    memberId = entry.arguments?.getLong("memberId") ?: 0L,
                    onDone = { nav.popBackStack() }
                )
            }
            composable(Routes.ATTENDANCE) {
                AttendanceScreen(onOpenDrawer = openDrawer, onEditMember = { nav.navigate(Routes.editMember(it)) })
            }
            composable(Routes.PAYMENTS) { PaymentsScreen(onOpenDrawer = openDrawer) }
            composable(Routes.BACKUP) {
                BackupScreen(onOpenDrawer = openDrawer, onRestored = { nav.navigateTopLevel(Routes.DASHBOARD) })
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(onOpenDrawer = openDrawer, onDataReset = { nav.navigateTopLevel(Routes.DASHBOARD) })
            }
        }
    }
}

@Composable
private fun AppDrawer(gymName: String, currentRoute: String?, onSelect: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    ModalDrawerSheet(
        modifier = Modifier.width(300.dp).testTag("drawer"),
        drawerContainerColor = scheme.surface,
        drawerShape = RoundedCornerShape(topEnd = 24.dp, bottomEnd = 24.dp)
    ) {
        Column(Modifier.fillMaxHeight()) {
            Box(
                Modifier.fillMaxWidth()
                    .background(Brush.linearGradient(listOf(scheme.primary, Color(0xFFC2410C))))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                Column {
                    Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.18f)) {
                        Box(Modifier.size(56.dp), contentAlignment = Alignment.Center) {
                            Icon(Icons.Default.FitnessCenter, null, tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        gymName,
                        style = MaterialTheme.typography.titleLarge,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text("Admin Portal", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                }
            }
            Spacer(Modifier.height(12.dp))
            destinations.forEach { d ->
                NavigationDrawerItem(
                    icon = { Icon(d.icon, null) },
                    label = { Text(d.label, style = MaterialTheme.typography.labelLarge) },
                    selected = currentRoute == d.route,
                    onClick = { onSelect(d.route) },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp).testTag("drawer_${d.route}"),
                    colors = NavigationDrawerItemDefaults.colors(
                        selectedContainerColor = scheme.primaryContainer,
                        selectedIconColor = scheme.primary,
                        selectedTextColor = scheme.onPrimaryContainer
                    )
                )
            }
            Spacer(Modifier.weight(1f))
            HorizontalDivider(Modifier.padding(horizontal = 24.dp), color = scheme.outlineVariant)
            Text(
                "Gym Manager v${BuildConfig.VERSION_NAME} • 100% offline",
                style = MaterialTheme.typography.labelSmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
