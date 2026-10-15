package com.hcloudcontrol.operations

import org.junit.Assert.*
import org.junit.Test

class OperationResultPolicyTest {
    private fun record(stage: OperationStage) = PersistedOperation(OperationIntent("test", ResourceIdentity(
        OperationContext("fixture", "https://example.invalid", OperationContext.Environment.TEST), "server", "42"), "delete", 0), stage, 0)

    @Test fun `sending cannot dismiss via back outside or close`() {
        assertFalse(canDismissOperationResult(record(OperationStage.SENDING)))
    }

    @Test fun `closing accepted or unknown result is navigation not cancellation`() {
        for (stage in listOf(OperationStage.ACCEPTED, OperationStage.RUNNING, OperationStage.OUTCOME_UNKNOWN, OperationStage.SUCCEEDED)) {
            val operation = record(stage)
            assertTrue(canDismissOperationResult(operation))
            assertEquals(stage, operation.stage)
        }
    }
}
