package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ServerInventoryFilterTest {
    @Test fun `every provider server status has a distinct localized resource`() {
        val resources = ServerStatus.entries.map(::serverStatusLabelResource)
        assertEquals(ServerStatus.entries.size, resources.distinct().size)
        org.junit.Assert.assertTrue(resources.all { it > 0 })
    }
    private val servers = listOf(
        Server(2, "Web", ServerStatus.RUNNING, "203.0.113.2", "fsn1", "cx23", labels = mapOf("role" to "frontend")),
        Server(1, "Database", ServerStatus.OFF, null, "fsn1", "cx23"),
    )
    @Test fun `query and status intersect without changing source data`() {
        assertEquals(listOf(2L), filterServers(servers, "ROLE=front", ServerInventoryFilter.RUNNING).map { it.id })
        assertEquals(emptyList<Server>(), filterServers(servers, "frontend", ServerInventoryFilter.OFF))
        assertEquals(listOf(1L, 2L), filterServers(servers, "", ServerInventoryFilter.ALL).map { it.id })
        assertEquals(listOf(2L, 1L), servers.map { it.id })
    }
}
