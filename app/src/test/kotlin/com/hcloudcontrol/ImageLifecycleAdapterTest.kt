package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.lang.reflect.Proxy

class ImageLifecycleAdapterTest {
    private val image = CloudImageInventory(42, "fixture", "Synthetic image", "snapshot", "x86", "available",
        1.0, 20.0, "2026-10-15", false, null, null)
    private fun repository(call: (String, Array<out Any?>) -> Any?): HCloudRepository = Proxy.newProxyInstance(
        HCloudRepository::class.java.classLoader, arrayOf(HCloudRepository::class.java),
    ) { _, method, args -> call(method.name, args ?: emptyArray()) } as HCloudRepository

    @Test fun `official image API permits backup deletion but protection only for snapshots`() {
        assertTrue(image.canDeleteImage()); assertTrue(image.canProtectImage())
        assertTrue(image.copy(type = "backup").canDeleteImage())
        assertFalse(image.copy(type = "backup").canProtectImage())
        assertFalse(image.copy(type = "system").canDeleteImage())
        assertFalse(image.copy(type = "system").canProtectImage())
        assertFalse(image.copy(deleteProtected = true).canDeleteImage())
        assertFalse(image.copy(deleteProtected = null).canDeleteImage())
        assertFalse(image.copy(status = "creating").canDeleteImage())
    }

    @Test fun `changed provider protection invalidates destructive review before mutation`() = runBlocking {
        var writes = 0
        val fake = repository { method, _ -> when (method) {
            "getImage" -> image.copy(deleteProtected = true)
            "deleteImage" -> { writes++; Unit }
            else -> error("Unexpected call")
        } }
        assertTrue(runCatching { ImageDeletionAdapter(fake, image).validateFresh() }.isFailure)
        assertEquals(0, writes)
    }

    @Test fun `deletion reconciliation requires authoritative missing response not arbitrary network failure`() = runBlocking {
        val fake = repository { _, args ->
            assertEquals(42L, args[0])
            throw HCloudApiException(HCloudApiFailure(404, "not_found", null, emptyList()))
        }
        assertTrue(ImageDeletionAdapter(fake, image).deleted())
        val failedRead = repository { _, _ -> throw HCloudApiException(HCloudApiFailure(503, "unavailable", null, emptyList())) }
        assertTrue(runCatching { ImageDeletionAdapter(failedRead, image).deleted() }.isFailure)
    }

    @Test fun `shared image protection adapter never updates other flags or permits backup protection`() = runBlocking {
        var patch: ImageProtectionPatch? = null
        val fake = repository { method, args -> when (method) {
            "getImage" -> image
            "changeImageProtection" -> { patch = args[1] as ImageProtectionPatch; CloudAction(88, "running") }
            else -> error("Unexpected call")
        } }
        val adapter = ProtectionAdapter(ProtectionResource.IMAGE, 42, ProtectionField.DELETE, fake)
        assertFalse(adapter.read().protected)
        adapter.applyDesired(true)
        assertEquals(ImageProtectionPatch(true), patch)
        val backup = repository { _, _ -> image.copy(type = "backup") }
        assertTrue(runCatching { ProtectionAdapter(ProtectionResource.IMAGE, 42, ProtectionField.DELETE, backup).read() }.isFailure)
    }
}
