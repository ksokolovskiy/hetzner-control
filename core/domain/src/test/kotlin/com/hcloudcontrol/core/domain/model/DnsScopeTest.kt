package com.hcloudcontrol.core.domain.model

import org.junit.Assert.assertTrue
import org.junit.Test

class DnsScopeTest {
    @Test
    fun `DNS scope is explicitly acknowledged and read-only`() {
        assertTrue(DnsScope.acknowledgeReadOnly().readOnlyAcknowledged)
    }
}
