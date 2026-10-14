package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.IsoSelection
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class IsoApiContractTest {
    @Test
    fun `ISO list detail and server actions use HCloud host credential and documented routes`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"isos":[{"id":100,"name":"debian-installer","description":"Debian","type":"public","architecture":"x86"}],"meta":{"pagination":{"next_page":2}}}"""))
            server.enqueue(MockResponse().setBody("""{"isos":[{"id":101,"name":"rescue","description":"Rescue","type":"private","architecture":"x86"}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"iso":{"id":101,"name":"rescue","description":"Rescue","type":"private","architecture":"x86"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":811,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":812,"status":"running"}}"""))
            val cloudToken = "project-cloud-token"
            val nonCloudToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            assertEquals(listOf(100L, 101L), repository.getIsos().map { it.id })
            assertEquals("rescue", repository.getIso(101).name)
            assertEquals(811L, repository.attachIso(42, IsoSelection(101, "rescue")).id)
            assertEquals(812L, repository.detachIso(42).id)

            val listFirst = requireNotNull(server.takeRequest())
            val listSecond = requireNotNull(server.takeRequest())
            val detail = requireNotNull(server.takeRequest())
            val attach = requireNotNull(server.takeRequest())
            val detach = requireNotNull(server.takeRequest())
            assertEquals("/v1/isos?page=1&per_page=50", listFirst.path)
            assertEquals("/v1/isos?page=2&per_page=50", listSecond.path)
            assertEquals("/v1/isos/101", detail.path)
            assertEquals("POST", attach.method)
            assertEquals("/v1/servers/42/actions/attach_iso", attach.path)
            assertEquals("{\"iso\":101}", attach.body.readUtf8())
            assertEquals("POST", detach.method)
            assertEquals("/v1/servers/42/actions/detach_iso", detach.path)
            listOf(listFirst, listSecond, detail, attach, detach).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(nonCloudToken))
            }
        }
    }
}
