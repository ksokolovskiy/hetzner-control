package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudApiFailure
import org.junit.Assert.*
import org.junit.Test

class MutationRejectionStateTest {
    @Test fun `normalized firewall placement power and volume rejection is failed not unknown`() {
        val context = OperationContext("synthetic", "https://api.hetzner.cloud/v1/", OperationContext.Environment.CLOUD)
        for ((index, target) in listOf("firewall" to "create", "placement_group" to "create", "server" to "power_on", "volume" to "resize").withIndex()) {
            val store = object : OperationStore {
                var records = emptyList<PersistedOperation>()
                override fun load() = records
                override fun save(records: List<PersistedOperation>) { this.records = records }
            }
            val coordinator = OperationCoordinator(store)
            val intent = OperationIntent("attempt-$index", ResourceIdentity(context, target.first, "7"), target.second, 1)
            val session = OperationSession(context, coordinator, intent, System.currentTimeMillis())
            session.begin(true, context)
            val status = listOf(401, 403, 409, 422)[index]
            session.rejectOrUnknown(HCloudApiException(HCloudApiFailure(status, "fixture_denied", null, emptyList())))
            assertEquals(OperationStage.FAILED, session.record?.stage)
            assertEquals(status, session.record?.diagnostic?.httpStatus)
            assertFalse(session.record?.isPending == true)
        }
    }
}
