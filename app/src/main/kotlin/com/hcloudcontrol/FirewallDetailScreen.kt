package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader

@Composable
internal fun FirewallDetailScreen(initial: FirewallInventory, repository: HCloudRepository, onBack: () -> Unit) =
    FirewallDetailById(initial.id, repository, onBack)

/** All entrances, including a newly-created immutable ID, use this single fresh detail owner. */
@Composable
internal fun FirewallDetailById(firewallId: Long, repository: HCloudRepository, onBack: () -> Unit) {
    val scope = "firewall:${LocalNavigationContext.current}:$firewallId"
    val metadata = rememberSectionLoader("$scope:metadata") { repository.getFirewall(firewallId) }
    val history = rememberSectionLoader("$scope:history") { repository.getFirewallActions(firewallId) }
    val draft = rememberFirewallFormDraft(scope)
    var section by rememberSaveable(scope) { mutableStateOf("rules") }
    var mode by remember(scope) { mutableStateOf<FirewallMutationMode?>(null) }
    var menu by remember(scope) { mutableStateOf(false) }
    var selectedRule by remember(scope) { mutableStateOf<FirewallRuleInventory?>(null) }
    val firewall = metadata.state.data
    RegisterResourceChildNavigation(RootDestination.NETWORK, ResourceType.FIREWALL,
        firewallId.toString(), firewall?.name ?: stringResource(R.string.firewall_detail_title), onBack)
    BackHandler(enabled = LocalNavigationChrome.current == null) { onBack() }
    val allowed = LocalHighImpactOperations.current.isAllowed && metadata.state.hasSuccessfulResponse &&
        !metadata.state.refreshing && !metadata.state.isStale && metadata.state.error == null
    HCloudPullToRefresh(metadata.state.refreshing || history.state.refreshing,
        { metadata.refresh(); history.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scope, contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                if (LocalNavigationChrome.current == null) OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) }
                HCloudTabs(listOf(ChoiceItem("rules", stringResource(R.string.firewall_detail_rules)),
                    ChoiceItem("targets", stringResource(R.string.firewall_redesign_targets)),
                    ChoiceItem("history", stringResource(R.string.firewall_actions_title))), section, { section = it })
            }
            if (section == "history") item { HCloudReadSection(history) { FirewallActionHistory(it) } }
            else item { HCloudReadSection(metadata) { current ->
                Text(current.name, style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.firewall_detail_id, current.id))
                if (current.labels.isNotEmpty()) Text(stringResource(R.string.common_labels_format,
                    current.labels.toSortedMap().entries.joinToString { "${it.key}=${it.value}" }))
                if (section == "rules") {
                    if (current.rules.isEmpty()) Text(stringResource(R.string.firewall_detail_rules_empty))
                    current.rules.forEachIndexed { index, rule ->
                        HCloudResourceCard(ResourceCardModel("$scope:rule:$index", "${rule.protocol.uppercase()} · ${rule.port.orEmpty()}",
                            rule.direction, firewallRuleText(rule)), { selectedRule = rule })
                    }
                    if (allowed) OutlinedButton(onClick = { mode = FirewallMutationMode.TCP }, Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.firewall_add_inbound_rule))
                    }
                } else {
                    Text(firewallAttachmentText(current.appliedTo).ifBlank { stringResource(R.string.common_none) })
                    if (allowed) {
                        OutlinedButton(onClick = { mode = FirewallMutationMode.APPLY }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_apply_firewall)) }
                        OutlinedButton(onClick = { mode = FirewallMutationMode.REMOVE }, Modifier.fillMaxWidth()) { Text(stringResource(R.string.network_remove_firewall)) }
                    }
                }
                if (allowed) {
                    OutlinedButton(onClick = { menu = true }) { Text(stringResource(R.string.ds_actions)) }
                    HCloudMenu(menu, listOf(MenuAction("delete", stringResource(R.string.firewall_delete_action))),
                        { menu = false }, { if (it == "delete") mode = FirewallMutationMode.DELETE })
                }
            } }
        }
    }
    selectedRule?.let { rule ->
        HCloudDialog(stringResource(R.string.firewall_detail_rules), stringResource(R.string.ds_close),
            { selectedRule = null }, { selectedRule = null }, dismissLabel = stringResource(R.string.ds_close)) {
            Text(firewallRuleText(rule))
            Text(stringResource(R.string.firewall_redesign_rule_readonly))
        }
    }
    mode?.let { requested ->
        if (firewall != null) FirewallMutationDialog(repository, firewall, requested,
            { mode = null }, { _ ->
                mode = null
                draft.value = FirewallFormDraft()
                if (requested == FirewallMutationMode.DELETE) onBack() else { metadata.refresh(); history.refresh() }
            }, draft)
    }
}

@Composable
private fun FirewallActionHistory(actions: List<CloudAction>) {
    if (actions.isEmpty()) Text(stringResource(R.string.firewall_actions_empty))
    actions.sortedByDescending { it.id }.forEach { action ->
        HCloudSectionState(SectionPresentation(SectionKind.CONTENT)) {
            Text(action.command ?: stringResource(R.string.firewall_action_unknown))
            Text(stringResource(R.string.firewall_action_status, action.status))
            action.progress?.let { Text(stringResource(R.string.firewall_action_progress, it)) }
            action.started?.let { Text(stringResource(R.string.firewall_action_started, it)) }
            action.finished?.let { Text(stringResource(R.string.firewall_action_finished, it)) }
            action.errorCode?.let { Text(stringResource(R.string.firewall_action_error_code, it)) }
        }
    }
}
