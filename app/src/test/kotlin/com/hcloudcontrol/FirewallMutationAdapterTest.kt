package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class FirewallMutationAdapterTest {
    @Test fun `draft restore pins exact project endpoint and resource without confirmations`() {
        val draft = FirewallFormDraft("fixture", "443", "192.0.2.0/24", "public metadata")
        val saved = draft.snapshot("projectA:endpointA:42")
        assertEquals(draft, FirewallFormDraft.restore("projectA:endpointA:42", saved))
        assertEquals(FirewallFormDraft(), FirewallFormDraft.restore("projectB:endpointA:42", saved))
        assertEquals(FirewallFormDraft(), FirewallFormDraft.restore("projectA:endpointB:42", saved))
        assertEquals(FirewallFormDraft(), FirewallFormDraft.restore("projectA:endpointA:43", saved))
        assertEquals(6, saved.size)
    }
    private val prior = FirewallRuleInventory("out", "udp", "53", emptyList(), listOf("203.0.113.53/32"), "fixture")
    private val firewall = FirewallInventory(42, "fixture", listOf(prior), emptyList(), emptyMap())
    private fun repository(call: (String, Array<out Any?>) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, args -> call(method.name, args ?: emptyArray()) } as HCloudRepository

    @Test fun `literal source validation never accepts names or ambiguous malformed CIDRs`() {
        listOf("192.0.2.1", "192.0.2.0/24", "2001:db8::/32", "::/0", "0.0.0.0/0").forEach { assertTrue(it, validFirewallSource(it)) }
        listOf("example.com", "192.0.2.0/33", "2001:db8::/129", "999.1.1.1", "127.1", "1.2.3.4/", "01.2.3.4", "::%wlan0", "::/0/1").forEach { assertFalse(it, validFirewallSource(it)) }
        assertNull(FirewallTcpDraft("0", "192.0.2.0/24", "").rule())
        assertNull(FirewallTcpDraft("65536", "192.0.2.0/24", "").rule())
        assertNull(FirewallTcpDraft("443", "", "").rule())
        assertTrue(FirewallTcpDraft("443", "0.0.0.0/0", "").broad)
        assertEquals("80-85", FirewallTcpDraft("80-85", "192.0.2.1", "").rule()?.port)
        assertEquals(listOf("192.0.2.1/32", "2001:db8::1/128"), FirewallTcpDraft("443", "192.0.2.1,2001:db8::1", "").rule()?.sourceIps)
        assertNull(FirewallTcpDraft("443", (1..101).joinToString(",") { "192.0.2.$it/32" }, "").rule())
        assertNull(FirewallTcpDraft("85-80", "192.0.2.1", "").rule())
    }
    @Test fun `add TCP preserves every original rule and never adds hidden IPv6`() = runBlocking {
        var sent: List<FirewallRuleSpec>? = null
        val fake = repository { method, args -> when (method) {
            "getFirewall" -> firewall
            "setFirewallRules" -> { @Suppress("UNCHECKED_CAST") val rules = args[1] as List<FirewallRuleSpec>; sent = rules; CloudAction(1, "success", progress = 100) }
            else -> error("Unexpected side effect")
        } }
        val change = FirewallChange.AddTcp(firewall, checkNotNull(FirewallTcpDraft("443", "192.0.2.0/24", "HTTPS").rule()))
        val adapter = FirewallMutationAdapter(fake)
        adapter.validate(change); adapter.execute(change)
        assertEquals(2, sent?.size)
        assertEquals(prior.protocol, sent?.first()?.protocol)
        assertEquals(prior.destinationIps, sent?.first()?.destinationIps)
        assertEquals(listOf("192.0.2.0/24"), sent?.last()?.sourceIps)
    }
    @Test fun `stale rules and changed attachments invalidate frozen review`() = runBlocking {
        val fake = repository { method, _ -> if (method == "getFirewall") firewall.copy(appliedTo = listOf(FirewallAppliedResource("server", 9, null, emptyList()))) else error("Write forbidden") }
        assertTrue(runCatching { FirewallMutationAdapter(fake).validate(FirewallChange.Delete(firewall)) }.isFailure)
        assertTrue(runCatching { FirewallMutationAdapter(fake).validate(FirewallChange.AddTcp(firewall, checkNotNull(FirewallTcpDraft("443", "::/0", "").rule()))) }.isFailure)
    }
    @Test fun `attached firewall deletion is rejected without auto detach`() = runBlocking {
        val attached = firewall.copy(appliedTo = listOf(FirewallAppliedResource("server", 9, null, emptyList())))
        val fake = repository { method, _ -> if (method == "getFirewall") attached else error("No automatic dependency changes") }
        assertTrue(runCatching { FirewallMutationAdapter(fake).validate(FirewallChange.Delete(attached)) }.isFailure)
    }

    private val target = Server(9, "target", ServerStatus.RUNNING, "192.0.2.9", "nbg1", "fixture")
    private val direct = FirewallAppliedResource("server", 9, null, emptyList())
    private val selector = FirewallAppliedResource("label_selector", null, "app=fixture", listOf(direct))

    private suspend fun targetRejected(before: FirewallInventory, current: FirewallInventory = before,
        server: Server = target, remove: Boolean) {
        val reads = mutableListOf<String>()
        val fake = repository { method, _ ->
            reads += method
            when (method) {
                "getFirewall" -> current
                "getServer" -> server
                else -> error("Unexpected write: $method")
            }
        }
        assertTrue(runCatching {
            FirewallMutationAdapter(fake).validate(FirewallChange.Target(before, target.id, target.name, remove))
        }.isFailure)
        assertTrue(reads.all { it == "getFirewall" || it == "getServer" })
    }

    @Test fun `apply rejects target renamed replaced or without public network`() = runBlocking {
        targetRejected(firewall, server = target.copy(name = "renamed"), remove = false)
        targetRejected(firewall, server = target.copy(id = 10), remove = false)
        targetRejected(firewall, server = target.copy(ipv4Address = null), remove = false)
    }

    @Test fun `apply rejects direct and selector managed targets including changed attachments`() = runBlocking {
        targetRejected(firewall.copy(appliedTo = listOf(direct)), remove = false)
        targetRejected(firewall.copy(appliedTo = listOf(selector)), remove = false)
        targetRejected(firewall, current = firewall.copy(appliedTo = listOf(direct)), remove = false)
    }

    @Test fun `remove requires fresh direct attachment and refuses selector ownership`() = runBlocking {
        targetRejected(firewall, remove = true)
        targetRejected(firewall.copy(appliedTo = listOf(selector)), remove = true)
        targetRejected(firewall.copy(appliedTo = listOf(direct, selector)), remove = true)
        targetRejected(firewall.copy(appliedTo = listOf(direct)), current = firewall, remove = true)
        targetRejected(firewall.copy(appliedTo = listOf(direct)), server = target.copy(name = "renamed"), remove = true)
    }

    @Test fun `valid apply and removal write only exact reviewed firewall and server IDs`() = runBlocking {
        listOf(false, true).forEach { remove ->
            val before = if (remove) firewall.copy(appliedTo = listOf(direct)) else firewall
            val writes = mutableListOf<Pair<String, List<Any?>>>()
            val expected = if (remove) "removeFirewallFromServer" else "applyFirewallToServer"
            val fake = repository { method, args -> when (method) {
                "getFirewall" -> before
                "getServer" -> target
                expected -> { writes += method to args.take(2); listOf(CloudAction(1, "success", progress = 100)) }
                else -> error("Unexpected call: $method")
            } }
            val change = FirewallChange.Target(before, target.id, target.name, remove)
            val adapter = FirewallMutationAdapter(fake)
            adapter.validate(change)
            adapter.execute(change)
            assertEquals(listOf(expected to listOf<Any?>(42L, 9L)), writes)
        }
    }
}
