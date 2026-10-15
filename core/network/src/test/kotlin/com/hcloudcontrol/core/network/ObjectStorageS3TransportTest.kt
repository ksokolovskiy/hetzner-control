package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.ObjectStorageCredentials
import com.hcloudcontrol.core.domain.model.ObjectStorageLocation
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class ObjectStorageS3TransportTest {
    private fun repository(server: MockWebServer) = ObjectStorageS3Repository(
        { ObjectStorageCredentials("fixture-access", "fixture-secret", ObjectStorageLocation.FALKENSTEIN) },
        OkHttpClient.Builder().addInterceptor { chain ->
            chain.proceed(chain.request().newBuilder().url(server.url(chain.request().url.encodedPath)).build())
        }.build(),
    )

    @Test fun `bucket create sends valid empty PUT body`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(200))
            repository(server).createBucket("fixture-bucket")
            val request = server.takeRequest()
            assertEquals("PUT", request.method)
            assertEquals("/fixture-bucket", request.path)
            assertEquals(0L, request.bodySize)
        }
    }

    @Test fun `provider denial exposes only vetted HTTP status without raw body`() = runBlocking {
        MockWebServer().use { server ->
            server.start(); server.enqueue(MockResponse().setResponseCode(403).setBody("<Error>DO_NOT_ECHO_RAW_BODY</Error>"))
            val error = runCatching { repository(server).createBucket("fixture-bucket") }.exceptionOrNull()
            assertTrue(error is ObjectStorageRequestException)
            assertEquals(403, (error as ObjectStorageRequestException).httpStatus)
            assertFalse(error.message.orEmpty().contains("DO_NOT_ECHO_RAW_BODY"))
            assertFalse(error.message.orEmpty().contains("fixture-secret"))
            assertEquals(1, server.requestCount)
        }
    }
}
