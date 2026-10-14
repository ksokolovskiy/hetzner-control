package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.FirewallInventory
import com.hcloudcontrol.core.domain.model.FirewallRuleSpec
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface FirewallDetailState {
    data object Loading : FirewallDetailState
    data class Content(val firewall: FirewallInventory, val actions: List<CloudAction>) : FirewallDetailState
    data object Error : FirewallDetailState
}

@Composable
internal fun FirewallDetailScreen(initial: FirewallInventory, repository: HCloudRepository, onBack: () -> Unit) {
    var refreshKey by remember(initial.id) { mutableStateOf(0) }
    var state by remember(initial.id) { mutableStateOf<FirewallDetailState>(FirewallDetailState.Loading) }
    var deleteRequested by remember(initial.id) { mutableStateOf(false) }
    var addRuleRequested by remember(initial.id) { mutableStateOf(false) }
    LaunchedEffect(initial.id, refreshKey) {
        state = FirewallDetailState.Loading
        coroutineScope {
            val detail = async(Dispatchers.IO) { runCatching { repository.getFirewall(initial.id) } }
            val history = async(Dispatchers.IO) { runCatching { repository.getFirewallActions(initial.id) } }
            state = if (detail.await().isSuccess && history.await().isSuccess) FirewallDetailState.Content(detail.await().getOrThrow(), history.await().getOrThrow()) else FirewallDetailState.Error
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item { Text(stringResource(R.string.firewall_detail_title), style = androidx.compose.material3.MaterialTheme.typography.headlineMedium) }
        when (val current = state) {
            FirewallDetailState.Loading -> item { Text(stringResource(R.string.firewall_detail_loading)) }
            FirewallDetailState.Error -> item { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(stringResource(R.string.firewall_detail_error)); OutlinedButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.common_retry)) } } }
            is FirewallDetailState.Content -> {
                item { FirewallDetailsCard(current.firewall, LocalHighImpactOperations.current.isAllowed, onAddInboundRule = { addRuleRequested = true }, onDelete = { deleteRequested = true }) }
                item { FirewallActionsCard(current.actions) }
            }
        }
    }
    if (deleteRequested && state is FirewallDetailState.Content) {
        val firewall = (state as FirewallDetailState.Content).firewall
        DeleteFirewallDialog(
            firewall = firewall,
            repository = repository,
            onDismiss = { deleteRequested = false },
            onDeleted = { deleteRequested = false; onBack() },
        )
    }
    if (addRuleRequested && state is FirewallDetailState.Content) {
        val firewall = (state as FirewallDetailState.Content).firewall
        AddFirewallInboundRuleDialog(
            firewall = firewall,
            repository = repository,
            onDismiss = { addRuleRequested = false },
            onUpdated = { addRuleRequested = false; refreshKey++ },
        )
    }
}

@Composable private fun FirewallDetailsCard(firewall: FirewallInventory, operationsAllowed: Boolean, onAddInboundRule: () -> Unit, onDelete: () -> Unit) = Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Text(firewall.name, style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.firewall_detail_id, firewall.id))
        firewall.labels.takeIf { it.isNotEmpty() }?.let { Text(stringResource(R.string.common_labels_format, it.toSortedMap().entries.joinToString { entry -> "${entry.key}=${entry.value}" })) }
        Text(stringResource(R.string.firewall_detail_rules), style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
        if (firewall.rules.isEmpty()) Text(stringResource(R.string.firewall_detail_rules_empty))
        firewall.rules.forEach { rule ->
            val sources = rule.sourceIps.ifEmpty { listOf(stringResource(R.string.network_any_address)) }.joinToString()
            val destinations = rule.destinationIps.ifEmpty { listOf(stringResource(R.string.network_any_address)) }.joinToString()
            Text("${rule.direction.uppercase()} ${rule.protocol}${rule.port?.let { ":$it" }.orEmpty()} · $sources → $destinations")
            rule.description?.takeIf { it.isNotBlank() }?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.labelSmall) }
        }
        if (operationsAllowed) {
            Text(stringResource(R.string.firewall_detail_rules_replace_notice), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
            OutlinedButton(onClick = onAddInboundRule, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.firewall_add_inbound_rule)) }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = androidx.compose.material3.MaterialTheme.colorScheme.error),
            ) { Text(stringResource(R.string.firewall_delete_action)) }
        } else {
            Text(stringResource(R.string.firewall_detail_read_only), style = androidx.compose.material3.MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AddFirewallInboundRuleDialog(firewall: FirewallInventory, repository: HCloudRepository, onDismiss: () -> Unit, onUpdated: () -> Unit) {
    var port by remember(firewall.id) { mutableStateOf("443") }
    var sourceIps by remember(firewall.id) { mutableStateOf("") }
    var description by remember(firewall.id) { mutableStateOf("") }
    var reviewed by remember(firewall.id) { mutableStateOf(false) }
    var submitting by remember(firewall.id) { mutableStateOf(false) }
    var progress by remember(firewall.id) { mutableStateOf<String?>(null) }
    var error by remember(firewall.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val operationsAllowed = LocalHighImpactOperations.current.isAllowed
    val failure = stringResource(R.string.firewall_rule_update_failed)
    val parsedSources = sourceIps.split(',', '\n').map { it.trim() }.filter { it.isNotEmpty() }
    val canSubmit = operationsAllowed && port.isNotBlank() && parsedSources.isNotEmpty() && reviewed && !submitting
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.firewall_add_inbound_rule_title, firewall.name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.firewall_add_inbound_rule_intro))
            OutlinedTextField(port, { port = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.firewall_rule_port)) }, enabled = !submitting, singleLine = true)
            OutlinedTextField(sourceIps, { sourceIps = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.firewall_rule_sources)) }, supportingText = { Text(stringResource(R.string.firewall_rule_sources_hint)) }, enabled = !submitting, minLines = 2)
            OutlinedTextField(description, { description = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.firewall_rule_description)) }, enabled = !submitting, singleLine = true)
            androidx.compose.foundation.layout.Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = reviewed, onCheckedChange = { reviewed = it }, enabled = !submitting)
                Text(stringResource(R.string.firewall_rule_replace_ack))
            }
            progress?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant) }
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(onClick = {
            if (!operationsAllowed) return@Button
            submitting = true; error = null
            scope.launch {
                try {
                    val retained = firewall.rules.map { rule -> FirewallRuleSpec(rule.direction, rule.protocol, rule.port, rule.sourceIps, rule.destinationIps, rule.description) }
                    val action = withContext(Dispatchers.IO) { repository.setFirewallRules(firewall.id, retained + FirewallRuleSpec("in", "tcp", port.trim(), sourceIps = parsedSources, description = description.trim().takeIf { it.isNotEmpty() })) }
                    awaitCloudAction(repository, action) { status -> progress = status }
                    onUpdated()
                } catch (throwable: Throwable) {
                    error = safeApiFailureMessage(throwable, failure)
                    submitting = false
                }
            }
        }, enabled = canSubmit) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.firewall_rule_add_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun DeleteFirewallDialog(firewall: FirewallInventory, repository: HCloudRepository, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    var typedName by remember(firewall.id) { mutableStateOf("") }
    var acknowledgement by remember(firewall.id) { mutableStateOf("") }
    var submitting by remember(firewall.id) { mutableStateOf(false) }
    var error by remember(firewall.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val failure = stringResource(R.string.firewall_delete_failed)
    val operationsAllowed = LocalHighImpactOperations.current.isAllowed
    val canDelete = operationsAllowed && typedName == firewall.name && acknowledgement == "DELETE"
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.firewall_delete_title, firewall.name)) },
        text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(stringResource(R.string.firewall_delete_warning), color = androidx.compose.material3.MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.firewall_delete_impact))
            Text(stringResource(R.string.firewall_delete_type_name, firewall.name))
            OutlinedTextField(typedName, { typedName = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.firewall_delete_name_label)) }, enabled = !submitting, singleLine = true)
            Text(stringResource(R.string.firewall_delete_type_word))
            OutlinedTextField(acknowledgement, { acknowledgement = it; error = null }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.firewall_delete_word_label)) }, enabled = !submitting, singleLine = true)
            error?.let { Text(it, color = androidx.compose.material3.MaterialTheme.colorScheme.error) }
        } },
        confirmButton = { Button(
            onClick = {
                if (!operationsAllowed) return@Button
                submitting = true; error = null
                scope.launch {
                    try {
                        withContext(Dispatchers.IO) { repository.deleteFirewall(firewall.id) }
                        onDeleted()
                    } catch (throwable: Throwable) {
                        error = safeApiFailureMessage(throwable, failure)
                        submitting = false
                    }
                }
            },
            enabled = canDelete && !submitting,
            colors = ButtonDefaults.buttonColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.error, contentColor = androidx.compose.material3.MaterialTheme.colorScheme.onError),
        ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.firewall_delete_confirm)) } },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable private fun FirewallActionsCard(actions: List<CloudAction>) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.firewall_actions_title), style = androidx.compose.material3.MaterialTheme.typography.titleLarge)
    if (actions.isEmpty()) Text(stringResource(R.string.firewall_actions_empty)) else actions.sortedByDescending { it.id }.forEach { action ->
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceVariant)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            Text(action.command ?: stringResource(R.string.firewall_action_unknown))
            Text(stringResource(R.string.firewall_action_status, action.status))
            action.progress?.let { Text(stringResource(R.string.firewall_action_progress, it)) }
            action.started?.let { Text(stringResource(R.string.firewall_action_started, it)) }
            action.finished?.let { Text(stringResource(R.string.firewall_action_finished, it)) }
            action.errorCode?.let { Text(stringResource(R.string.firewall_action_error_code, it)) }
        } }
    }
}
