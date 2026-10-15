package com.hcloudcontrol

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.*
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.*
import com.hcloudcontrol.ui.state.rememberSectionLoader
import kotlinx.coroutines.*

/** Unattached volume creation only; attachment remains a separately reviewed operation. */
@Composable
internal fun VolumeCreationFlow(repository: HCloudRepository, onCompleted: () -> Unit,
    onDismiss: () -> Unit, onCreated: ((Long) -> Unit)? = null) {
    val context by rememberUpdatedState(LocalOperationContext.current)
    val scopeKey = "${context?.connectionId}:${context?.endpoint}"
    var name by rememberSaveable(scopeKey) { mutableStateOf("") }
    var size by rememberSaveable(scopeKey) { mutableStateOf("") }
    var location by rememberSaveable(scopeKey) { mutableStateOf<String?>(null) }
    var picking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var failedRead by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf(0) }
    val locations = rememberSectionLoader("$scopeKey:volume-create:locations", { it: List<LocationOption> -> it.isEmpty() }) { repository.getLocations() }
    val valid = name.isNotBlank() && (size.toIntOrNull() ?: 0) > 0 && locations.state.canOfferMutation &&
        locations.state.data.orEmpty().any { it.name == location }
    val session = rememberOperationSession("volume", null, "create", listOf(name.trim(), size, location).hashCode().toLong() and 0xffffffffL)
    val coordinator = LocalOperationCoordinator.current
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val adapter = remember(repository, coordinator) { coordinator?.let { VolumeOperationAdapter(repository, it, { context }, { allowed }) } }
    var submitted by remember(session) { mutableStateOf(session.record != null) }
    val record = remember(session, update, submitted, busy) { session.record }
    val scope = rememberCoroutineScope()
    val title = stringResource(R.string.volume_create)
    suspend fun refreshResult() {
        busy = true
        try {
            failedRead = !refreshOperationResource(session) {
                check(context == session.context)
                withContext(Dispatchers.IO) { repository.getVolumes() }
                check(context == session.context)
                onCompleted()
            }
            update++
        } finally { busy = false }
    }
    if (submitted && record != null) {
        LaunchedEffect(record.stage) { if (record.stage == OperationStage.SUCCEEDED) {
            refreshResult()
        } }
        OperationResultDialog(title, record, record.intent.resource.context.connectionId, name.trim(), onDismiss, busy, session = session,
                onCheckStatus = if (busy) null else ({
                    busy = true; failedRead = false
                    scope.launch {
                        try { withContext(Dispatchers.IO) { adapter?.reconcile(record) }; update++ }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failedRead = true }
                        finally { busy = false }
                    }
                }), onRefreshResource = { if (!busy) scope.launch { refreshResult() } },
                onOpenResource = record.createdResourceId?.toLongOrNull()?.let { id -> onCreated?.let { open -> ({ open(id) }) } }) {
            if (failedRead) Text(stringResource(R.string.ds_read_error), color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val billingWarning = stringResource(R.string.volume_create_review_intro)
    if (reviewing && valid) {
        val intent = session.intent ?: return
        val review = remember(intent, update, billingWarning) { OperationReviewContent(intent, System.currentTimeMillis(), title,
            intent.resource.context.connectionId, name.trim(),
            listOf(ReviewChange(name.trim(), null, "$size GB · $location")), CostPresentation.Unavailable,
            acknowledgements = listOf(RiskAcknowledgement("billing", billingWarning))) }
        OperationReviewDialog(review, onConfirm = { proof ->
            val spec = CreateVolumeSpec(name.trim(), size.toInt(), requireNotNull(location))
            busy = true; failedRead = false
            scope.launch {
                try { withContext(Dispatchers.IO) { checkNotNull(adapter).create(proof, spec) {
                    withContext(Dispatchers.Main) { submitted = true; update++ }
                } }; submitted = session.record != null; update++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { failedRead = true; submitted = session.record != null; update++ }
                finally { busy = false }
            }
        }, onDismiss = { if (!busy) reviewing = false }, submitEnabled = allowed && session.configured && !busy)
    } else HCloudDialog(title, stringResource(R.string.volume_review), { reviewing = true }, onDismiss, confirmEnabled = valid && allowed) {
        HCloudFormField(name, { name = it }, stringResource(R.string.volume_name))
        HCloudFormField(size, { size = it.filter(Char::isDigit) }, stringResource(R.string.volume_size_gb))
        HCloudReadSection(locations) {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                Text(it.firstOrNull { item -> item.name == location }?.displayName() ?: stringResource(R.string.volume_location))
            }
        }
        HCloudCostPresentation(CostPresentation.Unavailable)
    }
    if (picking) HCloudResourcePicker(stringResource(R.string.volume_location), ResourcePickerState("$scopeKey:volume-location",
        locations.state.data.orEmpty().sortedBy { it.name }.map { ResourcePickerItem(it.name, it.displayName()) }), location,
        { location = it }, { picking = false }, onRetry = locations::refresh)
    if (failedRead) HCloudDialog(title, stringResource(R.string.ds_close), { failedRead = false }, { failedRead = false }) {
        Text(stringResource(R.string.ds_read_error))
    }
}
