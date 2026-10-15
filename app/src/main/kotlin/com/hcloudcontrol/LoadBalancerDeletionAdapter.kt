package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository

internal class LoadBalancerDeletionAdapter(private val repository: HCloudRepository, private val reviewed: LoadBalancerInventory) {
    suspend fun validateFresh() {
        val current = repository.getLoadBalancer(reviewed.id)
        check(current.id == reviewed.id && current.name == reviewed.name && canDeleteLoadBalancer(current) &&
            current.services == reviewed.services &&
            current.targets.map { it.withoutLiveHealth() } == reviewed.targets.map { it.withoutLiveHealth() })
    }
    suspend fun delete(): CloudAction = repository.deleteLoadBalancer(reviewed.id)
    suspend fun deleted(): Boolean = try { repository.getLoadBalancer(reviewed.id); false } catch (failure: HCloudApiException) {
        if (failure.failure.httpStatus == 404) true else throw failure
    }
}

internal fun canDeleteLoadBalancer(value: LoadBalancerInventory): Boolean = value.protectionKnown && !value.deleteProtected

/** Live health is telemetry, not a dependency identity. Resolved target IDs remain compared. */
internal fun LoadBalancerTarget.withoutLiveHealth(): LoadBalancerTarget = copy(healthStatuses = emptyList(),
    targets = targets.map { it.withoutLiveHealth() })
