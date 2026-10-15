package com.hcloudcontrol.operations

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class IndependentResourceRefreshTest {
    @Test fun `SSH and S3 successful retry clear original warning without changing identity`() = runBlocking {
        listOf("ssh_key" to OperationContext.Environment.CLOUD,
            "s3_bucket" to OperationContext.Environment.S3).forEach { (type, environment) ->
            val store = object : OperationStore {
                var saved = emptyList<PersistedOperation>()
                override fun load() = saved
                override fun save(records: List<PersistedOperation>) { saved = records }
            }
            val context = OperationContext("original-profile", "https://example.invalid", environment)
            val intent = OperationIntent("original-$type", ResourceIdentity(context, type, "original-id"), "create", 0)
            val coordinator = OperationCoordinator(store, now = { 1L })
            val session = OperationSession(context, coordinator, intent, 0)
            session.begin(true, context)
            session.succeed()
            session.refreshFailed()
            val original = checkNotNull(session.record).intent
            var reads = 0
            assertTrue(refreshOperationResource(session) { reads++ })
            assertEquals(1, reads)
            assertEquals(original, session.record?.intent)
            assertEquals(OperationStage.SUCCEEDED, session.record?.stage)
            assertFalse(checkNotNull(session.record).refreshFailed)
            assertThrows(IllegalStateException::class.java) { session.begin(true, context) }
        }
    }
}
