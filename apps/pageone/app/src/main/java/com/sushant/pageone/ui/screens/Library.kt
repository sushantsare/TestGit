package com.sushant.pageone.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.FileProvider
import androidx.navigation.NavHostController
import com.sushant.pageone.data.*
import com.sushant.pageone.ui.MainVm
import com.sushant.pageone.ui.Routes
import com.sushant.pageone.ui.components.*
import com.sushant.pageone.ui.goTab
import com.sushant.pageone.ui.theme.Sizes
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.roundToInt

private fun countText(n: Int) = if (n == 1) "1 PDF" else "$n PDFs"

fun openAllFilesSettings(context: android.content.Context) {
    val i = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:" + context.packageName))
    runCatching { context.startActivity(i) }.onFailure { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
}

// ======================= HOME =======================

@Composable
fun HomeScreen(vm: MainVm, nav: NavHostController) {
    val wide = LocalConfigurationWidth() >= 840
    if (wide) {
        var selected by rememberSaveable { mutableStateOf<String?>(null) }
        Row(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)) {
            SideRail(Tab.HOME, { nav.goTab(it) }) {
                Box { ToolsFab(vm, nav, compact = true) }
            }
            Box(Modifier.width(440.dp).fillMaxHeight()) { HomeList(vm, nav, onFolder = { selected = it }, selectedFolder = selected, showFab = false, showNav = false) }
            Surface(Modifier.weight(1f).fillMaxHeight().statusBarsPadding().padding(16.dp), shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                val folder = selected ?: vm.lib.pdfs.collectAsState().value.groupBy { it.folder }.keys.minOrNull()
                if (folder != null) FolderContent(vm, nav, folder, embedded = true)
                else EmptyState(EmptyType.CLEARED, "Pick a folder", "Its PDFs show up here.", Modifier.padding(top = 120.dp), icon = Icons.Rounded.Folder, kind = ExpressiveKind.CLOVER)
            }
        }
    } else {
        Box(Modifier.fillMaxSize()) { HomeList(vm, nav, onFolder = { nav.navigate(Routes.folder(it)) }, selectedFolder = null, showFab = true, showNav = true) }
    }
}

@Composable
fun LocalConfigurationWidth(): Int = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp

@Composable
private fun HomeList(vm: MainVm, nav: NavHostController, onFolder: (String) -> Unit, selectedFolder: String?, showFab: Boolean, showNav: Boolean) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val pdfs by vm.lib.pdfs.collectAsState()
    val recents by vm.lib.recents.collectAsState()
    val positions by vm.lib.positions.collectAsState()
    val scan by vm.lib.scan.collectAsState()
    val access by vm.access.collectAsState()
    val thumbs by vm.lib.thumbVersion.collectAsState()
    val folders = remember(pdfs, s.folderSort, s.folderSortAsc) { vm.lib.folders(pdfs, s.folderSort, s.folderSortAsc) }
    val total = remember(pdfs) { pdfs.sumOf { it.size } }
    val last = recents.firstOrNull()

    Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("home_list"), contentPadding = PaddingValues(bottom = 120.dp)) {
            item {
                Spacer(Modifier.statusBarsPadding())
                LargeTitle(
                    "PageOne",
                    when {
                        !access -> "Allow access to find your PDFs"
                        scan.running && pdfs.isEmpty() -> "Finding your PDFs…"
                        else -> "${countText(pdfs.size)} · ${Format.size(total)} on this phone"
                    },
                ) { TonalIconButton(Icons.Rounded.Settings, "Settings", { nav.navigate(Routes.SETTINGS) }, Modifier.testTag("open_settings")) }
            }
            item { SearchPill("Search your PDFs", { nav.navigate(Routes.SEARCH) }) }
            if (last != null) item {
                val pos = positions[last.fingerprint]
                ContinueCard(vm, last, pos?.page ?: 0, thumbs) {
                    vm.viewModelScopeLaunch { nav.navigate(MainVm.readerRoute(vm.lib.resolve(last.fingerprint, last.name, last.uri, last.path))) }
                }
            }
            if (!access) item { AccessCard(onAllow = { openAllFilesSettings(context) }, onOpen = { /* handled below */ }, vm = vm, nav = nav) }
            else if (scan.running && pdfs.isEmpty()) {
                item { ScanningCard(scan.found) }
                items(4) { i -> Box(Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp)) { SkeletonRow(i, 4) } }
            } else if (pdfs.isEmpty() && scan.done) item {
                EmptyState(EmptyType.FIRST_TIME, "No PDFs found", "PDFs you download or receive on WhatsApp and Telegram show up here.",
                    Modifier.padding(top = 32.dp), icon = Icons.Rounded.PictureAsPdf, kind = ExpressiveKind.COOKIE)
            } else {
                item {
                    SectionHeader("Folders") {
                        ConnectedButtons(
                            listOf("Name", "Date", "Size"), s.folderSort.ordinal, { i -> vm.update { it.copy(folderSort = SortKey.entries[i]) } },
                            trailingIcon = if (s.folderSortAsc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            onTrailing = { vm.update { it.copy(folderSortAsc = !it.folderSortAsc) } }, tagPrefix = "folder_sort",
                        )
                    }
                }
                itemsIndexed(folders, key = { _, f -> f.path }) { i, f ->
                    Box(Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp)) {
                        FolderRow(i, folders.size, f.name, "${countText(f.count)} · ${Format.size(f.size)}", { onFolder(f.path) }, selected = f.path == selectedFolder)
                    }
                }
            }
        }
        if (showNav) BottomNav(Tab.HOME) { nav.goTab(it) }
    }
    if (showFab) ToolsFab(vm, nav, compact = false)
    }
}

internal fun MainVm.viewModelScopeLaunch(block: suspend () -> Unit) {
    a.appScope.launch(kotlinx.coroutines.Dispatchers.Main) { block() }
}

@Composable
private fun ContinueCard(vm: MainVm, r: RecentEntity, page: Int, thumbs: Int, onResume: () -> Unit) {
    val key = r.path ?: r.uri
    LaunchedEffect(key) { vm.lib.ensureThumb(DocRef(r.uri, r.path, r.name)) }
    Surface(
        onClick = onResume, shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp).testTag("continue_card"),
    ) {
        Row(Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            PdfThumb(vm.lib.thumbFile(key), thumbs, width = 84.dp, height = 112.dp)
            Column(Modifier.weight(1f)) {
                Text("Continue reading", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(r.name.removeSuffix(".pdf"), style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (r.pages > 0) Text("${page + 1} / ${r.pages} pages", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (r.pages > 0) ProgressLine((page + 1f) / r.pages, Modifier.padding(top = 6.dp), height = 5.dp)
                Button(onClick = onResume, modifier = Modifier.padding(top = 12.dp).testTag("resume"), contentPadding = PaddingValues(start = 12.dp, end = 18.dp)) {
                    Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text("Resume")
                }
            }
        }
    }
}

@Composable
private fun AccessCard(onAllow: () -> Unit, onOpen: () -> Unit, vm: MainVm, nav: NavHostController) {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            val name = Library.displayName(ctx.contentResolver, uri) ?: "Document.pdf"
            nav.navigate(MainVm.readerRoute(DocRef(uri.toString(), null, name)))
        }
    }
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("access_card")) {
        Column(Modifier.padding(horizontal = 24.dp, vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ShapedIcon(Icons.Rounded.FolderOpen, ExpressiveKind.COOKIE, 88.dp, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, iconSize = 40.dp)
            Spacer(Modifier.height(18.dp))
            Text("Find every PDF on your phone", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(10.dp))
            Text("PageOne needs “All files access” to list your folders. Your files never leave this phone.", style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            Spacer(Modifier.height(22.dp))
            Button(onClick = onAllow, modifier = Modifier.testTag("allow_access")) { Text("Allow access") }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = { pick.launch(arrayOf("application/pdf")) }, modifier = Modifier.testTag("open_a_file")) {
                Icon(Icons.Rounded.Description, null); Spacer(Modifier.width(8.dp)); Text("Open a file")
            }
        }
    }
}

@Composable
private fun ScanningCard(found: Int) {
    Surface(shape = RoundedCornerShape(32.dp), color = MaterialTheme.colorScheme.surfaceContainer, modifier = Modifier.fillMaxWidth().padding(16.dp).testTag("scanning")) {
        Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            ExpressiveSpinner()
            Spacer(Modifier.height(12.dp))
            Text("Scanning your phone", style = MaterialTheme.typography.titleMedium)
            Text(if (found > 0) "$found PDFs found so far" else "Looking in every folder…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun ExpressiveSpinner(modifier: Modifier = Modifier) { LoadingIndicator(modifier.size(56.dp)) }

// ======================= + TOOLS MENU =======================

@Composable
fun BoxScope.ToolsFab(vm: MainVm, nav: NavHostController, compact: Boolean) {
    var open by rememberSaveable { mutableStateOf(false) }
    BackHandler(open) { open = false }
    if (!compact) {
        AnimatedVisibility(open, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.matchParentSize()) {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f))
                .clickable(remember { MutableInteractionSource() }, null) { open = false })
        }
    }
    val items = listOf(
        Triple(Icons.Rounded.MergeType, "Merge PDFs & images", Routes.merge()),
        Triple(Icons.Rounded.ContentCut, "Split or extract pages", Routes.split()),
        Triple(Icons.Rounded.Compress, "Compress PDF", Routes.compress()),
    )
    val align = if (compact) Alignment.TopStart else Alignment.BottomEnd
    Column(
        Modifier.align(align).then(if (compact) Modifier else Modifier.navigationBarsPadding().padding(end = 16.dp, bottom = 96.dp)),
        horizontalAlignment = if (compact) Alignment.Start else Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (!compact) AnimatedVisibility(open, enter = fadeIn() + expandVertically(expandFrom = Alignment.Bottom), exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Bottom)) {
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items.forEachIndexed { i, (icon, label, route) ->
                    Surface(onClick = { open = false; nav.navigate(route) }, shape = RoundedCornerShape(28.dp), color = MaterialTheme.colorScheme.primaryContainer,
                        shadowElevation = 3.dp, modifier = Modifier.height(56.dp).testTag("tool_$i")) {
                        Row(Modifier.padding(start = 18.dp, end = 22.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer); Spacer(Modifier.width(12.dp))
                            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }
                }
            }
        }
        val size by animateFloatAsState(if (open) 56f else if (compact) 56f else 80f, label = "fab")
        val corner by animateFloatAsState(if (open) 28f else if (compact) 18f else 24f, label = "fabc")
        if (compact) Box {
            var menu by remember { mutableStateOf(false) }
            Surface(onClick = { menu = true }, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer, shadowElevation = 3.dp, modifier = Modifier.size(56.dp).testTag("tools_fab")) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, "Page tools") }
            }
            DropdownMenu(menu, { menu = false }) {
                items.forEach { (icon, label, route) -> DropdownMenuItem({ Text(label) }, { menu = false; nav.navigate(route) }, leadingIcon = { Icon(icon, null) }) }
            }
        } else Surface(
            onClick = { open = !open },
            shape = RoundedCornerShape(corner.dp),
            color = if (open) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (open) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onPrimaryContainer,
            shadowElevation = 6.dp, modifier = Modifier.padding(end = if (open) 12.dp else 0.dp).size(size.dp).testTag("tools_fab"),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(if (open) Icons.Rounded.Close else Icons.Rounded.Add, if (open) "Close" else "Page tools", Modifier.size(if (open) 24.dp else 32.dp))
            }
        }
    }
}

// ======================= FOLDER =======================

@Composable
fun FolderScreen(vm: MainVm, nav: NavHostController, path: String) {
    FolderContent(vm, nav, path, embedded = false)
}

@Composable
fun PdfRow(
    vm: MainVm, p: PdfEntity, index: Int, count: Int, s: Settings, thumbs: Int,
    selected: Boolean = false, selecting: Boolean = false, onClick: () -> Unit, onLong: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null, nameOverride: AnnotatedString? = null, metaPrefix: String? = null,
) {
    val positions by vm.lib.positions.collectAsState()
    val stars by vm.lib.stars.collectAsState()
    val meta = listOfNotNull(
        metaPrefix,
        if (s.rowPages && p.pages > 0) (if (p.pages == 1) "1 page" else "${p.pages} pages") else if (s.rowPages && p.pages < 0) "Protected" else null,
        if (s.rowSizeDate) Format.size(p.size) else null,
        if (s.rowSizeDate) Format.shortDate(p.modified) else null,
    ).joinToString(" · ")
    val prog = if (s.rowProgress && p.fingerprint.isNotEmpty() && p.pages > 0) positions[p.fingerprint]?.let { (it.page + 1f) / p.pages } else null
    val starred = p.fingerprint.isNotEmpty() && stars.any { it.fingerprint == p.fingerprint }
    GroupedRow(index, count, onClick, Modifier.testTag("pdf_row"), onLongClick = onLong, selected = selected, minHeight = Sizes.pdfRowMin) {
        if (s.rowThumb) PdfThumb(vm.lib.thumbFile(p.path), thumbs)
        Column(Modifier.weight(1f)) {
            if (nameOverride != null) Text(nameOverride, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            else Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta.isNotEmpty()) Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (prog != null) ProgressLine(prog, Modifier.padding(top = 6.dp))
        }
        when {
            selecting -> Icon(if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked, null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline)
            trailing != null -> trailing()
            else -> IconButton(onClick = { vm.toggleStarPath(p) }, modifier = Modifier.testTag("star_toggle")) {
                Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (starred) "Remove star" else "Star",
                    tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
fun FolderContent(vm: MainVm, nav: NavHostController, path: String, embedded: Boolean) {
    val context = LocalContext.current
    val s by vm.settings.collectAsState()
    val all by vm.lib.pdfs.collectAsState()
    val thumbs by vm.lib.thumbVersion.collectAsState()
    val items = remember(all, path, s.pdfSort, s.pdfSortAsc) { vm.lib.sortPdfs(all.filter { it.folder == path }, s.pdfSort, s.pdfSortAsc) }
    var selected by remember(path) { mutableStateOf(setOf<String>()) }
    var confirmDelete by remember { mutableStateOf(false) }
    val selecting = selected.isNotEmpty()
    BackHandler(selecting) { selected = emptySet() }
    val scope = rememberCoroutineScope()

    Column(Modifier.fillMaxSize()) {
        if (selecting) {
            Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainer).then(if (embedded) Modifier else Modifier.statusBarsPadding())
                .padding(horizontal = 4.dp, vertical = 8.dp).testTag("selection_bar"), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { selected = emptySet() }) { Icon(Icons.Rounded.Close, "Cancel selection") }
                Text("${selected.size} selected", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = { shareMany(context, selected.toList()) }) { Icon(Icons.Rounded.Share, "Share") }
                IconButton(onClick = {
                    items.filter { it.path in selected }.forEach { vm.toggleStarPath(it) }; selected = emptySet()
                }) { Icon(Icons.Rounded.StarBorder, "Star") }
                IconButton(onClick = { confirmDelete = true }, modifier = Modifier.testTag("delete_selected")) { Icon(Icons.Rounded.Delete, "Delete") }
                IconButton(onClick = { selected = items.map { it.path }.toSet() }) { Icon(Icons.Rounded.SelectAll, "Select all") }
            }
        } else if (embedded) {
            SmallTopBar(Format.folderName(path), "${countText(items.size)} · ${Format.size(items.sumOf { it.size })}", onBack = null, modifier = Modifier.padding(top = 0.dp))
        } else {
            SmallTopBar(Format.folderName(path), "${countText(items.size)} · ${Format.size(items.sumOf { it.size })}", onBack = { nav.popBackStack() }) {
                IconButton(onClick = { nav.navigate(Routes.SEARCH) }) { Icon(Icons.Rounded.Search, "Search") }
            }
        }
        LazyColumn(Modifier.weight(1f).testTag("folder_list"), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                SectionHeader("PDFs") {
                    ConnectedButtons(listOf("Name", "Date", "Size"), s.pdfSort.ordinal, { i -> vm.update { it.copy(pdfSort = SortKey.entries[i]) } },
                        trailingIcon = if (s.pdfSortAsc) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                        onTrailing = { vm.update { it.copy(pdfSortAsc = !it.pdfSortAsc) } }, tagPrefix = "pdf_sort")
                }
            }
            if (items.isEmpty()) item {
                EmptyState(EmptyType.CLEARED, "No PDFs here", "This folder has no PDFs any more.", Modifier.padding(top = 60.dp), icon = Icons.Rounded.FolderOff, kind = ExpressiveKind.CLOVER)
            }
            itemsIndexed(items, key = { _, p -> p.path }) { i, p ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp)) {
                    PdfRow(vm, p, i, items.size, s, thumbs, selected = p.path in selected, selecting = selecting,
                        onClick = { if (selecting) selected = if (p.path in selected) selected - p.path else selected + p.path else nav.navigate(MainVm.readerRoute(vm.refOf(p))) },
                        onLong = { selected = selected + p.path })
                }
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = { Icon(Icons.Rounded.Delete, null) },
            title = { Text(if (selected.size == 1) "Delete this PDF?" else "Delete ${selected.size} PDFs?") },
            text = { Text("They will be removed from your phone. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    val paths = selected.toList(); confirmDelete = false; selected = emptySet()
                    scope.launch {
                        val n = deleteFiles(context, paths)
                        vm.lib.scan(force = true)
                        vm.message(if (n == paths.size) "Deleted ${countText(n)}" else "Deleted $n of ${paths.size}. Some files couldn't be removed")
                    }
                }, modifier = Modifier.testTag("confirm_delete")) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

fun shareMany(context: android.content.Context, paths: List<String>) {
    val uris = ArrayList(paths.map { FileProvider.getUriForFile(context, context.packageName + ".files", File(it)) })
    val i = Intent(if (uris.size == 1) Intent.ACTION_SEND else Intent.ACTION_SEND_MULTIPLE).setType("application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (uris.size == 1) i.putExtra(Intent.EXTRA_STREAM, uris[0]) else i.putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
    context.startActivity(Intent.createChooser(i, null))
}

suspend fun deleteFiles(context: android.content.Context, paths: List<String>): Int = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
    var n = 0
    for (p in paths) {
        val f = File(p)
        if (f.delete() || !f.exists()) n++
        android.media.MediaScannerConnection.scanFile(context, arrayOf(p), null, null)
    }
    n
}

// ======================= SEARCH =======================

@Composable
fun SearchScreen(vm: MainVm, nav: NavHostController) {
    val s by vm.settings.collectAsState()
    val all by vm.lib.pdfs.collectAsState()
    val stars by vm.lib.stars.collectAsState()
    val thumbs by vm.lib.thumbVersion.collectAsState()
    var q by rememberSaveable { mutableStateOf("") }
    var filter by rememberSaveable { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val weekAgo = remember { System.currentTimeMillis() - 7 * 86_400_000L }
    val results = remember(q, filter, all, stars) {
        val starFps = stars.map { it.fingerprint }.toSet()
        all.asSequence()
            .filter { q.isBlank() || it.name.contains(q.trim(), ignoreCase = true) }
            .filter { when (filter) { 1 -> it.fingerprint in starFps; 2 -> it.modified >= weekAgo; else -> true } }
            .sortedByDescending { it.modified }.take(300).toList()
    }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth().height(56.dp).clip(RoundedCornerShape(28.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh).padding(horizontal = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Box(Modifier.weight(1f)) {
                if (q.isEmpty()) Text("Search your PDFs", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(q, { q = it }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary), keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { }), modifier = Modifier.fillMaxWidth().focusRequester(focus).testTag("search_field"))
            }
            if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Rounded.Close, "Clear") }
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All folders", "Starred", "This week").forEachIndexed { i, l ->
                FilterChip(filter == i, { filter = i }, { Text(l) }, leadingIcon = if (filter == i) ({ Icon(Icons.Rounded.Check, null, Modifier.size(18.dp)) }) else null,
                    modifier = Modifier.testTag("search_filter_$i"))
            }
        }
        Spacer(Modifier.height(8.dp))
        if (results.isEmpty()) {
            EmptyState(EmptyType.NO_RESULTS, if (q.isBlank()) "Nothing here yet" else "No results for “${q.trim()}”",
                if (filter != 0) "Try All folders, or check the spelling." else "Check the spelling or try fewer words.", Modifier.padding(top = 80.dp),
                actionLabel = if (filter != 0) "Clear filters" else null, onAction = if (filter != 0) ({ filter = 0 }) else null)
        } else LazyColumn(Modifier.weight(1f).testTag("search_results"), contentPadding = PaddingValues(bottom = 32.dp)) {
            itemsIndexed(results, key = { _, p -> p.path }) { i, p ->
                Box(Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp)) {
                    PdfRow(vm, p, i, results.size, s.copy(rowSizeDate = false), thumbs, onClick = { nav.navigate(MainVm.readerRoute(vm.refOf(p))) },
                        nameOverride = highlight(p.name, q.trim()), metaPrefix = Format.folderName(p.folder))
                }
            }
            item { Text(if (results.size == 1) "1 result" else "${results.size} results", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 28.dp, vertical = 14.dp)) }
        }
    }
}

fun highlight(text: String, q: String): AnnotatedString = buildAnnotatedString {
    if (q.isEmpty()) { append(text); return@buildAnnotatedString }
    var i = 0
    while (true) {
        val j = text.indexOf(q, i, ignoreCase = true)
        if (j < 0) { append(text.substring(i)); break }
        append(text.substring(i, j))
        withStyle(SpanStyle(fontWeight = FontWeight.Black)) { append(text.substring(j, j + q.length)) }
        i = j + q.length
    }
}

// ======================= RECENT =======================

@Composable
fun RecentScreen(vm: MainVm, nav: NavHostController) {
    val recents by vm.lib.recents.collectAsState()
    val positions by vm.lib.positions.collectAsState()
    val stars by vm.lib.stars.collectAsState()
    val thumbs by vm.lib.thumbVersion.collectAsState()
    var menu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.lib.cleanRecents() }
    val groups = remember(recents) { recents.groupBy { Format.dayGroup(it.openedAt) } }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("recent_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Spacer(Modifier.statusBarsPadding())
                LargeTitle("Recent", if (recents.isEmpty()) null else "Last opened first") {
                    if (recents.isNotEmpty()) Box {
                        TonalIconButton(Icons.Rounded.MoreVert, "More options", { menu = true }, Modifier.testTag("recent_menu"))
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Clear all history") }, { menu = false; confirmClear = true }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null) },
                                modifier = Modifier.testTag("clear_history"))
                        }
                    }
                }
            }
            if (recents.isEmpty()) item {
                EmptyState(EmptyType.FIRST_TIME, "Nothing opened yet", "PDFs you open from PageOne, WhatsApp or any app show up here.",
                    Modifier.padding(top = 100.dp), icon = Icons.Rounded.History, kind = ExpressiveKind.CLOVER,
                    container = MaterialTheme.colorScheme.secondaryContainer, content = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            for (day in Format.Day.entries) {
                val list = groups[day].orEmpty()
                if (list.isEmpty()) continue
                item(key = "h_$day") { SectionHeader(when (day) { Format.Day.TODAY -> "Today"; Format.Day.YESTERDAY -> "Yesterday"; else -> "Earlier" }, color = MaterialTheme.colorScheme.primary) }
                itemsIndexed(list, key = { _, r -> r.fingerprint }) { i, r ->
                    val pos = positions[r.fingerprint]
                    val prog = if (r.pages > 0 && pos != null) (pos.page + 1f) / r.pages else null
                    val finished = pos != null && r.pages > 0 && pos.page >= r.pages - 1
                    val meta = Format.opened(r.openedAt) + when {
                        finished -> " · finished"
                        pos != null && r.pages > 0 && pos.page > 0 -> " · page ${pos.page + 1} of ${r.pages}"
                        else -> ""
                    }
                    SwipeToRemove(onRemove = { vm.removeRecent(r) }, modifier = Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp), index = i, count = list.size) {
                        RefRow(vm, r.fingerprint, r.name, r.uri, r.path, meta, prog, i, list.size, thumbs, starred = stars.any { it.fingerprint == r.fingerprint },
                            onStar = { vm.toggleStar(DocRef(r.uri, r.path, r.name), r.fingerprint, r.pages) },
                            onClick = { vm.viewModelScopeLaunch { nav.navigate(MainVm.readerRoute(vm.lib.resolve(r.fingerprint, r.name, r.uri, r.path))) } })
                    }
                }
            }
        }
        BottomNav(Tab.RECENT) { nav.goTab(it) }
    }
    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Clear all history?") },
        text = { Text("Recent will be empty. Your PDFs, stars and reading positions stay.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearRecents() }, modifier = Modifier.testTag("confirm_clear")) { Text("Clear") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
    )
}

/** Row for something identified by fingerprint (Recent, Starred). */
@Composable
fun RefRow(
    vm: MainVm, fp: String, name: String, uri: String, path: String?, meta: String, progress: Float?, index: Int, count: Int, thumbs: Int,
    starred: Boolean, onStar: () -> Unit, onClick: () -> Unit, trailing: (@Composable () -> Unit)? = null, container: androidx.compose.ui.graphics.Color? = null,
) {
    val s by vm.settings.collectAsState()
    val key = path ?: uri
    LaunchedEffect(key) { vm.lib.ensureThumb(DocRef(uri, path, name)) }
    GroupedRow(index, count, onClick, Modifier.testTag("ref_row"), minHeight = Sizes.pdfRowMin, container = container) {
        if (s.rowThumb) PdfThumb(vm.lib.thumbFile(key), thumbs)
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(meta, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (s.rowProgress && progress != null) ProgressLine(progress, Modifier.padding(top = 6.dp))
        }
        if (trailing != null) trailing()
        else IconButton(onClick = onStar) {
            Icon(if (starred) Icons.Rounded.Star else Icons.Rounded.StarBorder, if (starred) "Remove star" else "Star",
                tint = if (starred) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** Swipe from end to start to remove (red background with an icon); also works as a plain row. */
@Composable
fun SwipeToRemove(onRemove: () -> Unit, modifier: Modifier = Modifier, index: Int, count: Int, content: @Composable () -> Unit) {
    var offset by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableIntStateOf(1) }
    val anim by animateFloatAsState(offset, label = "swipe")
    Box(modifier.onWidth { width = it }) {
        if (anim < -4f) Box(Modifier.matchParentSize().clip(com.sushant.pageone.ui.theme.GroupShapes.of(index, count)).background(MaterialTheme.colorScheme.errorContainer),
            contentAlignment = Alignment.CenterEnd) {
            Icon(Icons.Rounded.DeleteSweep, "Remove", tint = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.padding(end = 24.dp))
        }
        Box(Modifier.offset { IntOffset(anim.roundToInt(), 0) }.pointerInput(Unit) {
            detectHorizontalDragGestures(
                onDragEnd = { if (offset < -width * 0.4f) { offset = -width.toFloat(); onRemove() }; offset = 0f },
                onDragCancel = { offset = 0f },
            ) { _, d -> offset = (offset + d).coerceIn(-width.toFloat(), 0f) }
        }) { content() }
    }
}

private fun Modifier.onWidth(f: (Int) -> Unit) = this.onSizeChanged { f(it.width) }

// ======================= STARRED =======================

@Composable
fun StarredScreen(vm: MainVm, nav: NavHostController) {
    val stars by vm.lib.stars.collectAsState()
    val positions by vm.lib.positions.collectAsState()
    val thumbs by vm.lib.thumbVersion.collectAsState()
    var order by remember(stars) { mutableStateOf(stars) }
    var dragging by remember { mutableStateOf<String?>(null) }
    var dragY by remember { mutableFloatStateOf(0f) }
    val rowPx = with(LocalDensity.current) { 75.dp.toPx() }
    Column(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.weight(1f).testTag("starred_list"), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Spacer(Modifier.statusBarsPadding())
                LargeTitle("Starred", if (stars.isEmpty()) null else "Drag to reorder")
                Spacer(Modifier.height(12.dp))
            }
            if (order.isEmpty()) item {
                EmptyState(EmptyType.FIRST_TIME, "No starred PDFs yet", "Tap the star on any PDF to keep it here for quick access.",
                    Modifier.padding(top = 100.dp), icon = Icons.Rounded.Star, kind = ExpressiveKind.SUNNY)
            }
            itemsIndexed(order, key = { _, st -> st.fingerprint }) { i, st ->
                val pos = positions[st.fingerprint]
                val isDrag = dragging == st.fingerprint
                Box(Modifier.padding(horizontal = 12.dp, vertical = 1.5.dp).zIndex(if (isDrag) 1f else 0f)
                    .graphicsLayer { translationY = if (isDrag) dragY else 0f; scaleX = if (isDrag) 1.02f else 1f; scaleY = if (isDrag) 1.02f else 1f }
                    .then(if (isDrag) Modifier.shadow(6.dp, RoundedCornerShape(16.dp)) else Modifier)) {
                    RefRow(vm, st.fingerprint, st.name, st.uri, st.path,
                        listOfNotNull(if (st.pages > 0) "${st.pages} pages" else null, pos?.let { "page ${it.page + 1}" }).joinToString(" · "),
                        if (st.pages > 0 && pos != null) (pos.page + 1f) / st.pages else null, i, order.size, thumbs, starred = true, onStar = {},
                        onClick = { vm.viewModelScopeLaunch { nav.navigate(MainVm.readerRoute(vm.lib.resolve(st.fingerprint, st.name, st.uri, st.path))) } },
                        trailing = {
                            Row {
                                IconButton(onClick = { vm.toggleStar(DocRef(st.uri, st.path, st.name), st.fingerprint, st.pages) }) {
                                    Icon(Icons.Rounded.Star, "Remove star", tint = MaterialTheme.colorScheme.primary)
                                }
                                Box(Modifier.size(48.dp).testTag("drag_handle").pointerInput(st.fingerprint) {
                                    detectDragGestures(
                                        onDragStart = { dragging = st.fingerprint; dragY = 0f },
                                        onDragEnd = { dragging = null; dragY = 0f; vm.reorderStars(order) },
                                        onDragCancel = { dragging = null; dragY = 0f },
                                    ) { change, d ->
                                        change.consume()
                                        dragY += d.y
                                        val idx = order.indexOfFirst { it.fingerprint == st.fingerprint }
                                        if (dragY > rowPx * 0.6f && idx < order.size - 1) {
                                            order = order.toMutableList().apply { add(idx + 1, removeAt(idx)) }; dragY -= rowPx
                                        } else if (dragY < -rowPx * 0.6f && idx > 0) {
                                            order = order.toMutableList().apply { add(idx - 1, removeAt(idx)) }; dragY += rowPx
                                        }
                                    }
                                }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.DragIndicator, "Drag to reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                            }
                        })
                }
            }
        }
        BottomNav(Tab.STARRED) { nav.goTab(it) }
    }
}
