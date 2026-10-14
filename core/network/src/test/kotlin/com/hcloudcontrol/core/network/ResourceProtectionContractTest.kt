package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.DeleteProtectionPatch
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ResourceProtectionContractTest {
    @Test
    fun `non-server deletion protection uses documented actions and project credential`() = runBlocking {
        MockWebServer().use { server ->
            repeat(5) { index ->
                server.enqueue(MockResponse().setBody("""{"action":{"id":${900 + index},"status":"running"}}"""))
            }
            val token = "project-token-used-only-here"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { token },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            repository.changeVolumeProtection(11, DeleteProtectionPatch(true))
            repository.changePrimaryIpProtection(12, DeleteProtectionPatch(false))
            repository.changeFloatingIpProtection(13, DeleteProtectionPatch(true))
            repository.changePrivateNetworkProtection(14, DeleteProtectionPatch(false))
            repository.changeLoadBalancerProtection(15, DeleteProtectionPatch(true))

            val expectedPaths = listOf(
                "/v1/volumes/11/actions/change_protection",
                "/v1/primary_ips/12/actions/change_protection",
                "/v1/floating_ips/13/actions/change_protection",
                "/v1/networks/14/actions/change_protection",
                "/v1/load_balancers/15/actions/change_protection",
            )
            expectedPaths.forEachIndexed { index, path ->
                val request = requireNotNull(server.takeRequest())
                assertEquals("POST", request.method)
                assertEquals(path, request.path)
                assertEquals(if (index == 1 || index == 3) "{\"delete\":false}" else "{\"delete\":true}", request.body.readUtf8())
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(token))
            }
        }
    }
}
