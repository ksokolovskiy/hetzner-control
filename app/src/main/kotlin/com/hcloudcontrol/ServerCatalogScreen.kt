package com.hcloudcontrol

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.LocationDetails
import com.hcloudcontrol.core.domain.model.LocationOption
import com.hcloudcontrol.core.domain.model.ServerTypeDetails
import com.hcloudcontrol.core.domain.model.ServerTypeOption
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface ServerCatalogState {
    data object Loading : ServerCatalogState
    data class Content(val types: List<ServerTypeOption>, val locations: List<LocationOption>) : ServerCatalogState
    data class Error(val message: String) : ServerCatalogState
}
private sealed interface TypeDetailState { data object Loading : TypeDetailState; data class Content(val value: ServerTypeDetails) : TypeDetailState; data class Error(val message: String) : TypeDetailState }
private sealed interface LocationDetailState { data object Loading : LocationDetailState; data class Content(val value: LocationDetails) : LocationDetailState; data class Error(val message: String) : LocationDetailState }

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
    var refreshKey by remember(projectId) { mutableIntStateOf(0) }
    var state by remember(projectId) { mutableStateOf<ServerCatalogState>(ServerCatalogState.Loading) }
    var type by remember(projectId) { mutableStateOf<ServerTypeOption?>(null) }
    var location by remember(projectId) { mutableStateOf<LocationOption?>(null) }
    BackHandler(enabled = type != null || location != null) {
        // Detail is always one level below the catalog; never fall through to the app root.
        type = null
        location = null
    }
    val loadError = stringResource(R.string.server_catalog_load_failed)
    LaunchedEffect(projectId, refreshKey) {
        state = ServerCatalogState.Loading
        state = try {
            val values = withContext(Dispatchers.IO) { repository.getServerTypes() to repository.getLocations() }
            ServerCatalogState.Content(values.first, values.second)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (error: Throwable) { ServerCatalogState.Error(safeApiFailureMessage(error, loadError)) }
    }
    type?.let {
        ServerTypeDetailScreen(
            initial = it,
            repository = repository,
            locations = (state as? ServerCatalogState.Content)?.locations.orEmpty(),
            changesAllowed = changesAllowed,
            onConfigure = { onConfigureServer(it, null) },
            onOpenSettings = onOpenSettings,
            onBack = { type = null },
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
            onBack = { location = null },
        )
        return
    }
    when (val current = state) {
        ServerCatalogState.Loading -> LoadingView(stringResource(R.string.server_catalog_loading))
        is ServerCatalogState.Error -> StateView(stringResource(R.string.server_catalog_title), current.message, stringResource(R.string.common_retry)) { refreshKey++ }
        is ServerCatalogState.Content -> HCloudPullToRefresh(
            isRefreshing = false,
            onRefresh = { refreshKey++ },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(state = rememberRestorableLazyListState("server-catalog"), modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text(stringResource(R.string.server_catalog_title), style = MaterialTheme.typography.headlineMedium) }
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
            item { Text(stringResource(R.string.server_catalog_types), style = MaterialTheme.typography.titleLarge) }
            items(current.types.sortedWith(compareBy<ServerTypeOption> { it.cores }.thenBy { it.memoryGb }.thenBy { it.diskGb }.thenBy { it.name }), key = { "t${it.id}" }) { item -> CatalogCard(item.name, "${item.cores} vCPU · ${item.memoryGb} GB · ${item.diskGb} GB", { type = item }) }
            item { Text(stringResource(R.string.server_catalog_locations), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp)) }
            items(current.locations.sortedBy { it.name }, key = { "l${it.id}" }) { item -> CatalogCard(item.displayName(), listOfNotNull(item.country).joinToString(" · "), { location = item }) }
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
}

@Composable private fun CatalogCard(title: String, subtitle: String, onClick: () -> Unit) = Card(Modifier.fillMaxWidth().clickable(onClick = onClick), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) { Text(title, style = MaterialTheme.typography.titleMedium); if (subtitle.isNotBlank()) Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.server_catalog_open), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
}

@Composable private fun ServerTypeDetailScreen(
    initial: ServerTypeOption,
    repository: HCloudRepository,
    locations: List<LocationOption>,
    changesAllowed: Boolean,
    onConfigure: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    val locationLabels = remember(locations) { locations.associate { it.name to it.displayName() } }
    var retry by remember(initial.id) { mutableIntStateOf(0) }; var state by remember(initial.id) { mutableStateOf<TypeDetailState>(TypeDetailState.Loading) }; val failure = stringResource(R.string.server_catalog_type_failed)
    LaunchedEffect(initial.id, retry) { state = TypeDetailState.Loading; state = try { TypeDetailState.Content(withContext(Dispatchers.IO) { repository.getServerType(initial.id) }) } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) { TypeDetailState.Error(safeApiFailureMessage(error, failure)) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }; when (val current = state) { TypeDetailState.Loading -> item { Text(stringResource(R.string.server_catalog_loading)) }; is TypeDetailState.Error -> item { Column { Text(current.message); OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.common_retry)) } } }; is TypeDetailState.Content -> item { val value = current.value; Text(value.name, style = MaterialTheme.typography.headlineMedium); Text(value.description); Text(stringResource(R.string.server_catalog_type_metadata, value.cores, value.memoryGb, value.diskGb)); value.category?.let { Text(stringResource(R.string.server_catalog_category, it)) }; value.architecture?.let { Text(stringResource(R.string.server_catalog_architecture, it)) }; Text(stringResource(R.string.server_catalog_availability), style = MaterialTheme.typography.titleMedium); value.locations.sortedBy { it.name }.forEach { availability -> Text(stringResource(if (availability.available == true) R.string.server_catalog_available else R.string.server_catalog_unavailable, locationLabels[availability.name] ?: availability.name)) }; Text(stringResource(R.string.server_catalog_prices), style = MaterialTheme.typography.titleMedium); if (value.prices.isEmpty()) Text(stringResource(R.string.server_catalog_prices_empty)); value.prices.sortedBy { it.location }.forEach { price -> Text(stringResource(R.string.server_catalog_price, locationLabels[price.location] ?: price.location, formatApiMoney(price.hourly.gross), formatApiMoney(price.monthly.gross), price.currency)) }; CatalogConfigureAction(changesAllowed, onConfigure, onOpenSettings) } } }
}

@Composable private fun LocationDetailScreen(
    initial: LocationOption,
    repository: HCloudRepository,
    changesAllowed: Boolean,
    onConfigure: () -> Unit,
    onOpenSettings: () -> Unit,
    onBack: () -> Unit,
) {
    var retry by remember(initial.id) { mutableIntStateOf(0) }; var state by remember(initial.id) { mutableStateOf<LocationDetailState>(LocationDetailState.Loading) }; val failure = stringResource(R.string.server_catalog_location_failed)
    LaunchedEffect(initial.id, retry) { state = LocationDetailState.Loading; state = try { LocationDetailState.Content(withContext(Dispatchers.IO) { repository.getLocation(initial.id) }) } catch (cancelled: CancellationException) { throw cancelled } catch (error: Throwable) { LocationDetailState.Error(safeApiFailureMessage(error, failure)) } }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) { item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }; when (val current = state) { LocationDetailState.Loading -> item { Text(stringResource(R.string.server_catalog_loading)) }; is LocationDetailState.Error -> item { Column { Text(current.message); OutlinedButton(onClick = { retry++ }) { Text(stringResource(R.string.common_retry)) } } }; is LocationDetailState.Content -> item { val value = current.value; Text(value.displayName(), style = MaterialTheme.typography.headlineMedium); Text(value.description); value.city?.let { Text(stringResource(R.string.server_catalog_city, it)) }; value.country?.let { Text(stringResource(R.string.server_catalog_country, it)) }; value.networkZone?.let { Text(stringResource(R.string.server_catalog_network_zone, it)) }; CatalogConfigureAction(changesAllowed, onConfigure, onOpenSettings) } } }
}

@Composable
private fun CatalogConfigureAction(changesAllowed: Boolean, onConfigure: () -> Unit, onOpenSettings: () -> Unit) {
    if (changesAllowed) {
        Button(onClick = onConfigure, modifier = Modifier.fillMaxWidth()) {
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
