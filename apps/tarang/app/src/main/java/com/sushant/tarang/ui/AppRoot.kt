package com.sushant.tarang.ui

import android.Manifest
import android.app.Activity
import android.content.ContentUris
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.util.UnstableApi
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.sushant.tarang.MainActivity
import com.sushant.tarang.data.Song
import com.sushant.tarang.ui.components.LocalCovers
import com.sushant.tarang.ui.player.PlayerLayer
import com.sushant.tarang.ui.player.SheetState
import com.sushant.tarang.ui.player.rememberSheetState
import com.sushant.tarang.ui.screens.*
import com.sushant.tarang.ui.sheets.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

object R0 {
    const val HOME = "home"; const val FOLDERS = "folders"; const val LIBRARY = "library"; const val STATS = "stats"
    const val SEARCH = "search"; const val SETTINGS = "settings"; const val SCAN_FOLDERS = "scan_folders"
    const val DIAGNOSTICS = "diagnostics"; const val ABOUT = "about"; const val LIKED = "liked"
    fun folder(path: String) = "folder?path=${Uri.encode(path)}"
    fun album(id: Long) = "album/$id"
    fun artist(name: String) = "artist?name=${Uri.encode(name)}"
    fun playlist(id: Long) = "playlist/$id"
    fun mix(kind: String) = "mix/$kind"
    fun edit(id: Long) = "edit/$id"
    fun lyrics(id: Long) = "lyrics/$id"
    val TABS = listOf(HOME, FOLDERS, LIBRARY, STATS)
}

/** Everything a screen needs to move around and open sheets. */
@UnstableApi
class AppNav(
    val nav: NavHostController,
    private val sheetSetter: (Sheet?) -> Unit,
    val player: SheetState,
    private val openPlayerFn: () -> Unit,
) {
    fun go(route: String) = nav.navigate(route) { launchSingleTop = true }
    fun tab(route: String) = nav.navigate(route) {
        popUpTo(R0.HOME) { saveState = true }
        launchSingleTop = true; restoreState = true
    }
    fun back() { if (!nav.popBackStack()) Unit }
    fun sheet(s: Sheet?) = sheetSetter(s)
    fun songMenu(song: Song, playlistId: Long? = null) = sheetSetter(Sheet.SongMenu(song, playlistId))
    fun openPlayer() = openPlayerFn()
}

val LocalNav = staticCompositionLocalOf<AppNav> { error("no nav") }
/** Space the screens leave at the bottom so the mini player and nav bar never cover content. */
val LocalDockPadding = compositionLocalOf { 0.dp }

fun hasAudioPermission(ctx: android.content.Context) = ContextCompat.checkSelfPermission(
    ctx, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE,
) == PackageManager.PERMISSION_GRANTED

@UnstableApi
@Composable
fun AppRoot(vm: MainVm, activity: MainActivity) {
    val ctx = LocalContext.current
    var granted by remember { mutableStateOf(hasAudioPermission(ctx)) }
    var askedOnce by remember { mutableStateOf(vm.prefs.ask.getBoolean("audio_asked", false)) }
    LaunchedEffect(granted) { if (granted) vm.onPermissionGranted() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { granted = hasAudioPermission(ctx) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok; askedOnce = true; vm.prefs.ask.edit().putBoolean("audio_asked", true).apply()
    }
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE
    val permanentlyDenied = askedOnce && !granted && !activity.shouldShowRequestPermissionRationale(perm)

    CompositionLocalProvider(LocalCovers provides vm.repo.covers, LocalVm provides vm) {
        if (!granted) {
            WelcomeScreen(denied = askedOnce, permanentlyDenied = permanentlyDenied,
                onAllow = { launcher.launch(perm) },
                onOpenSettings = { ctx.startActivity(Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", ctx.packageName, null))) })
        } else Shell(vm, activity)
    }
}

@UnstableApi
@Composable
private fun Shell(vm: MainVm, activity: MainActivity) {
    val ctx = LocalContext.current
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val sheetState = rememberSheetState()
    var sheet by remember { mutableStateOf<Sheet?>(null) }
    val snackHost = remember { SnackbarHostState() }
    val ui by vm.player.ui.collectAsState()
    val song by vm.currentSong.collectAsState()
    val liked by vm.liked.collectAsState()
    val played by vm.hub.playedThisSession.collectAsState()
    val ctxKind by vm.hub.contextKind.collectAsState()
    val ctxLabel by vm.hub.contextLabel.collectAsState()
    val sleepEnds by vm.hub.sleepEndsAt.collectAsState()
    val sleepAfter by vm.hub.sleepAfterSong.collectAsState()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route ?: R0.HOME
    val wide = LocalConfiguration.current.screenWidthDp >= 600
    val onTab = route in R0.TABS
    val navVisible = !wide && onTab
    // mini player shows once something has played in this session; before that Home shows "Continue listening"
    val miniVisible = song != null && ui.hasSong && (played || ui.isPlaying)
    // keep showing the last song while the full player is open (e.g. after switching to an empty queue)
    var lastSong by remember { mutableStateOf<Song?>(null) }
    LaunchedEffect(song) { if (song != null) lastSong = song }
    val openPlayer: () -> Unit = { scope.launch { sheetState.progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f)) } }
    val appNav = remember(nav) { AppNav(nav, { sheet = it }, sheetState, openPlayer) }

    LaunchedEffect(Unit) { vm.snack.collect { snackHost.currentSnackbarData?.dismiss(); snackHost.showSnackbar(it) } }

    // requests from the notification, the widget or other apps
    val pending by activity.pendingIntent.collectAsState()
    val lib by vm.library.collectAsState()
    LaunchedEffect(pending, lib.loaded) {
        val i = pending ?: return@LaunchedEffect
        val r = i.getStringExtra(MainActivity.EXTRA_ROUTE)
        if (r != null) {
            sheetState.progress.snapTo(0f)
            if (r in R0.TABS) appNav.tab(r) else nav.navigate(r) { launchSingleTop = true }
            activity.pendingIntent.value = null
        } else if (i.getBooleanExtra(MainActivity.EXTRA_OPEN_PLAYER, false)) { if (ui.hasSong) sheetState.progress.snapTo(1f); activity.pendingIntent.value = null }
        else if (i.action == Intent.ACTION_VIEW && i.data != null && lib.loaded) {
            val id = runCatching { ContentUris.parseId(i.data!!) }.getOrNull()
            val s = id?.let { lib.byId[it] }
            if (s != null) {
                kotlinx.coroutines.withTimeoutOrNull(5000) { vm.player.ui.first { it.connected } }
                vm.player.tapSong(s, listOf(s), "Folder", s.folderName); openPlayer()
            }
            else vm.snack.tryEmit("Tarang can only open songs that are in your music library")
            activity.pendingIntent.value = null
        } else if (i.action != Intent.ACTION_VIEW) activity.pendingIntent.value = null
    }

    // Android's own "Allow" boxes for deleting / editing a song file
    val ask by vm.systemAsk.collectAsState()
    val senderLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        when (val a = vm.systemAsk.value) {
            is SystemAsk.Delete -> if (r.resultCode == Activity.RESULT_OK) vm.afterDelete(a.song) else vm.snack.tryEmit("Nothing was deleted")
            is SystemAsk.Write -> if (r.resultCode == Activity.RESULT_OK) vm.writeTags(a.song, a.edit) else vm.snack.tryEmit("Nothing was changed")
            else -> {}
        }
        vm.systemAsk.value = null
    }
    val legacyLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        val a = vm.systemAsk.value as? SystemAsk.LegacyWrite
        if (a != null) {
            if (!ok) vm.snack.tryEmit("Tarang needs storage access to change files on this Android version")
            else if (a.edit == null) vm.deleteLegacy(a.song) else vm.writeTags(a.song, a.edit)
        }
        vm.systemAsk.value = null
    }
    LaunchedEffect(ask) {
        when (val a = ask) {
            is SystemAsk.Delete -> senderLauncher.launch(IntentSenderRequest.Builder(a.sender).build())
            is SystemAsk.Write -> senderLauncher.launch(IntentSenderRequest.Builder(a.sender).build())
            is SystemAsk.LegacyWrite -> {
                if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                    if (a.edit == null) vm.deleteLegacy(a.song) else vm.writeTags(a.song, a.edit); vm.systemAsk.value = null
                } else legacyLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            null -> {}
        }
    }

    val actions = remember(vm) {
        PlayerActions(
            next = vm.player::next, previous = vm.player::previous, togglePlay = vm.player::togglePlay,
            like = { vm.toggleLike(it) }, seekTo = vm.player::seekTo, toggleShuffle = vm.player::toggleShuffle,
            cycleRepeat = vm.player::cycleRepeat, playAt = vm.player::playAt, removeAt = vm.player::removeAt, move = vm.player::move,
            switchQueue = vm.player::switchQueue, clearQueue = vm.player::clearQueue,
            openLyricsEditor = { id -> scope.launch { sheetState.progress.animateTo(0f, tween(250)) }; nav.navigate(R0.lyrics(id)) },
            openEdit = { id -> scope.launch { sheetState.progress.animateTo(0f, tween(250)) }; nav.navigate(R0.edit(id)) },
        )
    }

    val density = LocalDensity.current
    val sysBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val dock: Dp = sysBottom + (if (navVisible) 76.dp else 0.dp) + (if (miniVisible) (if (navVisible) 76.dp else 86.dp) else 0.dp) + 12.dp

    CompositionLocalProvider(LocalNav provides appNav, LocalPlayerActions provides actions, LocalDockPadding provides dock) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // the screen behind shrinks a little while the player opens (finished by 70% of the way)
            val p = sheetState.progress.value
            val k = (p / 0.7f).coerceIn(0f, 1f)
            Row(Modifier.fillMaxSize().graphicsLayer { val sc = 1f - 0.06f * k; scaleX = sc; scaleY = sc; alpha = if (p >= 0.999f && lastSong != null) 0f else 1f }) {
                if (wide) Rail(route) { appNav.tab(it) }
                Box(Modifier.weight(1f).fillMaxHeight()) { Routes(nav, vm) }
            }
            PlayerLayer(
                state = sheetState, ui = ui, song = if (miniVisible) song else if (sheetState.progress.value > 0f) (song ?: lastSong) else null,
                liked = song?.id?.let { it in liked } == true, navVisible = navVisible, wide = wide,
                contextLine = ctxKind to ctxLabel, sleepActive = sleepEnds > System.currentTimeMillis() || sleepAfter,
                onOpenMenu = { (song ?: lastSong)?.let { sheet = Sheet.PlayerMenu(it) } },
                onSleep = { sheet = Sheet.Sleep }, onSpeed = { sheet = Sheet.Speed }, onSound = { sheet = Sheet.Sound },
                onPlaylist = { song?.let { sheet = Sheet.AddToPlaylist(listOf(it.id)) } },
                navBar = { m -> BottomBar(route, miniVisible, m) { appNav.tab(it) } },
            )
            SnackbarHost(snackHost, Modifier.align(Alignment.BottomCenter).padding(bottom = if (sheetState.progress.value > 0.5f) sysBottom + 80.dp else dock))
            SheetHost(sheet, vm) { sheet = it }
        }
    }
}

@UnstableApi
@Composable
private fun Routes(nav: NavHostController, vm: MainVm) {
    // one overlapping fade for every move: tabs, opening screens, back button and back swipe
    val inT = fadeIn(tween(250, delayMillis = 50, easing = LinearEasing))
    val outT = fadeOut(tween(250, easing = LinearEasing))
    NavHost(nav, startDestination = R0.HOME, enterTransition = { inT }, exitTransition = { outT }, popEnterTransition = { inT }, popExitTransition = { outT }) {
        composable(R0.HOME) { HomeScreen() }
        composable(R0.FOLDERS) { FoldersScreen() }
        composable(R0.LIBRARY) { LibraryScreen() }
        composable(R0.STATS) { StatsScreen() }
        composable(R0.SEARCH) { SearchScreen() }
        composable(R0.SETTINGS) { SettingsScreen() }
        composable(R0.SCAN_FOLDERS) { ScanFoldersScreen() }
        composable(R0.DIAGNOSTICS) { DiagnosticsScreen() }
        composable(R0.ABOUT) { AboutScreen() }
        composable(R0.LIKED) { SongListScreen(ListKind.Liked) }
        composable("folder?path={path}") { SongListScreen(ListKind.FolderList(it.arguments?.getString("path").orEmpty())) }
        composable("album/{id}") { SongListScreen(ListKind.AlbumList(it.arguments?.getString("id")?.toLongOrNull() ?: 0)) }
        composable("artist?name={name}") { SongListScreen(ListKind.ArtistList(it.arguments?.getString("name").orEmpty())) }
        composable("playlist/{id}") { SongListScreen(ListKind.PlaylistList(it.arguments?.getString("id")?.toLongOrNull() ?: 0)) }
        composable("mix/{kind}") { SongListScreen(ListKind.Mix(it.arguments?.getString("kind").orEmpty())) }
        composable("edit/{id}") { EditDetailsScreen(it.arguments?.getString("id")?.toLongOrNull() ?: 0) }
        composable("lyrics/{id}") { LyricsEditScreen(it.arguments?.getString("id")?.toLongOrNull() ?: 0) }
    }
}

private data class TabItem(val route: String, val label: String, val on: ImageVector, val off: ImageVector)
private val TAB_ITEMS = listOf(
    TabItem(R0.HOME, "Home", Icons.Rounded.Home, Icons.Outlined.Home),
    TabItem(R0.FOLDERS, "Folders", Icons.Rounded.Folder, Icons.Outlined.Folder),
    TabItem(R0.LIBRARY, "Library", Icons.Rounded.LibraryMusic, Icons.Outlined.LibraryMusic),
    TabItem(R0.STATS, "Stats", Icons.Rounded.Insights, Icons.Outlined.Insights),
)

/** Bottom part of the capsule dock (old-Tarang shape): flat-ish top that meets the mini player, round bottom. */
@Composable
private fun BottomBar(route: String, miniVisible: Boolean, modifier: Modifier, onTab: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val sysBottom = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()
    val top = if (miniVisible) 10.dp else 34.dp
    Box(modifier.fillMaxWidth().padding(bottom = sysBottom + 4.dp, start = 16.dp, end = 16.dp), contentAlignment = Alignment.BottomCenter) {
        Row(
            Modifier.widthIn(max = 580.dp).fillMaxWidth().height(68.dp)
                .clip(RoundedCornerShape(top, top, 34.dp, 34.dp)).background(cs.surfaceContainerHigh.copy(alpha = 0.95f)).testTag("nav_bar"),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TAB_ITEMS.forEach { t ->
                val sel = route == t.route
                Column(Modifier.weight(1f).fillMaxHeight().clickable(onClickLabel = t.label) { onTab(t.route) }.testTag("tab_${t.route}"),
                    horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Box(Modifier.size(56.dp, 32.dp).clip(RoundedCornerShape(16.dp)).background(if (sel) cs.secondaryContainer else cs.surfaceContainerHigh.copy(alpha = 0f)),
                        contentAlignment = Alignment.Center) {
                        Icon(if (sel) t.on else t.off, null, tint = if (sel) cs.onSecondaryContainer else cs.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(t.label, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                        color = if (sel) cs.onSurface else cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun Rail(route: String, onTab: (String) -> Unit) {
    NavigationRail(containerColor = MaterialTheme.colorScheme.surface, modifier = Modifier.testTag("nav_rail")) {
        Spacer(Modifier.height(12.dp))
        TAB_ITEMS.forEach { t ->
            val sel = route == t.route
            NavigationRailItem(sel, { onTab(t.route) }, icon = { Icon(if (sel) t.on else t.off, null) }, label = { Text(t.label) },
                modifier = Modifier.testTag("tab_${t.route}"))
        }
    }
}
