package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.CloudAction
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudActionAwaiterTest {
    @Test
    fun `terminal server action completion returns to the caller without polling another action`() = runBlocking {
        val observedStatuses = mutableListOf<String>()
        val requestedActionIds = mutableListOf<Long>()
        val initial = CloudAction(id = 77, status = "running")

        awaitCloudAction(
            initial = initial,
            getAction = { actionId ->
                requestedActionIds += actionId
                CloudAction(id = actionId, status = "success")
            },
            onProgress = observedStatuses::add,
            delayMillis = 0,
        )

        assertEquals(listOf("running", "success"), observedStatuses)
        assertEquals(listOf(77L), requestedActionIds)
    }

    @Test
    fun `terminal action error remains an error for the current workflow`() = runBlocking {
        val error = runCatching {
            awaitCloudAction(
                initial = CloudAction(id = 78, status = "error"),
                getAction = { error("A terminal error must not be polled") },
                onProgress = {},
                delayMillis = 0,
            )
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Hetzner power action failed", error?.message)
    }
}
