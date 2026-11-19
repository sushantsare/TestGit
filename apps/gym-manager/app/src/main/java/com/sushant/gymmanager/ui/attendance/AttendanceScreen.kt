package com.sushant.gymmanager.ui.attendance

import com.sushant.gymmanager.util.toast
import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.rememberCoroutineScope
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.sushant.gymmanager.data.repository.CheckInResult
import com.sushant.gymmanager.i18n.I18n
import com.sushant.gymmanager.i18n.currentLang
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.rememberGymApp
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.GymApp
import com.sushant.gymmanager.data.local.MemberEntity
import com.sushant.gymmanager.data.local.Shift
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.AppDatePickerDialog
import com.sushant.gymmanager.ui.components.ConfirmDialog
import com.sushant.gymmanager.ui.components.EmptyState
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.Pill
import com.sushant.gymmanager.ui.components.RollBadge
import com.sushant.gymmanager.ui.members.MemberCardDialog
import com.sushant.gymmanager.ui.theme.LocalExtraColors
import com.sushant.gymmanager.ui.theme.StatusGreen
import com.sushant.gymmanager.util.DateUtils
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import android.Manifest
import com.google.zxing.client.android.Intents
import com.sushant.gymmanager.ui.components.SearchField
import com.sushant.gymmanager.ui.components.matches
import com.sushant.gymmanager.ui.components.rememberPermissionRequester
import com.sushant.gymmanager.ui.navigation.RegisterLeaveGuard
import com.sushant.gymmanager.ui.theme.OnStatus
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.selection.toggleable

data class SlotGroup(val from: String, val to: String, val members: List<MemberEntity>)

class AttendanceViewModel(app: Application, private val handle: SavedStateHandle) : AndroidViewModel(app) {
    private val repo = (app as GymApp).repository

    var date by mutableStateOf(handle.get<String>("date") ?: DateUtils.today())
        private set
    var shift by mutableStateOf(handle.get<String>("shift") ?: Shift.MORNING)
        private set
    var marks by mutableStateOf<Map<Long, Boolean>>(emptyMap())
        private set
    var saved by mutableStateOf<Map<Long, Boolean>>(emptyMap())
        private set
    var loading by mutableStateOf(true)
        private set
    var query by mutableStateOf("")

    /** True while the screen should follow "today" (it moves to the new day after midnight). */
    private var followToday = handle.get<Boolean>("followToday") ?: true
    private var loadJob: Job? = null

    val activeMembers: StateFlow<List<MemberEntity>?> =
        repo.activeMembers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    init { load(restore = true) }

    private fun persist() {
        handle["date"] = date
        handle["shift"] = shift
        handle["followToday"] = followToday
        // Unsaved ticks survive the app being closed in the background.
        handle["present"] = marks.filterValues { it }.keys.toLongArray()
        handle["absent"] = marks.filterValues { !it }.keys.toLongArray()
    }

    private fun load(restore: Boolean = false) {
        loadJob?.cancel()
        loading = true
        val restoredPresent = if (restore) handle.get<LongArray>("present") else null
        val restoredAbsent = if (restore) handle.get<LongArray>("absent") else null
        loadJob = viewModelScope.launch {
            val existing = repo.attendanceFor(date, shift)
            saved = existing
            marks = if (restoredPresent != null || restoredAbsent != null) {
                existing + restoredPresent.orEmptyArray().associateWith { true } + restoredAbsent.orEmptyArray().associateWith { false }
            } else existing
            loading = false
            persist()
        }
    }

    private fun LongArray?.orEmptyArray(): List<Long> = this?.toList().orEmpty()

    fun groupsFor(all: List<MemberEntity>): List<SlotGroup> =
        all.filter { it.shift == shift && it.frozenFrom == null && it.dateOfJoining <= date }
            .groupBy { it.fromTime to it.toTime }
            .map { (k, v) -> SlotGroup(k.first, k.second, v) }
            .sortedWith(compareBy({ it.from }, { it.to }))

    fun isDirty(visible: List<MemberEntity>): Boolean =
        visible.any { (marks[it.id] ?: false) != (saved[it.id] ?: false) }

    fun toggle(id: Long) { marks = marks + (id to !(marks[id] ?: false)); persist() }
    fun setAll(visible: List<MemberEntity>, present: Boolean) { marks = marks + visible.associate { it.id to present }; persist() }
    fun changeDate(iso: String) { date = iso; followToday = iso == DateUtils.today(); marks = emptyMap(); load() }
    fun changeShift(s: String) { shift = s; marks = emptyMap(); load() }
    fun discard() { marks = saved; persist() }

    /** After midnight, a screen that was showing "today" moves to the new day (unless ticks are unsaved). */
    fun onResume(visible: List<MemberEntity>) {
        val today = DateUtils.today()
        if (followToday && date != today && !isDirty(visible)) changeDate(today)
    }

    /** A QR check-in was saved: add it to the ticks on screen without throwing away unsaved ones. */
    fun applyCheckIn(member: MemberEntity, checkInDate: String) {
        if (checkInDate != date || member.shift != shift) return
        saved = saved + (member.id to true)
        marks = marks + (member.id to true)
        persist()
    }

    fun save(visible: List<MemberEntity>, onDone: () -> Unit) {
        val d = date; val s = shift
        val toSave = visible.associate { it.id to (marks[it.id] ?: false) }
        val slots = visible.associate { it.id to DateUtils.formatTimeSlot(it.fromTime, it.toTime) }
        val app = getApplication<GymApp>()
        // Saved in the app scope so leaving the screen right away can't cancel it.
        app.appScope.launch {
            repo.saveAttendance(d, s, toSave, slots)
            viewModelScope.launch {
                saved = saved + toSave
                persist()
                onDone()
            }
        }
    }
}

@Composable
fun AttendanceScreen(onOpenDrawer: () -> Unit, onEditMember: (Long) -> Unit, vm: AttendanceViewModel = viewModel()) {
    val context = LocalContext.current
    val all by vm.activeMembers.collectAsStateWithLifecycle()
    val groups = remember(all, vm.shift, vm.date) { all?.let { vm.groupsFor(it) } }
    val visible = groups?.flatMap { it.members }.orEmpty()
    val shownGroups = remember(groups, vm.query) {
        groups?.map { g -> g.copy(members = g.members.filter { it.matches(vm.query) }) }?.filter { it.members.isNotEmpty() }
    }
    val dirty = vm.isDirty(visible)
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDate by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Long?>(null) }

    fun guarded(action: () -> Unit) { if (dirty) pending = action else action() }

    BackHandler(enabled = dirty) { pending = { vm.discard() } }
    RegisterLeaveGuard(dirty) { proceed -> pending = proceed }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume(visible) }

    val present = visible.count { vm.marks[it.id] == true }
    val savedMsg = t("Attendance saved: %d present", present)
    val scanPrompt = t("Point the camera at the member's QR code")
    val noCameraMsg = t("Camera access is off, so QR codes can't be scanned. You can still tick members by hand.")
    val lang = currentLang()
    val app = rememberGymApp()
    val scope = rememberCoroutineScope()
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        val code = result.contents
        if (code == null) {
            if (result.originalIntent?.hasExtra(Intents.Scan.MISSING_CAMERA_PERMISSION) == true) context.toast(noCameraMsg, long = true)
            return@rememberLauncherForActivityResult
        }
        scope.launch {
            val today = DateUtils.today()
            val r = app.repository.checkIn(code, today)
            val name = r.member?.fullName.orEmpty()
            val msg = when (r.result) {
                CheckInResult.OK -> I18n.tr(lang, "%s checked in", name)
                CheckInResult.ALREADY -> I18n.tr(lang, "%s is already marked present today", name)
                CheckInResult.INACTIVE -> I18n.tr(lang, "%s is deactivated", name)
                CheckInResult.FROZEN -> I18n.tr(lang, "%s's membership is frozen", name)
                CheckInResult.NOT_FOUND -> I18n.tr(lang, "No member found for this QR code")
            }
            context.toast(msg, long = true)
            if (r.result == CheckInResult.OK || r.result == CheckInResult.ALREADY) r.member?.let { vm.applyCheckIn(it, today) }
        }
    }
    val camera = rememberPermissionRequester(
        Manifest.permission.CAMERA,
        title = t("Scan member QR codes"),
        reason = t("The camera is used only to read members' QR ID cards for quick check-in. No photos are taken or saved."),
        deniedMessage = noCameraMsg
    )
    val isToday = vm.date == DateUtils.today()

    Scaffold(
        topBar = {
            GymTopBar(
                title = t("Attendance"),
                subtitle = if (isToday) t("Today • %s", DateUtils.formatShort(vm.date)) else DateUtils.formatFull(vm.date),
                onMenu = onOpenDrawer,
                actions = {
                    AssistChip(
                        onClick = { showDate = true },
                        label = { Text(if (isToday) t("Today") else DateUtils.formatShort(vm.date)) },
                        leadingIcon = { Icon(Icons.Default.CalendarToday, null, Modifier.size(AssistChipDefaults.IconSize)) },
                        modifier = Modifier.padding(end = 8.dp).testTag("date_chip")
                    )
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    camera.request {
                        scanner.launch(
                            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE).setPrompt(scanPrompt)
                                .setBeepEnabled(true).setOrientationLocked(false)
                        )
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.testTag("scan_qr")
            ) { Icon(Icons.Default.QrCodeScanner, t("Scan QR check-in")) }
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
                Column {
                    HorizontalDivider(color = LocalExtraColors.current.cardBorder)
                    Row(
                        Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { pending = { vm.discard() } },
                            enabled = dirty,
                            modifier = Modifier.weight(1f).height(50.dp)
                        ) { Text(t("Cancel")) }
                        Button(
                            onClick = {
                                vm.save(visible) {
                                    context.toast(savedMsg)
                                }
                            },
                            enabled = dirty,
                            modifier = Modifier.weight(1.4f).height(50.dp).testTag("save_attendance")
                        ) {
                            Icon(Icons.Default.Save, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(t("Save Attendance"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = if (vm.shift == Shift.MORNING) 0 else 1,
                containerColor = MaterialTheme.colorScheme.background
            ) {
                Tab(
                    selected = vm.shift == Shift.MORNING,
                    onClick = { if (vm.shift != Shift.MORNING) guarded { vm.changeShift(Shift.MORNING) } },
                    text = { Text(t("Morning Shift")) },
                    icon = { Icon(Icons.Default.WbSunny, null) }
                )
                Tab(
                    selected = vm.shift == Shift.EVENING,
                    onClick = { if (vm.shift != Shift.EVENING) guarded { vm.changeShift(Shift.EVENING) } },
                    text = { Text(t("Evening Shift")) },
                    icon = { Icon(Icons.Default.NightsStay, null) },
                    modifier = Modifier.testTag("tab_evening")
                )
            }

            when {
                groups == null || vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                groups.isEmpty() -> EmptyState(
                    Icons.Default.EventBusy,
                    t("No members in this shift"),
                    t(if (vm.shift == Shift.MORNING) "Active members whose workout starts in the morning window will appear here." else "Active members whose workout starts in the evening window will appear here.")
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.testTag("attendance_list")
                ) {
                    item(key = "summary") {
                        AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(14.dp)) {
                            val pct = if (visible.isEmpty()) 0 else present * 100 / visible.size
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(t("PRESENT"), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text("$present / ${visible.size}  ($pct%)", style = MaterialTheme.typography.titleLarge, modifier = Modifier.testTag("present_summary"))
                                }
                                if (dirty) Pill(t("UNSAVED"), MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                progress = { if (visible.isEmpty()) 0f else present.toFloat() / visible.size },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = StatusGreen
                            )
                            Spacer(Modifier.height(12.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                FilledTonalButton(
                                    onClick = { vm.setAll(visible, true) },
                                    modifier = Modifier.weight(1f).testTag("all_present")
                                ) {
                                    Icon(Icons.Default.DoneAll, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("All Present"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                OutlinedButton(onClick = { vm.setAll(visible, false) }, modifier = Modifier.weight(1f)) {
                                    Icon(Icons.Default.RemoveDone, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(t("Clear All"), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                    item(key = "search") {
                        SearchField(vm.query, { vm.query = it }, t("Search name or roll no."), tag = "attendance_search")
                    }
                    val list = shownGroups.orEmpty()
                    if (list.isEmpty()) {
                        item(key = "nomatch") { EmptyState(Icons.Default.EventBusy, t("No matches"), t("No member in this shift matches \"%s\".", vm.query)) }
                    }
                    items(list, key = { "${it.from}-${it.to}" }) { g ->
                        SlotCard(g, vm.marks, onToggle = vm::toggle, onOpen = { openId = it })
                    }
                }
            }
        }
    }

    if (showDate) {
        AppDatePickerDialog(vm.date, onPick = { iso -> if (iso != vm.date) guarded { vm.changeDate(iso) } }, onDismiss = { showDate = false }, pastOnly = true)
    }
    pending?.let { action ->
        ConfirmDialog(
            title = t("Discard unsaved attendance?"),
            message = t("You have marked attendance that hasn't been saved yet."),
            confirmText = t("Discard"),
            destructive = true,
            onConfirm = { vm.discard(); pending = null; action() },
            onDismiss = { pending = null }
        )
    }
    openId?.let { MemberCardDialog(it, onDismiss = { openId = null }, onEdit = onEditMember) }
}

@Composable
private fun SlotCard(group: SlotGroup, marks: Map<Long, Boolean>, onToggle: (Long) -> Unit, onOpen: (Long) -> Unit) {
    val presentHere = group.members.count { marks[it.id] == true }
    val openLabel = t("Open member card")
    AppCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(vertical = 8.dp)) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Row(Modifier.padding(horizontal = 10.dp, vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        DateUtils.formatTimeSlot(group.from, group.to),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            Text("$presentHere/${group.members.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        group.members.forEach { m ->
            val checked = marks[m.id] == true
            // Tapping the row ticks the member; tapping the photo opens their card.
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 6.dp).clip(RoundedCornerShape(12.dp))
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(m.id) })
                    .padding(start = 4.dp, top = 6.dp, bottom = 6.dp)
                    .testTag("row_${m.rollNumber}"),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier.size(48.dp).clip(RoundedCornerShape(24.dp)).clickable { onOpen(m.id) }
                        .semantics { contentDescription = openLabel },
                    contentAlignment = Alignment.Center
                ) { MemberAvatar(m.photoUri, m.fullName, size = 40.dp) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(m.fullName, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Spacer(Modifier.height(2.dp))
                    RollBadge(m.rollNumber)
                }
                Checkbox(
                    checked = checked,
                    onCheckedChange = null,
                    colors = CheckboxDefaults.colors(checkedColor = StatusGreen, checkmarkColor = OnStatus),
                    modifier = Modifier.padding(12.dp).testTag("check_${m.rollNumber}")
                )
            }
        }
    }
}
