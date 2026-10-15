package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.PlacementGroupInventory
import com.hcloudcontrol.core.domain.model.CreatePlacementGroupSpec
import com.hcloudcontrol.core.domain.model.PlacementGroupPatch
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.PlacementGroupOperationsUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import androidx.compose.runtime.saveable.rememberSaveable

/** Canonical placement inventory with reviewed, supported metadata and membership operations. */
@Composable
internal fun PlacementGroupsScreen(repository: HCloudRepository, projectId: String) {
    val scope = "placement-groups:$projectId:${LocalNavigationContext.current?.endpoint.orEmpty()}"
    val loader = rememberSectionLoader(scope, isEmpty = { it: List<PlacementGroupInventory> -> it.isEmpty() }) { repository.getPlacementGroups() }
    var selectedId by rememberContextualResourceId(scope)
    var query by rememberSaveable(scope) { mutableStateOf("") }
    var createRequested by rememberSaveable(scope) { mutableStateOf(false) }
    val createDraft = rememberPlacementFormDraft("$scope:create")
    val allowed = LocalHighImpactOperations.current.isAllowed
    BackHandler(enabled = LocalNavigationChrome.current == null && selectedId != null) { selectedId = null }
    val selected = loader.state.data?.firstOrNull { it.id == selectedId }
    if (selected != null) {
        RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.PLACEMENT_GROUP, selected.id.toString(), selected.name) { selectedId = null }
        PlacementGroupDetailScreen(selected, repository) { selectedId = null; loader.refresh() }
        return
    }
    HCloudPullToRefresh(loader.state.refreshing, loader::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState("placement-groups", contentReady = loader.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.placement_groups_title), style = MaterialTheme.typography.titleLarge)
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
                HCloudReadSection(loader) {}
                if (selectedId != null && loader.state.hasSuccessfulResponse && loader.state.data.orEmpty().none { it.id == selectedId }) {
                    HCloudSectionState(SectionPresentation(SectionKind.MISSING))
                    OutlinedButton(onClick = { selectedId = null }) { Text(stringResource(R.string.common_back)) }
                }
                if (allowed && loader.state.hasSuccessfulResponse && loader.state.error == null && !loader.state.refreshing)
                    Button(onClick = { createRequested = true }) { Text(stringResource(R.string.placement_groups_create)) }
            }
            val all = loader.state.data.orEmpty()
            val filtered = all.filter { query.isBlank() || it.name.contains(query.trim(), true) }
            if (all.isNotEmpty() && filtered.isEmpty()) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
            items(filtered.sortedWith(compareBy<PlacementGroupInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { group ->
                HCloudResourceCard(ResourceCardModel("$projectId:placement-group:${group.id}", group.name, group.type,
                    pluralStringResource(R.plurals.placement_groups_members, group.serverIds.size)), { selectedId = group.id })
            }
        }
    }
    if (createRequested) PlacementGroupMutationDialog(repository, null, PlacementMutationMode.CREATE,
        onDismiss = { createRequested = false }, onCompleted = {
            createRequested = false; createDraft.value = PlacementFormDraft(); loader.refresh()
        }, draftState = createDraft)
}

@Composable
private fun PlacementGroupDetailScreen(group: PlacementGroupInventory, repository: HCloudRepository, onBack: () -> Unit) {
    val scope = "placement-group:${LocalNavigationContext.current}:${group.id}"
    val metadata = rememberSectionLoader(scope) { repository.getPlacementGroup(group.id) }
    val editDraft = rememberPlacementFormDraft("$scope:edit", group)
    var manageMembers by remember(scope) { mutableStateOf(false) }
    var editRequested by remember(scope) { mutableStateOf(false) }
    var deleteRequested by remember(scope) { mutableStateOf(false) }
    val allowed = LocalHighImpactOperations.current.isAllowed && metadata.state.hasSuccessfulResponse &&
        metadata.state.error == null && !metadata.state.refreshing
    HCloudPullToRefresh(metadata.state.refreshing, metadata::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState("placement-group:${group.id}:detail", contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item { HCloudReadSection(metadata) { current ->
                Text(current.name, style = MaterialTheme.typography.titleLarge)
                PlacementGroupDetails(current, allowed, { manageMembers = true }, { editRequested = true }, { deleteRequested = true })
            } }
        }
    }
    metadata.state.data?.let { current ->
        if (manageMembers) PlacementGroupMembersDialog(current, repository, { manageMembers = false },
            { manageMembers = false; metadata.refresh() })
        if (editRequested) PlacementGroupMutationDialog(repository, current, PlacementMutationMode.EDIT,
            onDismiss = { editRequested = false }, onCompleted = {
                editRequested = false; editDraft.value = PlacementFormDraft.initial(current); metadata.refresh()
            }, draftState = editDraft)
        if (deleteRequested) PlacementGroupDeleteDialog(current, repository, { deleteRequested = false },
            { deleteRequested = false; onBack() })
    }
}

@Composable
private fun PlacementGroupDetails(
    group: PlacementGroupInventory,
    operationsAllowed: Boolean,
    onManageMembers: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) = Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PlacementGroupRow(stringResource(R.string.placement_groups_id), group.id.toString())
        PlacementGroupRow(stringResource(R.string.placement_groups_type_label), group.type)
        PlacementGroupRow(
            stringResource(R.string.placement_groups_status),
            stringResource(R.string.placement_groups_status_unavailable),
        )
        Text(stringResource(R.string.placement_groups_members_title), style = MaterialTheme.typography.titleSmall)
        if (group.serverIds.isEmpty()) {
            Text(stringResource(R.string.placement_groups_members_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            group.serverIds.sorted().forEach { serverId -> Text(stringResource(R.string.placement_groups_member, serverId)) }
        }
        if (group.labels.isNotEmpty()) {
            Text(
                stringResource(R.string.common_labels_format, group.labels.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (operationsAllowed) {
            OutlinedButton(onClick = onManageMembers, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.placement_groups_manage_members)) }
            OutlinedButton(onClick = onEdit, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.placement_groups_edit)) }
            Button(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
            ) { Text(stringResource(R.string.placement_groups_delete)) }
        }
        Text(stringResource(R.string.placement_groups_metadata_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlacementGroupMembersDialog(group: PlacementGroupInventory, repository: HCloudRepository,
    onDismiss: () -> Unit, onCompleted: () -> Unit) {
    val scope = "placement-members:${LocalNavigationContext.current}:${group.id}"
    val loader = rememberSectionLoader(scope) { repository.getServers() to repository.getPlacementGroup(group.id) }
    var add by remember(scope) { mutableStateOf(true) }
    var selectedId by remember(scope) { mutableStateOf<Long?>(null) }
    var picker by remember(scope) { mutableStateOf(false) }
    var confirm by remember(scope) { mutableStateOf(false) }
    var invalidated by remember(scope) { mutableStateOf(false) }
    val freshGroup = loader.state.data?.second ?: group
    val candidates = loader.state.data?.first.orEmpty().filter {
        if (add) it.placementGroupId == null && it.id !in freshGroup.serverIds else it.placementGroupId == freshGroup.id && it.id in freshGroup.serverIds
    }.sortedWith(compareBy<Server> { it.name.lowercase() }.thenBy { it.id })
    LaunchedEffect(candidates.map { it.id }, add) {
        if (selectedId != null && candidates.none { it.id == selectedId }) { selectedId = null; invalidated = true }
    }
    if (confirm && selectedId != null) {
        PlacementGroupMutationDialog(repository, freshGroup, if (add) PlacementMutationMode.ADD else PlacementMutationMode.REMOVE,
            selectedId, onDismiss = { confirm = false; selectedId = null; loader.refresh() }, onCompleted = onCompleted)
        return
    }
    HCloudDialog(stringResource(R.string.placement_groups_manage_title, group.name),
        stringResource(R.string.placement_groups_review_change), { confirm = true }, onDismiss,
        confirmEnabled = selectedId != null && loader.state.error == null && !loader.state.refreshing) {
        HCloudSegmentedControl(listOf(ChoiceItem("add", stringResource(R.string.placement_groups_add_server)),
            ChoiceItem("remove", stringResource(R.string.placement_groups_remove_server))),
            if (add) "add" else "remove", { add = it == "add"; selectedId = null })
        HCloudReadSection(loader) {}
        if (invalidated) Text(stringResource(R.string.placement_groups_candidates_changed), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { picker = true }, enabled = loader.state.error == null && loader.state.hasSuccessfulResponse) {
            Text(candidates.firstOrNull { it.id == selectedId }?.name ?: stringResource(R.string.common_select_server))
        }
    }
    if (picker) HCloudResourcePicker(stringResource(R.string.common_select_server),
        ResourcePickerState(scope, candidates.map { ResourcePickerItem(it.id.toString(), it.name, "#${it.id}") },
            loading = loader.state.refreshing, safeError = if (loader.state.error != null) stringResource(R.string.placement_groups_servers_load_error) else null),
        selectedId?.toString(), { selectedId = it.toLong(); invalidated = false }, { picker = false }, loader::refresh)
}

@Composable
private fun PlacementGroupRow(label: String, value: String) = Column(Modifier.fillMaxWidth()) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Medium)
}

@Composable
private fun PlacementGroupDeleteDialog(group: PlacementGroupInventory, repository: HCloudRepository, onDismiss: () -> Unit, onCompleted: () -> Unit) =
    PlacementGroupMutationDialog(repository, group, PlacementMutationMode.DELETE, onDismiss = onDismiss, onCompleted = onCompleted)
