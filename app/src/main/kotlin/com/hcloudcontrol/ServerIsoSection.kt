package com.hcloudcontrol

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hcloudcontrol.core.domain.model.CloudAction
import com.hcloudcontrol.core.domain.model.IsoOption
import com.hcloudcontrol.core.domain.model.IsoSelection
import com.hcloudcontrol.core.domain.model.Server
import com.hcloudcontrol.core.domain.repository.HCloudRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Attaches or detaches only API-listed ISOs, after an explicit second confirmation. */
@Composable
internal fun ServerIsoSection(
    server: Server,
    repository: HCloudRepository,
    readOnly: Boolean,
    onActionCompleted: () -> Unit,
) {
    val highImpactAllowed = LocalHighImpactOperations.current.isAllowed
    var managing by remember(server.id) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.server_iso_title), style = MaterialTheme.typography.titleLarge)
        Card(
            Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                server.attachedIso?.let { iso ->
                    Text(stringResource(R.string.server_iso_attached, iso.name), fontWeight = FontWeight.Medium)
                    iso.architecture?.let { Text(stringResource(R.string.server_iso_architecture, it), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                } ?: Text(stringResource(R.string.server_iso_none), color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (highImpactAllowed) {
                    OutlinedButton(onClick = { managing = true }, enabled = !readOnly, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.server_iso_manage))
                    }
                }
            }
        }
        if (readOnly) Text(stringResource(R.string.server_iso_read_only), color = MaterialTheme.colorScheme.error)
    }
    if (highImpactAllowed && managing) {
        ServerIsoManagerDialog(
            server = server,
            repository = repository,
            onDismiss = { managing = false },
            onActionCompleted = onActionCompleted,
        )
    }
}

private sealed interface IsoListState {
    data object Loading : IsoListState
    data class Content(val isos: List<IsoOption>) : IsoListState
    data class Error(val message: String) : IsoListState
}

private sealed interface PendingIsoOperation {
    data class Attach(val iso: IsoOption) : PendingIsoOperation
    data object Detach : PendingIsoOperation
}

@Composable
private fun ServerIsoManagerDialog(
    server: Server,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onActionCompleted: () -> Unit,
) {
    var listState by remember(server.id) { mutableStateOf<IsoListState>(IsoListState.Loading) }
    var selectedIso by remember(server.id) { mutableStateOf<IsoOption?>(null) }
    var pendingOperation by remember(server.id) { mutableStateOf<PendingIsoOperation?>(null) }
    val scope = rememberCoroutineScope()
    val loadError = stringResource(R.string.server_iso_load_error)
    androidx.compose.runtime.LaunchedEffect(server.id) {
        listState = try {
            IsoListState.Content(repository.getIsos())
        } catch (_: Throwable) {
            IsoListState.Error(loadError)
        }
    }
    pendingOperation?.let { operation ->
        IsoActionConfirmation(
            server = server,
            operation = operation,
            repository = repository,
            onDismiss = { pendingOperation = null },
            onCompleted = { onActionCompleted(); onDismiss() },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_iso_dialog_title, server.name)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                server.attachedIso?.let { Text(stringResource(R.string.server_iso_current, it.name)) }
                when (val current = listState) {
                    IsoListState.Loading -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.size(12.dp))
                        Text(stringResource(R.string.server_iso_loading))
                    }
                    is IsoListState.Error -> Text(current.message, color = MaterialTheme.colorScheme.error)
                    is IsoListState.Content -> {
                        if (current.isos.isEmpty()) {
                            Text(stringResource(R.string.server_iso_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            Text(stringResource(R.string.server_iso_select))
                            current.isos.sortedBy { it.name.lowercase() }.forEach { iso ->
                                val selected = selectedIso?.id == iso.id
                                OutlinedButton(
                                    onClick = { selectedIso = iso },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                        containerColor = if (selected) MaterialTheme.colorScheme.secondaryContainer else androidx.compose.ui.graphics.Color.Transparent,
                                    ),
                                ) {
                                    Column(Modifier.fillMaxWidth()) {
                                        Text(iso.name)
                                        Text(listOfNotNull(iso.type, iso.architecture).joinToString(" · "), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                server.attachedIso?.let {
                    TextButton(onClick = { pendingOperation = PendingIsoOperation.Detach }) { Text(stringResource(R.string.server_iso_detach)) }
                }
                Button(
                    onClick = { selectedIso?.let { pendingOperation = PendingIsoOperation.Attach(it) } },
                    enabled = selectedIso != null && listState is IsoListState.Content,
                ) { Text(stringResource(R.string.server_iso_review_attach)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.common_cancel)) } },
    )
}

@Composable
private fun IsoActionConfirmation(
    server: Server,
    operation: PendingIsoOperation,
    repository: HCloudRepository,
    onDismiss: () -> Unit,
    onCompleted: () -> Unit,
) {
    var submitting by remember(operation) { mutableStateOf(false) }
    var progress by remember(operation) { mutableStateOf<String?>(null) }
    var error by remember(operation) { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val sendingRequest = stringResource(R.string.common_sending_request)
    val verifyingIso = stringResource(R.string.server_iso_verifying)
    val actionError = stringResource(R.string.server_iso_action_error)
    val isDetach = operation is PendingIsoOperation.Detach
    AlertDialog(
        onDismissRequest = { if (!submitting) onDismiss() },
        title = { Text(stringResource(if (isDetach) R.string.server_iso_detach_confirm_title else R.string.server_iso_attach_confirm_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val detail = when (operation) {
                    is PendingIsoOperation.Attach -> stringResource(R.string.server_iso_attach_confirm, operation.iso.name, server.name)
                    PendingIsoOperation.Detach -> stringResource(R.string.server_iso_detach_confirm, server.attachedIso?.name ?: stringResource(R.string.server_iso_unknown), server.name)
                }
                Text(detail)
                Text(stringResource(if (isDetach) R.string.server_iso_detach_warning else R.string.server_iso_attach_warning), color = MaterialTheme.colorScheme.error)
                progress?.let { Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.size(12.dp))
                    Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    submitting = true
                    error = null
                    progress = sendingRequest
                    scope.launch {
                        try {
                            val action = when (operation) {
                                is PendingIsoOperation.Attach -> {
                                    progress = verifyingIso
                                    val iso = repository.getIso(operation.iso.id)
                                    repository.attachIso(server.id, IsoSelection(iso.id, iso.name))
                                }
                                PendingIsoOperation.Detach -> repository.detachIso(server.id)
                            }
                            awaitIsoAction(repository, action) { status ->
                                progress = context.getString(R.string.action_status_format, status)
                            }
                            onCompleted()
                        } catch (_: Throwable) {
                            error = actionError
                            submitting = false
                        }
                    }
                },
                enabled = !submitting,
                colors = if (isDetach) androidx.compose.material3.ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ) else androidx.compose.material3.ButtonDefaults.buttonColors(),
            ) { Text(stringResource(if (submitting) R.string.common_in_progress else if (isDetach) R.string.server_iso_detach else R.string.server_iso_attach)) }
        },
        dismissButton = { TextButton(onClick = onDismiss, enabled = !submitting) { Text(stringResource(R.string.common_cancel)) } },
    )
}

private suspend fun awaitIsoAction(repository: HCloudRepository, initial: CloudAction, onProgress: (String) -> Unit) {
    var current = initial
    repeat(600) {
        onProgress(current.status)
        when (current.status.lowercase()) {
            "success" -> return
            "error" -> throw IllegalStateException("ISO action returned error")
        }
        delay(1_000)
        current = repository.getAction(current.id)
    }
    throw IllegalStateException("ISO action timed out")
}
