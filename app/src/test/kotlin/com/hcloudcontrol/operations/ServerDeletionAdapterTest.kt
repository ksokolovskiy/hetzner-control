package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import java.io.IOException
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ServerDeletionAdapterTest {
    private val context = OperationContext("synthetic", "https://example.invalid/v1/", OperationContext.Environment.TEST)
    private val server = Server(42, "fixture", ServerStatus.OFF, null, "nbg1", "cx-test",
        protection = ServerProtection(false, false))
    private class Store : OperationStore {
        var records = emptyList<PersistedOperation>()
        override fun load() = records
        override fun save(records: List<PersistedOperation>) { this.records = records }
    }
    private fun repo(call: (String) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, _ -> call(method.name) } as HCloudRepository
    private fun proof() = OperationReview(OperationIntent("delete-1", ResourceIdentity(context, "server", "42"), "delete", 0), 100, true)

    @Test fun `original action determines completion and duplicate cannot resend`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        var writes = 0
        val repository = repo { when (it) {
            "getServer" -> server
            "deleteServerWithAction" -> { writes++; assertEquals(OperationStage.SENDING, coordinator.get("delete-1")?.stage); CloudAction(88, "running") }
            "getAction" -> CloudAction(88, "success")
            else -> error(it)
        } }
        val adapter = ServerDeletionAdapter(repository, coordinator, { context }, { true })
        val pending = adapter.submit(proof(), "fixture")
        assertEquals(OperationStage.RUNNING, pending.stage)
        assertTrue(runCatching { adapter.submit(proof(), "fixture") }.isFailure)
        assertEquals(1, writes)
        assertEquals(OperationStage.SUCCEEDED, adapter.reconcile(pending).stage)
    }

    @Test fun `protected or renamed resource prevents sending`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        var writes = 0
        val repository = repo { if (it == "getServer") server.copy(protection = ServerProtection(true, false)) else { writes++; error(it) } }
        val adapter = ServerDeletionAdapter(repository, coordinator, { context }, { true })
        assertTrue(runCatching { adapter.submit(proof(), "fixture") }.isFailure)
        assertEquals(0, writes)
        assertNull(coordinator.get("delete-1"))
    }

    @Test fun `lost response remains unknown and exact missing resource confirms without replay`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        var missing = false
        var writes = 0
        val repository = repo { when (it) {
            "getServer" -> if (!missing) server else throw HCloudApiException(HCloudApiFailure(404, "not_found", null, emptyList()))
            "deleteServerWithAction" -> { writes++; throw IOException("synthetic lost response") }
            else -> error(it)
        } }
        val adapter = ServerDeletionAdapter(repository, coordinator, { context }, { true })
        val pending = adapter.submit(proof(), "fixture")
        assertEquals(OperationStage.OUTCOME_UNKNOWN, pending.stage)
        missing = true
        assertEquals(OperationStage.SUCCEEDED, adapter.reconcile(pending).stage)
        assertEquals(1, writes)
    }
}
