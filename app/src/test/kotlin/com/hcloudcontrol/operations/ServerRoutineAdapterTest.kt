package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ServerRoutineAdapterTest {
    @Test fun `ISO review identifies exact selected name and immutable ID`() {
        assertEquals("synthetic.iso · #77", isoReviewIdentity(IsoOption(77, "synthetic.iso", null, "public", "x86")))
    }
    private val context = OperationContext("project-a", "https://api.hetzner.cloud/v1/", OperationContext.Environment.CLOUD)
    private val server = Server(42, "synthetic", ServerStatus.OFF, null, "nbg1", "cx-test")
    private fun fake(onCall: (String) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, _ -> onCall(method.name) } as HCloudRepository
    private fun session(action: ServerRoutineAction): OperationSession {
        val store = object : OperationStore {
            var records = emptyList<PersistedOperation>()
            override fun load() = records
            override fun save(records: List<PersistedOperation>) { this.records = records }
        }
        val intent = OperationIntent("test-intent", ResourceIdentity(context, "server", "42"), action.identity(ServerRoutineDraft()), 1)
        return OperationSession(context, OperationCoordinator(store), intent, System.currentTimeMillis())
    }
    @Test fun `fresh status change blocks power before write`() = runBlocking {
        var writes = 0
        val repo = fake { if (it == "getServer") server.copy(status = ServerStatus.RUNNING) else { writes++; error("No write allowed") } }
        val session = session(ServerRoutineAction.POWER_ON)
        val result = runCatching { ServerRoutineAdapter(repo, { context }, { true }).submit(session,
            OperationReview(requireNotNull(session.intent), System.currentTimeMillis(), true), server,
            ServerRoutineAction.POWER_ON, ServerRoutineDraft()) }
        assertTrue(result.isFailure); assertEquals(0, writes); assertNull(session.record)
    }
    @Test fun `lost response locks original intent unknown without automatic retry`() = runBlocking {
        var writes = 0
        val repo = fake { if (it == "getServer") server else { writes++; throw java.io.IOException("synthetic") } }
        val session = session(ServerRoutineAction.POWER_ON)
        ServerRoutineAdapter(repo, { context }, { true }).submit(session,
            OperationReview(requireNotNull(session.intent), System.currentTimeMillis(), true), server,
            ServerRoutineAction.POWER_ON, ServerRoutineDraft())
        assertEquals(1, writes); assertEquals(OperationStage.OUTCOME_UNKNOWN, session.record?.stage)
    }
    @Test fun `successful original action is retained`() = runBlocking {
        val repo = fake { if (it == "getServer") server else CloudAction(88, "success") }
        val session = session(ServerRoutineAction.POWER_ON)
        ServerRoutineAdapter(repo, { context }, { true }).submit(session,
            OperationReview(requireNotNull(session.intent), System.currentTimeMillis(), true), server,
            ServerRoutineAction.POWER_ON, ServerRoutineDraft())
        assertEquals(88L, session.record?.providerActionId); assertEquals(OperationStage.SUCCEEDED, session.record?.stage)
    }
    @Test fun `reboot cannot be proved by merely running server`() = runBlocking {
        val session = session(ServerRoutineAction.REBOOT)
        session.begin(true, context); session.unknownIfPending()
        val repo = fake { assertEquals("getServer", it); server.copy(status = ServerStatus.RUNNING) }
        ServerRoutineAdapter(repo, { context }, { true }).reconcile(session, ServerRoutineAction.REBOOT, ServerRoutineDraft())
        assertEquals(OperationStage.OUTCOME_UNKNOWN, session.record?.stage)
    }
    @Test fun `labels use official syntax and permit empty values`() {
        assertEquals(mapOf("team" to "", "example.org/owner" to "alice"), parseResourceLabels("team=\nexample.org/owner=alice"))
        assertNull(parseResourceLabels("team=a\nteam=b"))
        assertNull(parseResourceLabels("hetzner.cloud/private=x"))
        assertNull(parseResourceLabels("invalid prefix/key=x"))
        assertFalse(validResourceLabel("team", "x".repeat(64)))
    }
}
