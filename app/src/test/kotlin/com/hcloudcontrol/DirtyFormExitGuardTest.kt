package com.hcloudcontrol

import com.hcloudcontrol.ui.navigation.DirtyFormExitGuard
import org.junit.Assert.*
import org.junit.Test

class DirtyFormExitGuardTest {
    @Test fun `dirty Back waits for choice and retain preserves draft before leaving`() {
        val events = mutableListOf<String>()
        val guard = DirtyFormExitGuard({ true }, { false }, { events += "retain" }, { events += "discard" }, { events += "leave" })
        guard.requestLeave()
        assertTrue(guard.awaitingChoice)
        assertTrue(events.isEmpty())
        guard.retainAndLeave()
        guard.retainAndLeave()
        assertEquals(listOf("retain", "leave"), events)
    }

    @Test fun `discard is explicit and cancel keeps editing without leaving`() {
        val events = mutableListOf<String>()
        val guard = DirtyFormExitGuard({ true }, { false }, { events += "retain" }, { events += "discard" }, { events += "leave" })
        guard.requestLeave(); guard.stay()
        assertTrue(events.isEmpty())
        assertFalse(guard.awaitingChoice)
        guard.requestLeave(); guard.discardAndLeave()
        assertEquals(listOf("discard", "leave"), events)
    }

    @Test fun `submitted operation Close does not cancel discard retain or replay anything`() {
        val events = mutableListOf<String>()
        val guard = DirtyFormExitGuard({ true }, { true }, { events += "retain" }, { events += "discard" }, { events += "close" })
        guard.requestLeave()
        assertEquals(listOf("close"), events)
        assertFalse(guard.awaitingChoice)
    }

    @Test fun `unchanged form leaves directly`() {
        var left = false
        val guard = DirtyFormExitGuard({ false }, { false }, {}, {}, { left = true })
        guard.requestLeave()
        assertTrue(left)
        assertFalse(guard.awaitingChoice)
    }

    @Test fun `snapshot description retain preserves safe draft while discard clears only on explicit choice`() {
        var description = "Synthetic snapshot description"
        var retained: String? = null
        var leaves = 0
        val guard = DirtyFormExitGuard({ description.isNotEmpty() }, { false },
            { retained = description }, { description = "" }, { leaves++ })
        guard.requestLeave()
        assertEquals("Synthetic snapshot description", description)
        guard.stay()
        assertEquals(0, leaves)
        guard.requestLeave(); guard.retainAndLeave()
        assertEquals(description, retained)
        assertEquals(1, leaves)
        guard.requestLeave(); guard.discardAndLeave()
        assertEquals("", description)
        assertEquals(2, leaves)
    }
}
