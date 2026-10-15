package com.hcloudcontrol.ui.designsystem

import com.hcloudcontrol.ui.navigation.RootDestination
import org.junit.Assert.*
import org.junit.Test

class RootNavigationIconTest {
    @Test fun `all five canonical roots have a distinct code native glyph`() {
        assertEquals(5, RootDestination.entries.size)
        assertEquals(5, RootDestination.entries.map(::rootNavigationGlyph).toSet().size)
        RootDestination.entries.forEach { assertEquals(it.name, rootNavigationGlyph(it).name) }
    }
}
