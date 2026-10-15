package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ServerMetadataContractTest {
    @Test fun `location catalogue preserves network zone and absent zone stays unknown`() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(MockResponse().setBody("""{"locations":[{"id":1,"name":"nbg1","city":"Nuremberg","network_zone":"eu-central"},{"id":2,"name":"synthetic"}]}"""))
            val locations = repository(mock).getLocations()
            assertEquals("eu-central", locations[0].networkZone)
            assertNull(locations[1].networkZone)
            assertEquals("Nuremberg", locations[0].city)
        }
    }
    private fun repository(server: MockWebServer) = RetrofitHCloudRepository(HCloudApiFactory.create(
        tokenProvider = { "synthetic-only" },
        configProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
        diagnosticsLogger = HCloudApiDiagnosticsLogger {},
    ))
    @Test fun `server preserves actual retained disk independently of nominal plan and image metadata`() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(MockResponse().setBody("""{"server":{"id":42,"name":"synthetic","status":"off","public_net":{"ipv4":null},
                "primary_disk_size":40,"server_type":{"id":1,"name":"synthetic-type","cores":4,"memory":8,"disk":80,
                "architecture":"x86","storage_type":"local","cpu_type":"shared"},
                "image":{"id":2,"name":"ubuntu-24.04","description":"Synthetic OS","architecture":"x86","os_flavor":"ubuntu","os_version":"24.04"},
                "location":{"id":3,"name":"nbg1","city":"Nuremberg","country":"DE","network_zone":"eu-central"}}}"""))
            val server = repository(mock).getServer(42)
            assertEquals(40.0, server.primaryDiskSizeGb)
            assertEquals(80, server.hardware?.planDiskGb)
            assertEquals("local", server.hardware?.storageType)
            assertEquals(4, server.hardware?.cores)
            assertEquals("ubuntu", server.image?.osFlavor)
            assertEquals("24.04", server.image?.osVersion)
            assertEquals("Nuremberg", server.locationDetails?.city)
            assertEquals("eu-central", server.locationDetails?.networkZone)
            assertEquals("GET", mock.takeRequest().method)
        }
    }
    @Test fun `partial server never fabricates disk hardware or an installed image`() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(MockResponse().setBody("""{"server":{"id":42,"name":"synthetic","status":"off","public_net":{"ipv4":null},"server_type":{"id":1,"name":"synthetic-type"},"image":null}}"""))
            val server = repository(mock).getServer(42)
            assertNull(server.primaryDiskSizeGb)
            assertNull(server.hardware?.memoryGb)
            assertNull(server.hardware?.planDiskGb)
            assertNull(server.hardware?.storageType)
            assertNull(server.image)
            assertNull(server.locationDetails)
        }
    }
    @Test fun `server type catalogue retains API storage and CPU categories`() = runBlocking {
        MockWebServer().use { mock ->
            mock.enqueue(MockResponse().setBody("""{"server_types":[{"id":1,"name":"synthetic-type","cores":2,"memory":4,"disk":40,
                "architecture":"arm","storage_type":"network","cpu_type":"dedicated","locations":[{"name":"nbg1","available":true}]}]}"""))
            val type = repository(mock).getServerTypes().single()
            assertEquals("network", type.storageType)
            assertEquals("dedicated", type.cpuType)
            assertEquals("arm", type.architecture)
        }
    }
}
