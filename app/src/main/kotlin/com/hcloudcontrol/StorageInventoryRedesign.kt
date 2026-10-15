package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.VolumeInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader

/** One storage entry: aliases open the canonical volume/image detail, never another implementation. */
@Composable
internal fun StorageInventoryRedesign(repository: HCloudRepository, projectId: String, operationsAllowed: Boolean) {
    val contextKey = "$projectId:${LocalNavigationContext.current?.endpoint.orEmpty()}"
    var selectedVolumeId by rememberContextualResourceId("volume:$contextKey")
    var selectedImageId by rememberContextualResourceId("storage-image:$contextKey")
    var tab by rememberSaveable(contextKey) { mutableStateOf("volumes") }
    var volumeQuery by rememberSaveable(contextKey) { mutableStateOf("") }
    var snapshotQuery by rememberSaveable(contextKey) { mutableStateOf("") }
    var backupQuery by rememberSaveable(contextKey) { mutableStateOf("") }
    val query = when (tab) { "volumes" -> volumeQuery; "snapshot" -> snapshotQuery; else -> backupQuery }
    var createVolume by remember(contextKey) { mutableStateOf(false) }
    val volumes = rememberSectionLoader("$contextKey:volumes", { it: List<VolumeInventory> -> it.isEmpty() }) { repository.getVolumes() }
    val images = rememberSectionLoader("$contextKey:storage-images", { it: List<com.hcloudcontrol.core.domain.model.BackupSnapshotImage> -> it.isEmpty() }) {
        repository.getBackupAndSnapshotImages()
    }
    selectedVolumeId?.let { id ->
        VolumeDetailScreen(id, repository, onBack = { selectedVolumeId = null; volumes.refresh() })
        return
    }
    selectedImageId?.let { id ->
        CloudImagesScreen(repository, projectId, initialImageId = id, onBackFromInitial = { selectedImageId = null })
        return
    }
    HCloudPullToRefresh(
        isRefreshing = if (tab == "volumes") volumes.state.refreshing else images.state.refreshing,
        onRefresh = { if (tab == "volumes") volumes.refresh() else images.refresh() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(state = rememberRestorableLazyListState("$contextKey:storage:$tab",
            contentReady = if (tab == "volumes") volumes.state.hasSuccessfulResponse else images.state.hasSuccessfulResponse),
            contentPadding = PaddingValues(HCloudDesignTokens.contentInset),
            verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
            item(key = "tabs") { HCloudTabs(listOf(
                ChoiceItem("volumes", stringResource(R.string.storage_volumes)),
                ChoiceItem("snapshot", stringResource(R.string.storage_snapshots)),
                ChoiceItem("backup", stringResource(R.string.storage_backups)),
            ), tab, { tab = it }) }
            item(key = "search") { HCloudFormField(query, {
                when (tab) { "volumes" -> volumeQuery = it; "snapshot" -> snapshotQuery = it; else -> backupQuery = it }
            }, stringResource(R.string.ds_search)) }
            if (tab == "volumes") {
                if (operationsAllowed && volumes.state.canOfferMutation) item(key = "create") {
                    Button(onClick = { createVolume = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.volume_create)) }
                }
                val matching = volumes.state.data.orEmpty().filter { it.name.contains(query.trim(), true) || it.id.toString() == query.trim() }
                    .sortedWith(compareBy<VolumeInventory> { it.name.lowercase() }.thenBy { it.id })
                item(key = "volume-state") { HCloudReadSection(volumes) {
                    if (matching.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS))
                } }
                items(matching, key = { "volume:${it.id}" }) { volume ->
                    HCloudResourceCard(ResourceCardModel("$contextKey:volume:${volume.id}", volume.name, volume.status,
                        stringResource(R.string.storage_volume_size_location, volume.sizeGb, volume.location),
                        stale = volumes.state.isStale), { selectedVolumeId = volume.id })
                }
            } else {
                val matching = images.state.data.orEmpty().filter { it.type.equals(tab, true) }
                    .filter { (it.description ?: it.name ?: it.id.toString()).contains(query.trim(), true) }
                    .sortedBy { it.description ?: it.name ?: it.id.toString() }
                item(key = "image-state") { HCloudReadSection(images) {
                    if (matching.isEmpty()) HCloudSectionState(SectionPresentation(
                        if (query.isBlank()) SectionKind.EMPTY else SectionKind.NO_RESULTS))
                } }
                items(matching, key = { "image:${it.id}" }) { image ->
                    HCloudResourceCard(ResourceCardModel("$contextKey:image:${image.id}", image.description ?: image.name ?: image.id.toString(),
                        "", image.createdAt.orEmpty(), stale = images.state.isStale), { selectedImageId = image.id })
                }
            }
        }
    }
    if (createVolume && operationsAllowed) CreateVolumeDialog(repository,
        onCompleted = volumes::refresh, onDismiss = { createVolume = false },
        onCreated = { id -> createVolume = false; volumes.refresh(); selectedVolumeId = id })
}
