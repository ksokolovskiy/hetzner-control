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
    @Test fun `delete and rebuild use documented endpoints and explicit rebuild image`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setBody("""{"action":{"id":901,"status":"running"},"root_password":"one-time"}"""))
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create(
                tokenProvider = { "test-token" },
                configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                diagnosticsLogger = HCloudApiDiagnosticsLogger { },
            ))

            repository.deleteServer(42)
            val result = repository.rebuildServer(42, ServerRebuildSpec(image = "841", sshKeyIds = listOf(7)))

            val delete = requireNotNull(server.takeRequest())
            val rebuild = requireNotNull(server.takeRequest())
            assertEquals("DELETE", delete.method)
            assertEquals("/v1/servers/42", delete.path)
            assertEquals("POST", rebuild.method)
            assertEquals("/v1/servers/42/actions/rebuild", rebuild.path)
            assertEquals("{\"image\":\"841\",\"ssh_keys\":[7]}", rebuild.body.readUtf8())
            assertEquals(901L, result.action.id)
            assertEquals("one-time", result.password?.revealOnce())
        }
    }
}
