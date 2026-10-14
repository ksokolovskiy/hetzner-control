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

private sealed interface VolumeDetailState {
    data object Loading : VolumeDetailState
    data class Content(val volume: VolumeInventory, val actions: List<CloudAction>) : VolumeDetailState
    data class Error(val detailsFailed: Boolean, val historyFailed: Boolean) : VolumeDetailState
}

/**
 * Read-only detail route backed by the documented `GET /volumes/{id}` and
 * `GET /volumes/{id}/actions` endpoints. Mutations intentionally remain in the parent inventory.
 */
@Composable
internal fun VolumeDetailScreen(initial: VolumeInventory, repository: HCloudRepository, onBack: () -> Unit) {
    var refreshKey by remember(initial.id) { mutableStateOf(0) }
    var state by remember(initial.id) { mutableStateOf<VolumeDetailState>(VolumeDetailState.Loading) }
    LaunchedEffect(initial.id, refreshKey) {
        state = VolumeDetailState.Loading
        coroutineScope {
            val detail = async(Dispatchers.IO) { runCatching { repository.getVolume(initial.id) } }
            val history = async(Dispatchers.IO) { runCatching { repository.getVolumeActions(initial.id) } }
            val detailResult = detail.await()
            val historyResult = history.await()
            state = if (detailResult.isSuccess && historyResult.isSuccess) {
                VolumeDetailState.Content(detailResult.getOrThrow(), historyResult.getOrThrow())
            } else {
                VolumeDetailState.Error(detailResult.isFailure, historyResult.isFailure)
            }
        }
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item { Text(stringResource(R.string.volume_detail_title), style = MaterialTheme.typography.headlineMedium) }
        when (val current = state) {
            VolumeDetailState.Loading -> item { VolumeDetailLoading(stringResource(R.string.volume_detail_loading)) }
            is VolumeDetailState.Error -> item {
                val message = when {
                    current.detailsFailed && current.historyFailed -> stringResource(R.string.volume_detail_load_error)
                    current.detailsFailed -> stringResource(R.string.volume_detail_load_error)
                    else -> stringResource(R.string.volume_action_history_load_error)
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(message, color = MaterialTheme.colorScheme.error)
                    OutlinedButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.common_retry)) }
                }
            }
            is VolumeDetailState.Content -> {
                item { VolumeDetailsCard(current.volume) }
                item {
                    ResourceProtectionControls(
                        resourceName = current.volume.name,
                        deleteProtected = current.volume.deleteProtected,
                        repository = repository,
                        onChange = { patch -> repository.changeVolumeProtection(current.volume.id, patch) },
                        onCompleted = { refreshKey++ },
                    )
                }
                item { VolumeActionHistory(actions = current.actions) }
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
            stringResource(if (volume.deleteProtected) R.string.common_protected_enabled else R.string.common_protected_disabled),
        )
        if (volume.labels.isNotEmpty()) {
            VolumeDetailRow(stringResource(R.string.volume_detail_labels), volume.labels.toSortedMap().entries.joinToString { "${it.key}=${it.value}" })
        }
        Text(stringResource(R.string.volume_detail_read_only_note), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun VolumeDetailRow(label: String, value: String) = Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.SpaceBetween,
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

@Composable
private fun VolumeDetailLoading(message: String) = Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
) {
    CircularProgressIndicator(Modifier.padding(2.dp))
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
