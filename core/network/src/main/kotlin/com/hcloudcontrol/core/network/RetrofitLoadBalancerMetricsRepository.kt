package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricSample
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricSeries
import com.hcloudcontrol.core.domain.model.LoadBalancerMetrics
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricsQuery
import com.hcloudcontrol.core.domain.repository.LoadBalancerMetricsRepository
import java.time.Instant

internal class RetrofitLoadBalancerMetricsRepository(private val api: HCloudApi) : LoadBalancerMetricsRepository {
    override suspend fun getLoadBalancerMetrics(query: LoadBalancerMetricsQuery): LoadBalancerMetrics =
        api.getLoadBalancerMetrics(
            loadBalancerId = query.loadBalancerId,
            types = query.typesParameter(),
            start = query.start.toString(),
            end = query.end.toString(),
            step = query.stepParameter(),
        ).metrics.toDomain()
}

/** HCloud-only factory: no Storage Box host or credential provider is accepted. */
object LoadBalancerMetricsRepositoryFactory {
    fun create(
        hCloudTokenProvider: () -> String?,
        hCloudConfigProvider: () -> HCloudConnectionConfig = HCloudConnectionConfig::real,
    ): LoadBalancerMetricsRepository = RetrofitLoadBalancerMetricsRepository(
        HCloudApiFactory.create(hCloudTokenProvider, hCloudConfigProvider),
    )
}

private fun LoadBalancerMetricsDto.toDomain(): LoadBalancerMetrics = LoadBalancerMetrics(
    start = Instant.parse(start),
    end = Instant.parse(end),
    stepSeconds = step,
    timeSeries = timeSeries.mapValues { (_, series) ->
        LoadBalancerMetricSeries(series.values.map { raw ->
            require(raw.size == 2) { "A metrics sample must contain timestamp and value" }
            val timestamp = raw[0] as? Number ?: error("Metrics timestamp must be numeric")
            val value = raw[1] as? String ?: error("Metrics value must be a string")
            LoadBalancerMetricSample(timestampSeconds = timestamp.toDouble(), value = value)
        })
    },
)
