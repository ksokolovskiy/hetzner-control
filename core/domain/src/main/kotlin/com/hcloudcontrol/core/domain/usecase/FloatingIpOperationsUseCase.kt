package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.FloatingIpInventory
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Guards only locally knowable Floating IP operations; Hetzner remains the source of truth. */
class FloatingIpOperationsUseCase(private val repository: HCloudRepository) {
    suspend fun assign(floatingIp: FloatingIpInventory, target: Server): CloudAction {
        require(floatingIp.id > 0) { "Floating IP ID must be positive" }
        require(target.id > 0) { "Server ID must be positive" }
        // Floating IPs can be reassigned globally, including while assigned; do not invent
        // Primary-IP location or power-state restrictions.
        return repository.assignFloatingIp(floatingIp.id, target.id)
    }

    suspend fun unassign(floatingIp: FloatingIpInventory): CloudAction {
        require(floatingIp.id > 0) { "Floating IP ID must be positive" }
        require(floatingIp.assigneeId != null) { "Floating IP is not assigned" }
        return repository.unassignFloatingIp(floatingIp.id)
    }

    suspend fun changeReverseDns(floatingIp: FloatingIpInventory, ip: String, dnsPtr: String?): CloudAction {
        require(ip.isNotBlank()) { "IP address must not be blank" }
        require(floatingIp.dnsPointers.any { it.ip == ip } || floatingIp.address == ip) {
            "IP address does not belong to this Floating IP"
        }
        val normalizedDnsPtr = dnsPtr?.trim()?.ifBlank { null }
        require(normalizedDnsPtr?.endsWith('.') != true) { "DNS PTR must not end with a dot" }
        return repository.changeFloatingIpReverseDns(floatingIp.id, ip, normalizedDnsPtr)
    }

    /**
     * The Cloud API no longer permits deleting an assigned Floating IP. Keep that official
     * precondition local as well so a stale/incorrect UI path cannot issue the request.
     */
    suspend fun deleteUnassigned(floatingIp: FloatingIpInventory) {
        requireFloatingIpDeletionPreconditions(floatingIp)
        repository.deleteFloatingIp(floatingIp.id)
    }
}

/** Kept separately testable so a UI stale-state bug cannot bypass deletion checks. */
internal fun requireFloatingIpDeletionPreconditions(floatingIp: FloatingIpInventory) {
    require(floatingIp.id > 0) { "Floating IP ID must be positive" }
    require(floatingIp.assigneeId == null) { "Assigned Floating IP must be unassigned before deletion" }
    require(!floatingIp.deleteProtected) { "Floating IP deletion is protected" }
}
