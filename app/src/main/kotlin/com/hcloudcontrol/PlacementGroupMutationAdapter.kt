package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.validResourceLabel

internal sealed interface PlacementChange {
    data class Create(val spec: CreatePlacementGroupSpec) : PlacementChange
    data class Edit(val before: PlacementGroupInventory, val patch: PlacementGroupPatch) : PlacementChange
    data class Delete(val before: PlacementGroupInventory) : PlacementChange
    data class Member(val before: PlacementGroupInventory, val serverId: Long, val add: Boolean) : PlacementChange
}
internal data class PlacementMutationResult(val action: CloudAction? = null, val createdId: Long? = null)

/** Feature adapters freeze data/preconditions; shared coordinator alone owns execution state. */
internal class PlacementGroupMutationAdapter(private val repository: HCloudRepository) {
    suspend fun validate(change: PlacementChange) {
        if (change is PlacementChange.Create) {
            require(change.spec.type == "spread" && change.spec.name.isNotBlank())
            require(change.spec.labels.all { validResourceLabel(it.key, it.value) })
            repository.getPlacementGroups()
            return
        }
        val before = when (change) {
            is PlacementChange.Edit -> change.before
            is PlacementChange.Delete -> change.before
            is PlacementChange.Member -> change.before
            else -> error("Unsupported placement operation")
        }
        val current = repository.getPlacementGroup(before.id)
        check(current.id == before.id && current.name == before.name && current.type == before.type &&
            current.labels == before.labels && current.serverIds.toSet() == before.serverIds.toSet())
        if (change is PlacementChange.Delete) check(current.deleteProtected != true)
        if (change is PlacementChange.Edit) require(change.patch.labels?.all { validResourceLabel(it.key, it.value) } != false)
        if (change is PlacementChange.Member) {
            val server = repository.getServer(change.serverId)
            check(if (change.add) server.placementGroupId == null && server.id !in current.serverIds
                else server.placementGroupId == current.id && server.id in current.serverIds)
        }
    }
    suspend fun execute(change: PlacementChange): PlacementMutationResult = when (change) {
        is PlacementChange.Create -> repository.createPlacementGroup(change.spec).let { PlacementMutationResult(it.action, it.placementGroup.id) }
        is PlacementChange.Edit -> { repository.updatePlacementGroup(change.before.id, change.patch); PlacementMutationResult() }
        is PlacementChange.Delete -> { repository.deletePlacementGroup(change.before.id); PlacementMutationResult() }
        is PlacementChange.Member -> PlacementMutationResult(if (change.add) repository.addServerToPlacementGroup(change.serverId, change.before.id)
            else repository.removeServerFromPlacementGroup(change.serverId, change.before.id))
    }
}
