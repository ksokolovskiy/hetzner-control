package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.ImageOption
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.model.ServerRebuildSpec
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.SecretResult
import com.hcloudcontrol.operations.SecretResultDialog
import com.hcloudcontrol.operations.consumeSecretResult
import com.hcloudcontrol.operations.LocalOperationContext
import com.hcloudcontrol.operations.OperationStage
import com.hcloudcontrol.operations.rememberOperationSession
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** These controls exist only behind the user's global high-impact operations setting. */
@Composable
internal fun ServerDestructiveOperationsSection(
    server: Server,
    repository: HCloudRepository,
    readOnly: Boolean,
    onDeleted: () -> Unit,
    onCompleted: () -> Unit,
) {
    var deleteOpen by remember(server.id) { mutableStateOf(false) }
    var rebuildOpen by remember(server.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.server_high_impact_title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.server_high_impact_note), color = MaterialTheme.colorScheme.error)
        OutlinedButton(onClick = { rebuildOpen = true }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.server_rebuild_action))
        }
        OutlinedButton(onClick = { deleteOpen = true }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.server_delete_action), color = MaterialTheme.colorScheme.error)
        }
    }
    if (deleteOpen) DeleteServerDialog(server, repository, { deleteOpen = false }, onDeleted)
    if (rebuildOpen) RebuildServerDialog(server, repository, { rebuildOpen = false }, onCompleted)
}

@Composable private fun DeleteServerDialog(server: Server, repository: HCloudRepository, onDismiss: () -> Unit, onDeleted: () -> Unit) =
    ServerDeletionDialog(server, repository, onDismiss, onDeleted)

@Composable private fun RebuildServerDialog(server: Server, repository: HCloudRepository, onDismiss: () -> Unit, onCompleted: () -> Unit) {
    val originalServer = remember(LocalOperationContext.current, server.id, "rebuild") { server }
    val session = rememberOperationSession("server", originalServer.id.toString(), "rebuild", originalServer.hashCode().toLong() and 0xffffffffL)
    val currentContext by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    var imageId by rememberSaveable(originalServer.id) { mutableStateOf<Long?>(null) }
    var query by rememberSaveable(originalServer.id) { mutableStateOf("") }
    var busy by remember(session) { mutableStateOf(false) }
    var tick by remember(session) { mutableStateOf(0) }
    var failed by remember(session) { mutableStateOf(false) }
    var review by remember(session) { mutableStateOf<com.hcloudcontrol.operations.OperationReviewContent?>(null) }
    var frozenImage by remember(session) { mutableStateOf<ImageOption?>(null) }
    var secret by remember(session) { mutableStateOf<SecretResult?>(null) }
    var secretDismissed by remember(session) { mutableStateOf(false) }
    val record = remember(session, busy, tick) { session.record }
    val title = stringResource(R.string.server_rebuild_title, originalServer.name)
    val warning = stringResource(R.string.server_rebuild_warning)
    val imageLabel = stringResource(R.string.server_rebuild_select_image)
    val scope = rememberCoroutineScope()
    val loader = com.hcloudcontrol.ui.state.rememberSectionLoader("${currentContext}:server:${originalServer.id}:rebuild-images",
        isEmpty = { items: List<ImageOption> -> items.isEmpty() }) {
        val arch = originalServer.hardware?.architecture ?: repository.getServerTypes().firstOrNull { it.name == originalServer.serverType }?.architecture
        check(arch != null)
        repository.getSystemImages().filter { it.architecture == arch }.sortedBy { it.name }
    }
    DisposableEffect(session) { onDispose { secret?.close() } }
    fun refresh() { scope.launch {
        com.hcloudcontrol.operations.refreshOperationResource(session) {
            check(currentContext == session.context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.getServer(originalServer.id) }
            check(currentContext == session.context)
            onCompleted()
        }
        tick++
    } }
    fun checkOriginal() {
        if (busy || currentContext != session.context) return
        busy = true
        scope.launch {
            try {
                if (session.record?.originalActionIds?.isNotEmpty() == true)
                    com.hcloudcontrol.operations.awaitOriginalOperationActions(session, repository)
                else session.unknownIfPending() // Current image cannot prove a lost rebuild request.
                if (session.record?.stage == OperationStage.SUCCEEDED) refresh()
            } catch (cancelled: CancellationException) { session.unknownIfPending(); throw cancelled }
            catch (_: Throwable) { session.unknownIfPending(); failed = true }
            finally { busy = false; tick++ }
        }
    }
    if (record?.stage == OperationStage.SUCCEEDED && record.secretIssued && !secretDismissed) {
        SecretResultDialog(secret) { secret = null; secretDismissed = true; refresh() }
        return
    }
    if (record != null) {
        com.hcloudcontrol.operations.OperationResultDialog(title, record, record.intent.resource.context.connectionId,
            originalServer.name, onClose = onDismiss, busy = busy, session = session,
            onCheckStatus = if (record.isPending) ::checkOriginal else null, onRefreshResource = ::refresh) {
            if (record.stage == OperationStage.SUCCEEDED && !record.secretIssued)
                Text(stringResource(R.string.server_rebuild_no_password))
        }
        return
    }
    review?.let { model ->
        com.hcloudcontrol.operations.OperationReviewDialog(model, { proof ->
            if (!busy) { busy = true; failed = false; scope.launch {
                try {
                    val fresh = repository.getServer(originalServer.id)
                    check(fresh.name == originalServer.name && fresh.protection == originalServer.protection && fresh.protection?.rebuild == false)
                    val selected = requireNotNull(frozenImage)
                    val actual = repository.getSystemImages().firstOrNull { it.id == selected.id }
                    check(actual == selected)
                    val arch = fresh.hardware?.architecture ?: repository.getServerTypes().firstOrNull { it.name == fresh.serverType }?.architecture
                    check(arch != null && selected.architecture == arch)
                    session.beginReviewed(proof, allowed, currentContext); tick++
                    check(allowed && currentContext == session.context)
                    val response = repository.rebuildServer(originalServer.id, ServerRebuildSpec(selected.id.toString()))
                    session.acceptedActions(listOf(response.action), null, response.password != null)
                    secret = response.password?.let(::consumeSecretResult)
                    session.observeOriginalAction(response.action); tick++
                    if (session.record?.isPending == true)
                        com.hcloudcontrol.operations.awaitOriginalOperationActions(session, repository, listOf(response.action))
                    if (session.record?.stage == OperationStage.SUCCEEDED) refresh()
                } catch (cancelled: CancellationException) { session.unknownIfPending(); throw cancelled }
                catch (failure: Throwable) { session.rejectOrUnknown(failure); failed = true; if (session.record == null) review = null }
                finally { busy = false; tick++ }
            } }
        }, { if (!busy) review = null }, submitEnabled = allowed && !busy)
        return
    }
    com.hcloudcontrol.ui.designsystem.HCloudDialog(title, stringResource(R.string.ds_operation_confirm), {
        if (!busy) { busy = true; failed = false; scope.launch {
            try {
                val fresh = repository.getServer(originalServer.id)
                check(fresh.name == originalServer.name && fresh.protection == originalServer.protection && fresh.protection?.rebuild == false)
                val arch = fresh.hardware?.architecture ?: repository.getServerTypes().firstOrNull { it.name == fresh.serverType }?.architecture
                val selected = requireNotNull(repository.getSystemImages().firstOrNull { it.id == imageId && it.architecture == arch })
                check(arch != null)
                frozenImage = selected
                val intent = requireNotNull(session.intent)
                review = com.hcloudcontrol.operations.OperationReviewContent(intent, System.currentTimeMillis(), title,
                    intent.resource.context.connectionId, fresh.name,
                    listOf(com.hcloudcontrol.operations.ReviewChange(imageLabel, fresh.image?.description, selected.description)),
                    typedIdentity = fresh.name,
                    acknowledgements = listOf(com.hcloudcontrol.operations.RiskAcknowledgement("rebuild", warning)))
            } catch (_: Throwable) { failed = true }
            finally { busy = false }
        } }
    }, { if (!busy) onDismiss() }, confirmEnabled = imageId != null && allowed && session.configured && !busy && originalServer.protection?.rebuild == false) {
        Text(warning, color = MaterialTheme.colorScheme.error)
        com.hcloudcontrol.ui.designsystem.HCloudFormField(query, { query = it }, stringResource(R.string.ds_search))
        com.hcloudcontrol.ui.designsystem.HCloudReadSection(loader) { items ->
            items.filter { it.name.contains(query, true) || it.description.contains(query, true) }.forEach { candidate ->
                OutlinedButton(onClick = { imageId = candidate.id }, modifier = Modifier.fillMaxWidth()) {
                    Text((if (imageId == candidate.id) "✓ " else "") + candidate.name + " · " + candidate.description)
                }
            }
        }
        if (busy) CircularProgressIndicator()
        if (failed) Text(stringResource(R.string.server_rebuild_error), color = MaterialTheme.colorScheme.error)
    }
}
