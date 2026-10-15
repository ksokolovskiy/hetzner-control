package com.hcloudcontrol

import com.hcloudcontrol.connections.PinnedCloudConnection
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.security.*
import org.junit.Assert.*
import org.junit.Test

class PinnedCloudConnectionTest {
    private class Store : HCloudTokenStore {
        val profiles = listOf(CloudProjectConnection("first", "First", HCloudConnectionConfig.real()),
            CloudProjectConnection("second", "Second", HCloudConnectionConfig.testMock()))
        val credentials = mutableMapOf("first" to "synthetic-first", "second" to "synthetic-second")
        var selected = "first"
        override fun token() = credentials[selected]
        override fun tokenForConnection(connectionId: String) = credentials[connectionId]
        override fun connections() = profiles
        override fun activeConnectionId() = selected
        override fun selectConnection(connectionId: String) { selected = connectionId }
        override fun removeConnection(connectionId: String) { credentials.remove(connectionId) }
        override fun saveConnection(displayName: String, token: String, config: HCloudConnectionConfig): CloudProjectConnection = error("Not used")
    }

    @Test fun `pending request owner credential and endpoint never follow active project`() {
        val store = Store()
        val original = PinnedCloudConnection(store, store.profiles.first())
        store.selectConnection("second")
        assertEquals("synthetic-first", original.token())
        assertEquals(HCloudConnectionConfig.real(), original.config)
        assertFalse(original.toString().contains("synthetic-first"))
    }

    @Test fun `removed original credential fails closed instead of using another project`() {
        val store = Store()
        val original = PinnedCloudConnection(store, store.profiles.first())
        store.selectConnection("second")
        store.removeConnection("first")
        assertNull(original.token())
        assertNotNull(store.token())
    }
}
