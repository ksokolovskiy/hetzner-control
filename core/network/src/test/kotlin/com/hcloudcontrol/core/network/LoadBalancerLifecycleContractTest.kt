package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** Locks deletion to the documented asynchronous Cloud API contract; no Cloud request is made. */
class LoadBalancerLifecycleContractTest {
    @Test
    fun `delete load balancer uses documented asynchronous action endpoint`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setResponseCode(201).setBody(
                    """{"action":{"id":901,"command":"delete_load_balancer","status":"running"}}""",
                ),
            )
            val token = "load-balancer-project-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { token },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val action = repository.deleteLoadBalancer(701)

            assertEquals(901L, action.id)
            assertEquals("delete_load_balancer", action.command)
            val request = requireNotNull(server.takeRequest())
            assertEquals("DELETE", request.method)
            assertEquals("/v1/load_balancers/701", request.path)
            assertEquals("Bearer $token", request.getHeader("Authorization"))
            assertFalse(request.toString().contains(token))
        }
    }
}
