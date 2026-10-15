package com.hcloudcontrol

import org.junit.Assert.*
import org.junit.Test

class NetworkCategorySearchTest {
    @Test fun `Primary and Floating queries have independent restored fields`() {
        val primary = publicIpQueryScope("project:endpoint", true)
        val floating = publicIpQueryScope("project:endpoint", false)
        val saved = listOf("1", primary, "fixture primary filter")
        assertEquals("fixture primary filter", com.hcloudcontrol.ui.navigation.restoreContextualText(primary, saved))
        assertEquals("", com.hcloudcontrol.ui.navigation.restoreContextualText(floating, saved))
    }
    private val titles = listOf("Public IPs", "Private networks", "Firewalls", "Load balancers", "Placement groups")
    @Test fun `empty and matching queries retain categories but absent query has explicit no results`() {
        assertTrue(matchesNetworkCategory("", titles))
        assertTrue(matchesNetworkCategory("  ", titles))
        assertTrue(matchesNetworkCategory("NETWORK", titles))
        assertFalse(matchesNetworkCategory("snapshot", titles))
    }
}
