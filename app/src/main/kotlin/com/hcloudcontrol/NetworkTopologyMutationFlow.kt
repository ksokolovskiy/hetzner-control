package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.network.*
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import kotlinx.coroutines.*

@Composable
internal fun PrivateNetworkCreationFlow(repository: HCloudRepository, onDismiss: () -> Unit,
    onCreatedId: (Long) -> Unit, onDiscardDraft: () -> Unit = {}) = NetworkTopologyMutationFlow(
    null, NetworkMutationDraft(NetworkMutationKind.CREATE), repository, {}, onDismiss, onCreatedId, onDiscardDraft)

/** Forms supply fields only. The one adapter owns fresh identity/dependency checks and writes. */
@Composable
internal fun NetworkTopologyMutationFlow(networkId: Long?, initial: NetworkMutationDraft, repository: HCloudRepository,
    onCompleted: () -> Unit, onDismiss: () -> Unit, onCreatedId: ((Long) -> Unit)? = null,
    onDiscardDraft: () -> Unit = {}) {
    val owner = LocalOperationContext.current
    val scopeKey = "${owner?.connectionId}:${owner?.endpoint}:network:$networkId:${initial.identity()}"
    var name by rememberSaveable(scopeKey) { mutableStateOf(initial.name) }
    var range by rememberSaveable(scopeKey) { mutableStateOf(initial.subnet?.ipRange ?: initial.range) }
    var zone by rememberSaveable(scopeKey) { mutableStateOf(initial.subnet?.networkZone.orEmpty()) }
    var destination by rememberSaveable(scopeKey) { mutableStateOf(initial.route?.destination.orEmpty()) }
    var gateway by rememberSaveable(scopeKey) { mutableStateOf(initial.route?.gateway.orEmpty()) }
    var serverId by rememberSaveable(scopeKey) { mutableStateOf(initial.serverId) }
    var picker by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failedRead by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    val draft = when (initial.kind) {
        NetworkMutationKind.CREATE -> initial.copy(name = name, range = range)
        NetworkMutationKind.ADD_SUBNET -> initial.copy(subnet = if (range.isNotBlank() && zone.isNotBlank()) PrivateNetworkSubnetSpec(range, zone) else null)
        NetworkMutationKind.ADD_ROUTE -> initial.copy(route = if (destination.isNotBlank() && gateway.isNotBlank()) PrivateNetworkRouteSpec(destination, gateway) else null)
        NetworkMutationKind.ATTACH -> initial.copy(serverId = serverId)
        else -> initial
    }
    val session = rememberOperationSession("network", networkId?.toString(), draft.identity(), 0)
    val coordinator = LocalOperationCoordinator.current
    val context by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val adapter = remember(repository, coordinator) { coordinator?.let { NetworkTopologyAdapter(repository, it, { context }, { allowed }) } }
    var review by remember(session) { mutableStateOf<OperationReviewContent?>(null) }
    var reviewedSnapshot by remember(session) { mutableStateOf<NetworkSnapshot?>(null) }
    var sent by remember(session) { mutableStateOf(session.record != null) }
    val record = remember(session, version, busy, sent) {
        session.record ?: coordinator?.all()?.lastOrNull { it.isPending && it.intent.resource == session.intent?.resource }
    }
    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val title = stringResource(when (initial.kind) {
        NetworkMutationKind.CREATE -> R.string.network_create_title
        NetworkMutationKind.ADD_SUBNET -> R.string.network_add_subnet
        NetworkMutationKind.DELETE_SUBNET -> R.string.network_delete_subnet
        NetworkMutationKind.ADD_ROUTE -> R.string.network_add_route
        NetworkMutationKind.DELETE_ROUTE -> R.string.network_delete_route
        NetworkMutationKind.ATTACH -> R.string.network_attach_server
        NetworkMutationKind.DETACH -> R.string.network_detach_server
        NetworkMutationKind.DELETE -> R.string.network_delete
    })
    val warning = stringResource(when (initial.kind) {
        NetworkMutationKind.CREATE -> R.string.network_create_warning
        NetworkMutationKind.DETACH -> R.string.network_detach_warning
        NetworkMutationKind.DELETE -> R.string.topology_delete_detaches
        else -> R.string.network_topology_warning
    })
    val externalWarning = stringResource(R.string.topology_validation_scope)
    val metadata = rememberSectionLoader("$scopeKey:metadata") { networkId?.let { repository.getPrivateNetwork(it) } }
    val servers = rememberSectionLoader("$scopeKey:servers") { if (initial.kind == NetworkMutationKind.ATTACH) repository.getServers() else emptyList() }
    val locations = rememberSectionLoader("$scopeKey:zones") { if (initial.kind == NetworkMutationKind.ADD_SUBNET) repository.getLocations() else emptyList() }
    val zones = locations.state.data.orEmpty().mapNotNull { it.networkZone }.distinct().sorted()
    val candidates = metadata.state.data?.let { net -> servers.state.data.orEmpty().filter { eligibleNetworkMember(net, it) } }.orEmpty()
    val validFields = when (initial.kind) {
        NetworkMutationKind.CREATE -> name.isNotBlank() && name.length <= 128 && validPrivateNetworkCidr(range)
        NetworkMutationKind.ADD_SUBNET -> metadata.state.data?.let { net -> draft.subnet?.let { validCloudSubnet(net, it, zones.toSet()) } } == true && locations.state.canOfferMutation
        NetworkMutationKind.ADD_ROUTE -> metadata.state.data?.let { net -> draft.route?.let { validNetworkRoute(net, it) } } == true
        NetworkMutationKind.ATTACH -> servers.state.canOfferMutation && candidates.any { it.id == serverId }
        else -> true // Exact full-entry checks run against the fresh review snapshot and again before sending.
    }
    val valid = validFields && (networkId == null || metadata.state.canOfferMutation)
    val guard = rememberDirtyFormExitGuard(name.isNotBlank() || range.isNotBlank() || destination.isNotBlank() || gateway.isNotBlank() || serverId != null,
        onRetain = {}, onDiscard = onDiscardDraft, onLeave = onDismiss, submitted = record != null, contextKey = scopeKey)
    RegisterResourceFormNavigation(RootDestination.NETWORK, ResourceType.NETWORK, networkId?.toString(), title) {
        if (record != null) {
            if (record.canClose) { guard.requestLeave(); session.startNewAttemptAfterTerminalClose() }
        } else if (review != null) review = null else guard.requestLeave()
    }
    suspend fun refreshResult() {
        busy = true
        try {
            failedRead = !refreshOperationResource(session) {
                check(context == session.context)
                withContext(Dispatchers.IO) { repository.getPrivateNetworks() }
                check(context == session.context)
                onCompleted()
            }
            version++
        } finally { busy = false }
    }
    if (record != null) {
        OperationResultDialog(title, record, record.intent.resource.context.connectionId,
            record.intent.resource.resourceId ?: title, guard::requestLeave, busy,
            session = session.takeIf { it.intent?.intentId == record.intent.intentId },
            onCheckStatus = {
                busy = true; failedRead = false
                scope.launch {
                    try { withContext(Dispatchers.IO) { adapter?.reconcile(record) }; version++ }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { failedRead = true }
                    finally { busy = false }
                }
            }, onRefreshResource = { if (!busy) scope.launch { refreshResult() } },
            onOpenResource = record.createdResourceId?.toLongOrNull()?.let { id -> onCreatedId?.let { open -> ({ open(id) }) } }) {
            if (failedRead) Text(stringResource(R.string.ds_read_error), color = MaterialTheme.colorScheme.error)
        }
        LaunchedEffect(record.stage, record.intent.intentId) { if (record.stage == OperationStage.SUCCEEDED) {
            refreshResult()
        } }
        return
    }
    fun readReview() {
        if (busy || !valid || !allowed) return
        busy = true; failedRead = false
        scope.launch {
            try {
                val snapshot = withContext(Dispatchers.IO) { networkId?.let { checkNotNull(adapter).readSnapshot(it) } }
                reviewedSnapshot = snapshot
                val effects = buildList {
                    snapshot?.network?.let { net -> add(ReviewChange(net.name, null, net.ipRange)) }
                    if (initial.kind == NetworkMutationKind.CREATE) add(ReviewChange(name.trim(), null, range))
                    draft.subnet?.let { subnet ->
                        val original = snapshot?.network?.subnets?.firstOrNull { it.ipRange == subnet.ipRange && it.networkZone == subnet.networkZone }
                        add(ReviewChange(androidContext.getString(R.string.topology_subnets), null,
                            listOfNotNull(subnet.ipRange, subnet.networkZone, original?.type, original?.gateway).joinToString(" · ")))
                    }
                    draft.route?.let { route -> add(ReviewChange(androidContext.getString(R.string.topology_routes), null, "${route.destination} · ${route.gateway}")) }
                    draft.serverId?.let { add(ReviewChange(androidContext.getString(R.string.common_select_server), null, it.toString())) }
                    if (initial.kind == NetworkMutationKind.DELETE) snapshot?.members?.forEach { member ->
                        add(ReviewChange(member.type, null, "${member.id} · ${member.ip} · ${member.subnet}"))
                    }
                }
                review = OperationReviewContent(checkNotNull(session.intent), System.currentTimeMillis(), title,
                    session.context?.connectionId.orEmpty(), snapshot?.network?.name ?: name.trim(), effects,
                    typedIdentity = snapshot?.network?.name.takeIf { initial.kind == NetworkMutationKind.DELETE },
                    acknowledgements = listOf(RiskAcknowledgement("topology", "$warning\n$externalWarning")),
                    blockedReason = when {
                        initial.kind != NetworkMutationKind.DELETE -> null
                        snapshot?.network?.protectionKnown != true -> androidContext.getString(R.string.ds_cost_unknown)
                        snapshot.network.deleteProtected -> androidContext.getString(R.string.network_delete_protected)
                        else -> null
                    })
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { failedRead = true }
            finally { busy = false }
        }
    }
    val reviewed = review
    if (reviewed != null) OperationReviewDialog(reviewed, onConfirm = { proof ->
        busy = true; failedRead = false
        scope.launch {
            try { withContext(Dispatchers.IO) { checkNotNull(adapter).submit(proof, draft, reviewedSnapshot) {
                withContext(Dispatchers.Main) { sent = true; version++ }
            } }; version++ }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { failedRead = true; review = null; version++ }
            finally { busy = false }
        }
    }, onDismiss = { if (!busy) review = null }, submitEnabled = allowed && !busy) else {
        HCloudDialog(title, stringResource(R.string.ds_operation_review), ::readReview, guard::requestLeave,
            confirmEnabled = valid && allowed && !busy && session.configured) {
            if (networkId != null) HCloudReadSection(metadata) { network -> network?.let { Text(it.name) } }
            when (initial.kind) {
                NetworkMutationKind.CREATE -> {
                    HCloudFormField(name, { name = it }, stringResource(R.string.network_create_name),
                        error = stringResource(R.string.topology_invalid).takeIf { name.length > 128 })
                    HCloudFormField(range, { range = it }, stringResource(R.string.network_create_range),
                        error = stringResource(R.string.topology_invalid).takeIf { range.isNotBlank() && !validPrivateNetworkCidr(range) })
                    Text(stringResource(R.string.topology_network_cidr_hint))
                }
                NetworkMutationKind.ADD_SUBNET -> {
                    HCloudFormField(range, { range = it }, stringResource(R.string.network_subnet_range),
                        error = stringResource(R.string.topology_invalid).takeIf { range.isNotBlank() && zone.isNotBlank() && metadata.state.canOfferMutation && locations.state.canOfferMutation && !validFields })
                    HCloudReadSection(locations) {
                        OutlinedButton(onClick = { picker = true }) { Text(zone.ifEmpty { androidContext.getString(R.string.network_subnet_zone) }) }
                    }
                    Text(stringResource(R.string.topology_gateway_assigned))
                }
                NetworkMutationKind.ADD_ROUTE -> {
                    HCloudFormField(destination, { destination = it }, stringResource(R.string.network_route_destination),
                        error = stringResource(R.string.topology_invalid).takeIf { destination.isNotBlank() && ipv4Range(destination) == null })
                    HCloudFormField(gateway, { gateway = it }, stringResource(R.string.network_route_gateway),
                        error = stringResource(R.string.topology_invalid).takeIf { destination.isNotBlank() && gateway.isNotBlank() && metadata.state.canOfferMutation && !validFields })
                }
                NetworkMutationKind.ATTACH -> {
                    if (metadata.state.data?.subnets?.none { it.type in setOf("cloud", "server") } == true) Text(stringResource(R.string.topology_no_cloud_subnet))
                    OutlinedButton(onClick = { picker = true }) { Text(candidates.firstOrNull { it.id == serverId }?.name ?: stringResource(R.string.common_select_server)) }
                }
                else -> Text(listOfNotNull(draft.subnet?.ipRange, draft.subnet?.networkZone, draft.route?.destination, draft.route?.gateway,
                    draft.serverId?.toString(), networkId?.toString()).joinToString(" · "))
            }
            Text(warning)
            if (busy) CircularProgressIndicator()
            if (failedRead) Text(stringResource(R.string.topology_invalid), color = MaterialTheme.colorScheme.error)
        }
    }
    if (picker) {
        val source = if (initial.kind == NetworkMutationKind.ATTACH) servers.state else locations.state
        HCloudResourcePicker(if (initial.kind == NetworkMutationKind.ATTACH) stringResource(R.string.common_select_server) else stringResource(R.string.network_subnet_zone),
            ResourcePickerState(scopeKey, if (initial.kind == NetworkMutationKind.ATTACH) candidates.map { ResourcePickerItem(it.id.toString(), it.name, it.location) }
                else zones.map { ResourcePickerItem(it, it) }, loading = !source.hasSuccessfulResponse && source.error == null,
                safeError = source.error?.let { stringResource(R.string.ds_read_error) }),
            if (initial.kind == NetworkMutationKind.ATTACH) serverId?.toString() else zone,
            { if (initial.kind == NetworkMutationKind.ATTACH) serverId = it.toLong() else zone = it }, { picker = false },
            onRetry = { if (initial.kind == NetworkMutationKind.ATTACH) servers.refresh() else locations.refresh() })
    }
    DirtyFormExitPrompt(guard)
}
