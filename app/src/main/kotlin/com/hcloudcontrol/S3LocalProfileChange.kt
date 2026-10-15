package com.hcloudcontrol

import com.hcloudcontrol.core.security.ObjectStorageConnection
import com.hcloudcontrol.core.security.ObjectStorageCredentialStore

internal sealed interface S3LocalProfileChange {
    data class Select(val connectionId: String) : S3LocalProfileChange
    data class Remove(val connectionId: String) : S3LocalProfileChange
}
internal data class S3ProfileSnapshot(val connections: List<ObjectStorageConnection>, val activeId: String?)
internal sealed interface S3ProfileChangeResult {
    data class Applied(val snapshot: S3ProfileSnapshot) : S3ProfileChangeResult
    data object Failed : S3ProfileChangeResult
}

/** Local protected-store changes only. The caller publishes no new UI state until checked success. */
internal fun changeS3Profile(store: ObjectStorageCredentialStore, change: S3LocalProfileChange): S3ProfileChangeResult = try {
    when (change) {
        is S3LocalProfileChange.Select -> store.selectConnection(change.connectionId)
        is S3LocalProfileChange.Remove -> store.removeConnection(change.connectionId)
    }
    val snapshot = S3ProfileSnapshot(store.connections(), store.activeConnectionId())
    check(snapshot.activeId == null || snapshot.connections.any { it.id == snapshot.activeId })
    when (change) {
        is S3LocalProfileChange.Select -> check(snapshot.activeId == change.connectionId)
        is S3LocalProfileChange.Remove -> check(snapshot.connections.none { it.id == change.connectionId } && snapshot.activeId != change.connectionId)
    }
    S3ProfileChangeResult.Applied(snapshot)
} catch (_: Exception) {
    // No exception message, credentials, response payload, or journal mutation crosses this boundary.
    S3ProfileChangeResult.Failed
}
