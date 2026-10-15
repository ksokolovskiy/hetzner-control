package com.hcloudcontrol.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class ServerResourceLinkTest {
    private val scope = "project-a:https://api.example:server:41:linked-resource"
    @Test fun `each supported type restores exact ID without addresses or mutable metadata`() {
        ServerLinkedResource.entries.forEach { type ->
            val link = ServerResourceLink(type, 57)
            assertEquals(link, ServerResourceLink.restore(scope, link.snapshot(scope)))
        }
    }
    @Test fun `foreign project endpoint parent or unsupported type is rejected`() {
        val saved = ServerResourceLink(ServerLinkedResource.PRIMARY_IP, 57).snapshot(scope)
        listOf(scope.replace("project-a", "project-b"), scope.replace("api.example", "other.example"), scope.replace("server:41", "server:42"))
            .forEach { assertNull(ServerResourceLink.restore(it, saved)) }
        assertNull(ServerResourceLink.restore(scope, listOf("server-link-v1", scope, "IP_FROM_ADDRESS", "57")))
        assertNull(ServerResourceLink.restore(scope, listOf("server-link-v1", scope, "VOLUME", "0")))
        assertNull(ServerResourceLink.restore(scope, listOf("bad")))
    }
}
