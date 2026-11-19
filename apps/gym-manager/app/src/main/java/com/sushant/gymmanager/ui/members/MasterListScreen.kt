package com.sushant.gymmanager.ui.members

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PersonOff
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.data.local.GymSettingsEntity
import com.sushant.gymmanager.data.repository.MemberWithPlan
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.AppCard
import com.sushant.gymmanager.ui.components.EmptyState
import com.sushant.gymmanager.ui.components.GymTopBar
import com.sushant.gymmanager.ui.components.MemberAvatar
import com.sushant.gymmanager.ui.components.Pill
import com.sushant.gymmanager.ui.components.RollBadge
import com.sushant.gymmanager.ui.components.ShiftPill
import com.sushant.gymmanager.ui.components.WhatsAppButton
import com.sushant.gymmanager.ui.components.planStatus
import com.sushant.gymmanager.util.DateUtils
import com.sushant.gymmanager.util.WhatsAppHelper

@Composable
fun MasterListScreen(
    onOpenDrawer: () -> Unit,
    onAddMember: () -> Unit,
    onEditMember: (Long) -> Unit,
    onOpenDeactivated: () -> Unit,
    vm: MembersViewModel = viewModel()
) {
    val all by vm.allActive.collectAsStateWithLifecycle()
    val list by vm.filtered.collectAsStateWithLifecycle()
    val inactive by vm.inactive.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var menu by remember { mutableStateOf(false) }
    var openId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            GymTopBar(
                title = t("Master List (%d)", all?.size ?: 0),
                subtitle = t("Active members"),
                onMenu = onOpenDrawer,
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, t("More options")) }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(t("Deactivated Members (%d)", inactive?.size ?: 0)) },
                            leadingIcon = { Icon(Icons.Default.PersonOff, null) },
                            onClick = { menu = false; onOpenDeactivated() }
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddMember,
                icon = { Icon(Icons.Default.GroupAdd, null) },
                text = { Text(t("Add Member")) },
                modifier = Modifier.testTag("fab_add")
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { vm.query.value = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp).testTag("search"),
                placeholder = { Text(t("Search name, roll no. or WhatsApp")) },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                trailingIcon = {
                    if (query.isNotEmpty()) IconButton(onClick = { vm.query.value = "" }) { Icon(Icons.Default.Clear, t("Clear search")) }
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
                )
            )
            val items = list
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() && query.isNotBlank() -> EmptyState(Icons.Default.PersonSearch, t("No matches"), t("No active member matches \"%s\".", query))
                items.isEmpty() -> EmptyState(Icons.Default.GroupAdd, t("No members yet"), t("Tap Add Member to register your first gym member."))
                else -> MemberList(items, settings, onOpen = { openId = it })
            }
        }
    }

    openId?.let { id -> MemberCardDialog(memberId = id, onDismiss = { openId = null }, onEdit = onEditMember) }
}

@Composable
fun MemberList(items: List<MemberWithPlan>, settings: GymSettingsEntity, onOpen: (Long) -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.testTag("member_list")
    ) {
        items(items, key = { it.member.id }) { item -> MemberRowCard(item, settings, onClick = { onOpen(item.member.id) }) }
    }
}

@Composable
fun MemberRowCard(item: MemberWithPlan, settings: GymSettingsEntity, onClick: () -> Unit) {
    val context = LocalContext.current
    val m = item.member
    val status = planStatus(item.membership, settings.expiringSoonDays, m.frozenFrom != null)
    AppCard(onClick = onClick, contentPadding = PaddingValues(12.dp), modifier = Modifier.fillMaxWidth().testTag("member_${m.rollNumber}")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(m.photoUri, m.fullName, size = 52.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        m.fullName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(Modifier.width(8.dp))
                    RollBadge(m.rollNumber)
                }
                Spacer(Modifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        DateUtils.formatTimeSlot(m.fromTime, m.toTime),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ShiftPill(m.shift)
                    Pill(t(status.label).uppercase(), status.color)
                }
            }
            Spacer(Modifier.width(8.dp))
            WhatsAppButton(onClick = { WhatsAppHelper.open(context, m.whatsappNumber) }, contentDescription = t("Chat with %s", m.fullName))
        }
    }
}
