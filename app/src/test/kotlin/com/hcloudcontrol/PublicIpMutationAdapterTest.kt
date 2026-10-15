package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class PublicIpMutationAdapterTest {
    private val ip = PrimaryIpInventory(9, "fixture", "ipv4", "192.0.2.1", "fsn1", null, null, false, emptyMap(), emptyList())
    private fun repository(call: (String) -> Any?) = Proxy.newProxyInstance(HCloudRepository::class.java.classLoader,
        arrayOf(HCloudRepository::class.java)) { _, method, _ -> call(method.name) } as HCloudRepository

    @Test fun `deletion preconditions never unassign or unprotect as hidden side effects`() = runBlocking {
        listOf(ip.copy(assigneeId = 3, assigneeType = "server"), ip.copy(deleteProtected = true), ip.copy(protectionKnown = false)).forEach { current ->
            val fake = repository { method -> if (method == "getPrimaryIp") current else error("Unexpected write: $method") }
            assertTrue(runCatching { PublicIpMutationAdapter(fake, PublicIpKind.PRIMARY, 9).validate(current.snapshot(), PublicIpMutation.DELETE, null) }.isFailure)
        }
    }

    @Test fun `changed snapshot invalidates review before dispatch`() = runBlocking {
        val fake = repository { method -> if (method == "getPrimaryIp") ip.copy(name = "changed") else error("Write forbidden") }
        assertTrue(runCatching { PublicIpMutationAdapter(fake, PublicIpKind.PRIMARY, 9).validate(ip.snapshot(), PublicIpMutation.DELETE, null) }.isFailure)
    }

    @Test fun `load balancer assignee is not mistaken for a server`() = runBlocking {
        val attached = ip.copy(assigneeId = 7, assigneeType = "load_balancer")
        val fake = repository { method -> if (method == "getPrimaryIp") attached else error("Wrong assignee lookup or write") }
        assertTrue(runCatching { PublicIpMutationAdapter(fake, PublicIpKind.PRIMARY, 9).validate(attached.snapshot(), PublicIpMutation.UNASSIGN, null) }.isFailure)
    }

    @Test fun `only deliberate delete request is dispatched after validation`() = runBlocking {
        val calls = mutableListOf<String>()
        val fake = repository { method -> calls += method; when (method) { "getPrimaryIp" -> ip; "deletePrimaryIp" -> Unit; else -> error("Unexpected mutation") } }
        val adapter = PublicIpMutationAdapter(fake, PublicIpKind.PRIMARY, 9)
        adapter.validate(ip.snapshot(), PublicIpMutation.DELETE, null)
        assertNull(adapter.execute(PublicIpMutation.DELETE, null))
        assertEquals(listOf("getPrimaryIp", "deletePrimaryIp"), calls)
    }
}
