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
import kotlinx.coroutines.*

/** Form -> immutable shared review -> durable result. No mutation retry is exposed. */
@Composable
internal fun VolumeMutationFlow(initialVolume: VolumeInventory, kind: VolumeMutation, servers: List<Server>,
    repository: HCloudRepository, onCompleted: () -> Unit, onDismiss: () -> Unit) {
    val owner = LocalOperationContext.current
    val volume = remember(owner, initialVolume.id, kind) { initialVolume }
    var targetId by rememberSaveable(owner, volume.id, kind) { mutableStateOf<Long?>(null) }
    var size by rememberSaveable(owner, volume.id, kind) { mutableStateOf("") }
    var picking by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(kind == VolumeMutation.DETACH || kind == VolumeMutation.DELETE) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    var update by remember { mutableStateOf(0) }
    val draft = VolumeMutationDraft(kind, targetId, size.toIntOrNull())
    val session = rememberOperationSession("volume", volume.id.toString(), kind.name.lowercase(),
        listOf(volume, draft).hashCode().toLong() and 0xffffffffL)
    val coordinator = LocalOperationCoordinator.current
    val currentContext by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val adapter = remember(repository, coordinator) { coordinator?.let { VolumeOperationAdapter(repository, it, { currentContext }, { allowed }) } }
    var submitted by remember(session) { mutableStateOf(session.record != null) }
    val record = remember(session, update, submitted, busy) { session.record }
    val scope = rememberCoroutineScope()
    val title = stringResource(when (kind) {
        VolumeMutation.ATTACH -> R.string.storage_attach_volume
        VolumeMutation.DETACH -> R.string.storage_detach_volume
        VolumeMutation.RESIZE -> R.string.storage_resize_volume
        VolumeMutation.DELETE -> R.string.volume_delete
    })
    suspend fun refreshResult() {
        busy = true
        try {
            error = !refreshOperationResource(session) {
                check(currentContext == session.context)
                withContext(Dispatchers.IO) { repository.getVolumes() }
                check(currentContext == session.context)
                onCompleted()
            }
            update++
        } finally { busy = false }
    }
    if (submitted && record != null) {
        OperationResultDialog(title, record, record.intent.resource.context.connectionId, volume.name,
                onClose = onDismiss, busy = busy, session = session, onCheckStatus = if (busy) null else ({
                    busy = true; error = false
                    scope.launch {
                        try { withContext(Dispatchers.IO) { adapter?.reconcile(record) }; update++ }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { error = true }
                        finally { busy = false }
                    }
                }), onRefreshResource = { if (!busy) scope.launch { refreshResult() } }) {
            if (error) Text(stringResource(R.string.ds_read_error), color = MaterialTheme.colorScheme.error)
        }
        LaunchedEffect(record.stage) { if (record.stage == OperationStage.SUCCEEDED) {
            refreshResult()
        } }
        return
    }
    val eligible = servers.filter { it.location == volume.location }.sortedBy { it.name.lowercase() }
    val valid = when (kind) {
        VolumeMutation.ATTACH -> eligible.any { it.id == targetId } && volume.serverId == null
        VolumeMutation.DETACH -> volume.serverId != null
        VolumeMutation.RESIZE -> (draft.newSizeGb ?: 0) > volume.sizeGb
        VolumeMutation.DELETE -> !volume.deleteProtected && volume.serverId == null
    }
    val warning = if (kind == VolumeMutation.DELETE) stringResource(R.string.volume_delete_warning, volume.name) else stringResource(when (kind) {
        VolumeMutation.ATTACH -> R.string.storage_attach_explanation
        VolumeMutation.DETACH -> R.string.storage_detach
        VolumeMutation.RESIZE -> R.string.storage_resize_acknowledgement
        VolumeMutation.DELETE -> error("Handled above")
    })
    if (reviewing) {
        val intent = session.intent ?: return
        val effects = buildList {
            add(ReviewChange(title, null, warning))
            if (kind == VolumeMutation.RESIZE) add(ReviewChange(stringResource(R.string.storage_new_size), volume.sizeGb.toString(), size))
            if (kind == VolumeMutation.ATTACH) add(ReviewChange(stringResource(R.string.common_select_server), null,
                eligible.firstOrNull { it.id == targetId }?.let { "${it.name} · ${it.id}" }.orEmpty()))
        }
        val review = remember(intent, effects, update) { OperationReviewContent(intent, System.currentTimeMillis(), title,
            intent.resource.context.connectionId, volume.name, effects,
            cost = if (kind == VolumeMutation.RESIZE) CostPresentation.Unavailable else null,
            typedIdentity = if (kind == VolumeMutation.DELETE) volume.name else null,
            acknowledgements = listOf(RiskAcknowledgement("impact", warning))) }
        OperationReviewDialog(review, onConfirm = { proof ->
            busy = true; error = false
            scope.launch {
                try { withContext(Dispatchers.IO) { checkNotNull(adapter).submit(proof, volume, draft) {
                    withContext(Dispatchers.Main) { submitted = true; update++ }
                } }; submitted = session.record != null; update++ }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { error = true; submitted = session.record != null; update++ }
                finally { busy = false }
            }
        }, onDismiss = { if (!busy) { if (kind in listOf(VolumeMutation.ATTACH, VolumeMutation.RESIZE)) reviewing = false else onDismiss() } },
            submitEnabled = valid && allowed && session.configured && !busy)
    } else HCloudDialog(title, stringResource(R.string.ds_operation_review), { reviewing = true }, onDismiss,
        confirmEnabled = valid && allowed && !busy) {
        if (kind == VolumeMutation.RESIZE) {
            Text(stringResource(R.string.storage_resize_explanation, volume.sizeGb))
            HCloudFormField(size, { size = it.filter(Char::isDigit) }, stringResource(R.string.storage_new_size))
        } else OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
            Text(eligible.firstOrNull { it.id == targetId }?.name ?: stringResource(R.string.common_select_server))
        }
    }
    if (picking) HCloudResourcePicker(stringResource(R.string.common_select_server),
        ResourcePickerState("${owner?.connectionId}:${volume.id}:servers", eligible.map { ResourcePickerItem(it.id.toString(), it.name, it.location) }),
        targetId?.toString(), { targetId = it.toLong() }, { picking = false })
    if (error) HCloudDialog(title, stringResource(R.string.ds_close), { error = false }, { error = false }) {
        Text(stringResource(R.string.ds_read_error))
    }
}
