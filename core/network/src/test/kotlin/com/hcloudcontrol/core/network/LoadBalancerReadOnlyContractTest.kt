package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricType
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricsQuery
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class LoadBalancerReadOnlyContractTest {
    @Test
    fun `action history and metrics use documented GET paths pagination and selected Cloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"actions":[{"id":1401,"command":"change_load_balancer_type","status":"running","progress":30}],"meta":{"pagination":{"next_page":2}}}"""))
            server.enqueue(MockResponse().setBody("""{"actions":[{"id":1402,"command":"add_target","status":"error","error":{"code":"action_failed","message":"Target health check failed"}}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(
                MockResponse().setBody(
                    """{"metrics":{"start":"2026-10-01T00:00:00Z","end":"2026-10-01T01:00:00Z","step":60,"time_series":{"requests_per_second":{"values":[[1727740800.5,"42"]]},"bandwidth.in":{"values":[[1727740860,"1024"]]}}}}""",
                ),
            )
            val cloudToken = "project-cloud-token"
            val storageBoxToken = "storage-box-token"
            val api = HCloudApiFactory.create(
                tokenProvider = { cloudToken },
                configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                diagnosticsLogger = HCloudApiDiagnosticsLogger { },
            )
            val actions = RetrofitHCloudRepository(api).getLoadBalancerActions(901)
            val metrics = RetrofitLoadBalancerMetricsRepository(api).getLoadBalancerMetrics(
                LoadBalancerMetricsQuery(
                    loadBalancerId = 901,
                    types = listOf(LoadBalancerMetricType.REQUESTS_PER_SECOND, LoadBalancerMetricType.BANDWIDTH),
                    start = Instant.parse("2026-10-01T00:00:00Z"),
                    end = Instant.parse("2026-10-01T01:00:00Z"),
                    step = Duration.ofSeconds(60),
                ),
            )

            assertEquals(listOf(1401L, 1402L), actions.map { it.id })
            assertEquals("action_failed", actions.last().errorCode)
            assertEquals("42", metrics.timeSeries.getValue("requests_per_second").samples.single().value)
            assertEquals(1727740860.0, metrics.timeSeries.getValue("bandwidth.in").samples.single().timestampSeconds, 0.0)
            val first = requireNotNull(server.takeRequest())
            val second = requireNotNull(server.takeRequest())
            val third = requireNotNull(server.takeRequest())
            assertEquals("/v1/load_balancers/901/actions?page=1&per_page=50", first.path)
            assertEquals("/v1/load_balancers/901/actions?page=2&per_page=50", second.path)
            assertEquals("/v1/load_balancers/901/metrics", third.requestUrl?.encodedPath)
            assertEquals("requests_per_second,bandwidth", third.requestUrl?.queryParameter("type"))
            assertEquals("2026-10-01T00:00:00Z", third.requestUrl?.queryParameter("start"))
            assertEquals("2026-10-01T01:00:00Z", third.requestUrl?.queryParameter("end"))
            assertEquals("60", third.requestUrl?.queryParameter("step"))
            listOf(first, second, third).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(storageBoxToken))
            }
        }
    }
}
