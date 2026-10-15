package com.hcloudcontrol

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class FirewallMutationMode { CREATE, TCP, APPLY, REMOVE, DELETE }

/** A single supported Firewall operation consumer; the shared coordinator owns its lifecycle. */
@Composable
internal fun FirewallMutationDialog(repository: HCloudRepository, firewall: FirewallInventory?, mode: FirewallMutationMode,
    onDismiss: () -> Unit, onCompleted: (Long?) -> Unit, draftState: MutableState<FirewallFormDraft>? = null) {
    val context = LocalOperationContext.current
    val draftScope = "firewall:$context:${firewall?.id}:$mode"
    val localDraft = rememberFirewallFormDraft(draftScope)
    val draft = draftState ?: localDraft
    val name = draft.value.name
    val port = draft.value.port
    val sources = draft.value.sources
    val description = draft.value.description
    var serverId by com.hcloudcontrol.ui.navigation.rememberContextualResourceId(draftScope)
    var picker by remember(draftScope) { mutableStateOf(false) }
    val targets = if (mode in setOf(FirewallMutationMode.APPLY, FirewallMutationMode.REMOVE))
        rememberSectionLoader("$draftScope:targets") { repository.getServers() } else null
    val server = targets?.state?.data?.find { it.id == serverId }
    val tcp = FirewallTcpDraft(port, sources, description)
    val change: FirewallChange? = when (mode) {
        FirewallMutationMode.CREATE -> name.trim().takeIf { it.isNotBlank() && it.length <= 128 }?.let { FirewallChange.Create(it) }
        FirewallMutationMode.TCP -> tcp.rule()?.let { FirewallChange.AddTcp(checkNotNull(firewall), it) }
        FirewallMutationMode.APPLY, FirewallMutationMode.REMOVE -> server?.let { FirewallChange.Target(checkNotNull(firewall), it.id, it.name, mode == FirewallMutationMode.REMOVE) }
        FirewallMutationMode.DELETE -> FirewallChange.Delete(checkNotNull(firewall))
    }
    val session = rememberOperationSession("firewall", firewall?.id?.toString(), "${mode.name.lowercase()}${serverId?.let { "_$it" }.orEmpty()}",
        (change?.hashCode() ?: 0).toLong() and 0xffffffffL)
    val adapter = remember(repository) { FirewallMutationAdapter(repository) }
    val currentContext by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val scope = rememberCoroutineScope()
    var busy by remember(session) { mutableStateOf(false) }
    var failed by remember(session) { mutableStateOf(false) }
    var review by remember(session) { mutableStateOf<OperationReviewContent?>(null) }
    var reviewedChange by remember(session) { mutableStateOf<FirewallChange?>(null) }
    var progress by remember(session) { mutableStateOf(0) }
    val record = remember(session, busy, progress) { session.record }
    val exitGuard = com.hcloudcontrol.ui.navigation.rememberDirtyFormExitGuard(
        isDirty = draft.value.isDirty, onRetain = {}, onDiscard = { draft.value = FirewallFormDraft() },
        onLeave = onDismiss, submitted = record != null, contextKey = draftScope)
    DirtyFormExitPrompt(exitGuard)
    val title = stringResource(when (mode) {
        FirewallMutationMode.CREATE -> R.string.firewall_create_title
        FirewallMutationMode.TCP -> R.string.firewall_add_inbound_rule
        FirewallMutationMode.APPLY -> R.string.network_firewall_apply_title
        FirewallMutationMode.REMOVE -> R.string.network_firewall_remove_title
        FirewallMutationMode.DELETE -> R.string.firewall_delete_action
    })
    val warning = stringResource(when (mode) {
        FirewallMutationMode.CREATE -> R.string.firewall_create_no_rules_ack
        FirewallMutationMode.TCP -> R.string.firewall_rule_replace_ack
        FirewallMutationMode.APPLY -> R.string.network_firewall_apply_note
        FirewallMutationMode.REMOVE -> R.string.network_firewall_remove_note
        FirewallMutationMode.DELETE -> R.string.firewall_delete_impact
    })
    val resourceLabel = firewall?.let { "${it.name} · #${it.id}" } ?: name.trim()
    val rulesLabel = stringResource(R.string.firewall_detail_rules)
    val targetsLabel = stringResource(R.string.firewall_redesign_targets)
    val blocked = stringResource(R.string.firewall_redesign_attached_delete)
    if (record != null) {
        OperationResultDialog(title, record, record.intent.resource.context.connectionId,
            resourceLabel.ifBlank { record.createdResourceId.orEmpty() },
            onClose = { if (record.stage == OperationStage.SUCCEEDED) onCompleted(record.createdResourceId?.toLongOrNull()) else onDismiss() },
            busy = busy, session = session, onCheckStatus = {
                if (!busy && currentContext == session.context) { busy = true; scope.launch {
                    try {
                        if (record.originalActionIds.isNotEmpty()) {
                            session.observeOriginalActions(record.originalActionIds.map { repository.getAction(it) })
                        } else when (val evidence = withContext(Dispatchers.IO) { OriginalCloudResourceStatusReader(repository).read(record) }) {
                            is OperationCoordinator.Reconciliation.CreatedResourceEffect -> session.reconcileCreatedResource(checkNotNull(evidence.resource.resourceId), evidence.effectConfirmed)
                            is OperationCoordinator.Reconciliation.ExistingResourceEffect -> session.reconcileExistingEffect(evidence.effectConfirmed)
                            else -> session.unknownIfPending()
                        }
                    } catch (_: Throwable) { failed = true; session.unknownIfPending() }
                    busy = false; progress++
                } }
            }) { if (failed) Text(stringResource(R.string.firewall_redesign_failed), color = MaterialTheme.colorScheme.error) }
        return
    }
    review?.let { frozen ->
        OperationReviewDialog(frozen, { proof ->
            val pinned = reviewedChange ?: return@OperationReviewDialog
            if (!busy) { busy = true; scope.launch {
                try {
                    withContext(Dispatchers.IO) { adapter.validate(pinned) }
                    session.beginReviewed(proof, allowed, currentContext)
                    val result = withContext(Dispatchers.IO) {
                        check(allowed && currentContext == session.context)
                        adapter.execute(pinned)
                    }
                    if (result.actions.isEmpty() && result.createdId == null) session.succeed()
                    else {
                        session.acceptedActions(result.actions, result.createdId?.toString(), false)
                        if (result.actions.isEmpty()) session.succeed()
                        else {
                            val observed = result.actions.associateBy { it.id }.toMutableMap()
                            session.observeOriginalActions(observed.values.toList())
                            result.actions.forEach { original ->
                                awaitCloudAction(original, { id -> repository.getAction(id).also {
                                    observed[id] = it; session.observeOriginalActions(observed.values.toList()); progress++
                                } }, {})
                            }
                        }
                    }
                } catch (error: Throwable) { session.rejectOrUnknown(error); failed = true; review = null }
                busy = false; progress++
            } }
        }, { review = null }, submitEnabled = allowed && !busy)
        return
    }
    HCloudDialog(title, stringResource(R.string.ds_operation_review), {
        val pinned = change
        if (pinned != null && !busy && allowed) { busy = true; failed = false; scope.launch {
            try {
                // Attached delete is displayed as a blocked review; no mutation path bypasses validation.
                if (mode != FirewallMutationMode.DELETE || firewall?.appliedTo?.isEmpty() == true) {
                    withContext(Dispatchers.IO) { adapter.validate(pinned) }
                } else check(withContext(Dispatchers.IO) { repository.getFirewall(checkNotNull(firewall).id) } == firewall)
                val identity = checkNotNull(session.intent)
                reviewedChange = pinned
                val effects = when (pinned) {
                    is FirewallChange.Create -> listOf(ReviewChange(rulesLabel, null, "[]"))
                    is FirewallChange.AddTcp -> listOf(ReviewChange(rulesLabel, null, firewallRuleText(pinned.rule)),
                        ReviewChange(rulesLabel, null, pinned.before.rules.joinToString("\n") { firewallRuleText(it) }))
                    is FirewallChange.Target -> listOf(ReviewChange(targetsLabel, null, "${pinned.serverName} · #${pinned.serverId}"),
                        ReviewChange(rulesLabel, null, pinned.before.rules.joinToString("\n") { firewallRuleText(it) }))
                    is FirewallChange.Delete -> listOf(ReviewChange(targetsLabel, null, firewallAttachmentText(pinned.before.appliedTo)))
                }
                review = OperationReviewContent(identity, System.currentTimeMillis(), title, identity.resource.context.connectionId,
                    resourceLabel, effects, typedIdentity = if (mode == FirewallMutationMode.DELETE) firewall?.name else null,
                    acknowledgements = listOf(RiskAcknowledgement("firewall_effect", warning)),
                    blockedReason = blocked.takeIf { mode == FirewallMutationMode.DELETE && firewall?.appliedTo?.isNotEmpty() == true },
                    typedConfirmation = "DELETE".takeIf { mode == FirewallMutationMode.DELETE })
            } catch (_: Throwable) { failed = true }
            busy = false
        } }
    }, { if (!busy) exitGuard.requestLeave() }, confirmEnabled = change != null && allowed && !busy && session.configured) {
        when (mode) {
            FirewallMutationMode.CREATE -> {
                HCloudFormField(name, { draft.value = draft.value.copy(name = it) }, stringResource(R.string.firewall_create_name), enabled = !busy)
                Text(stringResource(R.string.firewall_create_no_rules_warning), color = MaterialTheme.colorScheme.error)
            }
            FirewallMutationMode.TCP -> {
                HCloudFormField(port, { draft.value = draft.value.copy(port = it) }, stringResource(R.string.firewall_rule_port), enabled = !busy)
                HCloudFormField(sources, { draft.value = draft.value.copy(sources = it) }, stringResource(R.string.firewall_rule_sources), enabled = !busy)
                Text(stringResource(R.string.firewall_rule_sources_hint))
                HCloudFormField(description, { draft.value = draft.value.copy(description = it) }, stringResource(R.string.firewall_rule_description), enabled = !busy)
                if (tcp.broad) Text(stringResource(R.string.firewall_redesign_broad), color = MaterialTheme.colorScheme.error)
                if (change == null && (port.isNotBlank() || sources.isNotBlank())) Text(stringResource(R.string.firewall_redesign_validation), color = MaterialTheme.colorScheme.error)
            }
            FirewallMutationMode.APPLY, FirewallMutationMode.REMOVE -> {
                OutlinedButton(onClick = { picker = true }, enabled = !busy) { Text(server?.let { "${it.name} · #${it.id}" } ?: targetsLabel) }
            }
            FirewallMutationMode.DELETE -> Text(firewallAttachmentText(checkNotNull(firewall).appliedTo).ifBlank { stringResource(R.string.common_none) })
        }
        Text(warning)
        if (busy) CircularProgressIndicator()
        if (failed) Text(stringResource(R.string.firewall_redesign_failed), color = MaterialTheme.colorScheme.error)
    }
    if (picker && targets != null) {
        val direct = firewall?.appliedTo.orEmpty().filter { it.type == "server" }.mapNotNull { it.serverId }.toSet()
        val selector = firewall?.appliedTo.orEmpty().filter { it.type == "label_selector" }.flatMap { it.appliedToResources }.mapNotNull { it.serverId }.toSet()
        val eligible = targets.state.data.orEmpty().filter {
            val publicInterface = it.ipv4Address != null || it.networkDevices.ipv4Address != null || it.networkDevices.ipv6Network != null
            publicInterface && if (mode == FirewallMutationMode.REMOVE) it.id in direct && it.id !in selector else it.id !in direct && it.id !in selector }
        HCloudResourcePicker(targetsLabel, ResourcePickerState(draftScope, eligible.map { ResourcePickerItem(it.id.toString(), it.name, "#${it.id} · ${it.location}") },
            targets.state.refreshing, stringResource(R.string.ds_read_error).takeIf { targets.state.error != null }), serverId?.toString(),
            { serverId = it.toLongOrNull() }, { picker = false }, targets::refresh)
    }
}

// Presentation helpers contain identifiers and public rules only; no raw provider error text.
internal fun firewallRuleText(rule: FirewallRuleSpec): String = listOfNotNull("${rule.direction} ${rule.protocol}", rule.port,
    rule.sourceIps.takeIf { it.isNotEmpty() }?.joinToString(), rule.destinationIps.takeIf { it.isNotEmpty() }?.joinToString(), rule.description).joinToString(" · ")
internal fun firewallRuleText(rule: FirewallRuleInventory): String = firewallRuleText(FirewallRuleSpec(rule.direction, rule.protocol, rule.port, rule.sourceIps, rule.destinationIps, rule.description))
internal fun firewallAttachmentText(resources: List<FirewallAppliedResource>): String = resources.joinToString("\n") {
    listOfNotNull(it.type, it.serverId?.let { id -> "#$id" }, it.labelSelector).joinToString(" · ") +
        it.appliedToResources.takeIf { children -> children.isNotEmpty() }?.let { children -> "\n" + firewallAttachmentText(children) }.orEmpty()
}
