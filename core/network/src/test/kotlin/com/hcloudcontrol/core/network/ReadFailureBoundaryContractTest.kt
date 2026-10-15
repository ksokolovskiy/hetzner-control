package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ReadFailureBoundaryContractTest {
    private data class ReadCase(val name: String, val read: suspend (HCloudRepository) -> Any?)
    private val reads = listOf(
        ReadCase("servers") { it.getServers() }, ReadCase("server") { it.getServer(7) },
        ReadCase("server actions") { it.getServerActions(7) }, ReadCase("action") { it.getAction(7) },
        ReadCase("server types") { it.getServerTypes() }, ReadCase("server type") { it.getServerType(7) },
        ReadCase("server price") { it.getServerTypePrice(7, "fsn1") },
        ReadCase("system images") { it.getSystemImages() }, ReadCase("backup images") { it.getBackupAndSnapshotImages() },
        ReadCase("images") { it.getImages() }, ReadCase("image") { it.getImage(7) }, ReadCase("image actions") { it.getImageActions(7) },
        ReadCase("ISOs") { it.getIsos() }, ReadCase("ISO") { it.getIso(7) },
        ReadCase("volumes") { it.getVolumes() }, ReadCase("volume") { it.getVolume(7) }, ReadCase("volume actions") { it.getVolumeActions(7) },
        ReadCase("primary IPs") { it.getPrimaryIps() }, ReadCase("primary IP") { it.getPrimaryIp(7) }, ReadCase("primary IP actions") { it.getPrimaryIpActions(7) },
        ReadCase("floating IPs") { it.getFloatingIps() }, ReadCase("floating IP") { it.getFloatingIp(7) }, ReadCase("floating IP actions") { it.getFloatingIpActions(7) },
        ReadCase("certificates") { it.getCertificates() }, ReadCase("certificate") { it.getCertificate(7) }, ReadCase("certificate actions") { it.getCertificateActions(7) },
        ReadCase("firewalls") { it.getFirewalls() }, ReadCase("firewall") { it.getFirewall(7) }, ReadCase("firewall actions") { it.getFirewallActions(7) },
        ReadCase("networks") { it.getPrivateNetworks() }, ReadCase("network") { it.getPrivateNetwork(7) },
        ReadCase("network members") { it.getPrivateNetworkMembers(7) }, ReadCase("network actions") { it.getPrivateNetworkActions(7) },
        ReadCase("load balancers") { it.getLoadBalancers() }, ReadCase("load balancer") { it.getLoadBalancer(7) }, ReadCase("load balancer actions") { it.getLoadBalancerActions(7) },
        ReadCase("load balancer types") { it.getLoadBalancerTypes() }, ReadCase("load balancer type") { it.getLoadBalancerType(7) },
        ReadCase("placement groups") { it.getPlacementGroups() }, ReadCase("placement group") { it.getPlacementGroup(7) },
        ReadCase("locations") { it.getLocations() }, ReadCase("location") { it.getLocation(7) }, ReadCase("SSH keys") { it.getSshKeys() },
    )

    @Test fun `every Cloud repository read exposes sanitized domain HTTP failures`() = runBlocking {
        MockWebServer().use { server ->
            val repository = RetrofitHCloudRepository(HCloudApiFactory.create(
                tokenProvider = { "synthetic-credential" },
                configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                diagnosticsLogger = HCloudApiDiagnosticsLogger { },
            ))
            reads.forEachIndexed { index, read ->
                val status = listOf(401, 403, 404)[index % 3]
                server.enqueue(MockResponse().setResponseCode(status).setBody(
                    """{"error":{"code":"fixture_error","message":"token=fixture-secret"}}""",
                ))
                try {
                    read.read(repository)
                    fail("${read.name} must expose an HTTP read failure")
                } catch (error: HCloudApiException) {
                    assertEquals(read.name, status, error.failure.httpStatus)
                    assertEquals("fixture_error", error.failure.code)
                    assertNull(error.cause)
                    assertFalse(error.toString().contains("fixture-secret"))
                }
                assertEquals("GET", server.takeRequest().method)
            }
        }
    }
}
