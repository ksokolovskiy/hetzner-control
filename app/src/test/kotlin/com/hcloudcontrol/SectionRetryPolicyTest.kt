package com.hcloudcontrol

import com.hcloudcontrol.ui.designsystem.SectionKind
import com.hcloudcontrol.ui.designsystem.SectionPresentation
import com.hcloudcontrol.ui.designsystem.shouldOfferSectionRetry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SectionRetryPolicyTest {
    @Test fun healthyContentDoesNotOfferRecovery() {
        assertFalse(shouldOfferSectionRetry(SectionPresentation(SectionKind.CONTENT, updatedAt = "12:00")))
    }

    @Test fun staleContentRetainsExplicitRecovery() {
        assertTrue(shouldOfferSectionRetry(SectionPresentation(SectionKind.CONTENT, stale = true)))
    }

    @Test fun FailedAndEmptyReadsOfferRecovery() {
        listOf(SectionKind.ERROR, SectionKind.OFFLINE, SectionKind.DENIED,
            SectionKind.AUTHENTICATION, SectionKind.MISSING, SectionKind.EMPTY,
            SectionKind.NO_RESULTS, SectionKind.NO_ELIGIBLE, SectionKind.NO_CONNECTION).forEach {
            assertTrue("Expected retry for $it", shouldOfferSectionRetry(SectionPresentation(it)))
        }
    }

    @Test fun loadingDoesNotOfferDuplicateRead() {
        assertFalse(shouldOfferSectionRetry(SectionPresentation(SectionKind.LOADING)))
        assertFalse(shouldOfferSectionRetry(SectionPresentation(SectionKind.LOADING, stale = true)))
    }

    @Test fun refreshingDoesNotOfferDuplicateRead() {
        SectionKind.entries.forEach {
            assertFalse("No duplicate retry while refreshing $it",
                shouldOfferSectionRetry(SectionPresentation(it, stale = true, refreshing = true)))
        }
    }
}
