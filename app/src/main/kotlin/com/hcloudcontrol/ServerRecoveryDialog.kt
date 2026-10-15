package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.EnableRescueSpec
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.ServerRecoveryUseCase
import com.hcloudcontrol.operations.SecretResult
import com.hcloudcontrol.operations.SecretResultDialog
import com.hcloudcontrol.operations.consumeSecretResult
import com.hcloudcontrol.operations.LocalOperationContext
import com.hcloudcontrol.operations.OperationStage
import com.hcloudcontrol.operations.rememberOperationSession
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal enum class ServerRecoveryOperation { RESET_ROOT_PASSWORD, ENABLE_RESCUE, DISABLE_RESCUE }

/** Recovery uses the same journal/review/result lifecycle; passwords never leave transient memory. */
@Composable
internal fun ServerRecoveryDialog(server: Server, operation: ServerRecoveryOperation,
    repository: HCloudRepository, onDismiss: () -> Unit, onActionCompleted: () -> Unit) {
    val originalServer = remember(LocalOperationContext.current, server.id, operation) { server }
    val session = rememberOperationSession("server", originalServer.id.toString(), operation.name.lowercase(), originalServer.hashCode().toLong() and 0xffffffffL)
    val currentContext by rememberUpdatedState(LocalOperationContext.current)
    val allowed by rememberUpdatedState(LocalHighImpactOperations.current.isAllowed)
    var busy by remember(session) { mutableStateOf(false) }
    var tick by remember(session) { mutableStateOf(0) }
    var error by remember(session) { mutableStateOf(false) }
    var review by remember(session) { mutableStateOf<com.hcloudcontrol.operations.OperationReviewContent?>(null) }
    var secretResult by remember(session) { mutableStateOf<SecretResult?>(null) }
    var secretDismissed by remember(session) { mutableStateOf(false) }
    val record = remember(session, busy, tick) { session.record }
    val title = recoveryTitle(operation)
    val description = recoveryDescription(operation, originalServer.name)
    val warning = stringResource(R.string.server_recovery_confirmation_warning)
    val scope = rememberCoroutineScope()
    val androidContext = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(session) { onDispose { secretResult?.close() } }
    fun refresh() { scope.launch {
        com.hcloudcontrol.operations.refreshOperationResource(session) {
            check(currentContext == session.context)
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { repository.getServer(originalServer.id) }
            check(currentContext == session.context)
            onActionCompleted()
        }
        tick++
    } }
    fun reconcile() {
        if (busy || currentContext != session.context) return
        busy = true
        scope.launch {
            try {
                if (session.record?.originalActionIds?.isNotEmpty() == true)
                    com.hcloudcontrol.operations.awaitOriginalOperationActions(session, repository)
                else session.unknownIfPending()
                if (session.record?.stage == OperationStage.SUCCEEDED) refresh()
            } catch (cancelled: CancellationException) { session.unknownIfPending(); throw cancelled }
            catch (_: Throwable) { session.unknownIfPending(); error = true }
            finally { busy = false; tick++ }
        }
    }
    if (record?.stage == OperationStage.SUCCEEDED && record.secretIssued && !secretDismissed) {
        SecretResultDialog(secretResult) {
            secretResult = null; secretDismissed = true
            refresh()
        }
        return
    }
    if (record != null) {
        com.hcloudcontrol.operations.OperationResultDialog(title, record, record.intent.resource.context.connectionId,
            originalServer.name, onClose = onDismiss, busy = busy, session = session,
            onCheckStatus = if (record.isPending) ::reconcile else null, onRefreshResource = ::refresh)
        return
    }
    review?.let { model ->
        com.hcloudcontrol.operations.OperationReviewDialog(model, onConfirm = { proof ->
            if (!busy) {
                busy = true; error = false
                scope.launch {
                    try {
                        val fresh = repository.getServer(originalServer.id)
                        check(fresh.name == originalServer.name && fresh.protection == originalServer.protection && fresh.status == originalServer.status)
                        check(operation != ServerRecoveryOperation.RESET_ROOT_PASSWORD || fresh.status == com.hcloudcontrol.core.domain.model.ServerStatus.RUNNING)
                        session.beginReviewed(proof, allowed, currentContext)
                        tick++
                        check(allowed && currentContext == session.context)
                        val action: CloudAction
                        var issued = false
                        when (operation) {
                            ServerRecoveryOperation.RESET_ROOT_PASSWORD -> {
                                val response = repository.resetRootPassword(originalServer.id)
                                action = response.action; issued = true
                                secretResult = consumeSecretResult(response.password)
                            }
                            ServerRecoveryOperation.ENABLE_RESCUE -> {
                                val response = repository.enableRescue(originalServer.id, EnableRescueSpec())
                                action = response.action; issued = true
                                secretResult = consumeSecretResult(response.password)
                            }
                            ServerRecoveryOperation.DISABLE_RESCUE -> action = repository.disableRescue(originalServer.id)
                        }
                        session.acceptedActions(listOf(action), null, issued)
                        session.observeOriginalAction(action); tick++
                        if (session.record?.isPending == true) com.hcloudcontrol.operations.awaitOriginalOperationActions(session, repository, listOf(action))
                        if (session.record?.stage == OperationStage.SUCCEEDED) refresh()
                    } catch (cancelled: CancellationException) { session.unknownIfPending(); throw cancelled }
                    catch (failure: Throwable) { session.rejectOrUnknown(failure); error = true; if (session.record == null) review = null }
                    finally { busy = false; tick++ }
                }
            }
        }, onDismiss = { if (!busy) review = null }, submitEnabled = allowed && !busy)
        return
    }
    com.hcloudcontrol.ui.designsystem.HCloudDialog(title, stringResource(R.string.ds_operation_confirm), {
        if (!busy) { busy = true; error = false; scope.launch {
            try {
                val fresh = repository.getServer(originalServer.id)
                check(fresh.name == originalServer.name && fresh.status == originalServer.status && fresh.protection == originalServer.protection)
                check(operation != ServerRecoveryOperation.RESET_ROOT_PASSWORD || fresh.status == com.hcloudcontrol.core.domain.model.ServerStatus.RUNNING)
                val intent = requireNotNull(session.intent)
                review = com.hcloudcontrol.operations.OperationReviewContent(intent, System.currentTimeMillis(), title,
                    intent.resource.context.connectionId, fresh.name,
                    listOf(com.hcloudcontrol.operations.ReviewChange(title, androidContext.getString(serverStatusLabelResource(fresh.status)), description)),
                    typedIdentity = fresh.name, acknowledgements = listOf(com.hcloudcontrol.operations.RiskAcknowledgement("recovery", warning)))
            } catch (_: Throwable) { error = true }
            finally { busy = false }
        } }
    }, { if (!busy) onDismiss() }, confirmEnabled = allowed && session.configured && !busy &&
        (operation != ServerRecoveryOperation.RESET_ROOT_PASSWORD || originalServer.status == com.hcloudcontrol.core.domain.model.ServerStatus.RUNNING)) {
        Text(description)
        Text(warning, color = MaterialTheme.colorScheme.error)
        if (busy) CircularProgressIndicator()
        if (error) Text(stringResource(R.string.server_recovery_action_failed), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun recoveryTitle(operation: ServerRecoveryOperation): String = stringResource(
    when (operation) {
        ServerRecoveryOperation.RESET_ROOT_PASSWORD -> R.string.server_recovery_reset_password_title
        ServerRecoveryOperation.ENABLE_RESCUE -> R.string.server_recovery_enable_rescue_title
        ServerRecoveryOperation.DISABLE_RESCUE -> R.string.server_recovery_disable_rescue_title
    },
)

@Composable
private fun recoveryDescription(operation: ServerRecoveryOperation, serverName: String): String = stringResource(
    when (operation) {
        ServerRecoveryOperation.RESET_ROOT_PASSWORD -> R.string.server_recovery_reset_password_description
        ServerRecoveryOperation.ENABLE_RESCUE -> R.string.server_recovery_enable_rescue_description
        ServerRecoveryOperation.DISABLE_RESCUE -> R.string.server_recovery_disable_rescue_description
    }, serverName,
)
