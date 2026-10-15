package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy
import java.nio.ByteBuffer
import java.util.Base64

class SshKeyOperationAdapterTest {
    private val context = OperationContext("project", "https://api.hetzner.cloud/v1/", OperationContext.Environment.CLOUD)
    private class Store : OperationStore {
        var records = emptyList<PersistedOperation>()
        override fun load() = records
        override fun save(records: List<PersistedOperation>) { this.records = records }
    }
    private fun repository(call: (String) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, _ -> call(method.name) } as HCloudRepository
    private fun publicKey(): String {
        val type = "ssh-ed25519".toByteArray()
        val bytes = ByteBuffer.allocate(4 + type.size + 4 + 32).putInt(type.size).put(type).putInt(32).put(ByteArray(32) { (it + 1).toByte() }).array()
        return "ssh-ed25519 " + Base64.getEncoder().encodeToString(bytes)
    }
    private fun review(id: String?, action: String) = OperationReview(OperationIntent("attempt", ResourceIdentity(context, "ssh_key", id), action, 0), 100, true)

    @Test fun `create locks duplicates and persists authoritative created ID`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        var writes = 0
        val fake = repository { method -> when (method) {
            "getSshKeys" -> emptyList<SshKeyOption>()
            "createSshKey" -> { writes++; SshKeyOption(42, "fixture", "fingerprint") }
            else -> error("Unexpected call")
        } }
        val adapter = SshKeyOperationAdapter(coordinator, fake, context, { context }, { true })
        val proof = review(null, "create_ssh_key")
        val result = adapter.submit(proof, CreateSshKeySpec("fixture", publicKey()))
        assertEquals(OperationStage.SUCCEEDED, result.stage)
        assertEquals("42", result.createdResourceId)
        assertTrue(runCatching { adapter.submit(proof, CreateSshKeySpec("fixture", publicKey())) }.isFailure)
        assertEquals(1, writes)
    }

    @Test fun `changed deletion fingerprint blocks provider mutation`() = runBlocking {
        val original = SshKeyOption(42, "fixture", "original")
        var writes = 0
        val fake = repository { method -> when (method) {
            "getSshKeys" -> listOf(original.copy(fingerprint = "changed"))
            "deleteSshKey" -> { writes++; Unit }
            else -> error("Unexpected call")
        } }
        val coordinator = OperationCoordinator(Store()) { 100 }
        val adapter = SshKeyOperationAdapter(coordinator, fake, context, { context }, { true })
        assertTrue(runCatching { adapter.submit(review("42", "delete_ssh_key"), expected = original) }.isFailure)
        assertEquals(0, writes)
        assertTrue(coordinator.all().isEmpty())
    }

    @Test fun `uncertain create reconciles by read without another POST`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        var writes = 0
        val fake = repository { method -> when (method) {
            "getSshKeys" -> emptyList<SshKeyOption>()
            "createSshKey" -> { writes++; error("Synthetic response loss") }
            else -> error("Unexpected call")
        } }
        val adapter = SshKeyOperationAdapter(coordinator, fake, context, { context }, { true })
        val record = adapter.submit(review(null, "create_ssh_key"), CreateSshKeySpec("fixture", publicKey()))
        assertEquals(OperationStage.OUTCOME_UNKNOWN, record.stage)
        assertEquals(OperationStage.OUTCOME_UNKNOWN, adapter.reconcile(record).stage)
        assertEquals(1, writes)
    }

    @Test fun `delete unknown reconciles absence of exact original key ID`() = runBlocking {
        val coordinator = OperationCoordinator(Store()) { 100 }
        val proof = review("42", "delete_ssh_key")
        coordinator.beginSubmission(proof, context, 0, true); coordinator.outcomeUnknown(proof.intent.intentId)
        val fake = repository { listOf(SshKeyOption(43, "fixture", "same-name-different-key")) }
        val adapter = SshKeyOperationAdapter(coordinator, fake, context, { context }, { true })
        assertEquals(OperationStage.SUCCEEDED, adapter.reconcile(coordinator.get("attempt")!!).stage)
    }
}
