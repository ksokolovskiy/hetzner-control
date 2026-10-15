package com.hcloudcontrol

import com.hcloudcontrol.ui.navigation.resourceSelectionSnapshot
import com.hcloudcontrol.ui.navigation.restoreResourceSelection
import com.hcloudcontrol.ui.navigation.restoreContextualFlag
import org.junit.Assert.*
import org.junit.Test

class ContextualResourceSelectionTest {
    private val scope = "server:project-a:https://cloud.example/v1"

    @Test fun `process recreation restores only the same context and resource type`() {
        val saved = resourceSelectionSnapshot(scope, 17)
        assertEquals(17L, restoreResourceSelection(scope, saved))
        assertNull(restoreResourceSelection(scope.replace("project-a", "project-b"), saved))
        assertNull(restoreResourceSelection(scope.replace("cloud.example", "mock.example"), saved))
        assertNull(restoreResourceSelection(scope.replace("server:", "volume:"), saved))
    }

    @Test fun `no selection and malformed or obsolete snapshots restore safely`() {
        assertNull(restoreResourceSelection(scope, resourceSelectionSnapshot(scope, null)))
        listOf(emptyList(), listOf("resource-id-v0", scope, "17"),
            listOf("resource-id-v1", scope, "-1"), listOf("resource-id-v1", scope, "0"),
            listOf("resource-id-v1", scope, "NaN"), listOf("resource-id-v1", scope)).forEach {
            assertNull(restoreResourceSelection(scope, it))
        }
    }

    @Test fun `form visibility restores only within its original context`() {
        val saved = listOf("context-flag-v1", scope, "true")
        assertTrue(restoreContextualFlag(scope, saved))
        assertFalse(restoreContextualFlag("other-project", saved))
        assertFalse(restoreContextualFlag(scope, listOf("obsolete", scope, "true")))
        assertFalse(restoreContextualFlag(scope, listOf("context-flag-v1", scope, "false")))
    }
}
