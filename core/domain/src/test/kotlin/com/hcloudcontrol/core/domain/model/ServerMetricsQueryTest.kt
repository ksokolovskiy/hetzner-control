package com.hcloudcontrol.core.domain.model

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerMetricsQueryTest {
    private val start = Instant.parse("2026-10-01T00:00:00Z")

    @Test
    fun `query keeps official metric names and integral positive step`() {
        val query = ServerMetricsQuery(
            serverId = 42,
            types = listOf(ServerMetricType.CPU, ServerMetricType.NETWORK),
            start = start,
            end = start.plus(Duration.ofHours(1)),
            step = Duration.ofSeconds(60),
        )

        assertEquals("cpu,network", query.typesParameter())
        assertEquals("60", query.stepParameter())
    }

    @Test
    fun `query rejects invalid id type range and step before network`() {
        val cases = listOf(
            runCatching { ServerMetricsQuery(0, listOf(ServerMetricType.CPU), start, start.plusSeconds(1)) },
            runCatching { ServerMetricsQuery(1, emptyList(), start, start.plusSeconds(1)) },
            runCatching { ServerMetricsQuery(1, listOf(ServerMetricType.CPU, ServerMetricType.CPU), start, start.plusSeconds(1)) },
            runCatching { ServerMetricsQuery(1, listOf(ServerMetricType.CPU), start, start) },
            runCatching { ServerMetricsQuery(1, listOf(ServerMetricType.CPU), start, start.plus(Duration.ofDays(31))) },
            runCatching { ServerMetricsQuery(1, listOf(ServerMetricType.CPU), start, start.plusSeconds(1), Duration.ZERO) },
            runCatching { ServerMetricsQuery(1, listOf(ServerMetricType.CPU), start, start.plusSeconds(1), Duration.ofMillis(1500)) },
        )

        assertTrue(cases.all { it.exceptionOrNull() is IllegalArgumentException })
    }
}
