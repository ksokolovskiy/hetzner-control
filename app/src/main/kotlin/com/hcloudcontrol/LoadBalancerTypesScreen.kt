package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.LoadBalancerTypeInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.ChoiceItem
import com.hcloudcontrol.ui.designsystem.HCloudResourceCard
import com.hcloudcontrol.ui.designsystem.HCloudSegmentedControl
import com.hcloudcontrol.ui.designsystem.ResourceCardModel
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import com.hcloudcontrol.core.domain.repository.BillingRateCardRepository
import com.hcloudcontrol.core.domain.model.BillingRateCard
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Read-only Cloud catalog. It deliberately has no create or change-type controls. */
@Composable
internal fun LoadBalancerTypesScreen(repository: HCloudRepository, projectId: String,
    billingRepository: BillingRateCardRepository? = null, onBack: () -> Unit) {
    val scope = "load-balancer-types:$projectId:${LocalNavigationContext.current?.endpoint.orEmpty()}"
    val loader = rememberSectionLoader(scope, isEmpty = { it: List<LoadBalancerTypeInventory> -> it.isEmpty() }) { repository.getLoadBalancerTypes() }
    var query by rememberSaveable(scope) { mutableStateOf("") }
    var selectedId by rememberContextualResourceId(scope)
    val hosted = LocalNavigationChrome.current != null
    RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.LOAD_BALANCER_TYPE,
        selectedId?.toString(), loader.state.data?.find { it.id == selectedId }?.name ?: stringResource(R.string.load_balancer_types_title)) {
        if (selectedId != null) selectedId = null else onBack()
    }
    BackHandler(enabled = !hosted && selectedId != null) { selectedId = null }
    selectedId?.let { id ->
        LoadBalancerTypeDetailScreen(id, scope, repository, billingRepository) { selectedId = null }
        return
    }
    HCloudPullToRefresh(
        isRefreshing = loader.state.refreshing,
        onRefresh = loader::refresh,
        modifier = Modifier.fillMaxSize(),
    ) {
    LazyColumn(
        state = rememberRestorableLazyListState(scope, contentReady = loader.state.data != null),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (!hosted) item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item {
            if (!hosted) Text(stringResource(R.string.load_balancer_types_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.load_balancer_types_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item { HCloudFormField(query, { query = it }, stringResource(R.string.ds_search)) }
        item { HCloudReadSection(loader) {} }
        val visible = loader.state.data.orEmpty().filter { query.isBlank() || it.name.contains(query.trim(), true) || it.description.orEmpty().contains(query.trim(), true) }
        if (loader.state.hasSuccessfulResponse && loader.state.data.orEmpty().isNotEmpty() && visible.isEmpty()) item {
            HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS))
        }
                items(visible.sortedWith(compareBy<LoadBalancerTypeInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { type ->
                    val limitLines = loadBalancerTypeLimits(type).take(3).map { (label, value) ->
                        "${stringResource(label)}: ${value?.toString() ?: stringResource(R.string.ds_cost_unknown)}"
                    }
                    HCloudResourceCard(
                        ResourceCardModel("$projectId:load-balancer-type:${type.id}", type.name,
                            stringResource(R.string.load_balancer_types_open), (listOfNotNull(type.description) + limitLines).joinToString("\n")),
                        onClick = { selectedId = type.id },
                    )
                }
    }
    }
}

@Composable
private fun LoadBalancerTypeDetailScreen(id: Long, scope: String, repository: HCloudRepository,
    billingRepository: BillingRateCardRepository?, onBack: () -> Unit) {
    val hosted = LocalNavigationChrome.current != null
    val metadata = rememberSectionLoader("$scope:$id") { repository.getLoadBalancerType(id) }
    val pricing = rememberSectionLoader("$scope:$id:billing") { billingRepository?.getRateCard() }
    val locations = rememberSectionLoader("$scope:$id:locations") { repository.getLocations() }
    HCloudPullToRefresh(metadata.state.refreshing || pricing.state.refreshing || locations.state.refreshing,
        { metadata.refresh(); pricing.refresh(); locations.refresh() }, Modifier.fillMaxSize()) {
    LazyColumn(state = rememberRestorableLazyListState("$scope:$id", contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hosted) item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item { HCloudReadSection(metadata) { type -> LoadBalancerTypeDetails(type, pricing.state.data,
            metadata.state.updatedAtMillis?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) }.orEmpty(),
            locations.state.data.orEmpty().associate { it.name to it.displayName() }) } }
        item {
            Text(stringResource(R.string.load_balancer_types_prices), style = MaterialTheme.typography.titleMedium)
            HCloudReadSection(pricing) {}
        }
        item {
            Text(stringResource(R.string.server_catalog_locations), style = MaterialTheme.typography.titleMedium)
            HCloudReadSection(locations) {}
        }
    }
    }
}

@Composable
private fun LoadBalancerTypeDetails(type: LoadBalancerTypeInventory, billing: BillingRateCard?, retrievedAt: String,
    locationNames: Map<String, String>) = Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
    var section by rememberSaveable(type.id) { mutableStateOf("limits") }
    if (LocalNavigationChrome.current == null) Text(type.name, style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.common_id, type.id))
    type.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    HCloudSegmentedControl(
        items = listOf(ChoiceItem("limits", stringResource(R.string.load_balancer_types_limits)), ChoiceItem("prices", stringResource(R.string.load_balancer_types_prices))),
        selectedId = section,
        onSelected = { section = it },
    )
    if (section == "limits") {
    val limits = loadBalancerTypeLimits(type)
    limits.forEach { (label, value) -> Text(if (value != null) stringResource(R.string.load_balancer_types_limit_value, stringResource(label), value)
        else "${stringResource(label)}: ${stringResource(R.string.ds_cost_unknown)}") }
    } else {
    Text(stringResource(R.string.load_balancer_types_prices_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (type.prices.isEmpty()) Text(stringResource(R.string.load_balancer_types_prices_empty))
    type.prices.sortedBy { it.location }.forEach { price ->
        HCloudCostPresentation(CostPresentation.Available(CostSnapshot(type.name, locationNames[price.location] ?: price.location,
            billing?.currency.orEmpty(), price.hourly.gross, price.monthly.gross, billing?.vatRate, retrievedAt)))
    }
    }
    Text(stringResource(R.string.load_balancer_types_read_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** One provider-limit mapping serves both catalogue comparison and canonical detail. */
internal fun loadBalancerTypeLimits(type: LoadBalancerTypeInventory): List<Pair<Int, Int?>> = listOf(
        R.string.load_balancer_types_limit_services to type.maxServices,
        R.string.load_balancer_types_limit_connections to type.maxConnections,
        R.string.load_balancer_types_limit_targets to type.maxTargets,
        R.string.load_balancer_types_limit_assignments to type.maxAssignments,
        R.string.load_balancer_types_limit_open_requests to type.maxOpenRequests,
        R.string.load_balancer_types_limit_routing_rules to type.maxRoutingRules,
        R.string.load_balancer_types_limit_tcp_connections to type.maxTcpConnections,
        R.string.load_balancer_types_limit_http_requests to type.maxHttpRequestsPerSecond,
        R.string.load_balancer_types_limit_requests to type.maxRequestsPerSecond,
        R.string.load_balancer_types_limit_ssl_certificates to type.maxSslCertificates,
    )
