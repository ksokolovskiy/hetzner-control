package com.hcloudcontrol.ui.state

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.network.ObjectStorageRequestException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/** Classification never retains raw exception/provider messages, headers or response bodies. */
fun sectionReadFailure(error: Exception): SectionReadError {
    if (error is CancellationException) throw error
    val status = when (error) {
        is HCloudApiException -> error.failure.httpStatus
        is ObjectStorageRequestException -> error.httpStatus
        else -> null
    }
    return when {
        status == 401 -> SectionReadError("authentication")
        status == 403 -> SectionReadError("denied", permissionDenied = true)
        status == 404 -> SectionReadError("missing")
        error is IOException -> SectionReadError("offline")
        else -> SectionReadError("read_failed")
    }
}
