package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*

/** Read-only evidence adapter for a lost action response. It never reconstructs proof from names. */
internal class OriginalCloudResourceStatusReader(private val repository: HCloudRepository) {
    suspend fun read(record: PersistedOperation): OperationCoordinator.Reconciliation {
        val identity = record.intent.resource
        require(identity.resourceType in setOf("firewall", "placement_group"))
        val createdId = record.createdResourceId?.toLongOrNull()?.takeIf { it > 0 }
        if (identity.resourceId == null && createdId != null) {
            val exists = try { readExact(identity.resourceType, createdId, creation = true) }
                catch (error: HCloudApiException) { if (error.failure.httpStatus == 404) false else throw error }
            return if (exists) OperationCoordinator.Reconciliation.CreatedResourceEffect(identity.copy(resourceId = createdId.toString()), true)
                else OperationCoordinator.Reconciliation.Ambiguous
        }
        val originalId = identity.resourceId?.toLongOrNull()?.takeIf { it > 0 }
        if (originalId == null) {
            // A list is diagnostic evidence only. Even a matching name cannot identify our creation.
            when (identity.resourceType) {
                "firewall" -> repository.getFirewalls()
                "placement_group" -> repository.getPlacementGroups()
            }
            return OperationCoordinator.Reconciliation.Ambiguous
        }
        val exists = try { readExact(identity.resourceType, originalId, creation = false) }
            catch (error: HCloudApiException) { if (error.failure.httpStatus == 404) false else throw error }
        if (record.intent.action == "delete") return OperationCoordinator.Reconciliation.ExistingResourceEffect(identity, !exists)
        // Member IDs are pinned in the original action name; never consult a new picker selection.
        val serverId = when {
            identity.resourceType == "placement_group" && record.intent.action.startsWith("add_server_") -> record.intent.action.removePrefix("add_server_").toLongOrNull()
            identity.resourceType == "placement_group" && record.intent.action.startsWith("remove_server_") -> record.intent.action.removePrefix("remove_server_").toLongOrNull()
            identity.resourceType == "firewall" && record.intent.action.startsWith("apply_") -> record.intent.action.removePrefix("apply_").toLongOrNull()
            identity.resourceType == "firewall" && record.intent.action.startsWith("remove_") -> record.intent.action.removePrefix("remove_").toLongOrNull()
            else -> null
        }
        if (serverId != null && serverId > 0) check(repository.getServer(serverId).id == serverId)
        // Desired metadata/rules and the complete original review were not persisted. Read the
        // original resources, but retain uncertainty rather than manufacture successful effects.
        return OperationCoordinator.Reconciliation.Ambiguous
    }

    private suspend fun readExact(type: String, id: Long, creation: Boolean): Boolean = when (type) {
        "firewall" -> repository.getFirewall(id).let { check(it.id == id); true }
        "placement_group" -> repository.getPlacementGroup(id).let { check(it.id == id); !creation || it.type == "spread" }
        else -> error("Unsupported original resource type")
    }
}
