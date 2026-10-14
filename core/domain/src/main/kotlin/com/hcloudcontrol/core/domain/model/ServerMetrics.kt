package com.hcloudcontrol.core.domain.model

import java.time.Duration
import java.time.Instant

enum class ServerMetricType(val apiValue: String) { CPU("cpu"), DISK("disk"), NETWORK("network") }

/** Validated request for the read-only Hetzner Cloud server metrics endpoint. */
data class ServerMetricsQuery(
    val serverId: Long,
    val types: List<ServerMetricType>,
    val start: Instant,
    val end: Instant,
    val step: Duration? = null,
) {
    init {
        require(serverId > 0) { "Server ID must be positive" }
        require(types.isNotEmpty()) { "At least one metric type is required" }
        require(types.distinct().size == types.size) { "Metric types must not repeat" }
        require(start.isBefore(end)) { "Metrics start must be before end" }
        require(Duration.between(start, end) <= MAXIMUM_WINDOW) { "Metrics window must not exceed 30 days" }
        step?.let {
            require(!it.isZero && !it.isNegative) { "Metrics step must be positive" }
            require(it.nano == 0) { "Metrics step must be a whole number of seconds" }
        }
    }

    /** Canonical query value required by the documented comma-separated API parameter. */
    fun typesParameter(): String = types.joinToString(",") { it.apiValue }
    /** Canonical whole-second query value; null leaves API step selection to Hetzner. */
    fun stepParameter(): String? = step?.seconds?.toString()

    private companion object { val MAXIMUM_WINDOW: Duration = Duration.ofDays(30) }
}

/** Returned metrics values are strings as supplied by the API; no measurement conversion occurs. */
data class ServerMetrics(
    val start: Instant,
    val end: Instant,
    val stepSeconds: Double,
    val timeSeries: Map<String, ServerMetricSeries>,
)

data class ServerMetricSeries(val samples: List<ServerMetricSample>)
data class ServerMetricSample(val timestampSeconds: Double, val value: String)
