package com.hcloudcontrol.operations

import kotlinx.coroutines.CancellationException

/** A scheduled UI refresh is not read evidence. Clear the warning only after this read completes. */
internal suspend fun refreshOperationResource(session: OperationSession, read: suspend () -> Unit): Boolean {
    if (session.record?.stage != OperationStage.SUCCEEDED) return false
    return try {
        read()
        session.refreshSucceeded()
        true
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Throwable) {
        session.refreshFailed()
        false
    }
}
