package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class LoadBalancerDeletionAdapterTest {
    private val balancer = LoadBalancerInventory(42, "fixture", "lb-test", "nbg1",
        LoadBalancerPublicInterface(true, "192.0.2.42", null, null, null), emptyList(),
        listOf(LoadBalancerService("tcp", 443, 443, false)), emptyList(), false, emptyMap(), emptyList())
    private fun repository(read: () -> LoadBalancerInventory): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, _ -> check(method.name == "getLoadBalancer"); read() } as HCloudRepository
    @Test fun `changed traffic dependencies or provider protection block destructive review`() = runBlocking {
        val changed = listOf(balancer.copy(deleteProtected = true), balancer.copy(services = emptyList()), balancer.copy(name = "renamed"))
        changed.forEach { fresh -> assertTrue(runCatching { LoadBalancerDeletionAdapter(repository { fresh }, balancer).validateFresh() }.isFailure) }
    }
    @Test fun `unknown deletion requires authoritative absence of original balancer`() = runBlocking {
        val missing = repository { throw HCloudApiException(HCloudApiFailure(404, "not_found", null, emptyList())) }
        assertTrue(LoadBalancerDeletionAdapter(missing, balancer).deleted())
        assertFalse(LoadBalancerDeletionAdapter(repository { balancer }, balancer).deleted())
        val offline = repository { throw IllegalStateException("Synthetic offline") }
        assertTrue(runCatching { LoadBalancerDeletionAdapter(offline, balancer).deleted() }.isFailure)
    }
    @Test fun `missing protection is unknown not off and blocks destructive review`() = runBlocking {
        val unknown = balancer.copy(protectionKnown = false, deleteProtected = false)
        assertFalse(canDeleteLoadBalancer(unknown))
        assertTrue(runCatching { LoadBalancerDeletionAdapter(repository { unknown }, balancer).validateFresh() }.isFailure)
        assertTrue(canDeleteLoadBalancer(balancer))
    }
    @Test fun `live nested target health does not change review identity but target IDs do`() = runBlocking {
        val child = LoadBalancerTarget("server", 7, null, null, false, listOf(LoadBalancerTargetHealth(443, "healthy")), emptyList())
        val selector = LoadBalancerTarget("label_selector", null, "env=test", null, false, emptyList(), listOf(child))
        val reviewed = balancer.copy(targets = listOf(selector))
        val healthOnly = reviewed.copy(targets = listOf(selector.copy(targets = listOf(child.copy(healthStatuses = listOf(LoadBalancerTargetHealth(443, "unhealthy")))))))
        LoadBalancerDeletionAdapter(repository { healthOnly }, reviewed).validateFresh()
        val changedId = reviewed.copy(targets = listOf(selector.copy(targets = listOf(child.copy(serverId = 8)))))
        assertTrue(runCatching { LoadBalancerDeletionAdapter(repository { changedId }, reviewed).validateFresh() }.isFailure)
    }
}
