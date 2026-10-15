package com.hcloudcontrol

import androidx.activity.compose.BackHandler
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
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.navigation.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import com.hcloudcontrol.operations.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.Box

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
fun LoadBalancersScreen(repository: HCloudRepository, metricsRepository: LoadBalancerMetricsRepository, projectId: String,
    billingRepository: com.hcloudcontrol.core.domain.repository.BillingRateCardRepository? = null) {
    val scope = "load-balancers:$projectId:${LocalNavigationContext.current?.endpoint.orEmpty()}"
    val loader = rememberSectionLoader(scope, isEmpty = { it: List<LoadBalancerInventory> -> it.isEmpty() }) { repository.getLoadBalancers() }
    var selectedId by rememberContextualResourceId(scope)
    var query by rememberSaveable(scope) { mutableStateOf("") }
    var showTypes by rememberSaveable(scope) { mutableStateOf(false) }
    BackHandler(enabled = LocalNavigationChrome.current == null && (selectedId != null || showTypes)) { if (selectedId != null) selectedId = null else showTypes = false }
    val selected = loader.state.data?.firstOrNull { it.id == selectedId }
    if (selected != null) {
        RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.LOAD_BALANCER, selected.id.toString(), selected.name) { selectedId = null }
        LoadBalancerDetailScreen(selected, repository, metricsRepository, { selectedId = null }, { selectedId = null; loader.refresh() })
        return
    }
    if (showTypes) { LoadBalancerTypesScreen(repository, projectId, billingRepository) { showTypes = false }; return }
    HCloudPullToRefresh(loader.state.refreshing, loader::refresh, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState("load-balancers", contentReady = loader.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text(stringResource(R.string.load_balancers_title), style = MaterialTheme.typography.titleLarge)
                HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
                OutlinedButton(onClick = { showTypes = true }) { Text(stringResource(R.string.load_balancer_types_open_catalog)) }
                HCloudReadSection(loader) {}
                if (selectedId != null && loader.state.hasSuccessfulResponse && loader.state.data.orEmpty().none { it.id == selectedId }) {
                    HCloudSectionState(SectionPresentation(SectionKind.MISSING))
                    OutlinedButton(onClick = { selectedId = null }) { Text(stringResource(R.string.common_back)) }
                }
            }
            val all = loader.state.data.orEmpty()
            val filtered = all.filter { query.isBlank() || it.name.contains(query.trim(), true) || it.location.contains(query.trim(), true) }
            if (all.isNotEmpty() && filtered.isEmpty()) item { HCloudSectionState(SectionPresentation(SectionKind.NO_RESULTS)) }
            items(filtered.sortedWith(compareBy<LoadBalancerInventory> { it.name.lowercase() }.thenBy { it.id }), key = { it.id }) { balancer ->
                HCloudResourceCard(ResourceCardModel("$projectId:load-balancer:${balancer.id}", balancer.name,
                    stringResource(R.string.load_balancers_type_location, balancer.type, balancer.location),
                    stringResource(R.string.load_balancers_targets_services, balancer.targets.size, balancer.services.size)), { selectedId = balancer.id })
            }
        }
    }
}

@Composable
private fun LoadBalancerDetailScreen(initial: LoadBalancerInventory, repository: HCloudRepository,
    metricsRepository: LoadBalancerMetricsRepository, onBack: () -> Unit, onDeleted: () -> Unit) {
    val scope = "load-balancer:${LocalNavigationContext.current}:${initial.id}"
    val metadata = rememberSectionLoader(scope + ":metadata") { repository.getLoadBalancer(initial.id) }
    val history = rememberSectionLoader(scope + ":history") { repository.getLoadBalancerActions(initial.id) }
    val metrics = rememberSectionLoader(scope + ":metrics") {
        val end = Instant.now()
        metricsRepository.getLoadBalancerMetrics(LoadBalancerMetricsQuery(initial.id, LoadBalancerMetricType.values().toList(),
            end.minus(LOAD_BALANCER_METRICS_WINDOW), end))
    }
    var deleteRequested by rememberSaveable(scope) { mutableStateOf(false) }
    var protectionRequested by remember(scope) { mutableStateOf(false) }
    var menu by remember(scope) { mutableStateOf(false) }
    var relations by rememberSaveable(scope) { mutableStateOf(false) }
    val fresh = metadata.state.hasSuccessfulResponse && metadata.state.error == null && !metadata.state.refreshing
    BackHandler(enabled = LocalNavigationChrome.current == null && relations) { relations = false }
    if (relations) {
        RegisterResourceChildNavigation(RootDestination.MORE, ResourceType.LOAD_BALANCER, initial.id.toString(),
            stringResource(R.string.load_balancers_title)) { relations = false }
        HCloudPullToRefresh(metadata.state.refreshing, metadata::refresh, Modifier.fillMaxSize()) {
            LazyColumn(state = rememberRestorableLazyListState("load-balancer:${initial.id}:relations", contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { TextButton(onClick = { relations = false }) { Text(stringResource(R.string.common_back)) } }
                item { HCloudReadSection(metadata) { LoadBalancerRelations(it) } }
            }
        }
        return
    }
    HCloudPullToRefresh(metadata.state.refreshing || history.state.refreshing || metrics.state.refreshing,
        { metadata.refresh(); history.refresh(); metrics.refresh() }, Modifier.fillMaxSize()) {
        LazyColumn(state = rememberRestorableLazyListState("load-balancer:${initial.id}:detail", contentReady = metadata.state.data != null), modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { TextButton(onClick = onBack) { Text(stringResource(R.string.common_back)) } }
            item { HCloudReadSection(metadata) { balancer ->
                LoadBalancerOverview(balancer)
                OutlinedButton(onClick = { relations = true }) {
                    Text(stringResource(R.string.load_balancers_targets_services, balancer.targets.size, balancer.services.size))
                }
                if (LocalHighImpactOperations.current.isAllowed && fresh) {
                    Box {
                        OutlinedButton(onClick = { menu = true }) { Text(stringResource(R.string.ds_actions)) }
                        HCloudMenu(menu, listOf(MenuAction("protection", if (!balancer.protectionKnown)
                            stringResource(R.string.resource_protection_current, stringResource(R.string.ds_cost_unknown))
                            else stringResource(if (balancer.deleteProtected) R.string.resource_protection_disable else R.string.resource_protection_enable), balancer.protectionKnown),
                            MenuAction("delete", stringResource(R.string.load_balancers_delete_action), canDeleteLoadBalancer(balancer))),
                            { menu = false }, { action -> if (action == "delete") deleteRequested = true else protectionRequested = true })
                    }
                }
                if (protectionRequested) ResourceProtectionControls(balancer.name, balancer.deleteProtected,
                    ProtectionAdapter(ProtectionResource.LOAD_BALANCER, balancer.id, ProtectionField.DELETE, repository),
                    onCompleted = metadata::refresh, enabled = fresh, currentKnown = balancer.protectionKnown)
            } }
            item { HCloudReadSection(metrics) { LoadBalancerMetricsSection(LoadBalancerMetricsState.Content(it)) } }
            item { HCloudReadSection(history) { LoadBalancerActionHistorySection(LoadBalancerActionsState.Content(it)) } }
        }
    }
    if (deleteRequested && metadata.state.data != null && fresh) DeleteLoadBalancerDialog(checkNotNull(metadata.state.data),
        repository, { deleteRequested = false }, onDeleted)
}

@Composable
private fun DeleteLoadBalancerDialog(loadBalancer: LoadBalancerInventory, repository: HCloudRepository,
    onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val session = rememberOperationSession("load_balancer", loadBalancer.id.toString(), "delete",
        listOf(loadBalancer.name, loadBalancer.protectionKnown, loadBalancer.deleteProtected, loadBalancer.services, loadBalancer.targets.map { it.withoutLiveHealth() }).hashCode().toLong() and 0xffffffffL)
    val adapter = remember(repository, loadBalancer) { LoadBalancerDeletionAdapter(repository, loadBalancer) }
    val scope = rememberCoroutineScope()
    val activeContext by androidx.compose.runtime.rememberUpdatedState(LocalOperationContext.current)
    val allowed by androidx.compose.runtime.rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    var busy by remember(session) { mutableStateOf(false) }
    var failed by remember(session) { mutableStateOf(false) }
    var reviewTime by remember(session) { mutableStateOf(0L) }
    var progress by remember(session) { mutableStateOf(0) }
    val record = remember(session, busy, progress) { session.record }
    LaunchedEffect(session) {
        if (session.record == null) {
            busy = true
            try { withContext(Dispatchers.IO) { adapter.validateFresh() }; reviewTime = System.currentTimeMillis() }
            catch (_: Throwable) { failed = true }
            busy = false
        }
    }
    val title = stringResource(R.string.load_balancers_delete_title, loadBalancer.name)
    if (record != null) {
        OperationResultDialog(title, record, record.intent.resource.context.connectionId, "${loadBalancer.name} · #${loadBalancer.id}",
            onClose = { if (record.stage == OperationStage.SUCCEEDED) onDeleted() else onDismiss() }, busy = busy, session = session,
            onCheckStatus = {
                if (!busy && activeContext == session.context) { busy = true; scope.launch {
                    try {
                        val actionId = record.providerActionId
                        if (actionId != null) {
                            val original = repository.getAction(actionId); session.observeOriginalAction(original)
                            if (original.status !in setOf("success", "error")) awaitCloudAction(original,
                                { id -> repository.getAction(id).also { session.observeOriginalAction(it); progress++ } }, {})
                        } else session.reconcileExistingEffect(withContext(Dispatchers.IO) { adapter.deleted() })
                    } catch (_: Throwable) { failed = true; session.unknownIfPending() }
                    busy = false; progress++
                } }
            }) { if (failed) Text(stringResource(R.string.load_balancers_delete_failed), color = MaterialTheme.colorScheme.error) }
        return
    }
    if (reviewTime > 0 && !failed && session.intent != null) {
        val model = OperationReviewContent(checkNotNull(session.intent), reviewTime, title,
            session.context?.connectionId.orEmpty(), "${loadBalancer.name} · #${loadBalancer.id}",
            listOf(ReviewChange(stringResource(R.string.load_balancers_delete_action), null,
                stringResource(R.string.load_balancers_delete_traffic_warning)),
                ReviewChange(stringResource(R.string.ds_operation_identity), null,
                    stringResource(R.string.load_balancers_targets_services, loadBalancer.targets.size, loadBalancer.services.size))),
            typedIdentity = loadBalancer.name,
            acknowledgements = listOf(RiskAcknowledgement("traffic", stringResource(R.string.load_balancers_delete_warning))))
        OperationReviewDialog(model, { proof ->
            if (!busy) { busy = true; scope.launch {
                try {
                    withContext(Dispatchers.IO) { adapter.validateFresh() }
                    session.beginReviewed(proof, allowed, activeContext)
                    val action = withContext(Dispatchers.IO) { check(allowed && activeContext == session.context); adapter.delete() }
                    session.accepted(action); session.observeOriginalAction(action)
                    awaitCloudAction(action, { id -> repository.getAction(id).also { session.observeOriginalAction(it); progress++ } }, {})
                } catch (error: Throwable) { session.rejectOrUnknown(error); failed = true }
                busy = false; progress++
            } }
        }, onDismiss, submitEnabled = allowed && !busy)
    } else HCloudDialog(title, stringResource(R.string.ds_close), onDismiss, onDismiss) {
        HCloudSectionState(SectionPresentation(if (busy) SectionKind.LOADING else SectionKind.ERROR))
    }
}

@Composable
private fun LoadBalancerOverview(loadBalancer: LoadBalancerInventory) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(loadBalancer.name, style = MaterialTheme.typography.headlineMedium)
    LoadBalancerValue(stringResource(R.string.common_id, loadBalancer.id))
    LoadBalancerValue(stringResource(R.string.load_balancers_type_location, loadBalancer.type, loadBalancer.location))
    LoadBalancerValue(stringResource(R.string.resource_protection_current, if (!loadBalancer.protectionKnown)
        stringResource(R.string.ds_cost_unknown) else stringResource(if (loadBalancer.deleteProtected)
            R.string.common_protected_enabled else R.string.common_protected_disabled)))
    val publicAddresses = listOfNotNull(loadBalancer.publicInterface.ipv4Address, loadBalancer.publicInterface.ipv6Address)
    LoadBalancerValue(
        if (loadBalancer.publicInterface.enabled) {
            stringResource(R.string.load_balancers_public_enabled, publicAddresses.joinToString().ifBlank { stringResource(R.string.load_balancers_public_no_address) })
        } else stringResource(R.string.load_balancers_public_disabled),
    )
}

@Composable
private fun LoadBalancerRelations(loadBalancer: LoadBalancerInventory) = Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
    loadBalancer.targets.forEach { target -> LoadBalancerTargetDetails(target) }
}

/** Label-selector targets retain their provider-reported resolved children and health. */
@Composable
private fun LoadBalancerTargetDetails(target: com.hcloudcontrol.core.domain.model.LoadBalancerTarget) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val targetName = target.serverId?.let { stringResource(R.string.load_balancers_target_server, it) }
            ?: target.labelSelector?.let { stringResource(R.string.load_balancers_target_label_selector, it) }
            ?: target.ip
            ?: target.type
        val health = target.healthStatuses.joinToString { "${it.listenPort}: ${it.status}" }
        LoadBalancerValue(
            if (health.isBlank()) targetName else stringResource(R.string.load_balancers_target_health, targetName, health),
        )
        if (target.targets.isNotEmpty()) Column(Modifier.padding(start = 16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            target.targets.forEach { LoadBalancerTargetDetails(it) }
        }
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
            } else Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.metrics.timeSeries.toSortedMap().forEach { (type, series) ->
                        com.hcloudcontrol.ui.designsystem.HCloudMetricSeriesCard(type.uppercase(),
                            series.samples.map { com.hcloudcontrol.ui.designsystem.MetricSeriesPoint(it.timestampSeconds, it.value) }, state.metrics.stepSeconds)
                    }
                    Text(stringResource(R.string.load_balancers_metrics_step, state.metrics.stepSeconds), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
