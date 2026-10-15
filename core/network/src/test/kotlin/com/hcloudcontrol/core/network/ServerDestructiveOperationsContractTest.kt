package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.ServerRebuildSpec
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerDestructiveOperationsContractTest {
    private fun repository(server: MockWebServer) = RetrofitHCloudRepository(HCloudApiFactory.create(
        tokenProvider = { "synthetic" },
        configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
        diagnosticsLogger = HCloudApiDiagnosticsLogger {},
    ))
    @Test fun `rebuild nullable password preserves original action without failure or replay`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"action":{"id":901,"status":"success"},"root_password":null}"""))
            val result = repository(server).rebuildServer(42, ServerRebuildSpec("841"))
            assertEquals(901L, result.action.id)
            assertEquals("success", result.action.status)
            org.junit.Assert.assertNull(result.password)
            assertEquals("{\"image\":\"841\"}", server.takeRequest().body.readUtf8())
            assertEquals(1, server.requestCount)
        }
    }
    @Test fun `rebuild unsupported SSH selection is rejected before any HTTP request`() = runBlocking {
        MockWebServer().use { server ->
            val result = runCatching { repository(server).rebuildServer(42, ServerRebuildSpec("841", listOf(7))) }
            org.junit.Assert.assertTrue(result.exceptionOrNull() is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }
    }
    @Test fun `delete and rebuild use documented endpoints and explicit rebuild image`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"action":{"id":900,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":901,"status":"running"},"root_password":"one-time"}"""))
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create(
                tokenProvider = { "test-token" },
                configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                diagnosticsLogger = HCloudApiDiagnosticsLogger { },
            ))

            val deletion = repository.deleteServerWithAction(42)
            val result = repository.rebuildServer(42, ServerRebuildSpec(image = "841"))

            val delete = requireNotNull(server.takeRequest())
            val rebuild = requireNotNull(server.takeRequest())
            assertEquals("DELETE", delete.method)
            assertEquals("/v1/servers/42", delete.path)
            assertEquals(900L, deletion.id)
            assertEquals("running", deletion.status)
            assertEquals("POST", rebuild.method)
            assertEquals("/v1/servers/42/actions/rebuild", rebuild.path)
            assertEquals("{\"image\":\"841\"}", rebuild.body.readUtf8())
            assertEquals(901L, result.action.id)
            assertEquals("one-time", result.password?.revealOnce())
        }
    }
}
