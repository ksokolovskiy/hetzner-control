package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.ServerMetricSample
import com.hcloudcontrol.core.domain.model.ServerMetricSeries
import com.hcloudcontrol.core.domain.model.ServerMetrics
import com.hcloudcontrol.core.domain.model.ServerMetricsQuery
import com.hcloudcontrol.core.domain.repository.ServerMetricsRepository
import java.time.Instant

internal class RetrofitServerMetricsRepository(private val api: HCloudApi) : ServerMetricsRepository {
    override suspend fun getServerMetrics(query: ServerMetricsQuery): ServerMetrics {
        val response = api.getServerMetrics(
            serverId = query.serverId,
            types = query.typesParameter(),
            start = query.start.toString(),
            end = query.end.toString(),
            step = query.stepParameter(),
        ).metrics
        return response.toDomain()
    }
}

/** HCloud-only factory: no Storage Box host or credential provider is accepted. */
object ServerMetricsRepositoryFactory {
    fun create(
        hCloudTokenProvider: () -> String?,
        hCloudConfigProvider: () -> HCloudConnectionConfig = HCloudConnectionConfig::real,
    ): ServerMetricsRepository = RetrofitServerMetricsRepository(
        HCloudApiFactory.create(hCloudTokenProvider, hCloudConfigProvider),
    )
}

private fun ServerMetricsDto.toDomain(): ServerMetrics = ServerMetrics(
    start = Instant.parse(start),
    end = Instant.parse(end),
    stepSeconds = step,
    timeSeries = timeSeries.mapValues { (_, series) ->
        ServerMetricSeries(series.values.map { raw ->
            require(raw.size == 2) { "A metrics sample must contain timestamp and value" }
            val timestamp = raw[0] as? Number ?: error("Metrics timestamp must be numeric")
            val value = raw[1] as? String ?: error("Metrics value must be a string")
            ServerMetricSample(timestampSeconds = timestamp.toDouble(), value = value)
        })
    },
)
