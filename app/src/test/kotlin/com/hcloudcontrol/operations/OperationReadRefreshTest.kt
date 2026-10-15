package com.hcloudcontrol.operations

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.async
import org.junit.Assert.*
import org.junit.Test

class OperationReadRefreshTest {
    private fun session(): OperationSession {
        val store = object : OperationStore {
            var records = emptyList<PersistedOperation>()
            override fun load() = records
            override fun save(records: List<PersistedOperation>) { this.records = records }
        }
        val context = OperationContext("synthetic", "https://api.hetzner.cloud/v1/", OperationContext.Environment.CLOUD)
        val intent = OperationIntent("original", ResourceIdentity(context, "server", "42"), "enable_rescue", 1)
        return OperationSession(context, OperationCoordinator(store, now = { 1L }), intent, 1)
    }

    @Test fun `failed recovery refresh stays successful and verified retry clears warning preserving action`() = runBlocking {
        val session = session()
        session.begin(true, session.context)
        session.accepted(com.hcloudcontrol.core.domain.model.CloudAction(id = 88, status = "success", command = "enable_rescue", progress = 100))
        session.succeed()
        assertFalse(refreshOperationResource(session) { error("synthetic read failure") })
        assertEquals(OperationStage.SUCCEEDED, session.record?.stage)
        assertTrue(session.record!!.refreshFailed)
        assertTrue(refreshOperationResource(session) {})
        assertFalse(session.record!!.refreshFailed)
        assertEquals(88L, session.record?.providerActionId)
    }

    @Test fun `accepted result cannot become successful from refresh callback`() = runBlocking {
        val session = session()
        session.begin(true, session.context)
        var reads = 0
        assertFalse(refreshOperationResource(session) { reads++ })
        assertEquals(0, reads)
        assertEquals(OperationStage.SENDING, session.record?.stage)
    }

    @Test fun `cancelled refresh does not clear an existing warning or alter successful outcome`() = runBlocking {
        val session = session()
        session.begin(true, session.context)
        session.succeed()
        session.refreshFailed()
        try {
            refreshOperationResource(session) { throw kotlinx.coroutines.CancellationException("synthetic cancellation") }
            fail("Cancellation must propagate")
        } catch (_: kotlinx.coroutines.CancellationException) {
            assertEquals(OperationStage.SUCCEEDED, session.record?.stage)
            assertTrue(session.record!!.refreshFailed)
        }
    }

    @Test fun `switched owner before refresh prevents read callback and retains original successful action`() = runBlocking {
        val session = session()
        session.begin(true, session.context)
        session.accepted(com.hcloudcontrol.core.domain.model.CloudAction(88, "success"))
        session.succeed()
        val current = session.context!!.copy(connectionId = "other-project")
        var reads = 0
        var callbacks = 0
        assertFalse(refreshOperationResource(session) {
            check(current == session.context)
            reads++
            callbacks++
        })
        assertEquals(0, reads)
        assertEquals(0, callbacks)
        assertEquals("synthetic", session.record!!.intent.resource.context.connectionId)
        assertEquals(88L, session.record!!.providerActionId)
        assertEquals(OperationStage.SUCCEEDED, session.record!!.stage)
        assertTrue(session.record!!.refreshFailed)
    }

    @Test fun `owner switch during suspended read prevents stale UI callback without replaying mutation`() = runBlocking<Unit> {
        val session = session()
        session.begin(true, session.context)
        session.accepted(com.hcloudcontrol.core.domain.model.CloudAction(88, "success"))
        session.succeed()
        var current = session.context!!
        val reading = kotlinx.coroutines.CompletableDeferred<Unit>()
        val finishRead = kotlinx.coroutines.CompletableDeferred<Unit>()
        var callbacks = 0
        var reads = 0
        val refresh = async {
            refreshOperationResource(session) {
                check(current == session.context)
                reads++
                reading.complete(Unit)
                finishRead.await()
                check(current == session.context)
                callbacks++
            }
        }
        reading.await()
        current = current.copy(connectionId = "other-project")
        finishRead.complete(Unit)
        assertFalse(refresh.await())
        assertEquals(1, reads)
        assertEquals(0, callbacks)
        assertEquals(88L, session.record!!.providerActionId)
        assertEquals(OperationStage.SUCCEEDED, session.record!!.stage)
        assertTrue(session.record!!.refreshFailed)
        assertThrows(IllegalStateException::class.java) { session.begin(true, session.context) }
    }
}
