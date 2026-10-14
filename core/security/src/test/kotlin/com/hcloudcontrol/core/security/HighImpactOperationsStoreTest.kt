package com.hcloudcontrol.core.security

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.RobolectricTestRunner
import org.junit.runner.RunWith

@RunWith(RobolectricTestRunner::class)
class HighImpactOperationsStoreTest {
    private fun store() = PersistentHighImpactOperationsStore(RuntimeEnvironment.getApplication())

    @After
    fun restoreSafeDefault() {
        store().setAllowed(false)
    }

    @Test
    fun `new permission is disabled by default`() {
        store().setAllowed(false)

        assertFalse(store().capability().isAllowed)
    }

    @Test
    fun `explicit permission persists across store instances`() {
        store().setAllowed(true)

        assertTrue(PersistentHighImpactOperationsStore(RuntimeEnvironment.getApplication()).capability().isAllowed)
    }
}
