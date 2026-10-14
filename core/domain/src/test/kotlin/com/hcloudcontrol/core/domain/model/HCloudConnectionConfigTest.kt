package com.hcloudcontrol.core.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HCloudConnectionConfigTest {
    @Test
    fun exposesOnlyRealMode() {
        assertEquals(ConnectionKind.REAL, HCloudConnectionConfig.real().kind)
    }
}
