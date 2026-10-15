package com.hcloudcontrol.ui.state

import com.hcloudcontrol.ui.designsystem.*
import org.junit.Assert.*
import org.junit.Test

class ServerInventoryReadPresentationTest {
    @Test fun `inventory distinguishes authentication permission offline missing and service failures`() {
        listOf("authentication" to SectionKind.AUTHENTICATION, "denied" to SectionKind.DENIED,
            "offline" to SectionKind.OFFLINE, "missing" to SectionKind.MISSING, "read_failed" to SectionKind.ERROR).forEach { (code, expected) ->
            assertEquals(expected, SectionState<List<String>>().fail(SectionReadError(code)).presentationKind())
        }
    }
    @Test fun `failed refresh retains successful data and typed failure without changing it to an empty inventory`() {
        val cached = SectionState<List<String>>().succeed(listOf("fixture"), 12)
        val stale = cached.startRead().fail(SectionReadError("denied", true))
        assertEquals(SectionKind.CONTENT, stale.presentationKind())
        assertEquals(listOf("fixture"), stale.data)
        assertEquals(12L, stale.updatedAtMillis)
        assertFalse(stale.canOfferMutation)
        assertEquals(SectionReadFailureKind.DENIED, stale.error?.kind)
    }
}
