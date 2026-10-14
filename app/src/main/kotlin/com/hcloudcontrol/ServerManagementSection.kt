package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerConfigurationPatch
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.ServerProtectionUseCase
import com.hcloudcontrol.core.domain.usecase.UpdateServerConfigurationUseCase
import kotlinx.coroutines.launch

/** Safe server metadata editor and protection controls; no deletion or rebuild action is exposed. */
@Composable
internal fun ServerManagementSection(
    server: Server,
    repository: HCloudRepository,
    readOnly: Boolean,
    onCompleted: () -> Unit,
) {
    var showMetadataEditor by remember(server.id) { mutableStateOf(false) }
    var pendingProtection by remember(server.id) { mutableStateOf<ProtectionChange?>(null) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.server_management_title), style = MaterialTheme.typography.titleLarge)
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.server_metadata_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.server_metadata_labels, server.labels.entries.sortedBy { it.key }.joinToString { "${it.key}=${it.value}" }.ifBlank { stringResource(R.string.common_none) }), color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.server_metadata_placement_group, server.placementGroupId?.toString() ?: stringResource(R.string.common_none)), color = MaterialTheme.colorScheme.onSurfaceVariant)
                OutlinedButton(onClick = { showMetadataEditor = true }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.server_metadata_edit)) }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.server_protection_title), style = MaterialTheme.typography.titleMedium)
                val protection = server.protection
                if (protection == null) {
                    Text(stringResource(R.string.server_protection_unavailable), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    ProtectionControl(stringResource(R.string.server_protection_delete), protection.delete, !readOnly) {
                        pendingProtection = ProtectionChange.Delete(!protection.delete)
                    }
                    ProtectionControl(stringResource(R.string.server_protection_rebuild), protection.rebuild, !readOnly) {
                        pendingProtection = ProtectionChange.Rebuild(!protection.rebuild)
                    }
                }
            }
        }
        if (readOnly) Text(stringResource(R.string.server_management_read_only), color = MaterialTheme.colorScheme.error)
    }
    if (showMetadataEditor) ServerMetadataDialog(server, repository, onDismiss = { showMetadataEditor = false }, onCompleted = { showMetadataEditor = false; onCompleted() })
    pendingProtection?.let { change -> ProtectionConfirmation(server, change, repository, onDismiss = { pendingProtection = null }, onCompleted = { pendingProtection = null; onCompleted() }) }
}

private sealed interface ProtectionChange {
    val enabled: Boolean
    data class Delete(override val enabled: Boolean) : ProtectionChange
    data class Rebuild(override val enabled: Boolean) : ProtectionChange
}

@Composable
private fun ProtectionControl(label: String, enabled: Boolean, actionable: Boolean, onClick: () -> Unit) = Row(
    Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
) {
    Column(Modifier.weight(1f)) {
        Text(label, fontWeight = FontWeight.Medium)
        Text(stringResource(if (enabled) R.string.server_protection_enabled else R.string.server_protection_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    OutlinedButton(onClick = onClick, enabled = actionable) { Text(stringResource(if (enabled) R.string.server_protection_turn_off else R.string.server_protection_turn_on)) }
}

@Composable
private fun ProtectionConfirmation(server: Server, change: ProtectionChange, repository: HCloudRepository, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    var submitting by remember(change) { mutableStateOf(false) }
    var progress by remember(change) { mutableStateOf<String?>(null) }
    var error by remember(change) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val title = stringResource(if (change is ProtectionChange.Delete) R.string.server_protection_delete else R.string.server_protection_rebuild)
    val desiredState = stringResource(if (change.enabled) R.string.server_protection_enable else R.string.server_protection_disable)
    val failure = stringResource(R.string.server_protection_error)
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.server_protection_confirm_title, desiredState, title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.server_protection_confirm_message, server.name, title, desiredState))
                Text(stringResource(R.string.server_protection_confirm_note), color = MaterialTheme.colorScheme.error)
                progress?.let { Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.size(12.dp)); Text(it) } }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = {
            submitting = true; error = null; progress = context.getString(R.string.common_sending_request)
            scope.launch {
                try {
                    val patch = when (change) {
                        is ProtectionChange.Delete -> ServerProtectionPatch(delete = change.enabled)
                        is ProtectionChange.Rebuild -> ServerProtectionPatch(rebuild = change.enabled)
                    }
                    val action = ServerProtectionUseCase(repository)(server.id, patch)
                    awaitServerManagementAction(repository, action.id) { progress = context.getString(R.string.action_status_format, it) }
                    onCompleted()
                } catch (_: Throwable) { error = failure; submitting = false }
            }
        }, enabled = !submitting) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.server_protection_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun ServerMetadataDialog(server: Server, repository: HCloudRepository, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    var name by remember(server.id) { mutableStateOf(server.name) }
    var labelsText by remember(server.id) { mutableStateOf(server.labels.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }) }
    var placementGroupId by remember(server.id) { mutableStateOf(server.placementGroupId) }
    var groups by remember(server.id) { mutableStateOf<List<com.hcloudcontrol.core.domain.model.PlacementGroupInventory>?>(null) }
    var groupsError by remember(server.id) { mutableStateOf(false) }
    var groupMenu by remember(server.id) { mutableStateOf(false) }
    var submitting by remember(server.id) { mutableStateOf(false) }
    var error by remember(server.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val groupError = stringResource(R.string.server_metadata_groups_error)
    val saveError = stringResource(R.string.server_metadata_save_error)
    androidx.compose.runtime.LaunchedEffect(server.id) {
        try { groups = repository.getPlacementGroups() } catch (_: Throwable) { groupsError = true }
    }
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.server_metadata_edit_title, server.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_metadata_name)) }, enabled = !submitting, singleLine = true)
                OutlinedTextField(labelsText, { labelsText = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.server_metadata_labels_input)) }, supportingText = { Text(stringResource(R.string.server_metadata_labels_help)) }, enabled = !submitting)
                if (groupsError) Text(groupError, color = MaterialTheme.colorScheme.error)
                else if (groups == null) Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp); Spacer(Modifier.size(12.dp)); Text(stringResource(R.string.server_metadata_groups_loading)) }
                else androidx.compose.foundation.layout.Box {
                    OutlinedButton(onClick = { groupMenu = true }, enabled = !submitting, modifier = Modifier.fillMaxWidth()) { Text(placementGroupId?.let { id -> groups!!.firstOrNull { it.id == id }?.name ?: "#$id" } ?: stringResource(R.string.server_metadata_placement_keep)) }
                    DropdownMenu(expanded = groupMenu, onDismissRequest = { groupMenu = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.server_metadata_placement_keep)) }, onClick = { placementGroupId = server.placementGroupId; groupMenu = false })
                        groups!!.sortedBy { it.name.lowercase() }.forEach { group -> DropdownMenuItem(text = { Text(group.name) }, onClick = { placementGroupId = group.id; groupMenu = false }) }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = { Button(onClick = {
            val labels = parseServerLabels(labelsText) ?: run { error = context.getString(R.string.server_metadata_labels_invalid); return@Button }
            if (name.isBlank()) { error = context.getString(R.string.server_metadata_name_invalid); return@Button }
            submitting = true; error = null
            scope.launch {
                try {
                    UpdateServerConfigurationUseCase(repository)(server.id, ServerConfigurationPatch(name = name.trim(), labels = labels, placementGroupId = placementGroupId))
                    onCompleted()
                } catch (_: Throwable) { error = saveError; submitting = false }
            }
        }, enabled = !submitting && groups != null) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.server_metadata_save)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private fun parseServerLabels(text: String): Map<String, String>? = buildMap {
    text.lineSequence().filter { it.isNotBlank() }.forEach { line ->
        val pivot = line.indexOf('=')
        if (pivot <= 0 || pivot == line.lastIndex) return null
        val key = line.substring(0, pivot).trim()
        val value = line.substring(pivot + 1).trim()
        if (key.isBlank() || value.isBlank() || put(key, value) != null) return null
    }
}

internal suspend fun awaitServerManagementAction(repository: HCloudRepository, actionId: Long, onProgress: (String) -> Unit) {
    var action = repository.getAction(actionId)
    repeat(600) {
        onProgress(action.status)
        when (action.status.lowercase()) {
            "success" -> return
            "error" -> throw IllegalStateException("Protection action returned error")
        }
        kotlinx.coroutines.delay(1_000)
        action = repository.getAction(actionId)
    }
    throw IllegalStateException("Protection action timed out")
}
