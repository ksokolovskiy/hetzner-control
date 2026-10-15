package com.hcloudcontrol

import com.hcloudcontrol.operations.*
import org.junit.Assert.*
import org.junit.Test

class SettingsNavigationStateTest {
    @Test fun `restored inactive profile Back retains connections origin while endpoint switch rejects it`() {
        val profile = SettingsNavigationState().openConnections().inspectProfile("project-b")
        val scope = "project-a:https://cloud.example/v1"
        val restored = SettingsNavigationState.restore(scope, profile.snapshot(scope))
        assertEquals(SettingsPage.CONNECTIONS, restored.back().page)
        assertEquals(SettingsNavigationState(), restored.back().back())
        assertEquals(SettingsNavigationState(), SettingsNavigationState.restore("project-a:https://other.example/v1", profile.snapshot(scope)))
    }

    @Test fun `malformed profile records cannot restore a missing ID or recursive parent`() {
        val scope = "project-a:https://cloud.example/v1"
        listOf(listOf("settings-v1", scope, "PROFILE", "", "INDEX"),
            listOf("settings-v1", scope, "PROFILE", "project-b", "PROFILE"),
            listOf("settings-v1", scope, "OBSOLETE", "project-b", "INDEX"))
            .forEach { assertEquals(SettingsNavigationState(), SettingsNavigationState.restore(scope, it)) }
    }

    @Test fun `pending warning includes Storage Box management and test Cloud but not independent S3`() {
        fun record(id: String, endpoint: String, type: String, environment: OperationContext.Environment) = PersistedOperation(
            OperationIntent(id, ResourceIdentity(OperationContext("project-a", endpoint, environment), type, "41"), "delete", 1), OperationStage.OUTCOME_UNKNOWN, 1)
        val records = listOf(record("box", "https://box.example/v1", "storage_box", OperationContext.Environment.CLOUD),
            record("test", "https://mock.example/v1", "server", OperationContext.Environment.TEST),
            record("s3", "https://s3.example", "bucket", OperationContext.Environment.S3))
        assertEquals(2, pendingCloudOperationCount("project-a", records))
    }
    @Test fun `inspect inactive profile preserves origin and never selects an active connection`() {
        val list = SettingsNavigationState().openConnections()
        val profile = list.inspectProfile("project-b")
        assertEquals(SettingsPage.PROFILE, profile.page)
        assertEquals("project-b", profile.inspectedProfileId)
        assertEquals(list, profile.back())
        assertEquals(SettingsNavigationState(), profile.back().back())
    }

    @Test fun `active profile entry returns directly to settings index`() {
        assertEquals(SettingsNavigationState(), SettingsNavigationState().inspectProfile("project-a").back())
    }

    @Test fun `profile detail restores only inside original active context`() {
        val profile = SettingsNavigationState().openConnections().inspectProfile("project-b")
        val scope = "project-a:https://cloud.example/v1"
        assertEquals(profile, SettingsNavigationState.restore(scope, profile.snapshot(scope)))
        assertEquals(SettingsNavigationState(), SettingsNavigationState.restore("project-c:https://cloud.example/v1", profile.snapshot(scope)))
        assertEquals(SettingsNavigationState(), SettingsNavigationState.restore(scope, listOf("obsolete")))
    }

    @Test fun `local removal warning counts only pending Cloud or Box operations in pinned project`() {
        fun operation(project: String, stage: OperationStage, environment: OperationContext.Environment = OperationContext.Environment.CLOUD) =
            PersistedOperation(OperationIntent("intent-${project}-${stage.name}-${environment.name}",
                ResourceIdentity(OperationContext(project, "https://api.example/v1", environment), "server", "7"), "delete", 1), stage, 1)
        val records = listOf(operation("project-a", OperationStage.RUNNING), operation("project-a", OperationStage.OUTCOME_UNKNOWN),
            operation("project-a", OperationStage.SUCCEEDED), operation("project-b", OperationStage.RUNNING),
            operation("project-a", OperationStage.RUNNING, OperationContext.Environment.S3))
        assertEquals(2, pendingCloudOperationCount("project-a", records))
        assertEquals(1, pendingCloudOperationCount("project-b", records))
        assertEquals(0, pendingCloudOperationCount("removed-project", records))
    }
}
