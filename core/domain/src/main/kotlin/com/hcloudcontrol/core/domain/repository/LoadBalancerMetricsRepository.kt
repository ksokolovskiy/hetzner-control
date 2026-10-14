package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.LoadBalancerMetrics
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricsQuery

/** Read-only Cloud monitoring boundary for a Load Balancer. */
interface LoadBalancerMetricsRepository {
    suspend fun getLoadBalancerMetrics(query: LoadBalancerMetricsQuery): LoadBalancerMetrics
}
