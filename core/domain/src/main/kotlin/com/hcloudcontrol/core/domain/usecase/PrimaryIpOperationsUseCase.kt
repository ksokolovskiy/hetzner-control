package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.PrimaryIpInventory
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerStatus
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/**
 * Guards the Primary IP operations that are exposed by the mobile UI.
 * Hetzner remains the source of truth, but rejecting known-invalid operations locally prevents
 * an accidental public-network outage before a request reaches the API.
 */
class PrimaryIpOperationsUseCase(private val repository: HCloudRepository) {
    suspend fun deleteUnassigned(primaryIp: PrimaryIpInventory) {
        requirePrimaryIpDeletionPreconditions(primaryIp)
        repository.deletePrimaryIp(primaryIp.id)
    }
    suspend fun assign(primaryIp: PrimaryIpInventory, target: Server): CloudAction {
        require(primaryIpAssignmentBlock(primaryIp, target) == null) { "Primary IP assignment preconditions are not met" }
        return repository.assignPrimaryIp(primaryIp.id, target.id)
    }

    suspend fun unassign(primaryIp: PrimaryIpInventory, currentServer: Server?): CloudAction {
        require(primaryIp.assigneeId != null) { "Primary IP is not assigned" }
        require(primaryIp.assigneeType == "server") { "Only server assignments are supported" }
        require(currentServer?.id == primaryIp.assigneeId) { "Assigned server identity differs" }
        require(currentServer?.status == ServerStatus.OFF) { "Assigned server must be powered off" }
        return repository.unassignPrimaryIp(primaryIp.id)
    }

    suspend fun changeReverseDns(primaryIp: PrimaryIpInventory, ip: String, dnsPtr: String?): CloudAction {
        require(ip.isNotBlank()) { "IP address must not be blank" }
        require(primaryIp.dnsPointers.any { it.ip == ip } || primaryIp.address == ip) {
            "IP address does not belong to this Primary IP"
        }
        return repository.changePrimaryIpReverseDns(primaryIp.id, ip, dnsPtr?.trim()?.ifBlank { null })
    }
}

/** Kept separate so stale UI state cannot bypass the API's delete preconditions. */
internal fun requirePrimaryIpDeletionPreconditions(primaryIp: PrimaryIpInventory) {
    require(primaryIp.id > 0) { "Primary IP ID must be positive" }
    require(primaryIp.assigneeId == null) { "Assigned Primary IP must be unassigned before deletion" }
    require(!primaryIp.deleteProtected) { "Primary IP deletion is protected" }
}
