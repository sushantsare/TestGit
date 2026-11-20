package com.sushant.saveo.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SwapVert
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sushant.saveo.R
import com.sushant.saveo.data.Formats
import com.sushant.saveo.data.LoadState
import com.sushant.saveo.data.Source
import com.sushant.saveo.ui.DateRangeKind
import com.sushant.saveo.ui.MainViewModel
import com.sushant.saveo.ui.Media
import com.sushant.saveo.ui.MediaFilter
import com.sushant.saveo.ui.Screen
import com.sushant.saveo.ui.components.EmptyState
import com.sushant.saveo.ui.components.GlossyButton
import com.sushant.saveo.ui.components.MediaTile
import com.sushant.saveo.ui.components.SaveState
import com.sushant.saveo.ui.components.dpToPx
import com.sushant.saveo.ui.theme.LocalSaveo
import kotlinx.coroutines.launch

/**
 * Home: title bar + Statuses / Saved tabs (swipe between them), filter chips and a 2-column grid of big
 * rounded tiles. [showTabs] is false when the navigation rail switches tabs (tablets / landscape).
 */
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: MainViewModel, showTabs: Boolean, modifier: Modifier = Modifier) {
    val tab by vm.tab.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val statuses by vm.statuses.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    val savedKeys by vm.savedKeys.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val pager = rememberPagerState(initialPage = tab) { 2 }
    val scope = rememberCoroutineScope()

    // keep pager and VM tab in sync both ways
    LaunchedEffect(tab) { if (pager.currentPage != tab) pager.animateScrollToPage(tab) }
    LaunchedEffect(pager) { snapshotFlow { pager.settledPage }.collect { vm.setTab(it) } }

    var sheet by rememberSaveable { mutableStateOf<String?>(null) }
    val selecting = selection != null
    val unsaved = remember(statuses, savedKeys) { statuses.count { it.status != null && it.status.matchKey !in savedKeys } }

    Box(
        modifier.fillMaxSize().onPreviewKeyEvent { e ->
            if (e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
            val list = if (tab == 0) statuses else saved
            when {
                e.isCtrlPressed && e.key == Key.A -> { vm.selectAll(list.map { it.key }); true }
                e.key == Key.Escape && selecting -> { vm.clearSelection(); true }
                e.key == Key.Delete && selecting && tab == 1 -> { vm.delete(list.filter { it.key in selection.orEmpty() }.mapNotNull { it.saved?.id }); true }
                e.isCtrlPressed && e.key == Key.R -> { vm.refresh(userAsked = true); true }
                else -> false
            }
        },
    ) {
        Column(Modifier.fillMaxSize()) {
            if (selecting) SelectionBar(
                count = selection!!.size,
                onClose = vm::clearSelection,
                onSelectAll = { vm.selectAll((if (tab == 0) statuses else saved).map { it.key }) },
            ) else TitleBar(
                title = if (showTabs) stringResource(R.string.app_name) else stringResource(if (tab == 0) R.string.tab_statuses else R.string.tab_saved),
                source = settings.sourceFilter,
                onSource = { sheet = "source" },
                onOpenWhatsApp = vm::openWhatsApp,
                onSelect = { vm.startSelection() },
                onRefresh = { vm.refresh(userAsked = true) },
                onSettings = { vm.go(Screen.SETTINGS) },
            )
            if (showTabs) {
                TabRow(
                    selectedTabIndex = pager.currentPage,
                    containerColor = MaterialTheme.colorScheme.surface,
                    indicator = { pos ->
                        if (pager.currentPage < pos.size) TabRowDefaults.PrimaryIndicator(
                            Modifier.tabIndicatorOffset(pos[pager.currentPage]), width = 64.dp, height = 3.dp, color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    divider = { Box(Modifier.fillMaxWidth().height(1.dp).background(MaterialTheme.colorScheme.outlineVariant)) },
                ) {
                    TabLabel(stringResource(R.string.tab_statuses), statuses.size, pager.currentPage == 0, "tab_statuses") { scope.launch { pager.animateScrollToPage(0) } }
                    TabLabel(stringResource(R.string.tab_saved), saved.size, pager.currentPage == 1, "tab_saved") { scope.launch { pager.animateScrollToPage(1) } }
                }
            }
            HorizontalPager(pager, Modifier.weight(1f), key = { it }, userScrollEnabled = showTabs && !selecting, beyondViewportPageCount = 1) { page ->
                if (page == 0) StatusesPage(vm) else SavedPage(vm, onSort = { sheet = "sort" }, onDates = { sheet = "dates" })
            }
        }

        // Save all
        AnimatedVisibility(
            visible = tab == 0 && !selecting && unsaved > 0,
            enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(16.dp),
        ) {
            GlossyButton(stringResource(R.string.save_all_n, unsaved), { vm.saveMany(vm.unsavedStatuses()) }, icon = Icons.Rounded.Download, modifier = Modifier.testTag("save_all"))
        }

        // Selection actions
        AnimatedVisibility(
            visible = selecting,
            enter = slideInVertically { it } + fadeIn(), exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(16.dp),
        ) {
            val sel = selection.orEmpty()
            val items = (if (tab == 0) statuses else saved).filter { it.key in sel }
            SelectionActions(
                fromSaved = tab == 1,
                count = items.size,
                onSave = { vm.saveMany(items) },
                onShare = { vm.share(items) },
                onRepost = { vm.share(items, repostTo = items.firstOrNull()?.source?.pkg ?: "com.whatsapp") },
                onDelete = { vm.delete(items.mapNotNull { it.saved?.id }) },
                onShow = { vm.setVisible(items.mapNotNull { it.saved?.id }, true) },
                onHide = { vm.setVisible(items.mapNotNull { it.saved?.id }, false) },
            )
        }
    }

    when (sheet) {
        "source" -> OptionSheet(
            title = stringResource(R.string.show_from), onDismiss = { sheet = null },
            options = listOf<Pair<String, Source?>>(stringResource(R.string.all_sources) to null) + Source.entries.map { it.label to it },
            selected = settings.sourceFilter,
        ) { vm.setSourceFilter(it); sheet = null }
        "sort" -> OptionSheet(
            title = stringResource(R.string.sort_title), onDismiss = { sheet = null },
            options = listOf(stringResource(R.string.newest_first) to true, stringResource(R.string.oldest_first) to false),
            selected = settings.sortNewest,
        ) { v -> vm.updateSettings { it.copy(sortNewest = v) }; sheet = null }
        "dates" -> {
            val dk by vm.dateKind.collectAsStateWithLifecycle()
            OptionSheet(
                title = stringResource(R.string.dates_title), onDismiss = { sheet = null },
                options = listOf(
                    stringResource(R.string.dates_any) to DateRangeKind.ANY, stringResource(R.string.dates_today) to DateRangeKind.TODAY,
                    stringResource(R.string.dates_week) to DateRangeKind.WEEK, stringResource(R.string.dates_month) to DateRangeKind.MONTH,
                    stringResource(R.string.dates_pick) to DateRangeKind.CUSTOM,
                ),
                selected = dk,
            ) { k -> if (k == DateRangeKind.CUSTOM) sheet = "picker" else { vm.setDates(k); sheet = null } }
        }
        "picker" -> {
            val st = rememberDateRangePickerState()
            DatePickerDialog(
                onDismissRequest = { sheet = null },
                confirmButton = {
                    TextButton(enabled = st.selectedStartDateMillis != null, onClick = {
                        val a = st.selectedStartDateMillis; val b = st.selectedEndDateMillis ?: a
                        if (a != null && b != null) vm.setDates(DateRangeKind.CUSTOM, a, b)
                        sheet = null
                    }) { Text(stringResource(R.string.done)) }
                },
                dismissButton = { TextButton(onClick = { sheet = null }) { Text(stringResource(R.string.cancel)) } },
            ) { DateRangePicker(st, Modifier.weight(1f)) }
        }
    }
}

@Composable
private fun TabLabel(text: String, count: Int, selected: Boolean, tag: String, onClick: () -> Unit) {
    Tab(selected = selected, onClick = onClick, modifier = Modifier.testTag(tag).heightIn(min = 48.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 12.dp)) {
            Text(text, style = MaterialTheme.typography.titleSmall.copy(fontSize = 15.sp), color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(6.dp))
            Text(
                "$count", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.clip(RoundedCornerShape(9.dp)).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 7.dp, vertical = 1.dp),
            )
        }
    }
}

@Composable
private fun TitleBar(
    title: String, source: Source?, onSource: () -> Unit, onOpenWhatsApp: () -> Unit,
    onSelect: () -> Unit, onRefresh: () -> Unit, onSettings: () -> Unit,
) {
    var menu by remember { mutableStateOf(false) }
    Row(
        Modifier.fillMaxWidth().statusBarsPadding().padding(start = 18.dp, end = 4.dp, top = 6.dp, bottom = 2.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f).widthIn(min = 72.dp).semantics { heading() }, maxLines = 1, softWrap = false)
        val shortLabel = stringResource(when (source) { null -> R.string.src_all_short; Source.WHATSAPP -> R.string.src_wa_short; Source.BUSINESS -> R.string.src_b_short; Source.DUAL -> R.string.src_dual_short })
        Surface(onClick = onSource, shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.heightIn(min = 40.dp).widthIn(max = 150.dp).testTag("source_chip")) {
            Text(
                "$shortLabel ▾", color = MaterialTheme.colorScheme.onSecondaryContainer,
                style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onOpenWhatsApp) { Icon(Icons.AutoMirrored.Rounded.OpenInNew, stringResource(R.string.open_whatsapp)) }
        Box {
            IconButton(onClick = { menu = true }, modifier = Modifier.testTag("overflow")) { Icon(Icons.Rounded.MoreVert, stringResource(R.string.more_options)) }
            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.select)) }, onClick = { menu = false; onSelect() })
                DropdownMenuItem(text = { Text(stringResource(R.string.refresh)) }, onClick = { menu = false; onRefresh() })
                DropdownMenuItem(text = { Text(stringResource(R.string.settings)) }, onClick = { menu = false; onSettings() }, modifier = Modifier.testTag("menu_settings"))
            }
        }
    }
}

@Composable
private fun SelectionBar(count: Int, onClose: () -> Unit, onSelectAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.secondaryContainer).statusBarsPadding().padding(horizontal = 4.dp, vertical = 6.dp).heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, stringResource(R.string.close_selection)) }
        Text(pluralStringResource(R.plurals.n_selected, count, count), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        IconButton(onClick = onSelectAll, modifier = Modifier.testTag("select_all")) { Icon(Icons.Rounded.DoneAll, stringResource(R.string.select_all)) }
    }
}

@Composable
private fun FilterRow(vm: MainViewModel, savedTab: Boolean = false, trailing: @Composable () -> Unit = {}) {
    val raw by vm.filter.collectAsStateWithLifecycle()
    val f = if (!savedTab && raw == MediaFilter.IN_GALLERY) MediaFilter.ALL else raw
    Row(Modifier.fillMaxWidth().padding(start = 2.dp, top = 10.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chips = listOf(MediaFilter.ALL to R.string.filter_all, MediaFilter.PHOTOS to R.string.filter_photos, MediaFilter.VIDEOS to R.string.filter_videos) +
                (if (savedTab) listOf(MediaFilter.IN_GALLERY to R.string.filter_in_gallery) else emptyList())
            chips.forEach { (k, s) -> GlossChip(stringResource(s), f == k, if (k == MediaFilter.IN_GALLERY) Icons.Rounded.Visibility else null) { vm.setFilter(k) } }
        }
        trailing()
    }
}

@Composable
private fun GlossChip(text: String, selected: Boolean, icon: ImageVector? = null, onClick: () -> Unit) {
    val ex = LocalSaveo.current
    FilterChip(
        selected = selected, onClick = onClick,
        label = { Text(text, style = MaterialTheme.typography.labelLarge) },
        leadingIcon = icon?.let { ic -> { Icon(ic, null, Modifier.size(18.dp)) } },
        shape = CircleShape, border = null,
        colors = FilterChipDefaults.filterChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            labelColor = MaterialTheme.colorScheme.onSurface,
            selectedContainerColor = ex.glossBottom,
            selectedLabelColor = ex.onGloss,
        ),
        modifier = Modifier.heightIn(min = 40.dp),
    )
}

private fun groupLabel(g: Formats.DayGroup) = when (g) {
    Formats.DayGroup.TODAY -> R.string.today
    Formats.DayGroup.YESTERDAY -> R.string.yesterday
    Formats.DayGroup.EARLIER -> R.string.earlier
}

/** Adds day-grouped tiles to a grid. */
private fun LazyGridScope.groupedTiles(
    items: List<Media>, timeOf: (Media) -> Long,
    header: @Composable (Formats.DayGroup, Int, Boolean) -> Unit,
    tile: @Composable LazyGridItemScope.(Media) -> Unit,
) {
    val now = System.currentTimeMillis()
    val groups = items.groupBy { Formats.dayGroup(timeOf(it), now) }
    val order = items.map { Formats.dayGroup(timeOf(it), now) }.distinct()
    order.forEachIndexed { idx, g ->
        val list = groups[g].orEmpty()
        item(key = "h_$g", span = { GridItemSpan(maxLineSpan) }, contentType = "header") { header(g, list.size, idx == 0) }
        items(list.size, key = { list[it].key }, contentType = { "tile" }) { i -> tile(list[i]) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StatusesPage(vm: MainViewModel) {
    val st by vm.status.collectAsStateWithLifecycle()
    val list by vm.statuses.collectAsStateWithLifecycle()
    val savedKeys by vm.savedKeys.collectAsStateWithLifecycle()
    val saving by vm.saving.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    var refreshing by remember { mutableStateOf(false) }
    val grid = rememberLazyGridState()
    val px = dpToPx(190.dp)
    LaunchedEffect(refreshing) { if (refreshing) { vm.refresh(); kotlinx.coroutines.delay(600); refreshing = false } }

    PullToRefreshBox(isRefreshing = refreshing, onRefresh = { refreshing = true }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(tileMin()), state = grid,
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 120.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize().testTag("status_grid"),
        ) {
            item(key = "filters", span = { GridItemSpan(maxLineSpan) }, contentType = "filters") { FilterRow(vm) }
            if (st.lost.isNotEmpty()) item(key = "lost", span = { GridItemSpan(maxLineSpan) }) { LostCard { vm.openSetup(false) } }
            when {
                st.load == LoadState.LOADING -> items(6, key = { "sk$it" }, contentType = { "sk" }) { Skeleton() }
                st.connected.isEmpty() && st.lost.isEmpty() -> item(key = "setup", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.not_setup_title), stringResource(R.string.not_setup_body)) {
                        GlossyButton(stringResource(R.string.connect), { vm.openSetup(false) }, modifier = Modifier.testTag("connect_btn"))
                    }
                }
                list.isEmpty() && filter != MediaFilter.ALL -> item(key = "fe", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.filter_empty_title), stringResource(R.string.filter_empty_body), illustration = false)
                }
                list.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                    EmptyState(stringResource(R.string.empty_title), stringResource(R.string.empty_body)) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            GlossyButton(stringResource(R.string.open_whatsapp), vm::openWhatsApp, icon = Icons.AutoMirrored.Rounded.OpenInNew)
                            TextButton(onClick = { vm.refresh(userAsked = true) }, modifier = Modifier.padding(top = 6.dp)) { Text(stringResource(R.string.refresh)) }
                        }
                    }
                }
                else -> groupedTiles(
                    list, { it.time },
                    header = { g, n, first -> GroupHeader("${stringResource(groupLabel(g))} · $n", action = if (selection == null && first) stringResource(R.string.select) else null) { vm.startSelection() } },
                ) { m ->
                    val sel = selection
                    val state = when {
                        m.status != null && m.status.matchKey in savedKeys -> SaveState.SAVED
                        m.key in saving -> SaveState.SAVING
                        else -> SaveState.IDLE
                    }
                    MediaTile(
                        m, state, selecting = sel != null, selected = sel?.contains(m.key) == true,
                        durationOf = vm.repo::durationOf,
                        onClick = { if (sel != null) vm.toggleSelect(m.key) else { vm.openViewer(false, m.key); vm.maybeShowSelectHint() } },
                        onLongClick = { if (sel == null) vm.startSelection(m.key) else vm.toggleSelect(m.key) },
                        onSave = { vm.save(m) },
                        modifier = Modifier.animateItem().testTag("tile"),
                        sizePx = px,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SavedPage(vm: MainViewModel, onSort: () -> Unit, onDates: () -> Unit) {
    val list by vm.saved.collectAsStateWithLifecycle()
    val all by vm.savedAll.collectAsStateWithLifecycle()
    val selection by vm.selection.collectAsStateWithLifecycle()
    val dk by vm.dateKind.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val grid = rememberLazyGridState()
    val px = dpToPx(190.dp)
    LazyVerticalGrid(
        columns = GridCells.Adaptive(tileMin()), state = grid,
        contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize().testTag("saved_grid"),
    ) {
        item(key = "filters", span = { GridItemSpan(maxLineSpan) }) {
            FilterRow(vm, savedTab = true) {
                RoundAction(Icons.Rounded.SwapVert, stringResource(R.string.sort), onSort)
                RoundAction(Icons.Rounded.CalendarMonth, stringResource(R.string.dates), onDates, active = dk != DateRangeKind.ANY)
            }
        }
        if (dk != DateRangeKind.ANY) item(key = "dk", span = { GridItemSpan(maxLineSpan) }) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp)) {
                Text(dateLabel(vm, dk), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.setDates(DateRangeKind.ANY) }) { Text(stringResource(R.string.clear_dates)) }
            }
        }
        when {
            all.isEmpty() -> item(key = "empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(stringResource(R.string.saved_empty_title), stringResource(R.string.saved_empty_body))
            }
            list.isEmpty() && filter == MediaFilter.IN_GALLERY -> item(key = "ge", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(stringResource(R.string.gallery_empty_title), stringResource(R.string.gallery_empty_body), illustration = false)
            }
            list.isEmpty() -> item(key = "fe", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(stringResource(R.string.filter_empty_title), stringResource(R.string.filter_empty_body), illustration = false)
            }
            else -> groupedTiles(
                list, { it.saved?.savedAt ?: it.time },
                header = { g, n, _ -> GroupHeader("${stringResource(groupLabel(g))} · $n") },
            ) { m ->
                val sel = selection
                MediaTile(
                    m, null, selecting = sel != null, selected = sel?.contains(m.key) == true,
                    durationOf = vm.repo::durationOf,
                    onClick = { if (sel != null) vm.toggleSelect(m.key) else vm.openViewer(true, m.key) },
                    onLongClick = { if (sel == null) vm.startSelection(m.key) else vm.toggleSelect(m.key) },
                    onSave = {},
                    modifier = Modifier.animateItem().testTag("saved_tile"),
                    sizePx = px,
                    galleryVisible = m.saved?.visible,
                    onEye = { m.saved?.let { s -> vm.setVisible(listOf(s.id), !s.visible) } },
                )
            }
        }
    }
}

@Composable
private fun dateLabel(vm: MainViewModel, dk: DateRangeKind): String = when (dk) {
    DateRangeKind.TODAY -> stringResource(R.string.dates_today)
    DateRangeKind.WEEK -> stringResource(R.string.dates_week)
    DateRangeKind.MONTH -> stringResource(R.string.dates_month)
    DateRangeKind.CUSTOM -> vm.customRangeValue?.let {
        val f = java.text.SimpleDateFormat("d MMM", java.util.Locale.ENGLISH)
        "${f.format(java.util.Date(it[0]))} – ${f.format(java.util.Date(it[1]))}"
    } ?: stringResource(R.string.dates_pick)
    DateRangeKind.ANY -> stringResource(R.string.dates_any)
}

@Composable
private fun RoundAction(icon: ImageVector, desc: String, onClick: () -> Unit, active: Boolean = false) {
    IconButton(onClick = onClick, modifier = Modifier.padding(start = 2.dp)) {
        Box(
            Modifier.size(40.dp).clip(CircleShape).background(if (active) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, desc, tint = if (active) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(20.dp)) }
    }
}

@Composable
private fun GroupHeader(text: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f).semantics { heading() })
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun Skeleton() {
    val a by animateFloatAsState(0.6f, tween(600), label = "sk")
    Box(Modifier.aspectRatio(1f).clip(MaterialTheme.shapes.large).alpha(a).background(MaterialTheme.colorScheme.surfaceContainerHigh))
}

@Composable
private fun LostCard(onFix: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.large, modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.padding(18.dp)) {
            Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.error)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(stringResource(R.string.lost_title), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(6.dp))
                Text(stringResource(R.string.lost_body), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.height(12.dp))
                GlossyButton(stringResource(R.string.fix_now), onFix)
            }
        }
    }
}

@Composable
private fun SelectionActions(fromSaved: Boolean, count: Int, onSave: () -> Unit, onShare: () -> Unit, onRepost: () -> Unit, onDelete: () -> Unit, onShow: () -> Unit, onHide: () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shadowElevation = 8.dp, modifier = Modifier.widthIn(max = 520.dp).fillMaxWidth()) {
        Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceAround) {
            if (!fromSaved) {
                BarAction(Icons.Rounded.Download, stringResource(R.string.save_n, count), true, count > 0, onSave)
                BarAction(Icons.Rounded.Share, stringResource(R.string.share), false, count > 0, onShare)
                BarAction(Icons.Rounded.Repeat, stringResource(R.string.repost), false, count > 0, onRepost)
            } else {
                BarAction(Icons.Rounded.Visibility, stringResource(R.string.show_in_gallery), true, count > 0, onShow)
                BarAction(Icons.Rounded.VisibilityOff, stringResource(R.string.hide), false, count > 0, onHide)
                BarAction(Icons.Rounded.Share, stringResource(R.string.share), false, count > 0, onShare)
                BarAction(Icons.Rounded.Delete, stringResource(R.string.delete), false, count > 0, onDelete)
            }
        }
    }
}

@Composable
private fun BarAction(icon: ImageVector, label: String, primary: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val c = if (!enabled) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else if (primary) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier.clip(RoundedCornerShape(16.dp)).clickable(enabled = enabled, role = Role.Button, onClick = onClick).padding(horizontal = 14.dp, vertical = 6.dp).heightIn(min = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(if (primary && enabled) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = c)
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = c)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> OptionSheet(title: String, options: List<Pair<String, T>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp).semantics { heading() })
        options.forEach { (label, v) ->
            Row(
                Modifier.fillMaxWidth().clickable(role = Role.RadioButton) { onPick(v) }.padding(horizontal = 16.dp, vertical = 4.dp).heightIn(min = 52.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = v == selected, onClick = null)
                Spacer(Modifier.width(12.dp))
                Text(label, style = MaterialTheme.typography.bodyLarge)
                if (v == selected) { Spacer(Modifier.weight(1f)); Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary) }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Smaller tiles when the screen is short (phone landscape), so several rows fit. */
val LocalShortWindow = androidx.compose.runtime.compositionLocalOf { false }

@Composable
private fun tileMin() = if (LocalShortWindow.current) 128.dp else 160.dp
