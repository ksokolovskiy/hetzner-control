package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.CreatePlacementGroupSpec
import com.hcloudcontrol.core.domain.model.PlacementGroupPatch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class PlacementGroupApiContractTest {
    @Test
    fun `placement group lifecycle uses documented cloud routes and exact reviewed bodies`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(201).setBody("""{"placement_group":{"id":1001,"name":"web-spread","type":"spread","servers":[],"labels":{"environment":"production"}},"action":{"id":820,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"placement_group":{"id":1001,"name":"web-spread-v2","type":"spread","servers":[],"labels":{}}}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val token = "project-cloud-token"
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create(
                tokenProvider = { token },
                configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                diagnosticsLogger = HCloudApiDiagnosticsLogger { },
            ))

            val created = repository.createPlacementGroup(CreatePlacementGroupSpec("web-spread", labels = mapOf("environment" to "production")))
            assertEquals(1001L, created.placementGroup.id)
            assertEquals(820L, created.action?.id)
            assertEquals("web-spread-v2", repository.updatePlacementGroup(1001, PlacementGroupPatch(name = "web-spread-v2", labels = emptyMap())).name)
            repository.deletePlacementGroup(1001)

            val create = requireNotNull(server.takeRequest())
            val update = requireNotNull(server.takeRequest())
            val delete = requireNotNull(server.takeRequest())
            assertEquals("POST", create.method)
            assertEquals("/v1/placement_groups", create.path)
            assertEquals("{\"name\":\"web-spread\",\"type\":\"spread\",\"labels\":{\"environment\":\"production\"}}", create.body.readUtf8())
            assertEquals("PUT", update.method)
            assertEquals("/v1/placement_groups/1001", update.path)
            assertEquals("{\"name\":\"web-spread-v2\",\"labels\":{}}", update.body.readUtf8())
            assertEquals("DELETE", delete.method)
            assertEquals("/v1/placement_groups/1001", delete.path)
            listOf(create, update, delete).forEach { assertEquals("Bearer $token", it.getHeader("Authorization")) }
        }
    }

    @Test
    fun `placement group detail and membership actions use exact routes bodies and HCloud token`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"placement_group":{"id":1001,"name":"web-spread","type":"spread","servers":[42,43],"labels":{"environment":"production"}}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":821,"status":"running"}}"""))
            server.enqueue(MockResponse().setBody("""{"action":{"id":822,"status":"running"}}"""))
            val cloudToken = "project-cloud-token"
            val nonCloudToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            assertEquals("web-spread", repository.getPlacementGroup(1001).name)
            assertEquals(821L, repository.addServerToPlacementGroup(42, 1001).id)
            assertEquals(822L, repository.removeServerFromPlacementGroup(42, 1001).id)

            val detail = requireNotNull(server.takeRequest())
            val add = requireNotNull(server.takeRequest())
            val remove = requireNotNull(server.takeRequest())
            assertEquals("/v1/placement_groups/1001", detail.path)
            assertEquals("POST", add.method)
            assertEquals("/v1/servers/42/actions/add_to_placement_group", add.path)
            assertEquals("{\"placement_group\":1001}", add.body.readUtf8())
            assertEquals("POST", remove.method)
            assertEquals("/v1/servers/42/actions/remove_from_placement_group", remove.path)
            assertEquals("{\"placement_group\":1001}", remove.body.readUtf8())
            listOf(detail, add, remove).forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(nonCloudToken))
            }
        }
    }
}
