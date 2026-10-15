package com.hcloudcontrol

import com.hcloudcontrol.ui.designsystem.*
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class CostPresentationTest {
    @Test fun `provider VAT percentage is not multiplied by one hundred`() {
        assertEquals("19", formatCostVat("19.00", Locale.US))
        assertEquals("0", formatCostVat("0.00", Locale.US))
        assertNull(formatCostVat(null))
    }
    @Test fun `missing and invalid monetary values never become zero`() {
        assertNull(formatCostAmount(null))
        assertNull(formatCostAmount("bad"))
        assertNull(formatCostAmount("-1"))
    }

    @Test fun `rates are localized and precision bounded without hiding positive tiny costs`() {
        assertEquals("5,99", formatCostAmount("5.990000000000000", Locale.GERMANY, 2))
        assertEquals("0.0096", formatCostAmount("0.009600000000000", Locale.US))
        assertEquals("<0.000001", formatCostAmount("0.000000001", Locale.US))
        assertEquals("0.00", formatCostAmount("0", Locale.US))
    }

    @Test fun `missing ancillary component keeps known provider rates incomplete`() {
        val price = CostSnapshot("cx", "nbg1", "EUR", "0.01", "5.99", "0", "2026-10-15",
            listOf(CostComponent("IPv4", CostComponentCoverage.UNKNOWN)))
        assertTrue(price.incomplete)
        assertEquals("5.99", price.monthly)
        assertFalse(price.copy(components = listOf(CostComponent("IPv4", CostComponentCoverage.EXCLUDED))).incomplete)
        assertTrue(price.copy(monthly = null).incomplete)
        assertTrue(price.copy(currency = "").incomplete)
    }
}
