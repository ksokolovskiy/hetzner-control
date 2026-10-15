package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.ObjectStorageBucket
import com.hcloudcontrol.core.domain.model.ObjectStorageCredentials
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import com.hcloudcontrol.core.network.ObjectStorageRepositoryFactory
import com.hcloudcontrol.core.security.ObjectStorageCredentialStore
import com.hcloudcontrol.operations.OperationContext

/** Credentials exist only in this read's transient repository closure, never in a saved draft. */
internal suspend fun readS3Inventory(store: ObjectStorageCredentialStore, expected: OperationContext,
    factory: (ObjectStorageCredentials) -> ObjectStorageRepository = { credentials -> ObjectStorageRepositoryFactory.create { credentials } },
): List<ObjectStorageBucket> {
    require(expected.environment == OperationContext.Environment.S3)
    check(store.activeConnectionId() == expected.connectionId)
    val metadata = checkNotNull(store.connections().singleOrNull { it.id == expected.connectionId })
    check(expected.endpoint == "https://${metadata.location.host}")
    val credentials = checkNotNull(store.activeCredentials())
    // The store may have changed while resolving protected credentials. Never bind a new profile
    // or endpoint to an earlier loader's identity, including the same UUID with another location.
    check(store.activeConnectionId() == expected.connectionId && credentials.location == metadata.location)
    check(store.connections().singleOrNull { it.id == expected.connectionId }?.location == metadata.location)
    return factory(credentials).listBuckets()
}
