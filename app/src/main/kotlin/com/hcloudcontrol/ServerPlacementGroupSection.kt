package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.PlacementGroupInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.LocalOperationContext
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.rememberSectionLoader

/** Server entry reuses the canonical placement mutation consumer and its existing API adapter. */
@Composable
internal fun ServerPlacementGroupSection(server: Server, repository: HCloudRepository, readOnly: Boolean,
    onCompleted: () -> Unit) {
    val context = LocalOperationContext.current
    var query by rememberSaveable(context, server.id) { mutableStateOf("") }
    var choose by rememberSaveable(context, server.id) { mutableStateOf(false) }
    var selected by remember(context, server.id) { mutableStateOf<PlacementGroupInventory?>(null) }
    var selectedMode by remember(context, server.id) { mutableStateOf(PlacementMutationMode.ADD) }
    val allowed = LocalHighImpactOperations.current.isAllowed && !readOnly
    val groups = rememberSectionLoader("${context}:server:${server.id}:placement:${server.placementGroupId}",
        isEmpty = { items: List<PlacementGroupInventory> -> items.isEmpty() }) { repository.getPlacementGroups() }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.placement_groups_title), style = MaterialTheme.typography.titleLarge)
        HCloudReadSection(groups) { inventory ->
            val current = inventory.firstOrNull { it.id == server.placementGroupId }
            Text(current?.let { "${it.name} · #${it.id} · ${it.type}" } ?: server.placementGroupId?.let { "#$it" } ?: stringResource(R.string.common_none))
            if (allowed) {
                if (server.placementGroupId == null) OutlinedButton(onClick = { choose = true }) {
                    Text(stringResource(R.string.placement_groups_manage_members))
                } else if (current != null) OutlinedButton(onClick = { selectedMode = PlacementMutationMode.REMOVE; selected = current }) {
                    Text(stringResource(R.string.placement_groups_remove_server))
                }
            }
        }
    }
    selected?.let { group ->
        PlacementGroupMutationDialog(repository, group, selectedMode, server.id,
            onDismiss = { selected = null }, onCompleted = { selected = null; choose = false; groups.refresh(); onCompleted() })
    } ?: if (choose) {
        HCloudDialog(stringResource(R.string.placement_groups_title), "", {}, { choose = false }, showActions = false) {
            HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
            HCloudReadSection(groups) { inventory ->
                inventory.filter { it.name.contains(query, true) }.forEach { group ->
                    OutlinedButton(onClick = { selectedMode = PlacementMutationMode.ADD; selected = group }, modifier = Modifier.fillMaxWidth(), enabled = allowed && server.placementGroupId == null) {
                        Text("${group.name} · #${group.id} · ${group.type}")
                    }
                }
            }
        }
    } else Unit
}
