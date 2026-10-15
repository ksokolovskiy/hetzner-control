package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PublicIpEligibilityTest {
    private val primary = PrimaryIpInventory(1, "primary", "ipv4", "192.0.2.1", "fsn1", null, null, false, emptyMap(), emptyList())
    private val floating = FloatingIpInventory(2, "floating", "ipv4", "192.0.2.2", "fsn1", null, null, false, emptyMap(), emptyList())
    private fun server() = Server(3, "target", ServerStatus.OFF, null, "fsn1-dc14", "cx23",
        locationDetails = LocationDetails(1, "fsn1", "Falkenstein", null, null, "eu-central"))
    private val locations = listOf(LocationOption(1, "fsn1", "Falkenstein", null, null, "eu-central"),
        LocationOption(2, "nbg1", "Nuremberg", null, null, "eu-central"))

    @Test fun `primary uses actual location not legacy datacenter and requires empty family`() {
        assertNull(primaryIpAssignmentBlock(primary, server()))
        assertEquals(PublicIpAssignmentBlock.FAMILY_OCCUPIED, primaryIpAssignmentBlock(primary, server().copy(ipv4Address = "192.0.2.3")))
        assertEquals(PublicIpAssignmentBlock.SERVER_NOT_OFF, primaryIpAssignmentBlock(primary, server().copy(status = ServerStatus.RUNNING)))
        assertEquals(PublicIpAssignmentBlock.ALREADY_ASSIGNED, primaryIpAssignmentBlock(primary.copy(assigneeId = 9), server()))
    }

    @Test fun `floating allows running server in another location within same zone`() {
        val target = server().copy(status = ServerStatus.RUNNING, ipv4Address = "192.0.2.3",
            locationDetails = LocationDetails(2, "nbg1", "Nuremberg", null, null, "eu-central"))
        assertNull(floatingIpAssignmentBlock(floating, target, locations))
        assertEquals(PublicIpAssignmentBlock.DIFFERENT_ZONE, floatingIpAssignmentBlock(floating,
            target.copy(locationDetails = target.locationDetails!!.copy(networkZone = "us-east")), locations))
    }

    @Test fun `unknown floating zone and missing primary fail closed without guessing geography`() {
        assertEquals(PublicIpAssignmentBlock.MISSING_PRIMARY_IP, floatingIpAssignmentBlock(floating, server(), locations))
        assertEquals(PublicIpAssignmentBlock.UNKNOWN_ZONE, floatingIpAssignmentBlock(floating.copy(homeLocation = "unknown"), server().copy(ipv4Address = "192.0.2.3"), locations))
    }
}
