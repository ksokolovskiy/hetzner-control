package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.EnableRescueResult
import com.hcloudcontrol.core.domain.model.EnableRescueSpec
import com.hcloudcontrol.core.domain.model.ResetRootPasswordResult
import com.hcloudcontrol.core.domain.repository.HCloudRepository

/** Recovery mutations. Returned actions may be running and should be polled via getAction by callers. */
class ServerRecoveryUseCase(private val repository: HCloudRepository) {
    suspend fun resetRootPassword(serverId: Long): ResetRootPasswordResult = repository.resetRootPassword(serverId)
    suspend fun enableRescue(serverId: Long, spec: EnableRescueSpec = EnableRescueSpec()): EnableRescueResult =
        repository.enableRescue(serverId, spec)
    suspend fun disableRescue(serverId: Long): CloudAction = repository.disableRescue(serverId)
}
