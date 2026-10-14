package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectionIsolationContractTest {
    @Test
    fun storageBoxUsesTheSelectedProjectsBearerCredential() = runBlocking {
        MockWebServer().use { firstCloud ->
            MockWebServer().use { secondCloud ->
                MockWebServer().use { storageBox ->
                    firstCloud.enqueue(MockResponse().setBody(serversResponse()))
                    secondCloud.enqueue(MockResponse().setBody(serversResponse()))
                    storageBox.enqueue(MockResponse().setBody("""{"storage_boxes":[]}"""))

                    val diagnostics = mutableListOf<HCloudApiDiagnostic>()
                    val firstCloudApi = HCloudApiFactory.create(
                        tokenProvider = { "test-a" },
                        configProvider = {
                            HCloudConnectionConfig(ConnectionKind.REAL, firstCloud.url("/v1").toString())
                        },
                        diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
                    )
                    val secondCloudApi = HCloudApiFactory.create(
                        tokenProvider = { "test-b" },
                        configProvider = {
                            HCloudConnectionConfig(ConnectionKind.REAL, secondCloud.url("/v1").toString())
                        },
                        diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
                    )
                    val storageBoxApi = StorageBoxApiFactory.create(
                        // Storage Box has its own host, but is scoped by the active project token.
                        tokenProvider = { "test-a" },
                        baseUrl = storageBox.url("/v1/").toString(),
                        diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
                    )

                    assertTrue(firstCloudApi.getServers().servers.isEmpty())
                    assertTrue(secondCloudApi.getServers().servers.isEmpty())
                    storageBoxApi.getStorageBoxes()

                    val firstRequest = requireNotNull(firstCloud.takeRequest())
                    val secondRequest = requireNotNull(secondCloud.takeRequest())
                    val storageRequest = requireNotNull(storageBox.takeRequest())
                    assertEquals("Bearer test-a", firstRequest.getHeader("Authorization"))
                    assertEquals("Bearer test-b", secondRequest.getHeader("Authorization"))
                    assertEquals("Bearer test-a", storageRequest.getHeader("Authorization"))
                    assertNoOtherCredential(firstRequest.toString(), "test-b")
                    assertNoOtherCredential(secondRequest.toString(), "test-a")
                    assertNoOtherCredential(storageRequest.toString(), "test-b")
                    assertDiagnosticsAreCredentialFree(
                        diagnostics,
                        "test-a",
                        "test-b",
                    )
                }
            }
        }
    }

    @Test
    fun testMockRouteAndTokenCannotAffectARealCloudClient() = runBlocking {
        MockWebServer().use { realCloud ->
            MockWebServer().use { testMock ->
                realCloud.enqueue(MockResponse().setBody(serversResponse()))
                testMock.enqueue(MockResponse().setBody(serversResponse()))

                val realApi = HCloudApiFactory.create(
                    tokenProvider = { "real-a" },
                    configProvider = {
                        HCloudConnectionConfig(ConnectionKind.REAL, realCloud.url("/v1").toString())
                    },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                )
                val testMockApi = HCloudApiFactory.create(
                    tokenProvider = { "mock-b" },
                    configProvider = {
                        HCloudConnectionConfig(ConnectionKind.TEST_MOCK, testMock.url("/hcloud-mock/v1").toString())
                    },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                )

                assertTrue(realApi.getServers().servers.isEmpty())
                assertTrue(testMockApi.getServers().servers.isEmpty())

                val realRequest = requireNotNull(realCloud.takeRequest())
                val testRequest = requireNotNull(testMock.takeRequest())
                assertEquals("/v1/servers?page=1&per_page=50", realRequest.path)
                assertEquals("/hcloud-mock/v1/servers?page=1&per_page=50", testRequest.path)
                assertEquals("Bearer real-a", realRequest.getHeader("Authorization"))
                assertEquals("Bearer mock-b", testRequest.getHeader("Authorization"))
                assertNoOtherCredential(realRequest.toString(), "mock-b")
                assertNoOtherCredential(testRequest.toString(), "real-a")
            }
        }
    }

    @Test
    fun `one shared Cloud client switches route and credential with the active project`() = runBlocking {
        MockWebServer().use { firstProject ->
            MockWebServer().use { secondProject ->
                firstProject.enqueue(MockResponse().setBody(serversResponse()))
                secondProject.enqueue(MockResponse().setBody(serversResponse()))

                var activeConfig = HCloudConnectionConfig(ConnectionKind.REAL, firstProject.url("/v1").toString())
                var activeToken = "first-a"
                val api = HCloudApiFactory.create(
                    tokenProvider = { activeToken },
                    configProvider = { activeConfig },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                )

                assertTrue(api.getServers().servers.isEmpty())
                activeConfig = HCloudConnectionConfig(ConnectionKind.REAL, secondProject.url("/v1").toString())
                activeToken = "second-b"
                assertTrue(api.getServers().servers.isEmpty())

                val firstRequest = requireNotNull(firstProject.takeRequest())
                val secondRequest = requireNotNull(secondProject.takeRequest())
                assertEquals("Bearer first-a", firstRequest.getHeader("Authorization"))
                assertEquals("Bearer second-b", secondRequest.getHeader("Authorization"))
                assertFalse(firstRequest.toString().contains("second-b"))
                assertFalse(secondRequest.toString().contains("first-a"))
            }
        }
    }

    @Test
    fun `missing active project credential fails before a Storage Box request can be sent`() = runBlocking {
        MockWebServer().use { storageBox ->
            val diagnostics = mutableListOf<HCloudApiDiagnostic>()
            val api = StorageBoxApiFactory.create(
                tokenProvider = { null },
                baseUrl = storageBox.url("/v1/").toString(),
                diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
            )

            val failure = runCatching { api.getStorageBoxes() }.exceptionOrNull()

            assertTrue(failure is MissingStorageBoxApiTokenException)
            assertEquals(0, storageBox.requestCount)
            assertTrue(diagnostics.isEmpty())
        }
    }

    @Test
    fun `Cloud API intentionally exposes no undocumented profile route`() {
        assertFalse(HCloudApi::class.java.methods.any { it.name == "getProfile" })
    }

    private fun serversResponse() = """{"servers":[],"meta":{"pagination":{"next_page":null}}}"""

    private fun assertNoOtherCredential(request: String, vararg excludedCredentials: String) {
        excludedCredentials.forEach { credential ->
            assertFalse("request unexpectedly contains another connection credential", request.contains(credential))
        }
    }

    private fun assertDiagnosticsAreCredentialFree(
        diagnostics: List<HCloudApiDiagnostic>,
        vararg credentials: String,
    ) {
        val rendered = diagnostics.joinToString(separator = "\n")
        credentials.forEach { credential ->
            assertFalse("diagnostics must not contain a credential", rendered.contains(credential))
        }
    }
}
