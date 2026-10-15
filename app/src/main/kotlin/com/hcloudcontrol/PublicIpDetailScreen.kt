package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.*
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PublicIpDetailScreen(repository: HCloudRepository, kind: PublicIpKind, id: Long, scope: String,
    onBack: () -> Unit, onChanged: () -> Unit, onDeleted: () -> Unit) {
    val adapter = remember(repository, kind, id) { PublicIpMutationAdapter(repository, kind, id) }
    val metadata = rememberSectionLoader("$scope:${kind.identity}:$id") { adapter.read() }
    val history = rememberSectionLoader("$scope:${kind.identity}:$id:history") {
        if (kind == PublicIpKind.PRIMARY) repository.getPrimaryIpActions(id) else repository.getFloatingIpActions(id)
    }
    val title = metadata.state.data?.name ?: stringResource(R.string.network_public_ips)
    RegisterResourceChildNavigation(RootDestination.NETWORK, if (kind == PublicIpKind.PRIMARY) ResourceType.PRIMARY_IP else ResourceType.FLOATING_IP, id.toString(), title, onBack)
    var reverseDns by rememberContextualFlag("$scope:${kind.identity}:$id:ptr")
    val formDrafts = rememberSaveableStateHolder()
    var mutationCode by rememberContextualText("$scope:${kind.identity}:$id:mutation")
    val mutation = PublicIpMutation.entries.firstOrNull { it.name == mutationCode }
    val allowed = LocalHighImpactOperations.current.isAllowed
    fun changed() { metadata.refresh(); history.refresh(); onChanged() }
    HCloudPullToRefresh(metadata.state.refreshing || history.state.refreshing, ::changed) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            HCloudReadSection(metadata) { ip ->
                Text(ip.name, style = MaterialTheme.typography.headlineSmall)
                Text(ip.address)
                Text(ip.location.orEmpty())
                Text(ip.assigneeId?.let { "#$it" } ?: stringResource(R.string.primary_ip_unassigned))
                ResourceProtectionControls(ip.name, ip.protected,
                    remember(repository, kind, id) { ProtectionAdapter(if (kind == PublicIpKind.PRIMARY) ProtectionResource.PRIMARY_IP else ProtectionResource.FLOATING_IP, id, ProtectionField.DELETE, repository) },
                    ::changed, metadata.state.canOfferMutation, currentKnown = ip.protectionKnown)
                if (allowed) {
                    OutlinedButton({ reverseDns = true }, enabled = metadata.state.canOfferMutation) { Text(stringResource(R.string.network_ptr_title)) }
                    OutlinedButton({ mutationCode = PublicIpMutation.ASSIGN.name }, enabled = metadata.state.canOfferMutation && (kind == PublicIpKind.FLOATING || ip.assigneeId == null)) { Text(stringResource(R.string.network_ip_assign)) }
                    OutlinedButton({ mutationCode = PublicIpMutation.UNASSIGN.name }, enabled = metadata.state.canOfferMutation && ip.assigneeId != null && ip.assigneeType == "server") { Text(stringResource(R.string.network_ip_unassign)) }
                    OutlinedButton({ mutationCode = PublicIpMutation.DELETE.name }, enabled = metadata.state.canOfferMutation && ip.protectionKnown && ip.assigneeId == null && !ip.protected) { Text(stringResource(R.string.network_ip_delete)) }
                }
            }
            HCloudReadSection(history) { actions -> actions.forEach { Text("#${it.id} · ${it.command} · ${it.status}") } }
        }
    }
    if (reverseDns) metadata.state.data?.let { ip -> formDrafts.SaveableStateProvider("$scope:${kind.identity}:$id:ptr") { ReverseDnsDialog(repository, kind.identity, id, ip.name,
        readAddresses = { adapter.read().pointers },
        write = { address, hostname -> if (kind == PublicIpKind.PRIMARY) repository.changePrimaryIpReverseDns(id, address, hostname) else repository.changeFloatingIpReverseDns(id, address, hostname) },
        onDismiss = { reverseDns = false }, onCompleted = ::changed) } }
    mutation?.let { change -> metadata.state.data?.let { ip -> formDrafts.SaveableStateProvider("$scope:${kind.identity}:$id:$change") { PublicIpMutationDialog(adapter, ip, change,
        onDismiss = { mutationCode = "" }, onCompleted = { if (change == PublicIpMutation.DELETE) onChanged() else changed() }, onDeleted = onDeleted) } } }
}

@Composable
private fun PublicIpMutationDialog(adapter: PublicIpMutationAdapter, expected: PublicIpSnapshot, mutation: PublicIpMutation,
    onDismiss: () -> Unit, onCompleted: () -> Unit, onDeleted: () -> Unit) {
    val context = LocalOperationContext.current
    val activeContext by rememberUpdatedState(context)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val servers = rememberSectionLoader("$context:ip-targets") { adapter.repository.getServers() }
    val locations = rememberSectionLoader("$context:ip-locations") { adapter.repository.getLocations() }
    var selected by rememberContextualResourceId("${context?.connectionId}:${context?.endpoint}:${adapter.kind.identity}:${expected.id}:$mutation:target")
    var reviewed by remember { mutableStateOf<OperationReviewContent?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    val session = rememberOperationSession(adapter.kind.identity, expected.id.toString(), mutation.name.lowercase(), listOf(expected, selected).hashCode().toLong() and 0xffffffffL)
    val record = remember(session, tick, busy) { session.record }
    val scope = rememberCoroutineScope()
    val title = stringResource(when (mutation) { PublicIpMutation.ASSIGN -> R.string.network_ip_assign; PublicIpMutation.UNASSIGN -> R.string.network_ip_unassign; PublicIpMutation.DELETE -> R.string.network_ip_delete })
    val warning = stringResource(R.string.network_ip_mutation_risk)
    val assignmentLabel = stringResource(R.string.network_ip_review_assignment)
    val protectionLabel = if (expected.protectionKnown) stringResource(R.string.resource_protection_current,
        stringResource(if (expected.protected) R.string.common_protected_enabled else R.string.common_protected_disabled))
        else stringResource(R.string.network_ip_protection_unknown)
    val noAssignment = stringResource(R.string.primary_ip_unassigned)
    RegisterResourceFormNavigation(RootDestination.NETWORK, if (adapter.kind == PublicIpKind.PRIMARY) ResourceType.PRIMARY_IP else ResourceType.FLOATING_IP, expected.id.toString(), title) {
        if (!busy && (record == null || record.canClose)) {
            if (reviewed != null) reviewed = null
            else if (mutation == PublicIpMutation.DELETE && record?.stage == OperationStage.SUCCEEDED) onDeleted()
            else onDismiss()
        }
    }
    val candidates = servers.state.data.orEmpty().sortedBy { it.name.lowercase() }.map { server -> server to
        if (adapter.kind == PublicIpKind.PRIMARY) primaryIpAssignmentBlock(checkNotNull(expected.primary), server)
        else floatingIpAssignmentBlock(checkNotNull(expected.floating), server, locations.state.data.orEmpty()) }
    val eligible = candidates.filter { it.second == null }.map { it.first }
    fun reconcile() { if (!busy && activeContext == session.context) { busy = true; scope.launch {
        try {
            if (session.record?.originalActionIds?.isNotEmpty() == true) awaitOriginalOperationActions(session, adapter.repository) { tick++ }
            else if (mutation == PublicIpMutation.DELETE) session.reconcileExistingEffect(adapter.deleted())
            if (session.record?.stage == OperationStage.SUCCEEDED) onCompleted()
        } catch (cancel: CancellationException) { session.unknownIfPending(); throw cancel }
        catch (_: Exception) { session.unknownIfPending() }
        finally { busy = false; tick++ }
    } } }
    if (record != null) {
        OperationResultDialog(title, record, context?.connectionId.orEmpty(), expected.name,
            { if (mutation == PublicIpMutation.DELETE && record.stage == OperationStage.SUCCEEDED) onDeleted() else onDismiss() },
            busy = busy, onCheckStatus = ::reconcile, onRefreshResource = onCompleted, session = session)
        return
    }
    reviewed?.let { model ->
        OperationReviewDialog(model, { review -> if (!busy) { busy = true; scope.launch {
            try {
                adapter.validate(expected, mutation, selected?.let { id -> servers.state.data?.firstOrNull { it.id == id } })
                session.beginReviewed(review, allowed, activeContext); tick++
                check(activeContext == session.context && allowed)
                val action = adapter.execute(mutation, selected)
                if (action != null) { session.accepted(action); tick++; awaitOriginalOperationActions(session, adapter.repository, listOf(action)) { tick++ } }
                else session.succeed() // DELETE 204 is authoritative; a lost response stays unknown.
                if (session.record?.stage == OperationStage.SUCCEEDED) onCompleted()
            } catch (cancel: CancellationException) { session.unknownIfPending(); throw cancel }
            catch (error: Exception) { if (session.record != null) session.rejectOrUnknown(error) else failure = true }
            finally { reviewed = null; busy = false; tick++ }
        } } }, { if (!busy) reviewed = null }, allowed && !busy)
        return
    }
    val targetsReady = servers.state.canOfferMutation && (adapter.kind == PublicIpKind.PRIMARY || locations.state.canOfferMutation)
    HCloudDialog(title, stringResource(R.string.server_dns_ptr_review), {
        if (session.intent != null && allowed && !busy) { busy = true; failure = false; scope.launch {
            try {
                val target = selected?.let { id -> servers.state.data?.firstOrNull { it.id == id } }
                adapter.validate(expected, mutation, target)
                val previous = expected.assigneeId?.let { id ->
                    check(expected.assigneeType == "server")
                    adapter.repository.getServer(id).let { "${it.name} (#${it.id})" }
                } ?: noAssignment
                val next = if (mutation == PublicIpMutation.ASSIGN) checkNotNull(target).let { "${it.name} (#${it.id})" } else noAssignment
                reviewed = OperationReviewContent(checkNotNull(session.intent), System.currentTimeMillis(), title,
                    context?.connectionId.orEmpty(), expected.name,
                    listOf(ReviewChange("${adapter.kind.identity} · ${expected.family}", null, expected.address),
                        ReviewChange(assignmentLabel, previous, next), ReviewChange(protectionLabel, null, expected.name)),
                    typedIdentity = expected.name.takeIf { mutation == PublicIpMutation.DELETE },
                    acknowledgements = listOf(RiskAcknowledgement("public-ip", warning)))
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure = true }
            finally { busy = false }
        } }
    }, onDismiss, confirmEnabled = !busy && allowed && session.configured && (mutation != PublicIpMutation.ASSIGN || (targetsReady && selected != null && eligible.any { it.id == selected }))) {
        if (mutation == PublicIpMutation.ASSIGN) {
            HCloudReadSection(servers) { }
            if (adapter.kind == PublicIpKind.FLOATING) HCloudReadSection(locations) {}
            if (targetsReady && eligible.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.NO_ELIGIBLE))
            candidates.forEach { (server, blocked) -> Row {
                RadioButton(selected == server.id, { selected = server.id }, enabled = blocked == null && targetsReady)
                Column { Text(server.name); blocked?.let { Text(publicIpExclusionText(it), style = MaterialTheme.typography.bodySmall) } }
            } }
        }
        Text(expected.address)
        if (failure) Text(stringResource(R.string.network_ptr_review_failed), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun publicIpExclusionText(block: PublicIpAssignmentBlock): String = stringResource(when (block) {
    PublicIpAssignmentBlock.ALREADY_ASSIGNED -> R.string.network_ip_reason_assigned
    PublicIpAssignmentBlock.UNKNOWN_LOCATION -> R.string.network_ip_reason_unknown_location
    PublicIpAssignmentBlock.DIFFERENT_LOCATION -> R.string.network_ip_reason_location
    PublicIpAssignmentBlock.SERVER_NOT_OFF -> R.string.network_ip_reason_power
    PublicIpAssignmentBlock.FAMILY_OCCUPIED -> R.string.network_ip_reason_family
    PublicIpAssignmentBlock.MISSING_PRIMARY_IP -> R.string.network_ip_reason_missing_primary
    PublicIpAssignmentBlock.UNKNOWN_ZONE -> R.string.network_ip_reason_unknown_zone
    PublicIpAssignmentBlock.DIFFERENT_ZONE -> R.string.network_ip_reason_zone
    PublicIpAssignmentBlock.UNSUPPORTED_ASSIGNEE -> R.string.network_ip_reason_assignee
})
