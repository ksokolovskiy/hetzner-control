package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PlacementGroupMutationAdapterTest {
    @Test fun `metadata draft restores only exact immutable owner scope`() {
        val draft = PlacementFormDraft("fixture", "env=test")
        val saved = draft.snapshot("project:endpoint:42")
        assertEquals(draft, PlacementFormDraft.restore("project:endpoint:42", saved, PlacementFormDraft()))
        assertEquals(PlacementFormDraft(), PlacementFormDraft.restore("other:endpoint:42", saved, PlacementFormDraft()))
        assertEquals(PlacementFormDraft(), PlacementFormDraft.restore("project:other:42", saved, PlacementFormDraft()))
        assertEquals(PlacementFormDraft(), PlacementFormDraft.restore("project:endpoint:43", saved, PlacementFormDraft()))
        assertEquals(4, saved.size)
    }
    private val group = PlacementGroupInventory(42, "fixture", "spread", listOf(1, 2), mapOf("env" to "test"), null)
    private fun repository(call: (String, Array<out Any?>) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, args -> call(method.name, args ?: emptyArray()) } as HCloudRepository
    @Test fun `changed membership invalidates deletion review before provider write`() = runBlocking {
        var writes = 0
        val fake = repository { method, _ -> when (method) {
            "getPlacementGroup" -> group.copy(serverIds = listOf(1, 2, 3))
            "deletePlacementGroup" -> { writes++; Unit }
            else -> error("Unexpected call")
        } }
        assertTrue(runCatching { PlacementGroupMutationAdapter(fake).validate(PlacementChange.Delete(group)) }.isFailure)
        assertEquals(0, writes)
    }
    @Test fun `metadata update cannot issue membership mutations`() = runBlocking {
        var saved: PlacementGroupPatch? = null
        val fake = repository { method, args -> when (method) {
            "getPlacementGroup" -> group
            "updatePlacementGroup" -> { assertEquals(42L, args[0]); saved = args[1] as PlacementGroupPatch; group.copy(name = "renamed") }
            else -> error("Membership mutation is forbidden")
        } }
        val change = PlacementChange.Edit(group, PlacementGroupPatch(name = "renamed", labels = emptyMap()))
        val adapter = PlacementGroupMutationAdapter(fake)
        adapter.validate(change); adapter.execute(change)
        assertEquals("renamed", saved?.name)
        assertEquals(listOf(1L, 2L), group.serverIds)
    }
    @Test fun `create is spread only with no member side effects`() = runBlocking {
        val fake = repository { method, args -> when (method) {
            "getPlacementGroups" -> emptyList<PlacementGroupInventory>()
            "createPlacementGroup" -> { assertEquals("spread", (args[0] as CreatePlacementGroupSpec).type); CreatedPlacementGroup(group.copy(serverIds = emptyList()), null) }
            else -> error("Unexpected side effect")
        } }
        val change = PlacementChange.Create(CreatePlacementGroupSpec("fixture"))
        val adapter = PlacementGroupMutationAdapter(fake)
        adapter.validate(change)
        assertEquals(42L, adapter.execute(change).createdId)
    }
    @Test fun `explicit deletion protection refuses delete but absent flag is not invented`() = runBlocking {
        val protected = repository { method, _ -> if (method == "getPlacementGroup") group.copy(deleteProtected = true) else error("Write forbidden") }
        assertTrue(runCatching { PlacementGroupMutationAdapter(protected).validate(PlacementChange.Delete(group)) }.isFailure)
        val documented = repository { method, _ -> if (method == "getPlacementGroup") group else error("Write forbidden") }
        PlacementGroupMutationAdapter(documented).validate(PlacementChange.Delete(group))
    }
}
