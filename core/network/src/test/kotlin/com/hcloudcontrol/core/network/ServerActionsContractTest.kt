package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerActionsContractTest {
    @Test
    fun `server action history paginates on documented resource path with HCloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"actions":[{"id":1301,"command":"poweron","status":"running","started":"2026-10-13T10:00:00Z","finished":null,"progress":50,"resources":[{"id":42,"type":"server"}]}],"meta":{"pagination":{"next_page":2}}}""",
                ),
            )
            server.enqueue(
                MockResponse().setBody(
                    """{"actions":[{"id":1302,"command":"create_image","status":"error","started":"2026-10-13T10:01:00Z","finished":"2026-10-13T10:02:00Z","progress":100,"error":{"code":"action_failed","message":"The API action failed"}}],"meta":{"pagination":{"next_page":null}}}""",
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

            val actions = repository.getServerActions(42)

            assertEquals(listOf(1301L, 1302L), actions.map { it.id })
            assertEquals("poweron", actions.first().command)
            assertEquals(50, actions.first().progress)
            assertEquals("action_failed", actions.last().errorCode)
            val first = requireNotNull(server.takeRequest())
            val second = requireNotNull(server.takeRequest())
            assertEquals("/v1/servers/42/actions?page=1&per_page=50", first.path)
            assertEquals("/v1/servers/42/actions?page=2&per_page=50", second.path)
            listOf(first, second).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(nonCloudToken))
            }
        }
    }
}
