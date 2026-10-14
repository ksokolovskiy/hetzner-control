package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.CreatePrimaryIpSpec
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PrimaryIpLifecycleContractTest {
    @Test fun `create and delete primary IP use documented routes and never leak credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"primary_ip":{"id":601,"name":"edge","type":"ipv4","ip":"192.0.2.1","location":{"id":1,"name":"fsn1-dc14"}}}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val token = "selected-primary-ip-token"
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create({ token }, { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) }, HCloudApiDiagnosticsLogger { }))
            repository.createPrimaryIp(CreatePrimaryIpSpec("ipv4", "edge", "fsn1-dc14", 42, mapOf("team" to "edge")))
            repository.deletePrimaryIp(601)
            val create = requireNotNull(server.takeRequest()); val delete = requireNotNull(server.takeRequest())
            assertEquals("POST", create.method); assertEquals("/v1/primary_ips", create.path)
            assertEquals("{\"type\":\"ipv4\",\"name\":\"edge\",\"location\":\"fsn1-dc14\",\"assignee_type\":\"server\",\"assignee_id\":42,\"labels\":{\"team\":\"edge\"}}", create.body.readUtf8())
            assertEquals("DELETE", delete.method); assertEquals("/v1/primary_ips/601", delete.path); assertEquals(0L, delete.body.size)
            listOf(create, delete).forEach { request -> assertEquals("Bearer $token", request.getHeader("Authorization")); assertFalse(request.toString().contains(token)) }
        }
    }
}
