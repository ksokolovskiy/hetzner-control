package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.DnsRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DnsReadPresentationTest {
    @Test fun `restored RRset tuple requires exact original project endpoint and zone scope`() {
        val identity = DnsRrSetIdentity("@", "TXT")
        val snapshot = dnsRrSelectionSnapshot("project:one:endpoint:a:zone:42", identity)
        assertEquals(identity, restoreDnsRrSelection("project:one:endpoint:a:zone:42", snapshot))
        assertNull(restoreDnsRrSelection("project:two:endpoint:a:zone:42", snapshot))
        assertNull(restoreDnsRrSelection("project:one:endpoint:b:zone:42", snapshot))
        assertNull(restoreDnsRrSelection("project:one:endpoint:a:zone:43", snapshot))
    }
    private class Fake : DnsRepository {
        var reads = 0
        override suspend fun getZones(): List<DnsZone> { reads++; return emptyList() }
        override suspend fun getZone(zoneIdOrName: String): DnsZone = error("Not requested")
        override suspend fun getRrSets(zoneIdOrName: String): List<DnsRrSet> = error("Not requested")
        override suspend fun getRrSet(zoneIdOrName: String, rrName: String, rrType: String): DnsRrSet = error("Not requested")
    }
    @Test fun `unchecked consent cannot issue DNS reads`() = runBlocking {
        val fake = Fake()
        assertTrue(runCatching { readAcknowledgedDnsZones(false, fake) }.isFailure)
        assertEquals(0, fake.reads)
        assertEquals(emptyList<DnsZone>(), readAcknowledgedDnsZones(true, fake))
        assertEquals(1, fake.reads)
    }
    @Test fun `RRset search matches name type full long TXT values and IPv6 without altering values`() {
        val longValue = "synthetic-".repeat(200) + "last-marker"
        val record = DnsRrSet("example.invalid", "TXT", 300, listOf(DnsRecord(longValue, null)), emptyMap())
        assertTrue(dnsRrSetMatches(record, "last-marker"))
        assertTrue(dnsRrSetMatches(record, "TXT"))
        assertFalse(dnsRrSetMatches(record, "missing"))
        assertEquals(longValue, record.records.single().value)
        assertTrue(dnsRrSetMatches(record.copy(type = "AAAA", records = listOf(DnsRecord("2001:db8::42", null))), "::42"))
    }
}
