package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerConfigurationPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.ProtectionAdapter
import com.hcloudcontrol.operations.ProtectionResource
import com.hcloudcontrol.operations.ProtectionField
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.operations.LocalOperationContext
import com.hcloudcontrol.operations.parseResourceLabels

/** Safe server metadata editor and protection controls; no deletion or rebuild action is exposed. */
@Composable
internal fun ServerManagementSection(
    server: Server,
    repository: HCloudRepository,
    readOnly: Boolean,
    onCompleted: () -> Unit,
) {
    val metadataScope = "${LocalOperationContext.current}:server:${server.id}:metadata"
    var showMetadataEditor by rememberContextualFlag(metadataScope)
    val metadataDrafts = rememberSaveableStateHolder()
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
                    Text(stringResource(R.string.server_protection_delete))
                    ResourceProtectionControls(server.name, protection.delete,
                        ProtectionAdapter(ProtectionResource.SERVER, server.id, ProtectionField.DELETE, repository), onCompleted, enabled = !readOnly)
                    Text(stringResource(R.string.server_protection_rebuild))
                    ResourceProtectionControls(server.name, protection.rebuild,
                        ProtectionAdapter(ProtectionResource.SERVER, server.id, ProtectionField.REBUILD, repository), onCompleted, enabled = !readOnly)
                }
            }
        }
        if (readOnly) Text(stringResource(R.string.server_management_read_only), color = MaterialTheme.colorScheme.error)
    }
    if (showMetadataEditor) metadataDrafts.SaveableStateProvider(metadataScope) {
        ServerMetadataDialog(server, repository, onDismiss = { showMetadataEditor = false }, onCompleted = onCompleted)
    }
}
@Composable
private fun ServerMetadataDialog(server: Server, repository: HCloudRepository, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    val context = LocalOperationContext.current
    val formScope = "$context:server:${server.id}:metadata"
    val original = remember(formScope) { server }
    val originalLabels = original.labels.entries.sortedBy { it.key }.joinToString("\n") { "${it.key}=${it.value}" }
    var name by rememberContextualText("$formScope:name", original.name)
    var labelsText by rememberContextualText("$formScope:labels", originalLabels)
    var reviewedPatch by remember(server.id) { mutableStateOf<ServerConfigurationPatch?>(null) }
    val labels = parseResourceLabels(labelsText)
    val exitGuard = rememberDirtyFormExitGuard(name != original.name || labelsText != originalLabels,
        onRetain = {}, onDiscard = { name = original.name; labelsText = originalLabels },
        onLeave = onDismiss, contextKey = formScope)
    DirtyFormExitPrompt(exitGuard)
    reviewedPatch?.let { patch ->
        ServerRoutineFlow(original, repository, com.hcloudcontrol.operations.ServerRoutineAction.METADATA,
            stringResource(R.string.server_metadata_edit_title, original.name), stringResource(R.string.server_metadata_save),
            true, onCompleted, onDismiss, com.hcloudcontrol.operations.ServerRoutineDraft(metadata = patch),
            onReviewBack = { reviewedPatch = null }, prepareImmediately = true)
        return
    }
    RegisterResourceFormNavigation(RootDestination.SERVERS, ResourceType.SERVER, original.id.toString(),
        stringResource(R.string.server_metadata_edit_title, original.name), exitGuard::requestLeave)
    HCloudDialog(stringResource(R.string.server_metadata_edit_title, original.name), stringResource(R.string.ds_operation_review),
        onConfirm = { if (name.isNotBlank() && labels != null) reviewedPatch = ServerConfigurationPatch(name.trim(), labels) },
        onDismiss = exitGuard::requestLeave, confirmEnabled = name.isNotBlank() && labels != null) {
        HCloudFormField(name, { name = it }, stringResource(R.string.server_metadata_name), singleLine = true,
            error = stringResource(R.string.server_metadata_name_invalid).takeIf { name.isBlank() })
        HCloudFormField(labelsText, { labelsText = it }, stringResource(R.string.server_metadata_labels_input),
            help = stringResource(R.string.server_metadata_labels_help),
            error = stringResource(R.string.server_metadata_labels_invalid).takeIf { labels == null })
        Text(stringResource(R.string.server_metadata_placement_group, original.placementGroupId?.toString() ?: stringResource(R.string.common_none)))
    }
}
