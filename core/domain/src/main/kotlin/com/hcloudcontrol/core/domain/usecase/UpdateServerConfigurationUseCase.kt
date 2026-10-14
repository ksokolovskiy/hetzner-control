package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerConfigurationPatch
import com.hcloudcontrol.core.domain.repository.HCloudRepository

class UpdateServerConfigurationUseCase(private val repository: HCloudRepository) {
    suspend operator fun invoke(serverId: Long, patch: ServerConfigurationPatch): Server =
        repository.updateServerConfiguration(serverId, patch)
}
