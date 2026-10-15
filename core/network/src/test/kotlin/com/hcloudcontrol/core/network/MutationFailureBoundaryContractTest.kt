package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

/** A provider rejection must reach the common journal as a typed HTTP rejection, not transport loss. */
class MutationFailureBoundaryContractTest {
    private data class Case(val name: String, val mutate: suspend (HCloudRepository) -> Any?)
    private val mutations = listOf(
        Case("firewall create") { it.createFirewall(CreateFirewallSpec("synthetic")) },
        Case("placement create") { it.createPlacementGroup(CreatePlacementGroupSpec("synthetic")) },
        Case("power on") { it.powerOn(7) },
        Case("volume resize") { it.resizeVolume(7, 10, 20) },
        Case("firewall rule update") { it.setFirewallRules(7, emptyList()) },
        Case("placement membership") { it.removeServerFromPlacementGroup(7, 9) },
        Case("volume attach") { it.attachVolume(7, 9) },
        Case("volume detach") { it.detachVolume(7) },
        Case("ISO attach") { it.attachIso(7, IsoSelection(9, "synthetic")) },
        Case("password reset") { it.resetRootPassword(7) },
        Case("rescue enable") { it.enableRescue(7, EnableRescueSpec()) },
        Case("backups enable") { it.enableBackups(7) },
        Case("snapshot") { it.createSnapshot(7, "synthetic") },
        Case("server PTR") { it.changeServerReverseDns(7, "192.0.2.1", null) },
        Case("private network attach") { it.attachServerToPrivateNetwork(7, 9) },
        Case("primary IP assign") { it.assignPrimaryIp(7, 9) },
        Case("floating IP assign") { it.assignFloatingIp(7, 9) },
        Case("SSH key delete") { it.deleteSshKey(7) },
    )
    @Test fun `mutation HTTP rejections are normalized exactly once without retry or credential leakage`() = runBlocking {
        MockWebServer().use { mock ->
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create(
                tokenProvider = { "fixture-token" },
                configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, mock.url("/v1").toString()) },
                diagnosticsLogger = HCloudApiDiagnosticsLogger {},
            ))
            mutations.forEachIndexed { index, entry ->
                val status = listOf(401, 403, 409, 422)[index % 4]
                mock.enqueue(MockResponse().setResponseCode(status).setBody(
                    """{"error":{"code":"fixture_denied","message":"token=synthetic-private-value"}}"""))
                val failure = runCatching { entry.mutate(repository) }.exceptionOrNull()
                assertTrue(entry.name, failure is HCloudApiException)
                val typed = failure as HCloudApiException
                assertEquals(entry.name, status, typed.failure.httpStatus)
                assertEquals("fixture_denied", typed.failure.code)
                assertNull(typed.cause)
                assertFalse(typed.toString().contains("synthetic-private-value"))
                assertFalse(mock.takeRequest().method == "GET")
                assertEquals(index + 1, mock.requestCount)
            }
        }
    }
}
