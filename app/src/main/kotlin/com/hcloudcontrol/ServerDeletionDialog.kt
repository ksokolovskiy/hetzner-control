package com.hcloudcontrol

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.res.stringResource
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.operations.*
import com.hcloudcontrol.ui.designsystem.HCloudDialog
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun ServerDeletionDialog(initialServer: Server, repository: HCloudRepository, onDismiss: () -> Unit, onDeleted: () -> Unit) {
    val owner = LocalOperationContext.current
    val server = remember(owner, initialServer.id) { initialServer }
    val session = rememberOperationSession("server", server.id.toString(), "delete",
        listOf(server.id, server.name, server.protection).hashCode().toLong() and 0xffffffffL)
    val coordinator = LocalOperationCoordinator.current
    val context by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    val adapter = remember(repository, coordinator) { coordinator?.let { ServerDeletionAdapter(repository, it, { context }, { allowed }) } }
    var submitted by remember(session) { mutableStateOf(session.record != null) }
    var busy by remember(session) { mutableStateOf(false) }
    var failedRead by remember(session) { mutableStateOf(false) }
    var revision by remember(session) { mutableStateOf(0) }
    val record = remember(session, revision, submitted, busy) { session.record }
    val scope = rememberCoroutineScope()
    if (submitted && record != null) {
        val closeResult: () -> Unit = {
            if (record.canClose) {
                if (record.stage == OperationStage.SUCCEEDED) runCatching { onDeleted() }
                    .onFailure { session.refreshFailed(); revision++ }
                onDismiss()
            }
        }
        OperationResultDialog(stringResource(R.string.server_delete_title, server.name), record,
                record.intent.resource.context.connectionId, server.name,
                onClose = closeResult, busy = busy, session = session, onCheckStatus = if (busy) null else ({
                    busy = true; failedRead = false
                    scope.launch {
                        try { withContext(Dispatchers.IO) { adapter?.reconcile(record) }; revision++ }
                        catch (cancelled: CancellationException) { throw cancelled }
                        catch (_: Exception) { failedRead = true }
                        finally { busy = false }
                    }
                })) {
            if (failedRead) Text(stringResource(R.string.ds_read_error), color = MaterialTheme.colorScheme.error)
        }
        return
    }
    val intent = session.intent ?: return
    val warning = stringResource(R.string.server_delete_warning)
    val review = remember(intent, server.name, warning, revision) { OperationReviewContent(
        intent, System.currentTimeMillis(), server.name, intent.resource.context.connectionId, server.name,
        effects = listOf(ReviewChange(server.name, null, warning)), typedIdentity = server.name,
        acknowledgements = listOf(RiskAcknowledgement("delete_data", warning)),
    ) }
    OperationReviewDialog(review, onConfirm = { proof ->
        busy = true; failedRead = false
        scope.launch {
            try {
                withContext(Dispatchers.IO) {
                    checkNotNull(adapter).submit(proof, server.name) {
                        withContext(Dispatchers.Main) { submitted = true; revision++ }
                    }
                }
                submitted = session.record != null; revision++
            } catch (cancelled: CancellationException) { throw cancelled }
              catch (_: Exception) { failedRead = true; submitted = session.record != null; revision++ }
            finally { busy = false }
        }
    }, onDismiss = { if (!busy) onDismiss() }, submitEnabled = allowed && session.configured && !busy)
    if (failedRead) HCloudDialog(stringResource(R.string.server_delete_action), stringResource(R.string.ds_close),
        { failedRead = false }, { failedRead = false }) { Text(stringResource(R.string.server_delete_error)) }
}
