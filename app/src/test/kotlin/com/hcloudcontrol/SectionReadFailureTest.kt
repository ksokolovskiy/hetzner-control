package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.HCloudApiException
import com.hcloudcontrol.core.domain.model.HCloudApiFailure
import com.hcloudcontrol.core.network.ObjectStorageRequestException
import com.hcloudcontrol.ui.designsystem.SectionKind
import com.hcloudcontrol.ui.designsystem.presentationKind
import com.hcloudcontrol.ui.state.*
import java.io.IOException
import kotlinx.coroutines.CancellationException
import org.junit.Assert.*
import org.junit.Test

class SectionReadFailureTest {
    @Test fun `S3 typed authentication and permission failures use shared sanitized section kinds`() {
        listOf(401 to SectionKind.AUTHENTICATION, 403 to SectionKind.DENIED).forEach { (status, expected) ->
            val failure = sectionReadFailure(ObjectStorageRequestException("untrusted S3 payload and credentials", status))
            assertEquals(expected, SectionState<String>().fail(failure).presentationKind())
            assertFalse(failure.toString().contains("payload"))
            assertFalse(failure.toString().contains("credentials"))
        }
    }
    @Test fun `S3 missing maps to missing and failed refresh retains previous inventory timestamp`() {
        val failure = sectionReadFailure(ObjectStorageRequestException("untrusted XML", 404))
        assertEquals(SectionKind.MISSING, SectionState<List<String>>().fail(failure).presentationKind())
        val stale = SectionState<List<String>>().succeed(listOf("fixture-bucket"), 123).startRead().fail(failure)
        assertEquals(listOf("fixture-bucket"), stale.data)
        assertEquals(123L, stale.updatedAtMillis)
        assertFalse(stale.canOfferMutation)
        assertFalse(failure.toString().contains("XML"))
    }
    @Test fun `first read maps provider status to explicit safe section states`() {
        listOf(
            401 to SectionKind.AUTHENTICATION,
            403 to SectionKind.DENIED,
            404 to SectionKind.MISSING,
            500 to SectionKind.ERROR,
        ).forEach { (status, expected) ->
            val exception = HCloudApiException(HCloudApiFailure(status, "untrusted", "secret-provider-message", emptyList()))
            val failure = sectionReadFailure(exception)
            assertEquals(expected, SectionState<String>().startRead().fail(failure).presentationKind())
            assertFalse(failure.toString().contains("secret-provider-message"))
            assertFalse(failure.toString().contains("untrusted"))
        }
    }

    @Test fun `offline is distinct from denied or invalid token`() {
        val failure = sectionReadFailure(IOException("raw secret network details"))
        assertEquals(SectionReadFailureKind.OFFLINE, failure.kind)
        assertEquals(SectionKind.OFFLINE, SectionState<String>().fail(failure).presentationKind())
        assertFalse(failure.permissionDenied)
        assertFalse(failure.toString().contains("secret"))
    }

    @Test fun `missing on refresh preserves old content but blocks unsafe changes`() {
        val previous = SectionState<String>().succeed("cached-resource", 123)
        val stale = previous.startRead().fail(SectionReadError("missing"))
        assertEquals(SectionKind.CONTENT, stale.presentationKind())
        assertEquals("cached-resource", stale.data)
        assertEquals(123L, stale.updatedAtMillis)
        assertEquals(SectionReadFailureKind.MISSING, stale.error?.kind)
        assertTrue(stale.isStale)
        assertFalse(stale.canOfferMutation)
    }

    @Test(expected = CancellationException::class)
    fun `cancelled read is never converted into a failure state`() {
        sectionReadFailure(CancellationException("cancelled"))
    }
}
