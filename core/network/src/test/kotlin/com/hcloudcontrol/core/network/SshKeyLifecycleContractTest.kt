package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.CreateSshKeySpec
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class SshKeyLifecycleContractTest {
    @Test
    fun `SSH key lifecycle uses documented paths and only serializes public key`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"ssh_key":{"id":77,"name":"laptop","fingerprint":"aa:bb"}}"""))
            server.enqueue(MockResponse().setResponseCode(204))
            val token = "ssh-project-token"
            val repository = RetrofitHCloudRepository(
                HCloudApiFactory.create(
                    tokenProvider = { token },
                    configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val created = repository.createSshKey(CreateSshKeySpec("laptop", "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPublicOnly laptop"))
            repository.deleteSshKey(created.id)

            assertEquals(77L, created.id)
            val create = requireNotNull(server.takeRequest())
            val delete = requireNotNull(server.takeRequest())
            assertEquals("POST", create.method)
            assertEquals("/v1/ssh_keys", create.path)
            assertEquals("{\"name\":\"laptop\",\"public_key\":\"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIPublicOnly laptop\"}", create.body.readUtf8())
            assertEquals("DELETE", delete.method)
            assertEquals("/v1/ssh_keys/77", delete.path)
            listOf(create, delete).forEach { request ->
                assertEquals("Bearer $token", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(token))
            }
        }
    }
}
