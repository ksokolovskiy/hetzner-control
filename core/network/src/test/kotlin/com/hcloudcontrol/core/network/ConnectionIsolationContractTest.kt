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
    fun cloudProjectsAndStorageBoxSendOnlyTheirOwnBearerCredentials() = runBlocking {
        MockWebServer().use { firstCloud ->
            MockWebServer().use { secondCloud ->
                MockWebServer().use { storageBox ->
                    firstCloud.enqueue(MockResponse().setBody(profileResponse("first")))
                    secondCloud.enqueue(MockResponse().setBody(profileResponse("second")))
                    storageBox.enqueue(MockResponse().setBody("""{"storage_boxes":[]}"""))

                    val diagnostics = mutableListOf<HCloudApiDiagnostic>()
                    val firstCloudApi = HCloudApiFactory.create(
                        tokenProvider = { "cloud-project-one-token" },
                        configProvider = {
                            HCloudConnectionConfig(ConnectionKind.REAL, firstCloud.url("/v1").toString())
                        },
                        diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
                    )
                    val secondCloudApi = HCloudApiFactory.create(
                        tokenProvider = { "cloud-project-two-token" },
                        configProvider = {
                            HCloudConnectionConfig(ConnectionKind.REAL, secondCloud.url("/v1").toString())
                        },
                        diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
                    )
                    val storageBoxApi = StorageBoxApiFactory.create(
                        tokenProvider = { "storage-box-only-token" },
                        baseUrl = storageBox.url("/v1/").toString(),
                        diagnosticsLogger = HCloudApiDiagnosticsLogger(diagnostics::add),
                    )

                    assertEquals("first", firstCloudApi.getProfile().profile.name)
                    assertEquals("second", secondCloudApi.getProfile().profile.name)
                    storageBoxApi.getStorageBoxes()

                    val firstRequest = requireNotNull(firstCloud.takeRequest())
                    val secondRequest = requireNotNull(secondCloud.takeRequest())
                    val storageRequest = requireNotNull(storageBox.takeRequest())
                    assertEquals("Bearer cloud-project-one-token", firstRequest.getHeader("Authorization"))
                    assertEquals("Bearer cloud-project-two-token", secondRequest.getHeader("Authorization"))
                    assertEquals("Bearer storage-box-only-token", storageRequest.getHeader("Authorization"))
                    assertNoOtherCredential(firstRequest.toString(), "cloud-project-two-token", "storage-box-only-token")
                    assertNoOtherCredential(secondRequest.toString(), "cloud-project-one-token", "storage-box-only-token")
                    assertNoOtherCredential(storageRequest.toString(), "cloud-project-one-token", "cloud-project-two-token")
                    assertDiagnosticsAreCredentialFree(
                        diagnostics,
                        "cloud-project-one-token",
                        "cloud-project-two-token",
                        "storage-box-only-token",
                    )
                }
            }
        }
    }

    @Test
    fun testMockRouteAndTokenCannotAffectARealCloudClient() = runBlocking {
        MockWebServer().use { realCloud ->
            MockWebServer().use { testMock ->
                realCloud.enqueue(MockResponse().setBody(profileResponse("real-project")))
                testMock.enqueue(MockResponse().setBody(profileResponse("test-project")))

                val realApi = HCloudApiFactory.create(
                    tokenProvider = { "real-project-token" },
                    configProvider = {
                        HCloudConnectionConfig(ConnectionKind.REAL, realCloud.url("/v1").toString())
                    },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                )
                val testMockApi = HCloudApiFactory.create(
                    tokenProvider = { "test-mock-token" },
                    configProvider = {
                        HCloudConnectionConfig(ConnectionKind.TEST_MOCK, testMock.url("/hcloud-mock/v1").toString())
                    },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                )

                assertEquals("real-project", realApi.getProfile().profile.name)
                assertEquals("test-project", testMockApi.getProfile().profile.name)

                val realRequest = requireNotNull(realCloud.takeRequest())
                val testRequest = requireNotNull(testMock.takeRequest())
                assertEquals("/v1/profile", realRequest.path)
                assertEquals("/hcloud-mock/v1/profile", testRequest.path)
                assertEquals("Bearer real-project-token", realRequest.getHeader("Authorization"))
                assertEquals("Bearer test-mock-token", testRequest.getHeader("Authorization"))
                assertNoOtherCredential(realRequest.toString(), "test-mock-token")
                assertNoOtherCredential(testRequest.toString(), "real-project-token")
            }
        }
    }

    @Test
    fun `one shared Cloud client switches route and credential with the active project`() = runBlocking {
        MockWebServer().use { firstProject ->
            MockWebServer().use { secondProject ->
                firstProject.enqueue(MockResponse().setBody(profileResponse("first-project")))
                secondProject.enqueue(MockResponse().setBody(profileResponse("second-project")))

                var activeConfig = HCloudConnectionConfig(ConnectionKind.REAL, firstProject.url("/v1").toString())
                var activeToken = "first-project-token"
                val api = HCloudApiFactory.create(
                    tokenProvider = { activeToken },
                    configProvider = { activeConfig },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                )

                assertEquals("first-project", api.getProfile().profile.name)
                activeConfig = HCloudConnectionConfig(ConnectionKind.REAL, secondProject.url("/v1").toString())
                activeToken = "second-project-token"
                assertEquals("second-project", api.getProfile().profile.name)

                val firstRequest = requireNotNull(firstProject.takeRequest())
                val secondRequest = requireNotNull(secondProject.takeRequest())
                assertEquals("Bearer first-project-token", firstRequest.getHeader("Authorization"))
                assertEquals("Bearer second-project-token", secondRequest.getHeader("Authorization"))
                assertFalse(firstRequest.toString().contains("second-project-token"))
                assertFalse(secondRequest.toString().contains("first-project-token"))
            }
        }
    }

    @Test
    fun `missing Storage Box credential fails before a Cloud credential can be sent`() = runBlocking {
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

    private fun profileResponse(name: String) = """{"profile":{"id":1,"name":"$name","email":"$name@example.invalid"}}"""

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
