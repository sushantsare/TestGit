package com.sushant.speedtile

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sushant.speedtile.ui.AboutScreen
import com.sushant.speedtile.ui.HomeScreen
import com.sushant.speedtile.ui.Notice
import com.sushant.speedtile.ui.SettingsScreen
import com.sushant.speedtile.ui.SpeedTileTheme
import com.sushant.speedtile.ui.isDark

class MainActivity : ComponentActivity() {

    /** Bumped when the tile asks us to start, so the screen shows the location prompt. */
    private val startRequest = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null && intent?.action == ACTION_START_FROM_TILE) startRequest.intValue++
        setContent {
            val prefs = app.prefs
            val themeMode by prefs.theme.collectAsStateWithLifecycle()
            val dark = isDark(themeMode)
            LaunchedEffect(dark) {
                val style = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
            }
            SpeedTileTheme(dark) { AppRoot() }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == ACTION_START_FROM_TILE) startRequest.intValue++
    }

    @Composable
    private fun AppRoot() {
        val prefs = app.prefs
        val engine = app.engine
        val state by engine.state.collectAsStateWithLifecycle()
        val decimals by prefs.decimals.collectAsStateWithLifecycle()
        val gaugeMax by prefs.gaugeMax.collectAsStateWithLifecycle()
        val autoStop by prefs.autoStopMin.collectAsStateWithLifecycle()
        val keepOn by prefs.keepScreenOn.collectAsStateWithLifecycle()
        val offOnClose by prefs.offOnPanelClose.collectAsStateWithLifecycle()
        val themeMode by prefs.theme.collectAsStateWithLifecycle()

        // Re-check permissions and GPS every time the app comes back to the front.
        var resumeTick by remember { mutableIntStateOf(0) }
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) resumeTick++ }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }
        var locDeniedOnce by rememberSaveable { mutableStateOf(false) }
        val notice = remember(resumeTick, locDeniedOnce, state.running, state.status) { computeNotice(locDeniedOnce) }

        var showLocRationale by rememberSaveable { mutableStateOf(false) }
        var showResetConfirm by rememberSaveable { mutableStateOf(false) }
        var showBgExplain by rememberSaveable { mutableStateOf(false) }
        val inPlace = remember(resumeTick) { hasBackgroundLocation(this) }
        val bgLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            resumeTick++
            // Android 11+ shows no dialog once refused: send the user to the app's location setting instead.
            if (!granted && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) openAppSettings()
        }

        fun startNow() {
            if (!isGpsOn(this)) {
                resumeTick++
                return
            }
            SpeedService.start(this)
            app.panelWatch.startedFromApp()
        }

        val locLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
            prefs.askedLocation = true
            if (res[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
                locDeniedOnce = false
                startNow()
            } else {
                locDeniedOnce = true
            }
            resumeTick++
        }

        fun onStart() {
            if (hasLocationPermission(this)) startNow() else showLocRationale = true
        }

        val startReq by startRequest
        LaunchedEffect(startReq) { if (startReq > 0) onStart() }

        // Keep the screen awake while the speedometer is visible and speed is on.
        DisposableEffect(keepOn, state.running) {
            if (keepOn && state.running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        val nav = rememberNavController()
        NavHost(nav, startDestination = "home") {
            composable("home") {
                HomeScreen(
                    state = state,
                    decimals = decimals,
                    gaugeMax = gaugeMax,
                    notice = notice,
                    onStart = { onStart() },
                    onStop = { SpeedService.stop(this@MainActivity) },
                    onFixNotice = {
                        when (notice) {
                            Notice.PERMISSION_DENIED -> { showLocRationale = true }
                            Notice.PERMISSION_BLOCKED, Notice.APPROXIMATE_ONLY -> { openAppSettings() }
                            Notice.GPS_OFF -> { openLocationSettings() }
                            Notice.NONE -> {}
                        }
                    },
                    onResetTrip = { showResetConfirm = true },
                    onSettings = { nav.navigate("settings") { launchSingleTop = true } },
                    onAbout = { nav.navigate("about") { launchSingleTop = true } },
                )
            }
            composable("settings") {
                SettingsScreen(
                    theme = themeMode, onTheme = prefs::setTheme,
                    decimals = decimals, onDecimals = prefs::setDecimals,
                    gaugeMax = gaugeMax, onGaugeMax = prefs::setGaugeMax,
                    autoStop = autoStop, onAutoStop = prefs::setAutoStop,
                    keepOn = keepOn, onKeepOn = prefs::setKeepScreenOn,
                    offOnClose = offOnClose, onOffOnClose = prefs::setOffOnPanelClose,
                    inPlace = inPlace, onEnableInPlace = { showBgExplain = true },
                    onBack = { nav.popBackStack() },
                )
            }
            composable("about") {
                AboutScreen(
                    version = BuildConfig.VERSION_NAME,
                    onEmail = { openEmail() },
                    onBack = { nav.popBackStack() },
                )
            }
        }

        if (showLocRationale) {
            AlertDialog(
                onDismissRequest = { showLocRationale = false },
                title = { Text("Allow location") },
                text = {
                    Text(
                        "Speed Tile reads your speed from GPS. Location is used only while speed is on, " +
                            "is never saved and never leaves your phone. Please choose Precise."
                    )
                },
                confirmButton = {
                    TextButton(modifier = Modifier.testTag("dlg_loc_continue"), onClick = {
                        showLocRationale = false
                        if (prefs.askedLocation && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) &&
                            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
                        ) {
                            openAppSettings()
                        } else {
                            locLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                        }
                    }) { Text("Continue") }
                },
                dismissButton = { TextButton(onClick = { showLocRationale = false }) { Text("Not now") } },
            )
        }

        if (showBgExplain) {
            AlertDialog(
                onDismissRequest = { showBgExplain = false },
                title = { Text("Start without closing the panel") },
                text = {
                    Text(
                        "Android lets the tile start GPS by itself only if location is set to \"Allow all the time\". " +
                            "Speed Tile still uses location only while speed is on, never saves it and has no internet. " +
                            "On the next screen choose Allow all the time."
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        showBgExplain = false
                        when {
                            !hasLocationPermission(this) -> showLocRationale = true
                            Build.VERSION.SDK_INT >= 29 -> bgLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                            else -> resumeTick++
                        }
                    }) { Text("Continue") }
                },
                dismissButton = { TextButton(onClick = { showBgExplain = false }) { Text("Not now") } },
            )
        }

        if (showResetConfirm) {
            AlertDialog(
                onDismissRequest = { showResetConfirm = false },
                title = { Text("Reset trip?") },
                text = { Text("Top speed, average, distance and moving time will go back to zero.") },
                confirmButton = {
                    TextButton(modifier = Modifier.testTag("dlg_reset_confirm"), onClick = {
                        showResetConfirm = false
                        engine.resetTrip()
                        toast("Trip reset")
                    }) { Text("Reset") }
                },
                dismissButton = { TextButton(onClick = { showResetConfirm = false }) { Text("Cancel") } },
            )
        }
    }

    private fun computeNotice(deniedOnce: Boolean): Notice {
        val fine = hasLocationPermission(this)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return when {
            !fine && coarse -> Notice.APPROXIMATE_ONLY
            !fine && deniedOnce && !shouldShowRequestPermissionRationale(Manifest.permission.ACCESS_FINE_LOCATION) -> Notice.PERMISSION_BLOCKED
            !fine && deniedOnce -> Notice.PERMISSION_DENIED
            !fine && app.engine.state.value.stopReason == StopReason.NO_PERMISSION -> Notice.PERMISSION_DENIED
            fine && !isGpsOn(this) -> Notice.GPS_OFF
            else -> Notice.NONE
        }
    }

    private fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    private fun openLocationSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
            .onFailure { toast("Open Settings and turn on Location") }
    }

    private fun openEmail() {
        val i = Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:sushant.sare@gmail.com"))
            .putExtra(Intent.EXTRA_SUBJECT, "Speed Tile")
        runCatching { startActivity(i) }.onFailure { toast("No email app found") }
    }

    private fun toast(msg: String) {
        ContextCompat.getMainExecutor(this).execute { Toast.makeText(this, msg, Toast.LENGTH_SHORT).show() }
    }

    companion object {
        const val ACTION_START_FROM_TILE = "com.sushant.speedtile.START_FROM_TILE"
    }
}

