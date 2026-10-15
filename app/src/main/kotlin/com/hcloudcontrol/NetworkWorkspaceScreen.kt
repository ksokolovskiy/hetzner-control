package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.*

/** Independent inventories survive sibling read failure; aliases reuse their owning detail. */
@Composable
internal fun NetworkWorkspaceScreen(repository: HCloudRepository, projectId: String,
    onOpenLoadBalancers: () -> Unit, onOpenPlacementGroups: () -> Unit) {
    val context = LocalNavigationContext.current ?: return
    val scope = "$projectId:${context.endpoint}"
    var navigation by rememberCanonicalNavigation(RootDestination.NETWORK)
    var query by rememberContextualText("$scope:network-overview:query")
    val primary = rememberSectionLoader("$scope:primary-ips") { repository.getPrimaryIps() }
    val floating = rememberSectionLoader("$scope:floating-ips") { repository.getFloatingIps() }
    val networks = rememberSectionLoader("$scope:networks") { repository.getPrivateNetworks() }
    val firewalls = rememberSectionLoader("$scope:firewalls") { repository.getFirewalls() }
    val balancers = rememberSectionLoader("$scope:load-balancers") { repository.getLoadBalancers() }
    val groups = rememberSectionLoader("$scope:placement-groups") { repository.getPlacementGroups() }
    fun refresh() { primary.refresh(); floating.refresh(); networks.refresh(); firewalls.refresh(); balancers.refresh(); groups.refresh() }
    fun open(type: ResourceType) { navigation = navigation.open(CanonicalDestination(RootDestination.NETWORK, DestinationKind.LIST, context, type)) }
    LaunchedEffect(scope) {
        if (navigation.current.context != null && navigation.current.context != context) navigation = navigation.switchCloudContext(context)
    }
    val route = navigation.current
    if (route.kind != DestinationKind.ROOT) {
        val title = stringResource(when (route.resourceType) {
            ResourceType.PUBLIC_IPS -> R.string.network_public_ips
            ResourceType.NETWORK -> R.string.network_private_networks
            else -> R.string.network_firewalls
        })
        RegisterChildNavigation(route, title) { navigation = navigation.up() }
        when (route.resourceType) {
            ResourceType.PUBLIC_IPS -> PublicIpInventoryScreen(repository, scope, primary, floating)
            ResourceType.NETWORK -> PrivateNetworkInventory(repository, scope, networks)
            ResourceType.FIREWALL -> FirewallInventoryScreen(repository, projectId, onBack = { navigation = navigation.up() })
            else -> Unit
        }
        return
    }
    val categoryTitles = listOf(R.string.network_public_ips, R.string.network_private_networks,
        R.string.network_firewalls, R.string.network_load_balancers, R.string.network_placement_groups).map { stringResource(it) }
    val noResults = !matchesNetworkCategory(query, categoryTitles)
    HCloudPullToRefresh(isRefreshing = listOf(primary.state.refreshing, floating.state.refreshing, networks.state.refreshing,
        firewalls.state.refreshing, balancers.state.refreshing, groups.state.refreshing).any { it }, onRefresh = ::refresh) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberRestorableLazyListState("network-overview"),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { HCloudFormField(query, { query = it }, stringResource(R.string.network_search_categories)) }
            if (noResults) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
            item {
                val title = stringResource(R.string.network_public_ips)
                if (query.isBlank() || title.contains(query, true)) {
                    HCloudResourceCard(ResourceCardModel("$scope:public-ips", title,
                        stringResource(R.string.network_public_counts, primary.state.data?.size?.toString() ?: "—", floating.state.data?.size?.toString() ?: "—"), ""), { open(ResourceType.PUBLIC_IPS) })
                    if (!primary.state.hasSuccessfulResponse || primary.state.isStale) HCloudReadSection(primary) {}
                    if (!floating.state.hasSuccessfulResponse || floating.state.isStale) HCloudReadSection(floating) {}
                }
            }
            item { NetworkCategory(networks, stringResource(R.string.network_private_networks), query, "$scope:networks") { open(ResourceType.NETWORK) } }
            item { NetworkCategory(firewalls, stringResource(R.string.network_firewalls), query, "$scope:firewalls") { open(ResourceType.FIREWALL) } }
            item { NetworkCategory(balancers, stringResource(R.string.network_load_balancers), query, "$scope:balancers", onOpenLoadBalancers) }
            item { NetworkCategory(groups, stringResource(R.string.network_placement_groups), query, "$scope:groups", onOpenPlacementGroups) }
        }
    }
}

internal fun matchesNetworkCategory(query: String, titles: List<String>): Boolean =
    query.isBlank() || titles.any { it.contains(query, ignoreCase = true) }

@Composable
private fun <T> NetworkCategory(loader: SectionLoader<List<T>>, title: String, query: String, identity: String, onOpen: () -> Unit) {
    if (query.isNotBlank() && !title.contains(query, true)) return
    HCloudResourceCard(ResourceCardModel(identity, title,
        loader.state.data?.size?.let { stringResource(R.string.network_resource_count, it) } ?: stringResource(R.string.ds_loading), ""), onOpen)
    if (!loader.state.hasSuccessfulResponse || loader.state.isStale) HCloudReadSection(loader) {}
}

@Composable
private fun PublicIpInventoryScreen(repository: HCloudRepository, scope: String,
    primary: SectionLoader<List<PrimaryIpInventory>>, floating: SectionLoader<List<FloatingIpInventory>>) {
    var tab by rememberSaveable(scope) { mutableStateOf("primary") }
    var primaryQuery by rememberContextualText(publicIpQueryScope(scope, true))
    var floatingQuery by rememberContextualText(publicIpQueryScope(scope, false))
    val query = if (tab == "primary") primaryQuery else floatingQuery
    var primaryId by rememberContextualResourceId("$scope:primary-ip")
    var floatingId by rememberContextualResourceId("$scope:floating-ip")
    var creating by rememberContextualFlag("$scope:create-primary-ip")
    val servers = rememberSectionLoader("$scope:public-ip-servers") { repository.getServers() }
    val allowed = LocalHighImpactOperations.current.isAllowed
    primaryId?.let { id ->
        PublicIpDetailScreen(repository, PublicIpKind.PRIMARY, id, scope,
            onBack = { primaryId = null }, onChanged = { primary.refresh() },
            onDeleted = { primaryId = null; primary.refresh() })
        return
    }
    floatingId?.let { id ->
        PublicIpDetailScreen(repository, PublicIpKind.FLOATING, id, scope,
            onBack = { floatingId = null }, onChanged = { floating.refresh() },
            onDeleted = { floatingId = null; floating.refresh() })
        return
    }
    val isPrimary = tab == "primary"
    val state = if (isPrimary) primary.state else floating.state
    HCloudPullToRefresh(state.refreshing, { if (isPrimary) primary.refresh() else floating.refresh() }) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberRestorableLazyListState("public-ips:$tab", state.hasSuccessfulResponse),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { HCloudSegmentedControl(listOf(ChoiceItem("primary", "Primary IP"), ChoiceItem("floating", "Floating IP")), tab, { tab = it }) }
            item { HCloudFormField(query, { if (tab == "primary") primaryQuery = it else floatingQuery = it }, stringResource(R.string.network_search_public_ips)) }
            if (isPrimary && allowed) item { Button(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.primary_ip_create)) } }
            item { Text(stringResource(R.string.network_floating_scope_note), style = MaterialTheme.typography.bodySmall) }
            if (!state.hasSuccessfulResponse || state.isStale || state.refreshing) item {
                if (isPrimary) HCloudReadSection(primary) {} else HCloudReadSection(floating) {}
            }
            val rows = if (isPrimary) primary.state.data.orEmpty().map { ip -> PublicIpRow(ip.id, ip.name, ip.address, ip.assigneeId) }
                else floating.state.data.orEmpty().map { ip -> PublicIpRow(ip.id, ip.name.orEmpty(), ip.address, ip.assigneeId) }
            val matching = rows.filter { row -> query.isBlank() || listOf(row.name, row.address,
                row.serverId?.let { id -> servers.state.data?.firstOrNull { it.id == id }?.name ?: "#$id" }.orEmpty()).any { it.contains(query, true) } }
                .sortedWith(compareBy<PublicIpRow> { it.name.lowercase() }.thenBy { it.id })
            if (state.hasSuccessfulResponse && matching.isEmpty()) item {
                HCloudSectionState(SectionPresentation(if (rows.isEmpty()) SectionKind.EMPTY else SectionKind.NO_RESULTS))
            }
            items(matching, key = { it.id }) { ip -> HCloudResourceCard(ResourceCardModel("$scope:$tab:${ip.id}", ip.name.ifBlank { ip.address }, ip.address,
                ip.serverId?.let { id -> servers.state.data?.firstOrNull { it.id == id }?.name ?: "#$id" } ?: stringResource(R.string.primary_ip_unassigned)),
                { if (isPrimary) primaryId = ip.id else floatingId = ip.id }) }
        }
    }
    val draftHolder = rememberSaveableStateHolder()
    if (creating) draftHolder.SaveableStateProvider("create-primary:$scope") {
        PrimaryIpCreationFlow(repository, onDismiss = { creating = false },
            onCreatedId = { id -> creating = false; draftHolder.removeState("create-primary:$scope"); primary.refresh(); primaryId = id },
            onCreated = { primary.refresh() })
    }
}

private data class PublicIpRow(val id: Long, val name: String, val address: String, val serverId: Long?)
internal fun publicIpQueryScope(scope: String, primary: Boolean): String = "$scope:${if (primary) "primary" else "floating"}-query"

/** Lists only; detail ownership stays in the existing canonical firewall/private-network screens. */
@Composable
private fun PrivateNetworkInventory(repository: HCloudRepository, scope: String,
    networks: SectionLoader<List<PrivateNetworkInventory>>) {
    var selectedId by rememberContextualResourceId("$scope:network")
    var creating by rememberContextualFlag("$scope:network:create")
    val drafts = rememberSaveableStateHolder()
    val draftKey = "create-network:$scope"
    var query by rememberSaveable(scope) { mutableStateOf("") }
    selectedId?.let { id ->
        PrivateNetworkDetailScreen(id, repository, onBack = { selectedId = null }, onDeleted = { selectedId = null; networks.refresh() })
        return
    }
    val state = networks.state
    val allowed = LocalHighImpactOperations.current.isAllowed
    val rows = networks.state.data.orEmpty().map { Triple(it.id, it.name, it.ipRange) }
    val matching = rows.filter { query.isBlank() || it.second.contains(query, true) || it.third.contains(query, true) }.sortedBy { it.second.lowercase() }
    HCloudPullToRefresh(state.refreshing, { networks.refresh() }) {
        LazyColumn(Modifier.fillMaxSize(), state = rememberRestorableLazyListState("private-networks", state.hasSuccessfulResponse),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { HCloudFormField(query, { query = it }, stringResource(R.string.ds_search)) }
            if (allowed) item { Button(onClick = { creating = true }) { Text(stringResource(R.string.network_create)) } }
            if (!state.hasSuccessfulResponse || state.isStale || state.refreshing) item { HCloudReadSection(networks) {} }
            if (state.hasSuccessfulResponse && matching.isEmpty()) item { HCloudSectionState(SectionPresentation(if (rows.isEmpty()) SectionKind.EMPTY else SectionKind.NO_RESULTS)) }
            items(matching, key = { it.first }) { row -> HCloudResourceCard(ResourceCardModel("$scope:${row.first}", row.second, row.third, ""), { selectedId = row.first }) }
        }
    }
    if (creating) drafts.SaveableStateProvider(draftKey) {
        PrivateNetworkCreationFlow(repository, onDismiss = { creating = false },
            onCreatedId = { id -> creating = false; drafts.removeState(draftKey); networks.refresh(); selectedId = id },
            onDiscardDraft = { drafts.removeState(draftKey) })
    }
}
