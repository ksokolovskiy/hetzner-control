package com.hcloudcontrol

import com.hcloudcontrol.ui.navigation.*
import org.junit.Assert.*
import org.junit.Test

/** Synthetic model contracts only; these do not claim physical gesture or Compose restoration. */
class CanonicalNavigationBranchesTest {
    private val cloud = ResourceContext("project-a", "https://cloud.example/v1", ContextKind.CLOUD)
    private val s3 = ResourceContext("s3-a", "https://s3.example", ContextKind.S3)
    private val detail = CanonicalDestination(RootDestination.SERVERS, DestinationKind.DETAIL, cloud, ResourceType.SERVER, "41")

    @Test fun `reopening canonical ancestor removes its form rather than duplicating history`() {
        val form = detail.copy(kind = DestinationKind.FORM)
        val nested = NavigationState().open(detail).open(form)
        assertEquals(listOf(CanonicalDestination(RootDestination.SERVERS), detail), nested.open(detail).stack)
        assertEquals(nested, nested.open(form))
    }

    @Test fun `same provider ID in another resource type is not an alias`() {
        val volume = detail.copy(root = RootDestination.STORAGE, resourceType = ResourceType.VOLUME)
        val nested = NavigationState().open(detail).open(volume)
        assertEquals(3, nested.stack.size)
        assertEquals(detail, nested.up().current)
        assertEquals(volume, nested.current)
    }

    @Test fun `opening each root clears descendants and Back at a root is idempotent`() {
        RootDestination.entries.forEach { root ->
            val selected = NavigationState().open(detail).open(CanonicalDestination(root))
            assertEquals(listOf(CanonicalDestination(root)), selected.stack)
            assertEquals(selected, selected.up())
            assertTrue(selected.showBottomNavigation)
            assertEquals(if (root == RootDestination.SERVERS) BackDecision.EXIT else BackDecision.OPEN_SERVERS, selected.decideBack())
        }
    }

    @Test fun `Cloud switch prunes Cloud ancestry but preserves independent S3 picker detail and form`() {
        val root = CanonicalDestination(RootDestination.MORE)
        val cloudList = Destination.Settings.canonicalRoute(cloud)
        val picker = Destination.ObjectStorage.canonicalRoute(cloud)
        val bucket = CanonicalDestination(RootDestination.MORE, DestinationKind.DETAIL, s3, ResourceType.BUCKET, "fixture-bucket")
        val form = bucket.copy(kind = DestinationKind.FORM)
        val original = NavigationState(listOf(root)).open(cloudList).open(picker).open(bucket).open(form)
        val switched = original.switchCloudContext(cloud.copy(ownerId = "project-b"))
        assertEquals(listOf(root, picker, bucket, form), switched.stack)
        assertEquals(listOf(root, picker, bucket), switched.up().stack)
        assertEquals(s3, switched.current.context)
        assertFalse(switched.showBottomNavigation)
        assertTrue(runCatching { original.switchCloudContext(s3) }.isFailure)
    }

    @Test fun `endpoint-only Cloud change clears descendants and keeps the owning canonical root`() {
        val list = Destination.LoadBalancers.canonicalRoute(cloud)
        val original = NavigationState().selectRoot(RootDestination.NETWORK).open(list)
        val switched = original.switchCloudContext(cloud.copy(endpoint = "https://mock.example/v1"))
        assertEquals(listOf(CanonicalDestination(RootDestination.MORE)), switched.stack)
        assertNull(switched.current.context)
    }

    @Test fun `saved nested form restores exact Back sequence without provider objects`() {
        val form = detail.copy(kind = DestinationKind.FORM)
        val saved = NavigationState().open(detail).open(form).snapshot()
        val restored = NavigationState.restore(saved)
        assertEquals(form, restored.current)
        assertEquals(detail, restored.up().current)
        assertEquals(NavigationState(), restored.up().up())
        assertFalse(restored.showBottomNavigation)
    }

    @Test fun `structurally malformed complete records fall back safely to Servers root`() {
        val valid = NavigationState().open(detail).snapshot()
        val malformed = listOf(
            valid.toMutableList().apply { this[7] = "UNKNOWN_ROOT" },
            valid.toMutableList().apply { this[8] = "ROOT" },
            valid.toMutableList().apply { this[11] = "" },
            valid.toMutableList().apply { this[10] = "https://user:fixture@cloud.example/v1" },
            valid.toMutableList().apply { this[10] = "https://cloud.example/v1?token=fixture" },
            valid.toMutableList().apply { this[13] = "" },
        )
        malformed.forEach { assertEquals(NavigationState(), NavigationState.restore(it)) }
    }
}
