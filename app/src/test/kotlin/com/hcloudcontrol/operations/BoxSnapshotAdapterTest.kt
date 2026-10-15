package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.StorageBoxRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class BoxSnapshotAdapterTest {
    private val context = OperationContext("original-project", "https://api.hetzner.com/v1/", OperationContext.Environment.CLOUD)
    private val box = StorageBox(7, "synthetic", "active", null)
    private val snapshot = StorageBoxSnapshot(42, "synthetic", null, false, null, null)
    private class Store : OperationStore {
        var records = emptyList<PersistedOperation>()
        override fun load() = records
        override fun save(records: List<PersistedOperation>) { this.records = records }
    }
    private fun proof(id: String = "original") = OperationReview(OperationIntent(id,
        ResourceIdentity(context, "storage_box", "7"), "create_snapshot", 1), 100, true)
    private fun fake(call: (String, Array<out Any?>) -> Any?): StorageBoxRepository = Proxy.newProxyInstance(
        StorageBoxRepository::class.java.classLoader, arrayOf(StorageBoxRepository::class.java),
    ) { _, method, args -> call(method.name, args ?: emptyArray()) } as StorageBoxRepository

    @Test fun `snapshot write starts only after durable sending and lost response never replays`() = runBlocking {
        val store = Store(); val coordinator = OperationCoordinator(store) { 100 }
        var writes = 0
        val repo = fake { name, _ -> when (name) {
            "getStorageBox" -> box
            "createStorageBoxSnapshot" -> { writes++; assertEquals(OperationStage.SENDING, store.records.single().stage); throw java.io.IOException("Synthetic lost response") }
            else -> error("Unexpected $name")
        } }
        val adapter = BoxSnapshotAdapter(repo, coordinator, { context }, { true })
        assertEquals(OperationStage.OUTCOME_UNKNOWN, adapter.submit(proof(), box, CreateStorageBoxSnapshotSpec()).stage)
        assertTrue(runCatching { adapter.submit(proof("new-intent"), box, CreateStorageBoxSnapshotSpec()) }.isFailure)
        assertEquals(1, writes)
    }
    @Test fun `accepted action survives recreation and checks only original action in original context`() = runBlocking {
        val store = Store(); val coordinator = OperationCoordinator(store) { 100 }
        var writes = 0; val reads = mutableListOf<Long>()
        val repo = fake { name, args -> when (name) {
            "getStorageBox" -> box
            "createStorageBoxSnapshot" -> { writes++; CreatedStorageBoxSnapshot(snapshot, CloudAction(99, "running")) }
            "getStorageBoxAction" -> { reads += args[0] as Long; CloudAction(99, "success") }
            else -> error("Unexpected $name")
        } }
        val pending = BoxSnapshotAdapter(repo, coordinator, { context }, { true }).submit(proof(), box, CreateStorageBoxSnapshotSpec())
        assertEquals(OperationStage.RUNNING, pending.stage)
        assertEquals("42", pending.createdResourceId)
        val restored = OperationCoordinator(store) { 100 }
        var active = context.copy(connectionId = "another-project")
        val adapter = BoxSnapshotAdapter(repo, restored, { active }, { false })
        assertTrue(runCatching { adapter.reconcile(requireNotNull(restored.get("original"))) }.isFailure)
        assertTrue(reads.isEmpty())
        active = context
        val success = adapter.reconcile(requireNotNull(restored.get("original")))
        assertEquals(OperationStage.SUCCEEDED, success.stage)
        assertEquals(listOf(99L), reads); assertEquals(1, writes)
        assertEquals(OperationStage.SUCCEEDED, restored.refreshFailed("original").stage)
        assertTrue(restored.get("original")!!.refreshFailed)
    }
    @Test fun `gate revoked after durable lock prevents snapshot request`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        var allowed = true; var writes = 0
        val repo = fake { name, _ -> if (name == "getStorageBox") box else { writes++; error("No write") } }
        val record = BoxSnapshotAdapter(repo, coordinator, { context }, { allowed }).submit(proof(), box, CreateStorageBoxSnapshotSpec()) { allowed = false }
        assertEquals(0, writes); assertEquals(OperationStage.OUTCOME_UNKNOWN, record.stage)
    }
    @Test fun `changed box review and invalid description are rejected before write`() = runBlocking {
        var writes = 0
        val repo = fake { name, _ -> if (name == "getStorageBox") box.copy(name = "changed") else { writes++; error("No write") } }
        val adapter = BoxSnapshotAdapter(repo, OperationCoordinator(Store()) { 100 }, { context }, { true })
        assertTrue(runCatching { adapter.submit(proof(), box, CreateStorageBoxSnapshotSpec()) }.isFailure)
        assertTrue(runCatching { adapter.submit(proof(), box, CreateStorageBoxSnapshotSpec("invalid/description")) }.isFailure)
        assertEquals(0, writes)
        assertTrue(validBoxSnapshotDescription("before-upgrade #1"))
        assertFalse(validBoxSnapshotDescription("x".repeat(1001)))
    }
    @Test fun `unknown snapshot without returned action cannot be proved by inventory or replay`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        coordinator.beginSubmission(proof(), context, 1, true)
        val unknown = coordinator.outcomeUnknown("original")
        val repo = fake { name, _ -> error("No provider call allowed: $name") }
        val result = BoxSnapshotAdapter(repo, coordinator, { context }, { true }).reconcile(unknown)
        assertEquals(OperationStage.OUTCOME_UNKNOWN, result.stage)
    }
}
