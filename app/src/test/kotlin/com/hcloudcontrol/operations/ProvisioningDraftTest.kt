package com.hcloudcontrol.operations

import com.hcloudcontrol.core.domain.model.*
import org.junit.Assert.*
import org.junit.Test
import com.hcloudcontrol.PricingState
import com.hcloudcontrol.sameReviewedPrice

class ProvisioningDraftTest {
    private fun type(id: Long, architecture: String, cores: Int = 2, memory: Double = 4.0, disk: Int = 40) =
        ServerTypeOption(id, "type-$id", "Synthetic", cores, memory, disk, "shared", architecture, setOf("nbg1"))

    @Test fun `saved draft restores every nonsecret field and current step`() {
        val draft = ProvisioningDraft("synthetic", 1, 2, 3, setOf(4, 5), false, true, ProvisioningStep.ACCESS)
        assertEquals(draft, ProvisioningDraft.restore(draft.snapshot()))
        assertEquals(ProvisioningDraft(), ProvisioningDraft.restore(listOf("broken")))
    }
    @Test fun `architecture change clears only incompatible image preserving name keys network`() {
        val original = ProvisioningDraft("synthetic", 1, 2, null, setOf(4), false, true)
        val change = original.selectType(type(3, "arm"), ImageOption(2, "ubuntu", "Synthetic", "system", "x86"), null)
        assertNull(change.draft.imageId)
        assertEquals("synthetic", change.draft.name)
        assertEquals(setOf(4L), change.draft.sshKeyIds)
        assertFalse(change.draft.ipv4)
        assertEquals(setOf(DraftReset.IMAGE_ARCHITECTURE), change.resets)
    }
    @Test fun `numeric filters sort by numerical CPU memory disk rather than string ordering`() {
        val entries = listOf(type(3, "x86", 16), type(2, "x86", 4), type(1, "x86", 2), type(4, "arm", 2, 2.0))
        assertEquals(listOf(4L, 1L, 2L, 3L), ServerTypeFilters().apply(entries).map { it.id })
        assertEquals(listOf(1L, 2L), ServerTypeFilters(architecture = "x86", memoryGb = 4.0).apply(entries).filter { it.cores < 16 }.map { it.id })
    }
    @Test fun `filtering catalogue never mutates the selected draft`() {
        val draft = ProvisioningDraft("synthetic", 1, 2, 3)
        assertTrue(ServerTypeFilters(query = "no-match").apply(listOf(type(1, "x86"))).isEmpty())
        assertEquals(1L, draft.typeId)
        assertEquals("synthetic", draft.name)
    }

    @Test fun `equal CPU sorts fractional memory then numerical disk sizes`() {
        val entries = listOf(
            type(1, "x86", memory = 2.0, disk = 100),
            type(2, "x86", memory = 1.5, disk = 100),
            type(3, "x86", memory = 2.0, disk = 20),
            type(4, "x86", memory = 10.0, disk = 10),
        )
        assertEquals(listOf(2L, 3L, 1L, 4L), ServerTypeFilters().apply(entries).map { it.id })
        assertEquals(listOf(3L), ServerTypeFilters(memoryGb = 2.0, diskGb = 20).apply(entries).map { it.id })
    }

    @Test fun `removed catalogue IDs clear only missing selections without choosing alternatives`() {
        val original = ProvisioningDraft("keep-name", 99, 2, 3, setOf(8), false, true, ProvisioningStep.REVIEW)
        val result = reconcileCatalogueDraft(original, listOf(type(1, "x86")),
            listOf(ImageOption(2, "ubuntu", "Synthetic", "system", "x86")), emptyList(), emptyList())
        assertNull(result.draft.typeId)
        assertNull(result.draft.locationId)
        assertEquals(2L, result.draft.imageId)
        assertTrue(result.draft.sshKeyIds.isEmpty())
        assertEquals("keep-name", result.draft.name)
        assertFalse(result.draft.ipv4)
        assertEquals(ProvisioningStep.RESOURCES, result.draft.step)
        assertEquals(setOf(DraftReset.UNAVAILABLE_SELECTION), result.resets)
    }

    @Test fun `refreshed compatibility clears changed architecture without clearing stable type or name`() {
        val original = ProvisioningDraft("keep-name", 1, 2)
        val result = reconcileCatalogueDraft(original, listOf(type(1, "arm")),
            listOf(ImageOption(2, "ubuntu", "Synthetic", "system", "x86")), emptyList(), null)
        assertEquals(1L, result.draft.typeId)
        assertNull(result.draft.imageId)
        assertEquals(setOf(DraftReset.IMAGE_ARCHITECTURE), result.resets)
    }

    @Test fun `withdrawn location availability clears location but never substitutes another`() {
        val original = ProvisioningDraft("keep-name", 1, locationId = 3, step = ProvisioningStep.ACCESS)
        val result = reconcileCatalogueDraft(original, listOf(type(1, "x86").copy(availableLocations = setOf("hel1"))),
            emptyList(), listOf(LocationOption(3, "nbg1", "Synthetic", "Nuremberg", "DE")), null)
        assertEquals(1L, result.draft.typeId)
        assertNull(result.draft.locationId)
        assertEquals(setOf(DraftReset.LOCATION_TYPE), result.resets)
        assertEquals(ProvisioningStep.RESOURCES, result.draft.step)
    }

    @Test fun `late original prefill ID remains unselected until explicitly reapplied from arriving inventory`() {
        val original = ProvisioningDraft("preserved", typeId = 9, imageId = 2)
        val image = ImageOption(2, "ubuntu", "Synthetic", "system", "x86")
        val unavailable = reconcileCatalogueDraft(original, listOf(type(1, "x86")), listOf(image), emptyList(), null)
        assertNull(unavailable.draft.typeId)
        val restored = ProvisioningDraft.restore(unavailable.draft.snapshot())
        val arriving = type(9, "x86")
        val reloaded = reconcileCatalogueDraft(restored, listOf(type(1, "x86"), arriving), listOf(image), emptyList(), null)
        assertNull(reloaded.draft.typeId) // Merely arriving never silently picks an alternative.
        val reappliedOriginal = reloaded.draft.selectType(arriving, image, null)
        assertEquals(9L, reappliedOriginal.draft.typeId)
        assertEquals("preserved", reappliedOriginal.draft.name)
        assertEquals(2L, reappliedOriginal.draft.imageId)
        assertTrue(reappliedOriginal.resets.isEmpty())
    }

    @Test fun `restored draft cannot cross project or environment boundary`() {
        val original = OperationContext("one", "https://api.hetzner.cloud/v1", OperationContext.Environment.CLOUD)
        val draft = ProvisioningDraft("synthetic", 1, 2, 3, setOf(4))
        assertEquals(draft, ProvisioningDraft.restoreFor(original, draft.snapshotFor(original)))
        assertEquals(ProvisioningDraft(), ProvisioningDraft.restoreFor(original.copy(connectionId = "two"), draft.snapshotFor(original)))
        assertEquals(ProvisioningDraft(), ProvisioningDraft.restoreFor(original.copy(environment = OperationContext.Environment.TEST), draft.snapshotFor(original)))
    }

    @Test fun `changed or newly known price invalidates previous review`() {
        val quote = ServerTypeLocationPrice(1, "synthetic", "nbg1", "EUR", "0", MoneyAmount("0.01", "0.01"), MoneyAmount("5", "5"))
        assertTrue(sameReviewedPrice(PricingState.Available(quote), PricingState.Available(quote)))
        assertFalse(sameReviewedPrice(PricingState.Available(quote), PricingState.Available(quote.copy(currency = "USD"))))
        assertFalse(sameReviewedPrice(PricingState.Unavailable, PricingState.Available(quote)))
        assertFalse(sameReviewedPrice(PricingState.Available(quote), PricingState.Failed))
        assertTrue(sameReviewedPrice(PricingState.Unavailable, PricingState.Failed))
    }
}
