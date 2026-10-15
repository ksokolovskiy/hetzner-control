package com.hcloudcontrol.core.network

import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

/** Bucket names, query values, XML bodies and signed headers never enter diagnostics. */
internal class S3ApiDiagnosticsInterceptor(
    private val logger: HCloudApiDiagnosticsLogger = AndroidHCloudApiDiagnosticsLogger,
    private val nowNanos: () -> Long = System::nanoTime,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val started = nowNanos()
        val path = if (request.url.encodedPath == "/") "/s3" else "/s3/{bucket}"
        fun log(status: Int?, failed: Boolean, requestId: String? = null) {
            // Logging must not alter the success or failure of a provider request.
            runCatching { logger.log(HCloudApiDiagnostic(request.method, path,
                ((nowNanos() - started) / 1_000_000L).coerceAtLeast(0), status,
                null, null, emptyList(), failed, requestId)) }
        }
        return try {
            chain.proceed(request).also { log(it.code, false, it.safeRequestId()) }
        } catch (failure: IOException) {
            log(null, true)
            throw failure
        }
    }
}
