package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import com.hcloudcontrol.core.security.CloudProjectConnection
import com.hcloudcontrol.core.security.HCloudTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ActiveConnectionConfigTest {
    @Test
    fun noActiveProfileCannotFallBackToTheRealCloudEndpoint() {
        try {
            EmptyTokenStore.requireActiveConnectionConfig()
            fail("A missing active connection must fail before routing a request")
        } catch (expected: IllegalArgumentException) {
            assertEquals("No active Cloud project connection", expected.message)
        }
    }

    @Test
    fun activeTestProfileKeepsItsOwnConfiguredEndpoint() {
        val connection = CloudProjectConnection("test", "Test Server", HCloudConnectionConfig.testMock())
        val store = FixedTokenStore(connection)

        assertEquals(HCloudConnectionConfig.TEST_MOCK_BASE_URL, store.requireActiveConnectionConfig().baseUrl)
    }

    private object EmptyTokenStore : HCloudTokenStore {
        override fun token(): String? = null
        override fun connections(): List<CloudProjectConnection> = emptyList()
        override fun activeConnectionId(): String? = null
        override fun selectConnection(connectionId: String) = error("not used")
        override fun saveConnection(displayName: String, token: String, config: HCloudConnectionConfig): CloudProjectConnection = error("not used")
        override fun removeConnection(connectionId: String) = error("not used")
    }

    private class FixedTokenStore(private val connection: CloudProjectConnection) : HCloudTokenStore {
        override fun token(): String? = null
        override fun connections(): List<CloudProjectConnection> = listOf(connection)
        override fun activeConnectionId(): String = connection.id
        override fun selectConnection(connectionId: String) = error("not used")
        override fun saveConnection(displayName: String, token: String, config: HCloudConnectionConfig): CloudProjectConnection = error("not used")
        override fun removeConnection(connectionId: String) = error("not used")
    }
}
