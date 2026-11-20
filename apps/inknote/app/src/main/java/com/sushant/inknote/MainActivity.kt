package com.sushant.inknote

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import com.sushant.inknote.data.Kind
import com.sushant.inknote.data.NoteItem
import com.sushant.inknote.editor.EditorPane
import com.sushant.inknote.editor.EditorViewModel
import com.sushant.inknote.io.Backup
import com.sushant.inknote.ui.Dates
import com.sushant.inknote.ui.MainViewModel
import com.sushant.inknote.ui.NoteThumb
import com.sushant.inknote.ui.home.FolderDialog
import com.sushant.inknote.ui.home.HomeActions
import com.sushant.inknote.ui.home.HomeScreen
import com.sushant.inknote.ui.home.MoveSheet
import com.sushant.inknote.ui.home.NavContent
import com.sushant.inknote.ui.home.NewNoteSheet
import com.sushant.inknote.ui.home.Place
import com.sushant.inknote.ui.lock.LockScreen
import com.sushant.inknote.ui.lock.SetPinDialog
import com.sushant.inknote.ui.screens.AboutScreen
import com.sushant.inknote.ui.screens.PagesScreen
import com.sushant.inknote.ui.screens.SearchScreen
import com.sushant.inknote.ui.screens.SettingsScreen
import com.sushant.inknote.ui.screens.TrashScreen
import com.sushant.inknote.ui.theme.InkTheme
import com.sushant.inknote.ui.theme.LocalInk
import com.sushant.inknote.ui.theme.isDark
import com.sushant.inknote.ui.toast
import com.sushant.inknote.work.IndexWorker
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private val vm: MainViewModel by viewModels()
    private var stoppedAt = 0L
    /** An incoming share/open intent, handled once the UI is ready. */
    val pendingIntent = mutableStateOf<Intent?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) pendingIntent.value = intent?.takeIf { isIncoming(it) }
        val app = application as InkApp
        setContent {
            val st by app.settings.state.collectAsState()
            val dark = isDark(st.theme)
            LaunchedEffect(st.theme) {
                androidx.appcompat.app.AppCompatDelegate.setDefaultNightMode(when (st.theme) {
                    com.sushant.inknote.data.ThemeMode.LIGHT -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_NO
                    com.sushant.inknote.data.ThemeMode.DARK -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_YES
                    else -> androidx.appcompat.app.AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                })
            }
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(Color.TRANSPARENT) else SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT),
                )
                onDispose { }
            }
            InkTheme(dark) { InkRoot(vm, this) }
        }
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); if (isIncoming(intent)) pendingIntent.value = intent }

    override fun onStart() {
        super.onStart()
        // Locked notes lock again after the app has been in the background for 30 seconds.
        if (stoppedAt > 0 && SystemClock.elapsedRealtime() - stoppedAt > 30_000) vm.unlocked.value = emptySet()
    }

    override fun onStop() { super.onStop(); stoppedAt = SystemClock.elapsedRealtime() }

    fun setSecure(on: Boolean) { if (on) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE) else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }

    private fun isIncoming(i: Intent) = i.action == Intent.ACTION_SEND || i.action == Intent.ACTION_SEND_MULTIPLE || i.action == Intent.ACTION_VIEW
}

object Routes {
    const val HOME = "home"; const val SEARCH = "search"; const val TRASH = "trash"; const val SETTINGS = "settings"; const val ABOUT = "about"
    fun note(id: Long, page: Int = -1, voice: Boolean = false) = "note/$id?page=$page&voice=$voice"
    fun pages(id: Long) = "pages/$id"
}

@Suppress("DEPRECATION")
private fun Intent.streams(): List<Uri> = when (action) {
    Intent.ACTION_SEND -> listOfNotNull(if (Build.VERSION.SDK_INT >= 33) getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableExtra(Intent.EXTRA_STREAM))
    Intent.ACTION_SEND_MULTIPLE -> (if (Build.VERSION.SDK_INT >= 33) getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java) else getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)).orEmpty()
    Intent.ACTION_VIEW -> listOfNotNull(data)
    else -> emptyList()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InkRoot(vm: MainViewModel, activity: MainActivity) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkApp
    val nav = rememberNavController()
    val scope = rememberCoroutineScope()
    val home by vm.home.collectAsState()
    val st by vm.settings.collectAsState()
    val selected by vm.selected.collectAsState()
    val unlocked by vm.unlocked.collectAsState()
    val trash by vm.trash.collectAsState()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var newSheet by rememberSaveable { mutableStateOf(false) }
    var folderEdit by rememberSaveable { mutableStateOf<Long?>(null) } // -1 = new
    var moveSheet by rememberSaveable { mutableStateOf(false) }
    var setPinThenLock by rememberSaveable { mutableStateOf(false) }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route ?: Routes.HOME

    // Navigation must always happen on the main thread.
    fun openNote(id: Long, page: Int = -1, voice: Boolean = false) = activity.runOnUiThread { vm.clearSelection(); nav.navigate(Routes.note(id, page, voice)) { launchSingleTop = true } }
    fun goTop(r: String) = activity.runOnUiThread { scope.launch { drawer.close() }; nav.navigate(r) { popUpTo(Routes.HOME); launchSingleTop = true } }

    val importPdf = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { vm.importPdf(uri).onSuccess { openNote(it) }.onFailure { ctx.toast(ctx.getString(R.string.pdf_failed)) } }
    }
    val scanner = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        val uris = GmsDocumentScanningResult.fromActivityResultIntent(r.data)?.pages?.map { it.imageUri }.orEmpty()
        if (uris.isNotEmpty()) scope.launch { openNote(vm.createWithImages(uris)) }
    }
    fun scan() {
        val o = GmsDocumentScannerOptions.Builder().setGalleryImportAllowed(true).setResultFormats(GmsDocumentScannerOptions.RESULT_FORMAT_JPEG).setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL).build()
        GmsDocumentScanning.getClient(o).getStartScanIntent(activity).addOnSuccessListener { scanner.launch(IntentSenderRequest.Builder(it).build()) }
            .addOnFailureListener { ctx.toast(ctx.getString(R.string.scan_unavailable)) }
    }

    // ---- share into InkNote / open files ----
    val incoming by activity.pendingIntent
    LaunchedEffect(incoming) {
        val i = incoming ?: return@LaunchedEffect
        activity.pendingIntent.value = null
        val uris = i.streams()
        val type = i.type ?: uris.firstOrNull()?.let { ctx.contentResolver.getType(it) } ?: ""
        val name = uris.firstOrNull()?.let { app.repo.displayName(it) } ?: ""
        runCatching {
            when {
                name.endsWith(".inknote", true) || name.endsWith(".inkbackup", true) -> {
                    val r = Backup(ctx, app.repo).restore(uris.first(), null); IndexWorker.schedule(ctx, 1)
                    ctx.toast(ctx.resources.getQuantityString(R.plurals.restore_done, r.notes, r.notes))
                }
                type == "application/pdf" || name.endsWith(".pdf", true) -> vm.importPdf(uris.first()).onSuccess { openNote(it) }.onFailure { ctx.toast(ctx.getString(R.string.pdf_failed)) }
                type.startsWith("image/") && uris.isNotEmpty() -> openNote(vm.createWithImages(uris))
                i.getStringExtra(Intent.EXTRA_TEXT) != null -> {
                    val text = i.getStringExtra(Intent.EXTRA_TEXT)!!
                    val title = i.getStringExtra(Intent.EXTRA_SUBJECT)?.takeIf { it.isNotBlank() } ?: text.lineSequence().first().take(40).ifBlank { "Shared text" }
                    openNote(vm.createTextNote(title, text))
                }
                else -> ctx.toast(ctx.getString(R.string.cant_open_shared))
            }
        }.onFailure { ctx.toast(ctx.getString(R.string.cant_open_shared)) }
    }

    androidx.activity.compose.BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    androidx.activity.compose.BackHandler(enabled = selected.isNotEmpty() && route == Routes.HOME) { vm.clearSelection() }
    BoxWithConstraints(Modifier.fillMaxSize().background(LocalInk.current.bg)) {
        val expanded = maxWidth >= 840.dp
        val canSplit = maxWidth >= 600.dp
        val navContent: @Composable (Place) -> Unit = { place ->
            NavContent(home, place, onFilter = { vm.setFilter(it); goTop(Routes.HOME) }, onTrash = { goTop(Routes.TRASH) }, onSettings = { goTop(Routes.SETTINGS) },
                onNewFolder = { folderEdit = -1L }, onEditFolder = { folderEdit = it.id })
        }
        val actions = HomeActions(
            openNote = { openNote(it) }, openDrawer = { scope.launch { drawer.open() } }, newNote = { newSheet = true }, search = { nav.navigate(Routes.SEARCH) },
            settings = { goTop(Routes.SETTINGS) }, importPdf = { importPdf.launch(arrayOf("application/pdf")) }, scan = ::scan, setFilter = vm::setFilter,
            toggleSelect = vm::toggleSelect, clearSelection = vm::clearSelection, pin = { vm.pin(it) }, move = { moveSheet = true },
            lock = { ids, v -> if (v && !app.securePrefs.hasPin.value) setPinThenLock = true else vm.lock(ids, v) }, delete = { vm.trashNotes(it); ctx.toast(ctx.resources.getQuantityString(R.plurals.moved_to_trash_n, it.size, it.size)) },
            setGrid = vm::setGrid, setSort = vm::setSort,
        )
        val place = when (route) { Routes.TRASH -> Place.TRASH; Routes.SETTINGS, Routes.ABOUT -> Place.SETTINGS; else -> Place.HOME }
        val withPanel: @Composable (@Composable () -> Unit) -> Unit = { content ->
            if (expanded) Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout)) {
                Box(Modifier.width(280.dp).fillMaxHeight().background(LocalInk.current.card).statusBarsPadding()) { navContent(place) }
                VerticalDivider(color = LocalInk.current.divider)
                Box(Modifier.weight(1f).statusBarsPadding()) { content() }
            } else content()
        }
        ModalNavigationDrawer(drawerState = drawer, gesturesEnabled = !expanded && route in listOf(Routes.HOME, Routes.TRASH, Routes.SETTINGS),
            drawerContent = { if (!expanded) ModalDrawerSheet(drawerContainerColor = LocalInk.current.card, modifier = Modifier.width(300.dp)) { Box(Modifier.statusBarsPadding()) { navContent(place) } } }) {
            NavHost(nav, Routes.HOME, Modifier.fillMaxSize()) {
                composable(Routes.HOME) { withPanel { HomeScreen(home, selected, st.gridView, st.sortBy, expanded, actions) } }
                composable(Routes.SEARCH) { withPanel { SearchScreen(home.all, { nav.popBackStack() }, { id, page -> openNote(id, page) }, expanded) } }
                composable(Routes.TRASH) {
                    withPanel { TrashScreen(trash, expanded, { nav.popBackStack() }, { vm.restore(setOf(it)); ctx.toast(ctx.getString(R.string.restored)) }, { vm.deleteForever(setOf(it)) }, { vm.emptyTrash() }) }
                }
                composable(Routes.SETTINGS) { withPanel { SettingsScreen(st, { f -> app.settings.update(f) }, expanded, { nav.popBackStack() }, { nav.navigate(Routes.ABOUT) }) } }
                composable(Routes.ABOUT) { withPanel { AboutScreen(expanded) { nav.popBackStack() } } }
                composable("note/{id}?page={page}&voice={voice}", arguments = listOf(navArgument("id") { type = NavType.LongType },
                    navArgument("page") { type = NavType.IntType; defaultValue = -1 }, navArgument("voice") { type = NavType.BoolType; defaultValue = false })) { e ->
                    val id = e.arguments!!.getLong("id")
                    NoteRoute(id, e.arguments!!.getInt("page"), e.arguments!!.getBoolean("voice"), home.all, unlocked, expanded, canSplit, vm, activity, nav, ::openNote)
                }
                composable("pages/{id}", arguments = listOf(navArgument("id") { type = NavType.LongType })) { e ->
                    val id = e.arguments!!.getLong("id")
                    val evm: EditorViewModel = viewModel(nav.previousBackStackEntry ?: e, key = "editor_$id", factory = EditorViewModel.factory(id))
                    Box(Modifier.fillMaxSize().then(if (expanded) Modifier.statusBarsPadding() else Modifier)) { PagesScreen(evm, st, { nav.popBackStack() }, expanded) }
                }
            }
        }
    }

    if (newSheet) NewNoteSheet(st, false, null, { newSheet = false },
        onCreate = { spec -> newSheet = false; scope.launch { openNote(vm.newNote(spec.kind, spec.orientation, spec.paper, spec.pageType)) } },
        onOpenPdf = { newSheet = false; importPdf.launch(arrayOf("application/pdf")) },
        onVoice = { newSheet = false; scope.launch { openNote(vm.newNote(Kind.INK, st.defaultOrientation, st.defaultPaper, st.defaultPageType), voice = true) } })
    folderEdit?.let { fid ->
        val f = home.folders.firstOrNull { it.id == fid }
        FolderDialog(f, { folderEdit = null }, { name, color -> if (f == null) vm.addFolder(name, color) else vm.renameFolder(f, name, color); folderEdit = null },
            if (f == null) null else ({ vm.deleteFolder(f.id); folderEdit = null }))
    }
    if (moveSheet) MoveSheet(home.folders, null, { moveSheet = false }, { vm.move(selected, it); moveSheet = false; ctx.toast(ctx.getString(R.string.moved)) }, { moveSheet = false; folderEdit = -1L })
    if (setPinThenLock) SetPinDialog({ setPinThenLock = false }) { setPinThenLock = false; vm.lock(selected, true) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NoteRoute(
    id: Long, page: Int, voice: Boolean, all: List<NoteItem>, unlocked: Set<Long>, expanded: Boolean, canSplit: Boolean,
    vm: MainViewModel, activity: MainActivity, nav: NavHostController, openNote: (Long, Int, Boolean) -> Unit,
) {
    val ctx = LocalContext.current
    val app = ctx.applicationContext as InkApp
    val st by vm.settings.collectAsState()
    val home by vm.home.collectAsState()
    val item = all.firstOrNull { it.note.id == id }
    var beside by rememberSaveable { mutableStateOf<Long?>(null) }
    var pick by rememberSaveable { mutableStateOf(false) }
    val locked = item?.note?.locked == true
    DisposableEffect(locked) { activity.setSecure(locked); onDispose { activity.setSecure(false) } }
    if (locked && id !in unlocked) {
        LockScreen(item!!.note.title, { vm.unlocked.value = vm.unlocked.value + id }, { nav.popBackStack() }, expanded)
        return
    }
    val evm: EditorViewModel = viewModel(key = "editor_$id", factory = EditorViewModel.factory(id))
    LaunchedEffect(item == null, home.loaded) { if (home.loaded && item == null && evm.note.value?.deletedAt != null) nav.popBackStack() }
    val editor: @Composable () -> Unit = {
        Row(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f)) {
                EditorPane(evm, false, canSplit, home.folders, home.tags, st, { f -> app.settings.update(f) }, { nav.popBackStack() }, { nav.navigate(Routes.pages(id)) }, { pick = true }, page, voice)
            }
            val b = beside
            if (b != null && canSplit) {
                VerticalDivider(color = LocalInk.current.divider)
                Box(Modifier.weight(1f)) {
                    val bvm: EditorViewModel = viewModel(key = "editor_$b", factory = EditorViewModel.factory(b))
                    EditorPane(bvm, true, false, home.folders, home.tags, st, { f -> app.settings.update(f) }, { beside = null }, {}, {})
                }
            }
        }
    }
    if (expanded) {
        Row(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.displayCutout).statusBarsPadding()) {
            if (beside == null) {
                Column(Modifier.width(340.dp).fillMaxHeight().background(LocalInk.current.card)) {
                    Row(Modifier.padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text(stringResource(R.string.all_notes), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Outlined.Search, stringResource(R.string.search)) }
                    }
                    LazyColumn(Modifier.testTag("mini_list")) {
                        items(home.shown, key = { it.note.id }) { n ->
                            ListItem(headlineContent = { Text(n.note.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(Dates.short(n.note.updatedAt)) },
                                leadingContent = { NoteThumb(n.note, Modifier.size(width = 48.dp, height = 58.dp)) },
                                colors = ListItemDefaults.colors(containerColor = if (n.note.id == id) MaterialTheme.colorScheme.primaryContainer else LocalInk.current.card),
                                modifier = Modifier.heightIn(min = 72.dp).clickable { if (n.note.id != id) { nav.popBackStack(); openNote(n.note.id, -1, false) } })
                        }
                    }
                }
                VerticalDivider(color = LocalInk.current.divider)
            }
            Box(Modifier.weight(1f)) { editor() }
        }
    } else Box(Modifier.fillMaxSize().statusBarsPadding()) { editor() }

    if (pick) {
        ModalBottomSheet(onDismissRequest = { pick = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            Column(Modifier.navigationBarsPadding().padding(bottom = 12.dp)) {
                Text(stringResource(R.string.open_beside), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 20.dp))
                Text(stringResource(R.string.open_beside_sub), style = MaterialTheme.typography.bodyMedium, color = LocalInk.current.muted, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
                HorizontalDivider(color = LocalInk.current.divider)
                LazyColumn {
                    items(home.all.filter { it.note.id != id && !it.note.locked }, key = { it.note.id }) { n ->
                        ListItem(headlineContent = { Text(n.note.title, maxLines = 1, overflow = TextOverflow.Ellipsis) }, supportingContent = { Text(Dates.short(n.note.updatedAt)) },
                            leadingContent = { NoteThumb(n.note, Modifier.size(width = 44.dp, height = 54.dp)) },
                            modifier = Modifier.clickable { beside = n.note.id; pick = false }.testTag("beside_${n.note.title}"))
                    }
                }
            }
        }
    }
}

