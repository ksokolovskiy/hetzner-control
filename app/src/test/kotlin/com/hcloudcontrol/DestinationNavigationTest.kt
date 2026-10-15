package com.hcloudcontrol

import com.hcloudcontrol.ui.navigation.*
import org.junit.Assert.*
import org.junit.Test

class DestinationNavigationTest {
    private val context = ResourceContext("project-a", "https://cloud.example/v1", ContextKind.CLOUD)

    @Test fun `all destinations have one canonical roundtrip including real More root`() {
        Destination.entries.forEach { destination -> assertEquals(destination, destination.canonicalRoute(context).legacyDestination()) }
        assertEquals(DestinationKind.ROOT, Destination.More.canonicalRoute(context).kind)
        assertEquals(RootDestination.MORE, Destination.More.canonicalRoute(context).root)
    }

    @Test fun `More child returns to More before Servers`() {
        val more = NavigationState().selectRoot(RootDestination.MORE)
        val images = more.open(Destination.Images.canonicalRoute(context))
        assertFalse(images.showBottomNavigation)
        assertEquals(more, images.up())
        assertTrue(images.up().showBottomNavigation)
        assertEquals(BackDecision.OPEN_SERVERS, images.up().decideBack())
    }

    @Test fun `network alias and More share exactly the same load balancer route`() {
        val route = Destination.LoadBalancers.canonicalRoute(context)
        val fromMore = NavigationState().selectRoot(RootDestination.MORE).open(route)
        val fromNetwork = NavigationState().selectRoot(RootDestination.NETWORK).open(route)
        assertEquals(fromMore.current, fromNetwork.current)
        assertEquals(RootDestination.NETWORK, fromNetwork.up().current.root)
    }

    @Test fun `device-local S3 connection list survives Cloud switch without borrowing Cloud identity`() {
        val route = Destination.ObjectStorage.canonicalRoute(context)
        assertNull(route.context)
        val navigation = NavigationState().selectRoot(RootDestination.MORE).open(route)
        assertEquals(navigation, navigation.switchCloudContext(context.copy(ownerId = "project-b")))
    }

    @Test fun `Storage Box canonical route keeps project owner and documented management host`() {
        val route = Destination.StorageBoxes.canonicalRoute(context)
        assertEquals(context.ownerId, route.context?.ownerId)
        assertEquals(com.hcloudcontrol.core.network.StorageBoxRepositoryFactory.API_BASE_URL, route.context?.endpoint)
        assertNotEquals(context.endpoint, route.context?.endpoint)
    }
}
