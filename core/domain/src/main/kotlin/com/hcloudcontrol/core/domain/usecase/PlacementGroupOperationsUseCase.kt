package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.CreatePlacementGroupSpec
import com.hcloudcontrol.core.domain.model.CreatedPlacementGroup
import com.hcloudcontrol.core.domain.model.PlacementGroupInventory
import com.hcloudcontrol.core.domain.model.PlacementGroupPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository

class PlacementGroupOperationsUseCase(private val repository: HCloudRepository) {
    suspend fun create(spec: CreatePlacementGroupSpec): CreatedPlacementGroup {
        require(spec.name.isNotBlank()) { "Placement group name must not be blank" }
        require(spec.type == "spread") { "Unsupported placement group type" }
        return repository.createPlacementGroup(spec)
    }

    suspend fun update(placementGroupId: Long, patch: PlacementGroupPatch): PlacementGroupInventory {
        require(placementGroupId > 0) { "Placement group ID must be positive" }
        return repository.updatePlacementGroup(placementGroupId, patch)
    }

    suspend fun delete(placementGroupId: Long) {
        require(placementGroupId > 0) { "Placement group ID must be positive" }
        repository.deletePlacementGroup(placementGroupId)
    }

    suspend fun addServer(serverId: Long, placementGroupId: Long): CloudAction {
        validateIds(serverId, placementGroupId)
        return repository.addServerToPlacementGroup(serverId, placementGroupId)
    }

    suspend fun removeServer(serverId: Long, placementGroupId: Long): CloudAction {
        validateIds(serverId, placementGroupId)
        return repository.removeServerFromPlacementGroup(serverId, placementGroupId)
    }

    private fun validateIds(serverId: Long, placementGroupId: Long) {
        require(serverId > 0) { "Server ID must be positive" }
        require(placementGroupId > 0) { "Placement group ID must be positive" }
    }
}
