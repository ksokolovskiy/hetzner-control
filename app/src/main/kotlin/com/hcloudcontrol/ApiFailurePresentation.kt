package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.HCloudApiException

/**
 * Adds only structured, already-sanitized API metadata to a screen's localized fallback.
 * Raw exception messages, HTTP bodies, diagnostics and API messages are deliberately ignored.
 */
internal fun safeApiFailureMessage(throwable: Throwable, fallback: String): String {
    val failure = (throwable as? HCloudApiException)?.failure ?: return fallback
    return buildString {
        append(fallback)
        append(" (HTTP ")
        append(failure.httpStatus)
        failure.code?.takeIf { it.isNotBlank() }?.let {
            append(", code: ")
            append(it)
        }
        failure.requestId?.takeIf { it.isNotBlank() }?.let {
            append(", request ID: ")
            append(it)
        }
        append(")")
    }
}
