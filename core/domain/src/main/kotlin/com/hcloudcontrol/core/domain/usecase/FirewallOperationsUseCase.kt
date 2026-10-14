package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Explicitly scoped Firewall mutations: attach or detach an existing Firewall to a Server. */
class FirewallOperationsUseCase(private val repository: HCloudRepository) {
    suspend fun applyToServer(firewallId: Long, serverId: Long): List<CloudAction> {
        validateExistingResourceIds(firewallId, serverId)
        return repository.applyFirewallToServer(firewallId, serverId)
    }

    suspend fun removeFromServer(firewallId: Long, serverId: Long): List<CloudAction> {
        validateExistingResourceIds(firewallId, serverId)
        return repository.removeFirewallFromServer(firewallId, serverId)
    }

    /** Never construct a Firewall mutation request from an absent or synthetic resource id. */
    private fun validateExistingResourceIds(firewallId: Long, serverId: Long) {
        require(firewallId > 0) { "Firewall id must identify an existing Firewall" }
        require(serverId > 0) { "Server id must identify an existing Server" }
    }
}
