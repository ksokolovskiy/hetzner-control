package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerProtection
import com.hcloudcontrol.core.domain.model.ServerProtectionPatch
import com.hcloudcontrol.core.domain.model.ServerStatus
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProtectionAdapterTest {
    private val server = Server(42, "synthetic", ServerStatus.OFF, null, "nbg1", "cx-test",
        protection = ServerProtection(delete = true, rebuild = false))
    private fun repository(onCall: (String, Array<out Any?>) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, args -> onCall(method.name, args ?: emptyArray()) } as HCloudRepository

    @Test fun `delete and rebuild adapters never modify another protection flag`() = runBlocking {
        val patches = mutableListOf<ServerProtectionPatch>()
        val fake = repository { method, args ->
            when (method) {
                "getServer" -> { assertEquals(42L, args[0]); server }
                "changeServerProtection" -> { assertEquals(42L, args[0]); patches += args[1] as ServerProtectionPatch; CloudAction(88, "running") }
                else -> error("Unexpected adapter call: $method")
            }
        }
        val delete = ProtectionAdapter(ProtectionResource.SERVER, 42, ProtectionField.DELETE, fake)
        val rebuild = ProtectionAdapter(ProtectionResource.SERVER, 42, ProtectionField.REBUILD, fake)
        assertTrue(delete.read().protected)
        assertFalse(rebuild.read().protected)
        delete.applyDesired(false)
        rebuild.applyDesired(true)
        assertEquals(listOf(ServerProtectionPatch(delete = false), ServerProtectionPatch(rebuild = true)), patches)
    }

    @Test fun `stale name flag or immutable ID invalidates protection review`() {
        val reviewed = ProtectionSnapshot(42, "synthetic", true)
        assertThrows(IllegalStateException::class.java) { reviewed.copy(id = 43).requireUnchanged(reviewed) }
        assertThrows(IllegalStateException::class.java) { reviewed.copy(name = "renamed").requireUnchanged(reviewed) }
        assertThrows(IllegalStateException::class.java) { reviewed.copy(protected = false).requireUnchanged(reviewed) }
        reviewed.requireUnchanged(reviewed)
    }

    @Test fun `non-server rebuild adapter is rejected before any API call`() {
        val fake = repository { _, _ -> error("No API call allowed") }
        assertThrows(IllegalArgumentException::class.java) {
            ProtectionAdapter(ProtectionResource.VOLUME, 42, ProtectionField.REBUILD, fake)
        }
    }
}
