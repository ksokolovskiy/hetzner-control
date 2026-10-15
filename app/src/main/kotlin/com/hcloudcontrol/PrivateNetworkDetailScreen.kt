package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.network.*
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import kotlinx.coroutines.*

/** One immutable network detail with independent sections and structured topology children. */
@Composable
internal fun PrivateNetworkDetailScreen(networkId: Long, repository: HCloudRepository,
    onBack: () -> Unit, onDeleted: () -> Unit = onBack) {
    val owner = LocalOperationContext.current
    val scopeKey = "${owner?.connectionId}:${owner?.endpoint}:network:$networkId"
    var page by rememberSaveable(scopeKey) { mutableStateOf("overview") }
    var draft by rememberNetworkDraft(scopeKey)
    val drafts = rememberSaveableStateHolder()
    var inspect by remember { mutableStateOf<String?>(null) }
    var pendingId by rememberSaveable(scopeKey) { mutableStateOf<String?>(null) }
    var checking by remember { mutableStateOf(false) }
    var readFailed by remember { mutableStateOf(false) }
    var version by remember { mutableStateOf(0) }
    val allowed = LocalHighImpactOperations.current.isAllowed
    val metadata = rememberSectionLoader("$scopeKey:metadata") { repository.getPrivateNetwork(networkId) }
    val members = rememberSectionLoader("$scopeKey:members", { it: List<PrivateNetworkMember> -> it.isEmpty() }) { repository.getPrivateNetworkMembers(networkId) }
    val history = rememberSectionLoader("$scopeKey:history", { it: List<CloudAction> -> it.isEmpty() }) { repository.getPrivateNetworkActions(networkId) }
    val coordinator = LocalOperationCoordinator.current
    val currentContext by rememberUpdatedState(LocalOperationContext.current)
    val currentAllowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val adapter = remember(repository, coordinator) { coordinator?.let { NetworkTopologyAdapter(repository, it, { currentContext }, { currentAllowed }) } }
    val coroutineScope = rememberCoroutineScope()
    fun refresh() { metadata.refresh(); members.refresh(); history.refresh() }
    fun back() { if (page != "overview") page = "overview" else onBack() }
    val title = when (page) {
        "members" -> stringResource(R.string.topology_members)
        "topology" -> stringResource(R.string.topology_entries)
        "history" -> stringResource(R.string.topology_history)
        else -> metadata.state.data?.name ?: stringResource(R.string.network_private_networks)
    }
    val hosted = LocalNavigationChrome.current != null
    RegisterResourceChildNavigation(RootDestination.NETWORK, ResourceType.NETWORK, networkId.toString(), title, ::back)
    BackHandler(enabled = !hosted && draft == null && pendingId == null) { back() }
    HCloudPullToRefresh(metadata.state.refreshing || members.state.refreshing || history.state.refreshing,
        ::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState("$scopeKey:$page"),
            contentPadding = PaddingValues(HCloudDesignTokens.contentInset),
            verticalArrangement = Arrangement.spacedBy(HCloudDesignTokens.spaceMedium)) {
            if (!hosted) item { OutlinedButton(onClick = ::back) { Text(stringResource(R.string.common_back)) } }
            if (page == "overview") {
                item { HCloudReadSection(metadata) { network ->
                    Text(network.name, style = MaterialTheme.typography.titleLarge)
                    Text(stringResource(R.string.common_id, network.id))
                    Text(stringResource(R.string.network_cidr, network.ipRange))
                    ResourceProtectionControls(network.name, network.deleteProtected,
                        ProtectionAdapter(ProtectionResource.NETWORK, network.id, ProtectionField.DELETE, repository),
                        onCompleted = ::refresh, enabled = metadata.state.canOfferMutation, currentKnown = network.protectionKnown)
                } }
                item { HCloudResourceCard(ResourceCardModel("$scopeKey:members", stringResource(R.string.topology_members), "",
                    members.state.data?.size?.toString().orEmpty()), { page = "members" }) }
                item { HCloudResourceCard(ResourceCardModel("$scopeKey:topology", stringResource(R.string.topology_entries), "", ""), { page = "topology" }) }
                item { HCloudResourceCard(ResourceCardModel("$scopeKey:history", stringResource(R.string.topology_history), "", ""), { page = "history" }) }
                if (allowed && metadata.state.canOfferMutation) {
                    item { OutlinedButton(onClick = { draft = NetworkMutationDraft(NetworkMutationKind.ATTACH) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.network_attach_server))
                    } }
                    item { OutlinedButton(onClick = { draft = NetworkMutationDraft(NetworkMutationKind.DELETE) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.network_delete), color = MaterialTheme.colorScheme.error)
                    } }
                }
            }
            if (page == "members") item { HCloudReadSection(members) { rows ->
                rows.sortedWith(compareBy({ it.type }, { it.id })).forEach { member ->
                    Text(stringResource(R.string.network_detail_member, member.type, member.id, member.ip, member.subnet, member.status))
                    if (member.aliasIps.isNotEmpty()) Text(stringResource(R.string.network_detail_aliases, member.aliasIps.joinToString()))
                    if (allowed && member.type == "server" && metadata.state.canOfferMutation && members.state.canOfferMutation) {
                        OutlinedButton(onClick = { draft = NetworkMutationDraft(NetworkMutationKind.DETACH, serverId = member.id) }) {
                            Text(stringResource(R.string.network_detach_server))
                        }
                    }
                }
            } }
            if (page == "topology") item { HCloudReadSection(metadata) { network ->
                Text(stringResource(R.string.topology_subnets), style = MaterialTheme.typography.titleMedium)
                if (network.subnets.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.EMPTY))
                network.subnets.sortedBy { it.ipRange }.forEach { subnet ->
                    val summary = listOfNotNull(subnet.type, subnet.networkZone, subnet.gateway, subnet.vswitchId?.toString()).joinToString(" · ")
                    HCloudResourceCard(ResourceCardModel("$scopeKey:subnet:${subnet.ipRange}", subnet.ipRange, "", summary), {
                        if (allowed && metadata.state.canOfferMutation) draft = NetworkMutationDraft(NetworkMutationKind.DELETE_SUBNET,
                            subnet = PrivateNetworkSubnetSpec(subnet.ipRange, subnet.networkZone))
                        else inspect = "${subnet.ipRange}\n$summary"
                    })
                }
                if (allowed && metadata.state.canOfferMutation) OutlinedButton(onClick = { draft = NetworkMutationDraft(NetworkMutationKind.ADD_SUBNET) }) {
                    Text(stringResource(R.string.network_add_subnet))
                }
                Text(stringResource(R.string.topology_routes), style = MaterialTheme.typography.titleMedium)
                if (network.routes.isEmpty()) HCloudSectionState(SectionPresentation(SectionKind.EMPTY))
                network.routes.sortedBy { it.destination }.forEach { route ->
                    HCloudResourceCard(ResourceCardModel("$scopeKey:route:${route.destination}:${route.gateway}", route.destination, "", route.gateway), {
                        if (allowed && metadata.state.canOfferMutation) draft = NetworkMutationDraft(NetworkMutationKind.DELETE_ROUTE,
                            route = PrivateNetworkRouteSpec(route.destination, route.gateway))
                        else inspect = "${route.destination}\n${route.gateway}"
                    })
                }
                if (allowed && metadata.state.canOfferMutation) OutlinedButton(onClick = { draft = NetworkMutationDraft(NetworkMutationKind.ADD_ROUTE) }) {
                    Text(stringResource(R.string.network_add_route))
                }
                Text(stringResource(R.string.network_topology_warning))
            } }
            if (page == "history") item { HCloudReadSection(history) { rows ->
                rows.sortedByDescending { it.id }.forEach { action ->
                    Text("${action.id} · ${action.command ?: stringResource(R.string.network_actions_unknown)} · ${action.status}")
                    action.started?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            } }
            val pending = coordinator?.all().orEmpty().filter { it.isPending && it.intent.resource.context == owner &&
                it.intent.resource.resourceType == "network" && it.intent.resource.resourceId == networkId.toString() }
            pending.forEach { operation -> item(key = operation.intent.intentId) {
                OutlinedButton(onClick = { pendingId = operation.intent.intentId }) {
                    Text(stringResource(R.string.topology_pending, operation.intent.action))
                }
            } }
        }
    }
    draft?.let { selected ->
        val key = "$scopeKey:${selected.identity()}"
        drafts.SaveableStateProvider(key) {
            NetworkTopologyMutationFlow(networkId, selected, repository, ::refresh,
                onDismiss = {
                    val deleted = coordinator?.all()?.any { it.stage == OperationStage.SUCCEEDED && it.intent.resource.context == owner &&
                        it.intent.resource.resourceId == networkId.toString() && it.intent.action == NetworkMutationDraft(NetworkMutationKind.DELETE).identity() } == true
                    draft = null
                    if (deleted) onDeleted()
                }, onDiscardDraft = { drafts.removeState(key) })
        }
    }
    val pendingRecord = remember(pendingId, version, checking) { pendingId?.let { coordinator?.get(it) }?.takeIf {
        it.intent.resource.context == owner && it.intent.resource.resourceType == "network" && it.intent.resource.resourceId == networkId.toString()
    } }
    pendingRecord?.let { original ->
        OperationResultDialog(stringResource(R.string.topology_history), original, original.intent.resource.context.connectionId,
            networkId.toString(), {
                pendingId = null
                if (original.stage == OperationStage.SUCCEEDED && original.intent.action == NetworkMutationDraft(NetworkMutationKind.DELETE).identity()) onDeleted()
            }, checking, onCheckStatus = {
                checking = true; readFailed = false
                coroutineScope.launch {
                    try { withContext(Dispatchers.IO) { adapter?.reconcile(original) }; version++; refresh() }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { readFailed = true }
                    finally { checking = false }
                }
            }) { if (readFailed) Text(stringResource(R.string.ds_read_error), color = MaterialTheme.colorScheme.error) }
    }
    inspect?.let { text -> HCloudDialog(stringResource(R.string.topology_entries), stringResource(R.string.ds_close),
        { inspect = null }, { inspect = null }) { Text(text) } }
}
