package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.repository.HCloudRepository

class ServerDnsPtrUseCase(private val repository: HCloudRepository) {
    suspend operator fun invoke(serverId: Long, ip: String, dnsPtr: String?): CloudAction {
        require(serverId > 0) { "Server ID must be positive" }
        require(ip.isNotBlank()) { "IP address must not be blank" }
        return repository.changeServerReverseDns(serverId, ip, dnsPtr)
    }
}
