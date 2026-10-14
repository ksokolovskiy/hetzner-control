package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerDnsPtrContractTest {
    @Test
    fun `server PTR action serializes dns ptr including explicit null using HCloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"action":{"id":831,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":832,"status":"running"}}"""))
            val cloudToken = "project-cloud-token"
            val nonCloudToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            assertEquals(831L, repository.changeServerReverseDns(42, "192.0.2.42", null).id)
            assertEquals(832L, repository.changeServerReverseDns(42, "2001:db8::1", "host.example.test").id)

            val clear = requireNotNull(server.takeRequest())
            val set = requireNotNull(server.takeRequest())
            assertEquals("POST", clear.method)
            assertEquals("/v1/servers/42/actions/change_dns_ptr", clear.path)
            assertEquals("{\"ip\":\"192.0.2.42\",\"dns_ptr\":null}", clear.body.readUtf8())
            assertEquals("{\"ip\":\"2001:db8::1\",\"dns_ptr\":\"host.example.test\"}", set.body.readUtf8())
            listOf(clear, set).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(nonCloudToken))
            }
        }
    }
}
