package com.hcloudcontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class LocationPresentationTest {
    @Test fun `uses API city with machine code`() {
        assertEquals("Nuremberg (nbg1)", locationDisplayName("nbg1", city = "Nuremberg", description = "Nuremberg DC Park 1"))
    }

    @Test fun `uses API description only when city is absent`() {
        assertEquals("Helsinki (hel1)", locationDisplayName("hel1", description = "Helsinki"))
    }

    @Test fun `keeps unknown location code only`() {
        assertEquals("future1", locationDisplayName("future1"))
    }
}
