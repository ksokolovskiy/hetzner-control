package com.hcloudcontrol.core.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LoadBalancerMetricsQueryTest {
    private val start = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    fun `query keeps official load balancer metric names and validates range`() {
        val query = LoadBalancerMetricsQuery(
            loadBalancerId = 901,
            types = listOf(LoadBalancerMetricType.REQUESTS_PER_SECOND, LoadBalancerMetricType.BANDWIDTH),
            start = start,
            end = start.plus(Duration.ofHours(1)),
            step = Duration.ofSeconds(60),
        )

        assertEquals("requests_per_second,bandwidth", query.typesParameter())
        assertEquals("60", query.stepParameter())
        val invalid = listOf(
            runCatching { LoadBalancerMetricsQuery(0, listOf(LoadBalancerMetricType.BANDWIDTH), start, start.plusSeconds(1)) },
            runCatching { LoadBalancerMetricsQuery(1, emptyList(), start, start.plusSeconds(1)) },
            runCatching { LoadBalancerMetricsQuery(1, listOf(LoadBalancerMetricType.BANDWIDTH, LoadBalancerMetricType.BANDWIDTH), start, start.plusSeconds(1)) },
            runCatching { LoadBalancerMetricsQuery(1, listOf(LoadBalancerMetricType.BANDWIDTH), start, start.plus(Duration.ofDays(31))) },
            runCatching { LoadBalancerMetricsQuery(1, listOf(LoadBalancerMetricType.BANDWIDTH), start, start.plusSeconds(1), Duration.ofMillis(1500)) },
        )
        assertTrue(invalid.all { it.exceptionOrNull() is IllegalArgumentException })
    }
}
