package com.hcloudcontrol

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.LoadBalancerInventory
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricType
import com.hcloudcontrol.core.domain.model.LoadBalancerMetrics
import com.hcloudcontrol.core.domain.model.LoadBalancerMetricsQuery
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.repository.LoadBalancerMetricsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant

private sealed interface LoadBalancerListState {
    data object Loading : LoadBalancerListState
    data class Content(val loadBalancers: List<LoadBalancerInventory>) : LoadBalancerListState
    data object Error : LoadBalancerListState
}

private sealed interface LoadBalancerDetailState {
    data object Loading : LoadBalancerDetailState
    data class Content(val loadBalancer: LoadBalancerInventory) : LoadBalancerDetailState
    data object Error : LoadBalancerDetailState
}

private sealed interface LoadBalancerActionsState {
    data object Loading : LoadBalancerActionsState
    data class Content(val actions: List<CloudAction>) : LoadBalancerActionsState
    data object Error : LoadBalancerActionsState
}

private sealed interface LoadBalancerMetricsState {
    data object Loading : LoadBalancerMetricsState
    data class Content(val metrics: LoadBalancerMetrics) : LoadBalancerMetricsState
    data object Error : LoadBalancerMetricsState
}

/** A bounded window keeps the monitoring request small and inside the API's 30-day validation rule. */
private val LOAD_BALANCER_METRICS_WINDOW: Duration = Duration.ofHours(6)

@Composable
fun LoadBalancersScreen(
    repository: HCloudRepository,
    metricsRepository: LoadBalancerMetricsRepository,
    projectId: String,
) {
    var refreshKey by remember(projectId) { mutableIntStateOf(0) }
    var state by remember(projectId) { mutableStateOf<LoadBalancerListState>(LoadBalancerListState.Loading) }
    var selected by remember(projectId) { mutableStateOf<LoadBalancerInventory?>(null) }
    var showTypes by remember(projectId) { mutableStateOf(false) }

    LaunchedEffect(projectId, refreshKey) {
        state = LoadBalancerListState.Loading
        state = try {
            LoadBalancerListState.Content(withContext(Dispatchers.IO) { repository.getLoadBalancers() })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Throwable) {
            LoadBalancerListState.Error
        }
    }

    selected?.let { loadBalancer ->
        LoadBalancerDetailScreen(
            initial = loadBalancer,
            repository = repository,
            metricsRepository = metricsRepository,
            onBack = { selected = null },
            onDeleted = {
                selected = null
                refreshKey++
            },
        )
        return
    }
    if (showTypes) {
        LoadBalancerTypesScreen(repository = repository, projectId = projectId, onBack = { showTypes = false })
        return
    }

    when (val current = state) {
        LoadBalancerListState.Loading -> LoadBalancerLoading(stringResource(R.string.load_balancers_loading))
        LoadBalancerListState.Error -> StateView(
            stringResource(R.string.load_balancers_error_title),
            stringResource(R.string.load_balancers_error_message),
            stringResource(R.string.load_balancers_retry),
        ) { refreshKey++ }
        is LoadBalancerListState.Content -> {
            if (current.loadBalancers.isEmpty()) {
                LoadBalancerEmptyState(onRetry = { refreshKey++ }, onBrowseTypes = { showTypes = true })
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    item {
                        Text(stringResource(R.string.load_balancers_title), style = MaterialTheme.typography.headlineMedium)
                        Text(stringResource(R.string.load_balancers_description), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        OutlinedButton(onClick = { showTypes = true }, modifier = Modifier.padding(top = 8.dp)) {
                            Text(stringResource(R.string.load_balancer_types_open_catalog))
                        }
                    }
                    items(current.loadBalancers.sortedWith(compareBy<LoadBalancerInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { loadBalancer ->
                        LoadBalancerCard(loadBalancer) { selected = loadBalancer }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadBalancerEmptyState(onRetry: () -> Unit, onBrowseTypes: () -> Unit) = Column(
    modifier = Modifier.fillMaxSize().padding(24.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
) {
    Text(stringResource(R.string.load_balancers_empty_title), style = MaterialTheme.typography.headlineMedium)
    Text(stringResource(R.string.load_balancers_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
    OutlinedButton(onClick = onBrowseTypes) { Text(stringResource(R.string.load_balancer_types_open_catalog)) }
    OutlinedButton(onClick = onRetry) { Text(stringResource(R.string.load_balancers_retry)) }
}

@Composable
private fun LoadBalancerCard(loadBalancer: LoadBalancerInventory, onClick: () -> Unit) = Card(
    Modifier.fillMaxWidth().clickable(onClick = onClick),
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
) {
    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(loadBalancer.name, style = MaterialTheme.typography.titleMedium)
        Text(
            stringResource(R.string.load_balancers_type_location, loadBalancer.type, loadBalancer.location),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            stringResource(R.string.load_balancers_targets_services, loadBalancer.targets.size, loadBalancer.services.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadBalancerDetailScreen(
    initial: LoadBalancerInventory,
    repository: HCloudRepository,
    metricsRepository: LoadBalancerMetricsRepository,
    onBack: () -> Unit,
    onDeleted: () -> Unit,
) {
    var detailState by remember(initial.id) { mutableStateOf<LoadBalancerDetailState>(LoadBalancerDetailState.Loading) }
    var actionsState by remember(initial.id) { mutableStateOf<LoadBalancerActionsState>(LoadBalancerActionsState.Loading) }
    var metricsState by remember(initial.id) { mutableStateOf<LoadBalancerMetricsState>(LoadBalancerMetricsState.Loading) }
    var deleteRequested by remember(initial.id) { mutableStateOf(false) }
    var refreshKey by remember(initial.id) { mutableIntStateOf(0) }

    LaunchedEffect(initial.id, refreshKey) {
        coroutineScope {
            val detail = async(Dispatchers.IO) { runCatching { repository.getLoadBalancer(initial.id) } }
            val actions = async(Dispatchers.IO) { runCatching { repository.getLoadBalancerActions(initial.id) } }
            val metrics = async(Dispatchers.IO) {
                val end = Instant.now()
                val query = LoadBalancerMetricsQuery(
                    loadBalancerId = initial.id,
                    types = LoadBalancerMetricType.values().toList(),
                    start = end.minus(LOAD_BALANCER_METRICS_WINDOW),
                    end = end,
                )
                runCatching { metricsRepository.getLoadBalancerMetrics(query) }
            }
            detailState = detail.await().fold(
                onSuccess = { LoadBalancerDetailState.Content(it) },
                onFailure = { LoadBalancerDetailState.Error },
            )
            actionsState = actions.await().fold(
                onSuccess = { LoadBalancerActionsState.Content(it) },
                onFailure = { LoadBalancerActionsState.Error },
            )
            metricsState = metrics.await().fold(
                onSuccess = { LoadBalancerMetricsState.Content(it) },
                onFailure = { LoadBalancerMetricsState.Error },
            )
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { TextButton(onClick = onBack) { Text("← ${stringResource(R.string.common_back)}") } }
        item {
            when (val current = detailState) {
                LoadBalancerDetailState.Loading -> LoadBalancerLoading(stringResource(R.string.load_balancers_detail_loading))
                LoadBalancerDetailState.Error -> Text(stringResource(R.string.load_balancers_detail_load_error), color = MaterialTheme.colorScheme.error)
                is LoadBalancerDetailState.Content -> {
                    LoadBalancerOverview(current.loadBalancer)
                    LoadBalancerLifecycleControls(
                        loadBalancer = current.loadBalancer,
                        operationsAllowed = LocalHighImpactOperations.current.isAllowed,
                        onDelete = { deleteRequested = true },
                    )
                    ResourceProtectionControls(
                        resourceName = current.loadBalancer.name,
                        deleteProtected = current.loadBalancer.deleteProtected,
                        repository = repository,
                        onChange = { patch -> repository.changeLoadBalancerProtection(current.loadBalancer.id, patch) },
                        onCompleted = { refreshKey++ },
                    )
                }
            }
        }
        item { LoadBalancerMetricsSection(metricsState) }
        item { LoadBalancerActionHistorySection(actionsState) }
    }
    if (deleteRequested && detailState is LoadBalancerDetailState.Content) {
        DeleteLoadBalancerDialog(
            loadBalancer = (detailState as LoadBalancerDetailState.Content).loadBalancer,
            repository = repository,
            onDismiss = { deleteRequested = false },
            onDeleted = onDeleted,
        )
    }
}

@Composable
private fun LoadBalancerLifecycleControls(
    loadBalancer: LoadBalancerInventory,
    operationsAllowed: Boolean,
    onDelete: () -> Unit,
) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.load_balancers_lifecycle_title), style = MaterialTheme.typography.titleMedium)
    if (!operationsAllowed) {
        Text(stringResource(R.string.load_balancers_lifecycle_disabled), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else if (loadBalancer.deleteProtected) {
        Text(stringResource(R.string.load_balancers_delete_protected), color = MaterialTheme.colorScheme.onSurfaceVariant)
    } else {
        Text(stringResource(R.string.load_balancers_delete_cost_warning), color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(
            onClick = onDelete,
            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) { Text(stringResource(R.string.load_balancers_delete_action)) }
    }
}

@Composable
private fun DeleteLoadBalancerDialog(
    loadBalancer: LoadBalancerInventory,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onDeleted: () -> Unit,
) {
    var typedName by remember(loadBalancer.id) { mutableStateOf("") }
    var acknowledgement by remember(loadBalancer.id) { mutableStateOf("") }
    var submitting by remember(loadBalancer.id) { mutableStateOf(false) }
    var progress by remember(loadBalancer.id) { mutableStateOf<String?>(null) }
    var failure by remember(loadBalancer.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val errorFallback = stringResource(R.string.load_balancers_delete_failed)
    val isAllowed = LocalHighImpactOperations.current.isAllowed
    val canDelete = isAllowed && !loadBalancer.deleteProtected && typedName == loadBalancer.name && acknowledgement == "DELETE"
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(R.string.load_balancers_delete_title, loadBalancer.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.load_balancers_delete_warning), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.load_balancers_delete_traffic_warning))
                Text(stringResource(R.string.load_balancers_delete_type_name, loadBalancer.name))
                OutlinedTextField(
                    value = typedName,
                    onValueChange = { typedName = it; failure = null },
                    enabled = !submitting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.load_balancers_delete_name_label)) },
                )
                Text(stringResource(R.string.load_balancers_delete_type_word))
                OutlinedTextField(
                    value = acknowledgement,
                    onValueChange = { acknowledgement = it; failure = null },
                    enabled = !submitting,
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.load_balancers_delete_word_label)) },
                )
                if (submitting) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                    Text(progress ?: stringResource(R.string.common_sending_request))
                }
                failure?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!canDelete) return@Button
                    submitting = true
                    failure = null
                    scope.launch {
                        try {
                            val action = withContext(Dispatchers.IO) { repository.deleteLoadBalancer(loadBalancer.id) }
                            awaitCloudAction(repository, action) { progress = it }
                            onDeleted()
                        } catch (error: Throwable) {
                            failure = safeApiFailureMessage(error, errorFallback)
                            submitting = false
                        }
                    }
                },
                enabled = canDelete && !submitting,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else R.string.load_balancers_delete_confirm)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun LoadBalancerOverview(loadBalancer: LoadBalancerInventory) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(loadBalancer.name, style = MaterialTheme.typography.headlineMedium)
    LoadBalancerValue(stringResource(R.string.common_id, loadBalancer.id))
    LoadBalancerValue(stringResource(R.string.load_balancers_type_location, loadBalancer.type, loadBalancer.location))
    val publicAddresses = listOfNotNull(loadBalancer.publicInterface.ipv4Address, loadBalancer.publicInterface.ipv6Address)
    LoadBalancerValue(
        if (loadBalancer.publicInterface.enabled) {
            stringResource(R.string.load_balancers_public_enabled, publicAddresses.joinToString().ifBlank { stringResource(R.string.load_balancers_public_no_address) })
        } else stringResource(R.string.load_balancers_public_disabled),
    )
    Text(stringResource(R.string.load_balancers_services_title, loadBalancer.services.size), style = MaterialTheme.typography.titleMedium)
    if (loadBalancer.services.isEmpty()) LoadBalancerValue(stringResource(R.string.common_none))
    loadBalancer.services.forEach { service ->
        LoadBalancerValue(
            stringResource(
                R.string.load_balancers_service,
                service.protocol,
                service.listenPort?.toString() ?: "—",
                service.destinationPort?.toString() ?: "—",
            ),
        )
    }
    Text(stringResource(R.string.load_balancers_targets_title, loadBalancer.targets.size), style = MaterialTheme.typography.titleMedium)
    if (loadBalancer.targets.isEmpty()) LoadBalancerValue(stringResource(R.string.common_none))
    loadBalancer.targets.forEach { target ->
        val targetName = target.serverId?.let { stringResource(R.string.load_balancers_target_server, it) }
            ?: target.labelSelector?.let { stringResource(R.string.load_balancers_target_label_selector, it) }
            ?: target.ip
            ?: target.type
        val health = target.healthStatuses.joinToString { "${it.listenPort}: ${it.status}" }
        LoadBalancerValue(
            if (health.isBlank()) targetName else stringResource(R.string.load_balancers_target_health, targetName, health),
        )
    }
}

@Composable
private fun LoadBalancerMetricsSection(state: LoadBalancerMetricsState) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.load_balancers_metrics_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.load_balancers_metrics_window), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    when (state) {
        LoadBalancerMetricsState.Loading -> LoadBalancerLoading(stringResource(R.string.load_balancers_metrics_loading))
        LoadBalancerMetricsState.Error -> Text(stringResource(R.string.load_balancers_metrics_load_error), color = MaterialTheme.colorScheme.error)
        is LoadBalancerMetricsState.Content -> {
            if (state.metrics.timeSeries.isEmpty()) {
                LoadBalancerValue(stringResource(R.string.load_balancers_metrics_empty))
            } else Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.metrics.timeSeries.toSortedMap().forEach { (type, series) ->
                        val latest = series.samples.maxByOrNull { it.timestampSeconds }
                        LoadBalancerValue("${type.uppercase()}: ${latest?.value ?: stringResource(R.string.load_balancers_metrics_no_samples)}")
                    }
                    Text(stringResource(R.string.load_balancers_metrics_step, state.metrics.stepSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun LoadBalancerActionHistorySection(state: LoadBalancerActionsState) = Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
    Text(stringResource(R.string.load_balancers_action_history_title), style = MaterialTheme.typography.titleLarge)
    Text(stringResource(R.string.load_balancers_action_history_description), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    when (state) {
        LoadBalancerActionsState.Loading -> LoadBalancerLoading(stringResource(R.string.load_balancers_action_history_loading))
        LoadBalancerActionsState.Error -> Text(stringResource(R.string.load_balancers_action_history_load_error), color = MaterialTheme.colorScheme.error)
        is LoadBalancerActionsState.Content -> {
            if (state.actions.isEmpty()) {
                LoadBalancerValue(stringResource(R.string.load_balancers_action_history_empty))
            } else state.actions.sortedByDescending { it.id }.forEach { action ->
                Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(action.command ?: stringResource(R.string.load_balancers_action_unknown), style = MaterialTheme.typography.titleSmall)
                        LoadBalancerValue(stringResource(R.string.load_balancers_action_status, action.status))
                        action.progress?.let { LoadBalancerValue(stringResource(R.string.load_balancers_action_progress, it)) }
                        action.started?.let { LoadBalancerValue(stringResource(R.string.load_balancers_action_started, it)) }
                        action.finished?.let { LoadBalancerValue(stringResource(R.string.load_balancers_action_finished, it)) }
                        // API error messages can contain operator-facing values, so only the stable code is shown.
                        action.errorCode?.let { LoadBalancerValue(stringResource(R.string.load_balancers_action_error_code, it)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadBalancerLoading(message: String) = Row(verticalAlignment = Alignment.CenterVertically) {
    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
    Spacer(Modifier.width(12.dp))
    Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LoadBalancerValue(value: String) = Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant)
