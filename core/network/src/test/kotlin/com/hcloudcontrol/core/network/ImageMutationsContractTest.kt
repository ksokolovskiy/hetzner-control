package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.ImageProtectionPatch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImageMutationsContractTest {
    @Test
    fun `image delete and protection use documented paths explicit payload and selected credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(204))
            server.enqueue(MockResponse().setBody("""{"action":{"id":702,"command":"change_protection","status":"running"}}"""))
            val token = "image-project-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { token },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            repository.deleteImage(70)
            assertEquals(702L, repository.changeImageProtection(70, ImageProtectionPatch(delete = true)).id)

            val delete = requireNotNull(server.takeRequest())
            val protection = requireNotNull(server.takeRequest())
            assertEquals("DELETE", delete.method)
            assertEquals("/v1/images/70", delete.path)
            assertEquals("POST", protection.method)
            assertEquals("/v1/images/70/actions/change_protection", protection.path)
            assertEquals("{\"delete\":true}", protection.body.readUtf8())
            listOf(delete, protection).forEach { request ->
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(token))
            }
        }
    }
}
