package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.*
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ProtectionPresenceContractTest {
    private fun repository(mock: MockWebServer) = RetrofitHCloudRepository(HCloudApiFactory.create(
        tokenProvider = { "synthetic-only" },
        configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, mock.url("/v1").toString()) },
        diagnosticsLogger = HCloudApiDiagnosticsLogger {},
    ))
    @Test fun `missing or incomplete protection never becomes known disabled across resource mappings`() = runBlocking {
        for ((field, known) in listOf("" to false, ",\"protection\":{}" to false, ",\"protection\":{\"delete\":false}" to true)) {
            MockWebServer().use { mock ->
                val location = "\"location\":{\"id\":1,\"name\":\"nbg1\"}"
                listOf(
                    """{"volume":{"id":42,"name":"synthetic","size":10,"status":"available",$location$field}}""",
                    """{"network":{"id":42,"name":"synthetic","ip_range":"10.0.0.0/16"$field}}""",
                    """{"primary_ip":{"id":42,"name":"synthetic","type":"ipv4","ip":"192.0.2.1",$location$field}}""",
                    """{"floating_ip":{"id":42,"name":"synthetic","type":"ipv4","ip":"192.0.2.1","server":null$field}}""",
                    """{"load_balancer":{"id":42,"name":"synthetic","load_balancer_type":{"id":1,"name":"lb11"},$location$field}}""",
                ).forEach { mock.enqueue(MockResponse().setBody(it)) }
                val repo = repository(mock)
                assertEquals(known, repo.getVolume(42).protectionKnown)
                assertEquals(known, repo.getPrivateNetwork(42).protectionKnown)
                assertEquals(known, repo.getPrimaryIp(42).protectionKnown)
                assertEquals(known, repo.getFloatingIp(42).protectionKnown)
                assertEquals(known, repo.getLoadBalancer(42).protectionKnown)
            }
        }
    }
    @Test fun `server partial protection flags remain unavailable rather than fabricated false`() = runBlocking {
        MockWebServer().use { mock ->
            for (flags in listOf("{}", "{\"delete\":false}")) mock.enqueue(MockResponse().setBody(
                """{"server":{"id":42,"name":"synthetic","status":"off","public_net":{"ipv4":null},"server_type":{"id":1,"name":"synthetic"},"protection":$flags}}"""))
            val repo = repository(mock)
            assertNull(repo.getServer(42).protection)
            assertNull(repo.getServer(42).protection)
        }
    }
    @Test fun `floating IP assignment uses official server field not Primary IP assignee fields`() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(MockResponse().setBody("""{"floating_ip":{"id":42,"type":"ipv4","ip":"192.0.2.1","server":88,"assignee_id":999,"assignee_type":"unsupported"}}"""))
            val ip = repository(mock).getFloatingIp(42)
            assertEquals(88L, ip.assigneeId)
            assertEquals("server", ip.assigneeType)
        }
    }
}
