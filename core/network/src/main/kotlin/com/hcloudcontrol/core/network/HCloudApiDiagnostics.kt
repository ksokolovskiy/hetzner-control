package com.hcloudcontrol.core.network

import android.util.Log
import com.hcloudcontrol.core.domain.model.HCloudApiFailure
import java.io.IOException
import okhttp3.Interceptor
import okhttp3.Response

private const val MAX_DIAGNOSTIC_ERROR_BODY_BYTES = 64L * 1024L

/** Safe summary of one API attempt. It contains no headers, query values, or HTTP bodies. */
internal data class HCloudApiDiagnostic(
    val method: String,
    val redactedPath: String,
    val elapsedMillis: Long,
    val httpStatus: Int?,
    val apiCode: String?,
    val apiMessage: String?,
    val fieldNames: List<String>,
    val networkFailure: Boolean,
    /** Safe, bounded server correlation ID when the response supplied one. */
    val requestId: String? = null,
)

internal fun interface HCloudApiDiagnosticsLogger {
    fun log(diagnostic: HCloudApiDiagnostic)
}

/**
 * Central diagnostics boundary for every Retrofit call made by [HCloudApiFactory].
 *
 * This interceptor must be observational: an OkHttp interceptor runs on OkHttp's dispatcher,
 * where throwing an application [RuntimeException] can bypass Retrofit's suspend continuation
 * and terminate the process.  Retrofit owns delivery of non-2xx responses to the calling
 * coroutine; repository boundaries then turn its [retrofit2.HttpException] into the typed,
 * safe domain failure used by the UI.  It intentionally never reads request bodies and only
 * peeks at error bodies for the documented Hetzner error envelope.
 */
internal class HCloudApiDiagnosticsInterceptor(
    private val logger: HCloudApiDiagnosticsLogger = AndroidHCloudApiDiagnosticsLogger,
    private val nowNanos: () -> Long = System::nanoTime,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val path = redactHetznerPath(request.url.encodedPath)
        val startedAt = nowNanos()
        try {
            val response = chain.proceed(request)
            val elapsed = elapsedMillis(startedAt)
            if (response.isSuccessful) {
                logger.log(HCloudApiDiagnostic(method, path, elapsed, response.code, null, null, emptyList(), false, response.safeRequestId()))
                return response
            }

            val failure = HCloudErrorParser.parse(
                response.code,
                response.peekBody(MAX_DIAGNOSTIC_ERROR_BODY_BYTES).string(),
            ).copy(requestId = response.safeRequestId())
            logger.log(failure.toDiagnostic(method, path, elapsed, response.safeRequestId()))
            // Do not close or throw here. Retrofit needs this response to resume the caller
            // with HttpException, which safeHCloudApiCall maps on the caller's coroutine.
            return response
        } catch (exception: MissingApiTokenException) {
            // This is a local configuration failure, not an API attempt. Do not turn it into a
            // misleading network diagnostic and never fabricate a request record.
            throw exception
        } catch (exception: IOException) {
            logger.log(HCloudApiDiagnostic(method, path, elapsedMillis(startedAt), null, null, null, emptyList(), true))
            throw exception
        }
    }

    private fun elapsedMillis(startedAt: Long): Long = ((nowNanos() - startedAt) / NANOS_PER_MILLISECOND).coerceAtLeast(0)

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}

/** Passive Storage Box diagnostics; safe error mapping happens at the Retrofit repository boundary. */
internal class StorageBoxApiDiagnosticsInterceptor(
    private val logger: HCloudApiDiagnosticsLogger = AndroidHCloudApiDiagnosticsLogger,
    private val nowNanos: () -> Long = System::nanoTime,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val method = request.method
        val path = redactHetznerPath(request.url.encodedPath)
        val startedAt = nowNanos()
        try {
            val response = chain.proceed(request)
            val elapsed = elapsedMillis(startedAt)
            if (response.isSuccessful) {
                logger.log(HCloudApiDiagnostic(method, path, elapsed, response.code, null, null, emptyList(), false, response.safeRequestId()))
            } else {
                val failure = HCloudErrorParser.parse(
                    response.code,
                    response.peekBody(MAX_DIAGNOSTIC_ERROR_BODY_BYTES).string(),
                )
                logger.log(failure.toDiagnostic(method, path, elapsed, response.safeRequestId()))
            }
            return response
        } catch (exception: MissingStorageBoxApiTokenException) {
            // See the Cloud equivalent above: no request reached the network.
            throw exception
        } catch (exception: IOException) {
            logger.log(HCloudApiDiagnostic(method, path, elapsedMillis(startedAt), null, null, null, emptyList(), true))
            throw exception
        }
    }

    private fun elapsedMillis(startedAt: Long): Long = ((nowNanos() - startedAt) / 1_000_000L).coerceAtLeast(0)
}

/** Sanitizes path values and never includes query values or any header/body material. */
internal fun redactHetznerPath(encodedPath: String): String = encodedPath
    .split('/')
    .filter(String::isNotEmpty)
    .joinToString(prefix = "/", separator = "/") { segment ->
        when {
            segment.matches(Regex("\\d+")) -> "{id}"
            segment in SAFE_PATH_SEGMENTS -> segment
            else -> "{value}"
        }
    }

private val SAFE_PATH_SEGMENTS = setOf(
    "v1", "profile", "servers", "server_types", "images", "locations", "ssh_keys", "pricing",
    "volumes", "primary_ips", "floating_ips", "firewalls", "networks", "load_balancers", "placement_groups",
    "certificates", "zones", "rrsets", "storage_boxes", "snapshots", "subaccounts", "folders", "actions",
    // These are documented static Cloud endpoint segments, not user values. Keeping them makes
    // diagnostics useful for the read-only resource screens without ever retaining an ID/query.
    "load_balancer_types", "members", "metrics", "apply_to_resources", "remove_from_resources",
    "poweron", "poweroff", "shutdown", "reboot", "reset", "change_type", "enable_backup", "disable_backup",
    "create_image", "attach", "detach", "resize", "assign", "unassign", "change_dns_ptr", "change_protection",
)

/** Correlation headers are server-controlled; only retain a bounded opaque-safe identifier. */
internal fun Response.safeRequestId(): String? = header("X-Request-ID")
    ?.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,128}")) }

internal fun HCloudApiFailure.toDiagnostic(method: String, path: String, elapsed: Long, requestId: String?) = HCloudApiDiagnostic(
    method = method,
    redactedPath = path,
    elapsedMillis = elapsed,
    httpStatus = httpStatus,
    apiCode = code,
    apiMessage = code?.let { "Hetzner API error code: $it" } ?: "Hetzner API returned an error",
    fieldNames = emptyList(),
    networkFailure = false,
    requestId = requestId,
)

internal object AndroidHCloudApiDiagnosticsLogger : HCloudApiDiagnosticsLogger {
    override fun log(diagnostic: HCloudApiDiagnostic) {
        // Android's unit-test stub throws; diagnostics must never break an API request.
        runCatching {
            Log.i(
                "HCloudApi",
                "method=${diagnostic.method} path=${diagnostic.redactedPath} elapsed_ms=${diagnostic.elapsedMillis} " +
                    "status=${diagnostic.httpStatus ?: "network"} code=${diagnostic.apiCode ?: "-"} " +
                    "message=${diagnostic.apiMessage ?: "-"} fields=- request_id=${diagnostic.requestId ?: "-"}",
            )
        }
    }
}
