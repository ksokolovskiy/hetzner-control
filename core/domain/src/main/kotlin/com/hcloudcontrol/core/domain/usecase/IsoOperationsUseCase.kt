package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.IsoSelection
import com.hcloudcontrol.core.domain.repository.HCloudRepository

class IsoOperationsUseCase(private val repository: HCloudRepository) {
    suspend fun attach(serverId: Long, iso: IsoSelection): CloudAction = repository.attachIso(serverId, iso)
    suspend fun detach(serverId: Long): CloudAction = repository.detachIso(serverId)
}
