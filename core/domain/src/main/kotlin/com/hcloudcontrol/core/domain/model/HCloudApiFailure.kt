package com.hcloudcontrol.core.domain.model

/**
 * Safe, structured information returned by a failed Hetzner Cloud API request.
 *
 * This deliberately excludes request/response bodies, authorization data, and generated
 * credentials. Consumers can show [code] and [fieldNames] without inspecting an HTTP error.
 */
data class HCloudApiFailure(
    val httpStatus: Int,
    val code: String?,
    val apiMessage: String?,
    val fieldNames: List<String>,
    /** A bounded opaque server correlation id, if one was supplied. */
    val requestId: String? = null,
)

/** A non-2xx Cloud API response represented without retaining the original HTTP exception. */
class HCloudApiException(val failure: HCloudApiFailure) : RuntimeException(
    "Hetzner Cloud API request failed (HTTP ${failure.httpStatus}${failure.code?.let { ", $it" }.orEmpty()})",
)
