package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class NetworkReadOnlyContractTest {
    @Test
    fun `network detail and members use documented read-only endpoints`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"network":{"id":801,"name":"private","ip_range":"10.42.0.0/16","subnets":[{"type":"cloud","ip_range":"10.42.1.0/24","network_zone":"eu-central","gateway":"10.42.1.1"}],"routes":[],"servers":[42],"protection":{"delete":true}}}"""))
            server.enqueue(MockResponse().setBody("""{"members":[{"type":"server","id":42,"ip":"10.42.1.2","alias_ips":["10.42.1.3"],"subnet":"10.42.1.0/24","status":"ok"}],"meta":{"pagination":{"next_page":null}}}"""))
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create(
                tokenProvider = { "project-token" },
                configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                diagnosticsLogger = HCloudApiDiagnosticsLogger { },
            ))

            val network = repository.getPrivateNetwork(801)
            val members = repository.getPrivateNetworkMembers(801)

            assertEquals("10.42.0.0/16", network.ipRange)
            assertEquals("cloud", network.subnets.single().type)
            assertEquals("10.42.1.2", members.single().ip)
            assertEquals(listOf("10.42.1.3"), members.single().aliasIps)
            assertEquals("/v1/networks/801", requireNotNull(server.takeRequest()).path)
            assertEquals("/v1/networks/801/members?page=1&per_page=50", requireNotNull(server.takeRequest()).path)
        }
    }
}
