package com.hcloudcontrol.core.domain.repository

import com.hcloudcontrol.core.domain.model.ObjectStorageBucket

/** S3-compatible bucket lifecycle boundary; it never uses a Cloud REST bearer token. */
interface ObjectStorageRepository {
    suspend fun listBuckets(): List<ObjectStorageBucket>
    suspend fun createBucket(name: String)
    /** Caller must freshly verify emptiness; S3 refuses a non-empty bucket. */
    suspend fun deleteBucket(name: String)
}
