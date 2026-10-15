package com.hcloudcontrol

import com.hcloudcontrol.connections.*
import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudApiFailure
import com.hcloudcontrol.core.domain.model.HCloudConnectionConfig
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class VerifiedCloudConnectionCoordinatorTest {
    private val config = HCloudConnectionConfig.testMock()
    private val fixtureToken = "synthetic-token"

    @Test fun `successful safe read precedes protected save and preserves tested routing`() = runBlocking {
        val calls = mutableListOf<String>()
        val coordinator = VerifiedCloudConnectionCoordinator(
            { token, endpoint -> assertEquals(fixtureToken, token); assertEquals(config, endpoint); calls += "read" },
            { name, token, endpoint -> assertEquals("Example", name); assertEquals(fixtureToken, token); assertEquals(config, endpoint); calls += "save"; "connection-7" },
        )
        val phases = mutableListOf<ConnectionCheckPhase>()
        assertEquals(VerifiedConnectionResult.Connected("connection-7"), coordinator.connect(" Example ", fixtureToken, config) { phases += it })
        assertEquals(listOf("read", "save"), calls)
        assertEquals(listOf(ConnectionCheckPhase.CHECKING, ConnectionCheckPhase.SAVING), phases)
    }

    @Test fun `no save occurs when offline or provider read denied`() = runBlocking {
        listOf(
            IOException("raw confidential message") to ConnectionFailureKind.OFFLINE,
            apiFailure(401) to ConnectionFailureKind.INVALID_TOKEN,
            apiFailure(403) to ConnectionFailureKind.PERMISSION_DENIED,
            apiFailure(503) to ConnectionFailureKind.SERVICE,
        ).forEach { (failure, kind) ->
            var saves = 0
            val coordinator = VerifiedCloudConnectionCoordinator({ _, _ -> throw failure }, { _, _, _ -> saves++; "unexpected" })
            val result = coordinator.connect("Example", fixtureToken, config) as VerifiedConnectionResult.Failed
            assertEquals(kind, result.failure.kind)
            assertEquals(0, saves)
            assertFalse(result.toString().contains("confidential"))
        }
    }

    @Test fun `storage failure cannot claim connection saved`() = runBlocking {
        val coordinator = VerifiedCloudConnectionCoordinator({ _, _ -> }, { _, _, _ -> error("secret store detail") })
        assertEquals(VerifiedConnectionResult.Failed(ConnectionFailure(ConnectionFailureKind.SECURE_STORAGE)), coordinator.connect("Example", fixtureToken, config))
    }

    @Test fun `double submit while checking sends only one read and save`() = runBlocking {
        val release = CompletableDeferred<Unit>()
        var reads = 0
        var saves = 0
        val coordinator = VerifiedCloudConnectionCoordinator({ _, _ -> reads++; release.await() }, { _, _, _ -> saves++; "saved" })
        val first = async(start = CoroutineStart.UNDISPATCHED) { coordinator.connect("Example", fixtureToken, config) }
        assertEquals(VerifiedConnectionResult.AlreadyChecking, coordinator.connect("Example", fixtureToken, config))
        assertEquals(1, reads)
        assertEquals(0, saves)
        release.complete(Unit)
        assertEquals(VerifiedConnectionResult.Connected("saved"), first.await())
        assertEquals(1, saves)
    }

    @Test fun `cancelling pending check saves nothing and releases lock for retry`() = runBlocking {
        val waiting = CompletableDeferred<Unit>()
        var saves = 0
        val coordinator = VerifiedCloudConnectionCoordinator({ _, _ -> waiting.await() }, { _, _, _ -> saves++; "saved" })
        val cancelled = launch(start = CoroutineStart.UNDISPATCHED) { coordinator.connect("Example", fixtureToken, config) }
        cancelled.cancelAndJoin()
        assertEquals(0, saves)
        waiting.complete(Unit)
        assertEquals(VerifiedConnectionResult.Connected("saved"), coordinator.connect("Example", fixtureToken, config))
        assertEquals(1, saves)
    }

    @Test fun `cancellation is never represented as failure`() = runBlocking {
        val coordinator = VerifiedCloudConnectionCoordinator({ _, _ -> throw CancellationException("cancel") }, { _, _, _ -> fail("must not save"); "" })
        var cancelled = false
        try { coordinator.connect("Example", fixtureToken, config) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
    }

    @Test fun `invalid input does not read or save`() = runBlocking {
        val coordinator = VerifiedCloudConnectionCoordinator({ _, _ -> fail("must not read") }, { _, _, _ -> fail("must not save"); "" })
        assertEquals(VerifiedConnectionResult.Failed(ConnectionFailure(ConnectionFailureKind.INVALID_INPUT)), coordinator.connect("", fixtureToken, config))
    }

    @Test fun `failure result discards provider message and unsafe metadata`() = runBlocking {
        val failure = HCloudApiException(HCloudApiFailure(401, "Bearer token value", fixtureToken, emptyList(), "bad\nheader"))
        val coordinator = VerifiedCloudConnectionCoordinator({ _, _ -> throw failure }, { _, _, _ -> "" })
        val result = coordinator.connect("Example", fixtureToken, config) as VerifiedConnectionResult.Failed
        assertEquals(ConnectionFailureKind.INVALID_TOKEN, result.failure.kind)
        assertNull(result.failure.code)
        assertNull(result.failure.requestId)
        assertFalse(result.toString().contains(fixtureToken))
    }

    private fun apiFailure(status: Int) = HCloudApiException(HCloudApiFailure(status, "fixture_error", "confidential", emptyList(), "request-7"))
}
