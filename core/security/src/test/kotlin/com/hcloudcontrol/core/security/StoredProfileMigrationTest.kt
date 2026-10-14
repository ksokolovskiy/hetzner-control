package com.hcloudcontrol.core.security

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoredProfileMigrationTest {
    @Test
    fun `legacy tokenless sandbox is retired instead of becoming real`() {
        val connection = StoredProfile("sandbox", "Sandbox", "BUILT_IN_DEMO")
            .toConnectionOrNull(token = null)

        assertNull(connection)
    }

    @Test
    fun `legacy sandbox is retired even if obsolete metadata still has a credential`() {
        val connection = StoredProfile("sandbox", "Sandbox", "BUILT_IN_DEMO")
            .toConnectionOrNull(token = "must-not-be-routed")

        assertNull(connection)
    }

    @Test
    fun `credentialed legacy profile remains real`() {
        val connection = StoredProfile("real", "Production", kind = "")
            .toConnectionOrNull(token = "credential")

        requireNotNull(connection)
        assertEquals(ConnectionKind.REAL, connection.config.kind)
    }

    @Test
    fun `tokenless real metadata is not exposed as a usable connection`() {
        val connection = StoredProfile("real", "Production", ConnectionKind.REAL.name)
            .toConnectionOrNull(token = "   ")

        assertNull(connection)
    }

    @Test
    fun `credentialed test mock keeps distinct routing`() {
        val connection = StoredProfile("test", "Test Server", ConnectionKind.TEST_MOCK.name)
            .toConnectionOrNull(token = "separate-credential")

        requireNotNull(connection)
        assertEquals(ConnectionKind.TEST_MOCK, connection.config.kind)
        assertEquals(HCloudConnectionConfig.TEST_MOCK_BASE_URL, connection.config.baseUrl)
    }
}
