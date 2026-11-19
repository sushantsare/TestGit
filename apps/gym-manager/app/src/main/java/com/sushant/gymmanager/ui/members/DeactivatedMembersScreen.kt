package com.sushant.gymmanager.ui.members

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import com.sushant.gymmanager.ui.components.SearchField
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HowToReg
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sushant.gymmanager.i18n.t
import com.sushant.gymmanager.ui.components.EmptyState
import com.sushant.gymmanager.ui.components.GymTopBar

@Composable
fun DeactivatedMembersScreen(
    onBack: () -> Unit,
    onEditMember: (Long) -> Unit,
    vm: MembersViewModel = viewModel()
) {
    val list by vm.inactive.collectAsStateWithLifecycle()
    val total by vm.inactiveCount.collectAsStateWithLifecycle()
    val query by vm.inactiveQuery.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var openId by remember { mutableStateOf<Long?>(null) }

    Scaffold(
        topBar = {
            GymTopBar(
                title = t("Deactivated (%d)", total),
                subtitle = t("Tap a member to reactivate, edit or delete"),
                onBack = onBack
            )
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (total > 0) {
                SearchField(query, { vm.inactiveQuery.value = it }, t("Search name, roll no. or WhatsApp"), Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
            val items = list
            when {
                items == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                items.isEmpty() && query.isNotBlank() -> EmptyState(Icons.Default.HowToReg, t("No matches"), t("No deactivated member matches \"%s\".", query))
                items.isEmpty() -> EmptyState(Icons.Default.HowToReg, t("No deactivated members"), t("Members you deactivate will appear here with their history intact."))
                else -> MemberList(items, settings, onOpen = { openId = it })
            }
        }
    }
    openId?.let { id -> MemberCardDialog(memberId = id, onDismiss = { openId = null }, onEdit = onEditMember) }
}
