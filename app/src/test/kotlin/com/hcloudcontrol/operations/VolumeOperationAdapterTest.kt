package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VolumeOperationAdapterTest {
    private val context = OperationContext("fixture", "https://example.invalid/v1/", OperationContext.Environment.TEST)
    private val volume = VolumeInventory(12, "fixture", 10, "nbg1", null, "available", false, emptyMap())
    private class Store : OperationStore {
        var records = emptyList<PersistedOperation>()
        override fun load() = records
        override fun save(records: List<PersistedOperation>) { this.records = records }
    }
    private fun repo(call: (String) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, _ -> call(method.name) } as HCloudRepository
    private fun review(kind: VolumeMutation) = OperationReview(OperationIntent("op", ResourceIdentity(context, "volume", "12"),
        kind.name.lowercase(), 0), 100, true)

    @Test fun `protected or attached delete cannot send and never detaches or unprotects`() = runBlocking {
        for (current in listOf(volume.copy(deleteProtected = true), volume.copy(serverId = 22), volume.copy(protectionKnown = false))) {
            var writes = 0
            val adapter = VolumeOperationAdapter(repo { if (it == "getVolume") current else { writes++; error(it) } },
                OperationCoordinator(Store()) { 100 }, { context }, { true })
            assertTrue(runCatching { adapter.submit(review(VolumeMutation.DELETE), current, VolumeMutationDraft(VolumeMutation.DELETE)) }.isFailure)
            assertEquals(0, writes)
        }
    }

    @Test fun `incompatible target location prevents attach`() = runBlocking {
        var writes = 0
        val repository = repo { when (it) {
            "getVolume" -> volume
            "getServer" -> Server(22, "server", ServerStatus.RUNNING, null, "fsn1", "cx-test")
            else -> { writes++; error(it) }
        } }
        val adapter = VolumeOperationAdapter(repository, OperationCoordinator(Store()) { 100 }, { context }, { true })
        assertTrue(runCatching { adapter.submit(review(VolumeMutation.ATTACH), volume, VolumeMutationDraft(VolumeMutation.ATTACH, 22)) }.isFailure)
        assertEquals(0, writes)
    }

    @Test fun `resize records original action and requires authoritative completion`() = runBlocking {
        var writes = 0
        val store = Store()
        val coordinator = OperationCoordinator(store) { 100 }
        val repository = repo { when (it) {
            "getVolume" -> volume
            "resizeVolume" -> { writes++; assertEquals(OperationStage.SENDING, store.records.single().stage); CloudAction(33, "running") }
            "getAction" -> CloudAction(33, "success")
            else -> error(it)
        } }
        val adapter = VolumeOperationAdapter(repository, coordinator, { context }, { true })
        val draft = VolumeMutationDraft(VolumeMutation.RESIZE, newSizeGb = 20)
        val pending = adapter.submit(review(VolumeMutation.RESIZE), volume, draft)
        assertEquals(OperationStage.RUNNING, pending.stage)
        assertEquals(OperationStage.SUCCEEDED, adapter.reconcile(pending).stage)
        assertEquals(1, writes)
    }

    @Test fun `create remains unattached and preserves authoritative new ID`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        var writes = 0
        val repository = repo { when (it) {
            "getLocations" -> listOf(LocationOption(1, "nbg1", "Nuremberg", "Nuremberg", "DE"))
            "createVolume" -> { writes++; CreatedVolume(volume, CloudAction(33, "success")) }
            else -> error(it)
        } }
        val adapter = VolumeOperationAdapter(repository, coordinator, { context }, { true })
        val proof = OperationReview(OperationIntent("create", ResourceIdentity(context, "volume", null), "create", 0), 100, true)
        val result = adapter.create(proof, CreateVolumeSpec("fixture", 10, "nbg1"))
        assertEquals(OperationStage.SUCCEEDED, result.stage)
        assertEquals("12", result.createdResourceId)
        assertEquals(1, writes)
    }
    @Test fun `volume creation waits for every original next action and keeps bundle through recreation`() = runBlocking {
        val store = Store()
        val coordinator = OperationCoordinator(store) { 100 }
        var actionReads = 0
        var writes = 0
        val repository = repo { when (it) {
            "getLocations" -> listOf(LocationOption(1, "nbg1", "Nuremberg", "Nuremberg", "DE"))
            "createVolume" -> { writes++; CreatedVolume(volume, CloudAction(33, "success"), listOf(CloudAction(34, "running"))) }
            "getAction" -> { actionReads++; CloudAction(if (actionReads == 1) 33 else 34, "success") }
            else -> error(it)
        } }
        val proof = OperationReview(OperationIntent("create-bundle", ResourceIdentity(context, "volume", null), "create", 0), 100, true)
        val accepted = VolumeOperationAdapter(repository, coordinator, { context }, { true }).create(proof, CreateVolumeSpec("fixture", 10, "nbg1"))
        assertEquals(OperationStage.RUNNING, accepted.stage)
        assertEquals(listOf(33L, 34L), accepted.originalActionIds)
        val restored = OperationCoordinator(store) { 100 }
        val success = VolumeOperationAdapter(repository, restored, { context }, { true }).reconcile(requireNotNull(restored.get("create-bundle")))
        assertEquals(OperationStage.SUCCEEDED, success.stage)
        assertEquals(2, actionReads); assertEquals(1, writes)
    }
    @Test fun `documented no content volume delete succeeds without invented action`() = runBlocking {
        var writes = 0
        val repository = repo { when (it) {
            "getVolume" -> volume
            "deleteVolume" -> { writes++; null }
            else -> error(it)
        } }
        val adapter = VolumeOperationAdapter(repository, OperationCoordinator(Store()) { 100 }, { context }, { true })
        val result = adapter.submit(review(VolumeMutation.DELETE), volume, VolumeMutationDraft(VolumeMutation.DELETE))
        assertEquals(OperationStage.SUCCEEDED, result.stage)
        assertNull(result.providerActionId); assertEquals(1, writes)
    }

    @Test fun `context or permission changing after submission lock prevents every volume write`() = runBlocking {
        for (changeOwner in listOf(true, false)) {
            var active = context
            var allowed = true
            var writes = 0
            val store = Store()
            val repository = repo { name ->
                if (name == "getVolume") volume else { writes++; error("Unexpected request") }
            }
            val adapter = VolumeOperationAdapter(repository, OperationCoordinator(store) { 100 }, { active }, { allowed })
            val result = adapter.submit(review(VolumeMutation.DELETE), volume, VolumeMutationDraft(VolumeMutation.DELETE)) {
                if (changeOwner) active = context.copy(connectionId = "other") else allowed = false
            }
            assertEquals(0, writes)
            assertEquals(OperationStage.OUTCOME_UNKNOWN, result.stage)
            assertEquals(context, store.records.single().intent.resource.context)
            assertNull(result.providerActionId)
        }
    }

    @Test fun `lost create response locks original scope and status reads cannot replay create`() = runBlocking {
        var writes = 0
        val coordinator = OperationCoordinator(Store()) { 100 }
        val repository = repo { name -> when (name) {
            "getLocations" -> listOf(LocationOption(1, "nbg1", "Synthetic", "Nuremberg", "DE"))
            "createVolume" -> { writes++; throw java.io.IOException("Synthetic lost response") }
            else -> error("Unexpected request")
        } }
        val adapter = VolumeOperationAdapter(repository, coordinator, { context }, { true })
        val proof = OperationReview(OperationIntent("lost", ResourceIdentity(context, "volume", null), "create", 0), 100, true)
        val spec = CreateVolumeSpec("fixture", 10, "nbg1")
        val unknown = adapter.create(proof, spec)
        assertEquals(OperationStage.OUTCOME_UNKNOWN, unknown.stage)
        assertEquals(OperationStage.OUTCOME_UNKNOWN, adapter.reconcile(unknown).stage)
        val second = proof.copy(intent = proof.intent.copy(intentId = "second"))
        assertTrue(runCatching { adapter.create(second, spec) }.isFailure)
        assertEquals(1, writes)
    }
}
