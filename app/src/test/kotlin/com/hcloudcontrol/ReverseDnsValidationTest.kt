package com.hcloudcontrol

import org.junit.Assert.*
import org.junit.Test

class ReverseDnsValidationTest {
    @Test fun `blank is explicit null and hostname normalized`() {
        assertNull(normalizePtrHostname("  "))
        assertEquals("host.example.org", normalizePtrHostname(" Host.Example.ORG "))
        assertEquals("xn--bcher-kva.example", normalizePtrHostname("bücher.example"))
    }
    @Test fun `URL address wildcard and malformed labels rejected`() {
        listOf("https://example.org", "192.0.2.3", "host.example.", "*.example.org", "-bad.example", "a..example", "a".repeat(64) + ".example")
            .forEach { assertTrue(it, runCatching { normalizePtrHostname(it) }.isFailure) }
    }
}
