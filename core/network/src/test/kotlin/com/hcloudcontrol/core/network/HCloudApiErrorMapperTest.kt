package com.hcloudcontrol.core.network

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class HCloudApiErrorMapperTest {
    @Test
    fun `mapped HTTP failure keeps raw response details out of exception text`() {
        val token = "token-should-never-reach-ui-or-logcat"
        val rootPassword = "one-time-root-password"
        val requestBody = "{\\\"name\\\":\\\"private-server-name\\\"}"
        val response = Response.error<Any>(
            400,
            """
            {
              "error": {
                "code": "invalid_input",
                "message": "Authorization: Bearer $token; root_password=$rootPassword; request=$requestBody",
                "details": { "fields": [{ "name": "server_type" }] }
              }
            }
            """.trimIndent().toResponseBody("application/json".toMediaType()),
        )

        val mapped = HCloudApiErrorMapper.from(HttpException(response))

        assertEquals(400, mapped.failure.httpStatus)
        assertEquals("invalid_input", mapped.failure.code)
        assertEquals(listOf("server_type"), mapped.failure.fieldNames)
        assertFalse(mapped.failure.apiMessage!!.contains(token))
        assertFalse(mapped.failure.apiMessage!!.contains(rootPassword))
        assertFalse(mapped.failure.apiMessage!!.contains(requestBody))
        assertFalse(mapped.message!!.contains(token))
        assertFalse(mapped.message!!.contains(rootPassword))
        assertFalse(mapped.message!!.contains(requestBody))
        assertEquals("Hetzner Cloud API request failed (HTTP 400, invalid_input)", mapped.message)
    }
}
