package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import com.hcloudcontrol.core.domain.model.DnsPointer
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import com.hcloudcontrol.core.domain.usecase.ServerDnsPtrUseCase
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class ServerDnsPtrDialogState { EDITING, CONFIRMING, SUBMITTING, COMPLETE, ERROR }

/** PTR only: selectable addresses are supplied from this Server's public-net API response. */
@Composable
internal fun ServerDnsPtrDialog(
    server: Server,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onActionCompleted: () -> Unit,
) {
    val addresses = remember(server.id, server.publicDnsPointers) { server.publicDnsPointers.distinctBy { it.ip } }
    var selectedAddress by remember(server.id) { mutableStateOf(addresses.firstOrNull()) }
    var hostname by remember(server.id) { mutableStateOf(selectedAddress?.dnsPtr.orEmpty()) }
    var state by remember(server.id) { mutableStateOf(ServerDnsPtrDialogState.EDITING) }
    var error by remember(server.id) { mutableStateOf<String?>(null) }
    var status by remember(server.id) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val useCase = remember(repository) { ServerDnsPtrUseCase(repository) }
    val failureMessage = stringResource(R.string.server_dns_ptr_action_failed)

    fun submit() {
        val target = selectedAddress ?: return
        state = ServerDnsPtrDialogState.SUBMITTING
        error = null
        scope.launch {
            try {
                val action = useCase(server.id, target.ip, hostname.trim().ifBlank { null })
                val succeeded = awaitServerDnsPtrAction(repository, action) { current -> status = current }
                if (succeeded) {
                    state = ServerDnsPtrDialogState.COMPLETE
                    onActionCompleted()
                } else {
                    state = ServerDnsPtrDialogState.ERROR
                    error = failureMessage
                }
            } catch (_: Throwable) {
                state = ServerDnsPtrDialogState.ERROR
                error = failureMessage
            }
        }
    }

    AlertDialog(
        onDismissRequest = { if (state != ServerDnsPtrDialogState.SUBMITTING) onDismiss() },
        title = { Text(stringResource(R.string.server_dns_ptr_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when (state) {
                    ServerDnsPtrDialogState.EDITING -> {
                        Text(stringResource(R.string.server_dns_ptr_intro), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        addresses.forEach { address ->
                            OutlinedButton(
                                onClick = { selectedAddress = address; hostname = address.dnsPtr.orEmpty() },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(containerColor = if (selectedAddress?.ip == address.ip) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surface),
                            ) {
                                Text(stringResource(R.string.server_dns_ptr_address, if (selectedAddress?.ip == address.ip) "✓ " else "", address.ip, address.dnsPtr ?: stringResource(R.string.server_dns_ptr_not_set)))
                            }
                        }
                        if (addresses.isEmpty()) Text(stringResource(R.string.server_dns_ptr_no_public_addresses), color = MaterialTheme.colorScheme.error)
                        selectedAddress?.let {
                            OutlinedTextField(
                                value = hostname,
                                onValueChange = { value -> hostname = value },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text(stringResource(R.string.server_dns_ptr_hostname)) },
                                supportingText = { Text(stringResource(R.string.server_dns_ptr_empty_clears)) },
                                singleLine = true,
                            )
                        }
                    }
                    ServerDnsPtrDialogState.CONFIRMING -> {
                        val target = selectedAddress ?: return@Column
                        val value = hostname.trim().ifBlank { stringResource(R.string.server_dns_ptr_clear_value) }
                        Text(stringResource(R.string.server_dns_ptr_confirm_message, target.ip, value))
                        Text(stringResource(R.string.server_dns_ptr_confirm_warning), color = MaterialTheme.colorScheme.error)
                    }
                    ServerDnsPtrDialogState.SUBMITTING -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(12.dp))
                        Text(status ?: stringResource(R.string.server_dns_ptr_saving), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    ServerDnsPtrDialogState.COMPLETE -> Text(stringResource(R.string.server_dns_ptr_complete), color = MaterialTheme.colorScheme.primary)
                    ServerDnsPtrDialogState.ERROR -> error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            }
        },
        confirmButton = {
            when (state) {
                ServerDnsPtrDialogState.EDITING -> Button(
                    onClick = { state = ServerDnsPtrDialogState.CONFIRMING },
                    enabled = selectedAddress != null,
                ) { Text(stringResource(R.string.server_dns_ptr_review)) }
                ServerDnsPtrDialogState.CONFIRMING -> Button(
                    onClick = ::submit,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                ) { Text(stringResource(R.string.server_dns_ptr_confirm)) }
                ServerDnsPtrDialogState.ERROR -> Button(onClick = ::submit) { Text(stringResource(R.string.server_dns_ptr_retry)) }
                ServerDnsPtrDialogState.COMPLETE -> Button(onClick = onDismiss) { Text(stringResource(R.string.server_dns_ptr_close)) }
                ServerDnsPtrDialogState.SUBMITTING -> Unit
            }
        },
        dismissButton = {
            when (state) {
                ServerDnsPtrDialogState.EDITING, ServerDnsPtrDialogState.ERROR -> TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) }
                ServerDnsPtrDialogState.CONFIRMING -> TextButton(onClick = { state = ServerDnsPtrDialogState.EDITING }) { Text(stringResource(R.string.common_back_action)) }
                else -> Unit
            }
        },
    )
}

private suspend fun awaitServerDnsPtrAction(
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
