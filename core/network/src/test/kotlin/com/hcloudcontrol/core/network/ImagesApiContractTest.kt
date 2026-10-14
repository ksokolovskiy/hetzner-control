package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ImagesApiContractTest {
    @Test
    fun `image inventory paginates and detail uses only the selected HCloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(
                MockResponse().setBody(
                    """{"images":[{"id":90,"name":"ubuntu-24.04","description":"Ubuntu","type":"system","status":"available","image_size":12.5,"disk_size":25.0,"created":"2026-10-01T00:00:00+00:00","protection":{"delete":true},"deprecated":null,"deprecation":{"announced":"2026-10-10T00:00:00+00:00","unavailable_after":"2026-12-01T00:00:00+00:00"}}],"meta":{"pagination":{"next_page":2}}}""",
                ),
            )
            server.enqueue(MockResponse().setBody("""{"images":[{"id":91,"type":"snapshot","status":"available"}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"image":{"id":91,"name":"before-upgrade","type":"snapshot","status":"available","image_size":1.0,"disk_size":2.0,"protection":{"delete":false}}}"""))
            val cloudToken = "project-cloud-token"
            val otherToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val images = repository.getImages()
            val detail = repository.getImage(91)

            assertEquals(listOf(90L, 91L), images.map { it.id })
            assertEquals(12.5, images.first().imageSize ?: 0.0, 0.0)
            assertEquals("2026-12-01T00:00:00+00:00", images.first().deprecation?.unavailableAfter)
            assertEquals(false, detail.deleteProtected)
            val first = requireNotNull(server.takeRequest())
            val second = requireNotNull(server.takeRequest())
            val third = requireNotNull(server.takeRequest())
            assertEquals("/v1/images?page=1&per_page=50", first.path)
            assertEquals("/v1/images?page=2&per_page=50", second.path)
            assertEquals("/v1/images/91", third.path)
            listOf(first, second, third).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(otherToken))
            }
        }
    }
}
