package com.hcloudcontrol.core.security

import org.junit.Assert.*
import org.junit.Test

class PersistenceFailureGuardTest {
    @Test fun `failed permission disable stays blocked across store recreation until explicit checked recovery`() {
        val fixture = CheckedPreferencesTest.Fixture(mutableMapOf("high_impact_operations_allowed" to true), mutableListOf(false, true, true))
        val processLatch = PersistenceFailureLatch()
        val first = PersistentHighImpactOperationsStore(fixture.preferences, processLatch)
        assertTrue(first.capability().isAllowed)
        assertTrue(runCatching { first.setAllowed(false) }.isFailure)
        assertFalse(first.capability().isAllowed)
        val recreated = PersistentHighImpactOperationsStore(fixture.preferences, processLatch)
        assertFalse(recreated.capability().isAllowed)
        recreated.setAllowed(true)
        assertTrue(recreated.capability().isAllowed)
    }

    @Test fun `failed rollback preserves old public owner and blocks credential reads after wrapper recreation`() {
        data class PublicSnapshot(val ids: List<String>, val selected: String)
        val guard = ProfilePersistenceGuard<PublicSnapshot>()
        val old = PublicSnapshot(listOf("profile-a", "profile-b"), "profile-a")
        var diskMemory = old
        assertTrue(runCatching { guard.checked(old) {
            diskMemory = old.copy(selected = "profile-b")
            error("Synthetic write and rollback failure")
        } }.isFailure)
        fun recreatedPublicReader() = guard.publicView { diskMemory }
        assertEquals(old, recreatedPublicReader())
        var credentialReads = 0
        assertNull(guard.credential { credentialReads++; "synthetic credential" })
        assertEquals(0, credentialReads)
        assertTrue(guard.blocked)
        guard.checked(old) { diskMemory = old.copy(selected = "profile-b") }
        assertFalse(guard.blocked)
        assertEquals("profile-b", recreatedPublicReader().selected)
        assertEquals("synthetic credential", guard.credential { "synthetic credential" })
    }

    @Test fun `Cloud and S3 guard namespaces are independent`() {
        val cloud = ProfilePersistenceGuard<String>()
        val s3 = ProfilePersistenceGuard<String>()
        runCatching { cloud.checked("old-cloud") { error("Synthetic") } }
        assertTrue(cloud.blocked)
        assertFalse(s3.blocked)
        assertEquals("s3-fixture", s3.credential { "s3-fixture" })
    }
}
