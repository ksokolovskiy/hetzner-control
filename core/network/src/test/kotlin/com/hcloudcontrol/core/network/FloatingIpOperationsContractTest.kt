package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingIpOperationsContractTest {
    @Test
    fun `floating IP operations use official actions and explicitly serialize PTR clear with selected Cloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"action":{"id":1011,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":1012,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":1013,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":1014,"status":"running"}}"""))
            val cloudToken = "selected-cloud-token"
            val unrelatedToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            assertEquals(1011L, repository.assignFloatingIp(601, 42).id)
            assertEquals(1012L, repository.unassignFloatingIp(601).id)
            assertEquals(1013L, repository.changeFloatingIpReverseDns(601, "192.0.2.15", null).id)
            assertEquals(1014L, repository.changeFloatingIpReverseDns(601, "2001:db8::1", "host.example.test").id)

            val assign = requireNotNull(server.takeRequest())
            val unassign = requireNotNull(server.takeRequest())
            val clear = requireNotNull(server.takeRequest())
            val set = requireNotNull(server.takeRequest())
            assertEquals("POST", assign.method)
            assertEquals("/v1/floating_ips/601/actions/assign", assign.path)
            assertEquals("{\"server\":42}", assign.body.readUtf8())
            assertEquals("POST", unassign.method)
            assertEquals("/v1/floating_ips/601/actions/unassign", unassign.path)
            assertEquals("POST", clear.method)
            assertEquals("/v1/floating_ips/601/actions/change_dns_ptr", clear.path)
            assertEquals("{\"ip\":\"192.0.2.15\",\"dns_ptr\":null}", clear.body.readUtf8())
            assertEquals("{\"ip\":\"2001:db8::1\",\"dns_ptr\":\"host.example.test\"}", set.body.readUtf8())
            listOf(assign, unassign, clear, set).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(unrelatedToken))
            }
        }
    }

    @Test
    fun `delete unassigned floating IP uses official no-body delete route and selected Cloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            val cloudToken = "selected-cloud-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            repository.deleteFloatingIp(601)

            val request = requireNotNull(server.takeRequest())
            assertEquals("DELETE", request.method)
            assertEquals("/v1/floating_ips/601", request.path)
            assertEquals(0L, request.body.size)
            assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
            assertTrue(request.body.readUtf8().isEmpty())
        }
    }
}
