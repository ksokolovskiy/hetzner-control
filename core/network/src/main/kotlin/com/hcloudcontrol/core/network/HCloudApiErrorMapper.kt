package com.hcloudcontrol.core.network

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudApiFailure
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import retrofit2.HttpException

/** Converts a Retrofit non-2xx response into a safe, UI-consumable domain exception. */
internal object HCloudApiErrorMapper {
    fun from(exception: HttpException): HCloudApiException {
        val body = runCatching { exception.response()?.errorBody()?.string() }.getOrNull()
        return fromResponse(exception.code(), body, exception.response()?.raw()?.safeRequestId())
    }

    fun fromResponse(httpStatus: Int, body: String?, requestId: String? = null): HCloudApiException = HCloudApiException(
        HCloudErrorParser.parse(httpStatus, body).copy(requestId = requestId),
    )
}

internal object HCloudErrorParser {
    private const val MAX_MESSAGE_LENGTH = 240
    private const val MAX_FIELD_COUNT = 20
    private val errorAdapter = Moshi.Builder()
        .addLast(KotlinJsonAdapterFactory())
        .build()
        .adapter(HCloudErrorResponseDto::class.java)
    private val safeFieldName = Regex("[A-Za-z0-9_.\\[\\]-]{1,80}")
    // Details field names are server-controlled. A reflected credential identifier must not
    // become a user-visible or diagnostic value even if it has harmless characters.
    private val sensitiveFieldName = Regex("(?i).*(token|password|secret|authorization|ssh|api[_-]?key).*" )
    private val authorization = Regex("(?i)authorization\\s*[:=]\\s*bearer\\s+[^\\s,;]+")
    private val bearer = Regex("(?i)bearer\\s+[A-Za-z0-9._~-]+")
    private val secretAssignment = Regex("(?i)(root_password|password|token|api[_-]?key)\\s*[:=]\\s*[^\\s,;]+")
    // Android's ICU regex implementation treats an unescaped `}` as a syntax error, whereas
    // the desktop/JVM implementation accepted the old expression. Escape both delimiters so
    // sanitising an API error can never take down an OkHttp dispatcher thread.
    private val requestPayload = Regex("(?i)request\\s*[:=]\\s*(\\{.*\\}|\\[.*\\]|[^\\s,;]+)")

    fun parse(httpStatus: Int, body: String?): HCloudApiFailure {
        val error = runCatching { body?.let(errorAdapter::fromJson)?.error }.getOrNull()
        return HCloudApiFailure(
            httpStatus = httpStatus,
            code = error?.code?.takeIf { it.matches(Regex("[a-z0-9_]{1,80}")) },
            apiMessage = error?.message?.let(::sanitizeMessage),
            fieldNames = error?.details?.fields.orEmpty()
                .mapNotNull { it.name?.takeIf(safeFieldName::matches) }
                .filterNot(sensitiveFieldName::matches)
                .distinct()
                .take(MAX_FIELD_COUNT),
        )
    }

    private fun sanitizeMessage(value: String): String? = value
        .replace(Regex("[\\r\\n\\t]+"), " ")
        .replace(authorization, "Authorization: Bearer [REDACTED]")
        .replace(bearer, "Bearer [REDACTED]")
        .replace(secretAssignment, "${'$'}1=[REDACTED]")
        .replace(requestPayload, "request=[REDACTED]")
        .trim()
        .take(MAX_MESSAGE_LENGTH)
        .takeIf(String::isNotEmpty)
}

internal suspend fun <T> safeHCloudApiCall(block: suspend () -> T): T = try {
    block()
} catch (exception: HttpException) {
    throw HCloudApiErrorMapper.from(exception)
}
