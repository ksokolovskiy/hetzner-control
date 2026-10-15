package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import com.hcloudcontrol.core.security.ObjectStorageConnection
import com.hcloudcontrol.core.security.ObjectStorageCredentialStore
import com.hcloudcontrol.operations.OperationContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class S3InventoryReadTest {
    private class Store : ObjectStorageCredentialStore {
        var active = "original"
        var switchDuringLookup = false
        val credentials = ObjectStorageCredentials("synthetic-access", "synthetic-secret", ObjectStorageLocation.FALKENSTEIN)
        override fun connections() = listOf(ObjectStorageConnection("original", "Fixture", ObjectStorageLocation.FALKENSTEIN))
        override fun activeConnectionId(): String = active
        override fun activeCredentials(): ObjectStorageCredentials {
            if (switchDuringLookup) active = "other"
            return credentials
        }
        override fun selectConnection(connectionId: String) = error("Inventory cannot select a profile")
        override fun saveConnection(displayName: String, location: ObjectStorageLocation, accessKey: String, secretKey: String): ObjectStorageConnection = error("Inventory cannot save credentials")
        override fun removeConnection(connectionId: String) = error("Inventory cannot remove a profile")
    }
    private val context = OperationContext("original", "https://fsn1.your-objectstorage.com", OperationContext.Environment.S3)
    @Test fun `selected profile change or endpoint mismatch cannot issue even an inventory request`() = runBlocking {
        var factories = 0
        val factory: (ObjectStorageCredentials) -> ObjectStorageRepository = { factories++; error("Wrong context reached repository") }
        assertTrue(runCatching { readS3Inventory(Store().apply { switchDuringLookup = true }, context, factory) }.isFailure)
        assertTrue(runCatching { readS3Inventory(Store(), context.copy(endpoint = "https://nbg1.your-objectstorage.com"), factory) }.isFailure)
        assertEquals(0, factories)
    }
    @Test fun `one verified inventory read pins transient credentials and never offers a write`() = runBlocking {
        val store = Store()
        var reads = 0
        val buckets = readS3Inventory(store, context) { pinned ->
            assertSame(store.credentials, pinned)
            object : ObjectStorageRepository {
                override suspend fun listBuckets(): List<ObjectStorageBucket> { reads++; return listOf(ObjectStorageBucket("fixture-bucket")) }
                override suspend fun createBucket(name: String) = error("Inventory cannot create resources")
                override suspend fun deleteBucket(name: String) = error("Inventory cannot delete resources")
            }
        }
        assertEquals(1, reads)
        assertEquals(listOf("fixture-bucket"), buckets.map { it.name })
    }
}
