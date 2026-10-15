package com.hcloudcontrol.ui.navigation

import org.junit.Assert.*
import org.junit.Test

class ContextualTextDraftTest {
    @Test fun `safe text roundtrip rejects foreign profile endpoint field and malformed version`() {
        val scope = "project-a:https://api.example:primary:9:name"
        val saved = arrayListOf("1", scope, "public fixture")
        assertEquals("public fixture", restoreContextualText(scope, saved))
        assertEquals("", restoreContextualText(scope.replace("project-a", "project-b"), saved))
        assertEquals("", restoreContextualText(scope.replace("api.example", "other.example"), saved))
        assertEquals("", restoreContextualText(scope.replace(":name", ":labels"), saved))
        assertEquals("ipv4", restoreContextualText(scope, listOf("bad"), "ipv4"))
        assertEquals("", restoreContextualText(scope, listOf("2", scope, "stale")))
    }
}
