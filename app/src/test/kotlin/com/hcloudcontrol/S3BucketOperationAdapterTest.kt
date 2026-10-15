package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.ObjectStorageBucket
import com.hcloudcontrol.core.domain.repository.ObjectStorageRepository
import com.hcloudcontrol.core.network.ObjectStorageBucketNotEmptyException
import com.hcloudcontrol.core.network.ObjectStorageRequestException
import com.hcloudcontrol.operations.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class S3BucketOperationAdapterTest {
    private val context = OperationContext("s3-profile", "https://fsn1.your-objectstorage.com", OperationContext.Environment.S3)
    private class Store : OperationStore {
        var records = emptyList<PersistedOperation>()
        override fun load() = records
        override fun save(records: List<PersistedOperation>) { this.records = records }
    }
    private class Fake : ObjectStorageRepository {
        var buckets = emptyList<ObjectStorageBucket>()
        var writes = 0
        var error: Throwable? = null
        override suspend fun listBuckets() = buckets
        override suspend fun createBucket(name: String) { writes++; error?.let { throw it }; buckets = buckets + ObjectStorageBucket(name) }
        override suspend fun deleteBucket(name: String) { writes++; error?.let { throw it }; buckets = buckets.filterNot { it.name == name } }
    }
    private fun review(action: S3BucketAction) = OperationReview(OperationIntent("attempt", ResourceIdentity(context, "s3_bucket", "fixture-bucket"), action.wireName, 0), 100, true)

    @Test fun `successful create is authoritative and duplicate proof cannot send twice`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val fake = Fake()
        val adapter = S3BucketOperationAdapter(coordinator, fake, context, { context }, { true })
        assertEquals(OperationStage.SUCCEEDED, adapter.submit(review(S3BucketAction.CREATE)).stage)
        runCatching { adapter.submit(review(S3BucketAction.CREATE)) }
        assertEquals(1, fake.writes)
    }

    @Test fun `lost create response remains unknown even if matching bucket appears`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val fake = Fake().apply { error = IOException("synthetic") }
        val adapter = S3BucketOperationAdapter(coordinator, fake, context, { context }, { true })
        val unknown = adapter.submit(review(S3BucketAction.CREATE))
        fake.buckets = listOf(ObjectStorageBucket("fixture-bucket"))
        assertEquals(OperationStage.OUTCOME_UNKNOWN, adapter.reconcile(unknown).stage)
        assertEquals(1, fake.writes)
    }

    @Test fun `lost delete reconciles only absence in original profile`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val fake = Fake().apply { buckets = listOf(ObjectStorageBucket("fixture-bucket")); error = IOException("synthetic") }
        var selected: OperationContext? = context
        val adapter = S3BucketOperationAdapter(coordinator, fake, context, { selected }, { true })
        val unknown = adapter.submit(review(S3BucketAction.DELETE))
        selected = context.copy(connectionId = "other")
        assertTrue(runCatching { adapter.reconcile(unknown) }.isFailure)
        selected = context; fake.buckets = emptyList()
        assertEquals(OperationStage.SUCCEEDED, adapter.reconcile(unknown).stage)
        assertEquals(1, fake.writes)
    }

    @Test fun `nonempty rejection never triggers another mutation or object purge`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val fake = Fake().apply { buckets = listOf(ObjectStorageBucket("fixture-bucket")); error = ObjectStorageBucketNotEmptyException() }
        val adapter = S3BucketOperationAdapter(coordinator, fake, context, { context }, { true })
        assertEquals(OperationStage.FAILED, adapter.submit(review(S3BucketAction.DELETE)).stage)
        assertEquals(1, fake.writes)
        assertEquals(1, fake.buckets.size)
    }

    @Test fun `revoked app gate never reaches provider write`() = runBlocking {
        val fake = Fake()
        val coordinator = OperationCoordinator(Store()) { 100 }
        val adapter = S3BucketOperationAdapter(coordinator, fake, context, { context }, { false })
        assertTrue(runCatching { adapter.submit(review(S3BucketAction.CREATE)) }.isFailure)
        assertEquals(0, fake.writes)
        assertTrue(coordinator.all().isEmpty())
    }

    @Test fun `gate revoked during presentation handoff blocks actual write`() = runBlocking {
        val fake = Fake()
        val coordinator = OperationCoordinator(Store()) { 100 }
        var allowed = true
        val adapter = S3BucketOperationAdapter(coordinator, fake, context, { context }, { allowed })
        val result = adapter.submit(review(S3BucketAction.CREATE)) { allowed = false }
        assertEquals(0, fake.writes)
        assertEquals(OperationStage.OUTCOME_UNKNOWN, result.stage)
    }

    @Test fun `typed provider denial is distinct from unknown network failure`() = runBlocking {
        val fake = Fake().apply { error = ObjectStorageRequestException("safe", 403) }
        val coordinator = OperationCoordinator(Store()) { 100 }
        val adapter = S3BucketOperationAdapter(coordinator, fake, context, { context }, { true })
        val result = adapter.submit(review(S3BucketAction.CREATE))
        assertEquals(OperationStage.FAILED, result.stage)
        assertEquals(OperationDiagnostic.Code.PROVIDER_DENIED, result.diagnostic?.code)
        assertEquals(403, result.diagnostic?.httpStatus)
    }
}
