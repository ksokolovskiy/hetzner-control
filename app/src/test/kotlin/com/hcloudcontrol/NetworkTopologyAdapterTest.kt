package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.network.*
import com.hcloudcontrol.operations.*
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class NetworkTopologyAdapterTest {
    private val context = OperationContext("original", "https://api.hetzner.cloud/v1/", OperationContext.Environment.CLOUD)
    private val network = PrivateNetworkInventory(42, "synthetic", "10.0.0.0/16", emptyList(), emptyList(), emptyList(), false, emptyMap())
    private fun fake(block: (String, Array<out Any?>) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, args -> block(method.name, args ?: emptyArray()) } as HCloudRepository
    private fun coordinator() = OperationCoordinator(object : OperationStore {
        var records = emptyList<PersistedOperation>()
        override fun load() = records
        override fun save(records: List<PersistedOperation>) { this.records = records }
    })
    private fun review(draft: NetworkMutationDraft, id: String = "intent", resourceId: String? = "42") =
        OperationReview(OperationIntent(id, ResourceIdentity(context, "network", resourceId), draft.identity(), 1), System.currentTimeMillis(), true)

    @Test fun `protected network delete never sends or changes protection`() = runBlocking {
        var writes = 0
        val protected = network.copy(deleteProtected = true)
        val repo = fake { name, _ -> when (name) {
            "getPrivateNetwork" -> protected
            "getPrivateNetworkMembers" -> emptyList<PrivateNetworkMember>()
            else -> { writes++; error("Write not allowed") }
        } }
        val draft = NetworkMutationDraft(NetworkMutationKind.DELETE)
        val owner = coordinator()
        val result = runCatching { NetworkTopologyAdapter(repo, owner, { context }, { true }).submit(review(draft), draft, NetworkSnapshot(protected, emptyList())) }
        assertTrue(result.isFailure); assertEquals(0, writes); assertTrue(owner.all().isEmpty())
    }
    @Test fun `missing protection is not permission to delete`() = runBlocking {
        var writes = 0
        val unknown = network.copy(protectionKnown = false)
        val repo = fake { name, _ -> when (name) {
            "getPrivateNetwork" -> unknown
            "getPrivateNetworkMembers" -> emptyList<PrivateNetworkMember>()
            else -> { writes++; error("Unknown protection must block writes") }
        } }
        val draft = NetworkMutationDraft(NetworkMutationKind.DELETE)
        val owner = coordinator()
        assertTrue(runCatching {
            NetworkTopologyAdapter(repo, owner, { context }, { true }).submit(review(draft), draft, NetworkSnapshot(unknown, emptyList()))
        }.isFailure)
        assertEquals(0, writes)
        assertTrue(owner.all().isEmpty())
    }
    @Test fun `changed topology invalidates reviewed operation before send`() = runBlocking {
        var writes = 0
        val repo = fake { name, _ -> when (name) {
            "getPrivateNetwork" -> network.copy(routes = listOf(PrivateNetworkRoute("10.9.0.0/16", "10.0.0.2")))
            "getPrivateNetworkMembers" -> emptyList<PrivateNetworkMember>()
            else -> { writes++; error("No write") }
        } }
        val draft = NetworkMutationDraft(NetworkMutationKind.DELETE)
        val result = runCatching { NetworkTopologyAdapter(repo, coordinator(), { context }, { true }).submit(review(draft), draft, NetworkSnapshot(network, emptyList())) }
        assertTrue(result.isFailure); assertEquals(0, writes)
    }
    @Test fun `gate revoked between lock and send prevents request and preserves uncertain intent`() = runBlocking {
        var allowed = true; var writes = 0
        val repo = fake { name, _ -> when (name) {
            "getPrivateNetwork" -> network
            "getPrivateNetworkMembers" -> emptyList<PrivateNetworkMember>()
            else -> { writes++; error("No write") }
        } }
        val draft = NetworkMutationDraft(NetworkMutationKind.DELETE)
        val record = NetworkTopologyAdapter(repo, coordinator(), { context }, { allowed }).submit(review(draft), draft,
            NetworkSnapshot(network, emptyList()), onSending = { allowed = false })
        assertEquals(0, writes); assertEquals(OperationStage.OUTCOME_UNKNOWN, record.stage)
    }
    @Test fun `lost create response locks equivalent scope and never replays POST`() = runBlocking {
        var writes = 0
        val repo = fake { name, _ -> when (name) {
            "getPrivateNetworks" -> emptyList<PrivateNetworkInventory>()
            "createPrivateNetwork" -> { writes++; throw java.io.IOException("Synthetic lost response") }
            else -> error("Unexpected $name")
        } }
        val draft = NetworkMutationDraft(NetworkMutationKind.CREATE, "synthetic", "10.0.0.0/16")
        val adapter = NetworkTopologyAdapter(repo, coordinator(), { context }, { true })
        assertEquals(OperationStage.OUTCOME_UNKNOWN, adapter.submit(review(draft, resourceId = null), draft, null).stage)
        assertTrue(runCatching { adapter.submit(review(draft, id = "second", resourceId = null), draft, null) }.isFailure)
        assertEquals(1, writes)
    }
    @Test fun `status checks target original provider action and context only`() = runBlocking {
        var writes = 0; val readIds = mutableListOf<Long>()
        val repo = fake { name, args -> when (name) {
            "getPrivateNetwork" -> network
            "getPrivateNetworkMembers" -> emptyList<PrivateNetworkMember>()
            "addPrivateNetworkRoute" -> { writes++; CloudAction(88, "running") }
            "getAction" -> { readIds += args[0] as Long; CloudAction(88, "success") }
            else -> error("Unexpected $name")
        } }
        var active = context
        val draft = NetworkMutationDraft(NetworkMutationKind.ADD_ROUTE, route = PrivateNetworkRouteSpec("10.9.0.0/16", "10.0.0.2"))
        val adapter = NetworkTopologyAdapter(repo, coordinator(), { active }, { true })
        val accepted = adapter.submit(review(draft), draft, NetworkSnapshot(network, emptyList()))
        active = context.copy(connectionId = "other")
        assertTrue(runCatching { adapter.reconcile(accepted) }.isFailure)
        assertTrue(readIds.isEmpty())
        active = context
        assertEquals(OperationStage.SUCCEEDED, adapter.reconcile(accepted).stage)
        assertEquals(listOf(88L), readIds); assertEquals(1, writes)
    }
    @Test fun `authoritative create resource without action succeeds with original returned ID`() = runBlocking {
        var writes = 0
        val repo = fake { name, _ -> when (name) {
            "getPrivateNetworks" -> emptyList<PrivateNetworkInventory>()
            "createPrivateNetwork" -> { writes++; CreatedPrivateNetwork(network, null) }
            else -> error("Unexpected $name")
        } }
        val draft = NetworkMutationDraft(NetworkMutationKind.CREATE, "synthetic", "10.0.0.0/16")
        val record = NetworkTopologyAdapter(repo, coordinator(), { context }, { true }).submit(review(draft, resourceId = null), draft, null)
        assertEquals(OperationStage.SUCCEEDED, record.stage)
        assertEquals("42", record.createdResourceId)
        assertNull(record.providerActionId); assertEquals(1, writes)
    }
    @Test fun `authoritative 204 delete succeeds without invented action`() = runBlocking {
        var writes = 0
        val repo = fake { name, _ -> when (name) {
            "getPrivateNetwork" -> network
            "getPrivateNetworkMembers" -> emptyList<PrivateNetworkMember>()
            "deletePrivateNetwork" -> { writes++; null }
            else -> error("Unexpected $name")
        } }
        val draft = NetworkMutationDraft(NetworkMutationKind.DELETE)
        val record = NetworkTopologyAdapter(repo, coordinator(), { context }, { true }).submit(review(draft), draft, NetworkSnapshot(network, emptyList()))
        assertEquals(OperationStage.SUCCEEDED, record.stage)
        assertNull(record.providerActionId); assertEquals(1, writes)
    }
}
