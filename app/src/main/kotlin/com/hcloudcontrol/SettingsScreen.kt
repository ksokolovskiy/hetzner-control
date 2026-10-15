package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.ConnectionKind
import com.hcloudcontrol.core.security.CloudProjectConnection
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*

private enum class SettingsChoice { APPEARANCE, LANGUAGE }

/** Local preferences and connection metadata only; no provider mutations or credential display. */
@Composable
internal fun SettingsScreen(
    activeProject: CloudProjectConnection,
    connections: List<CloudProjectConnection>,
    onAddProject: () -> Unit,
    onDisconnect: (String) -> Unit,
    onSelectProject: (String) -> Unit,
    appearanceMode: AppearanceMode,
    onAppearanceModeChange: (AppearanceMode) -> Unit,
    onHighImpactOperationsChange: (Boolean) -> Unit,
) {
    var settingsNavigation by rememberSettingsNavigation("${activeProject.id}:${activeProject.config.baseUrl}")
    val page = settingsNavigation.page
    val inspectedProfile = connections.firstOrNull { it.id == settingsNavigation.inspectedProfileId }
    var choice by remember(activeProject.id) { mutableStateOf<SettingsChoice?>(null) }
    var removal by remember(activeProject.id) { mutableStateOf<CloudProjectConnection?>(null) }
    var changePermission by remember(activeProject.id) { mutableStateOf<Boolean?>(null) }
    var acknowledged by remember { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf("") }
    var language by remember { mutableStateOf(currentLanguageMode()) }
    var persistenceFailed by remember { mutableStateOf(false) }
    val capability = LocalHighImpactOperations.current
    val uri = LocalUriHandler.current
    val hosted = LocalNavigationChrome.current != null
    val coordinator = com.hcloudcontrol.operations.LocalOperationCoordinator.current
    if (page == SettingsPage.PROFILE && inspectedProfile != null) RegisterChildNavigation(
        CanonicalDestination(RootDestination.MORE, DestinationKind.DETAIL,
            ResourceContext(inspectedProfile.id, inspectedProfile.config.baseUrl, ContextKind.CLOUD), ResourceType.PROJECT, inspectedProfile.id),
        stringResource(R.string.settings_connection_title, inspectedProfile.displayName)) { settingsNavigation = settingsNavigation.back() }
    else if (page != SettingsPage.INDEX) RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.SETTINGS, page.name,
        stringResource(if (page == SettingsPage.PROFILE) R.string.settings_profile_title else R.string.settings_all_connections)) { settingsNavigation = settingsNavigation.back() }
    BackHandler(enabled = !hosted && page != SettingsPage.INDEX && choice == null && removal == null && changePermission == null) { settingsNavigation = settingsNavigation.back() }

    LazyColumn(state = rememberRestorableLazyListState("settings-${page.name}"), modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(HCloudDesignTokens.contentInset), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (persistenceFailed) item { Text(stringResource(R.string.settings_local_save_failed), color = MaterialTheme.colorScheme.error) }
        if (!hosted && page != SettingsPage.INDEX) item {
            TextButton(onClick = { settingsNavigation = settingsNavigation.back() }) { Text(stringResource(R.string.common_back)) }
        }
        if (!hosted) item { Text(stringResource(when (page) {
            SettingsPage.INDEX -> R.string.settings_title
            SettingsPage.PROFILE -> R.string.settings_profile_title
            SettingsPage.CONNECTIONS -> R.string.settings_all_connections
        }), style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() }) }
        when (page) {
            SettingsPage.INDEX -> {
                item { SettingsGroupLabel(stringResource(R.string.settings_group_application)) }
                item { SettingsEntry("appearance", stringResource(R.string.settings_appearance), stringResource(appearanceMode.labelRes)) { choice = SettingsChoice.APPEARANCE } }
                item { SettingsEntry("language", stringResource(R.string.settings_language), stringResource(language.labelRes)) { choice = SettingsChoice.LANGUAGE } }
                item { SettingsEntry("privacy", stringResource(R.string.settings_privacy_policy), stringResource(R.string.settings_external_link)) { uri.openUri(PRIVACY_POLICY_URL) } }
                item { SettingsGroupLabel(stringResource(R.string.settings_group_safety)) }
                item { SettingsEntry("changes", stringResource(R.string.settings_high_impact_title), stringResource(if (capability.isAllowed) R.string.settings_high_impact_enabled else R.string.settings_high_impact_disabled)) {
                    acknowledged = false
                    changePermission = !capability.isAllowed
                } }
                item { SettingsGroupLabel(stringResource(R.string.settings_group_projects)) }
                item { SettingsEntry(activeProject.id, activeProject.displayName, stringResource(if (activeProject.config.kind == ConnectionKind.REAL) R.string.settings_profile_project_connection else R.string.settings_profile_test_connection)) { settingsNavigation = settingsNavigation.inspectProfile(activeProject.id) } }
                item { SettingsEntry("connections", stringResource(R.string.settings_all_connections), pluralStringResource(R.plurals.settings_projects_configured, connections.size, connections.size)) { settingsNavigation = settingsNavigation.openConnections() } }
            }
            SettingsPage.PROFILE -> {
                val profile = inspectedProfile
                if (profile == null) item { HCloudSectionState(SectionPresentation(SectionKind.MISSING)) }
                else {
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(profile.displayName, style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(if (profile.config.kind == ConnectionKind.REAL) R.string.settings_profile_project_connection else R.string.settings_profile_test_connection))
                        Text(stringResource(R.string.settings_profile_shared_scope), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                item { Text(stringResource(if (profile.id == activeProject.id) R.string.settings_profile_selected_note else R.string.settings_profile_not_selected_note)) }
                if (profile.config.kind == ConnectionKind.TEST_MOCK) item { Text(profile.config.baseUrl, style = MaterialTheme.typography.bodySmall) }
                item { Text(stringResource(R.string.settings_profile_local_note), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val pendingCount = pendingCloudOperationCount(profile.id, coordinator?.all().orEmpty())
                if (pendingCount > 0) item { Text(stringResource(R.string.settings_pending_operation_warning, pendingCount), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                if (profile.id != activeProject.id) item { Button(onClick = {
                    try { onSelectProject(profile.id); persistenceFailed = false }
                    catch (_: Exception) { persistenceFailed = true }
                }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_switch_to_project)) } }
                item { OutlinedButton(onClick = { settingsNavigation = settingsNavigation.openConnections() }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_all_connections)) } }
                item { OutlinedButton(onClick = { removal = profile }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_remove_project, profile.displayName)) } }
                }
            }
            SettingsPage.CONNECTIONS -> {
                item { HCloudFormField(search, { search = it }, stringResource(R.string.settings_search_connections)) }
                val matching = connections.filter { it.displayName.contains(search, ignoreCase = true) }
                if (matching.isEmpty()) item { HCloudSectionState(SectionPresentation(if (connections.isEmpty()) SectionKind.NO_CONNECTION else SectionKind.NO_RESULTS)) }
                items(matching, key = { it.id }) { connection ->
                    SettingsEntry(connection.id, connection.displayName,
                        listOfNotNull(
                            stringResource(if (connection.config.kind == ConnectionKind.REAL) R.string.settings_profile_project_connection else R.string.settings_profile_test_connection),
                            stringResource(R.string.settings_selected_connection).takeIf { connection.id == activeProject.id },
                        ).joinToString(" · ")) {
                        settingsNavigation = settingsNavigation.inspectProfile(connection.id)
                    }
                }
                item { Button(onClick = onAddProject, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.settings_add_project)) } }
            }
        }
    }
    choice?.let { selectedChoice ->
        val appearance = selectedChoice == SettingsChoice.APPEARANCE
        HCloudChoiceSheet(
            title = stringResource(if (appearance) R.string.settings_appearance else R.string.settings_language),
            items = if (appearance) AppearanceMode.entries.map { ChoiceItem(it.name, stringResource(it.labelRes)) }
                else LanguageMode.entries.map { ChoiceItem(it.name, stringResource(it.labelRes)) },
            selectedId = if (appearance) appearanceMode.name else language.name,
            onSelected = { selected ->
                if (appearance) onAppearanceModeChange(AppearanceMode.valueOf(selected))
                else { language = LanguageMode.valueOf(selected); applyLanguage(language) }
            },
            onDismiss = { choice = null },
        )
    }
    removal?.let { pinned ->
        HCloudDialog(title = stringResource(R.string.settings_remove_project_title, pinned.displayName),
            confirmLabel = stringResource(R.string.settings_remove_project_confirm),
            onConfirm = {
                try { onDisconnect(pinned.id); removal = null; persistenceFailed = false; settingsNavigation = settingsNavigation.openConnections() }
                catch (_: Exception) { persistenceFailed = true; removal = null }
            }, onDismiss = { removal = null }) {
            Text(stringResource(R.string.settings_remove_project_message))
            Text(stringResource(R.string.settings_profile_local_note))
            val pendingCount = pendingCloudOperationCount(pinned.id, coordinator?.all().orEmpty())
            if (pendingCount > 0) Text(stringResource(R.string.settings_pending_operation_warning, pendingCount))
        }
    }
    changePermission?.let { allow ->
        HCloudDialog(
            title = stringResource(if (allow) R.string.settings_high_impact_enable_title else R.string.settings_high_impact_disable_title),
            confirmLabel = stringResource(if (allow) R.string.settings_high_impact_enable_confirm else R.string.settings_high_impact_disable_confirm),
            confirmEnabled = !allow || acknowledged,
            onConfirm = { if (!allow || acknowledged) {
                try { onHighImpactOperationsChange(allow); persistenceFailed = false }
                catch (_: Exception) { persistenceFailed = true }
                changePermission = null; acknowledged = false
            } },
            onDismiss = { changePermission = null; acknowledged = false },
        ) {
            Text(stringResource(if (allow) R.string.settings_high_impact_enable_message else R.string.settings_high_impact_disable_message))
            Text(stringResource(R.string.settings_global_gate_note))
            if (allow) Row(Modifier.fillMaxWidth().heightIn(min = HCloudDesignTokens.minimumTarget)
                .toggleable(value = acknowledged, role = Role.Checkbox, onValueChange = { acknowledged = it }), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = acknowledged, onCheckedChange = null)
                Text(stringResource(R.string.settings_risk_acknowledgement), modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun SettingsGroupLabel(label: String) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp).semantics { heading() })
}

@Composable
private fun SettingsEntry(id: String, title: String, value: String, onClick: () -> Unit) {
    HCloudResourceCard(ResourceCardModel("settings:$id", title, value, ""), onClick)
}

private const val PRIVACY_POLICY_URL = "https://ksokolovskiy.github.io/hetzner-control/privacy-policy/"
