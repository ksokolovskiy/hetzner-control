package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.DnsScope
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class DnsReadOnlyContractTest {
    @Test
    fun `acknowledged Cloud DNS scope lists details RRsets paginates and encodes path values with only project credential`() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("""{"zones":[{"id":501,"name":"example.test","mode":"primary","ttl":3600,"labels":{"environment":"production"}}],"meta":{"pagination":{"next_page":2}}}"""))
            server.enqueue(MockResponse().setBody("""{"zones":[{"id":502,"name":"secondary.test","mode":"secondary","labels":{}}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"zone":{"id":501,"name":"example.test","mode":"primary","ttl":3600,"labels":{"environment":"production"}}}"""))
            server.enqueue(MockResponse().setBody("""{"rrsets":[{"name":"@","type":"MX","ttl":3600,"records":[{"value":"10 mail.example.test.","comment":"primary mail"}],"labels":{"role":"mail"}}],"meta":{"pagination":{"next_page":2}}}"""))
            server.enqueue(MockResponse().setBody("""{"rrsets":[{"name":"www","type":"A","ttl":60,"records":[{"value":"192.0.2.10"}],"labels":{}}],"meta":{"pagination":{"next_page":null}}}"""))
            server.enqueue(MockResponse().setBody("""{"rrset":{"name":"@","type":"MX","ttl":3600,"records":[{"value":"10 mail.example.test.","comment":"primary mail"}],"labels":{"role":"mail"}}}"""))
            val cloudToken = "project-cloud-token"
            val legacyDnsConsoleToken = "legacy-dns-console-token"
            val repository = RetrofitDnsRepository(
                DnsApiFactory.create(
                    scope = DnsScope.acknowledgeReadOnly(),
                    hCloudTokenProvider = { cloudToken },
                    hCloudConfigProvider = { HCloudConnectionConfig(ConnectionKind.REAL, server.url("/v1").toString()) },
                    diagnosticsLogger = HCloudApiDiagnosticsLogger { },
                ),
            )

            val zones = repository.getZones()
            val zone = repository.getZone("example.test")
            val rrsets = repository.getRrSets("example.test")
            // A slash is not a valid DNS label; this transport-level check proves that an
            // untrusted path value cannot escape the rr_name segment before API validation.
            val rrset = repository.getRrSet("example.test", "untrusted/segment", "MX")

            assertEquals(listOf(501L, 502L), zones.map { it.id })
            assertEquals("primary", zone.mode)
            assertEquals("10 mail.example.test.", rrsets.first().records.single().value)
            assertEquals("primary mail", rrset.records.single().comment)
            val requests = List(6) { requireNotNull(server.takeRequest()) }
            assertEquals("/v1/zones?page=1&per_page=50", requests[0].path)
            assertEquals("/v1/zones?page=2&per_page=50", requests[1].path)
            assertEquals("/v1/zones/example.test", requests[2].path)
            assertEquals("/v1/zones/example.test/rrsets?page=1&per_page=50", requests[3].path)
            assertEquals("/v1/zones/example.test/rrsets?page=2&per_page=50", requests[4].path)
            assertEquals("/v1/zones/example.test/rrsets/untrusted%2Fsegment/MX", requests[5].path)
            requests.forEach { request ->
                assertEquals("Bearer $cloudToken", request.getHeader("Authorization"))
                assertFalse(request.toString().contains(legacyDnsConsoleToken))
            }
        }
    }
}
