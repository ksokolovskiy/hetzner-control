package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class OriginalCloudResourceStatusReaderTest {
    private val context = OperationContext("fixture", "https://example.invalid", OperationContext.Environment.TEST)
    private val firewall = FirewallInventory(42, "fixture", emptyList(), emptyList(), emptyMap())
    private val group = PlacementGroupInventory(42, "fixture", "spread", listOf(9), emptyMap(), null)
    private fun record(type: String, action: String, id: String? = null, created: String? = null) =
        PersistedOperation(OperationIntent("intent", ResourceIdentity(context, type, id), action, 0),
            OperationStage.OUTCOME_UNKNOWN, 1, createdResourceId = created)
    private fun repository(call: (String, Array<out Any?>) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, args -> call(method.name, args ?: emptyArray()) } as HCloudRepository

    @Test fun `known creation ID is read exactly and can prove existing created resource`() = runBlocking {
        val fake = repository { method, args -> check(method == "getFirewall" && args[0] == 42L); firewall }
        val evidence = OriginalCloudResourceStatusReader(fake).read(record("firewall", "create", created = "42")) as OperationCoordinator.Reconciliation.CreatedResourceEffect
        assertEquals("42", evidence.resource.resourceId)
        assertEquals(context, evidence.resource.context)
        assertTrue(evidence.effectConfirmed)
    }
    @Test fun `lost creation ID reads inventory but matching name is never proof or a replay`() = runBlocking {
        val reads = mutableListOf<String>()
        val fake = repository { method, _ -> reads += method; when (method) {
            "getFirewalls" -> listOf(firewall)
            "getPlacementGroups" -> listOf(group)
            else -> error("Only read-only inventory is allowed")
        } }
        val reader = OriginalCloudResourceStatusReader(fake)
        assertEquals(OperationCoordinator.Reconciliation.Ambiguous, reader.read(record("firewall", "create")))
        assertEquals(OperationCoordinator.Reconciliation.Ambiguous, reader.read(record("placement_group", "create")))
        assertEquals(listOf("getFirewalls", "getPlacementGroups"), reads)
    }
    @Test fun `member check reads original group and server IDs but lacks full durable review proof`() = runBlocking {
        val reads = mutableListOf<Pair<String, Long>>()
        val fake = repository { method, args -> reads += method to (args[0] as Long); when (method) {
            "getPlacementGroup" -> group
            "getServer" -> Server(9, "fixture server", ServerStatus.RUNNING, "192.0.2.9", "nbg1", "fixture", placementGroupId = 42)
            else -> error("No provider write or new picker target is allowed")
        } }
        assertEquals(OperationCoordinator.Reconciliation.Ambiguous,
            OriginalCloudResourceStatusReader(fake).read(record("placement_group", "add_server_9", "42")))
        assertEquals(listOf("getPlacementGroup" to 42L, "getServer" to 9L), reads)
    }
    @Test fun `only authoritative original deletion absence confirms existing effect`() = runBlocking {
        val fake = repository { method, args -> check(method == "getFirewall" && args[0] == 42L)
            throw HCloudApiException(HCloudApiFailure(404, "not_found", null, emptyList())) }
        val evidence = OriginalCloudResourceStatusReader(fake).read(record("firewall", "delete", "42")) as OperationCoordinator.Reconciliation.ExistingResourceEffect
        assertEquals("42", evidence.resource.resourceId)
        assertTrue(evidence.effectConfirmed)
    }
    @Test fun `known group ID with unsupported creation kind remains unknown`() = runBlocking {
        val fake = repository { method, args -> check(method == "getPlacementGroup" && args[0] == 42L); group.copy(type = "unsupported") }
        assertEquals(OperationCoordinator.Reconciliation.Ambiguous,
            OriginalCloudResourceStatusReader(fake).read(record("placement_group", "create", created = "42")))
    }
}
