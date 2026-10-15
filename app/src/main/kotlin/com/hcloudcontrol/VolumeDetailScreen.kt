package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.VolumeInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import com.hcloudcontrol.ui.navigation.*

/**
 * Read-only detail route backed by the documented `GET /volumes/{id}` and
 * `GET /volumes/{id}/actions` endpoints. All lifecycle entry points share this canonical detail.
 */
@Composable
internal fun VolumeDetailScreen(volumeId: Long, repository: HCloudRepository, onBack: () -> Unit) {
    val key = LocalScrollProjectNamespace.current + ":volume:" + volumeId
    val detail = com.hcloudcontrol.ui.state.rememberSectionLoader(key + ":metadata") { repository.getVolume(volumeId) }
    val history = com.hcloudcontrol.ui.state.rememberSectionLoader(key + ":history", { it: List<CloudAction> -> it.isEmpty() }) {
        repository.getVolumeActions(volumeId)
    }
    val hosted = LocalNavigationChrome.current != null
    RegisterResourceChildNavigation(RootDestination.STORAGE, ResourceType.VOLUME, volumeId.toString(),
        detail.state.data?.name ?: stringResource(R.string.volume_detail_title), onBack)
    HCloudPullToRefresh(detail.state.refreshing || history.state.refreshing,
        onRefresh = { detail.refresh(); history.refresh() }, modifier = Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        state = rememberRestorableLazyListState(key,
            contentReady = detail.state.hasSuccessfulResponse &&
                (history.state.hasSuccessfulResponse || history.state.error != null)),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hosted) item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        if (!hosted) item { Text(stringResource(R.string.volume_detail_title), style = MaterialTheme.typography.titleLarge) }
        item {
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(detail) { current ->
                VolumeDetailsCard(current)
                ResourceProtectionControls(
                    resourceName = current.name,
                    currentProtected = current.deleteProtected,
                    currentKnown = current.protectionKnown,
                    adapter = com.hcloudcontrol.operations.ProtectionAdapter(
                        com.hcloudcontrol.operations.ProtectionResource.VOLUME, current.id,
                        com.hcloudcontrol.operations.ProtectionField.DELETE, repository,
                    ),
                    onCompleted = { detail.refresh() },
                    enabled = detail.state.canOfferMutation,
                )
                VolumeLifecycleControls(current, repository, detail.state.canOfferMutation,
                    onCompleted = { detail.refresh(); history.refresh() }, onDeleted = onBack)
            }
        }
        item {
            com.hcloudcontrol.ui.designsystem.HCloudReadSection(history) { actions -> VolumeActionHistory(actions) }
        }
    }
    }
}
@Composable
private fun VolumeDetailsCard(volume: VolumeInventory) = Card(
    Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(volume.name, style = MaterialTheme.typography.titleLarge)
        VolumeDetailRow(stringResource(R.string.volume_detail_id), volume.id.toString())
        VolumeDetailRow(stringResource(R.string.volume_detail_size), stringResource(R.string.volume_detail_size_value, volume.sizeGb))
        VolumeDetailRow(stringResource(R.string.volume_detail_location), volume.location)
        VolumeDetailRow(stringResource(R.string.volume_detail_status), volume.status)
        val attachedServerId = volume.serverId
        VolumeDetailRow(
            stringResource(R.string.volume_detail_attachment),
            if (attachedServerId == null) stringResource(R.string.storage_volume_unattached)
            else stringResource(R.string.storage_volume_attached, attachedServerId),
        )
        VolumeDetailRow(
            stringResource(R.string.volume_detail_protection),
            stringResource(if (!volume.protectionKnown) R.string.ds_cost_unknown else if (volume.deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled),
        )
        if (volume.labels.isNotEmpty()) {
            VolumeDetailRow(stringResource(R.string.volume_detail_labels), volume.labels.toSortedMap().entries.joinToString { "${it.key}=${it.value}" })
        }
        Text(stringResource(R.string.volume_detail_read_only_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VolumeDetailRow(label: String, value: String) = Column(
    Modifier.fillMaxWidth(),
    verticalArrangement = Arrangement.spacedBy(4.dp),
) {
    Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Text(value, fontWeight = FontWeight.Medium)
}

@Composable
private fun VolumeActionHistory(actions: List<CloudAction>) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.volume_action_history_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.volume_action_history_description), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (actions.isEmpty()) {
        Text(stringResource(R.string.volume_action_history_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        actions.sortedByDescending { it.id }.forEach { action ->
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(action.command ?: stringResource(R.string.volume_action_unknown), style = MaterialTheme.typography.titleSmall)
                    Text(stringResource(R.string.volume_action_status, action.status), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    action.progress?.let { Text(stringResource(R.string.volume_action_progress, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    action.started?.let { Text(stringResource(R.string.volume_action_started, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    action.finished?.let { Text(stringResource(R.string.volume_action_finished, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    // Never render server-provided raw error detail in a shareable action timeline.
                    action.errorCode?.let { Text(stringResource(R.string.volume_action_error_code, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
        }
    }
}
