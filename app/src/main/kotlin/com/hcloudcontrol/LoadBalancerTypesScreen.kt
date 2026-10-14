package com.hcloudcontrol

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
import com.hcloudcontrol.core.domain.model.LoadBalancerTypeInventory
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private sealed interface LoadBalancerTypesState {
    data object Loading : LoadBalancerTypesState
    data class Content(val types: List<LoadBalancerTypeInventory>) : LoadBalancerTypesState
    data object Error : LoadBalancerTypesState
}

private sealed interface LoadBalancerTypeDetailState {
    data object Loading : LoadBalancerTypeDetailState
    data class Content(val type: LoadBalancerTypeInventory) : LoadBalancerTypeDetailState
    data object Error : LoadBalancerTypeDetailState
}

/** Read-only Cloud catalog. It deliberately has no create or change-type controls. */
@Composable
internal fun LoadBalancerTypesScreen(repository: HCloudRepository, projectId: String, onBack: () -> Unit) {
    var refreshKey by remember(projectId) { mutableIntStateOf(0) }
    var state by remember(projectId) { mutableStateOf<LoadBalancerTypesState>(LoadBalancerTypesState.Loading) }
    var selected by remember(projectId) { mutableStateOf<LoadBalancerTypeInventory?>(null) }
    LaunchedEffect(projectId, refreshKey) {
        state = LoadBalancerTypesState.Loading
        state = try {
            LoadBalancerTypesState.Content(withContext(Dispatchers.IO) { repository.getLoadBalancerTypes() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            LoadBalancerTypesState.Error
        }
    }
    selected?.let { initial ->
        LoadBalancerTypeDetailScreen(initial, repository) { selected = null }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        item {
            Text(stringResource(R.string.load_balancer_types_title), style = MaterialTheme.typography.headlineMedium)
            Text(stringResource(R.string.load_balancer_types_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        when (val current = state) {
            LoadBalancerTypesState.Loading -> item { Text(stringResource(R.string.load_balancer_types_loading)) }
            LoadBalancerTypesState.Error -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.load_balancer_types_error))
                    OutlinedButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.common_retry)) }
                }
            }
            is LoadBalancerTypesState.Content -> {
                if (current.types.isEmpty()) item { Text(stringResource(R.string.load_balancer_types_empty)) }
                items(current.types.sortedWith(compareBy<LoadBalancerTypeInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { type ->
                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { selected = type },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(type.name, style = MaterialTheme.typography.titleMedium)
                            type.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            Text(stringResource(R.string.load_balancer_types_open), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadBalancerTypeDetailScreen(initial: LoadBalancerTypeInventory, repository: HCloudRepository, onBack: () -> Unit) {
    var refreshKey by remember(initial.id) { mutableIntStateOf(0) }
    var state by remember(initial.id) { mutableStateOf<LoadBalancerTypeDetailState>(LoadBalancerTypeDetailState.Loading) }
    LaunchedEffect(initial.id, refreshKey) {
        state = LoadBalancerTypeDetailState.Loading
        state = try {
            LoadBalancerTypeDetailState.Content(withContext(Dispatchers.IO) { repository.getLoadBalancerType(initial.id) })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            LoadBalancerTypeDetailState.Error
        }
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item { OutlinedButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
        when (val current = state) {
            LoadBalancerTypeDetailState.Loading -> item { Text(stringResource(R.string.load_balancer_types_loading)) }
            LoadBalancerTypeDetailState.Error -> item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.load_balancer_types_detail_error))
                    OutlinedButton(onClick = { refreshKey++ }) { Text(stringResource(R.string.common_retry)) }
                }
            }
            is LoadBalancerTypeDetailState.Content -> item { LoadBalancerTypeDetails(current.type) }
        }
    }
}

@Composable
private fun LoadBalancerTypeDetails(type: LoadBalancerTypeInventory) = Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
    Text(type.name, style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.common_id, type.id))
    type.description?.takeIf { it.isNotBlank() }?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    Text(stringResource(R.string.load_balancer_types_limits), style = MaterialTheme.typography.titleLarge)
    val limits = listOf(
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
    limits.filter { it.second != null }.forEach { (label, value) -> Text(stringResource(R.string.load_balancer_types_limit_value, stringResource(label), value!!)) }
    if (limits.all { it.second == null }) Text(stringResource(R.string.load_balancer_types_limits_unavailable))
    Text(stringResource(R.string.load_balancer_types_prices), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.load_balancer_types_prices_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (type.prices.isEmpty()) Text(stringResource(R.string.load_balancer_types_prices_empty))
    type.prices.sortedBy { it.location }.forEach { price ->
        Text(stringResource(R.string.load_balancer_types_price, price.location, price.hourly.gross, price.monthly.gross))
    }
    Text(stringResource(R.string.load_balancer_types_read_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
