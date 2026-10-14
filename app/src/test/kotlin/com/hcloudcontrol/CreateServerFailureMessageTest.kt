package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudApiFailure
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateServerFailureMessageTest {
    @Test
    fun `create failure UI allows only safe error metadata`() {
        val token = "token-must-not-be-rendered"
        val rootPassword = "one-time-root-password"
        val requestBody = "{\"name\":\"private-server-name\"}"
        val message = createServerFailureMessage(
            HCloudApiException(
                HCloudApiFailure(
                    httpStatus = 422,
                    code = "invalid_input",
                    apiMessage = "Authorization: Bearer $token; root_password=$rootPassword; request=$requestBody",
                    fieldNames = listOf("server_type", "Authorization: Bearer $token", rootPassword),
                ),
            ),
        )

        assertTrue(message.contains("HTTP 422, код: invalid_input"))
        assertTrue(message.contains("server_type"))
        assertFalse(message.contains(token))
        assertFalse(message.contains(rootPassword))
        assertFalse(message.contains(requestBody))
        assertFalse(message.contains("Authorization"))
    }

    @Test
    fun `unknown server error fields are omitted`() {
        assertTrue(safeCreateServerFieldNames(listOf("name", "ssh_keys", "unknown", "name")) == listOf("name", "ssh_keys"))
    }

    @Test
    fun `generic failure presentation renders only structured safe metadata`() {
        val token = "do-not-render-token"
        val message = safeApiFailureMessage(
            HCloudApiException(
                HCloudApiFailure(
                    httpStatus = 403,
                    code = "forbidden",
                    apiMessage = "Authorization: Bearer $token",
                    fieldNames = listOf("api_token"),
                    requestId = "request-403",
                ),
            ),
            fallback = "Could not load servers",
        )

        assertTrue(message.contains("Could not load servers"))
        assertTrue(message.contains("HTTP 403"))
        assertTrue(message.contains("code: forbidden"))
        assertTrue(message.contains("request ID: request-403"))
        assertFalse(message.contains(token))
        assertFalse(message.contains("api_token"))
        assertFalse(message.contains("Authorization"))
    }
}
