package com.hcloudcontrol

import androidx.compose.foundation.layout.Row

import androidx.activity.compose.BackHandler
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Resize is a reviewed mutation of the existing ID, never an alternate provisioning workflow. */
@Composable
internal fun UnifiedResizeServerScreen(server: Server, repository: HCloudRepository, onCancel: () -> Unit, onChanged: () -> Unit) {
    val context = LocalOperationContext.current
    val originalServer = remember(context, server.id) { server }
    val activeContext by rememberUpdatedState(context)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val typesRead = rememberSectionLoader("$context:resize:${originalServer.id}:types", isEmpty = { it: List<ServerTypeOption> -> it.isEmpty() }) { repository.getServerTypes() }
    val types = typesRead.state.data
    var targetId by rememberSaveable(context?.connectionId, originalServer.id) { mutableStateOf<Long?>(null) }
    // Intentionally false: retaining the existing disk is the explicit initial path.
    var growDisk by rememberSaveable(context?.connectionId, originalServer.id) { mutableStateOf(false) }
    var choose by remember { mutableStateOf(false) }
    var review by remember { mutableStateOf(false) }
    var reviewAt by remember { mutableStateOf(System.currentTimeMillis()) }
    var busy by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var resultRevision by remember { mutableStateOf(0) }
    var reviewedPrice by remember { mutableStateOf<PricingState?>(null) }
    val revision = listOf(originalServer.id, originalServer.serverType, originalServer.location, targetId, growDisk).hashCode().toLong() and 0xffffffffL
    val session = rememberOperationSession("server", originalServer.id.toString(), "resize_server", revision)
    val record = remember(session, resultRevision, busy) { session.record }
    val source = types?.firstOrNull { it.name == originalServer.serverType }
    val target = types?.firstOrNull { it.id == targetId }
    val eligible = types.orEmpty().filter { serverResizeEligible(originalServer, source, it) }
        .sortedWith(compareBy<ServerTypeOption> { it.cores }.thenBy { it.memoryGb }.thenBy { it.diskGb }.thenBy { it.name })
    val price = rememberWizardPrice(repository, target, originalServer.location)
    val scope = rememberCoroutineScope()
    fun back() { if (record != null) { if (record.canClose) { session.startNewAttemptAfterTerminalClose(); onCancel() } } else if (choose) choose = false else if (review) review = false else onCancel() }
    val nav = LocalNavigationContext.current
    val title = stringResource(R.string.resize_title)
    if (nav != null) RegisterChildNavigation(CanonicalDestination(RootDestination.SERVERS, DestinationKind.FORM,
        nav, ResourceType.SERVER, originalServer.id.toString()), title, ::back)
    if (LocalNavigationChrome.current == null) BackHandler { back() }
    fun refreshResult() { scope.launch {
        refreshOperationResource(session) {
            check(activeContext == session.context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.getServer(originalServer.id) }
            check(activeContext == session.context)
            onChanged()
        }
        resultRevision++
    } }
    fun checkStatus() {
        if (busy || activeContext != session.context) return
        busy = true
        scope.launch {
            try {
                if (session.record?.originalActionIds.isNullOrEmpty()) {
                    repository.getServer(originalServer.id)
                    // A resource read alone cannot confirm irreversible disk growth.
                    session.unknownIfPending()
                } else awaitOriginalOperationActions(session, repository)
            }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Throwable) { session.unknownIfPending() }
            finally { busy = false; resultRevision++ }
        }
    }
    if (record != null) {
        OperationResultPresentation(record, record.intent.resource.context.connectionId, "${originalServer.name} · #${originalServer.id}", onCancel, session = session,
            onCheckStatus = if (!busy) ::checkStatus else null,
            onRefreshResource = ::refreshResult, onOpenResource = if (record.stage == OperationStage.SUCCEEDED) ::refreshResult else null)
        return
    }
    if (types == null) {
        androidx.compose.foundation.layout.Column { HCloudReadSection(typesRead) {} }
        return
    }
    HCloudFormShell(title, stringResource(R.string.resize_review),
        {
            val selected = target
            if (selected != null && !busy) {
                busy = true
                scope.launch {
                    try {
                        reviewedPrice = readWizardPrice(repository, selected, originalServer.location)
                        reviewAt = System.currentTimeMillis(); review = true
                    } finally { busy = false }
                }
            }
        },
        submitEnabled = typesRead.state.canOfferMutation && target != null && target in eligible && originalServer.status == ServerStatus.OFF && allowed && session.configured && !review,
        busy = busy) {
        if (LocalNavigationChrome.current == null) TextButton(onClick = ::back) { Text(stringResource(R.string.common_back)) }
        HCloudReadSection(typesRead) {}
        Text("${originalServer.name} · #${originalServer.id}")
        Text(stringResource(R.string.resize_current_type, originalServer.serverType))
        Text(stringResource(R.string.resize_location, originalServer.location))
        Text(stringResource(if (originalServer.status == ServerStatus.OFF) R.string.resize_server_off else R.string.resize_server_must_be_off))
        if (source == null || originalServer.primaryDiskSizeGb == null || (originalServer.hardware?.storageType ?: source.storageType) == null) {
            Text(stringResource(R.string.resize_types_load_failed))
            OutlinedButton(onClick = typesRead::refresh) { Text(stringResource(R.string.common_retry)) }
        }
        OutlinedButton(onClick = { choose = true }) { Text(target?.name ?: stringResource(R.string.resize_choose_type)) }
        WizardPriceSection(price)
        Row { Checkbox(growDisk, { growDisk = it }); Text(stringResource(R.string.resize_upgrade_disk_label)) }
        Text(stringResource(if (growDisk) R.string.resize_disk_upgrade else R.string.resize_keep_disk))
        if (failed) Text(stringResource(R.string.resize_failed), color = MaterialTheme.colorScheme.error)
    }
    if (choose) WizardChoiceDialog(stringResource(R.string.resize_target_type), eligible.map {
        ChoiceItem(it.id.toString(), "${it.name} · ${it.cores} vCPU · ${it.memoryGb} GB · ${it.diskGb} GB", typesRead.state.canOfferMutation)
    }, targetId?.toString(), { choose = false }, readStatus = { HCloudReadSection(typesRead) {} }, showEmpty = typesRead.state.hasSuccessfulResponse && types.orEmpty().isNotEmpty(), selectionScope = "$context:resize:${originalServer.id}") { targetId = it.toLong(); choose = false }
    val identity = session.intent
    if (review && identity != null && target != null) {
        val frozenPrice = reviewedPrice ?: PricingState.Unavailable
        val cost = pricePresentation(frozenPrice)
        val diskText = stringResource(if (growDisk) R.string.resize_disk_upgrade else R.string.resize_keep_disk)
        val growAck = stringResource(R.string.resize_disk_upgrade_acknowledgement)
        val unknownAck = stringResource(R.string.wizard_unknown_cost_ack)
        val costAck = stringResource(R.string.wizard_resize_cost_ack)
        val effects = listOf(
            ReviewChange(stringResource(R.string.resize_target_type), source?.let { "${it.name} · ${it.cores} vCPU · ${it.memoryGb} GB · ${it.diskGb} GB" } ?: originalServer.serverType,
                "${target.name} · ${target.cores} vCPU · ${target.memoryGb} GB · ${target.diskGb} GB"),
            ReviewChange(stringResource(R.string.resize_upgrade_disk_label), null, diskText))
        val model = remember(identity, target, cost, reviewAt, growDisk, diskText, growAck, unknownAck, costAck, effects) {
            OperationReviewContent(identity, reviewAt, title, identity.resource.context.connectionId, "${originalServer.name} · #${originalServer.id}",
                effects, cost, acknowledgements = listOf(RiskAcknowledgement("cost", costAck)) +
                    (if (growDisk) listOf(RiskAcknowledgement("disk-growth", growAck)) else emptyList()) +
                    (if (cost !is CostPresentation.Available || cost.snapshot.incomplete) listOf(RiskAcknowledgement("unknown-cost", unknownAck)) else emptyList()))
        }
        OperationReviewDialog(model, onConfirm = { proof ->
            if (!busy) {
                busy = true; failed = false
                val frozenTarget = target
                val frozenGrowDisk = growDisk
                scope.launch {
                    try {
                        val freshServer = repository.getServer(originalServer.id)
                        check(freshServer.status == ServerStatus.OFF && freshServer.name == originalServer.name &&
                            freshServer.serverType == originalServer.serverType && freshServer.location == originalServer.location)
                        val freshType = repository.getServerTypes().firstOrNull { it.id == frozenTarget.id }
                        check(freshType == frozenTarget && serverResizeEligible(freshServer, source, frozenTarget))
                        check(sameReviewedPrice(frozenPrice, readWizardPrice(repository, frozenTarget, originalServer.location)))
                        session.beginReviewed(proof, allowed, activeContext)
                        resultRevision++
                        val action = repository.changeServerType(originalServer.id, frozenTarget.name, frozenGrowDisk)
                        session.acceptedActions(listOf(action), null, false)
                        resultRevision++
                        awaitOriginalOperationActions(session, repository, listOf(action)) { resultRevision++ }
                    } catch (cancelled: CancellationException) { throw cancelled }
                      catch (throwable: Throwable) { session.rejectOrUnknown(throwable); failed = true; review = false; if (session.record == null) typesRead.refresh() }
                    finally { busy = false; resultRevision++ }
                }
            }
        }, onDismiss = { review = false }, submitEnabled = allowed && !busy)
    }
}
