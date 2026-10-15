package com.hcloudcontrol

import com.hcloudcontrol.ui.navigation.*
import org.junit.Assert.*
import org.junit.Test

class CanonicalNavigationTest {
    private val cloud = ResourceContext("project-a", "https://cloud.example/v1", ContextKind.CLOUD)
    private val secondCloud = cloud.copy(ownerId = "project-b")
    private val s3 = ResourceContext("connection-a", "https://s3.example", ContextKind.S3)
    private val detail = CanonicalDestination(RootDestination.SERVERS, DestinationKind.DETAIL, cloud, ResourceType.SERVER, "7")

    @Test fun `aliases cannot duplicate canonical details and nested route hides bar`() {
        val nested = NavigationState().open(detail)
        assertFalse(nested.showBottomNavigation)
        assertEquals(nested, nested.open(detail))
        assertTrue(nested.up().showBottomNavigation)
    }

    @Test fun `Back priority is keyboard overlay dirty form nested root then exit`() {
        val nested = NavigationState().open(detail)
        assertEquals(BackDecision.HIDE_KEYBOARD, nested.decideBack(BackEnvironment(true, true, true)))
        assertEquals(BackDecision.CLOSE_OVERLAY, nested.decideBack(BackEnvironment(false, true, true)))
        assertEquals(BackDecision.ASK_RETAIN_OR_DISCARD, nested.decideBack(BackEnvironment(dirtyForm = true)))
        assertEquals(BackDecision.POP, nested.decideBack())
        assertEquals(BackDecision.OPEN_SERVERS, NavigationState().selectRoot(RootDestination.MORE).decideBack())
        assertEquals(BackDecision.EXIT, NavigationState().decideBack())
    }

    @Test fun `switching cloud clears old descendants but preserves independent S3 context`() {
        assertEquals(1, NavigationState().open(detail).switchCloudContext(secondCloud).stack.size)
        val bucket = CanonicalDestination(RootDestination.STORAGE, DestinationKind.DETAIL, s3, ResourceType.BUCKET, "example")
        val switched = NavigationState().open(detail).open(bucket).switchCloudContext(secondCloud)
        assertEquals(listOf(CanonicalDestination(RootDestination.SERVERS), bucket), switched.stack)
        assertEquals(s3, switched.current.context)
    }

    @Test fun `identity distinguishes endpoints and never depends on mutable names`() {
        assertNotEquals(detail, detail.copy(context = cloud.copy(endpoint = "https://mock.example/v1")))
        assertNotEquals(detail, detail.copy(context = secondCloud))
    }

    @Test fun `safe route snapshot restores exact stack and corrupt snapshot returns to root`() {
        val state = NavigationState().open(detail)
        assertEquals(state, NavigationState.restore(state.snapshot()))
        assertEquals(NavigationState(), NavigationState.restore(listOf("obsolete")))
    }

    @Test fun `credential-bearing endpoint cannot enter persisted navigation`() {
        listOf("https://user:secret@example.test", "https://example.test?token=secret").forEach { endpoint ->
            assertTrue(runCatching { ResourceContext("project", endpoint, ContextKind.CLOUD) }.isFailure)
        }
    }
}
