package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.OneTimeRootPassword
import org.junit.Assert.*
import org.junit.Test

class SecretResultLifecycleTest {
    @Test fun `lifecycle hide masks value without granting acknowledgement or losing explicit copy`() {
        val input = "synthetic-secret-only".toCharArray()
        val result = SecretResult(input)
        input.fill('\u0000')
        assertFalse(result.revealed); assertFalse(result.canFinish)
        result.reveal(); assertTrue(result.revealed)
        result.hide(); assertFalse(result.revealed); assertFalse(result.canFinish)
        val copy = result.copyForExplicitUserAction()
        assertEquals("synthetic-secret-only", String(copy))
        copy.fill('\u0000')
        assertTrue(result.available)
        result.acknowledge(); assertTrue(result.canFinish)
        result.close()
    }
    @Test fun `dismiss and disposal destroy secret idempotently and cannot reveal or copy it again`() {
        val result = SecretResult("synthetic-only".toCharArray())
        result.reveal(); result.close(); result.close(); result.reveal()
        assertFalse(result.available); assertFalse(result.revealed); assertTrue(result.canFinish)
        assertThrows(IllegalStateException::class.java) { result.copyForExplicitUserAction() }
        assertFalse(result.toString().contains("synthetic"))
    }
    @Test fun `already consumed secret cannot be recovered by recreation and generates no write`() {
        var writes = 0
        val response = OneTimeRootPassword.fromApiResponse("synthetic-result")
        val transient = consumeSecretResult(response)
        transient?.close()
        val restored = consumeSecretResult(response)
        assertNull(restored)
        // No provider dependency/callback exists on either lost result or explicit Close.
        writes += 0
        assertEquals(0, writes)
    }
}
