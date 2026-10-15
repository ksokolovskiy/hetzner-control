package com.hcloudcontrol.core.network

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class S3ApiDiagnosticsTest {
    @Test fun `diagnostics retain status but no bucket query signature or XML`() {
        MockWebServer().use { server ->
            server.start()
            server.enqueue(MockResponse().setResponseCode(403).setBody("sensitive-body"))
            val records = mutableListOf<HCloudApiDiagnostic>()
            val client = OkHttpClient.Builder().addInterceptor(S3ApiDiagnosticsInterceptor({ records += it })).build()
            client.newCall(Request.Builder().url(server.url("/private-bucket?secret-query=value"))
                .header("Authorization", "sensitive-signature").build()).execute().use { assertEquals(403, it.code) }
            val record = records.single()
            assertEquals("GET", record.method)
            assertEquals("/s3/{bucket}", record.redactedPath)
            assertEquals(403, record.httpStatus)
            for (secret in listOf("private-bucket", "secret-query", "sensitive-signature", "sensitive-body"))
                assertFalse(record.toString().contains(secret))
        }
    }

    @Test fun `diagnostic failure never breaks a successful response`() {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(200))
            val client = OkHttpClient.Builder().addInterceptor(S3ApiDiagnosticsInterceptor({ error("unavailable logger") })).build()
            client.newCall(Request.Builder().url(server.url("/")).build()).execute().use { assertEquals(200, it.code) }
        }
    }
}
