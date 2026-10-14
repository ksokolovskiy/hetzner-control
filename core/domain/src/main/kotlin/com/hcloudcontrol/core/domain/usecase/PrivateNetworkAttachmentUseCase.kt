package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.PrivateNetworkInventory
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/**
 * Safe first slice for private networking: only attach/detach existing resources.
 * Address selection remains with Hetzner, avoiding client-side subnet/IP allocation mistakes.
 */
class PrivateNetworkAttachmentUseCase(private val repository: HCloudRepository) {
    suspend fun attach(network: PrivateNetworkInventory, server: Server): CloudAction {
        require(server.id !in network.serverIds) { "Server is already attached to this network" }
        require(network.subnets.any { it.type == "cloud" }) { "Network has no Cloud subnet" }
        return repository.attachServerToPrivateNetwork(network.id, server.id)
    }

    suspend fun detach(network: PrivateNetworkInventory, server: Server): CloudAction {
        require(server.id in network.serverIds) { "Server is not attached to this network" }
        return repository.detachServerFromPrivateNetwork(network.id, server.id)
    }
}
