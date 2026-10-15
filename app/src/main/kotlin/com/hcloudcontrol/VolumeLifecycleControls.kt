package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.VolumeInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.rememberSectionLoader

/** All volume lifecycle entry points live in the canonical detail, not inventory rows. */
@Composable
internal fun VolumeLifecycleControls(volume: VolumeInventory, repository: HCloudRepository,
    fresh: Boolean, onCompleted: () -> Unit, onDeleted: () -> Unit) {
    if (!LocalHighImpactOperations.current.isAllowed) return
    var action by remember(volume.id) { mutableStateOf<VolumeAction?>(null) }
    var deleting by remember(volume.id) { mutableStateOf(false) }
    var deletionConfirmed by remember(volume.id) { mutableStateOf(false) }
    val candidates = rememberSectionLoader("${LocalScrollProjectNamespace.current}:volume:${volume.id}:servers",
        { it: List<Server> -> it.isEmpty() }) { repository.getServers() }
    Column(verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
        if (volume.serverId == null) {
            OutlinedButton(onClick = { action = VolumeAction.Attach }, enabled = fresh, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.storage_attach))
            }
        } else OutlinedButton(onClick = { action = VolumeAction.Detach }, enabled = fresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.storage_detach))
        }
        OutlinedButton(onClick = { action = VolumeAction.Resize }, enabled = fresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.storage_resize))
        }
        OutlinedButton(onClick = { deleting = true }, enabled = fresh && !volume.deleteProtected && volume.serverId == null,
            modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.volume_delete), color = MaterialTheme.colorScheme.error) }
        if (volume.deleteProtected) Text(stringResource(R.string.common_protected_enabled))
    }
    action?.let { selected ->
        if (selected == VolumeAction.Attach && !candidates.state.hasSuccessfulResponse) {
            HCloudDialog(stringResource(R.string.storage_attach), stringResource(R.string.ds_close),
                { action = null }, { action = null }) { HCloudReadSection(candidates) {} }
        } else VolumeActionConfirmation(VolumeActionRequest(volume, selected),
            candidates.state.data.orEmpty().filter { it.location == volume.location }, repository,
            onCompleted = onCompleted, onDismiss = { action = null })
    }
    if (deleting) DeleteVolumeDialog(volume, repository,
        onCompleted = { deletionConfirmed = true; onCompleted() },
        onDismiss = { deleting = false; if (deletionConfirmed) onDeleted() })
}
