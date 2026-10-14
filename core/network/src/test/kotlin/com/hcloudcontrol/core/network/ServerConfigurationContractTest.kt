package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.model.ServerConfigurationPatch
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerConfigurationContractTest {
    @Test
    fun `partial server PUT omits untouched values and uses only HCloud credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody(serverResponse(name = "renamed", labels = "{\"existing\":\"kept\"}", placementGroup = "null")))
            server.enqueue(MockResponse().setBody(serverResponse(name = "renamed", labels = "{}", placementGroup = "{\"id\":77}")))
            val cloudToken = "project-cloud-token"
            val otherToken = "storage-box-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { cloudToken },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val renamed = repository.updateServerConfiguration(42, ServerConfigurationPatch(name = "renamed"))
            val configured = repository.updateServerConfiguration(
                42,
                ServerConfigurationPatch(labels = emptyMap(), placementGroupId = 77),
            )

            assertEquals("renamed", renamed.name)
            assertEquals(mapOf("existing" to "kept"), renamed.labels)
            assertEquals(77L, configured.placementGroupId)
            val nameRequest = requireNotNull(server.takeRequest())
            val configurationRequest = requireNotNull(server.takeRequest())
            val nameBody = nameRequest.body.readUtf8()
            val configurationBody = configurationRequest.body.readUtf8()
            assertEquals("PUT", nameRequest.method)
            assertEquals("/v1/servers/42", nameRequest.path)
            assertEquals("{\"name\":\"renamed\"}", nameBody)
            assertFalse(nameBody.contains("labels"))
            assertEquals("Bearer $cloudToken", nameRequest.getHeader("Authorization"))
            assertEquals("PUT", configurationRequest.method)
            assertEquals("{\"labels\":{},\"placement_group\":77}", configurationBody)
            assertEquals("Bearer $cloudToken", configurationRequest.getHeader("Authorization"))
            assertFalse(configurationRequest.toString().contains(otherToken))
        }
    }

    private fun serverResponse(name: String, labels: String, placementGroup: String) =
        """{"server":{"id":42,"name":"$name","status":"running","public_net":{"ipv4":{"ip":"192.0.2.42"}},"location":{"id":1,"name":"fsn1-dc14"},"server_type":{"id":1,"name":"cpx21"},"labels":$labels,"placement_group":$placementGroup}}"""
}
