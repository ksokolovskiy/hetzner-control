package com.hcloudcontrol

import com.hcloudcontrol.operations.*
import org.junit.Assert.*
import org.junit.Test

class S3PublicDraftTest {
    private val context = OperationContext("fixture", "https://fsn1.your-objectstorage.com", OperationContext.Environment.S3)
    @Test fun `public draft validates owner endpoint and environment from saved snapshot itself`() {
        val draft = S3PublicDraft("search", "fixture-bucket")
        val saved = draft.snapshot(context)
        assertEquals(draft, S3PublicDraft.restore(context, saved))
        assertEquals(S3PublicDraft(), S3PublicDraft.restore(context.copy(connectionId = "other"), saved))
        assertEquals(S3PublicDraft(), S3PublicDraft.restore(context.copy(endpoint = "https://nbg1.your-objectstorage.com"), saved))
        assertEquals(S3PublicDraft(), S3PublicDraft.restore(context.copy(environment = OperationContext.Environment.CLOUD), saved))
        assertEquals(S3PublicDraft(), S3PublicDraft.restore(null, saved))
        assertEquals(6, saved.size)
    }
    @Test fun `late status read must not reopen dismissed or another profile result`() {
        assertTrue(shouldPublishS3Result("intent", "intent", context, context))
        assertFalse(shouldPublishS3Result(null, "intent", context, context))
        assertFalse(shouldPublishS3Result("new-intent", "intent", context, context))
        assertFalse(shouldPublishS3Result("intent", "intent", context.copy(connectionId = "other"), context))
    }

    @Test fun `late successful refresh cannot clear a newer same profile result latch`() {
        var awaitingRefresh: String? = "new-intent"
        var inventoryRefreshes = 0
        if (shouldPublishS3Result("new-intent", "old-intent", context, context)) {
            awaitingRefresh = null
            inventoryRefreshes++
        }
        assertEquals("new-intent", awaitingRefresh)
        assertEquals(0, inventoryRefreshes)
    }
    @Test fun `removed connection retains original pending identity and never rebinds same host new UUID`() {
        val record = PersistedOperation(OperationIntent("intent", ResourceIdentity(context, "s3_bucket", "fixture-bucket"), "create_bucket", 0),
            OperationStage.OUTCOME_UNKNOWN, 1)
        assertEquals(listOf(record), unavailableS3Operations(listOf(record), emptySet()))
        assertEquals(listOf(record), unavailableS3Operations(listOf(record), setOf(context.copy(connectionId = "new-uuid"))))
        assertTrue(unavailableS3Operations(listOf(record), setOf(context)).isEmpty())
        assertTrue(unavailableS3Operations(listOf(record.copy(stage = OperationStage.SUCCEEDED)), emptySet()).isEmpty())
        assertEquals("fixture", unavailableS3Operations(listOf(record), emptySet()).single().intent.resource.context.connectionId)
    }
    @Test fun `all active profile pending buckets remain discoverable after closing latest result`() {
        val first = PersistedOperation(OperationIntent("first", ResourceIdentity(context, "s3_bucket", "first-bucket"), "create_bucket", 0), OperationStage.OUTCOME_UNKNOWN, 1)
        val second = first.copy(intent = first.intent.copy(intentId = "second", resource = first.intent.resource.copy(resourceId = "second-bucket")), updatedAtMillis = 2)
        val completed = first.copy(intent = first.intent.copy(intentId = "completed"), stage = OperationStage.SUCCEEDED)
        assertEquals(listOf("second", "first"), pendingS3Operations(listOf(first, second, completed)).map { it.intent.intentId })
        assertTrue(unavailableS3Operations(listOf(first, second), setOf(context)).isEmpty())
    }
}
