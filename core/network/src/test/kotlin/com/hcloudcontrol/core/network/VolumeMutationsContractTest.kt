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
    @Test fun `missing required next actions cannot silently complete a partial volume response`() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(MockResponse().setResponseCode(201).setBody("""{"volume":{"id":501,"name":"synthetic","size":10,"status":"available","location":{"id":1,"name":"nbg1"}},"action":{"id":901,"status":"success"}}"""))
            org.junit.Assert.assertTrue(runCatching { repository(mock).createVolume(CreateVolumeSpec("synthetic", 10, "nbg1")) }.isFailure)
            assertEquals(1, mock.requestCount)
        }
    }
    private fun repository(server: MockWebServer) = RetrofitHCloudRepository(HCloudApiFactory.create(
        tokenProvider = { "synthetic" },
        configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
        diagnosticsLogger = HCloudApiDiagnosticsLogger {},
    ))
    @Test fun `unexpected successful delete response remains uncertain and is not replayed`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(200))
            org.junit.Assert.assertTrue(runCatching { repository(server).deleteVolume(501) }.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals("DELETE", server.takeRequest().method)
        }
    }
    @Test fun `missing required volume creation action cannot fabricate synchronous success`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"volume":{"id":501,"name":"synthetic","size":10,"status":"available"},"next_actions":[]}"""))
            org.junit.Assert.assertTrue(runCatching { repository(server).createVolume(CreateVolumeSpec("synthetic", 10, "nbg1")) }.isFailure)
            assertEquals(1, server.requestCount)
            assertEquals("POST", server.takeRequest().method)
        }
    }
    @Test
    fun `create and delete volume use official paths payload and token without leaking it`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"volume":{"id":501,"name":"postgres-data","size":50,"location":{"id":1,"name":"fsn1-dc14"},"status":"creating"},"action":{"id":901,"command":"create_volume","status":"running"},"next_actions":[{"id":903,"command":"attach_volume","status":"running"}]}""",
                ),
            )
            server.enqueue(MockResponse().setResponseCode(204))
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
            assertEquals(901L, created.action.id)
            assertEquals(listOf(903L), created.nextActions.map { it.id })
            org.junit.Assert.assertNull(deleted)
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
