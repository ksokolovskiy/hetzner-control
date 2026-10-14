package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class TestMockBaseUrlInterceptorTest {
    @Test
    fun rewritesLocationsToTestMockV1RootWithoutDuplicatingV1() {
        val interceptor = BaseUrlInterceptor(HCloudConnectionConfig::testMock)
        val request = Request.Builder().url("https://api.hetzner.cloud/v1/locations?page=2".toHttpUrl()).build()
        var forwarded: Request? = null
        try {
            interceptor.intercept(object : okhttp3.Interceptor.Chain {
                override fun request() = request
                override fun proceed(request: Request): okhttp3.Response { forwarded = request; throw Stop }
                override fun connection() = null
                override fun call(): okhttp3.Call = throw UnsupportedOperationException()
                override fun connectTimeoutMillis() = 0
                override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun readTimeoutMillis() = 0
                override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
                override fun writeTimeoutMillis() = 0
                override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            })
        } catch (_: Stop) { }
        assertEquals("https://tst.pumpkin.co.il/hcloud-mock/v1/locations?page=2", forwarded?.url.toString())
    }
    private object Stop : RuntimeException()
}
