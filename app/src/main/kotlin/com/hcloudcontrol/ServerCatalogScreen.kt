package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.ServerTypeOption
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.ui.designsystem.ChoiceItem
import com.hcloudcontrol.ui.designsystem.HCloudTabs
import com.hcloudcontrol.ui.designsystem.HCloudFilterChip
import com.hcloudcontrol.ui.designsystem.HCloudResourceCard
import com.hcloudcontrol.ui.designsystem.ResourceCardModel
import com.hcloudcontrol.ui.designsystem.HCloudSectionState
import com.hcloudcontrol.ui.designsystem.SectionKind
import com.hcloudcontrol.ui.designsystem.SectionPresentation
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import com.hcloudcontrol.ui.state.SectionLoader
import com.hcloudcontrol.ui.designsystem.HCloudReadSection
import com.hcloudcontrol.ui.designsystem.HCloudFormField
import com.hcloudcontrol.operations.LocalOperationContext

private data class CatalogInventory(val types: List<ServerTypeOption>, val locations: List<LocationOption>)

/** A discovery surface that hands server choices to the one existing provisioning form. */
@Composable
internal fun ServerCatalogScreen(
    repository: HCloudRepository,
    projectId: String,
    onConfigureServer: (ServerTypeOption?, LocationOption?) -> Unit,
    onOpenStorage: () -> Unit,
    onOpenNetwork: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val changesAllowed = LocalHighImpactOperations.current.isAllowed
    val catalogScope = "$projectId:${LocalNavigationContext.current?.endpoint}:${LocalOperationContext.current}:catalog"
    val types = rememberSectionLoader("$catalogScope:types", isEmpty = { it: List<ServerTypeOption> -> it.isEmpty() }) { repository.getServerTypes() }
    val locations = rememberSectionLoader("$catalogScope:locations", isEmpty = { it: List<LocationOption> -> it.isEmpty() }) { repository.getLocations() }
    var typeId by rememberContextualResourceId("$catalogScope:type")
    var locationId by rememberContextualResourceId("$catalogScope:location")
    val type = types.state.data?.firstOrNull { it.id == typeId }
    val location = locations.state.data?.firstOrNull { it.id == locationId }
    var catalogTab by rememberSaveable(catalogScope) { mutableStateOf("types") }
    var coreFilter by rememberSaveable(catalogScope) { mutableStateOf<Int?>(null) }
    var query by rememberSaveable(catalogScope) { mutableStateOf("") }
    val hosted = LocalNavigationChrome.current != null
    typeId?.let { id -> RegisterResourceChildNavigation(RootDestination.CREATE, ResourceType.SERVER_TYPE, id.toString(), type?.name ?: stringResource(R.string.server_catalog_title)) { typeId = null } }
    locationId?.let { id -> RegisterResourceChildNavigation(RootDestination.CREATE, ResourceType.LOCATION, id.toString(), location?.displayName() ?: stringResource(R.string.server_catalog_title)) { locationId = null } }
    BackHandler(enabled = !hosted && (typeId != null || locationId != null)) {
        // Detail is always one level below the catalog; never fall through to the app root.
        typeId = null
        locationId = null
    }
    type?.let {
        ServerTypeDetailScreen(
            initial = it,
            repository = repository,
            locations = locations,
            changesAllowed = changesAllowed,
            onConfigure = onConfigureServer,
            onOpenSettings = onOpenSettings,
            onBack = { typeId = null },
        )
        return
    }
    location?.let {
        LocationDetailScreen(
            initial = it,
            repository = repository,
            changesAllowed = changesAllowed,
            onConfigure = { onConfigureServer(null, it) },
            onOpenSettings = onOpenSettings,
            onBack = { locationId = null },
        )
        return
    }
    if (typeId != null || locationId != null) {
        Column { if (typeId != null) HCloudReadSection(types) { HCloudSectionState(SectionPresentation(SectionKind.MISSING)) }
            else HCloudReadSection(locations) { HCloudSectionState(SectionPresentation(SectionKind.MISSING)) } }
        return
    }
    val current = CatalogInventory(types.state.data.orEmpty(), locations.state.data.orEmpty())
    val visibleTypes = current.types.filter { catalogueMatches(query, it.name, it.description) && (coreFilter == null || it.cores == coreFilter) }
    val visibleLocations = current.locations.filter { catalogueMatches(query, it.name, it.displayName(), it.country.orEmpty()) }
    HCloudPullToRefresh(
            isRefreshing = if (catalogTab == "types") types.state.refreshing else locations.state.refreshing,
            onRefresh = { types.refresh(); locations.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = rememberRestorableLazyListState("$catalogScope:$catalogTab", contentReady = if (catalogTab == "types") types.state.data != null else locations.state.data != null), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (!hosted) item { Text(stringResource(R.string.server_catalog_title), style = MaterialTheme.typography.headlineMedium) }
            item { Text(stringResource(R.string.server_catalog_description), color = MaterialTheme.colorScheme.onSurfaceVariant) }
            item {
                if (changesAllowed) {
                    Button(onClick = { onConfigureServer(null, null) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.server_catalog_configure_server))
                    }
                } else {
                    CatalogChangesDisabled(onOpenSettings)
                }
            }
            item {
                HCloudTabs(
                    items = listOf(ChoiceItem("types", stringResource(R.string.server_catalog_types)), ChoiceItem("locations", stringResource(R.string.server_catalog_locations))),
                    selectedId = catalogTab,
                    onSelected = { catalogTab = it },
                )
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
            }
            if (catalogTab == "types") {
                if (coreFilter != null) item {
                    OutlinedButton(onClick = { coreFilter = null }) { Text(stringResource(R.string.provision_clear_filters)) }
                }
                item { HCloudReadSection(types) {} }
                if (current.types.isNotEmpty() && visibleTypes.isEmpty()) {
                    item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
                }
                item {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        current.types.map { it.cores }.distinct().sorted().forEach { cores ->
                            HCloudFilterChip(stringResource(R.string.provision_filter_cpu_active, cores), coreFilter == cores,
                                onClick = { coreFilter = cores.takeUnless { coreFilter == cores } })
                        }
                    }
                }
                items(visibleTypes.sortedWith(compareBy<ServerTypeOption> { it.cores }.thenBy { it.memoryGb }.thenBy { it.diskGb }.thenBy { it.name }), key = { "t${it.id}" }) { item ->
                    HCloudResourceCard(ResourceCardModel("$projectId:server-type:${item.id}", item.name, stringResource(R.string.server_catalog_open), stringResource(R.string.server_catalog_type_metadata, item.cores, item.memoryGb, item.diskGb)), onClick = { typeId = item.id })
                }
            } else {
                item { HCloudReadSection(locations) {} }
                if (current.locations.isNotEmpty() && visibleLocations.isEmpty()) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
                items(visibleLocations.sortedBy { it.name }, key = { "l${it.id}" }) { item ->
                    HCloudResourceCard(ResourceCardModel("$projectId:location:${item.id}", item.displayName(), stringResource(R.string.server_catalog_open), listOfNotNull(item.country).joinToString(" · ")), onClick = { locationId = item.id })
                }
            }
            item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(stringResource(R.string.server_catalog_other_resources), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.server_catalog_other_resources_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = onOpenStorage, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.server_catalog_open_storage))
                        }
                        OutlinedButton(onClick = onOpenNetwork, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.server_catalog_open_network))
                        }
                    }
                }
            }
            }
        }
}

@Composable private fun ServerTypeDetailScreen(
    initial: ServerTypeOption,
    repository: HCloudRepository,
    locations: SectionLoader<List<LocationOption>>,
    changesAllowed: Boolean,
    onConfigure: (ServerTypeOption?, LocationOption?) -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = "${LocalOperationContext.current}:catalog:type:${initial.id}"
    // Metadata remains usable when the independently fetched pricing response fails.
    val metadata = rememberSectionLoader(scope + ":metadata", isEmpty = { it: List<ServerTypeOption> -> it.isEmpty() }) {
        repository.getServerTypes().filter { it.id == initial.id }
    }
    val prices = rememberSectionLoader(scope + ":pricing") { repository.getServerType(initial.id) }
    val locationLabels = locations.state.data.orEmpty().associate { it.name to it.displayName() }
    HCloudPullToRefresh(metadata.state.refreshing || prices.state.refreshing || locations.state.refreshing, { metadata.refresh(); prices.refresh(); locations.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scope, contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { if (LocalNavigationChrome.current == null) OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item {
                HCloudReadSection(metadata) { values -> values.firstOrNull()?.let { value ->
                    if (LocalNavigationChrome.current == null) Text(value.name, style = MaterialTheme.typography.headlineMedium)
                    Text(value.description)
                    Text(stringResource(R.string.server_catalog_type_metadata, value.cores, value.memoryGb, value.diskGb))
                    value.category?.let { Text(stringResource(R.string.server_catalog_category, it)) }
                    value.architecture?.let { Text(stringResource(R.string.server_catalog_architecture, it)) }
                    Text(stringResource(R.string.server_catalog_availability), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.server_catalog_capacity_caveat), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    value.availableLocations.sorted().forEach { code ->
                        Text(stringResource(R.string.server_catalog_available, locationLabels[code] ?: code))
                        locations.state.data?.firstOrNull { it.name == code }?.let { location ->
                            if (changesAllowed) OutlinedButton(onClick = { onConfigure(value, location) },
                                enabled = metadata.state.canOfferMutation && locations.state.canOfferMutation) {
                                Text(stringResource(R.string.server_catalog_configure_server) + " · " + location.displayName())
                            }
                        }
                    }
                    CatalogConfigureAction(changesAllowed, { onConfigure(value, null) }, onOpenSettings, metadata.state.canOfferMutation)
                } }
            }
            item { HCloudReadSection(locations) {} }
            item {
                Text(stringResource(R.string.server_catalog_prices), style = MaterialTheme.typography.titleMedium)
                HCloudReadSection(prices) { value ->
                    if (value.prices.isEmpty()) Text(stringResource(R.string.server_catalog_prices_empty))
                    value.prices.sortedBy { it.location }.forEach { price ->
                        Text(stringResource(R.string.server_catalog_price, locationLabels[price.location] ?: price.location,
                            formatApiMoney(price.hourly.gross), formatApiMoney(price.monthly.gross), price.currency))
                    }
                }
            }
        }
    }
}

@Composable private fun LocationDetailScreen(
    initial: LocationOption,
    repository: HCloudRepository,
    changesAllowed: Boolean,
    onConfigure: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val scope = "${LocalOperationContext.current}:catalog:location:${initial.id}"
    val detail = rememberSectionLoader(scope) { repository.getLocation(initial.id) }
    HCloudPullToRefresh(detail.state.refreshing, detail::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState(scope, contentReady = detail.state.data != null), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { if (LocalNavigationChrome.current == null) OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item { HCloudReadSection(detail) { value ->
                if (LocalNavigationChrome.current == null) Text(value.displayName(), style = MaterialTheme.typography.headlineMedium)
                Text(value.description)
                value.city?.let { Text(stringResource(R.string.server_catalog_city, it)) }
                value.country?.let { Text(stringResource(R.string.server_catalog_country, it)) }
                value.networkZone?.let { Text(stringResource(R.string.server_catalog_network_zone, it)) }
                CatalogConfigureAction(changesAllowed, onConfigure, onOpenSettings, detail.state.canOfferMutation)
            } }
        }
    }
}

@Composable
private fun CatalogConfigureAction(changesAllowed: Boolean, onConfigure: () -> Unit, onOpenSettings: () -> Unit, fresh: Boolean = true) {
    if (changesAllowed) {
        Button(onClick = onConfigure, enabled = fresh, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.server_catalog_configure_server))
        }
    } else {
        CatalogChangesDisabled(onOpenSettings)
    }
}

@Composable
private fun CatalogChangesDisabled(onOpenSettings: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.server_catalog_changes_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = onOpenSettings) { Text(stringResource(R.string.server_catalog_open_settings)) }
        }
    }
}
