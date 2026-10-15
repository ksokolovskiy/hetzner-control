package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.*
import com.hcloudcontrol.ui.navigation.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** One PTR editor and durable operation flow for Server, Primary IP and Floating IP. */
@Composable
internal fun ReverseDnsDialog(repository: HCloudRepository, resourceType: String, resourceId: Long,
    resourceLabel: String, readAddresses: suspend () -> List<DnsPointer>,
    write: suspend (String, String?) -> CloudAction, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    val context = LocalOperationContext.current
    val allowed = LocalHighImpactOperations.current.isAllowed
    val activeContext by rememberUpdatedState(context)
    val changesAllowed by rememberUpdatedState(allowed)
    val addresses = rememberSectionLoader("$context:$resourceType:$resourceId:ptr") { readAddresses().distinctBy { it.ip } }
    val draftScope = "${context?.connectionId}:${context?.endpoint}:$resourceType:$resourceId:ptr"
    var selected by rememberContextualText("$draftScope:address")
    var hostname by rememberContextualText("$draftScope:hostname")
    var review by remember(context, resourceId) { mutableStateOf<OperationReviewContent?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var revision by remember { mutableStateOf(0L) }
    var tick by remember { mutableStateOf(0) }
    val session = rememberOperationSession(resourceType, resourceId.toString(), "change_dns_ptr", revision)
    val record = remember(session, tick, busy) { session.record }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.network_ptr_title)
    val label = stringResource(R.string.server_dns_ptr_hostname)
    val reset = stringResource(R.string.network_ptr_reset)
    val risk = stringResource(R.string.network_ptr_risk)
    RegisterResourceFormNavigation(if (resourceType == "server") RootDestination.SERVERS else RootDestination.NETWORK,
        when (resourceType) { "server" -> ResourceType.SERVER; "primary_ip" -> ResourceType.PRIMARY_IP; else -> ResourceType.FLOATING_IP }, resourceId.toString(), title) {
        if (!busy && (record == null || record.canClose)) { if (review != null) review = null else onDismiss() }
    }
    fun refreshStatus() { if (!busy && activeContext == session.context) { busy = true; scope.launch {
        try { awaitOriginalOperationActions(session, repository) { tick++ }; if (session.record?.stage == OperationStage.SUCCEEDED) { onCompleted(); addresses.refresh() } }
        catch (cancel: CancellationException) { session.unknownIfPending(); throw cancel }
        catch (_: Exception) { session.unknownIfPending() }
        finally { busy = false; tick++ }
    } } }
    if (record != null) {
        OperationResultDialog(title, record, context?.connectionId.orEmpty(), resourceLabel, onDismiss,
            busy = busy, onCheckStatus = ::refreshStatus,
            onRefreshResource = { addresses.refresh(); onCompleted() }, session = session)
        return
    }
    review?.let { model ->
        OperationReviewDialog(model, onConfirm = { reviewed ->
            if (!busy) { busy = true; scope.launch {
                try {
                    val address = selected.also { check(it.isNotBlank()) }
                    val old = addresses.state.data?.firstOrNull { it.ip == address }
                    check(readAddresses().firstOrNull { it.ip == address } == old && old != null)
                    val normalized = normalizePtrHostname(hostname)
                    session.beginReviewed(reviewed, changesAllowed, activeContext)
                    tick++
                    check(activeContext == session.context && changesAllowed)
                    val action = write(address, normalized)
                    session.accepted(action); tick++
                    awaitOriginalOperationActions(session, repository, listOf(action)) { tick++ }
                    if (session.record?.stage == OperationStage.SUCCEEDED) { onCompleted(); addresses.refresh() }
                } catch (cancel: CancellationException) { session.unknownIfPending(); throw cancel }
                catch (error: Exception) { if (session.record != null) session.rejectOrUnknown(error) else failed = true }
                finally { busy = false; review = null; tick++ }
            } }
        }, onDismiss = { if (!busy) review = null }, submitEnabled = allowed && !busy)
        return
    }
    val normalized = runCatching { normalizePtrHostname(hostname) }
    HCloudDialog(title, stringResource(R.string.server_dns_ptr_review), {
        val address = selected
        if (address.isNotBlank() && normalized.isSuccess && addresses.state.canOfferMutation && allowed && session.intent != null && !busy) {
            busy = true; failed = false; scope.launch {
                try {
                    val old = addresses.state.data?.firstOrNull { it.ip == address }
                    check(old != null && readAddresses().firstOrNull { it.ip == address } == old)
                    review = OperationReviewContent(checkNotNull(session.intent), System.currentTimeMillis(), title,
                        context?.connectionId.orEmpty(), resourceLabel,
                        listOf(ReviewChange(address, old.dnsPtr, normalized.getOrNull() ?: reset)),
                        acknowledgements = listOf(RiskAcknowledgement("reverse-dns", risk)))
                } catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { failed = true }
                finally { busy = false }
            }
        }
    }, { if (!busy) onDismiss() }, confirmEnabled = !busy && selected.isNotBlank() && normalized.isSuccess && addresses.state.canOfferMutation && allowed && session.configured) {
        HCloudReadSection(addresses) { rows ->
            if (rows.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.EMPTY))
            rows.forEach { row ->
                Row { RadioButton(selected == row.ip, onClick = { selected = row.ip; hostname = row.dnsPtr.orEmpty(); revision++; failed = false })
                    Text(row.ip) }
            }
        }
        HCloudFormField(hostname, { hostname = it; revision++; failed = false }, label,
            help = reset, error = if (normalized.isFailure) stringResource(R.string.network_ptr_invalid) else null)
        if (failed) Text(stringResource(R.string.network_ptr_review_failed), color = MaterialTheme.colorScheme.error)
    }
}
