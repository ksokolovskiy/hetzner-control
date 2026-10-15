package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.primaryIpAssignmentBlock
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun PrimaryIpCreationFlow(repository: HCloudRepository, onDismiss: () -> Unit, onCreatedId: (Long) -> Unit,
    onCreated: () -> Unit = {}) {
    val context = LocalOperationContext.current
    val activeContext by rememberUpdatedState(context)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val draftScope = "${context?.connectionId}:${context?.endpoint}:create-primary"
    var name by rememberContextualText("$draftScope:name")
    var family by rememberContextualText("$draftScope:family", "ipv4")
    var labelsText by rememberContextualText("$draftScope:labels")
    var locationName by rememberContextualText("$draftScope:location")
    var targetId by rememberContextualResourceId("$draftScope:target")
    var picker by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf(false) }
    var tick by remember { mutableStateOf(0) }
    var model by remember { mutableStateOf<OperationReviewContent?>(null) }
    val locations = rememberSectionLoader("$context:create-primary:locations") { repository.getLocations() }
    val servers = rememberSectionLoader("$context:create-primary:servers") { repository.getServers() }
    val labels = parseResourceLabels(labelsText)
    val prototype = PrimaryIpInventory(1, name, family, "", locationName.orEmpty(), null, null, false, labels.orEmpty(), emptyList())
    val eligible = servers.state.data.orEmpty().filter { primaryIpAssignmentBlock(prototype, it) == null }
    val session = rememberOperationSession("primary_ip", null, "create", listOf(name, family, labelsText, locationName, targetId).hashCode().toLong() and 0xffffffffL)
    val record = remember(session, tick, busy) { session.record }
    LaunchedEffect(record?.intent?.intentId, record?.stage) {
        if (record?.stage == OperationStage.SUCCEEDED) onCreated()
    }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.primary_ip_create_title)
    val stringResourceLabels = stringResource(R.string.primary_ip_create_labels)
    val risk = stringResource(R.string.network_ip_create_risk)
    val guard = rememberDirtyFormExitGuard(name.isNotBlank() || labelsText.isNotBlank() || locationName.isNotBlank() || targetId != null,
        onRetain = {}, onDiscard = { name = ""; labelsText = ""; locationName = ""; targetId = null; family = "ipv4" }, onLeave = onDismiss,
        submitted = record != null, contextKey = context)
    DirtyFormExitPrompt(guard)
    RegisterResourceFormNavigation(RootDestination.NETWORK, ResourceType.PRIMARY_IP, null, title) {
        if (!busy && (record == null || record.canClose)) guard.requestLeave()
    }
    fun checkStatus() { if (!busy && activeContext == session.context) { busy = true; scope.launch {
        try {
            val original = checkNotNull(session.record)
            if (original.originalActionIds.isNotEmpty()) awaitOriginalOperationActions(session, repository) { tick++ }
            else original.createdResourceId?.toLongOrNull()?.let { id ->
                val actual = repository.getPrimaryIp(id)
                session.reconcileCreatedResource(id.toString(), actual.id == id)
            }
        } catch (cancel: CancellationException) { session.unknownIfPending(); throw cancel }
        catch (_: Exception) { session.unknownIfPending() }
        finally { busy = false; tick++ }
    } } }
    if (record != null) {
        OperationResultDialog(title, record, context?.connectionId.orEmpty(), name, { guard.requestLeave() }, busy,
            onCheckStatus = ::checkStatus, onOpenResource = record.createdResourceId?.toLongOrNull()?.let { id -> { onCreatedId(id) } }, session = session)
        return
    }
    model?.let { review ->
        OperationReviewDialog(review, { confirmed -> if (!busy) { busy = true; scope.launch {
            try {
                val location = checkNotNull(locationName)
                check(repository.getLocations().any { it.name == location })
                targetId?.let { id -> check(primaryIpAssignmentBlock(prototype, repository.getServer(id)) == null) }
                val spec = CreatePrimaryIpSpec(family, name.trim(), location, targetId, checkNotNull(parseResourceLabels(labelsText)))
                session.beginReviewed(confirmed, allowed, activeContext); tick++
                check(activeContext == session.context && allowed)
                val created = repository.createPrimaryIp(spec)
                session.acceptedActions(listOfNotNull(created.action), created.primaryIp.id.toString(), false); tick++
                if (created.action != null) awaitOriginalOperationActions(session, repository, listOfNotNull(created.action)) { tick++ }
                else session.reconcileCreatedResource(created.primaryIp.id.toString(), repository.getPrimaryIp(created.primaryIp.id).id == created.primaryIp.id)
            } catch (cancel: CancellationException) { session.unknownIfPending(); throw cancel }
            catch (error: Exception) { if (session.record != null) session.rejectOrUnknown(error) else failure = true }
            finally { model = null; busy = false; tick++ }
        } } }, { if (!busy) model = null }, !busy && allowed)
        return
    }
    val valid = name.trim().isNotEmpty() && name.length <= 128 && labels != null && locationName.isNotBlank() && locations.state.canOfferMutation &&
        (targetId == null || (servers.state.canOfferMutation && eligible.any { it.id == targetId }))
    HCloudDialog(title, stringResource(R.string.server_dns_ptr_review), {
        if (valid && session.intent != null && !busy) { busy = true; failure = false; scope.launch {
            try {
                check(repository.getLocations().any { it.name == locationName })
                targetId?.let { id -> check(primaryIpAssignmentBlock(prototype, repository.getServer(id)) == null) }
                model = OperationReviewContent(checkNotNull(session.intent), System.currentTimeMillis(), title, context?.connectionId.orEmpty(), name.trim(),
            listOf(ReviewChange("Primary IP", null, "$family · $locationName"), ReviewChange("Server", null, targetId?.let { id -> servers.state.data?.firstOrNull { it.id == id }?.name ?: "#$id" } ?: "—"),
                ReviewChange(stringResourceLabels, null, labels.orEmpty().entries.joinToString("\n") { "${it.key}=${it.value}" }.ifEmpty { "—" })),
            cost = CostPresentation.Unavailable, acknowledgements = listOf(RiskAcknowledgement("paid-resource", risk)))
            } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { failure = true }
            finally { busy = false }
        } }
    }, { if (!busy) guard.requestLeave() }, confirmEnabled = !busy && valid && allowed && session.configured) {
        HCloudFormField(name, { name = it }, stringResource(R.string.primary_ip_create_name))
        HCloudSegmentedControl(listOf(ChoiceItem("ipv4", "IPv4"), ChoiceItem("ipv6", "IPv6")), family, { family = it; targetId = null })
        HCloudReadSection(locations) {}
        OutlinedButton({ picker = "location" }) { Text(locationName.ifBlank { stringResource(R.string.primary_ip_create_location) }) }
        OutlinedButton({ picker = "server" }, enabled = locationName.isNotBlank()) { Text(targetId?.let { id -> servers.state.data?.firstOrNull { it.id == id }?.name ?: "#$id" } ?: stringResource(R.string.primary_ip_create_unassigned)) }
        HCloudFormField(labelsText, { labelsText = it }, stringResource(R.string.primary_ip_create_labels), help = stringResource(R.string.primary_ip_create_labels_hint))
        HCloudCostPresentation(CostPresentation.Unavailable)
        if (failure) Text(stringResource(R.string.network_ptr_review_failed), color = MaterialTheme.colorScheme.error)
    }
    if (picker == "location") HCloudChoiceSheet(stringResource(R.string.primary_ip_create_location), locations.state.data.orEmpty().map { ChoiceItem(it.name, it.displayName()) }, locationName.orEmpty(),
        { locationName = it; targetId = null; picker = null }, { picker = null }, searchable = true)
    if (picker == "server") HCloudChoiceSheet(stringResource(R.string.network_ip_assign), listOf(ChoiceItem("none", stringResource(R.string.primary_ip_create_unassigned))) + eligible.map { ChoiceItem(it.id.toString(), it.name) }, targetId?.toString() ?: "none",
        { targetId = it.toLongOrNull(); picker = null }, { picker = null }, searchable = true)
}
