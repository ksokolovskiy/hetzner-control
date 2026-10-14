package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.ServerMetrics
import com.hcloudcontrol.core.domain.model.ServerMetricsQuery

/** Read-only Cloud monitoring boundary. */
interface ServerMetricsRepository {
    suspend fun getServerMetrics(query: ServerMetricsQuery): ServerMetrics
}
