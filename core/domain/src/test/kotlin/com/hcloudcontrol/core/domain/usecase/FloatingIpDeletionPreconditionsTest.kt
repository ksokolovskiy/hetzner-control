package com.hcloudcontrol.core.domain.usecase

import com.hcloudcontrol.core.domain.model.FloatingIpInventory
import org.junit.Assert.assertTrue
import org.junit.Test

class FloatingIpDeletionPreconditionsTest {
    @Test
    fun `only an unassigned and unprotected floating IP passes deletion preconditions`() {
        val allowed = floatingIp()
        requireFloatingIpDeletionPreconditions(allowed)

        val failures = listOf(
            runCatching { requireFloatingIpDeletionPreconditions(allowed.copy(id = 0)) },
            runCatching { requireFloatingIpDeletionPreconditions(allowed.copy(assigneeId = 42)) },
            runCatching { requireFloatingIpDeletionPreconditions(allowed.copy(deleteProtected = true)) },
        )

        assertTrue(failures.all { it.exceptionOrNull() is IllegalArgumentException })
    }

    private fun floatingIp() = FloatingIpInventory(
        id = 601,
        name = "egress",
        type = "ipv4",
        address = "192.0.2.15",
        homeLocation = "fsn1",
        assigneeId = null,
        assigneeType = null,
        deleteProtected = false,
        labels = emptyMap(),
        dnsPointers = emptyList(),
    )
}
