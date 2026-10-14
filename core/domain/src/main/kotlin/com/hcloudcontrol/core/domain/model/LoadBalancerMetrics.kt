package com.hcloudcontrol.core.domain.model

import java.time.Duration
import java.time.Instant

/** Types documented by Hetzner for Load Balancer monitoring. */
enum class LoadBalancerMetricType(val apiValue: String) {
    OPEN_CONNECTIONS("open_connections"),
    CONNECTIONS_PER_SECOND("connections_per_second"),
    REQUESTS_PER_SECOND("requests_per_second"),
    BANDWIDTH("bandwidth"),
}

/** Validated read-only query for the documented 30-day Load Balancer metrics window. */
data class LoadBalancerMetricsQuery(
    val loadBalancerId: Long,
    val types: List<LoadBalancerMetricType>,
    val start: Instant,
    val end: Instant,
    val step: Duration? = null,
) {
    init {
        require(loadBalancerId > 0) { "Load Balancer ID must be positive" }
        require(types.isNotEmpty()) { "At least one metric type is required" }
        require(types.distinct().size == types.size) { "Metric types must not repeat" }
        require(start.isBefore(end)) { "Metrics start must be before end" }
        require(Duration.between(start, end) <= MAXIMUM_WINDOW) { "Metrics window must not exceed 30 days" }
        step?.let {
            require(!it.isZero && !it.isNegative) { "Metrics step must be positive" }
            require(it.nano == 0) { "Metrics step must be a whole number of seconds" }
        }
    }

    fun typesParameter(): String = types.joinToString(",") { it.apiValue }
    fun stepParameter(): String? = step?.seconds?.toString()

    private companion object { val MAXIMUM_WINDOW: Duration = Duration.ofDays(30) }
}

/** Values are preserved exactly as API-supplied strings; no unit conversion occurs. */
data class LoadBalancerMetrics(
    val start: Instant,
    val end: Instant,
    val stepSeconds: Double,
    val timeSeries: Map<String, LoadBalancerMetricSeries>,
)

data class LoadBalancerMetricSeries(val samples: List<LoadBalancerMetricSample>)
data class LoadBalancerMetricSample(val timestampSeconds: Double, val value: String)
