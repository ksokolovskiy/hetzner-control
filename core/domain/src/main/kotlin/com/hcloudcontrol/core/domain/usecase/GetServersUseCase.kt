package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository

class GetServersUseCase(private val repository: HCloudRepository) {
    suspend operator fun invoke(): List<Server> = repository.getServers()
}
