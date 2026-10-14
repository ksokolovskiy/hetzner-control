package com.hcloudcontrol

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

/** Isolated, deliberately read-only Placement Group list/detail UI. */
@Composable
internal fun PlacementGroupsScreen(repository: HCloudRepository, projectId: String) {
    var state by remember(projectId) { mutableStateOf<PlacementGroupsState>(PlacementGroupsState.Loading) }
    var refreshKey by remember(projectId) { mutableStateOf(0) }
    var selectedGroup by remember(projectId) { mutableStateOf<PlacementGroupInventory?>(null) }
    var createRequested by remember(projectId) { mutableStateOf(false) }
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    val scope = rememberCoroutineScope()
    val loadError = stringResource(R.string.placement_groups_load_error)
    fun load() {
        state = PlacementGroupsState.Loading
        scope.launch {
            state = try {
                PlacementGroupsState.Content(withContext(Dispatchers.IO) { repository.getPlacementGroups() })
            } catch (_: Throwable) {
                PlacementGroupsState.Error(loadError)
            }
        }
    }
    LaunchedEffect(projectId, refreshKey) { load() }
    selectedGroup?.let { group ->
        PlacementGroupDetailScreen(group, repository) { selectedGroup = null }
        return
    }
    when (val current = state) {
        PlacementGroupsState.Loading -> LoadingView(stringResource(R.string.placement_groups_loading))
        is PlacementGroupsState.Error -> StateView(
            stringResource(R.string.placement_groups_title), current.message, stringResource(R.string.common_retry), { refreshKey++ },
        )
        is PlacementGroupsState.Content -> PlacementGroupsList(
            groups = current.groups,
            operationsAllowed = highImpactAllowed,
            onCreate = { createRequested = true },
            onClick = { selectedGroup = it },
        )
    }
    if (highImpactAllowed && createRequested) PlacementGroupCreateDialog(
        repository = repository,
        onDismiss = { createRequested = false },
        onCompleted = { createRequested = false; refreshKey++ },
    )
}

private sealed interface PlacementGroupsState {
    data object Loading : PlacementGroupsState
    data class Content(val groups: List<PlacementGroupInventory>) : PlacementGroupsState
    data class Error(val message: String) : PlacementGroupsState
}

private sealed interface PlacementGroupDetailState {
    data object Loading : PlacementGroupDetailState
    data class Content(val group: PlacementGroupInventory) : PlacementGroupDetailState
    data class Error(val message: String) : PlacementGroupDetailState
}

@Composable
private fun PlacementGroupsList(
    groups: List<PlacementGroupInventory>,
    operationsAllowed: Boolean,
    onCreate: () -> Unit,
    onClick: (PlacementGroupInventory) -> Unit,
) = LazyColumn(
    modifier = Modifier.fillMaxSize(),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    item {
        Text(stringResource(R.string.placement_groups_title), style = MaterialTheme.typography.headlineMedium)
        Text(stringResource(R.string.placement_groups_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (operationsAllowed) Button(onClick = onCreate, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.placement_groups_create)) }
    }
    if (groups.isEmpty()) {
        item { Text(stringResource(R.string.placement_groups_empty), color = MaterialTheme.colorScheme.onSurfaceVariant) }
    } else {
        items(groups.sortedWith(compareBy<PlacementGroupInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { group ->
            PlacementGroupCard(group) { onClick(group) }
        }
    }
}

@Composable
private fun PlacementGroupCard(group: PlacementGroupInventory, onClick: () -> Unit) = Card(
    Modifier.fillMaxWidth().clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(group.name, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.placement_groups_type, group.type), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(pluralStringResource(R.plurals.placement_groups_members, group.serverIds.size), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PlacementGroupDetailScreen(group: PlacementGroupInventory, repository: HCloudRepository, onBack: () -> Unit) {
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var state by remember(group.id) { mutableStateOf<PlacementGroupDetailState>(PlacementGroupDetailState.Loading) }
    var refreshKey by remember(group.id) { mutableStateOf(0) }
    var manageMembers by remember(group.id) { mutableStateOf(false) }
    var editRequested by remember(group.id) { mutableStateOf(false) }
    var deleteRequested by remember(group.id) { mutableStateOf(false) }
    val loadError = stringResource(R.string.placement_group_load_error)
    LaunchedEffect(group.id, refreshKey) {
        state = PlacementGroupDetailState.Loading
        state = try {
            PlacementGroupDetailState.Content(withContext(Dispatchers.IO) { repository.getPlacementGroup(group.id) })
        } catch (_: Throwable) {
            PlacementGroupDetailState.Error(loadError)
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item { Text(group.name, style = MaterialTheme.typography.headlineMedium) }
        when (val current = state) {
            PlacementGroupDetailState.Loading -> item { PlacementGroupLoading(stringResource(R.string.placement_group_loading)) }
            is PlacementGroupDetailState.Error -> item {
                Text(current.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.common_retry)) }
            }
            is PlacementGroupDetailState.Content -> item {
                PlacementGroupDetails(
                    current.group,
                    operationsAllowed = highImpactAllowed,
                    onManageMembers = { manageMembers = true },
                    onEdit = { editRequested = true },
                    onDelete = { deleteRequested = true },
                )
            }
        }
    }
    if (highImpactAllowed && manageMembers) PlacementGroupMembersDialog(
        group = (state as? PlacementGroupDetailState.Content)?.group ?: group,
        repository = repository,
        onDismiss = { manageMembers = false },
        onCompleted = { manageMembers = false; refreshKey++ },
    )
    val currentGroup = (state as? PlacementGroupDetailState.Content)?.group ?: group
    if (highImpactAllowed && editRequested) PlacementGroupEditDialog(
        group = currentGroup,
        repository = repository,
        onDismiss = { editRequested = false },
        onCompleted = { editRequested = false; refreshKey++ },
    )
    if (highImpactAllowed && deleteRequested) PlacementGroupDeleteDialog(
        group = currentGroup,
        repository = repository,
        onDismiss = { deleteRequested = false },
        onCompleted = { deleteRequested = false; onBack() },
    )
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
        Text(stringResource(R.string.placement_groups_read_only_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private enum class PlacementGroupMemberOperation { ADD, REMOVE }
private data class PlacementGroupMemberRequest(val operation: PlacementGroupMemberOperation, val server: Server)

@Composable
private fun PlacementGroupMembersDialog(
    group: PlacementGroupInventory,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var servers by remember(group.id) { mutableStateOf<List<Server>?>(null) }
    var loadFailed by remember(group.id) { mutableStateOf(false) }
    var selectedServer by remember(group.id) { mutableStateOf<Server?>(null) }
    var operation by remember(group.id) { mutableStateOf(PlacementGroupMemberOperation.ADD) }
    var menuExpanded by remember(group.id) { mutableStateOf(false) }
    var confirmRequest by remember(group.id) { mutableStateOf<PlacementGroupMemberRequest?>(null) }
    androidx.compose.runtime.LaunchedEffect(group.id) {
        try { servers = repository.getServers() } catch (_: Throwable) { loadFailed = true }
    }
    confirmRequest?.let { request ->
        PlacementGroupMemberConfirmation(group, request, repository, onDismiss = { confirmRequest = null }, onCompleted = onCompleted)
        return
    }
    val candidates = servers.orEmpty().filter { server ->
        when (operation) {
            PlacementGroupMemberOperation.ADD -> server.id !in group.serverIds && server.placementGroupId == null
            PlacementGroupMemberOperation.REMOVE -> server.id in group.serverIds
        }
    }.sortedBy { it.name.lowercase() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.placement_groups_manage_title, group.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.placement_groups_manage_description))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { operation = PlacementGroupMemberOperation.ADD; selectedServer = null }) { Text(stringResource(R.string.placement_groups_add_server)) }
                    OutlinedButton(onClick = { operation = PlacementGroupMemberOperation.REMOVE; selectedServer = null }) { Text(stringResource(R.string.placement_groups_remove_server)) }
                }
                when {
                    loadFailed -> Text(stringResource(R.string.placement_groups_servers_load_error), color = MaterialTheme.colorScheme.error)
                    servers == null -> PlacementGroupLoading(stringResource(R.string.placement_groups_servers_loading))
                    candidates.isEmpty() -> Text(stringResource(if (operation == PlacementGroupMemberOperation.ADD) R.string.placement_groups_add_empty else R.string.placement_groups_remove_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else -> androidx.compose.foundation.layout.Box {
                        OutlinedButton(onClick = { menuExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(selectedServer?.name ?: stringResource(R.string.common_select_server)) }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            candidates.forEach { server -> DropdownMenuItem(text = { Text("${server.name} · #${server.id}") }, onClick = { selectedServer = server; menuExpanded = false }) }
                        }
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { selectedServer?.let { confirmRequest = PlacementGroupMemberRequest(operation, it) } }, enabled = selectedServer != null) { Text(stringResource(R.string.placement_groups_review_change)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun PlacementGroupMemberConfirmation(
    group: PlacementGroupInventory,
    request: PlacementGroupMemberRequest,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var submitting by remember(request) { mutableStateOf(false) }
    var progress by remember(request) { mutableStateOf<String?>(null) }
    var error by remember(request) { mutableStateOf<String?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val isRemoval = request.operation == PlacementGroupMemberOperation.REMOVE
    val failure = stringResource(R.string.placement_groups_change_error)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(if (isRemoval) R.string.placement_groups_remove_confirm_title else R.string.placement_groups_add_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(if (isRemoval) R.string.placement_groups_remove_confirm else R.string.placement_groups_add_confirm, request.server.name, group.name))
                Text(stringResource(if (isRemoval) R.string.placement_groups_remove_warning else R.string.placement_groups_add_warning), color = MaterialTheme.colorScheme.error)
                progress?.let { PlacementGroupLoading(it) }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = {
            submitting = true; error = null; progress = context.getString(R.string.common_sending_request)
            scope.launch {
                try {
                    val operations = PlacementGroupOperationsUseCase(repository)
                    val action = if (isRemoval) operations.removeServer(request.server.id, group.id) else operations.addServer(request.server.id, group.id)
                    awaitPlacementGroupAction(repository, action.id) { progress = context.getString(R.string.action_status_format, it) }
                    onCompleted()
                } catch (_: Throwable) { error = failure; submitting = false }
            }
        }, enabled = !submitting, colors = if (isRemoval) androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError) else androidx.compose.material3.ButtonDefaults.buttonColors()) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.placement_groups_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private suspend fun awaitPlacementGroupAction(repository: HCloudRepository, actionId: Long, onProgress: (String) -> Unit) {
    var action = repository.getAction(actionId)
    repeat(600) {
        onProgress(action.status)
        when (action.status.lowercase()) { "success" -> return; "error" -> throw IllegalStateException("Placement Group action returned error") }
        delay(1_000)
        action = repository.getAction(actionId)
    }
    throw IllegalStateException("Placement Group action timed out")
}

@Composable
private fun PlacementGroupLoading(message: String) = Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    CircularProgressIndicator()
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun PlacementGroupRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Medium)
}

private fun parsePlacementGroupLabels(raw: String): Map<String, String>? = buildMap {
    raw.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
        val separator = line.indexOf('=')
        if (separator <= 0) return null
        val key = line.substring(0, separator).trim()
        if (key.isBlank()) return null
        put(key, line.substring(separator + 1).trim())
    }
}

@Composable
private fun PlacementGroupCreateDialog(
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var labelsInput by remember { mutableStateOf("") }
    var reviewed by remember { mutableStateOf(false) }
    var submitting by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val genericFailure = stringResource(R.string.placement_groups_create_failed)
    val labels = parsePlacementGroupLabels(labelsInput)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.placement_groups_create_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.placement_groups_create_intro))
                Text(stringResource(R.string.placement_groups_create_type), color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it; failure = null }, enabled = !submitting,
                    singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.placement_groups_name)) },
                )
                androidx.compose.material3.OutlinedTextField(
                    value = labelsInput, onValueChange = { labelsInput = it; failure = null }, enabled = !submitting,
                    modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.placement_groups_labels_optional)) },
                    supportingText = { Text(stringResource(R.string.placement_groups_labels_hint)) },
                )
                if (labels == null) Text(stringResource(R.string.placement_groups_labels_invalid), color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = reviewed, onCheckedChange = { reviewed = it }, enabled = !submitting)
                    Text(stringResource(R.string.placement_groups_create_ack))
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && labels != null && reviewed && !submitting,
                onClick = {
                    submitting = true; failure = null
                    scope.launch {
                        try {
                            val created = withContext(Dispatchers.IO) {
                                PlacementGroupOperationsUseCase(repository).create(
                                    CreatePlacementGroupSpec(name = name.trim(), labels = requireNotNull(labels)),
                                )
                            }
                            created.action?.let { action -> awaitPlacementGroupAction(repository, action.id) { } }
                            onCompleted()
                        } catch (error: Throwable) {
                            failure = safeApiFailureMessage(error, genericFailure); submitting = false
                        }
                    }
                },
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.placement_groups_create_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun PlacementGroupEditDialog(
    group: PlacementGroupInventory,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var name by remember(group.id) { mutableStateOf(group.name) }
    var labelsInput by remember(group.id) { mutableStateOf(group.labels.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }) }
    var reviewed by remember(group.id) { mutableStateOf(false) }
    var submitting by remember(group.id) { mutableStateOf(false) }
    var failure by remember(group.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val genericFailure = stringResource(R.string.placement_groups_update_failed)
    val labels = parsePlacementGroupLabels(labelsInput)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.placement_groups_edit_title, group.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.placement_groups_edit_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                androidx.compose.material3.OutlinedTextField(value = name, onValueChange = { name = it; failure = null }, enabled = !submitting, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.placement_groups_name)) })
                androidx.compose.material3.OutlinedTextField(value = labelsInput, onValueChange = { labelsInput = it; failure = null }, enabled = !submitting, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.placement_groups_labels)) }, supportingText = { Text(stringResource(R.string.placement_groups_labels_replace_hint)) })
                if (labels == null) Text(stringResource(R.string.placement_groups_labels_invalid), color = MaterialTheme.colorScheme.error)
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = reviewed, onCheckedChange = { reviewed = it }, enabled = !submitting)
                    Text(stringResource(R.string.placement_groups_edit_ack))
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = name.isNotBlank() && labels != null && reviewed && !submitting,
                onClick = {
                    submitting = true; failure = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) {
                                PlacementGroupOperationsUseCase(repository).update(group.id, PlacementGroupPatch(name = name.trim(), labels = requireNotNull(labels)))
                            }
                            onCompleted()
                        } catch (error: Throwable) { failure = safeApiFailureMessage(error, genericFailure); submitting = false }
                    }
                },
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.placement_groups_edit_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun PlacementGroupDeleteDialog(
    group: PlacementGroupInventory,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var typedName by remember(group.id) { mutableStateOf("") }
    var acknowledged by remember(group.id) { mutableStateOf(false) }
    var submitting by remember(group.id) { mutableStateOf(false) }
    var failure by remember(group.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val genericFailure = stringResource(R.string.placement_groups_delete_failed)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.placement_groups_delete_title, group.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.placement_groups_delete_warning), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.placement_groups_delete_type_name, group.name))
                androidx.compose.material3.OutlinedTextField(value = typedName, onValueChange = { typedName = it; failure = null }, enabled = !submitting, singleLine = true, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.placement_groups_name)) })
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    androidx.compose.material3.Checkbox(checked = acknowledged, onCheckedChange = { acknowledged = it }, enabled = !submitting)
                    Text(stringResource(R.string.placement_groups_delete_ack))
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                enabled = typedName == group.name && acknowledged && !submitting,
                colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                onClick = {
                    submitting = true; failure = null
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { PlacementGroupOperationsUseCase(repository).delete(group.id) }
                            onCompleted()
                        } catch (error: Throwable) { failure = safeApiFailureMessage(error, genericFailure); submitting = false }
                    }
                },
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.placement_groups_delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}
