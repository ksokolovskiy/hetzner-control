package com.hcloudcontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeBackNavigationTest {
    @Test
    fun `back closes transient surfaces before navigating away`() {
        assertEquals(
            HomeBackAction.CloseMore,
            resolveHomeBack(
                showMoreDestinations = true,
                showCreateServer = true,
                selectedServer = sampleServer(),
                destination = Destination.Catalog,
            ),
        )
        assertEquals(
            HomeBackAction.CancelServerCreate,
            resolveHomeBack(
                showMoreDestinations = false,
                showCreateServer = true,
                selectedServer = sampleServer(),
                destination = Destination.Catalog,
            ),
        )
    }

    @Test
    fun `back returns from a server detail to its list before changing tab`() {
        assertEquals(
            HomeBackAction.CloseServerDetail,
            resolveHomeBack(false, false, sampleServer(), Destination.Catalog),
        )
        assertEquals(
            HomeBackAction.LeavePrimaryDestination,
            resolveHomeBack(false, false, null, Destination.Catalog),
        )
    }

    @Test
    fun `back at the primary server list is delegated to Android`() {
        assertEquals(
            HomeBackAction.LetSystemHandle,
            resolveHomeBack(false, false, null, Destination.Servers),
        )
    }

    private fun sampleServer() = com.hcloudcontrol.core.domain.model.Server(
        id = 1,
        name = "example",
        status = com.hcloudcontrol.core.domain.model.ServerStatus.RUNNING,
        serverType = "cx22",
        location = "fsn1",
        ipv4Address = null,
        backupWindow = null,
        publicDnsPointers = emptyList(),
    )
}
