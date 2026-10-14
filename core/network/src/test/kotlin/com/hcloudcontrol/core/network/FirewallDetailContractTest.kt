package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.CreateFirewallSpec
import com.hcloudcontrol.core.domain.model.FirewallRuleSpec
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FirewallDetailContractTest {
    @Test
    fun `firewall create replace rules and delete use official payloads`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"firewall":{"id":44,"name":"edge","rules":[],"applied_to":[],"labels":{"environment":"test"}},"actions":[{"id":601,"status":"running","command":"apply_firewall"}]}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":602,"status":"running","command":"set_firewall_rules"}}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { "project-cloud-token" },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )
            val rule = FirewallRuleSpec("in", "tcp", "443", sourceIps = listOf("0.0.0.0/0"), description = "HTTPS")
            val created = repository.createFirewall(CreateFirewallSpec("edge", listOf(rule), mapOf("environment" to "test")))
            val action = repository.setFirewallRules(44, listOf(rule))
            repository.deleteFirewall(44)

            assertEquals(44L, created.firewall.id)
            assertEquals(601L, created.actions.single().id)
            assertEquals(602L, action.id)
            val requests = List(3) { requireNotNull(server.takeRequest()) }
            assertEquals("POST", requests[0].method)
            assertEquals("/v1/firewalls", requests[0].path)
            assertEquals("""{"name":"edge","rules":[{"direction":"in","protocol":"tcp","port":"443","source_ips":["0.0.0.0/0"],"destination_ips":[],"description":"HTTPS"}],"labels":{"environment":"test"}}""", requests[0].body.readUtf8())
            assertEquals("POST", requests[1].method)
            assertEquals("/v1/firewalls/44/actions/set_rules", requests[1].path)
            assertEquals("""{"rules":[{"direction":"in","protocol":"tcp","port":"443","source_ips":["0.0.0.0/0"],"destination_ips":[],"description":"HTTPS"}]}""", requests[1].body.readUtf8())
            assertEquals("DELETE", requests[2].method)
            assertEquals("/v1/firewalls/44", requests[2].path)
        }
    }

    @Test
    fun `Firewall detail and scoped action history use documented paginated Cloud paths only`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"firewall":{"id":44,"name":"edge","rules":[{"direction":"in","protocol":"tcp","port":"443","source_ips":["0.0.0.0/0"],"destination_ips":[],"description":"https"}],"applied_to":[{"type":"server","server":{"id":42}}],"labels":{"environment":"production"}}}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"actions":[{"id":501,"command":"apply_firewall","status":"running","progress":50}],"meta":{"pagination":{"next_page":2}}}"""))
            server.enqueue(MockResponse().setBody("""{"actions":[{"id":502,"command":"set_rules","status":"error","error":{"code":"action_failed","message":"must not be surfaced"}}],"meta":{"pagination":{"next_page":null}}}"""))
            val cloudToken = "project-cloud-token"
            val storageBoxToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val firewall = repository.getFirewall(44)
            val actions = repository.getFirewallActions(44)

            assertEquals("edge", firewall.name)
            assertEquals("https", firewall.rules.single().description)
            assertEquals(42L, firewall.appliedTo.single().serverId)
            assertEquals(listOf(501L, 502L), actions.map { it.id })
            assertEquals("action_failed", actions.last().errorCode)
            val requests = List(3) { requireNotNull(server.takeRequest()) }
            assertEquals("/v1/firewalls/44", requests[0].path)
            assertEquals("/v1/firewalls/44/actions?page=1&per_page=50", requests[1].path)
            assertEquals("/v1/firewalls/44/actions?page=2&per_page=50", requests[2].path)
            requests.forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(storageBoxToken))
            }
        }
    }
}
