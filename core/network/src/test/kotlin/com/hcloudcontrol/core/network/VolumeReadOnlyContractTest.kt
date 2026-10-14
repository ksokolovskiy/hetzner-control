package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class VolumeReadOnlyContractTest {
    @Test
    fun `volume detail and scoped action history use documented paths and paginate`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"volume":{"id":501,"name":"postgres-data","size":50,"location":{"id":1,"name":"fsn1-dc14"},"server":42,"status":"in-use","protection":{"delete":true},"labels":{"environment":"production"}}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"actions":[{"id":1901,"command":"attach_volume","status":"success","started":"2026-10-14T08:00:00Z","finished":"2026-10-14T08:01:00Z","progress":100}],"meta":{"pagination":{"next_page":2}}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"actions":[{"id":1902,"command":"resize_volume","status":"error","started":"2026-10-14T08:02:00Z","finished":"2026-10-14T08:03:00Z","progress":100,"error":{"code":"action_failed","message":"Raw server detail must not reach UI"}}],"meta":{"pagination":{"next_page":null}}}""",
                ),
            )
            val cloudToken = "project-cloud-token"
            val nonCloudToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val volume = repository.getVolume(501)
            val actions = repository.getVolumeActions(501)

            assertEquals("postgres-data", volume.name)
            assertEquals(50, volume.sizeGb)
            assertEquals("fsn1-dc14", volume.location)
            assertEquals(42L, volume.serverId)
            assertEquals(listOf(1901L, 1902L), actions.map { it.id })
            assertEquals("action_failed", actions.last().errorCode)

            val requests = List(3) { requireNotNull(server.takeRequest()) }
            assertEquals("/v1/volumes/501", requests[0].path)
            assertEquals("/v1/volumes/501/actions?page=1&per_page=50", requests[1].path)
            assertEquals("/v1/volumes/501/actions?page=2&per_page=50", requests[2].path)
            requests.forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(nonCloudToken))
            }
        }
    }
}
