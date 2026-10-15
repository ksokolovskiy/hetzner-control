package com.hcloudcontrol

import com.hcloudcontrol.core.domain.model.LoadBalancerTypeInventory
import com.hcloudcontrol.ui.designsystem.CostSnapshot
import org.junit.Assert.*
import org.junit.Test

class LoadBalancerCataloguePresentationTest {
    private val fixture = LoadBalancerTypeInventory(7, "fixture", null,
        null, null, null, null, null, null, null, null, null, null, emptyList())
    @Test fun `catalogue and detail share all limits without synthesizing missing zeroes`() {
        assertEquals(10, loadBalancerTypeLimits(fixture).size)
        assertTrue(loadBalancerTypeLimits(fixture).all { it.second == null })
        val limits = loadBalancerTypeLimits(fixture.copy(maxServices = 0, maxTargets = 25)).toMap()
        assertEquals(0, limits[R.string.load_balancer_types_limit_services])
        assertEquals(25, limits[R.string.load_balancer_types_limit_targets])
        assertNull(limits[R.string.load_balancer_types_limit_connections])
    }
    @Test fun `project currency and VAT are required no catalogue currency fallback`() {
        val withoutProjectRates = CostSnapshot("fixture", "nbg1", "", "0.01", "5.99", null, "fixture-time")
        assertTrue(withoutProjectRates.incomplete)
        assertEquals("", withoutProjectRates.currency)
        assertFalse(withoutProjectRates.copy(currency = "EUR", vatRate = "19.00").incomplete)
    }
}
