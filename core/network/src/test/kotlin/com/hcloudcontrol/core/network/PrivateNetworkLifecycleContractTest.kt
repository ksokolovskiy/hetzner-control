package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.CreatePrivateNetworkSpec
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.PrivateNetworkRouteSpec
import com.hcloudcontrol.core.domain.model.PrivateNetworkSubnetSpec
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

/** Locks lifecycle operations to the public Cloud API contract; no live Cloud calls are made. */
class PrivateNetworkLifecycleContractTest {
    @Test
    fun `network lifecycle uses documented actions and explicit payloads`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody(networkResponse(801, "private-a")))
            repeat(4) { index ->
                server.enqueue(MockResponse().setBody(action(802 + index, "network_action")))
            }
            server.enqueue(MockResponse().setResponseCode(204))
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { "test-token" },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val created = repository.createPrivateNetwork(CreatePrivateNetworkSpec("private-a", "10.42.0.0/16", mapOf("env" to "test")))
            org.junit.Assert.assertNull(created.action)
            repository.addPrivateNetworkSubnet(created.network.id, PrivateNetworkSubnetSpec("10.42.1.0/24", "eu-central"))
            repository.deletePrivateNetworkSubnet(created.network.id, PrivateNetworkSubnetSpec("10.42.1.0/24", "eu-central"))
            repository.addPrivateNetworkRoute(created.network.id, PrivateNetworkRouteSpec("10.60.0.0/16", "10.42.1.254"))
            repository.deletePrivateNetworkRoute(created.network.id, PrivateNetworkRouteSpec("10.60.0.0/16", "10.42.1.254"))
            org.junit.Assert.assertNull(repository.deletePrivateNetwork(created.network.id))

            val requests = List(6) { requireNotNull(server.takeRequest()) }
            assertEquals("POST", requests[0].method)
            assertEquals("/v1/networks", requests[0].path)
            assertEquals("{\"name\":\"private-a\",\"ip_range\":\"10.42.0.0/16\",\"labels\":{\"env\":\"test\"}}", requests[0].body.readUtf8())
            assertEquals("/v1/networks/801/actions/add_subnet", requests[1].path)
            assertEquals("/v1/networks/801/actions/delete_subnet", requests[2].path)
            assertEquals("{\"type\":\"cloud\",\"ip_range\":\"10.42.1.0/24\",\"network_zone\":\"eu-central\"}", requests[1].body.readUtf8())
            assertEquals("{\"ip_range\":\"10.42.1.0/24\"}", requests[2].body.readUtf8())
            assertEquals("/v1/networks/801/actions/add_route", requests[3].path)
            assertEquals("/v1/networks/801/actions/delete_route", requests[4].path)
            assertEquals("{\"destination\":\"10.60.0.0/16\",\"gateway\":\"10.42.1.254\"}", requests[3].body.readUtf8())
            assertEquals("DELETE", requests[5].method)
            assertEquals("/v1/networks/801", requests[5].path)
        }
    }

    private fun action(id: Int, command: String) = """{"action":{"id":$id,"command":"$command","status":"running"}}"""
    private fun networkResponse(id: Int, name: String) =
        """{"network":{"id":$id,"name":"$name","ip_range":"10.42.0.0/16"}}"""
}
