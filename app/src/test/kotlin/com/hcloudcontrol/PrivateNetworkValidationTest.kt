package com.hcloudcontrol

import com.hcloudcontrol.network.*
import com.hcloudcontrol.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class PrivateNetworkValidationTest {
    private val network = PrivateNetworkInventory(7, "fixture", "10.0.0.0/16",
        listOf(PrivateNetworkSubnet("cloud", "10.0.1.0/24", "eu-central", null, null)), emptyList(), emptyList(), false, emptyMap())

    @Test fun `CIDR is numeric aligned RFC1918 and minimum provider size`() {
        assertTrue(validPrivateNetworkCidr("10.0.0.0/16"))
        for (invalid in listOf("example.com/16", "10.0.0.1/16", "8.8.8.0/24", "10.0.0.0/25", "010.0.0.0/16", "10.0.0.0/33")) {
            assertFalse(invalid, validPrivateNetworkCidr(invalid))
        }
    }

    @Test fun `subnet requires actual zone containment no overlap and provider assigned gateway`() {
        val valid = PrivateNetworkSubnetSpec("10.0.2.0/24", "eu-central")
        assertTrue(validCloudSubnet(network, valid, setOf("eu-central")))
        assertFalse(validCloudSubnet(network, valid.copy(networkZone = "guessed-zone"), setOf("eu-central")))
        assertFalse(validCloudSubnet(network, valid.copy(ipRange = "10.1.0.0/24"), setOf("eu-central")))
        assertFalse(validCloudSubnet(network, valid.copy(ipRange = "10.0.1.0/25"), setOf("eu-central")))
        assertFalse(validCloudSubnet(network, valid.copy(gateway = "10.0.2.1"), setOf("eu-central")))
    }

    @Test fun `numeric private range constants preserve every RFC1918 boundary`() {
        for (valid in listOf("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "172.31.255.0/24")) {
            assertTrue(valid, validPrivateNetworkCidr(valid))
        }
        for (invalid in listOf("9.255.255.0/24", "11.0.0.0/8", "172.15.255.0/24", "172.32.0.0/16", "192.167.0.0/16", "192.169.0.0/16")) {
            assertFalse(invalid, validPrivateNetworkCidr(invalid))
        }
    }

    @Test fun `route keeps reserved and topology constraints separate from external reachability`() {
        assertTrue(validNetworkRoute(network, PrivateNetworkRouteSpec("10.2.0.0/16", "10.0.1.2")))
        assertTrue(validNetworkRoute(network, PrivateNetworkRouteSpec("0.0.0.0/0", "10.0.1.2")))
        assertFalse(validNetworkRoute(network, PrivateNetworkRouteSpec("10.0.1.0/24", "10.0.1.2")))
        assertFalse(validNetworkRoute(network, PrivateNetworkRouteSpec("10.2.0.0/16", "10.0.0.0")))
        assertFalse(validNetworkRoute(network, PrivateNetworkRouteSpec("10.2.0.0/16", "172.31.1.1")))
        assertFalse(validNetworkRoute(network, PrivateNetworkRouteSpec("172.31.1.0/24", "10.0.1.2")))
    }

    @Test fun `member eligibility uses real location zone never code prefix guesses`() {
        val server = Server(42, "fixture", ServerStatus.RUNNING, null, "nbg1", "cx-test",
            locationDetails = LocationDetails(1, "nbg1", "Nuremberg", "Nuremberg", "DE", "eu-central"))
        assertTrue(eligibleNetworkMember(network, server))
        assertFalse(eligibleNetworkMember(network, server.copy(locationDetails = null)))
        assertFalse(eligibleNetworkMember(network, server.copy(locationDetails = server.locationDetails?.copy(networkZone = "us-east"))))
        assertFalse(eligibleNetworkMember(network.copy(serverIds = listOf(42)), server))
    }
}
