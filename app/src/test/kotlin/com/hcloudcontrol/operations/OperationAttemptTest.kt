package com.hcloudcontrol.operations

import org.junit.Assert.*
import org.junit.Test

class OperationAttemptTest {
    @Test fun `only explicit terminal close rotates attempt and keeps durable original result`() {
        val store = object : OperationStore {
            var records = emptyList<PersistedOperation>()
            override fun load() = records
            override fun save(records: List<PersistedOperation>) { this.records = records }
        }
        val context = OperationContext("one", "https://api.hetzner.cloud/v1/", OperationContext.Environment.CLOUD)
        val intent = OperationIntent("original", ResourceIdentity(context, "server", "42"), "snapshot", 1)
        val coordinator = OperationCoordinator(store)
        var rotations = 0
        val session = OperationSession(context, coordinator, intent, System.currentTimeMillis()) { rotations++ }
        assertFalse(session.startNewAttemptAfterTerminalClose())
        session.begin(true, context)
        assertFalse(session.startNewAttemptAfterTerminalClose())
        coordinator.accepted("original", 88)
        assertFalse(session.startNewAttemptAfterTerminalClose())
        coordinator.outcomeUnknown("original")
        assertFalse(session.startNewAttemptAfterTerminalClose())
        coordinator.reconcile("original", OperationCoordinator.Reconciliation.ActionStatus(88,
            OperationCoordinator.Reconciliation.Status.SUCCEEDED))
        assertEquals(0, rotations)
        assertTrue(session.startNewAttemptAfterTerminalClose())
        assertFalse(session.startNewAttemptAfterTerminalClose())
        assertEquals(1, rotations)
        assertEquals(OperationStage.SUCCEEDED, coordinator.get("original")?.stage)
        assertEquals(88L, store.records.single().providerActionId)
    }
}
