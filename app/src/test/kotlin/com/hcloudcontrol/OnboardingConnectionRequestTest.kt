package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OnboardingConnectionRequestTest {
    @Test
    fun `test server CTA accepts a token without a project-name field`() {
        val request = onboardingConnectionRequest(
            connectionKind = ConnectionKind.TEST_MOCK,
            projectName = "",
            testProfileName = "Temporary Test Server",
            token = "test-token",
        )

        requireNotNull(request)
        assertEquals("Temporary Test Server", request.displayName)
        assertEquals("test-token", request.token)
        assertEquals(HCloudConnectionConfig.testMock(), request.config)
    }

    @Test
    fun `CTA rejects blank credentials before persistence`() {
        assertNull(
            onboardingConnectionRequest(
                connectionKind = ConnectionKind.TEST_MOCK,
                projectName = "",
                testProfileName = "Temporary Test Server",
                token = "   ",
            ),
        )
    }
}
