package com.hcloudcontrol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class MoreDestinationGroupsTest {
    @Test fun `secondary destinations appear exactly once and never duplicate roots`() {
        val destinations = moreDestinationGroups.flatMap { it.second }
        val roots = setOf(Destination.Servers, Destination.Catalog, Destination.Storage, Destination.Network, Destination.More)
        assertEquals(Destination.entries.toSet() - roots, destinations.toSet())
        assertEquals(destinations.size, destinations.distinct().size)
        assertFalse(destinations.any { it in roots })
    }

    @Test fun `T02 groups match canonical ownership`() {
        assertEquals(listOf(
            listOf(Destination.SshKeys, Destination.Certificates),
            listOf(Destination.Images, Destination.PlacementGroups, Destination.Dns, Destination.LoadBalancers),
            listOf(Destination.StorageBoxes, Destination.ObjectStorage),
            listOf(Destination.Tariffs, Destination.Settings),
        ), moreDestinationGroups.map { it.second })
    }
}
