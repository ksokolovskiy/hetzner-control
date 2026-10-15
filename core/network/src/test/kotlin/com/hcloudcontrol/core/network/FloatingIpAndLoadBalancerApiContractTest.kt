package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FloatingIpAndLoadBalancerApiContractTest {
    @Test
    fun `floating IP and load balancer inventory paginate detail and use only selected Cloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"floating_ips":[{"id":601,"name":"failover-v4","type":"ipv4","ip":"192.0.2.15","home_location":{"id":1,"name":"fsn1-dc14"},"server":42,"protection":{"delete":true},"labels":{"environment":"production"},"dns_ptr":[{"ip":"192.0.2.15","dns_ptr":"web.example.test"}]}],"meta":{"pagination":{"next_page":2}}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"floating_ips":[{"id":602,"name":"unassigned-v6","type":"ipv6","ip":"2001:db8::/64","protection":{"delete":false},"labels":{},"dns_ptr":[]}],"meta":{"pagination":{"next_page":null}}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"floating_ip":{"id":601,"name":"failover-v4","type":"ipv4","ip":"192.0.2.15","home_location":{"id":1,"name":"fsn1-dc14"},"server":42,"protection":{"delete":true},"labels":{},"dns_ptr":[]}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"load_balancer":{"id":901,"name":"public-web","load_balancer_type":{"id":1,"name":"lb11","prices":[]},"location":{"id":1,"name":"fsn1-dc14"},"public_net":{"enabled":true,"ipv4":{"ip":"192.0.2.80","dns_ptr":"web.example.test"},"ipv6":null},"private_net":[],"services":[],"targets":[],"protection":{"delete":true},"labels":{"environment":"production"}}}""",
                ),
            )
            val cloudToken = "selected-cloud-token"
            val unrelatedToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val floatingIps = repository.getFloatingIps()
            val floatingIp = repository.getFloatingIp(601)
            val loadBalancer = repository.getLoadBalancer(901)

            assertEquals(listOf(601L, 602L), floatingIps.map { it.id })
            assertEquals("web.example.test", floatingIps.first().dnsPointers.single().dnsPtr)
            assertEquals(42L, floatingIp.assigneeId)
            assertEquals("lb11", loadBalancer.type)
            assertEquals("192.0.2.80", loadBalancer.publicInterface.ipv4Address)

            val requests = List(4) { requireNotNull(server.takeRequest()) }
            assertEquals("/v1/floating_ips?page=1&per_page=50", requests[0].path)
            assertEquals("/v1/floating_ips?page=2&per_page=50", requests[1].path)
            assertEquals("/v1/floating_ips/601", requests[2].path)
            assertEquals("/v1/load_balancers/901", requests[3].path)
            requests.forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(unrelatedToken))
            }
        }
    }
}
