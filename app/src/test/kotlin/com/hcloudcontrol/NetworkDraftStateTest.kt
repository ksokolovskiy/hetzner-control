package com.hcloudcontrol

import com.hcloudcontrol.network.*
import com.hcloudcontrol.core.domain.model.PrivateNetworkRouteSpec
import org.junit.Assert.*
import org.junit.Test

class NetworkDraftStateTest {
    @Test fun `safe topology draft restores only in the same original context`() {
        val scope = "first:https://example.invalid:network:7"
        val draft = NetworkMutationDraft(NetworkMutationKind.DELETE_ROUTE, route = PrivateNetworkRouteSpec("10.2.0.0/16", "10.0.1.2"))
        val saved = networkDraftSnapshot(scope, draft)
        assertEquals(draft, restoreNetworkDraft(scope, saved))
        assertNull(restoreNetworkDraft("second:https://example.invalid:network:7", saved))
        assertNull(restoreNetworkDraft("first:https://other.invalid:network:7", saved))
    }

    @Test fun `malformed exact-entry deletion and arbitrary IDs fail closed`() {
        val saved = networkDraftSnapshot("scope", NetworkMutationDraft(NetworkMutationKind.DETACH, serverId = 42))
        saved[10] = "-1"
        assertNull(restoreNetworkDraft("scope", saved))
        assertNull(restoreNetworkDraft("scope", networkDraftSnapshot("scope", NetworkMutationDraft(NetworkMutationKind.DELETE_SUBNET))))
    }
}
