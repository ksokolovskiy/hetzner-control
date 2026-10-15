package com.hcloudcontrol

import com.hcloudcontrol.ui.designsystem.SectionKind
import org.junit.Assert.*
import org.junit.Test

class CatalogueSelectionPresentationTest {
    @Test fun `search matches names descriptions and human location labels without case sensitivity`() {
        assertTrue(catalogueMatches(" HEL sinki ", "hel1", "Helsinki"))
        assertTrue(catalogueMatches("arm dedicated", "CAX", "Dedicated ARM"))
        assertFalse(catalogueMatches("Berlin", "nbg1", "Nuremberg"))
        assertTrue(catalogueMatches("   ", "cx23"))
    }

    @Test fun `read failure and initial loading never claim no matching or eligible targets`() {
        assertNull(cataloguePickerEmptyKind(false, 0, 0, 0))
        assertNull(cataloguePickerEmptyKind(true, 0, 0, 0))
        assertEquals(SectionKind.NO_ELIGIBLE, cataloguePickerEmptyKind(true, 4, 0, 0))
        assertEquals(SectionKind.NO_RESULTS, cataloguePickerEmptyKind(true, 4, 3, 0))
        assertNull(cataloguePickerEmptyKind(true, 4, 3, 1))
    }
}
