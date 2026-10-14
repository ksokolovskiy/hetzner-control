package com.hcloudcontrol

import org.junit.Assert.assertEquals
import org.junit.Test

class PriceFormatterTest {
    @Test
    fun `formats API decimals without redundant trailing zeros`() {
        assertEquals("5,49", formatApiMoney("5.4900000000"))
        assertEquals("6,50", formatApiMoney("6.5000000000"))
    }

    @Test
    fun `preserves small hourly API prices without rounding them up`() {
        assertEquals("0,0096", formatApiMoney("0.0096000000"))
        assertEquals("0,0083", formatApiMoney("0.0083000000"))
    }

    @Test
    fun `returns an invalid API value unchanged instead of calculating a price`() {
        assertEquals("unavailable", formatApiMoney("unavailable"))
    }
}
