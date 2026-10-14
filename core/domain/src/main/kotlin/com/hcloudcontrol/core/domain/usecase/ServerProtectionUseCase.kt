package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository

class ServerProtectionUseCase(private val repository: HCloudRepository) {
    suspend operator fun invoke(serverId: Long, patch: ServerProtectionPatch): CloudAction {
        require(serverId > 0) { "Server ID must be positive" }
        return repository.changeServerProtection(serverId, patch)
    }
}
