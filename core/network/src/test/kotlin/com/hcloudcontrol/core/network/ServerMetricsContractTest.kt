package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.ServerMetricType
import com.hcloudcontrol.core.domain.model.ServerMetricsQuery
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerMetricsContractTest {
    @Test
    fun `metrics uses HCloud host credential and preserves string samples`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{
                      "metrics": {
                        "start":"2026-10-01T00:00:00Z", "end":"2026-10-01T01:00:00Z", "step":60,
                        "time_series":{"cpu":{"values":[[1727740800.5,"42.25"]]},"network.0.bandwidth.in":{"values":[[1727740860,"1024"]]}}
                      }
                    }""".trimIndent(),
                ),
            )
            val cloudToken = "project-cloud-token"
            val storageBoxToken = "storage-box-token"
            val repository = RetrofitServerMetricsRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = {
                        HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString())
                    },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )
            val metrics = repository.getServerMetrics(
                ServerMetricsQuery(
                    serverId = 42,
                    types = listOf(ServerMetricType.CPU, ServerMetricType.NETWORK),
                    start = Instant.parse("2026-10-01T00:00:00Z"),
                    end = Instant.parse("2026-10-01T01:00:00Z"),
                    step = Duration.ofSeconds(60),
                ),
            )

            assertEquals(60.0, metrics.stepSeconds, 0.0)
            assertEquals("42.25", metrics.timeSeries.getValue("cpu").samples.single().value)
            assertEquals(1727740800.5, metrics.timeSeries.getValue("cpu").samples.single().timestampSeconds, 0.0)
            val request = requireNotNull(server.takeRequest())
            assertEquals("/v1/servers/42/metrics", request.requestUrl?.encodedPath)
            assertEquals("cpu,network", request.requestUrl?.queryParameter("type"))
            assertEquals("2026-10-01T00:00:00Z", request.requestUrl?.queryParameter("start"))
            assertEquals("2026-10-01T01:00:00Z", request.requestUrl?.queryParameter("end"))
            assertEquals("60", request.requestUrl?.queryParameter("step"))
            assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
            assertFalse(request.toString().contains(storageBoxToken))
        }
    }
}
