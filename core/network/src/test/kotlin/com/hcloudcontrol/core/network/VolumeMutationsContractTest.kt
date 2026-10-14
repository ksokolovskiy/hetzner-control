package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.CreateVolumeSpec
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VolumeMutationsContractTest {
    @Test
    fun `create and delete volume use official paths payload and token without leaking it`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"volume":{"id":501,"name":"postgres-data","size":50,"location":{"id":1,"name":"fsn1-dc14"},"status":"creating"},"action":{"id":901,"command":"create_volume","status":"running"}}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"action":{"id":902,"command":"delete_volume","status":"running"}}"""))
            val token = "volume-project-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { token },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val created = repository.createVolume(CreateVolumeSpec("postgres-data", 50, "fsn1-dc14"))
            val deleted = repository.deleteVolume(created.volume.id)

            assertEquals(501L, created.volume.id)
            assertEquals(901L, created.action?.id)
            assertEquals(902L, deleted.id)
            val create = requireNotNull(server.takeRequest())
            val delete = requireNotNull(server.takeRequest())
            assertEquals("POST", create.method)
            assertEquals("/v1/volumes", create.path)
            assertEquals("{\"name\":\"postgres-data\",\"size\":50,\"location\":\"fsn1-dc14\",\"labels\":{}}", create.body.readUtf8())
            assertEquals("DELETE", delete.method)
            assertEquals("/v1/volumes/501", delete.path)
            listOf(create, delete).forEach { request ->
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(token))
            }
        }
    }
}
