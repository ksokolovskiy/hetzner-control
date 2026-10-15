package com.hcloudcontrol

import com.hcloudcontrol.ui.state.*
import org.junit.Assert.*
import org.junit.Test

class SectionStateTest {
    @Test fun `refresh preserves last successful inventory and timestamp then marks stale on failure`() {
        val loaded = SectionState<List<String>>().succeed(listOf("server-7"), 100)
        assertTrue(loaded.canOfferMutation)
        val refreshing = loaded.startRead()
        assertEquals(loaded.data, refreshing.data)
        assertEquals(100L, refreshing.updatedAtMillis)
        assertTrue(refreshing.refreshing)
        assertFalse(refreshing.canOfferMutation)
        val failed = refreshing.fail(SectionReadError("offline"))
        assertTrue(failed.isStale)
        assertEquals(loaded.data, failed.data)
        assertEquals(100L, failed.updatedAtMillis)
        assertFalse(failed.canOfferMutation)
        assertFalse(failed.refreshing)
    }

    @Test fun `first failure differs from successful empty response`() {
        val initial = SectionState<List<String>>().startRead()
        assertEquals(SectionPhase.LOADING, initial.phase)
        assertEquals(SectionPhase.ERROR, initial.fail(SectionReadError("forbidden", true)).phase)
        val empty = initial.succeed(emptyList(), 1, EmptyReason.INVENTORY)
        assertEquals(SectionPhase.EMPTY, empty.phase)
        assertTrue(empty.canOfferMutation)
        assertTrue(empty.hasSuccessfulResponse)
        assertEquals(SectionPhase.STALE, empty.fail(SectionReadError("offline")).phase)
    }

    @Test fun `independent history failure cannot change metadata section`() {
        val metadata = SectionState<String>().succeed("server-7", 100)
        val history = SectionState<List<String>>().startRead().fail(SectionReadError("unavailable"))
        assertEquals(SectionPhase.READY, metadata.phase)
        assertEquals(SectionPhase.ERROR, history.phase)
        assertNull(history.updatedAtMillis)
    }

    @Test fun `no connection does not enable create`() {
        assertFalse(SectionState<List<String>>().succeed(emptyList(), 1, EmptyReason.NO_CONNECTION).canOfferMutation)
    }

    @Test fun `catalogue pricing or location failures preserve independent type metadata`() {
        val types = SectionState<List<Long>>().succeed(listOf(17L), 100)
        val prices = SectionState<String>().startRead().fail(SectionReadError("unavailable"))
        val locations = SectionState<List<Long>>().succeed(listOf(3L), 90)
            .startRead().fail(SectionReadError("offline"))
        assertEquals(listOf(17L), types.data)
        assertTrue(types.canOfferMutation)
        assertEquals(SectionPhase.ERROR, prices.phase)
        assertTrue(locations.isStale)
        assertEquals(listOf(3L), locations.data)
        assertEquals(90L, locations.updatedAtMillis)
    }
}
