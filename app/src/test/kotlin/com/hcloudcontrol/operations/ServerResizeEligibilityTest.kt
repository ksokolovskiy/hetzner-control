package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class ServerResizeEligibilityTest {
    private val source = ServerTypeOption(1, "source", "Synthetic", 4, 8.0, 80, "shared", "x86", setOf("nbg1"), "local")
    private val target = source.copy(id = 2, name = "target", cores = 2, memoryGb = 4.0, diskGb = 40)
    private val server = Server(42, "synthetic", ServerStatus.OFF, null, "nbg1", "source", primaryDiskSizeGb = 40.0)
    @Test fun `retained actual disk permits smaller compute plan with sufficient disk`() {
        assertTrue(serverResizeEligible(server, source, target))
        assertFalse(serverResizeEligible(server.copy(primaryDiskSizeGb = 80.0), source, target))
    }
    @Test fun `cross architecture or storage changes are not eligible`() {
        assertFalse(serverResizeEligible(server, source, target.copy(architecture = "arm")))
        assertFalse(serverResizeEligible(server, source, target.copy(storageType = "network")))
    }
    @Test fun `unknown actual disk cannot be replaced with nominal plan disk`() {
        assertFalse(serverResizeEligible(server.copy(primaryDiskSizeGb = null), source, target.copy(diskGb = 160)))
        assertFalse(serverResizeEligible(server, source.copy(storageType = null), target))
    }
    @Test fun `temporary unavailable target or same current plan is not eligible`() {
        assertFalse(serverResizeEligible(server, source, target.copy(availableLocations = emptySet())))
        assertFalse(serverResizeEligible(server, source, source))
    }
}
