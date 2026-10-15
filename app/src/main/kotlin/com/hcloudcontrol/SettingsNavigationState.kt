package com.hcloudcontrol

import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import com.hcloudcontrol.operations.OperationContext
import com.hcloudcontrol.operations.PersistedOperation

internal enum class SettingsPage { INDEX, PROFILE, CONNECTIONS }

/** Inspecting a saved profile is independent from selecting it for Cloud requests. */
internal data class SettingsNavigationState(
    val page: SettingsPage = SettingsPage.INDEX,
    val inspectedProfileId: String? = null,
    val profileParent: SettingsPage = SettingsPage.INDEX,
) {
    fun openConnections() = SettingsNavigationState(SettingsPage.CONNECTIONS)
    fun inspectProfile(id: String) = SettingsNavigationState(SettingsPage.PROFILE, id,
        if (page == SettingsPage.CONNECTIONS) SettingsPage.CONNECTIONS else SettingsPage.INDEX)
    fun back() = if (page == SettingsPage.PROFILE) SettingsNavigationState(profileParent)
        else SettingsNavigationState()
    fun snapshot(scope: String): ArrayList<String> = arrayListOf("settings-v1", scope, page.name,
        inspectedProfileId.orEmpty(), profileParent.name)

    companion object {
        fun restore(scope: String, snapshot: List<String>): SettingsNavigationState = runCatching {
            require(snapshot.size == 5 && snapshot[0] == "settings-v1" && snapshot[1] == scope)
            val page = SettingsPage.valueOf(snapshot[2])
            val id = snapshot[3].takeIf { it.isNotBlank() }
            require(page != SettingsPage.PROFILE || id != null)
            val parent = SettingsPage.valueOf(snapshot[4])
            require(parent != SettingsPage.PROFILE)
            SettingsNavigationState(page, id, parent)
        }.getOrElse { SettingsNavigationState() }
    }
}

@Composable
internal fun rememberSettingsNavigation(scope: String): MutableState<SettingsNavigationState> {
    val saver = remember(scope) { Saver<SettingsNavigationState, ArrayList<String>>(
        save = { it.snapshot(scope) }, restore = { SettingsNavigationState.restore(scope, it) }) }
    return rememberSaveable(scope, stateSaver = saver) { mutableStateOf(SettingsNavigationState()) }
}

internal fun pendingCloudOperationCount(connectionId: String, operations: List<PersistedOperation>): Int =
    operations.count { it.isPending && it.intent.resource.context.connectionId == connectionId &&
        it.intent.resource.context.environment != OperationContext.Environment.S3 }
