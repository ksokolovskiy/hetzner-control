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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.EnableRescueSpec
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.ServerRecoveryUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal enum class ServerRecoveryOperation { RESET_ROOT_PASSWORD, ENABLE_RESCUE, DISABLE_RESCUE }

private enum class RecoveryExecutionState { CONFIRMING, SUBMITTING, WAITING, COMPLETE, ERROR }

/**
 * All recovery secrets remain in composition memory only. The source OneTimeRootPassword is consumed
 * immediately and neither it nor its value is logged, stored, saved in state, or sent to navigation.
 */
@Composable
internal fun ServerRecoveryDialog(
    server: Server,
    operation: ServerRecoveryOperation,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onActionCompleted: () -> Unit,
) {
    var executionState by remember(server.id, operation) { mutableStateOf(RecoveryExecutionState.CONFIRMING) }
    var progressStatus by remember(server.id, operation) { mutableStateOf<String?>(null) }
    var error by remember(server.id, operation) { mutableStateOf<String?>(null) }
    var revealedPassword by remember(server.id, operation) { mutableStateOf<String?>(null) }
    var passwordAcknowledged by remember(server.id, operation) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val recovery = remember(repository) { ServerRecoveryUseCase(repository) }
    val clipboard = LocalClipboardManager.current
    val actionFailed = stringResource(R.string.server_recovery_action_failed)

    fun execute() {
        executionState = RecoveryExecutionState.SUBMITTING
        error = null
        scope.launch {
            try {
                val action = when (operation) {
                    ServerRecoveryOperation.RESET_ROOT_PASSWORD -> {
                        val result = recovery.resetRootPassword(server.id)
                        // revealOnce() is deliberately called exactly once; the String stays only in this dialog memory.
                        revealedPassword = result.password.revealOnce()
                        result.action
                    }
                    ServerRecoveryOperation.ENABLE_RESCUE -> {
                        val result = recovery.enableRescue(server.id, EnableRescueSpec())
                        revealedPassword = result.password.revealOnce()
                        result.action
                    }
                    ServerRecoveryOperation.DISABLE_RESCUE -> recovery.disableRescue(server.id)
                }
                executionState = RecoveryExecutionState.WAITING
                val succeeded = awaitRecoveryAction(repository, action) { status -> progressStatus = status }
                if (succeeded) {
                    executionState = RecoveryExecutionState.COMPLETE
                    onActionCompleted()
                } else {
                    executionState = RecoveryExecutionState.ERROR
                    error = actionFailed
                }
            } catch (_: Throwable) {
                executionState = RecoveryExecutionState.ERROR
                error = actionFailed
            }
        }
    }

    val secretMustBeAcknowledged = revealedPassword != null
    AlertDialog(
        onDismissRequest = {
            if (executionState != RecoveryExecutionState.SUBMITTING && executionState != RecoveryExecutionState.WAITING && (!secretMustBeAcknowledged || passwordAcknowledged)) onDismiss()
        },
        title = { Text(recoveryTitle(operation)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(recoveryDescription(operation, server.name))
                if (executionState == RecoveryExecutionState.CONFIRMING) {
                    Text(stringResource(R.string.server_recovery_confirmation_warning), color = MaterialTheme.colorScheme.error)
                }
                if (executionState == RecoveryExecutionState.SUBMITTING || executionState == RecoveryExecutionState.WAITING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator()
                        Spacer(Modifier.width(12.dp))
                        Text(progressStatus ?: stringResource(R.string.server_recovery_submitting))
                    }
                }
                revealedPassword?.let { password ->
                    Text(stringResource(R.string.server_recovery_password_title), style = MaterialTheme.typography.titleMedium)
                    Text(stringResource(R.string.server_recovery_password_warning), color = MaterialTheme.colorScheme.error)
                    Text(password, style = MaterialTheme.typography.bodyLarge)
                    OutlinedButton(onClick = { clipboard.setText(AnnotatedString(password)) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.server_recovery_copy_password))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { passwordAcknowledged = !passwordAcknowledged },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = passwordAcknowledged, onCheckedChange = null)
                        Text(stringResource(R.string.server_recovery_password_acknowledgement), modifier = Modifier.fillMaxWidth())
                    }
                }
                if (executionState == RecoveryExecutionState.COMPLETE && revealedPassword == null) {
                    Text(stringResource(R.string.server_recovery_complete), color = MaterialTheme.colorScheme.primary)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            when (executionState) {
                RecoveryExecutionState.CONFIRMING -> Button(
                    onClick = ::execute,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(stringResource(R.string.server_recovery_confirm)) }
                RecoveryExecutionState.ERROR -> Button(onClick = ::execute) { Text(stringResource(R.string.server_recovery_retry)) }
                RecoveryExecutionState.COMPLETE -> Button(
                    onClick = onDismiss,
                    enabled = !secretMustBeAcknowledged || passwordAcknowledged,
                ) { Text(stringResource(R.string.server_recovery_close)) }
                RecoveryExecutionState.SUBMITTING, RecoveryExecutionState.WAITING -> Unit
            }
        },
        dismissButton = {
            if (executionState == RecoveryExecutionState.CONFIRMING || executionState == RecoveryExecutionState.ERROR) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
            }
        },
    )
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

private suspend fun awaitRecoveryAction(
    repository: HCloudRepository,
    initial: CloudAction,
    onStatus: (String) -> Unit,
): Boolean {
    var action = initial
    repeat(120) {
        onStatus(action.status)
        when (action.status.lowercase()) {
            "success" -> return true
            "error" -> return false
        }
        delay(1_000)
        action = repository.getAction(action.id)
    }
    return false
}
