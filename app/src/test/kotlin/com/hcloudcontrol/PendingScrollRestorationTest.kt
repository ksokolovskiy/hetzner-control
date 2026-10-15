package com.hcloudcontrol

import org.junit.Assert.*
import org.junit.Test

class PendingScrollRestorationTest {
    @Test fun `loading placeholder never overwrites deep saved viewport then restores once ready`() {
        val saved = ScrollPosition(52, 17)
        val policy = PendingScrollRestoration(saved)
        assertNull(policy.target(false)); assertFalse(policy.canRecord(false))
        assertEquals(saved, policy.target(true)); assertFalse(policy.canRecord(true))
        policy.restored()
        assertNull(policy.target(true)); assertTrue(policy.canRecord(true))
        assertFalse(policy.canRecord(false))
    }
    @Test fun `real user loading scroll supersedes restoration and fresh context starts independent policy`() {
        val first = PendingScrollRestoration(ScrollPosition(52, 17))
        first.userInteractedWhileLoading()
        assertNull(first.target(true)); assertTrue(first.canRecord(true))
        val otherContext = PendingScrollRestoration(ScrollPosition.Zero)
        assertEquals(ScrollPosition.Zero, otherContext.target(true))
    }
}
