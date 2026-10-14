package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerProtectionContractTest {
    @Test
    fun `server protection patch sends only supplied flags with HCloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"action":{"id":841,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":842,"status":"running"}}"""))
            val cloudToken = "project-cloud-token"
            val nonCloudToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            assertEquals(841L, repository.changeServerProtection(42, ServerProtectionPatch(delete = true)).id)
            assertEquals(842L, repository.changeServerProtection(42, ServerProtectionPatch(rebuild = false)).id)

            val deletePatch = requireNotNull(server.takeRequest())
            val rebuildPatch = requireNotNull(server.takeRequest())
            assertEquals("POST", deletePatch.method)
            assertEquals("/v1/servers/42/actions/change_protection", deletePatch.path)
            assertEquals("{\"delete\":true}", deletePatch.body.readUtf8())
            assertEquals("{\"rebuild\":false}", rebuildPatch.body.readUtf8())
            listOf(deletePatch, rebuildPatch).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(nonCloudToken))
            }
        }
    }
}
